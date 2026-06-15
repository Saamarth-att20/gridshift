package com.gridshift.routing.model;

import java.time.Instant;

/**
 * Represents an AI inference workload to be routed across cloud regions.
 *
 * Fields are intentionally minimal — only what the scoring engine needs.
 * The signal layer (carbon API, spot pricing, etc.) is responsible for
 * hydrating RoutingCandidates; this record just carries the job identity
 * and deadline constraint.
 *
 * @author Saamarth Attray
 */
public record Workload(
        String id,
        String modelId,           // e.g. "llama-3-70b", "gpt-4o"
        double gpuHours,          // estimated GPU-hours for this job
        Instant submittedAt,
        Instant deadline,         // hard deadline — candidates missing this are excluded
        String homeRegion         // where the job would run if not routed (baseline)
) {
    public Workload {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Workload id required");
        if (gpuHours <= 0) throw new IllegalArgumentException("gpuHours must be positive");
        if (deadline.isBefore(submittedAt)) throw new IllegalArgumentException("deadline must be after submission");
    }
}
