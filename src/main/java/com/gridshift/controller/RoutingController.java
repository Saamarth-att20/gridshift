package com.gridshift.controller;

import com.gridshift.config.RateLimitConfig;
import com.gridshift.dto.RouteRequest;
import com.gridshift.dto.RouteResponse;
import com.gridshift.routing.RoutingEngine;
import com.gridshift.routing.model.RoutingDecision;
import com.gridshift.routing.model.Workload;
import com.gridshift.routing.scoring.CandidateScorer;
import com.gridshift.routing.scoring.ScoringWeights;
import com.gridshift.routing.signal.ElectricityMapsSignal;
import com.gridshift.routing.signal.SignalLayer;
import com.gridshift.routing.signal.StaticFallbackSignal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller for routing decisions.
 *
 * POST /api/v1/route  — submit a workload, get a routing decision back
 * GET  /api/v1/health — liveness check (also at /actuator/health)
 *
 * @author Saamarth Attray
 */
@RestController
@RequestMapping("/api/v1")
public class RoutingController {

    @Value("${gridshift.electricity-maps-api-key:}")
    private String electricityMapsKey;

    private final RateLimitConfig rateLimiter;

    public RoutingController(RateLimitConfig rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/route")
    public ResponseEntity<?> route(
            @Valid @RequestBody RouteRequest req,
            HttpServletRequest httpRequest) {

        // Rate limit by IP
        String ip = getClientIp(httpRequest);
        if (!rateLimiter.tryConsume(ip)) {
            return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                    "error", "Rate limit exceeded",
                    "message", "Max 30 requests per minute per IP"
                ));
        }

        try {
            Workload workload = new Workload(
                req.jobId(),
                req.modelId(),
                req.gpuHours(),
                Instant.now(),
                Instant.now().plusSeconds(req.deadlineHours() * 3600L),
                req.homeRegion()
            );

            var carbonSignal = (electricityMapsKey != null && !electricityMapsKey.isBlank())
                ? new ElectricityMapsSignal(electricityMapsKey)
                : StaticFallbackSignal.INSTANCE;

            SignalLayer signals = new SignalLayer(carbonSignal);
            SignalLayer.Result signalResult = signals.assembleForWorkload(workload);

            ScoringWeights weights = ScoringWeights.defaults();
            CandidateScorer scorer = new CandidateScorer(weights, workload);
            RoutingEngine engine = new RoutingEngine(scorer);

            RoutingDecision decision = engine.route(
                workload,
                signalResult.baseline(),
                signalResult.alternatives()
            );

            return ResponseEntity.ok(toResponse(decision, signalResult.usingLiveCarbon()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                .badRequest()
                .body(Map.of("error", "Invalid request", "message", e.getMessage()));
        } catch (Exception e) {
            // Never leak stack traces to callers
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Routing failed"));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "gridshift-routing",
            "carbonSignal", (electricityMapsKey != null && !electricityMapsKey.isBlank())
                ? "live" : "static-fallback"
        ));
    }

    private RouteResponse toResponse(RoutingDecision d, boolean liveCarbon) {
        long delayMin = java.time.Duration.between(
            d.baseline().windowStart(), d.chosen().windowStart()).toMinutes();

        double carbonReduction = d.baseline().carbonIntensity() > 0
            ? (d.baseline().carbonIntensity() - d.chosen().carbonIntensity())
              / d.baseline().carbonIntensity() * 100.0
            : 0;

        List<RouteResponse.CandidateSummary> all = d.allConsidered().stream()
            .map(sc -> new RouteResponse.CandidateSummary(
                sc.candidate().region(),
                sc.candidate().provider(),
                round(sc.score().total()),
                sc.candidate().carbonIntensity(),
                sc.candidate().equals(d.chosen())
            ))
            .toList();

        return new RouteResponse(
            d.workloadId(),
            d.decidedAt(),
            d.chosen().region(),
            d.chosen().provider(),
            round(d.chosenScore().total()),
            round(d.baselineScore().total()),
            round(d.baselineScore().total() - d.chosenScore().total()),
            d.chosen().carbonIntensity(),
            d.baseline().carbonIntensity(),
            round(carbonReduction),
            delayMin,
            liveCarbon,
            d.rationale(),
            all
        );
    }

    /** Extract real client IP — handles proxies and load balancers. */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
