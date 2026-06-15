package com.gridshift.routing.scoring;

/**
 * Decomposed shadow cost for one candidate, in USD.
 * Stored in the decision log so every routing choice is fully auditable.
 *
 * @author Saamarth Attray
 */
public record ScoreBreakdown(
        double computeCost,   // raw spot cost for the GPU-hours
        double carbonCost,    // shadow price of carbon intensity * GPU-hours
        double waterCost,     // shadow price of water stress * GPU-hours
        double latencyCost    // shadow price of round-trip latency
) {
    public double total() {
        return computeCost + carbonCost + waterCost + latencyCost;
    }

    @Override
    public String toString() {
        return String.format(
                "total $%.4f (compute %.4f / carbon %.4f / water %.4f / lat %.4f)",
                total(), computeCost, carbonCost, waterCost, latencyCost
        );
    }
}
