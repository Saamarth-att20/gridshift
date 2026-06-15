package com.gridshift.routing.signal;

import com.gridshift.routing.model.RoutingCandidate;
import com.gridshift.routing.model.Workload;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the list of RoutingCandidates for a workload by hydrating
 * real-time signals (carbon, water stress, spot compute cost) per region.
 *
 * This is the only class that touches external data sources.
 * The RoutingEngine itself is pure logic and never calls this.
 *
 * Water stress values come from WRI Aqueduct (updated annually, stable enough
 * to embed as constants). Compute costs would come from spot pricing APIs in
 * production; here they're representative values from AWS/GCP/Azure published
 * A100 rates as of 2024.
 *
 * @author Saamarth Attray
 */
public final class SignalLayer {

    /** Candidate regions GridShift knows how to route to. Add more as needed. */
    private static final List<RegionSpec> KNOWN_REGIONS = List.of(
            // provider,  region,            waterStress, costPerGpuHr, latencyMs(vs us-east-1)
            new RegionSpec("aws",   "us-east-1",       3.6,  3.40,   0),
            new RegionSpec("aws",   "us-west-2",       1.2,  3.40,  70),
            new RegionSpec("aws",   "eu-north-1",      0.5,  3.55, 100),
            new RegionSpec("gcp",   "us-central1",     2.4,  2.95,  20),
            new RegionSpec("gcp",   "us-west1",        1.0,  2.95,  80),
            new RegionSpec("gcp",   "europe-north1",   0.4,  3.10, 105),
            new RegionSpec("azure", "eastus",          3.5,  3.60,   5),
            new RegionSpec("azure", "westus2",         1.1,  3.60,  75),
            new RegionSpec("azure", "swedencentral",   0.5,  3.65, 108)
    );

    private final CarbonSignal carbonSignal;

    public SignalLayer(CarbonSignal carbonSignal) {
        this.carbonSignal = carbonSignal;
    }

    /**
     * Returns the baseline candidate (home region, start now) and all
     * alternative candidates. Feasibility is determined by whether
     * the earliest window start leaves enough time before the deadline.
     */
    public Result assembleForWorkload(Workload workload) {
        Instant now = Instant.now();

        RoutingCandidate baseline = null;
        List<RoutingCandidate> alternatives = new ArrayList<>();

        for (RegionSpec spec : KNOWN_REGIONS) {
            boolean isHome = spec.region().equals(workload.homeRegion());

            double carbon = carbonSignal.getCarbonIntensity(spec.provider(), spec.region());

            // Simple window model: home region starts now; remotes start after network propagation
            long propagationMs = isHome ? 0L : (spec.baseLatencyMs() + 120_000L); // +2 min dispatch overhead
            Instant windowStart = now.plusMillis(propagationMs);

            boolean feasible = windowStart.isBefore(workload.deadline());

            RoutingCandidate candidate = new RoutingCandidate(
                    spec.provider(),
                    spec.region(),
                    carbon,
                    spec.waterStressIndex(),
                    spec.computeCostPerGpuHr(),
                    spec.baseLatencyMs(),
                    windowStart,
                    feasible
            );

            if (isHome) {
                baseline = candidate;
            } else {
                alternatives.add(candidate);
            }
        }

        // If home region not in known list, create a generic baseline
        if (baseline == null) {
            baseline = new RoutingCandidate(
                    "unknown", workload.homeRegion(),
                    400.0, 3.0, 3.50, 0, now, true
            );
        }

        return new Result(baseline, alternatives, carbonSignal.isLive());
    }

    public record Result(
            RoutingCandidate baseline,
            List<RoutingCandidate> alternatives,
            boolean usingLiveCarbon
    ) {}

    private record RegionSpec(
            String provider,
            String region,
            double waterStressIndex,
            double computeCostPerGpuHr,
            long baseLatencyMs
    ) {}
}
