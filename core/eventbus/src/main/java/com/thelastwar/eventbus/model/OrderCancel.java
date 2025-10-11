package com.thelastwar.eventbus.model;

/**
 * OrderCancel represents a request to cancel an existing order.
 * Used for order cancellation operations in the matching engine.
 * 
 * Performance characteristics:
 * - Compact memory layout via record
 * - All fields are primitives or immutable strings
 * - Zero allocation in hot path
 * - Suitable for deterministic replay
 * 
 * @param orderId      Order identifier to cancel
 * @param symbol       Trading symbol (for validation)
 * @param timestamp    Cancel request timestamp in nanoseconds
 * @param account      Account identifier (for validation)
 * @param requestId    Unique identifier for this cancel request
 */
public record OrderCancel(
        long orderId,
        String symbol,
        long timestamp,
        long account,
        long requestId) {
    
    /**
     * Compact constructor with validation.
     */
    public OrderCancel {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative");
        }
        if (requestId <= 0) {
            throw new IllegalArgumentException("Request ID must be positive");
        }
    }
    
    /**
     * Factory method for creating a cancel request.
     * 
     * @param orderId   Order identifier to cancel
     * @param symbol    Trading symbol
     * @param account   Account identifier
     * @param requestId Unique request identifier
     * @return new OrderCancel with current timestamp
     */
    public static OrderCancel create(long orderId, String symbol, long account, long requestId) {
        return new OrderCancel(orderId, symbol, System.nanoTime(), account, requestId);
    }
}
