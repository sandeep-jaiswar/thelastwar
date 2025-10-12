package com.thelastwar.orderbook.integration;

import com.thelastwar.orderbook.LimitOrderBook;
import com.thelastwar.orderbook.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LimitOrderBook with simulated trading scenarios.
 * Tests realistic order flow patterns and validates price-time priority.
 */
class LimitOrderBookIntegrationTest {
    
    private LimitOrderBook book;
    private Random random;
    
    @BeforeEach
    void setUp() {
        book = new LimitOrderBook("AAPL");
        random = new Random(42); // Fixed seed for reproducibility
    }
    
    @Test
    void testSimpleMarketMaking() {
        // Market maker places orders around mid-price
        long midPrice = 15000L;
        int spread = 10;
        
        // Place buy orders below mid
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, midPrice - spread, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, midPrice - spread * 2, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, midPrice - spread * 3, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        
        // Place sell orders above mid
        book.addOrder(new Order(4L, "AAPL", Order.SIDE_SELL, midPrice + spread, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(5L, "AAPL", Order.SIDE_SELL, midPrice + spread * 2, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(6L, "AAPL", Order.SIDE_SELL, midPrice + spread * 3, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        
        assertEquals(6, book.getOrderCount());
        assertEquals(midPrice - spread, book.getBestBid());
        assertEquals(midPrice + spread, book.getBestAsk());
        assertEquals(spread * 2, book.getSpread());
    }
    
    @Test
    void testOrderCancellations() {
        // Add multiple orders
        for (long i = 1; i <= 10; i++) {
            book.addOrder(new Order(i, "AAPL", Order.SIDE_BUY, 15000L - i * 10, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        }
        
        assertEquals(10, book.getOrderCount());
        
        // Cancel half the orders
        for (long i = 1; i <= 5; i++) {
            assertNotNull(book.removeOrder(i));
        }
        
        assertEquals(5, book.getOrderCount());
        
        // Verify remaining orders
        for (long i = 6; i <= 10; i++) {
            assertNotNull(book.getOrder(i));
        }
    }
    
    @Test
    void testOrderModifications() {
        // Add initial orders
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 14990L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        
        assertEquals(15000L, book.getBestBid());
        
        // Modify order 2 to improve price
        assertTrue(book.modifyOrder(2L, 15010L, 100L));
        
        // Best bid should now be the modified order
        assertEquals(15010L, book.getBestBid());
        
        // Modify quantity
        assertTrue(book.modifyOrder(1L, 0, 200L));
        assertEquals(200L, book.getOrder(1L).quantity());
    }
    
    @Test
    void testHighVolumeOrderFlow() {
        int orderCount = 1000;
        long startTime = System.nanoTime();
        
        // Add many orders
        for (int i = 1; i <= orderCount; i++) {
            byte side = (i % 2 == 0) ? Order.SIDE_BUY : Order.SIDE_SELL;
            long basePrice = 15000L;
            long price = side == Order.SIDE_BUY 
                ? basePrice - (i % 100) 
                : basePrice + (i % 100);
            
            book.addOrder(new Order(i, "AAPL", side, price, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        }
        
        long addDuration = System.nanoTime() - startTime;
        
        assertEquals(orderCount, book.getOrderCount());
        assertTrue(book.getBestBid() > 0);
        assertTrue(book.getBestAsk() > 0);
        assertTrue(book.getSpread() >= 0);
        
        // Verify we can remove all orders efficiently
        startTime = System.nanoTime();
        for (int i = 1; i <= orderCount; i++) {
            assertNotNull(book.removeOrder(i));
        }
        long removeDuration = System.nanoTime() - startTime;
        
        assertEquals(0, book.getOrderCount());
        
        // Log performance for manual verification
        System.out.printf("Added %d orders in %.2f µs (avg: %.2f ns/order)%n",
                orderCount, addDuration / 1000.0, addDuration / (double) orderCount);
        System.out.printf("Removed %d orders in %.2f µs (avg: %.2f ns/order)%n",
                orderCount, removeDuration / 1000.0, removeDuration / (double) orderCount);
    }
    
    @Test
    void testPriceTimePriorityWithMultipleOrders() {
        long price = 15000L;
        
        // Add orders at same price with incrementing timestamps
        for (long i = 1; i <= 5; i++) {
            book.addOrder(new Order(i, "AAPL", Order.SIDE_BUY, price, 100L, i * 1000L, Order.TYPE_LIMIT, Order.TIF_GTC));
        }
        
        // Verify order count and quantity
        assertEquals(5, book.getOrderCount());
        assertEquals(500L, book.getBestBidQuantity());
        
        // Remove orders - should maintain FIFO
        var levels = book.getBidLevels(1);
        assertEquals(1, levels.size());
        
        var level = levels.get(0);
        
        // Poll should return in time priority (oldest first)
        for (long i = 1; i <= 5; i++) {
            var order = level.poll();
            assertNotNull(order);
            assertEquals(i, order.orderId());
        }
    }
    
    @Test
    void testRealisticTradingScenario() {
        // Simulate a realistic trading day scenario
        
        // 1. Market opens with initial orders
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 14990L, 500L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 14980L, 300L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_SELL, 15010L, 500L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(4L, "AAPL", Order.SIDE_SELL, 15020L, 300L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        
        assertEquals(20L, book.getSpread());
        
        // 2. Aggressive buyer improves best bid
        book.addOrder(new Order(5L, "AAPL", Order.SIDE_BUY, 15000L, 200L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
        assertEquals(15000L, book.getBestBid());
        assertEquals(10L, book.getSpread());
        
        // 3. Another trader cancels their order
        book.removeOrder(3L);
        assertEquals(15020L, book.getBestAsk());
        
        // 4. Market maker updates their quotes
        book.modifyOrder(1L, 14995L, 500L);
        
        // 5. End state verification
        assertFalse(book.isEmpty());
        assertTrue(book.getOrderCount() > 0);
        assertTrue(book.getBestBid() > 0);
        assertTrue(book.getBestAsk() > 0);
    }
    
    @Test
    void testStressTestRandomOrders() {
        int iterations = 5000;
        long orderId = 1;
        long midPrice = 15000L;
        
        for (int i = 0; i < iterations; i++) {
            int action = random.nextInt(100);
            
            if (action < 60 && book.getOrderCount() < 1000) {
                // 60% chance: Add order
                byte side = random.nextBoolean() ? Order.SIDE_BUY : Order.SIDE_SELL;
                // Generate prices that don't cross: buys below mid, sells above mid
                long priceOffset = 1 + random.nextInt(100);
                long price = side == Order.SIDE_BUY 
                    ? midPrice - priceOffset 
                    : midPrice + priceOffset;
                long quantity = 100L + random.nextInt(900);
                
                book.addOrder(new Order(orderId++, "AAPL", side, price, quantity, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC));
                
            } else if (action < 80 && book.getOrderCount() > 0) {
                // 20% chance: Remove random order
                long removeId = 1 + random.nextInt((int) (orderId - 1));
                book.removeOrder(removeId);
                
            } else if (book.getOrderCount() > 0) {
                // 20% chance: Modify random order
                long modifyId = 1 + random.nextInt((int) (orderId - 1));
                if (book.getOrder(modifyId) != null) {
                    long newQuantity = 100L + random.nextInt(900);
                    book.modifyOrder(modifyId, 0, newQuantity);
                }
            }
        }
        
        // Verify book is still consistent
        if (!book.isEmpty()) {
            assertTrue(book.getOrderCount() > 0);
            
            if (book.getBestBid() > 0 && book.getBestAsk() > 0) {
                assertTrue(book.getBestBid() < book.getBestAsk(),
                        "Best bid should be lower than best ask (no crossed book)");
            }
        }
        
        System.out.printf("Stress test completed: %d operations, final order count: %d%n",
                iterations, book.getOrderCount());
    }
}
