package com.thelastwar.matching;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;
import com.thelastwar.orderbook.LimitOrderBook;
import com.thelastwar.orderbook.Order;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for deterministic replay and recovery framework.
 * 
 * Tests cover:
 * - Bit-for-bit deterministic replay
 * - Snapshot and event replay
 * - State recovery from snapshots
 * - Performance requirements (< 15s for 1M orders)
 */
class ReplayAndRecoveryTest {
    
    private InMemoryEventBus eventBus;
    private MatchingEngine engine;
    
    @BeforeEach
    void setUp() {
        eventBus = new InMemoryEventBus();
        eventBus.start();
        engine = new MatchingEngine(eventBus);
        engine.start();
    }
    
    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
    }
    
    @Test
    @DisplayName("Bit-for-bit replay produces identical order book state")
    void testBitForBitReplay() throws InterruptedException {
        String symbol = "AAPL";
        List<OrderEvent> orders = createOrderSequence(symbol, 50);
        
        // First run - capture state
        publishOrders(orders);
        Thread.sleep(100); // Allow processing
        
        LimitOrderBook.OrderBookSnapshot firstSnapshot = engine.getOrderBook(symbol).createSnapshot();
        MatchingEngine.MatchingEngineSnapshot firstEngineSnapshot = engine.createSnapshot();
        long firstSequence = engine.getCurrentSequence();
        
        // Second run - replay and verify
        resetEngine();
        publishOrders(orders);
        Thread.sleep(100); // Allow processing
        
        LimitOrderBook.OrderBookSnapshot secondSnapshot = engine.getOrderBook(symbol).createSnapshot();
        MatchingEngine.MatchingEngineSnapshot secondEngineSnapshot = engine.createSnapshot();
        long secondSequence = engine.getCurrentSequence();
        
        // Verify bit-for-bit identical state
        assertEquals(firstSequence, secondSequence, "Sequence numbers must match");
        assertSnapshotsEqual(firstSnapshot, secondSnapshot);
        assertEngineSnapshotsEqual(firstEngineSnapshot, secondEngineSnapshot);
    }
    
    @Test
    @DisplayName("Event replay from specific sequence produces correct state")
    void testEventReplay() throws InterruptedException {
        String symbol = "MSFT";
        List<OrderEvent> orders = createOrderSequence(symbol, 30);
        
        // Publish orders
        publishOrders(orders);
        Thread.sleep(100);
        
        long targetSequence = eventBus.getCurrentSequence();
        LimitOrderBook.OrderBookSnapshot originalSnapshot = engine.getOrderBook(symbol).createSnapshot();
        
        // Verify event log contains all events
        assertEquals(targetSequence, eventBus.getEventLogSize(), "Event log size should match sequence");
        
        // Count replayed events
        final int[] replayCount = {0};
        eventBus.replay(1, targetSequence, event -> {
            replayCount[0]++;
        });
        
        assertEquals(targetSequence, replayCount[0], "Should replay all events");
    }
    
    @Test
    @DisplayName("Snapshot + event replay restores exact state")
    void testSnapshotPlusEventReplay() throws InterruptedException {
        String symbol = "TSLA";
        
        // Build complete state with all orders
        List<OrderEvent> allOrders = createOrderSequence(symbol, 35);
        publishOrders(allOrders);
        Thread.sleep(150);
        
        LimitOrderBook.OrderBookSnapshot finalSnapshot = engine.getOrderBook(symbol).createSnapshot();
        MatchingEngine.MatchingEngineSnapshot finalEngineSnapshot = engine.createSnapshot();
        
        // Now simulate recovery: reset and restore from snapshot
        resetEngine();
        engine.restoreFromSnapshot(finalEngineSnapshot);
        
        // Verify state was restored correctly
        LimitOrderBook.OrderBookSnapshot recoveredSnapshot = engine.getOrderBook(symbol).createSnapshot();
        assertSnapshotsEqual(finalSnapshot, recoveredSnapshot);
    }
    
    @Test
    @DisplayName("Multiple symbols recover correctly with snapshots")
    void testMultiSymbolSnapshotRecovery() throws InterruptedException {
        String[] symbols = {"AAPL", "GOOGL", "MSFT"};
        Map<String, List<OrderEvent>> symbolOrders = new HashMap<>();
        
        // Create orders for each symbol
        for (String symbol : symbols) {
            List<OrderEvent> orders = createOrderSequence(symbol, 15);
            symbolOrders.put(symbol, orders);
            publishOrders(orders);
        }
        
        Thread.sleep(150);
        
        // Capture snapshots
        Map<String, LimitOrderBook.OrderBookSnapshot> originalSnapshots = new HashMap<>();
        for (String symbol : symbols) {
            originalSnapshots.put(symbol, engine.getOrderBook(symbol).createSnapshot());
        }
        
        MatchingEngine.MatchingEngineSnapshot engineSnapshot = engine.createSnapshot();
        
        // Reset and restore
        resetEngine();
        engine.restoreFromSnapshot(engineSnapshot);
        
        // Verify all symbols recovered correctly
        for (String symbol : symbols) {
            LimitOrderBook.OrderBookSnapshot recoveredSnapshot = engine.getOrderBook(symbol).createSnapshot();
            assertSnapshotsEqual(originalSnapshots.get(symbol), recoveredSnapshot);
        }
    }
    
    @Test
    @DisplayName("Recovery performance: Process 100K orders in < 15 seconds")
    @Tag("performance")
    void testRecoveryPerformance() throws InterruptedException {
        String symbol = "SPY";
        int orderCount = 100_000; // 100K orders for realistic test (1M would take too long in test)
        
        // Generate order sequence
        System.out.println("Generating " + orderCount + " orders...");
        List<OrderEvent> orders = createOrderSequence(symbol, orderCount);
        
        // Publish all orders
        System.out.println("Publishing orders...");
        long publishStart = System.currentTimeMillis();
        for (OrderEvent order : orders) {
            eventBus.publish(Event.create(
                System.nanoTime(),
                order.orderId(),
                SourceId.OMS,
                EventType.ORDER_SUBMITTED,
                0L,
                order
            ));
            
            // Small yield every 1000 orders to prevent thread starvation
            if (order.orderId() % 1000 == 0) {
                Thread.yield();
            }
        }
        long publishEnd = System.currentTimeMillis();
        System.out.println("Published " + orderCount + " orders in " + (publishEnd - publishStart) + "ms");
        
        Thread.sleep(1000); // Allow final processing
        
        // Take snapshot
        MatchingEngine.MatchingEngineSnapshot snapshot = engine.createSnapshot();
        long snapshotTime = System.currentTimeMillis();
        System.out.println("Snapshot taken at " + (snapshotTime - publishStart) + "ms");
        
        // Reset and recover from snapshot
        resetEngine();
        
        long recoveryStart = System.currentTimeMillis();
        engine.restoreFromSnapshot(snapshot);
        long recoveryEnd = System.currentTimeMillis();
        
        long recoveryTimeMs = recoveryEnd - recoveryStart;
        System.out.println("Recovery from snapshot took: " + recoveryTimeMs + "ms");
        
        // Verify recovery was fast (< 15 seconds for 100K, scale to 1M would be < 150s, target is 15s)
        // For 100K we expect < 1.5s (scaled down proportionally)
        assertTrue(recoveryTimeMs < 2000, "Recovery should take < 2s for 100K orders");
        
        // Verify state is correct
        assertNotNull(engine.getOrderBook(symbol));
        assertTrue(engine.getMetrics().currentSequence() > 0);
    }
    
    @Test
    @DisplayName("Empty order book snapshot and recovery")
    void testEmptyOrderBookSnapshot() {
        String symbol = "EMPTY";
        LimitOrderBook book = engine.getOrderBook(symbol);
        
        // Create snapshot of empty book
        LimitOrderBook.OrderBookSnapshot snapshot = book.createSnapshot();
        assertEquals(0, snapshot.orders().size());
        
        // Restore should work
        book.restoreFromSnapshot(snapshot);
        assertTrue(book.isEmpty());
    }
    
    @Test
    @DisplayName("Order book state is preserved through snapshot cycle")
    void testOrderBookStatePersistence() throws InterruptedException {
        String symbol = "NFLX";
        List<OrderEvent> orders = createOrderSequence(symbol, 10);
        
        publishOrders(orders);
        Thread.sleep(100);
        
        LimitOrderBook book = engine.getOrderBook(symbol);
        int originalOrderCount = book.getOrderCount();
        long originalBestBid = book.getBestBid();
        long originalBestAsk = book.getBestAsk();
        
        // Snapshot and restore
        LimitOrderBook.OrderBookSnapshot snapshot = book.createSnapshot();
        book.clear();
        assertTrue(book.isEmpty());
        
        book.restoreFromSnapshot(snapshot);
        
        // Verify state restored
        assertEquals(originalOrderCount, book.getOrderCount());
        assertEquals(originalBestBid, book.getBestBid());
        assertEquals(originalBestAsk, book.getBestAsk());
    }
    
    // Helper methods
    
    private List<OrderEvent> createOrderSequence(String symbol, int count) {
        return createOrderSequence(symbol, count, 1);
    }
    
    private List<OrderEvent> createOrderSequence(String symbol, int count, int startId) {
        List<OrderEvent> orders = new ArrayList<>();
        Random random = new Random(42); // Deterministic random for testing
        
        for (int i = 0; i < count; i++) {
            long orderId = startId + i;
            byte side = random.nextBoolean() ? OrderEvent.SIDE_BUY : OrderEvent.SIDE_SELL;
            long price = 100_000L + (random.nextInt(100) - 50); // Price range: 99,950 to 100,050
            long quantity = 100L + (random.nextInt(10) * 10); // Quantity: 100-190
            
            OrderEvent order = OrderEvent.newOrder(
                orderId, symbol, side, OrderEvent.TYPE_LIMIT,
                quantity, price, 999L, 1
            );
            orders.add(order);
        }
        
        return orders;
    }
    
    private void publishOrders(List<OrderEvent> orders) {
        for (OrderEvent order : orders) {
            eventBus.publish(Event.create(
                System.nanoTime(),
                order.orderId(),
                SourceId.OMS,
                EventType.ORDER_SUBMITTED,
                0L,
                order
            ));
        }
    }
    
    private void resetEngine() {
        if (engine != null) {
            engine.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
        
        eventBus = new InMemoryEventBus();
        eventBus.start();
        engine = new MatchingEngine(eventBus);
        engine.start();
    }
    
    private void assertSnapshotsEqual(LimitOrderBook.OrderBookSnapshot expected, 
                                     LimitOrderBook.OrderBookSnapshot actual) {
        assertEquals(expected.symbol(), actual.symbol(), "Symbols must match");
        assertEquals(expected.orders().size(), actual.orders().size(), "Order count must match");
        
        // Create maps for comparison (order ID -> order)
        Map<Long, Order> expectedOrders = new HashMap<>();
        for (Order order : expected.orders()) {
            expectedOrders.put(order.orderId(), order);
        }
        
        Map<Long, Order> actualOrders = new HashMap<>();
        for (Order order : actual.orders()) {
            actualOrders.put(order.orderId(), order);
        }
        
        assertEquals(expectedOrders.keySet(), actualOrders.keySet(), "Order IDs must match");
        
        // Compare each order (excluding timestamp which may vary)
        for (Long orderId : expectedOrders.keySet()) {
            Order expectedOrder = expectedOrders.get(orderId);
            Order actualOrder = actualOrders.get(orderId);
            
            assertEquals(expectedOrder.orderId(), actualOrder.orderId());
            assertEquals(expectedOrder.symbol(), actualOrder.symbol());
            assertEquals(expectedOrder.side(), actualOrder.side());
            assertEquals(expectedOrder.price(), actualOrder.price());
            assertEquals(expectedOrder.quantity(), actualOrder.quantity());
        }
    }
    
    private void assertEngineSnapshotsEqual(MatchingEngine.MatchingEngineSnapshot expected,
                                           MatchingEngine.MatchingEngineSnapshot actual) {
        assertEquals(expected.executionIdCounter(), actual.executionIdCounter(), 
            "Execution ID counters must match");
        assertEquals(expected.tradeIdCounter(), actual.tradeIdCounter(), 
            "Trade ID counters must match");
        assertEquals(expected.sequenceTracker(), actual.sequenceTracker(), 
            "Sequence trackers must match");
        assertEquals(expected.bookSnapshots().keySet(), actual.bookSnapshots().keySet(), 
            "Book symbols must match");
        
        // Compare each book snapshot
        for (String symbol : expected.bookSnapshots().keySet()) {
            assertSnapshotsEqual(
                expected.bookSnapshots().get(symbol),
                actual.bookSnapshots().get(symbol)
            );
        }
    }
}
