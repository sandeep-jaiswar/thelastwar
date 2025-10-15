package com.thelastwar.restgateway.dto;

import com.dslplatform.json.CompiledJson;

/**
 * DTO for risk metrics responses.
 */
@CompiledJson
public record RiskMetricsResponse(
    long totalChecks,
    long approvedCount,
    long rejectedCount,
    long failureCount,
    double approvalRate,
    double rejectionRate,
    double failureRate,
    long averageLatencyNanos,
    long maxLatencyNanos
) {
}
