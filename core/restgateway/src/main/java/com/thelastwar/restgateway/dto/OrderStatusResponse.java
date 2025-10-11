package com.thelastwar.restgateway.dto;

import com.dslplatform.json.CompiledJson;

/**
 * DTO for order status responses.
 * Optimized for DSL-JSON serialization.
 */
@CompiledJson
public record OrderStatusResponse(
    long orderId,
    String symbol,
    String side,
    String orderType,
    long quantity,
    long price,
    String status,    // "NEW", "PARTIALLY_FILLED", "FILLED", "CANCELLED", "REJECTED"
    long timestamp
) {
    /**
     * Creates a response with status name.
     */
    public static OrderStatusResponse fromStatus(long orderId, String symbol, String side, 
                                                  String orderType, long quantity, long price,
                                                  byte statusCode, long timestamp) {
        String statusName = switch (statusCode) {
            case 0 -> "NEW";
            case 1 -> "PARTIALLY_FILLED";
            case 2 -> "FILLED";
            case 3 -> "CANCELLED";
            case 4 -> "REJECTED";
            default -> "UNKNOWN";
        };
        return new OrderStatusResponse(orderId, symbol, side, orderType, quantity, price, statusName, timestamp);
    }
}
