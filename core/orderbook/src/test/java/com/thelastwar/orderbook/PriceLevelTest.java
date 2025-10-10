package com.thelastwar.orderbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PriceLevel.
 */
class PriceLevelTest {
    
    private PriceLevel level;
    
    @BeforeEach
    void setUp() {
        level = new PriceLevel(15000L);
    }
    
    @Test
    void testAddOrder() {
        Order order1 = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L);
        Order order2 = new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, 2000L);
        
        level.addOrder(order1);
        level.addOrder(order2);
        
        assertEquals(2, level.getOrderCount());
        assertEquals(300L, level.getTotalQuantity());
    }
    
    @Test
    void testAddOrderInvalidPrice() {
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15001L, 100L, 1000L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            level.addOrder(order);
        });
    }
    
    @Test
    void testRemoveOrder() {
        Order order1 = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L);
        Order order2 = new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, 2000L);
        
        level.addOrder(order1);
        level.addOrder(order2);
        
        assertTrue(level.removeOrder(1L));
        assertEquals(1, level.getOrderCount());
        assertEquals(200L, level.getTotalQuantity());
        
        assertFalse(level.removeOrder(999L));
    }
    
    @Test
    void testUpdateOrder() {
        Order order1 = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L);
        Order order2 = new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, 2000L);
        
        level.addOrder(order1);
        level.addOrder(order2);
        
        assertTrue(level.updateOrder(1L, 150L));
        assertEquals(2, level.getOrderCount());
        assertEquals(350L, level.getTotalQuantity());
        
        assertFalse(level.updateOrder(999L, 100L));
    }
    
    @Test
    void testPeek() {
        Order order1 = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L);
        Order order2 = new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, 2000L);
        
        assertNull(level.peek());
        
        level.addOrder(order1);
        level.addOrder(order2);
        
        Order peeked = level.peek();
        assertNotNull(peeked);
        assertEquals(1L, peeked.orderId());
        assertEquals(2, level.getOrderCount());
    }
    
    @Test
    void testPoll() {
        Order order1 = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L);
        Order order2 = new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, 2000L);
        
        assertNull(level.poll());
        
        level.addOrder(order1);
        level.addOrder(order2);
        
        Order polled = level.poll();
        assertNotNull(polled);
        assertEquals(1L, polled.orderId());
        assertEquals(1, level.getOrderCount());
        assertEquals(200L, level.getTotalQuantity());
        
        polled = level.poll();
        assertNotNull(polled);
        assertEquals(2L, polled.orderId());
        assertEquals(0, level.getOrderCount());
        assertTrue(level.isEmpty());
    }
    
    @Test
    void testIsEmpty() {
        assertTrue(level.isEmpty());
        
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L);
        level.addOrder(order);
        
        assertFalse(level.isEmpty());
        
        level.removeOrder(1L);
        assertTrue(level.isEmpty());
    }
    
    @Test
    void testGetPrice() {
        assertEquals(15000L, level.getPrice());
    }
    
    @Test
    void testPriceTimePriority() {
        // Add orders with different timestamps
        Order order1 = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L);
        Order order2 = new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, 2000L);
        Order order3 = new Order(3L, "AAPL", Order.SIDE_BUY, 15000L, 150L, 3000L);
        
        level.addOrder(order1);
        level.addOrder(order2);
        level.addOrder(order3);
        
        // Poll should return orders in FIFO order
        assertEquals(1L, level.poll().orderId());
        assertEquals(2L, level.poll().orderId());
        assertEquals(3L, level.poll().orderId());
        assertTrue(level.isEmpty());
    }
}
