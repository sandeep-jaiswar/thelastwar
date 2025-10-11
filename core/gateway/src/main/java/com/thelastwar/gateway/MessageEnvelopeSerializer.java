package com.thelastwar.gateway;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * High-performance serializer/deserializer for MessageEnvelope.
 * 
 * Binary format (little-endian):
 * - Protocol type (1 byte): 0=FIX, 1=REST, 2=WEBSOCKET
 * - Correlation ID (8 bytes)
 * - Timestamp (8 bytes)
 * - Client ID length (4 bytes)
 * - Client ID (variable bytes, UTF-8)
 * - Payload length (4 bytes)
 * - Payload (variable bytes)
 * 
 * Performance targets:
 * - Serialization: &lt; 5 µs for small payloads (128 bytes)
 * - Deserialization: &lt; 5 µs for small payloads
 * - Zero heap allocation in hot path
 * 
 * Thread-safety: This class is thread-safe (stateless utility).
 */
public class MessageEnvelopeSerializer {
    
    // Offsets in the binary format
    private static final int PROTOCOL_TYPE_OFFSET = 0;
    private static final int CORRELATION_ID_OFFSET = 1;
    private static final int TIMESTAMP_OFFSET = 9;
    private static final int CLIENT_ID_LENGTH_OFFSET = 17;
    private static final int CLIENT_ID_OFFSET = 21;
    
    // Header size (fixed part before variable-length fields)
    private static final int FIXED_HEADER_SIZE = 21;
    
    /**
     * Serializes a MessageEnvelope to a DirectBuffer.
     * 
     * @param envelope The envelope to serialize
     * @param buffer Target buffer for serialization
     * @param offset Offset in the buffer to start writing
     * @return Number of bytes written
     */
    public static int serialize(MessageEnvelope envelope, MutableDirectBuffer buffer, int offset) {
        int currentOffset = offset;
        
        // Protocol type (1 byte)
        buffer.putByte(currentOffset, (byte) envelope.getProtocolType().ordinal());
        currentOffset += 1;
        
        // Correlation ID (8 bytes)
        buffer.putLong(currentOffset, envelope.getCorrelationId());
        currentOffset += 8;
        
        // Timestamp (8 bytes)
        buffer.putLong(currentOffset, envelope.getTimestamp());
        currentOffset += 8;
        
        // Client ID
        byte[] clientIdBytes = envelope.getClientId() != null 
            ? envelope.getClientId().getBytes(StandardCharsets.UTF_8) 
            : null;
        int clientIdLength = clientIdBytes != null ? clientIdBytes.length : -1;
        buffer.putInt(currentOffset, clientIdLength);
        currentOffset += 4;
        if (clientIdLength > 0) {
            buffer.putBytes(currentOffset, clientIdBytes);
            currentOffset += clientIdLength;
        }
        
        // Payload length (4 bytes)
        buffer.putInt(currentOffset, envelope.getPayloadLength());
        currentOffset += 4;
        
        // Payload
        if (envelope.getPayloadLength() > 0) {
            buffer.putBytes(currentOffset, envelope.getPayload(), 0, envelope.getPayloadLength());
            currentOffset += envelope.getPayloadLength();
        }
        
        return currentOffset - offset;
    }
    
    /**
     * Serializes a MessageEnvelope to a DirectBuffer.
     * 
     * @param envelope The envelope to serialize
     * @param buffer Target buffer for serialization
     * @return Number of bytes written
     */
    public static int serialize(MessageEnvelope envelope, MutableDirectBuffer buffer) {
        return serialize(envelope, buffer, 0);
    }
    
    /**
     * Serializes a MessageEnvelope to a ByteBuffer.
     * 
     * @param envelope The envelope to serialize
     * @param buffer Target ByteBuffer for serialization
     * @return Number of bytes written
     */
    public static int serialize(MessageEnvelope envelope, ByteBuffer buffer) {
        UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);
        int length = serialize(envelope, unsafeBuffer);
        buffer.position(buffer.position() + length);
        return length;
    }
    
    /**
     * Deserializes a MessageEnvelope from a DirectBuffer.
     * 
     * @param buffer Source buffer containing serialized data
     * @param offset Offset in the buffer
     * @param envelope Target envelope to populate (reused for zero-copy)
     * @return Number of bytes read
     */
    public static int deserialize(DirectBuffer buffer, int offset, MessageEnvelope envelope) {
        int currentOffset = offset;
        
        // Protocol type (1 byte)
        byte protocolTypeOrdinal = buffer.getByte(currentOffset);
        envelope.setProtocolType(MessageEnvelope.ProtocolType.values()[protocolTypeOrdinal]);
        currentOffset += 1;
        
        // Correlation ID (8 bytes)
        envelope.setCorrelationId(buffer.getLong(currentOffset));
        currentOffset += 8;
        
        // Timestamp (8 bytes)
        envelope.setTimestamp(buffer.getLong(currentOffset));
        currentOffset += 8;
        
        // Client ID
        int clientIdLength = buffer.getInt(currentOffset);
        currentOffset += 4;
        if (clientIdLength > 0) {
            byte[] clientIdBytes = new byte[clientIdLength];
            buffer.getBytes(currentOffset, clientIdBytes);
            envelope.setClientId(new String(clientIdBytes, StandardCharsets.UTF_8));
            currentOffset += clientIdLength;
        } else if (clientIdLength == 0) {
            envelope.setClientId("");
        } else {
            envelope.setClientId(null);
        }
        
        // Payload length (4 bytes)
        int payloadLength = buffer.getInt(currentOffset);
        currentOffset += 4;
        
        // Payload
        if (payloadLength > 0) {
            envelope.setPayload(buffer, currentOffset, payloadLength);
            currentOffset += payloadLength;
        }
        
        return currentOffset - offset;
    }
    
    /**
     * Deserializes a MessageEnvelope from a ByteBuffer.
     * 
     * @param buffer Source ByteBuffer containing serialized data
     * @param envelope Target envelope to populate (reused for zero-copy)
     * @return Number of bytes read
     */
    public static int deserialize(ByteBuffer buffer, MessageEnvelope envelope) {
        UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);
        int length = deserialize(unsafeBuffer, buffer.position(), envelope);
        buffer.position(buffer.position() + length);
        return length;
    }
    
    /**
     * Calculates the serialized size of a MessageEnvelope.
     * 
     * @param envelope The envelope to measure
     * @return Size in bytes
     */
    public static int getSerializedSize(MessageEnvelope envelope) {
        int size = FIXED_HEADER_SIZE;
        
        // Client ID length
        if (envelope.getClientId() != null) {
            size += envelope.getClientId().getBytes(StandardCharsets.UTF_8).length;
        }
        
        // Payload length field
        size += 4;
        
        // Payload
        size += envelope.getPayloadLength();
        
        return size;
    }
}
