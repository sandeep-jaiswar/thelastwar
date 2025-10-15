package com.thelastwar.restgateway.dto;

import com.dslplatform.json.CompiledJson;

/**
 * DTO for order submission response.
 */
@CompiledJson
public record OrderSubmissionResponse(
    boolean success,
    long orderId,
    String clientOrderId,
    String message,
    String correlationId,
    long latencyNanos,
    boolean riskRejected,
    int riskReasonCode
) {
    public static OrderSubmissionResponse success(long orderId, String clientOrderId, String correlationId, long latencyNanos) {
        return new OrderSubmissionResponse(true, orderId, clientOrderId, "Order submitted successfully", correlationId, latencyNanos, false, 0);
    }
    
    public static OrderSubmissionResponse error(String message, String correlationId) {
        return new OrderSubmissionResponse(false, 0, null, message, correlationId, 0, false, 0);
    }
    
    public static OrderSubmissionResponse riskRejection(long orderId, String clientOrderId, String message, int reasonCode, String correlationId, long latencyNanos) {
        return new OrderSubmissionResponse(false, orderId, clientOrderId, message, correlationId, latencyNanos, true, reasonCode);
    }
}
