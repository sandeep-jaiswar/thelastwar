package com.thelastwar.wsgateway;

import java.util.List;

/**
 * Represents a message from/to WebSocket clients.
 * 
 * Supports:
 * - Subscription requests (order IDs, symbols)
 * - Unsubscription requests
 * - Heartbeat/ping-pong
 * - Event broadcasts
 */
public class WebSocketMessage {
    
    /**
     * Message types for client-server communication.
     */
    public enum MessageType {
        SUBSCRIBE_ORDER,
        UNSUBSCRIBE_ORDER,
        SUBSCRIBE_SYMBOL,
        UNSUBSCRIBE_SYMBOL,
        HEARTBEAT,
        PING,
        PONG,
        EVENT,
        ERROR,
        ACK
    }
    
    private final MessageType type;
    private final Object payload;
    
    public WebSocketMessage(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }
    
    public MessageType getType() {
        return type;
    }
    
    public Object getPayload() {
        return payload;
    }
    
    /**
     * Creates a subscribe to order message.
     */
    public static WebSocketMessage subscribeOrder(long orderId) {
        return new WebSocketMessage(MessageType.SUBSCRIBE_ORDER, orderId);
    }
    
    /**
     * Creates an unsubscribe from order message.
     */
    public static WebSocketMessage unsubscribeOrder(long orderId) {
        return new WebSocketMessage(MessageType.UNSUBSCRIBE_ORDER, orderId);
    }
    
    /**
     * Creates a subscribe to symbol message.
     */
    public static WebSocketMessage subscribeSymbol(String symbol) {
        return new WebSocketMessage(MessageType.SUBSCRIBE_SYMBOL, symbol);
    }
    
    /**
     * Creates an unsubscribe from symbol message.
     */
    public static WebSocketMessage unsubscribeSymbol(String symbol) {
        return new WebSocketMessage(MessageType.UNSUBSCRIBE_SYMBOL, symbol);
    }
    
    /**
     * Creates a heartbeat message.
     */
    public static WebSocketMessage heartbeat() {
        return new WebSocketMessage(MessageType.HEARTBEAT, System.nanoTime());
    }
    
    /**
     * Creates a ping message.
     */
    public static WebSocketMessage ping() {
        return new WebSocketMessage(MessageType.PING, System.nanoTime());
    }
    
    /**
     * Creates a pong message.
     */
    public static WebSocketMessage pong(long pingTimestamp) {
        return new WebSocketMessage(MessageType.PONG, pingTimestamp);
    }
    
    /**
     * Creates an event message.
     */
    public static WebSocketMessage event(Object eventData) {
        return new WebSocketMessage(MessageType.EVENT, eventData);
    }
    
    /**
     * Creates an error message.
     */
    public static WebSocketMessage error(String errorMessage) {
        return new WebSocketMessage(MessageType.ERROR, errorMessage);
    }
    
    /**
     * Creates an acknowledgment message.
     */
    public static WebSocketMessage ack(String message) {
        return new WebSocketMessage(MessageType.ACK, message);
    }
    
    @Override
    public String toString() {
        return "WebSocketMessage{type=" + type + ", payload=" + payload + "}";
    }
}
