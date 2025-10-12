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
 * Advanced tests for matching engine including IOC, FOK, and MARKET orders.
 */
class AdvancedMatchingTest {
    
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
    
    // =====================================
    // IOC (Immediate-Or-Cancel) Order Tests
    // =====================================
    
    @Test
    void testIOC_FullyFilled() throws InterruptedException {
        String symbol = "AAPL";
        CountDownLatch latch = new CountDownLatch(1);
        List<Event> receivedEvents = new ArrayList<>();
        
        // Subscribe to execution events
        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            receivedEvents.add(event);
            latch.countDown();
        });
        
        // Add a sell order at 15000
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sellOrder
        ));
        
        Thread.sleep(50);
        
        // Add an IOC buy order at 15000 (should match fully)
        OrderEvent iocBuyOrder = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 888L, 1, OrderEvent.TIF_IOC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, iocBuyOrder
        ));
        
        // Wait for events
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Expected fill event not received");
        
        // Order book should be empty after full match
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getOrderCount());
    }
    
    @Test
    void testIOC_PartialFill_CancelRemainder() throws InterruptedException {
        String symbol = "MSFT";
        CountDownLatch fillLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        List<Event> receivedEvents = new ArrayList<>();
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            receivedEvents.add(event);
            fillLatch.countDown();
        });
        
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> {
            receivedEvents.add(event);
            fillLatch.countDown();
        });
        
        eventBus.subscribe(EventType.ORDER_CANCELLED, event -> {
            receivedEvents.add(event);
            cancelLatch.countDown();
        });
        
        // Add a sell order for 50 shares at 28000
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            50L, 28000L, 999L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sellOrder
        ));
        
        Thread.sleep(50);
        
        // Add an IOC buy order for 100 shares (only 50 available)
        OrderEvent iocBuyOrder = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 28000L, 888L, 1, OrderEvent.TIF_IOC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, iocBuyOrder
        ));
        
        // Wait for fill and cancel events
        assertTrue(fillLatch.await(5, TimeUnit.SECONDS), "Expected fill event");
        assertTrue(cancelLatch.await(5, TimeUnit.SECONDS), "Expected cancel event for unfilled portion");
        
        // Verify book is empty (sell order filled, IOC cancelled)
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getOrderCount());
    }
    
    @Test
    void testIOC_NoMatch_ImmediatelyCancelled() throws InterruptedException {
        String symbol = "GOOGL";
        CountDownLatch cancelLatch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.ORDER_CANCELLED, event -> cancelLatch.countDown());
        
        // Add an IOC buy order at 27000 with no matching sell orders
        OrderEvent iocBuyOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 27000L, 888L, 1, OrderEvent.TIF_IOC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, iocBuyOrder
        ));
        
        // Should be immediately cancelled
        assertTrue(cancelLatch.await(5, TimeUnit.SECONDS), "IOC order should be cancelled immediately");
        
        // Verify book is empty
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getOrderCount());
    }
    
    // ======================================
    // FOK (Fill-Or-Kill) Order Tests
    // ======================================
    
    @Test
    void testFOK_FullyFilled() throws InterruptedException {
        String symbol = "TSLA";
        CountDownLatch latch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> latch.countDown());
        
        // Add a sell order for 100 shares at 25000
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            100L, 25000L, 999L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sellOrder
        ));
        
        Thread.sleep(50);
        
        // Add a FOK buy order for 100 shares (sufficient liquidity)
        OrderEvent fokBuyOrder = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 25000L, 888L, 1, OrderEvent.TIF_FOK
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, fokBuyOrder
        ));
        
        // Should be fully filled
        assertTrue(latch.await(5, TimeUnit.SECONDS), "FOK order should be fully filled");
        
        // Verify book is empty
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getOrderCount());
    }
    
    @Test
    void testFOK_InsufficientLiquidity_EntireOrderCancelled() throws InterruptedException {
        String symbol = "AMZN";
        CountDownLatch cancelLatch = new CountDownLatch(1);
        AtomicInteger fillCount = new AtomicInteger(0);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> fillCount.incrementAndGet());
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> fillCount.incrementAndGet());
        eventBus.subscribe(EventType.ORDER_CANCELLED, event -> cancelLatch.countDown());
        
        // Add a sell order for 50 shares at 30000
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            50L, 30000L, 999L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sellOrder
        ));
        
        Thread.sleep(50);
        
        // Add a FOK buy order for 100 shares (insufficient liquidity)
        OrderEvent fokBuyOrder = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 30000L, 888L, 1, OrderEvent.TIF_FOK
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, fokBuyOrder
        ));
        
        // Should be immediately cancelled without any fills
        assertTrue(cancelLatch.await(5, TimeUnit.SECONDS), "FOK order should be cancelled");
        assertEquals(0, fillCount.get(), "FOK order should not generate any fills");
        
        // Verify sell order is still in book (not filled)
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(1, book.getOrderCount());
        assertEquals(50L, book.getBestAskQuantity());
    }
    
    @Test
    void testFOK_MultipleOrders_FullyFilled() throws InterruptedException {
        String symbol = "NFLX";
        CountDownLatch latch = new CountDownLatch(3); // Expect 3 fills
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> latch.countDown());
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> latch.countDown());
        
        // Add three sell orders at 40000
        for (int i = 1; i <= 3; i++) {
            OrderEvent sell = OrderEvent.newOrder(
                (long) i, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
                30L, 40000L, 999L, 1, OrderEvent.TIF_GTC
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell
            ));
            Thread.sleep(10);
        }
        
        Thread.sleep(50);
        
        // Add a FOK buy order for 90 shares (sufficient liquidity across 3 orders)
        OrderEvent fokBuyOrder = OrderEvent.newOrder(
            100L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            90L, 40000L, 888L, 1, OrderEvent.TIF_FOK
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 100L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, fokBuyOrder
        ));
        
        // Should match all three orders
        assertTrue(latch.await(5, TimeUnit.SECONDS), "FOK order should match all orders");
        
        // Verify book is empty
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getOrderCount());
    }
    
    // ======================================
    // MARKET Order Tests
    // ======================================
    
    @Test
    void testMarket_Buy_MatchesAtBestAsk() throws InterruptedException {
        String symbol = "IBM";
        CountDownLatch latch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> latch.countDown());
        
        // Add a sell order at 15000
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sellOrder
        ));
        
        Thread.sleep(50);
        
        // Add a MARKET buy order (price doesn't matter)
        OrderEvent marketBuyOrder = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_MARKET,
            100L, 0L, 888L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, marketBuyOrder
        ));
        
        // Should match
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Market order should match");
        
        // Verify book is empty
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getOrderCount());
    }
    
    @Test
    void testMarket_Sell_MatchesAtBestBid() throws InterruptedException {
        String symbol = "AAPL";
        CountDownLatch latch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> latch.countDown());
        
        // Add a buy order at 15000
        OrderEvent buyOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, buyOrder
        ));
        
        Thread.sleep(50);
        
        // Add a MARKET sell order
        OrderEvent marketSellOrder = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_MARKET,
            100L, 0L, 888L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, marketSellOrder
        ));
        
        // Should match
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Market order should match");
        
        // Verify book is empty
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getOrderCount());
    }
    
    @Test
    void testMarket_PartialFill_ExhaustsLiquidity() throws InterruptedException {
        String symbol = "MSFT";
        CountDownLatch latch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> latch.countDown());
        eventBus.subscribe(EventType.ORDER_CANCELLED, event -> latch.countDown());
        
        // Add a sell order for 50 shares
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            50L, 28000L, 999L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sellOrder
        ));
        
        Thread.sleep(50);
        
        // Add a MARKET buy order for 100 shares (only 50 available)
        OrderEvent marketBuyOrder = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_MARKET,
            100L, 0L, 888L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, marketBuyOrder
        ));
        
        // Should partially fill and cancel remainder
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Market order should partially fill");
        
        // Verify book is empty
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getOrderCount());
    }
    
    @Test
    void testMarket_MultipleOrders_WalksTheBook() throws InterruptedException {
        String symbol = "GOOGL";
        CountDownLatch latch = new CountDownLatch(3);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> latch.countDown());
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> latch.countDown());
        
        // Add three sell orders at different prices
        OrderEvent sell1 = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            30L, 27000L, 999L, 1, OrderEvent.TIF_GTC
        );
        OrderEvent sell2 = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            30L, 27100L, 999L, 1, OrderEvent.TIF_GTC
        );
        OrderEvent sell3 = OrderEvent.newOrder(
            3L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            30L, 27200L, 999L, 1, OrderEvent.TIF_GTC
        );
        
        eventBus.publish(Event.create(System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell1));
        Thread.sleep(10);
        eventBus.publish(Event.create(System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell2));
        Thread.sleep(10);
        eventBus.publish(Event.create(System.nanoTime(), 3L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell3));
        Thread.sleep(50);
        
        // Add a MARKET buy order for 90 shares (should walk the book)
        OrderEvent marketBuyOrder = OrderEvent.newOrder(
            100L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_MARKET,
            90L, 0L, 888L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 100L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, marketBuyOrder
        ));
        
        // Should match all three orders
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Market order should walk the book");
        
        // Verify book is empty
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getOrderCount());
    }
    
    // ======================================
    // Combined Tests
    // ======================================
    
    @Test
    void testMarket_IOC_ImmediateOrCancel() throws InterruptedException {
        String symbol = "TSLA";
        CountDownLatch latch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> latch.countDown());
        eventBus.subscribe(EventType.ORDER_CANCELLED, event -> latch.countDown());
        
        // Add a sell order for 50 shares
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            50L, 25000L, 999L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sellOrder
        ));
        
        Thread.sleep(50);
        
        // Add a MARKET IOC buy order for 100 shares
        OrderEvent marketIOCOrder = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_MARKET,
            100L, 0L, 888L, 1, OrderEvent.TIF_IOC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, marketIOCOrder
        ));
        
        // Should partially fill and cancel
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Market IOC order should execute");
        
        // Verify book is empty
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(0, book.getOrderCount());
    }
    
    @Test
    void testMarket_FOK_FillOrKill() throws InterruptedException {
        String symbol = "AMZN";
        CountDownLatch cancelLatch = new CountDownLatch(1);
        AtomicInteger fillCount = new AtomicInteger(0);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> fillCount.incrementAndGet());
        eventBus.subscribe(EventType.ORDER_CANCELLED, event -> cancelLatch.countDown());
        
        // Add a sell order for 50 shares (insufficient)
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            50L, 30000L, 999L, 1, OrderEvent.TIF_GTC
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sellOrder
        ));
        
        Thread.sleep(50);
        
        // Add a MARKET FOK buy order for 100 shares
        OrderEvent marketFOKOrder = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_MARKET,
            100L, 0L, 888L, 1, OrderEvent.TIF_FOK
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, marketFOKOrder
        ));
        
        // Should be cancelled without any fills
        assertTrue(cancelLatch.await(5, TimeUnit.SECONDS), "Market FOK should be cancelled");
        assertEquals(0, fillCount.get(), "No fills should occur");
        
        // Verify sell order still in book
        LimitOrderBook book = engine.getOrderBook(symbol);
        assertEquals(1, book.getOrderCount());
    }
}
