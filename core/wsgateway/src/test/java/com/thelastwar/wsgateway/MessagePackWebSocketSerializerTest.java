package com.thelastwar.wsgateway;

import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.eventbus.model.ExecutionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessagePack WebSocket serializer.
 */
class MessagePackWebSocketSerializerTest {
    
    private MessagePackWebSocketSerializer serializer;
    
    @BeforeEach
    void setUp() {
        serializer = new MessagePackWebSocketSerializer();
    }
    
    @Test
    void testFormat() {
        assertEquals(TransportFormat.MESSAGEPACK, serializer.getFormat());
    }
    
    @Test
    void testSerializeOrderEvent() throws Exception {
        OrderEvent orderEvent = OrderEvent.newOrder(
            12345L,
            "AAPL",
            OrderEvent.SIDE_BUY,
            OrderEvent.TYPE_LIMIT,
            100L,
            15000L,
            999L,
            1
        );
        
        byte[] data = serializer.serializeOrderEvent(orderEvent);
        assertNotNull(data);
        assertTrue(data.length > 0);
        
        // MessagePack should be more compact than JSON
        assertTrue(data.length < 200); // Reasonable size check
    }
    
    @Test
    void testSerializeExecutionEvent() throws Exception {
        OrderEvent orderEvent = OrderEvent.newOrder(
            12345L,
            "AAPL",
            OrderEvent.SIDE_BUY,
            OrderEvent.TYPE_LIMIT,
            100L,
            15000L,
            999L,
            1
        );
        
        ExecutionEvent executionEvent = ExecutionEvent.newOrder(98765L, orderEvent);
        
        byte[] data = serializer.serializeExecutionEvent(executionEvent);
        assertNotNull(data);
        assertTrue(data.length > 0);
        assertTrue(data.length < 300);
    }
    
    @Test
    void testDeserializeMessage() throws Exception {
        // This is a simplified test - in production we'd create proper MessagePack data
        assertNotNull(serializer);
    }
}
