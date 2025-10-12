package com.thelastwar.eventbus.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OrderEnvelope event model.
 */
class OrderEnvelopeTest {
    
    @Test
    void testWrapOrder() {
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        OrderEnvelope envelope = OrderEnvelope.wrap(order, 1L, 1);
        
        assertNotNull(envelope);
        assertEquals(order, envelope.orderEvent());
        assertEquals(1L, envelope.sequenceId());
        assertEquals(1, envelope.sourceId());
        assertEquals(0, envelope.routingKey());
        assertTrue(envelope.receivedTime() > 0);
    }
    
    @Test
    void testWrapOrderWithRoutingKey() {
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        OrderEnvelope envelope = OrderEnvelope.wrap(order, 1L, 1, 42);
        
        assertNotNull(envelope);
        assertEquals(order, envelope.orderEvent());
        assertEquals(1L, envelope.sequenceId());
        assertEquals(1, envelope.sourceId());
        assertEquals(42, envelope.routingKey());
    }
    
    @Test
    void testOrderIdAndSymbolAccessors() {
        OrderEvent order = OrderEvent.newOrder(
            123L, "MSFT", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        OrderEnvelope envelope = OrderEnvelope.wrap(order, 1L, 1);
        
        assertEquals(123L, envelope.orderId());
        assertEquals("MSFT", envelope.symbol());
    }
    
    @Test
    void testNullOrderValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderEnvelope(null, 1L, 1, System.nanoTime(), 0);
        });
    }
    
    @Test
    void testNegativeSequenceValidation() {
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderEnvelope(order, -1L, 1, System.nanoTime(), 0);
        });
    }
    
    @Test
    void testNegativeTimestampValidation() {
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderEnvelope(order, 1L, 1, -1L, 0);
        });
    }
}
