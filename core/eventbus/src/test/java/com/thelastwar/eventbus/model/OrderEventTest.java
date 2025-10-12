package com.thelastwar.eventbus.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderEvent immutable record.
 */
class OrderEventTest {
    
    @Test
    void testOrderEventCreation() {
        OrderEvent order = new OrderEvent(
                12345L,
                "AAPL",
                OrderEvent.SIDE_BUY,
                OrderEvent.TYPE_LIMIT,
                100L,
                15000L, // $150.00 in cents
                System.nanoTime(),
                OrderEvent.STATUS_NEW,
                999L,
                1,
                OrderEvent.TIF_GTC
        );
        
        assertEquals(12345L, order.orderId());
        assertEquals("AAPL", order.symbol());
        assertEquals(OrderEvent.SIDE_BUY, order.side());
        assertEquals(OrderEvent.TYPE_LIMIT, order.orderType());
        assertEquals(100L, order.quantity());
        assertEquals(15000L, order.price());
        assertEquals(OrderEvent.STATUS_NEW, order.status());
        assertEquals(999L, order.account());
        assertEquals(1, order.exchange());
        assertEquals(OrderEvent.TIF_GTC, order.timeInForce());
    }
    
    @Test
    void testNewOrderFactoryMethod() {
        OrderEvent order = OrderEvent.newOrder(
                12345L,
                "AAPL",
                OrderEvent.SIDE_BUY,
                OrderEvent.TYPE_LIMIT,
                100L,
                15000L,
                999L,
                1
        );
        
        assertEquals(12345L, order.orderId());
        assertEquals("AAPL", order.symbol());
        assertEquals(OrderEvent.SIDE_BUY, order.side());
        assertEquals(OrderEvent.STATUS_NEW, order.status());
        assertTrue(order.timestamp() > 0);
    }
    
    @Test
    void testInvalidOrderId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderEvent(
                    0L, // invalid
                    "AAPL",
                    OrderEvent.SIDE_BUY,
                    OrderEvent.TYPE_LIMIT,
                    100L,
                    15000L,
                    System.nanoTime(),
                    OrderEvent.STATUS_NEW,
                    999L,
                    1,
                    OrderEvent.TIF_GTC
            );
        });
    }
    
    @Test
    void testInvalidSymbol() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderEvent(
                    12345L,
                    null, // invalid
                    OrderEvent.SIDE_BUY,
                    OrderEvent.TYPE_LIMIT,
                    100L,
                    15000L,
                    System.nanoTime(),
                    OrderEvent.STATUS_NEW,
                    999L,
                    1,
                    OrderEvent.TIF_GTC
            );
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderEvent(
                    12345L,
                    "", // invalid
                    OrderEvent.SIDE_BUY,
                    OrderEvent.TYPE_LIMIT,
                    100L,
                    15000L,
                    System.nanoTime(),
                    OrderEvent.STATUS_NEW,
                    999L,
                    1,
                    OrderEvent.TIF_GTC
            );
        });
    }
    
    @Test
    void testInvalidSide() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderEvent(
                    12345L,
                    "AAPL",
                    (byte) 99, // invalid
                    OrderEvent.TYPE_LIMIT,
                    100L,
                    15000L,
                    System.nanoTime(),
                    OrderEvent.STATUS_NEW,
                    999L,
                    1,
                    OrderEvent.TIF_GTC
            );
        });
    }
    
    @Test
    void testInvalidOrderType() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderEvent(
                    12345L,
                    "AAPL",
                    OrderEvent.SIDE_BUY,
                    (byte) 99, // invalid
                    100L,
                    15000L,
                    System.nanoTime(),
                    OrderEvent.STATUS_NEW,
                    999L,
                    1,
                    OrderEvent.TIF_GTC
            );
        });
    }
    
    @Test
    void testInvalidQuantity() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderEvent(
                    12345L,
                    "AAPL",
                    OrderEvent.SIDE_BUY,
                    OrderEvent.TYPE_LIMIT,
                    0L, // invalid
                    15000L,
                    System.nanoTime(),
                    OrderEvent.STATUS_NEW,
                    999L,
                    1,
                    OrderEvent.TIF_GTC
            );
        });
    }
    
    @Test
    void testWithStatus() {
        OrderEvent original = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        OrderEvent updated = original.withStatus(OrderEvent.STATUS_FILLED);
        
        assertEquals(OrderEvent.STATUS_FILLED, updated.status());
        assertEquals(original.orderId(), updated.orderId());
        assertEquals(original.symbol(), updated.symbol());
        assertTrue(updated.timestamp() >= original.timestamp());
    }
    
    @Test
    void testWithQuantity() {
        OrderEvent original = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        OrderEvent updated = original.withQuantity(50L);
        
        assertEquals(50L, updated.quantity());
        assertEquals(original.orderId(), updated.orderId());
        assertEquals(original.status(), updated.status());
    }
    
    @Test
    void testIsBuy() {
        OrderEvent buyOrder = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        assertTrue(buyOrder.isBuy());
        assertFalse(buyOrder.isSell());
    }
    
    @Test
    void testIsSell() {
        OrderEvent sellOrder = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        assertTrue(sellOrder.isSell());
        assertFalse(sellOrder.isBuy());
    }
    
    @Test
    void testIsTerminal() {
        OrderEvent newOrder = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        assertFalse(newOrder.isTerminal());
        
        OrderEvent filledOrder = newOrder.withStatus(OrderEvent.STATUS_FILLED);
        assertTrue(filledOrder.isTerminal());
        
        OrderEvent cancelledOrder = newOrder.withStatus(OrderEvent.STATUS_CANCELLED);
        assertTrue(cancelledOrder.isTerminal());
        
        OrderEvent rejectedOrder = newOrder.withStatus(OrderEvent.STATUS_REJECTED);
        assertTrue(rejectedOrder.isTerminal());
        
        OrderEvent partialOrder = newOrder.withStatus(OrderEvent.STATUS_PARTIALLY_FILLED);
        assertFalse(partialOrder.isTerminal());
    }
    
    @Test
    void testOrderTypeConstants() {
        assertEquals(1, OrderEvent.TYPE_MARKET);
        assertEquals(2, OrderEvent.TYPE_LIMIT);
        assertEquals(3, OrderEvent.TYPE_STOP);
        assertEquals(4, OrderEvent.TYPE_STOP_LIMIT);
    }
    
    @Test
    void testOrderStatusConstants() {
        assertEquals(0, OrderEvent.STATUS_NEW);
        assertEquals(1, OrderEvent.STATUS_PARTIALLY_FILLED);
        assertEquals(2, OrderEvent.STATUS_FILLED);
        assertEquals(3, OrderEvent.STATUS_CANCELLED);
        assertEquals(4, OrderEvent.STATUS_REJECTED);
    }
    
    @Test
    void testImmutability() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        // These should create new instances
        OrderEvent updated1 = order.withStatus(OrderEvent.STATUS_FILLED);
        OrderEvent updated2 = order.withQuantity(50L);
        
        // Original should be unchanged
        assertEquals(OrderEvent.STATUS_NEW, order.status());
        assertEquals(100L, order.quantity());
        
        // New instances should have changes
        assertEquals(OrderEvent.STATUS_FILLED, updated1.status());
        assertEquals(50L, updated2.quantity());
    }
}
