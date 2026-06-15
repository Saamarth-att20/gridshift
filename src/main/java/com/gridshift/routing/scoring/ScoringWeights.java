package com.gridshift.routing.scoring;

/**
 * Dollar-denominated shadow prices that convert physical signals into a
 * single comparable cost dimension.
 *
 * Everything is in USD per unit so the final score is one number
 * (total shadow cost in dollars) that the engine can rank directly.
 *
 * THIS RECORD IS THE ML SWAP POINT.
 * When you train a model, you replace static defaults here with learned
 * weights from the decision log. The scoring engine itself never changes.
 *
 * Defaults derived from typical enterprise carbon credit pricing (~$15/tCO2),
 * WRI water risk premium literature, and a conservative latency SLA.
 *
 * @author Saamarth Attray
 */
public record ScoringWeights(
        double carbonDollarsPerTonne,    // USD per tonne CO2 (1 tonne = 1,000,000 gCO2)
        double waterDollarsPerStressUnit,// USD per WRI Aqueduct unit per GPU-hour
        double latencyDollarsPerMs       // USD per ms of round-trip latency
) {
    /** Sensible production defaults. */
    public static ScoringWeights defaults() {
        return new ScoringWeights(
                15.0,    // $15 / tonne CO2 (conservative carbon credit floor)
                0.06,    // $0.06 per WRI unit per GPU-hour
                0.000005 // $0.000005 per ms (penalizes >200ms gently)
        );
    }
}
