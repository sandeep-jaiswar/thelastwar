package com.thelastwar.wsgateway;

/**
 * Specific exception type for WebSocketGateway to avoid throwing generic
 * RuntimeException.
 */
public class WebSocketGatewayException extends RuntimeException {
    public WebSocketGatewayException(String message) {
        super(message);
    }

    public WebSocketGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
