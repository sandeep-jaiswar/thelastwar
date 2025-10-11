package com.thelastwar.gateway;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * Unified message envelope for all gateway implementations (FIX, REST, WebSocket).
 * 
 * This class provides a common message format that can be used across different
 * protocol gateways, enabling consistent message handling and routing.
 * 
 * Key features:
 * - Zero-copy message payload using Agrona DirectBuffer
 * - Protocol-agnostic design supporting FIX, REST, and WebSocket
 * - Correlation ID for request-response tracking
 * - Metadata for timestamps and client identification
 * - Object pooling support to eliminate GC pressure
 * 
 * Performance targets:
 * - Serialization: &lt; 5 µs for small payloads (128 bytes)
 * - Zero heap allocation in hot path after warmup
 * - Thread-safe for concurrent access
 */
public class MessageEnvelope {
    
    /**
     * Protocol type enumeration.
     */
    public enum ProtocolType {
        FIX,
        REST,
        WEBSOCKET
    }
    
    // Message fields
    private ProtocolType protocolType;
    private long correlationId;
    private MutableDirectBuffer payload;
    private int payloadLength;
    private long timestamp;
    private String clientId;
    
    /**
     * Creates a new MessageEnvelope.
     * Default constructor for object pooling.
     */
    public MessageEnvelope() {
        this.payload = new UnsafeBuffer(ByteBuffer.allocateDirect(8192)); // 8KB default
    }
    
    /**
     * Creates a new MessageEnvelope with specified buffer capacity.
     * 
     * @param bufferCapacity Initial buffer capacity in bytes
     */
    public MessageEnvelope(int bufferCapacity) {
        this.payload = new UnsafeBuffer(ByteBuffer.allocateDirect(bufferCapacity));
    }
    
    /**
     * Resets the envelope for reuse (for object pooling).
     */
    public void reset() {
        this.protocolType = null;
        this.correlationId = 0;
        this.payloadLength = 0;
        this.timestamp = 0;
        this.clientId = null;
    }
    
    /**
     * Sets the protocol type.
     * 
     * @param protocolType Protocol type
     * @return this for fluent API
     */
    public MessageEnvelope setProtocolType(ProtocolType protocolType) {
        this.protocolType = protocolType;
        return this;
    }
    
    /**
     * Gets the protocol type.
     * 
     * @return Protocol type
     */
    public ProtocolType getProtocolType() {
        return protocolType;
    }
    
    /**
     * Sets the correlation ID for request-response tracking.
     * 
     * @param correlationId Correlation ID
     * @return this for fluent API
     */
    public MessageEnvelope setCorrelationId(long correlationId) {
        this.correlationId = correlationId;
        return this;
    }
    
    /**
     * Gets the correlation ID.
     * 
     * @return Correlation ID
     */
    public long getCorrelationId() {
        return correlationId;
    }
    
    /**
     * Sets the payload from a byte array.
     * 
     * @param data Byte array containing the payload
     * @param offset Offset in the byte array
     * @param length Length of the payload
     * @return this for fluent API
     */
    public MessageEnvelope setPayload(byte[] data, int offset, int length) {
        ensureCapacity(length);
        payload.putBytes(0, data, offset, length);
        this.payloadLength = length;
        return this;
    }
    
    /**
     * Sets the payload from a DirectBuffer.
     * 
     * @param buffer Source buffer
     * @param offset Offset in the source buffer
     * @param length Length of the payload
     * @return this for fluent API
     */
    public MessageEnvelope setPayload(DirectBuffer buffer, int offset, int length) {
        ensureCapacity(length);
        payload.putBytes(0, buffer, offset, length);
        this.payloadLength = length;
        return this;
    }
    
    /**
     * Gets the payload buffer (read-only).
     * 
     * @return DirectBuffer containing the payload
     */
    public DirectBuffer getPayload() {
        return payload;
    }
    
    /**
     * Gets the payload length in bytes.
     * 
     * @return Payload length
     */
    public int getPayloadLength() {
        return payloadLength;
    }
    
    /**
     * Sets the timestamp (typically message creation or reception time).
     * 
     * @param timestamp Timestamp in nanoseconds
     * @return this for fluent API
     */
    public MessageEnvelope setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }
    
    /**
     * Gets the timestamp.
     * 
     * @return Timestamp in nanoseconds
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Sets the client ID.
     * 
     * @param clientId Client identifier
     * @return this for fluent API
     */
    public MessageEnvelope setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }
    
    /**
     * Gets the client ID.
     * 
     * @return Client identifier
     */
    public String getClientId() {
        return clientId;
    }
    
    /**
     * Ensures the payload buffer has at least the specified capacity.
     * Expands the buffer if necessary.
     * 
     * @param requiredCapacity Required capacity in bytes
     */
    private void ensureCapacity(int requiredCapacity) {
        if (payload.capacity() < requiredCapacity) {
            // Allocate new buffer with double capacity or required size, whichever is larger
            int newCapacity = Math.max(requiredCapacity, payload.capacity() * 2);
            MutableDirectBuffer newBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(newCapacity));
            // Copy existing data if any
            if (payloadLength > 0) {
                newBuffer.putBytes(0, payload, 0, payloadLength);
            }
            payload = newBuffer;
        }
    }
    
    /**
     * Copies payload data to a byte array.
     * 
     * @return Byte array containing the payload
     */
    public byte[] getPayloadAsBytes() {
        byte[] result = new byte[payloadLength];
        payload.getBytes(0, result);
        return result;
    }
    
    @Override
    public String toString() {
        return "MessageEnvelope{" +
                "protocolType=" + protocolType +
                ", correlationId=" + correlationId +
                ", payloadLength=" + payloadLength +
                ", timestamp=" + timestamp +
                ", clientId='" + clientId + '\'' +
                '}';
    }
}
