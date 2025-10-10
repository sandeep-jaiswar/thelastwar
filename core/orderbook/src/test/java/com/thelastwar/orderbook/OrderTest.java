package com.thelastwar.orderbook;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Order record.
 */
class OrderTest {
    
    @Test
    void testOrderCreation() {
        Order order = new Order(
                12345L,
                "AAPL",
                Order.SIDE_BUY,
                15000L,
                100L,
                System.nanoTime()
        );
        
        assertEquals(12345L, order.orderId());
        assertEquals("AAPL", order.symbol());
        assertEquals(Order.SIDE_BUY, order.side());
        assertEquals(15000L, order.price());
        assertEquals(100L, order.quantity());
        assertTrue(order.timestamp() > 0);
    }
    
    @Test
    void testInvalidOrderId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(0L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(-1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        });
    }
    
    @Test
    void testInvalidSymbol() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(12345L, null, Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(12345L, "", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        });
    }
    
    @Test
    void testInvalidSide() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(12345L, "AAPL", (byte) 0, 15000L, 100L, System.nanoTime());
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(12345L, "AAPL", (byte) 3, 15000L, 100L, System.nanoTime());
        });
    }
    
    @Test
    void testInvalidQuantity() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(12345L, "AAPL", Order.SIDE_BUY, 15000L, 0L, System.nanoTime());
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(12345L, "AAPL", Order.SIDE_BUY, 15000L, -1L, System.nanoTime());
        });
    }
    
    @Test
    void testInvalidTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(12345L, "AAPL", Order.SIDE_BUY, 15000L, 100L, -1L);
        });
    }
    
    @Test
    void testWithQuantity() {
        Order order = new Order(12345L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L);
        Order updated = order.withQuantity(50L);
        
        assertEquals(12345L, updated.orderId());
        assertEquals("AAPL", updated.symbol());
        assertEquals(Order.SIDE_BUY, updated.side());
        assertEquals(15000L, updated.price());
        assertEquals(50L, updated.quantity());
        assertEquals(1000L, updated.timestamp());
    }
    
    @Test
    void testIsBuy() {
        Order buyOrder = new Order(12345L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        assertTrue(buyOrder.isBuy());
        assertFalse(buyOrder.isSell());
    }
    
    @Test
    void testIsSell() {
        Order sellOrder = new Order(12345L, "AAPL", Order.SIDE_SELL, 15000L, 100L, System.nanoTime());
        assertTrue(sellOrder.isSell());
        assertFalse(sellOrder.isBuy());
    }
    
    @Test
    void testImmutability() {
        Order order1 = new Order(12345L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L);
        Order order2 = new Order(12345L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L);
        
        assertEquals(order1, order2);
        assertEquals(order1.hashCode(), order2.hashCode());
    }
}
