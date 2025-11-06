package com.thelastwar.ems;

/**
 * StrategyState represents the lifecycle state of an execution strategy.
 */
public enum StrategyState {
    /** Strategy has been created but not yet started */
    CREATED,
    
    /** Strategy is actively generating child orders */
    RUNNING,
    
    /** Strategy execution is temporarily paused */
    PAUSED,
    
    /** Strategy has been stopped and cannot be restarted */
    STOPPED,
    
    /** Strategy has completed execution (all quantity filled) */
    COMPLETED,
    
    /** Strategy encountered an error and cannot continue */
    ERROR
}
