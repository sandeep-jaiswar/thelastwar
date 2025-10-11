package com.thelastwar.orderbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OffHeapOrderBook with O(1) operations.
 */
class OffHeapOrderBookTest {
    
    private OffHeapOrderBook book;
    
    @BeforeEach
    void setUp() {
        book = new OffHeapOrderBook("AAPL");
    }
    
    @Test
    void testAddOrder() {
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC);
        assertTrue(book.addOrder(order));
        assertEquals(1, book.getOrderCount());
        assertFalse(book.isEmpty());
    }
    
    @Test
    void testAddMultipleOrders() {
        for (long i = 1; i <= 100; i++) {
            Order order = new Order(i, "AAPL", Order.SIDE_BUY, 15000L + i, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC);
            assertTrue(book.addOrder(order));
        }
        assertEquals(100, book.getOrderCount());
    }
    
    @Test
    void testRemoveOrder() {
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC);
        book.addOrder(order);
        
        assertTrue(book.removeOrder(1L));
        assertEquals(0, book.getOrderCount());
        assertTrue(book.isEmpty());
    }
    
    @Test
    void testRemoveNonExistentOrder() {
        assertFalse(book.removeOrder(999L));
    }
    
    @Test
    void testUpdateOrder() {
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC);
        book.addOrder(order);
        
        assertTrue(book.updateOrder(1L, 200L));
        
        Order updated = book.getOrder(1L);
        assertNotNull(updated);
        assertEquals(200L, updated.quantity());
    }
    
    @Test
    void testUpdateNonExistentOrder() {
        assertFalse(book.updateOrder(999L, 200L));
    }
    
    @Test
    void testGetOrder() {
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC);
        book.addOrder(order);
        
        Order retrieved = book.getOrder(1L);
        assertNotNull(retrieved);
        assertEquals(1L, retrieved.orderId());
        assertEquals("AAPL", retrieved.symbol());
        assertEquals(Order.SIDE_BUY, retrieved.side());
        assertEquals(15000L, retrieved.price());
        assertEquals(100L, retrieved.quantity());
    }
    
    @Test
    void testGetNonExistentOrder() {
        assertNull(book.getOrder(999L));
    }
    
    @Test
    void testBestBid() {
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        assertEquals(15000L, book.getBestBid());
        
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 15100L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        assertEquals(15100L, book.getBestBid());
        
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, 14900L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        assertEquals(15100L, book.getBestBid());
    }
    
    @Test
    void testBestAsk() {
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_SELL, 15100L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        assertEquals(15100L, book.getBestAsk());
        
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_SELL, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        assertEquals(15000L, book.getBestAsk());
        
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_SELL, 15200L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        assertEquals(15000L, book.getBestAsk());
    }
    
    @Test
    void testBestBidAfterRemoval() {
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 15100L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        
        assertEquals(15100L, book.getBestBid());
        
        book.removeOrder(2L);
        assertEquals(15000L, book.getBestBid());
        
        book.removeOrder(1L);
        assertEquals(0L, book.getBestBid());
    }
    
    @Test
    void testBestAskAfterRemoval() {
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_SELL, 15100L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_SELL, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        
        assertEquals(15000L, book.getBestAsk());
        
        book.removeOrder(2L);
        assertEquals(15100L, book.getBestAsk());
        
        book.removeOrder(1L);
        assertEquals(0L, book.getBestAsk());
    }
    
    @Test
    void testSpread() {
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_SELL, 15100L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        
        assertEquals(100L, book.getSpread());
    }
    
    @Test
    void testSpreadOneSided() {
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        assertEquals(0L, book.getSpread());
    }
    
    @Test
    void testBestBidQuantity() {
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, 14900L, 150L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        
        assertEquals(300L, book.getBestBidQuantity());
    }
    
    @Test
    void testBestAskQuantity() {
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_SELL, 15100L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_SELL, 15100L, 200L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_SELL, 15200L, 150L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        
        assertEquals(300L, book.getBestAskQuantity());
    }
    
    @Test
    void testPriceTimePriority() {
        long t0 = System.nanoTime();
        
        // Add three orders at same price with different timestamps
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, t0, Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, t0 + 1000, Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, 15000L, 300L, t0 + 2000, Order.TYPE_LIMIT, Order.TIF_GTC));
        
        // Initially should have total of 600
        assertEquals(600L, book.getBestBidQuantity());
        
        // Remove first order
        assertTrue(book.removeOrder(1L));
        
        // Best bid quantity should reflect remaining orders (200 + 300)
        assertEquals(500L, book.getBestBidQuantity());
        
        // Verify order 1 is gone
        assertNull(book.getOrder(1L));
        
        // Verify orders 2 and 3 are still there
        assertNotNull(book.getOrder(2L));
        assertNotNull(book.getOrder(3L));
    }
    
    @Test
    void testInvalidSymbol() {
        Order order = new Order(1L, "GOOGL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC);
        assertThrows(IllegalArgumentException.class, () -> book.addOrder(order));
    }
    
    @Test
    void testGetSymbol() {
        assertEquals("AAPL", book.getSymbol());
    }
    
    @Test
    void testDepthSnapshot() {
        // Add bid orders
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 14900L, 200L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, 14800L, 150L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        
        // Add ask orders
        book.addOrder(new Order(4L, "AAPL", Order.SIDE_SELL, 15100L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(5L, "AAPL", Order.SIDE_SELL, 15200L, 200L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(6L, "AAPL", Order.SIDE_SELL, 15300L, 150L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        
        OffHeapOrderBook.PriceLevelSnapshot[] depth = book.getDepthSnapshot(5);
        
        assertNotNull(depth);
        assertEquals(6, depth.length); // 3 bid levels + 3 ask levels
    }
    
    @Test
    void testHighVolumeOrders() {
        // Add 10,000 orders
        long startTime = System.nanoTime();
        
        for (long i = 1; i <= 10000; i++) {
            Order order = new Order(i, "AAPL", 
                i % 2 == 0 ? Order.SIDE_BUY : Order.SIDE_SELL,
                15000L + (i % 100), 100L, System.nanoTime());
            book.addOrder(order);
        }
        
        long addTime = System.nanoTime() - startTime;
        
        assertEquals(10000, book.getOrderCount());
        
        // Verify best bid/ask lookup is fast
        startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            book.getBestBid();
            book.getBestAsk();
        }
        long lookupTime = System.nanoTime() - startTime;
        
        // Average lookup time should be < 200 ns
        long avgLookupTime = lookupTime / 20000;
        System.out.println("Average best bid/ask lookup time: " + avgLookupTime + " ns");
        assertTrue(avgLookupTime < 1000, "Lookup time " + avgLookupTime + " ns exceeds 1000 ns");
    }
    
    @Test
    void testRemoveAndReAddOrders() {
        // Add orders
        for (long i = 1; i <= 100; i++) {
            book.addOrder(new Order(i, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        }
        
        // Remove all orders
        for (long i = 1; i <= 100; i++) {
            assertTrue(book.removeOrder(i));
        }
        
        assertEquals(0, book.getOrderCount());
        
        // Re-add orders
        for (long i = 101; i <= 200; i++) {
            book.addOrder(new Order(i, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        }
        
        assertEquals(100, book.getOrderCount());
    }
}
