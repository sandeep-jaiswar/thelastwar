package com.thelastwar.matching;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;
import com.thelastwar.orderbook.*;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MatchingEngine.
 */
class MatchingEngineTest {
    
    private EventBus eventBus;
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
        engine.stop();
        eventBus.stop();
    }
    
    @Test
    void testEngineStartStop() {
        assertTrue(engine.isRunning());
        engine.stop();
        assertFalse(engine.isRunning());
    }
    
    @Test
    void testGetOrderBook() {
        LimitOrderBook book = engine.getOrderBook("AAPL");
        assertNotNull(book);
        assertEquals("AAPL", book.getSymbol());
        
        // Should return same instance for same symbol
        LimitOrderBook book2 = engine.getOrderBook("AAPL");
        assertSame(book, book2);
    }
    
    @Test
    void testSimpleMatch() throws InterruptedException {
        String symbol = "AAPL";
        CountDownLatch latch = new CountDownLatch(2); // Expect 2 events: partial fill + full fill
        List<Event> receivedEvents = new ArrayList<>();
        
        // Subscribe to execution events
        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            receivedEvents.add(event);
            latch.countDown();
        });
        
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> {
            receivedEvents.add(event);
            latch.countDown();
        });
        
        // Add a sell order at 15000
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        Event sellEvent = Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sellOrder
        );
        eventBus.publish(sellEvent);
        
        // Give time for processing
        Thread.sleep(50);
        
        // Add a buy order at 15000 (should match)
        OrderEvent buyOrder = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 888L, 1
        );
        Event buyEvent = Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, buyOrder
        );
        eventBus.publish(buyEvent);
        
        // Wait for events
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Expected execution events not received");
        
        // Verify events received
        assertFalse(receivedEvents.isEmpty());
        
        // Order book should be empty after full match
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getOrderCount());
    }
    
    @Test
    void testPartialMatch() throws InterruptedException {
        String symbol = "MSFT";
        CountDownLatch latch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> latch.countDown());
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> latch.countDown());
        
        // Add a sell order for 100 shares at 28000
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            100L, 28000L, 999L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sellOrder
        ));
        
        Thread.sleep(50);
        
        // Add a buy order for 50 shares at 28000 (partial match)
        OrderEvent buyOrder = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            50L, 28000L, 888L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, buyOrder
        ));
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify book state - should have 50 remaining on sell side
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(1, book.getOrderCount());
        assertEquals(28000L, book.getBestAsk());
        assertEquals(50L, book.getBestAskQuantity());
    }
    
    @Test
    void testNoMatch() throws InterruptedException {
        String symbol = "GOOGL";
        
        // Add a sell order at 28000
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            100L, 28000L, 999L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sellOrder
        ));
        
        Thread.sleep(50);
        
        // Add a buy order at 27000 (no match - prices don't cross)
        OrderEvent buyOrder = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 27000L, 888L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, buyOrder
        ));
        
        Thread.sleep(50);
        
        // Both orders should be in the book
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(2, book.getOrderCount());
        assertEquals(27000L, book.getBestBid());
        assertEquals(28000L, book.getBestAsk());
    }
    
    @Test
    void testPriceTimePriority() throws InterruptedException {
        String symbol = "TSLA";
        CountDownLatch latch = new CountDownLatch(2);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> latch.countDown());
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> latch.countDown());
        
        // Add first sell order at 25000 (timestamp 1)
        OrderEvent sell1 = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            50L, 25000L, 999L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell1
        ));
        
        Thread.sleep(10);
        
        // Add second sell order at 25000 (timestamp 2)
        OrderEvent sell2 = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            50L, 25000L, 999L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell2
        ));
        
        Thread.sleep(10);
        
        // Add buy order for 50 shares - should match first sell order (FIFO)
        OrderEvent buy = OrderEvent.newOrder(
            3L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            50L, 25000L, 888L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 3L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, buy
        ));
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify only second sell order remains
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(1, book.getOrderCount());
        com.thelastwar.orderbook.Order remaining = book.getOrder(2L);
        assertNotNull(remaining);
        assertEquals(50L, remaining.quantity());
    }
    
    @Test
    void testMultipleMatches() throws InterruptedException {
        String symbol = "AMZN";
        CountDownLatch latch = new CountDownLatch(3); // Expect multiple fills
        AtomicInteger fillCount = new AtomicInteger(0);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            fillCount.incrementAndGet();
            latch.countDown();
        });
        
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> {
            fillCount.incrementAndGet();
            latch.countDown();
        });
        
        // Add three sell orders
        for (int i = 1; i <= 3; i++) {
            OrderEvent sell = OrderEvent.newOrder(
                (long) i, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
                30L, 30000L, 999L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell
            ));
            Thread.sleep(10);
        }
        
        Thread.sleep(50);
        
        // Add buy order for 90 shares - should match all three
        OrderEvent buy = OrderEvent.newOrder(
            100L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            90L, 30000L, 888L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 100L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, buy
        ));
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify multiple fills occurred
        assertTrue(fillCount.get() >= 3);
        
        // Order book should be empty
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getOrderCount());
    }
    
    @Test
    void testSequenceTracking() throws InterruptedException {
        String symbol = "IBM";
        
        long initialSeq = engine.getCurrentSequence();
        
        // Process some orders
        for (int i = 1; i <= 5; i++) {
            OrderEvent order = OrderEvent.newOrder(
                (long) i, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L + i, 999L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, order
            ));
            Thread.sleep(10);
        }
        
        Thread.sleep(100);
        
        // Sequence should have incremented
        long finalSeq = engine.getCurrentSequence();
        assertTrue(finalSeq > initialSeq);
    }
    
    @Test
    void testMetrics() throws InterruptedException {
        String symbol = "NFLX";
        
        // Add some orders
        OrderEvent sell = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            100L, 40000L, 999L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell
        ));
        
        Thread.sleep(50);
        
        OrderEvent buy = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 40000L, 888L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, buy
        ));
        
        Thread.sleep(100);
        
        var metrics = engine.getMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.symbolCount() >= 1);
        assertTrue(metrics.currentSequence() > 0);
    }
    
    @Test
    void testMultipleSymbols() throws InterruptedException {
        // Add orders for different symbols
        String[] symbols = {"AAPL", "MSFT", "GOOGL"};
        
        for (String symbol : symbols) {
            OrderEvent order = OrderEvent.newOrder(
                System.nanoTime(), symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), System.nanoTime(), SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, order
            ));
            Thread.sleep(10);
        }
        
        Thread.sleep(100);
        
        // Verify separate order books
        for (String symbol : symbols) {
            LimitOrderBook book = engine.getOrderBook(symbol);
            assertNotNull(book);
            assertEquals(symbol, book.getSymbol());
        }
        
        var metrics = engine.getMetrics();
        assertEquals(3, metrics.symbolCount());
    }
}
