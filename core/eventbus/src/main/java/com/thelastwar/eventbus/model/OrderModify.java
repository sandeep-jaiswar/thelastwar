package com.thelastwar.eventbus.model;

/**
 * OrderModify represents a request to modify an existing order's price and/or quantity.
 * Used for order replacement operations in the matching engine.
 * 
 * Performance characteristics:
 * - Compact memory layout via record
 * - All fields are primitives or immutable strings
 * - Zero allocation in hot path
 * - Suitable for deterministic replay
 * 
 * @param orderId      Order identifier to modify
 * @param symbol       Trading symbol (for validation)
 * @param newPrice     New price (0 means no change)
 * @param newQuantity  New quantity (0 means no change)
 * @param timestamp    Modify request timestamp in nanoseconds
 * @param account      Account identifier (for validation)
 * @param requestId    Unique identifier for this modify request
 */
public record OrderModify(
        long orderId,
        String symbol,
        long newPrice,
        long newQuantity,
        long timestamp,
        long account,
        long requestId) {
    
    /**
     * Compact constructor with validation.
     */
    public OrderModify {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (newPrice < 0) {
            throw new IllegalArgumentException("New price cannot be negative");
        }
        if (newQuantity < 0) {
            throw new IllegalArgumentException("New quantity cannot be negative");
        }
        if (newPrice == 0 && newQuantity == 0) {
            throw new IllegalArgumentException("At least one of price or quantity must be specified");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative");
        }
        if (requestId <= 0) {
            throw new IllegalArgumentException("Request ID must be positive");
        }
    }
    
    /**
     * Factory method for creating a modify request.
     * 
     * @param orderId     Order identifier to modify
     * @param symbol      Trading symbol
     * @param newPrice    New price (0 means no change)
     * @param newQuantity New quantity (0 means no change)
     * @param account     Account identifier
     * @param requestId   Unique request identifier
     * @return new OrderModify with current timestamp
     */
    public static OrderModify create(long orderId, String symbol, long newPrice, 
                                     long newQuantity, long account, long requestId) {
        return new OrderModify(orderId, symbol, newPrice, newQuantity, 
                              System.nanoTime(), account, requestId);
    }
    
    /**
     * Factory method for price-only modification.
     * 
     * @param orderId   Order identifier to modify
     * @param symbol    Trading symbol
     * @param newPrice  New price
     * @param account   Account identifier
     * @param requestId Unique request identifier
     * @return new OrderModify with only price changed
     */
    public static OrderModify modifyPrice(long orderId, String symbol, long newPrice, 
                                         long account, long requestId) {
        return create(orderId, symbol, newPrice, 0, account, requestId);
    }
    
    /**
     * Factory method for quantity-only modification.
     * 
     * @param orderId     Order identifier to modify
     * @param symbol      Trading symbol
     * @param newQuantity New quantity
     * @param account     Account identifier
     * @param requestId   Unique request identifier
     * @return new OrderModify with only quantity changed
     */
    public static OrderModify modifyQuantity(long orderId, String symbol, long newQuantity, 
                                            long account, long requestId) {
        return create(orderId, symbol, 0, newQuantity, account, requestId);
    }
    
    /**
     * Checks if this modification changes the price.
     * 
     * @return true if price is being modified
     */
    public boolean modifiesPrice() {
        return newPrice > 0;
    }
    
    /**
     * Checks if this modification changes the quantity.
     * 
     * @return true if quantity is being modified
     */
    public boolean modifiesQuantity() {
        return newQuantity > 0;
    }
}
