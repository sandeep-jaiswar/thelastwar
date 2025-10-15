package com.thelastwar.restgateway.dto;

import com.dslplatform.json.CompiledJson;

/**
 * DTO for order cancellation response.
 */
@CompiledJson
public record OrderCancellationResponse(
    boolean success,
    long orderId,
    String clientOrderId,
    String message,
    String correlationId
) {
    public static OrderCancellationResponse success(long orderId, String clientOrderId, String correlationId) {
        return new OrderCancellationResponse(true, orderId, clientOrderId, "Order cancelled successfully", correlationId);
    }
    
    public static OrderCancellationResponse error(String message, String correlationId) {
        return new OrderCancellationResponse(false, 0, null, message, correlationId);
    }
}
