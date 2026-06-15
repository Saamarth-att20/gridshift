package com.gridshift.routing.model;

import com.gridshift.routing.scoring.ScoreBreakdown;

import java.time.Instant;
import java.util.List;

/**
 * The complete output of one routing decision.
 *
 * This record is triple-purpose:
 *   1. Audit log entry — every field needed to explain a routing choice
 *   2. Demo artifact — pretty-printable summary for stakeholder demos
 *   3. ML training row — when you have enough of these, train the ML layer
 *                        to learn the weights that minimize real cost
 *
 * @author Saamarth Attray
 */
public record RoutingDecision(
        String workloadId,
        Instant decidedAt,
        RoutingCandidate baseline,
        ScoreBreakdown baselineScore,
        RoutingCandidate chosen,
        ScoreBreakdown chosenScore,
        List<ScoredCandidate> allConsidered,
        String rationale
) {

    /** One-line summary for logs / demo output. */
    public String summary() {
        double dollarsSaved  = baselineScore.total() - chosenScore.total();
        double carbonAvoided = baseline.carbonIntensity() - chosen.carbonIntensity();

        long delayMinutes = java.time.Duration.between(
                baseline.windowStart(), chosen.windowStart()).toMinutes();

        return String.format(
                "Routed %s → %s (+%dm). Saved $%.4f and %.1f gCO2/kWh vs. running at %s now.",
                workloadId,
                chosen.regionKey(),
                delayMinutes,
                dollarsSaved,
                carbonAvoided,
                baseline.regionKey()
        );
    }

    /** Full breakdown for demo / debug output. */
    public String verbose() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== ROUTING DECISION: %s ===\n", workloadId));
        sb.append(summary()).append("\n");
        sb.append(String.format("Chosen: %-20s %s\n", chosen.regionKey(), chosenScore));
        sb.append(String.format("Baseline (%-18s): %s\n", baseline.regionKey(), baselineScore));
        sb.append(String.format("Dollars saved: $%.4f | Carbon avoided: %.1f gCO2/kWh (~%.0f%%)\n",
                baselineScore.total() - chosenScore.total(),
                baseline.carbonIntensity() - chosen.carbonIntensity(),
                baseline.carbonIntensity() > 0
                        ? (baseline.carbonIntensity() - chosen.carbonIntensity()) / baseline.carbonIntensity() * 100 : 0));
        sb.append("\nAll feasible candidates considered:\n");
        for (ScoredCandidate sc : allConsidered) {
            String marker = sc.candidate().equals(chosen) ? "  <- chosen" : "";
            long delay = java.time.Duration.between(
                    baseline.windowStart(), sc.candidate().windowStart()).toMinutes();
            sb.append(String.format("  %-6s %-16s +%dm   %s%s\n",
                    sc.candidate().provider(),
                    sc.candidate().region(),
                    delay,
                    sc.score(),
                    marker));
        }
        return sb.toString();
    }
}
