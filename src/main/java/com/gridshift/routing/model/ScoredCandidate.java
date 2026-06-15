package com.gridshift.routing.model;

import com.gridshift.routing.scoring.ScoreBreakdown;

/**
 * A RoutingCandidate paired with its computed ScoreBreakdown.
 * Used internally by RoutingEngine for ranking and stored in RoutingDecision
 * for full audit trail.
 *
 * @author Saamarth Attray
 */
public record ScoredCandidate(
        RoutingCandidate candidate,
        ScoreBreakdown score
) {}
