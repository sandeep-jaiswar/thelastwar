package com.thelastwar.ems;

import java.time.Duration;
import java.time.Instant;

/**
 * StrategyMetrics tracks performance metrics for an execution strategy.
 * 
 * This immutable record provides insights into strategy execution quality.
 */
public record StrategyMetrics(
    /** Strategy ID */
    String strategyId,
    
    /** Strategy type */
    StrategyType strategyType,
    
    /** Start time */
    Instant startTime,
    
    /** End time (null if still running) */
    Instant endTime,
    
    /** Total parent order quantity */
    long totalQuantity,
    
    /** Quantity filled so far */
    long filledQuantity,
    
    /** Number of child orders generated */
    int childOrdersGenerated,
    
    /** Number of child orders filled */
    int childOrdersFilled,
    
    /** Number of child orders partially filled */
    int childOrdersPartiallyFilled,
    
    /** Number of child orders rejected */
    int childOrdersRejected,
    
    /** Average fill price */
    long averageFillPrice,
    
    /** Volume-weighted average price achieved */
    long vwap,
    
    /** Best price achieved */
    long bestPrice,
    
    /** Worst price achieved */
    long worstPrice,
    
    /** Total slippage (difference from arrival price) */
    long totalSlippage,
    
    /** Number of market ticks processed */
    long ticksProcessed,
    
    /** Number of fills processed */
    long fillsProcessed
) {
    
    /**
     * Calculate the fill rate (percentage of order filled).
     * 
     * @return Fill rate as a decimal (0.0 to 1.0)
     */
    public double getFillRate() {
        return totalQuantity > 0 ? (double) filledQuantity / totalQuantity : 0.0;
    }
    
    /**
     * Calculate the child order success rate.
     * 
     * @return Success rate as a decimal (0.0 to 1.0)
     */
    public double getChildOrderSuccessRate() {
        int total = childOrdersFilled + childOrdersPartiallyFilled + childOrdersRejected;
        return total > 0 ? (double) childOrdersFilled / total : 0.0;
    }
    
    /**
     * Calculate execution duration.
     * 
     * @return Duration, or null if not yet ended
     */
    public Duration getExecutionDuration() {
        if (endTime == null) {
            return Duration.between(startTime, Instant.now());
        }
        return Duration.between(startTime, endTime);
    }
    
    /**
     * Calculate average slippage per share.
     * 
     * @return Average slippage
     */
    public long getAverageSlippage() {
        return filledQuantity > 0 ? totalSlippage / filledQuantity : 0;
    }
}
