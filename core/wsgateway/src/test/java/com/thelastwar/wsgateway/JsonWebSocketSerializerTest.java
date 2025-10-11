package com.thelastwar.wsgateway;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.SourceId;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.eventbus.model.ExecutionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JSON WebSocket serializer.
 */
class JsonWebSocketSerializerTest {
    
    private JsonWebSocketSerializer serializer;
    
    @BeforeEach
    void setUp() {
        serializer = new JsonWebSocketSerializer();
    }
    
    @Test
    void testFormat() {
        assertEquals(TransportFormat.JSON, serializer.getFormat());
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
        
        // Verify it's valid JSON
        String json = new String(data);
        assertTrue(json.contains("orderId"));
        assertTrue(json.contains("symbol"));
        assertTrue(json.contains("AAPL"));
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
        
        String json = new String(data);
        assertTrue(json.contains("executionId"));
        assertTrue(json.contains("orderId"));
    }
    
    @Test
    void testSerializeEvent() throws Exception {
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
        
        Event event = Event.create(
            System.nanoTime(),
            1L,
            SourceId.MATCHING_ENGINE,
            EventType.ORDER_ACCEPTED,
            0L,
            orderEvent
        );
        
        byte[] data = serializer.serialize(event);
        assertNotNull(data);
        assertTrue(data.length > 0);
        
        String json = new String(data);
        assertTrue(json.contains("timestamp"));
        assertTrue(json.contains("sequence"));
        assertTrue(json.contains("eventType"));
    }
    
    @Test
    void testDeserializeMessage() throws Exception {
        String json = "{\"type\":\"SUBSCRIBE_ORDER\",\"payload\":12345}";
        byte[] data = json.getBytes();
        
        WebSocketMessage message = serializer.deserialize(data);
        assertNotNull(message);
        assertEquals(WebSocketMessage.MessageType.SUBSCRIBE_ORDER, message.getType());
        assertNotNull(message.getPayload());
    }
}
