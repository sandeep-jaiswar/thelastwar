package com.thelastwar.restgateway.dto;

import com.dslplatform.json.CompiledJson;

/**
 * DTO for risk check responses.
 */
@CompiledJson
public record RiskCheckResponse(
    boolean approved,
    int reasonCode,
    String message,
    String correlationId,
    long latencyNanos,
    boolean serviceAvailable
) {
    /**
     * Creates an error response for validation errors.
     */
    public static RiskCheckResponse error(String message, String correlationId) {
        return new RiskCheckResponse(
            false,
            -1,
            message,
            correlationId,
            0L,
            true
        );
    }
}
