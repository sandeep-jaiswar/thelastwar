package com.thelastwar.gateway;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageEnvelope.
 */
class MessageEnvelopeTest {
    
    private MessageEnvelope envelope;
    
    @BeforeEach
    void setUp() {
        envelope = new MessageEnvelope();
    }
    
    @Test
    void testDefaultConstruction() {
        assertNotNull(envelope);
        assertNull(envelope.getProtocolType());
        assertEquals(0, envelope.getCorrelationId());
        assertEquals(0, envelope.getPayloadLength());
        assertEquals(0, envelope.getTimestamp());
        assertNull(envelope.getClientId());
    }
    
    @Test
    void testConstructionWithBufferCapacity() {
        MessageEnvelope env = new MessageEnvelope(4096);
        assertNotNull(env);
        assertNotNull(env.getPayload());
    }
    
    @Test
    void testSetAndGetProtocolType() {
        envelope.setProtocolType(MessageEnvelope.ProtocolType.FIX);
        assertEquals(MessageEnvelope.ProtocolType.FIX, envelope.getProtocolType());
        
        envelope.setProtocolType(MessageEnvelope.ProtocolType.REST);
        assertEquals(MessageEnvelope.ProtocolType.REST, envelope.getProtocolType());
        
        envelope.setProtocolType(MessageEnvelope.ProtocolType.WEBSOCKET);
        assertEquals(MessageEnvelope.ProtocolType.WEBSOCKET, envelope.getProtocolType());
    }
    
    @Test
    void testSetAndGetCorrelationId() {
        envelope.setCorrelationId(12345L);
        assertEquals(12345L, envelope.getCorrelationId());
        
        envelope.setCorrelationId(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, envelope.getCorrelationId());
        
        envelope.setCorrelationId(0L);
        assertEquals(0L, envelope.getCorrelationId());
    }
    
    @Test
    void testSetPayloadFromByteArray() {
        byte[] data = "Hello, World!".getBytes();
        envelope.setPayload(data, 0, data.length);
        
        assertEquals(data.length, envelope.getPayloadLength());
        
        byte[] result = envelope.getPayloadAsBytes();
        assertArrayEquals(data, result);
    }
    
    @Test
    void testSetPayloadFromByteArrayWithOffset() {
        byte[] data = "0123456789".getBytes();
        envelope.setPayload(data, 2, 5); // "23456"
        
        assertEquals(5, envelope.getPayloadLength());
        
        byte[] result = envelope.getPayloadAsBytes();
        assertArrayEquals("23456".getBytes(), result);
    }
    
    @Test
    void testSetPayloadFromDirectBuffer() {
        byte[] data = "Test Data".getBytes();
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(data.length));
        buffer.putBytes(0, data);
        
        envelope.setPayload(buffer, 0, data.length);
        
        assertEquals(data.length, envelope.getPayloadLength());
        assertArrayEquals(data, envelope.getPayloadAsBytes());
    }
    
    @Test
    void testSetAndGetTimestamp() {
        long timestamp = System.nanoTime();
        envelope.setTimestamp(timestamp);
        assertEquals(timestamp, envelope.getTimestamp());
    }
    
    @Test
    void testSetAndGetClientId() {
        envelope.setClientId("client-123");
        assertEquals("client-123", envelope.getClientId());
        
        envelope.setClientId(null);
        assertNull(envelope.getClientId());
    }
    
    @Test
    void testReset() {
        envelope.setProtocolType(MessageEnvelope.ProtocolType.FIX);
        envelope.setCorrelationId(999L);
        envelope.setPayload("test".getBytes(), 0, 4);
        envelope.setTimestamp(12345L);
        envelope.setClientId("test-client");
        
        envelope.reset();
        
        assertNull(envelope.getProtocolType());
        assertEquals(0, envelope.getCorrelationId());
        assertEquals(0, envelope.getPayloadLength());
        assertEquals(0, envelope.getTimestamp());
        assertNull(envelope.getClientId());
    }
    
    @Test
    void testFluentAPI() {
        byte[] data = "payload".getBytes();
        
        MessageEnvelope result = envelope
            .setProtocolType(MessageEnvelope.ProtocolType.REST)
            .setCorrelationId(456L)
            .setPayload(data, 0, data.length)
            .setTimestamp(789L)
            .setClientId("fluent-client");
        
        assertSame(envelope, result);
        assertEquals(MessageEnvelope.ProtocolType.REST, envelope.getProtocolType());
        assertEquals(456L, envelope.getCorrelationId());
        assertEquals(data.length, envelope.getPayloadLength());
        assertEquals(789L, envelope.getTimestamp());
        assertEquals("fluent-client", envelope.getClientId());
    }
    
    @Test
    void testLargePayload() {
        byte[] largeData = new byte[16384]; // 16KB
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }
        
        envelope.setPayload(largeData, 0, largeData.length);
        assertEquals(largeData.length, envelope.getPayloadLength());
        
        byte[] result = envelope.getPayloadAsBytes();
        assertArrayEquals(largeData, result);
    }
    
    @Test
    void testPayloadExpansion() {
        // Start with small default buffer (8KB)
        MessageEnvelope env = new MessageEnvelope(1024);
        
        // Set large payload that exceeds initial capacity
        byte[] largeData = new byte[8192];
        env.setPayload(largeData, 0, largeData.length);
        
        assertEquals(largeData.length, env.getPayloadLength());
        byte[] result = env.getPayloadAsBytes();
        assertEquals(largeData.length, result.length);
    }
    
    @Test
    void testMultiplePayloadSets() {
        byte[] data1 = "First".getBytes();
        envelope.setPayload(data1, 0, data1.length);
        assertEquals(data1.length, envelope.getPayloadLength());
        
        byte[] data2 = "Second payload longer".getBytes();
        envelope.setPayload(data2, 0, data2.length);
        assertEquals(data2.length, envelope.getPayloadLength());
        assertArrayEquals(data2, envelope.getPayloadAsBytes());
    }
    
    @Test
    void testEmptyPayload() {
        byte[] emptyData = new byte[0];
        envelope.setPayload(emptyData, 0, 0);
        assertEquals(0, envelope.getPayloadLength());
    }
    
    @Test
    void testToString() {
        envelope.setProtocolType(MessageEnvelope.ProtocolType.WEBSOCKET);
        envelope.setCorrelationId(777L);
        envelope.setTimestamp(888L);
        envelope.setClientId("test");
        envelope.setPayload("data".getBytes(), 0, 4);
        
        String str = envelope.toString();
        assertNotNull(str);
        assertTrue(str.contains("WEBSOCKET"));
        assertTrue(str.contains("777"));
        assertTrue(str.contains("888"));
        assertTrue(str.contains("test"));
        assertTrue(str.contains("payloadLength=4"));
    }
    
    @Test
    void testProtocolTypeValues() {
        MessageEnvelope.ProtocolType[] types = MessageEnvelope.ProtocolType.values();
        assertEquals(3, types.length);
        assertEquals(MessageEnvelope.ProtocolType.FIX, types[0]);
        assertEquals(MessageEnvelope.ProtocolType.REST, types[1]);
        assertEquals(MessageEnvelope.ProtocolType.WEBSOCKET, types[2]);
    }
}
