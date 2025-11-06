package com.thelastwar.ems;

/**
 * StrategyException is thrown when a strategy encounters an error during execution.
 */
public class StrategyException extends Exception {
    
    public StrategyException(String message) {
        super(message);
    }
    
    public StrategyException(String message, Throwable cause) {
        super(message, cause);
    }
}
