package com.thelastwar.eventbus.model;

/**
 * Immutable Order Event record for representing order lifecycle events.
 * Uses Java record for compact, efficient representation optimized for low-latency systems.
 * 
 * Performance characteristics:
 * - Compact memory layout via record
 * - All fields are primitives or immutable strings (no autoboxing in hot path)
 * - Binary serialization target: < 200 ns
 * - Suitable for deterministic replay
 * 
 * @param orderId       Unique order identifier
 * @param symbol        Trading symbol/instrument
 * @param side          Order side: 1=Buy, 2=Sell
 * @param orderType     Order type: 1=Market, 2=Limit, 3=Stop, 4=StopLimit
 * @param quantity      Order quantity (in lots or shares)
 * @param price         Order price (in minimum price increments, e.g., cents or ticks)
 * @param timestamp     Event timestamp in nanoseconds
 * @param status        Order status: 0=New, 1=PartiallyFilled, 2=Filled, 3=Cancelled, 4=Rejected
 * @param account       Trading account identifier
 * @param exchange      Exchange identifier
 * @param timeInForce   Time in force: 0=GTC, 1=IOC, 2=FOK, 3=DAY
 */
public record OrderEvent(
        long orderId,
        String symbol,
        byte side,
        byte orderType,
        long quantity,
        long price,
        long timestamp,
        byte status,
        long account,
        int exchange,
        byte timeInForce) {
    
    // Order Side constants
    public static final byte SIDE_BUY = 1;
    public static final byte SIDE_SELL = 2;
    
    // Order Type constants
    public static final byte TYPE_MARKET = 1;
    public static final byte TYPE_LIMIT = 2;
    public static final byte TYPE_STOP = 3;
    public static final byte TYPE_STOP_LIMIT = 4;
    
    // Order Status constants
    public static final byte STATUS_NEW = 0;
    public static final byte STATUS_PARTIALLY_FILLED = 1;
    public static final byte STATUS_FILLED = 2;
    public static final byte STATUS_CANCELLED = 3;
    public static final byte STATUS_REJECTED = 4;
    
    // Time In Force constants
    public static final byte TIF_GTC = 0; // Good Till Cancel
    public static final byte TIF_IOC = 1; // Immediate Or Cancel
    public static final byte TIF_FOK = 2; // Fill Or Kill
    public static final byte TIF_DAY = 3; // Day order
    
    /**
     * Compact constructor with validation.
     */
    public OrderEvent {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (side != SIDE_BUY && side != SIDE_SELL) {
            throw new IllegalArgumentException("Invalid side: " + side);
        }
        if (orderType < TYPE_MARKET || orderType > TYPE_STOP_LIMIT) {
            throw new IllegalArgumentException("Invalid order type: " + orderType);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative");
        }
    }
    
    /**
     * Factory method for creating a new order event.
     * 
     * @param orderId   Order identifier
     * @param symbol    Trading symbol
     * @param side      Buy or Sell
     * @param orderType Market, Limit, etc.
     * @param quantity  Order quantity
     * @param price     Order price (0 for market orders)
     * @param account   Account identifier
     * @param exchange  Exchange identifier
     * @return new OrderEvent with current timestamp and NEW status
     */
    public static OrderEvent newOrder(long orderId, String symbol, byte side, byte orderType,
                                      long quantity, long price, long account, int exchange) {
        return new OrderEvent(orderId, symbol, side, orderType, quantity, price,
                System.nanoTime(), STATUS_NEW, account, exchange, TIF_GTC);
    }
    
    /**
     * Factory method for creating a new order event with specified time-in-force.
     * 
     * @param orderId     Order identifier
     * @param symbol      Trading symbol
     * @param side        Buy or Sell
     * @param orderType   Market, Limit, etc.
     * @param quantity    Order quantity
     * @param price       Order price (0 for market orders)
     * @param account     Account identifier
     * @param exchange    Exchange identifier
     * @param timeInForce Time in force (GTC, IOC, FOK, DAY)
     * @return new OrderEvent with current timestamp and NEW status
     */
    public static OrderEvent newOrder(long orderId, String symbol, byte side, byte orderType,
                                      long quantity, long price, long account, int exchange, byte timeInForce) {
        return new OrderEvent(orderId, symbol, side, orderType, quantity, price,
                System.nanoTime(), STATUS_NEW, account, exchange, timeInForce);
    }
    
    /**
     * Creates a copy with updated status.
     * 
     * @param newStatus Updated status
     * @return new OrderEvent with updated status and current timestamp
     */
    public OrderEvent withStatus(byte newStatus) {
        return new OrderEvent(orderId, symbol, side, orderType, quantity, price,
                System.nanoTime(), newStatus, account, exchange, timeInForce);
    }
    
    /**
     * Creates a copy with updated quantity (for partial fills).
     * 
     * @param newQuantity Updated quantity
     * @return new OrderEvent with updated quantity and current timestamp
     */
    public OrderEvent withQuantity(long newQuantity) {
        return new OrderEvent(orderId, symbol, side, orderType, newQuantity, price,
                System.nanoTime(), status, account, exchange, timeInForce);
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
     * Checks if order is in a terminal state.
     * 
     * @return true if order is filled, cancelled, or rejected
     */
    public boolean isTerminal() {
        return status == STATUS_FILLED || status == STATUS_CANCELLED || status == STATUS_REJECTED;
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
