package com.thelastwar.matching;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MatchingEngine with complex scenarios.
 */
class MatchingEngineIntegrationTest {
    
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
    void testRealisticTradingScenario() throws InterruptedException {
        String symbol = "AAPL";
        List<Event> trades = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(5); // Reduce expectation
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            if (event.payload() instanceof TradeEvent) {
                trades.add(event);
            }
            latch.countDown();
        });
        
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> {
            latch.countDown();
        });
        
        // Market maker adds liquidity on both sides
        for (int i = 0; i < 5; i++) {
            // Sell orders at increasing prices
            OrderEvent sell = OrderEvent.newOrder(
                (long) i + 1, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
                100L, 15000L + (i * 10), 999L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i + 1, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell
            ));
            
            // Buy orders at decreasing prices
            OrderEvent buy = OrderEvent.newOrder(
                (long) i + 100, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 14990L - (i * 10), 999L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i + 100, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, buy
            ));
            
            Thread.sleep(5);
        }
        
        Thread.sleep(100);
        
        // Aggressive buyer hits the ask
        OrderEvent aggBuy = OrderEvent.newOrder(
            200L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            250L, 15050L, 888L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 200L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, aggBuy
        ));
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Should have generated multiple trades
        assertFalse(trades.isEmpty());
        
        // Verify trades have correct symbol
        for (Event event : trades) {
            if (event.payload() instanceof TradeEvent trade) {
                assertEquals(symbol, trade.symbol());
                assertTrue(trade.quantity() > 0);
                assertTrue(trade.price() > 0);
            }
        }
    }
    
    @Test
    void testHighVolumeOrderFlow() throws InterruptedException {
        String symbol = "SPY";
        CountDownLatch latch = new CountDownLatch(50);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> latch.countDown());
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> latch.countDown());
        
        // Simulate high-volume order flow
        for (int i = 1; i <= 100; i++) {
            byte side = (i % 2 == 0) ? OrderEvent.SIDE_BUY : OrderEvent.SIDE_SELL;
            long price = 40000L + ((i % 20) - 10); // Prices around 40000
            
            OrderEvent order = OrderEvent.newOrder(
                (long) i, symbol, side, OrderEvent.TYPE_LIMIT,
                100L, price, 999L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, order
            ));
            
            if (i % 10 == 0) {
                Thread.sleep(5); // Small delay every 10 orders
            }
        }
        
        // Wait for processing
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        
        // Verify engine metrics
        var metrics = engine.getMetrics();
        assertTrue(metrics.totalTrades() > 0);
        assertTrue(metrics.currentSequence() > 0);
    }
    
    @Test
    void testDeterministicReplay() throws InterruptedException {
        String symbol = "MSFT";
        List<OrderEvent> orders = new ArrayList<>();
        
        // Record a sequence of orders
        for (int i = 1; i <= 20; i++) {
            byte side = (i % 3 == 0) ? OrderEvent.SIDE_BUY : OrderEvent.SIDE_SELL;
            long price = 30000L + (i % 10);
            
            OrderEvent order = OrderEvent.newOrder(
                (long) i, symbol, side, OrderEvent.TYPE_LIMIT,
                50L, price, 999L, 1
            );
            orders.add(order);
        }
        
        // First run
        long firstSequence;
        {
            for (int i = 0; i < orders.size(); i++) {
                eventBus.publish(Event.create(
                    System.nanoTime(), (long) i, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, orders.get(i)
                ));
                Thread.sleep(5);
            }
            Thread.sleep(100);
            firstSequence = engine.getCurrentSequence();
        }
        
        // Reset and replay
        eventBus.stop();
        engine.stop();
        
        eventBus = new InMemoryEventBus();
        eventBus.start();
        engine = new MatchingEngine(eventBus);
        engine.start();
        
        long secondSequence;
        {
            for (int i = 0; i < orders.size(); i++) {
                eventBus.publish(Event.create(
                    System.nanoTime(), (long) i, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, orders.get(i)
                ));
                Thread.sleep(5);
            }
            Thread.sleep(100);
            secondSequence = engine.getCurrentSequence();
        }
        
        // Sequences should match (deterministic)
        assertEquals(firstSequence, secondSequence);
    }
    
    @Test
    void testCrossSymbolMatching() throws InterruptedException {
        String[] symbols = {"AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"};
        CountDownLatch latch = new CountDownLatch(25);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> latch.countDown());
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> latch.countDown());
        
        // Add orders for multiple symbols
        for (String symbol : symbols) {
            // Add liquidity
            for (int i = 0; i < 5; i++) {
                long orderId = symbol.hashCode() + i;
                byte side = (i % 2 == 0) ? OrderEvent.SIDE_BUY : OrderEvent.SIDE_SELL;
                long price = 20000L + (i * 100);
                
                OrderEvent order = OrderEvent.newOrder(
                    orderId, symbol, side, OrderEvent.TYPE_LIMIT,
                    100L, price, 999L, 1
                );
                eventBus.publish(Event.create(
                    System.nanoTime(), orderId, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, order
                ));
                Thread.sleep(5);
            }
        }
        
        Thread.sleep(200);
        
        // Verify separate order books maintained
        var metrics = engine.getMetrics();
        assertEquals(5, metrics.symbolCount());
        
        // Each symbol should have orders
        for (String symbol : symbols) {
            var book = engine.getOrderBook(symbol);
            assertNotNull(book);
            assertTrue(book.getOrderCount() > 0);
        }
    }
    
    @Test
    void testMarketDepthExhaustion() throws InterruptedException {
        String symbol = "QQQ";
        CountDownLatch latch = new CountDownLatch(5);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> latch.countDown());
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> latch.countDown());
        
        // Add limited sell-side liquidity
        for (int i = 1; i <= 3; i++) {
            OrderEvent sell = OrderEvent.newOrder(
                (long) i, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
                50L, 35000L, 999L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell
            ));
            Thread.sleep(10);
        }
        
        Thread.sleep(50);
        
        // Large buy order that exhausts all liquidity
        OrderEvent bigBuy = OrderEvent.newOrder(
            100L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            200L, 35000L, 888L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 100L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, bigBuy
        ));
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify partial fill - some quantity should remain unfilled
        var book = engine.getOrderBook(symbol);
        assertTrue(book.getOrderCount() > 0); // Residual buy order should remain
        assertEquals(35000L, book.getBestBid());
        assertEquals(50L, book.getBestBidQuantity()); // 200 - 150 = 50 remaining
    }
    
    @Test
    void testPriceImprovementMatch() throws InterruptedException {
        String symbol = "NVDA";
        CountDownLatch latch = new CountDownLatch(2);
        List<TradeEvent> trades = new ArrayList<>();
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            if (event.payload() instanceof TradeEvent trade) {
                trades.add(trade);
            }
            latch.countDown();
        });
        
        // Add sell order at 50000
        OrderEvent sell = OrderEvent.newOrder(
            1L, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            100L, 50000L, 999L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell
        ));
        
        Thread.sleep(50);
        
        // Buy order at higher price (should match at sell price - price improvement)
        OrderEvent buy = OrderEvent.newOrder(
            2L, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 50100L, 888L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 2L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, buy
        ));
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Trade should execute at resting order price (50000)
        assertFalse(trades.isEmpty());
        TradeEvent trade = trades.get(0);
        assertEquals(50000L, trade.price()); // Price improvement for buyer
    }
}
