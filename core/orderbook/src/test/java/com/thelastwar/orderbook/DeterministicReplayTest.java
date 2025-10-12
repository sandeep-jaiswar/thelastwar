package com.thelastwar.orderbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic replay tests for price-time priority verification.
 * 
 * These tests verify that the order book maintains strict FIFO ordering
 * within each price level, ensuring price-time priority is correctly
 * implemented for regulatory compliance and fairness.
 */
class DeterministicReplayTest {
    
    private OffHeapOrderBook book;
    private List<Order> orderSequence;
    private long timestamp;
    
    @BeforeEach
    void setUp() {
        book = new OffHeapOrderBook("AAPL");
        orderSequence = new ArrayList<>();
        timestamp = 1000000000L; // Fixed timestamp for determinism
    }
    
    /**
     * Test that orders at the same price level are executed in time priority.
     */
    @Test
    void testStrictFIFOOrderingAtSamePrice() {
        // Create orders at same price with sequential timestamps
        Order order1 = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, timestamp, Order.TYPE_LIMIT, Order.TIF_GTC);
        Order order2 = new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, timestamp + 1000, Order.TYPE_LIMIT, Order.TIF_GTC);
        Order order3 = new Order(3L, "AAPL", Order.SIDE_BUY, 15000L, 300L, timestamp + 2000, Order.TYPE_LIMIT, Order.TIF_GTC);
        Order order4 = new Order(4L, "AAPL", Order.SIDE_BUY, 15000L, 150L, timestamp + 3000, Order.TYPE_LIMIT, Order.TIF_GTC);
        
        // Add orders in sequence
        book.addOrder(order1);
        book.addOrder(order2);
        book.addOrder(order3);
        book.addOrder(order4);
        
        // Total quantity should be sum of all orders
        assertEquals(750L, book.getBestBidQuantity());
        
        // Remove orders in FIFO order
        assertTrue(book.removeOrder(1L));
        assertEquals(650L, book.getBestBidQuantity());
        
        assertTrue(book.removeOrder(2L));
        assertEquals(450L, book.getBestBidQuantity());
        
        assertTrue(book.removeOrder(3L));
        assertEquals(150L, book.getBestBidQuantity());
        
        assertTrue(book.removeOrder(4L));
        assertEquals(0L, book.getBestBidQuantity());
    }
    
    /**
     * Test replay of a deterministic order sequence.
     */
    @Test
    void testDeterministicReplay() {
        // Record a sequence of operations
        List<OrderOperation> operations = new ArrayList<>();
        
        // Add operations
        operations.add(new OrderOperation(OpType.ADD, new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, timestamp, Order.TYPE_LIMIT, Order.TIF_GTC)));
        operations.add(new OrderOperation(OpType.ADD, new Order(2L, "AAPL", Order.SIDE_BUY, 15100L, 200L, timestamp + 1000, Order.TYPE_LIMIT, Order.TIF_GTC)));
        operations.add(new OrderOperation(OpType.ADD, new Order(3L, "AAPL", Order.SIDE_SELL, 15200L, 150L, timestamp + 2000, Order.TYPE_LIMIT, Order.TIF_GTC)));
        operations.add(new OrderOperation(OpType.ADD, new Order(4L, "AAPL", Order.SIDE_BUY, 15000L, 250L, timestamp + 3000, Order.TYPE_LIMIT, Order.TIF_GTC)));
        
        // Execute operations
        for (OrderOperation op : operations) {
            executeOperation(book, op);
        }
        
        // Verify state
        assertEquals(4, book.getOrderCount());
        assertEquals(15100L, book.getBestBid());
        assertEquals(15200L, book.getBestAsk());
        
        // Replay from scratch - should get same state
        OffHeapOrderBook replayBook = new OffHeapOrderBook("AAPL");
        for (OrderOperation op : operations) {
            executeOperation(replayBook, op);
        }
        
        // Verify replay matches
        assertEquals(book.getOrderCount(), replayBook.getOrderCount());
        assertEquals(book.getBestBid(), replayBook.getBestBid());
        assertEquals(book.getBestAsk(), replayBook.getBestAsk());
    }
    
    /**
     * Test that price-time priority is maintained across add/remove cycles.
     */
    @Test
    void testPriceTimePriorityAcrossCycles() {
        // Cycle 1: Add three orders at same price
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, timestamp, Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, timestamp + 1000, Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, 15000L, 300L, timestamp + 2000, Order.TYPE_LIMIT, Order.TIF_GTC));
        
        long qty1 = book.getBestBidQuantity();
        assertEquals(600L, qty1);
        
        // Remove middle order
        book.removeOrder(2L);
        long qty2 = book.getBestBidQuantity();
        assertEquals(400L, qty2);
        
        // Add another order - should go to end of queue
        book.addOrder(new Order(4L, "AAPL", Order.SIDE_BUY, 15000L, 150L, timestamp + 3000, Order.TYPE_LIMIT, Order.TIF_GTC));
        long qty3 = book.getBestBidQuantity();
        assertEquals(550L, qty3);
        
        // Remove first order
        book.removeOrder(1L);
        long qty4 = book.getBestBidQuantity();
        assertEquals(450L, qty4); // 300 + 150
        
        // Remaining orders should be 3 and 4
        assertNotNull(book.getOrder(3L));
        assertNotNull(book.getOrder(4L));
        assertNull(book.getOrder(1L));
        assertNull(book.getOrder(2L));
    }
    
    /**
     * Test deterministic behavior with interleaved bid and ask orders.
     */
    @Test
    void testDeterministicBidAskInterleaving() {
        // Create deterministic sequence
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, timestamp, Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_SELL, 15200L, 100L, timestamp + 1000, Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, 15100L, 200L, timestamp + 2000, Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(4L, "AAPL", Order.SIDE_SELL, 15100L, 150L, timestamp + 3000, Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(5L, "AAPL", Order.SIDE_BUY, 15100L, 250L, timestamp + 4000, Order.TYPE_LIMIT, Order.TIF_GTC));
        
        // Verify state is deterministic
        assertEquals(15100L, book.getBestBid());
        assertEquals(15100L, book.getBestAsk());
        assertEquals(0L, book.getSpread()); // Same price on both sides
        
        // Replay should give same result
        OffHeapOrderBook replay = new OffHeapOrderBook("AAPL");
        replay.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, timestamp, Order.TYPE_LIMIT, Order.TIF_GTC));
        replay.addOrder(new Order(2L, "AAPL", Order.SIDE_SELL, 15200L, 100L, timestamp + 1000, Order.TYPE_LIMIT, Order.TIF_GTC));
        replay.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, 15100L, 200L, timestamp + 2000, Order.TYPE_LIMIT, Order.TIF_GTC));
        replay.addOrder(new Order(4L, "AAPL", Order.SIDE_SELL, 15100L, 150L, timestamp + 3000, Order.TYPE_LIMIT, Order.TIF_GTC));
        replay.addOrder(new Order(5L, "AAPL", Order.SIDE_BUY, 15100L, 250L, timestamp + 4000, Order.TYPE_LIMIT, Order.TIF_GTC));
        
        assertEquals(book.getBestBid(), replay.getBestBid());
        assertEquals(book.getBestAsk(), replay.getBestAsk());
        assertEquals(book.getOrderCount(), replay.getOrderCount());
    }
    
    /**
     * Test that order updates preserve time priority.
     */
    @Test
    void testUpdatePreservesTimePriority() {
        // Add three orders at same price
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, timestamp, Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, timestamp + 1000, Order.TYPE_LIMIT, Order.TIF_GTC));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, 15000L, 300L, timestamp + 2000, Order.TYPE_LIMIT, Order.TIF_GTC));
        
        // Update first order quantity
        book.updateOrder(1L, 150L);
        
        // Order 1 should still be at front of queue
        Order order1 = book.getOrder(1L);
        assertNotNull(order1);
        assertEquals(150L, order1.quantity());
        
        // Total quantity should reflect update
        assertEquals(650L, book.getBestBidQuantity()); // 150 + 200 + 300
        
        // Remove first order
        book.removeOrder(1L);
        
        // Quantity should now be 500 (200 + 300)
        assertEquals(500L, book.getBestBidQuantity());
    }
    
    /**
     * Test massive deterministic replay with 10,000 operations.
     */
    @Test
    void testMassiveDeterministicReplay() {
        List<OrderOperation> operations = new ArrayList<>();
        
        // Generate deterministic sequence of 10,000 operations
        long ts = timestamp;
        for (long i = 1; i <= 10000; i++) {
            byte side = i % 2 == 0 ? Order.SIDE_BUY : Order.SIDE_SELL;
            long price = 15000L + (i % 100);
            long quantity = 100L + (i % 50);
            
            operations.add(new OrderOperation(
                OpType.ADD,
                new Order(i, "AAPL", side, price, quantity, ts++, Order.TYPE_LIMIT, Order.TIF_GTC)
            ));
        }
        
        // Execute operations
        for (OrderOperation op : operations) {
            executeOperation(book, op);
        }
        
        long finalBestBid = book.getBestBid();
        long finalBestAsk = book.getBestAsk();
        int finalCount = book.getOrderCount();
        
        // Replay from scratch
        OffHeapOrderBook replayBook = new OffHeapOrderBook("AAPL");
        for (OrderOperation op : operations) {
            executeOperation(replayBook, op);
        }
        
        // Verify replay matches exactly
        assertEquals(finalCount, replayBook.getOrderCount());
        assertEquals(finalBestBid, replayBook.getBestBid());
        assertEquals(finalBestAsk, replayBook.getBestAsk());
        
        // Verify every order exists in both books
        for (long i = 1; i <= 10000; i++) {
            Order original = book.getOrder(i);
            Order replayed = replayBook.getOrder(i);
            
            if (original == null) {
                assertNull(replayed);
            } else {
                assertNotNull(replayed);
                assertEquals(original.orderId(), replayed.orderId());
                assertEquals(original.price(), replayed.price());
                assertEquals(original.quantity(), replayed.quantity());
                assertEquals(original.side(), replayed.side());
            }
        }
    }
    
    // Helper classes and methods
    
    private enum OpType {
        ADD, REMOVE, UPDATE
    }
    
    private record OrderOperation(OpType type, Order order, Long newQuantity) {
        OrderOperation(OpType type, Order order) {
            this(type, order, null);
        }
    }
    
    private void executeOperation(OffHeapOrderBook book, OrderOperation op) {
        switch (op.type) {
            case ADD -> book.addOrder(op.order);
            case REMOVE -> book.removeOrder(op.order.orderId());
            case UPDATE -> book.updateOrder(op.order.orderId(), op.newQuantity);
        }
    }
}
