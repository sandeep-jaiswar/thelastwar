package com.thelastwar.ems;

/**
 * StrategyType enum defines the types of execution strategies supported by the EMS.
 */
public enum StrategyType {
    /** Time-Weighted Average Price - splits order evenly over time */
    TWAP,
    
    /** Volume-Weighted Average Price - targets market volume distribution */
    VWAP,
    
    /** Immediate-or-Cancel - attempts immediate execution, cancels remainder */
    IOC,
    
    /** Iceberg - shows only a portion of the order, replenishes as filled */
    ICEBERG,
    
    /** Direct Market Access - routes entire order to specific venue */
    DMA
}
