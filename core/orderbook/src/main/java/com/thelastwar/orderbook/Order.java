package com.thelastwar.orderbook;

/**
 * Internal order representation for the order book.
 * Optimized for minimal memory footprint and cache-friendly layout.
 * 
 * Uses primitives to avoid boxing overhead in the hot path.
 * All fields are final for thread-safety and JIT optimization.
 * 
 * @param orderId     Unique order identifier
 * @param symbol      Trading symbol
 * @param side        Order side: 1=Buy, 2=Sell
 * @param price       Order price (in minimum price increments)
 * @param quantity    Remaining quantity (unfilled)
 * @param timestamp   Order entry timestamp in nanoseconds
 * @param orderType   Order type: 1=Market, 2=Limit, 3=Stop, 4=StopLimit
 * @param timeInForce Time in force: 0=GTC, 1=IOC, 2=FOK, 3=DAY
 */
public record Order(
        long orderId,
        String symbol,
        byte side,
        long price,
        long quantity,
        long timestamp,
        byte orderType,
        byte timeInForce) {
    
    // Order Side constants
    public static final byte SIDE_BUY = 1;
    public static final byte SIDE_SELL = 2;
    
    // Order Type constants
    public static final byte TYPE_MARKET = 1;
    public static final byte TYPE_LIMIT = 2;
    public static final byte TYPE_STOP = 3;
    public static final byte TYPE_STOP_LIMIT = 4;
    
    // Time In Force constants
    public static final byte TIF_GTC = 0; // Good Till Cancel
    public static final byte TIF_IOC = 1; // Immediate Or Cancel
    public static final byte TIF_FOK = 2; // Fill Or Kill
    public static final byte TIF_DAY = 3; // Day order
    
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
        return new Order(orderId, symbol, side, price, newQuantity, timestamp, orderType, timeInForce);
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
    
    /**
     * Checks if this is an IOC (Immediate-Or-Cancel) order.
     * 
     * @return true if IOC order
     */
    public boolean isIOC() {
        return timeInForce == TIF_IOC;
    }
    
    /**
     * Checks if this is a FOK (Fill-Or-Kill) order.
     * 
     * @return true if FOK order
     */
    public boolean isFOK() {
        return timeInForce == TIF_FOK;
    }
    
    /**
     * Checks if this is a MARKET order.
     * 
     * @return true if market order
     */
    public boolean isMarketOrder() {
        return orderType == TYPE_MARKET;
    }
}
