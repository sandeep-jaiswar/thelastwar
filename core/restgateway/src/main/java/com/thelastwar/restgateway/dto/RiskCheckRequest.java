package com.thelastwar.restgateway.dto;

import com.dslplatform.json.CompiledJson;

/**
 * DTO for risk check requests.
 */
@CompiledJson
public record RiskCheckRequest(
    String symbol,
    String side,      // "BUY" or "SELL"
    String orderType, // "MARKET", "LIMIT", "STOP", "STOP_LIMIT"
    long quantity,
    long price,       // Price in minimum increments (0 for market orders)
    long account
) {
    /**
     * Validates the risk check request.
     * 
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (side == null || (!side.equals("BUY") && !side.equals("SELL"))) {
            throw new IllegalArgumentException("Side must be BUY or SELL");
        }
        if (orderType == null) {
            throw new IllegalArgumentException("Order type is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (!orderType.equals("MARKET") && price <= 0) {
            throw new IllegalArgumentException("Price must be positive for non-market orders");
        }
    }
    
    /**
     * Converts string side to byte representation.
     */
    public byte getSideByte() {
        return side.equals("BUY") ? (byte) 1 : (byte) 2;
    }
    
    /**
     * Converts string order type to byte representation.
     */
    public byte getOrderTypeByte() {
        return switch (orderType) {
            case "MARKET" -> (byte) 1;
            case "LIMIT" -> (byte) 2;
            case "STOP" -> (byte) 3;
            case "STOP_LIMIT" -> (byte) 4;
            default -> throw new IllegalArgumentException("Invalid order type: " + orderType);
        };
    }
}
