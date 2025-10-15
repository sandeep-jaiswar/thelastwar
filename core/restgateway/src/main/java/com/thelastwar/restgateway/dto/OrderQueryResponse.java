package com.thelastwar.restgateway.dto;

import com.dslplatform.json.CompiledJson;

/**
 * DTO for order query response.
 */
@CompiledJson
public record OrderQueryResponse(
    boolean found,
    long orderId,
    String clientOrderId,
    String state,
    boolean isTerminal,
    String correlationId,
    String message
) {
    public static OrderQueryResponse found(long orderId, String clientOrderId, String state, boolean isTerminal, String correlationId) {
        return new OrderQueryResponse(true, orderId, clientOrderId, state, isTerminal, correlationId, null);
    }
    
    public static OrderQueryResponse notFound(String message) {
        return new OrderQueryResponse(false, 0, null, null, false, null, message);
    }
}
