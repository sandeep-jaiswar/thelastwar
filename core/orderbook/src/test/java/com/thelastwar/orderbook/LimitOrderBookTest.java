package com.thelastwar.orderbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Unit tests for LimitOrderBook.
 */
class LimitOrderBookTest {
    
    private LimitOrderBook book;
    
    @BeforeEach
    void setUp() {
        book = new LimitOrderBook("AAPL");
    }
    
    @Test
    void testAddBuyOrder() {
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        
        assertTrue(book.addOrder(order));
        assertEquals(1, book.getOrderCount());
        assertEquals(15000L, book.getBestBid());
        assertEquals(100L, book.getBestBidQuantity());
    }
    
    @Test
    void testAddSellOrder() {
        Order order = new Order(1L, "AAPL", Order.SIDE_SELL, 15100L, 100L, System.nanoTime());
        
        assertTrue(book.addOrder(order));
        assertEquals(1, book.getOrderCount());
        assertEquals(15100L, book.getBestAsk());
        assertEquals(100L, book.getBestAskQuantity());
    }
    
    @Test
    void testAddOrderInvalidSymbol() {
        Order order = new Order(1L, "MSFT", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        
        assertThrows(IllegalArgumentException.class, () -> {
            book.addOrder(order);
        });
    }
    
    @Test
    void testAddDuplicateOrder() {
        Order order1 = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        Order order2 = new Order(1L, "AAPL", Order.SIDE_BUY, 15100L, 100L, System.nanoTime());
        
        book.addOrder(order1);
        
        assertThrows(IllegalArgumentException.class, () -> {
            book.addOrder(order2);
        });
    }
    
    @Test
    void testRemoveOrder() {
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        
        book.addOrder(order);
        assertEquals(1, book.getOrderCount());
        
        Order removed = book.removeOrder(1L);
        assertNotNull(removed);
        assertEquals(1L, removed.orderId());
        assertEquals(0, book.getOrderCount());
        assertEquals(0, book.getBestBid());
    }
    
    @Test
    void testRemoveNonExistentOrder() {
        Order removed = book.removeOrder(999L);
        assertNull(removed);
    }
    
    @Test
    void testModifyOrderQuantity() {
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        book.addOrder(order);
        
        assertTrue(book.modifyOrder(1L, 0, 150L));
        
        Order modified = book.getOrder(1L);
        assertNotNull(modified);
        assertEquals(150L, modified.quantity());
        assertEquals(15000L, modified.price());
        assertEquals(150L, book.getBestBidQuantity());
    }
    
    @Test
    void testModifyOrderPrice() {
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        book.addOrder(order);
        
        assertTrue(book.modifyOrder(1L, 15100L, 100L));
        
        Order modified = book.getOrder(1L);
        assertNotNull(modified);
        assertEquals(100L, modified.quantity());
        assertEquals(15100L, modified.price());
        assertEquals(15100L, book.getBestBid());
    }
    
    @Test
    void testModifyNonExistentOrder() {
        assertFalse(book.modifyOrder(999L, 15000L, 100L));
    }
    
    @Test
    void testGetBestBidAsk() {
        // Empty book
        assertEquals(0, book.getBestBid());
        assertEquals(0, book.getBestAsk());
        
        // Add buy orders
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime()));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 14900L, 100L, System.nanoTime()));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, 15100L, 100L, System.nanoTime()));
        
        // Best bid should be highest price
        assertEquals(15100L, book.getBestBid());
        
        // Add sell orders
        book.addOrder(new Order(4L, "AAPL", Order.SIDE_SELL, 15200L, 100L, System.nanoTime()));
        book.addOrder(new Order(5L, "AAPL", Order.SIDE_SELL, 15300L, 100L, System.nanoTime()));
        book.addOrder(new Order(6L, "AAPL", Order.SIDE_SELL, 15150L, 100L, System.nanoTime()));
        
        // Best ask should be lowest price
        assertEquals(15150L, book.getBestAsk());
    }
    
    @Test
    void testGetSpread() {
        // Empty book
        assertEquals(0, book.getSpread());
        
        // One-sided book
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime()));
        assertEquals(0, book.getSpread());
        
        // Two-sided book
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_SELL, 15100L, 100L, System.nanoTime()));
        assertEquals(100L, book.getSpread());
    }
    
    @Test
    void testGetBidLevels() {
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime()));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 14900L, 100L, System.nanoTime()));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, 15100L, 100L, System.nanoTime()));
        
        List<PriceLevel> levels = book.getBidLevels(2);
        
        assertEquals(2, levels.size());
        assertEquals(15100L, levels.get(0).getPrice());
        assertEquals(15000L, levels.get(1).getPrice());
    }
    
    @Test
    void testGetAskLevels() {
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_SELL, 15200L, 100L, System.nanoTime()));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_SELL, 15300L, 100L, System.nanoTime()));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_SELL, 15150L, 100L, System.nanoTime()));
        
        List<PriceLevel> levels = book.getAskLevels(2);
        
        assertEquals(2, levels.size());
        assertEquals(15150L, levels.get(0).getPrice());
        assertEquals(15200L, levels.get(1).getPrice());
    }
    
    @Test
    void testMultipleOrdersAtSamePrice() {
        Order order1 = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L);
        Order order2 = new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, 2000L);
        Order order3 = new Order(3L, "AAPL", Order.SIDE_BUY, 15000L, 150L, 3000L);
        
        book.addOrder(order1);
        book.addOrder(order2);
        book.addOrder(order3);
        
        assertEquals(3, book.getOrderCount());
        assertEquals(15000L, book.getBestBid());
        assertEquals(450L, book.getBestBidQuantity());
    }
    
    @Test
    void testPriceTimePriority() {
        // Add orders at same price with different timestamps
        Order order1 = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L);
        Order order2 = new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, 2000L);
        Order order3 = new Order(3L, "AAPL", Order.SIDE_BUY, 15000L, 150L, 3000L);
        
        book.addOrder(order1);
        book.addOrder(order2);
        book.addOrder(order3);
        
        // Get the price level
        List<PriceLevel> levels = book.getBidLevels(1);
        assertEquals(1, levels.size());
        
        PriceLevel level = levels.get(0);
        
        // Orders should be in FIFO order (price-time priority)
        assertEquals(1L, level.poll().orderId());
        assertEquals(2L, level.poll().orderId());
        assertEquals(3L, level.poll().orderId());
    }
    
    @Test
    void testClear() {
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime()));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_SELL, 15100L, 100L, System.nanoTime()));
        
        assertEquals(2, book.getOrderCount());
        
        book.clear();
        
        assertEquals(0, book.getOrderCount());
        assertTrue(book.isEmpty());
        assertEquals(0, book.getBestBid());
        assertEquals(0, book.getBestAsk());
    }
    
    @Test
    void testGetSymbol() {
        assertEquals("AAPL", book.getSymbol());
    }
}
