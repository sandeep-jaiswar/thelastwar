package com.thelastwar.ems;

import java.time.Instant;

/**
 * MarketTick represents a market data update containing price and volume information.
 * 
 * This immutable record is used by strategies to make execution decisions based on
 * current market conditions.
 */
public record MarketTick(
    /** Symbol for this tick */
    String symbol,
    
    /** Timestamp of the tick */
    Instant timestamp,
    
    /** Best bid price */
    long bidPrice,
    
    /** Best ask price */
    long askPrice,
    
    /** Bid size */
    long bidSize,
    
    /** Ask size */
    long askSize,
    
    /** Last trade price */
    long lastPrice,
    
    /** Last trade size */
    long lastSize,
    
    /** Cumulative volume for the day */
    long cumulativeVolume
) {
    
    /**
     * Get the mid price (average of bid and ask).
     * 
     * @return Mid price
     */
    public long getMidPrice() {
        return (bidPrice + askPrice) / 2;
    }
    
    /**
     * Get the spread (difference between ask and bid).
     * 
     * @return Spread
     */
    public long getSpread() {
        return askPrice - bidPrice;
    }
}
