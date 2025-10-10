package com.thelastwar.orderbook;

/**
 * Internal order representation for the order book.
 * Optimized for minimal memory footprint and cache-friendly layout.
 * 
 * Uses primitives to avoid boxing overhead in the hot path.
 * All fields are final for thread-safety and JIT optimization.
 * 
 * @param orderId   Unique order identifier
 * @param symbol    Trading symbol
 * @param side      Order side: 1=Buy, 2=Sell
 * @param price     Order price (in minimum price increments)
 * @param quantity  Remaining quantity (unfilled)
 * @param timestamp Order entry timestamp in nanoseconds
 */
public record Order(
        long orderId,
        String symbol,
        byte side,
        long price,
        long quantity,
        long timestamp) {
    
    // Order Side constants
    public static final byte SIDE_BUY = 1;
    public static final byte SIDE_SELL = 2;
    
    /**
     * Compact constructor with validation.
     */
    public Order {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (side != SIDE_BUY && side != SIDE_SELL) {
            throw new IllegalArgumentException("Invalid side: " + side);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative");
        }
    }
    
    /**
     * Creates a new order with updated quantity (for partial fills).
     * 
     * @param newQuantity Updated quantity
     * @return new Order with updated quantity
     */
    public Order withQuantity(long newQuantity) {
        return new Order(orderId, symbol, side, price, newQuantity, timestamp);
    }
    
    /**
     * Checks if this is a buy order.
     * 
     * @return true if buy order
     */
    public boolean isBuy() {
        return side == SIDE_BUY;
    }
    
    /**
     * Checks if this is a sell order.
     * 
     * @return true if sell order
     */
    public boolean isSell() {
        return side == SIDE_SELL;
    }
}
