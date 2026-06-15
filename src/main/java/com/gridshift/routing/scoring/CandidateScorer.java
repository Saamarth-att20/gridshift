package com.gridshift.routing.scoring;

import com.gridshift.routing.model.RoutingCandidate;
import com.gridshift.routing.model.Workload;

/**
 * Converts a RoutingCandidate into a dollar-denominated ScoreBreakdown.
 *
 * Formula (all in USD):
 *   computeCost = candidate.computeCostPerGpuHour * workload.gpuHours
 *   carbonCost  = (candidate.carbonIntensity * workload.gpuHours * GPU_KWH_PER_HOUR)
 *                  / 1_000_000  [gCO2 → tonnes]  * carbonDollarsPerTonne
 *   waterCost   = candidate.waterStressIndex * workload.gpuHours * waterDollarsPerStressUnit
 *   latencyCost = candidate.latencyMs * latencyDollarsPerMs
 *
 * GPU energy constant: A100 draws ~0.4 kWh per GPU-hour (conservative estimate).
 *
 * @author Saamarth Attray
 */
public final class CandidateScorer {

    /** A100 80GB TDP in kW — used to convert GPU-hours to kWh for carbon math. */
    private static final double GPU_KW = 0.4;

    private final ScoringWeights weights;
    private final Workload workload;

    public CandidateScorer(ScoringWeights weights, Workload workload) {
        this.weights = weights;
        this.workload = workload;
    }

    public ScoreBreakdown score(RoutingCandidate c) {
        double gpuHours = workload.gpuHours();

        double computeCost = c.computeCostPerGpuHour() * gpuHours;

        // gCO2/kWh * kWh = gCO2; divide by 1e6 to get tonnes; multiply by $/tonne
        double kWh = gpuHours * GPU_KW;
        double carbonCost = (c.carbonIntensity() * kWh / 1_000_000.0) * weights.carbonDollarsPerTonne();

        double waterCost = c.waterStressIndex() * gpuHours * weights.waterDollarsPerStressUnit();

        double latencyCost = c.latencyMs() * weights.latencyDollarsPerMs();

        return new ScoreBreakdown(computeCost, carbonCost, waterCost, latencyCost);
    }
}
