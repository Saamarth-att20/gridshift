package com.gridshift.dto;

import java.time.Instant;
import java.util.List;

/**
 * Outbound routing response. Intentionally omits internal scoring
 * details that could leak pricing signal or system architecture.
 *
 * @author Saamarth Attray
 */
public record RouteResponse(
    String jobId,
    Instant decidedAt,
    String chosenRegion,
    String chosenProvider,
    double totalCostUsd,
    double baselineCostUsd,
    double dollarsSaved,
    double carbonIntensity,
    double baselineCarbonIntensity,
    double carbonReductionPct,
    long delayMinutes,
    boolean usedLiveCarbon,
    String rationale,
    List<CandidateSummary> allConsidered
) {
    public record CandidateSummary(
        String region,
        String provider,
        double totalCostUsd,
        double carbonIntensity,
        boolean chosen
    ) {}
}
