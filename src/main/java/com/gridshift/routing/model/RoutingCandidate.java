package com.gridshift.routing.model;

import java.time.Instant;

/**
 * A candidate cloud region where a workload could be dispatched.
 *
 * The signal layer (carbon API, spot pricing, water stress index) is
 * responsible for populating this before passing it to the scoring engine.
 * The routing engine itself is pure math — it never makes network calls.
 *
 * carbonIntensity : gCO2eq per kWh from ElectricityMaps / WattTime
 * waterStressIndex: 0–5 basin-level score from WRI Aqueduct
 * computeCostPerGpuHour: real-time spot price in USD
 * windowStart     : earliest the job can begin in this region
 * latencyMs       : estimated round-trip from the submitting service
 *
 * @author Saamarth Attray
 */
public record RoutingCandidate(
        String provider,              // "aws" | "gcp" | "azure"
        String region,                // e.g. "us-east-1", "us-central1"
        double carbonIntensity,       // gCO2eq/kWh
        double waterStressIndex,      // 0–5 (WRI Aqueduct)
        double computeCostPerGpuHour, // USD
        long   latencyMs,             // ms round-trip from submitter
        Instant windowStart,          // earliest start in this region
        boolean feasible              // false if misses deadline
) {
    public boolean isFeasible() { return feasible; }

    public String regionKey() { return provider + "/" + region; }
}
