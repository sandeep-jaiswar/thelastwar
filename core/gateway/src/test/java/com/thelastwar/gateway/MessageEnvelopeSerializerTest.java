package com.thelastwar.gateway;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageEnvelopeSerializer.
 */
class MessageEnvelopeSerializerTest {
    
    private MessageEnvelope envelope;
    
    @BeforeEach
    void setUp() {
        envelope = new MessageEnvelope();
    }
    
    @Test
    void testSerializeDeserializeSimple() {
        // Setup envelope
        envelope.setProtocolType(MessageEnvelope.ProtocolType.FIX);
        envelope.setCorrelationId(12345L);
        envelope.setTimestamp(System.nanoTime());
        envelope.setClientId("client-1");
        envelope.setPayload("Hello World".getBytes(), 0, 11);
        
        // Serialize
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
        int bytesWritten = MessageEnvelopeSerializer.serialize(envelope, buffer);
        assertTrue(bytesWritten > 0);
        
        // Deserialize
        MessageEnvelope deserialized = new MessageEnvelope();
        int bytesRead = MessageEnvelopeSerializer.deserialize(buffer, 0, deserialized);
        
        assertEquals(bytesWritten, bytesRead);
        assertEquals(envelope.getProtocolType(), deserialized.getProtocolType());
        assertEquals(envelope.getCorrelationId(), deserialized.getCorrelationId());
        assertEquals(envelope.getTimestamp(), deserialized.getTimestamp());
        assertEquals(envelope.getClientId(), deserialized.getClientId());
        assertEquals(envelope.getPayloadLength(), deserialized.getPayloadLength());
        assertArrayEquals(envelope.getPayloadAsBytes(), deserialized.getPayloadAsBytes());
    }
    
    @Test
    void testSerializeDeserializeAllProtocolTypes() {
        for (MessageEnvelope.ProtocolType type : MessageEnvelope.ProtocolType.values()) {
            envelope.reset();
            envelope.setProtocolType(type);
            envelope.setCorrelationId(999L);
            envelope.setPayload("test".getBytes(), 0, 4);
            
            UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
            MessageEnvelopeSerializer.serialize(envelope, buffer);
            
            MessageEnvelope deserialized = new MessageEnvelope();
            MessageEnvelopeSerializer.deserialize(buffer, 0, deserialized);
            
            assertEquals(type, deserialized.getProtocolType());
        }
    }
    
    @Test
    void testSerializeDeserializeWithNullClientId() {
        envelope.setProtocolType(MessageEnvelope.ProtocolType.REST);
        envelope.setCorrelationId(456L);
        envelope.setTimestamp(789L);
        envelope.setClientId(null);
        envelope.setPayload("data".getBytes(), 0, 4);
        
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
        int bytesWritten = MessageEnvelopeSerializer.serialize(envelope, buffer);
        
        MessageEnvelope deserialized = new MessageEnvelope();
        int bytesRead = MessageEnvelopeSerializer.deserialize(buffer, 0, deserialized);
        
        assertEquals(bytesWritten, bytesRead);
        assertNull(deserialized.getClientId());
        assertEquals(envelope.getCorrelationId(), deserialized.getCorrelationId());
    }
    
    @Test
    void testSerializeDeserializeEmptyPayload() {
        envelope.setProtocolType(MessageEnvelope.ProtocolType.WEBSOCKET);
        envelope.setCorrelationId(111L);
        envelope.setTimestamp(222L);
        envelope.setClientId("client-2");
        // No payload set
        
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
        int bytesWritten = MessageEnvelopeSerializer.serialize(envelope, buffer);
        
        MessageEnvelope deserialized = new MessageEnvelope();
        int bytesRead = MessageEnvelopeSerializer.deserialize(buffer, 0, deserialized);
        
        assertEquals(bytesWritten, bytesRead);
        assertEquals(0, deserialized.getPayloadLength());
    }
    
    @Test
    void testSerializeDeserializeLargePayload() {
        // Create large payload
        byte[] largePayload = new byte[4096];
        for (int i = 0; i < largePayload.length; i++) {
            largePayload[i] = (byte) (i % 256);
        }
        
        envelope.setProtocolType(MessageEnvelope.ProtocolType.FIX);
        envelope.setCorrelationId(999999L);
        envelope.setTimestamp(123456789L);
        envelope.setClientId("large-client");
        envelope.setPayload(largePayload, 0, largePayload.length);
        
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(8192));
        int bytesWritten = MessageEnvelopeSerializer.serialize(envelope, buffer);
        
        MessageEnvelope deserialized = new MessageEnvelope(8192);
        int bytesRead = MessageEnvelopeSerializer.deserialize(buffer, 0, deserialized);
        
        assertEquals(bytesWritten, bytesRead);
        assertEquals(largePayload.length, deserialized.getPayloadLength());
        assertArrayEquals(largePayload, deserialized.getPayloadAsBytes());
    }
    
    @Test
    void testSerializeDeserializeWithByteBuffer() {
        envelope.setProtocolType(MessageEnvelope.ProtocolType.REST);
        envelope.setCorrelationId(555L);
        envelope.setTimestamp(666L);
        envelope.setClientId("test-client");
        envelope.setPayload("ByteBuffer test".getBytes(), 0, 15);
        
        ByteBuffer buffer = ByteBuffer.allocate(512);
        int bytesWritten = MessageEnvelopeSerializer.serialize(envelope, buffer);
        
        buffer.flip();
        
        MessageEnvelope deserialized = new MessageEnvelope();
        int bytesRead = MessageEnvelopeSerializer.deserialize(buffer, deserialized);
        
        assertEquals(bytesWritten, bytesRead);
        assertEquals(envelope.getProtocolType(), deserialized.getProtocolType());
        assertEquals(envelope.getCorrelationId(), deserialized.getCorrelationId());
        assertArrayEquals(envelope.getPayloadAsBytes(), deserialized.getPayloadAsBytes());
    }
    
    @Test
    void testGetSerializedSize() {
        envelope.setProtocolType(MessageEnvelope.ProtocolType.FIX);
        envelope.setCorrelationId(123L);
        envelope.setTimestamp(456L);
        envelope.setClientId("client-123");
        envelope.setPayload("payload data".getBytes(), 0, 12);
        
        int calculatedSize = MessageEnvelopeSerializer.getSerializedSize(envelope);
        
        // Serialize and check actual size
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(1024));
        int actualSize = MessageEnvelopeSerializer.serialize(envelope, buffer);
        
        assertEquals(calculatedSize, actualSize);
    }
    
    @Test
    void testGetSerializedSizeWithNullClientId() {
        envelope.setProtocolType(MessageEnvelope.ProtocolType.REST);
        envelope.setCorrelationId(789L);
        envelope.setClientId(null);
        envelope.setPayload("test".getBytes(), 0, 4);
        
        int calculatedSize = MessageEnvelopeSerializer.getSerializedSize(envelope);
        
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
        int actualSize = MessageEnvelopeSerializer.serialize(envelope, buffer);
        
        assertEquals(calculatedSize, actualSize);
    }
    
    @Test
    void testMultipleSerializeDeserialize() {
        // Create multiple envelopes
        MessageEnvelope[] envelopes = new MessageEnvelope[5];
        for (int i = 0; i < 5; i++) {
            envelopes[i] = new MessageEnvelope();
            envelopes[i].setProtocolType(MessageEnvelope.ProtocolType.values()[i % 3]);
            envelopes[i].setCorrelationId(i * 100L);
            envelopes[i].setTimestamp(i * 1000L);
            envelopes[i].setClientId("client-" + i);
            envelopes[i].setPayload(("payload-" + i).getBytes(), 0, ("payload-" + i).length());
        }
        
        // Serialize all
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(4096));
        int offset = 0;
        for (MessageEnvelope env : envelopes) {
            int written = MessageEnvelopeSerializer.serialize(env, buffer, offset);
            offset += written;
        }
        
        // Deserialize all
        offset = 0;
        for (int i = 0; i < 5; i++) {
            MessageEnvelope deserialized = new MessageEnvelope();
            int read = MessageEnvelopeSerializer.deserialize(buffer, offset, deserialized);
            offset += read;
            
            assertEquals(envelopes[i].getProtocolType(), deserialized.getProtocolType());
            assertEquals(envelopes[i].getCorrelationId(), deserialized.getCorrelationId());
            assertEquals(envelopes[i].getClientId(), deserialized.getClientId());
        }
    }
    
    @Test
    void testSerializeDeserializeUnicodeClientId() {
        envelope.setProtocolType(MessageEnvelope.ProtocolType.FIX);
        envelope.setCorrelationId(321L);
        envelope.setClientId("客户端-123"); // Unicode characters
        envelope.setPayload("test".getBytes(), 0, 4);
        
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(512));
        int bytesWritten = MessageEnvelopeSerializer.serialize(envelope, buffer);
        
        MessageEnvelope deserialized = new MessageEnvelope();
        int bytesRead = MessageEnvelopeSerializer.deserialize(buffer, 0, deserialized);
        
        assertEquals(bytesWritten, bytesRead);
        assertEquals("客户端-123", deserialized.getClientId());
    }
    
    @Test
    void testSerializeDeserializeZeroValues() {
        envelope.setProtocolType(MessageEnvelope.ProtocolType.FIX);
        envelope.setCorrelationId(0L);
        envelope.setTimestamp(0L);
        envelope.setClientId("");
        envelope.setPayload(new byte[0], 0, 0);
        
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
        int bytesWritten = MessageEnvelopeSerializer.serialize(envelope, buffer);
        
        MessageEnvelope deserialized = new MessageEnvelope();
        int bytesRead = MessageEnvelopeSerializer.deserialize(buffer, 0, deserialized);
        
        assertEquals(bytesWritten, bytesRead);
        assertEquals(0L, deserialized.getCorrelationId());
        assertEquals(0L, deserialized.getTimestamp());
        assertEquals("", deserialized.getClientId());
        assertEquals(0, deserialized.getPayloadLength());
    }
    
    @Test
    void testSerializeDeserializeMaxValues() {
        envelope.setProtocolType(MessageEnvelope.ProtocolType.WEBSOCKET);
        envelope.setCorrelationId(Long.MAX_VALUE);
        envelope.setTimestamp(Long.MAX_VALUE);
        envelope.setClientId("max-client");
        envelope.setPayload("max".getBytes(), 0, 3);
        
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
        int bytesWritten = MessageEnvelopeSerializer.serialize(envelope, buffer);
        
        MessageEnvelope deserialized = new MessageEnvelope();
        int bytesRead = MessageEnvelopeSerializer.deserialize(buffer, 0, deserialized);
        
        assertEquals(bytesWritten, bytesRead);
        assertEquals(Long.MAX_VALUE, deserialized.getCorrelationId());
        assertEquals(Long.MAX_VALUE, deserialized.getTimestamp());
    }
}
