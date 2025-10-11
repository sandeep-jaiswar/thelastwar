package com.thelastwar.wsgateway;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;

/**
 * Interface for serializing events to WebSocket messages.
 * Supports both JSON and MessagePack formats.
 */
public interface WebSocketMessageSerializer {
    
    /**
     * Serializes an event to bytes for transmission.
     * 
     * @param event Event to serialize
     * @return Serialized bytes
     * @throws SerializationException if serialization fails
     */
    byte[] serialize(Event event) throws SerializationException;
    
    /**
     * Serializes an OrderEvent to bytes.
     * 
     * @param orderEvent Order event to serialize
     * @return Serialized bytes
     * @throws SerializationException if serialization fails
     */
    byte[] serializeOrderEvent(OrderEvent orderEvent) throws SerializationException;
    
    /**
     * Serializes an ExecutionEvent to bytes.
     * 
     * @param executionEvent Execution event to serialize
     * @return Serialized bytes
     * @throws SerializationException if serialization fails
     */
    byte[] serializeExecutionEvent(ExecutionEvent executionEvent) throws SerializationException;
    
    /**
     * Deserializes a message from the client.
     * 
     * @param data Raw bytes from client
     * @return Deserialized message object
     * @throws SerializationException if deserialization fails
     */
    WebSocketMessage deserialize(byte[] data) throws SerializationException;
    
    /**
     * Gets the format this serializer uses.
     */
    TransportFormat getFormat();
    
    /**
     * Exception thrown when serialization fails.
     */
    class SerializationException extends Exception {
        public SerializationException(String message) {
            super(message);
        }
        
        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
