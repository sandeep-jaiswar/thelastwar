package com.thelastwar.gateway;

/**
 * Exception thrown by gateway operations.
 */
public class GatewayException extends Exception {
    
    public GatewayException(String message) {
        super(message);
    }
    
    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public GatewayException(Throwable cause) {
        super(cause);
    }
}
