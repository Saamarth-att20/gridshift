package com.gridshift.routing;

import com.gridshift.routing.model.RoutingCandidate;
import com.gridshift.routing.model.RoutingDecision;
import com.gridshift.routing.model.ScoredCandidate;
import com.gridshift.routing.model.Workload;
import com.gridshift.routing.scoring.CandidateScorer;
import com.gridshift.routing.scoring.ScoreBreakdown;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure decision logic, no I/O. Takes a workload, the baseline candidate
 * (run at home, now), and the set of alternatives the signal layer has
 * assembled, and returns a fully-explained RoutingDecision.
 *
 * Flow is deliberately boring and inspectable:
 *   1. Keep only feasible candidates (deadline-respecting)
 *   2. Always include the baseline (doing nothing must be selectable)
 *   3. Score everything with the same scorer
 *   4. Pick the minimum total shadow cost
 *   5. Tie-break deterministically (lower carbon, then earlier window)
 *   6. Emit the decision with every considered candidate logged
 *
 * Determinism is the whole point: identical inputs always yield identical
 * decisions, so the log is reproducible and the future ML layer has clean labels.
 *
 * @author Saamarth Attray
 */
public final class RoutingEngine {

    private final CandidateScorer scorer;

    public RoutingEngine(CandidateScorer scorer) {
        this.scorer = scorer;
    }

    public RoutingDecision route(Workload workload,
                                 RoutingCandidate baseline,
                                 List<RoutingCandidate> alternatives) {

        ScoreBreakdown baselineScore = scorer.score(baseline);

        List<ScoredCandidate> scored = new ArrayList<>();
        scored.add(new ScoredCandidate(baseline, baselineScore));

        for (RoutingCandidate c : alternatives) {
            if (c.isFeasible()) {
                scored.add(new ScoredCandidate(c, scorer.score(c)));
            }
        }

        // Pick minimum total cost; tie-break on lower carbon, then earlier start
        Optional<ScoredCandidate> best = scored.stream().min(
                Comparator.<ScoredCandidate>comparingDouble(sc -> sc.score().total())
                        .thenComparingDouble(sc -> sc.candidate().carbonIntensity())
                        .thenComparing(sc -> sc.candidate().windowStart())
        );

        ScoredCandidate winner = best.orElse(new ScoredCandidate(baseline, baselineScore));

        return new RoutingDecision(
                workload.id(),
                Instant.now(),
                baseline,
                baselineScore,
                winner.candidate(),
                winner.score(),
                scored,
                buildRationale(baseline, baselineScore, winner)
        );
    }

    private String buildRationale(RoutingCandidate baseline,
                                  ScoreBreakdown baselineScore,
                                  ScoredCandidate winner) {
        RoutingCandidate w = winner.candidate();
        if (w.equals(baseline)) {
            return "Kept at home region; no feasible alternative scored lower.";
        }
        double savings = baselineScore.total() - winner.score().total();
        double carbonPct = baseline.carbonIntensity() > 0
                ? (baseline.carbonIntensity() - w.carbonIntensity()) / baseline.carbonIntensity() * 100 : 0;
        return String.format(
                "Routed to %s/%s: saves $%.4f total shadow cost (%.0f%% less carbon).",
                w.provider(), w.region(), savings, carbonPct
        );
    }
}
