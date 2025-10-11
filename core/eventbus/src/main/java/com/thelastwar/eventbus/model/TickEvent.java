package com.thelastwar.eventbus.model;

/**
 * TickEvent represents a market data update (tick) from the feed handler.
 * Used for updating reference prices and triggering stop orders in the matching engine.
 * 
 * Performance characteristics:
 * - Compact memory layout via record
 * - All fields are primitives or immutable strings
 * - Zero allocation in hot path
 * - Suitable for high-frequency market data processing
 * 
 * @param symbol        Trading symbol/instrument
 * @param bidPrice      Best bid price (in minimum price increments)
 * @param askPrice      Best ask price (in minimum price increments)
 * @param lastPrice     Last trade price (0 if no recent trade)
 * @param bidSize       Size available at best bid
 * @param askSize       Size available at best ask
 * @param timestamp     Tick timestamp in nanoseconds
 * @param sequenceNum   Sequence number from feed handler
 * @param exchange      Exchange identifier
 */
public record TickEvent(
        String symbol,
        long bidPrice,
        long askPrice,
        long lastPrice,
        long bidSize,
        long askSize,
        long timestamp,
        long sequenceNum,
        int exchange) {
    
    /**
     * Compact constructor with validation.
     */
    public TickEvent {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (bidPrice < 0) {
            throw new IllegalArgumentException("Bid price cannot be negative");
        }
        if (askPrice < 0) {
            throw new IllegalArgumentException("Ask price cannot be negative");
        }
        if (lastPrice < 0) {
            throw new IllegalArgumentException("Last price cannot be negative");
        }
        if (bidSize < 0) {
            throw new IllegalArgumentException("Bid size cannot be negative");
        }
        if (askSize < 0) {
            throw new IllegalArgumentException("Ask size cannot be negative");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative");
        }
        if (sequenceNum < 0) {
            throw new IllegalArgumentException("Sequence number cannot be negative");
        }
        if (bidPrice > 0 && askPrice > 0 && bidPrice >= askPrice) {
            throw new IllegalArgumentException("Bid price must be less than ask price when both are set");
        }
    }
    
    /**
     * Factory method for creating a tick event.
     * 
     * @param symbol      Trading symbol
     * @param bidPrice    Best bid price
     * @param askPrice    Best ask price
     * @param lastPrice   Last trade price
     * @param bidSize     Bid size
     * @param askSize     Ask size
     * @param sequenceNum Sequence number
     * @param exchange    Exchange identifier
     * @return new TickEvent with current timestamp
     */
    public static TickEvent create(String symbol, long bidPrice, long askPrice, long lastPrice,
                                   long bidSize, long askSize, long sequenceNum, int exchange) {
        return new TickEvent(symbol, bidPrice, askPrice, lastPrice, bidSize, askSize,
                            System.nanoTime(), sequenceNum, exchange);
    }
    
    /**
     * Gets the spread between bid and ask.
     * 
     * @return spread in price increments (0 if either side is missing)
     */
    public long getSpread() {
        if (bidPrice == 0 || askPrice == 0) {
            return 0;
        }
        return askPrice - bidPrice;
    }
    
    /**
     * Gets the mid-price between bid and ask.
     * 
     * @return mid-price (0 if either side is missing)
     */
    public long getMidPrice() {
        if (bidPrice == 0 || askPrice == 0) {
            return 0;
        }
        return (bidPrice + askPrice) / 2;
    }
    
    /**
     * Checks if this is a valid two-sided quote.
     * 
     * @return true if both bid and ask are present
     */
    public boolean isTwoSided() {
        return bidPrice > 0 && askPrice > 0;
    }
}
