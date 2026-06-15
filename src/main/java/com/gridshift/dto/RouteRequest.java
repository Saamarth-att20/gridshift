package com.gridshift.dto;

import jakarta.validation.constraints.*;

/**
 * Inbound routing request. All fields validated before touching
 * the routing engine — never trust raw user input.
 *
 * @author Saamarth Attray
 */
public record RouteRequest(

    @NotBlank(message = "jobId is required")
    @Size(max = 64, message = "jobId must be 64 chars or fewer")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "jobId must be alphanumeric")
    String jobId,

    @NotBlank(message = "modelId is required")
    @Size(max = 128, message = "modelId must be 128 chars or fewer")
    String modelId,

    @NotNull(message = "gpuHours is required")
    @DecimalMin(value = "0.1", message = "gpuHours must be at least 0.1")
    @DecimalMax(value = "1000.0", message = "gpuHours cannot exceed 1000")
    Double gpuHours,

    @NotNull(message = "deadlineHours is required")
    @Min(value = 1, message = "deadlineHours must be at least 1")
    @Max(value = 168, message = "deadlineHours cannot exceed 168")
    Integer deadlineHours,

    @NotBlank(message = "homeRegion is required")
    @Size(max = 64, message = "homeRegion must be 64 chars or fewer")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "homeRegion must be alphanumeric")
    String homeRegion

) {}
