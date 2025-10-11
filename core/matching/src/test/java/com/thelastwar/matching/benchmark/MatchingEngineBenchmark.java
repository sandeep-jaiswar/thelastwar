package com.thelastwar.matching.benchmark;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;
import com.thelastwar.matching.*;
import com.thelastwar.orderbook.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for matching engine performance.
 * 
 * Target: < 5 µs per match (p99)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class MatchingEngineBenchmark {
    
    private EventBus eventBus;
    private MatchingEngine engine;
    private long orderIdCounter;
    
    @Setup(Level.Trial)
    public void setupTrial() {
        eventBus = new SimpleEventBus();
        eventBus.start();
        engine = new MatchingEngine(eventBus);
        engine.start();
        orderIdCounter = 1;
    }
    
    @TearDown(Level.Trial)
    public void tearDownTrial() {
        engine.stop();
        eventBus.stop();
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() {
        // Pre-populate order book with realistic market depth
        String symbol = "AAPL";
        LimitOrderBook book = engine.getOrderBook(symbol);
        book.clear();
        orderIdCounter = 1;
        
        // Add 50 buy orders at decreasing prices
        for (int i = 0; i < 50; i++) {
            OrderEvent buy = OrderEvent.newOrder(
                orderIdCounter++, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 14900L - i, 999L, 1
            );
            Event event = Event.create(
                System.nanoTime(), orderIdCounter, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, buy
            );
            eventBus.publish(event);
        }
        
        // Add 50 sell orders at increasing prices
        for (int i = 0; i < 50; i++) {
            OrderEvent sell = OrderEvent.newOrder(
                orderIdCounter++, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
                100L, 15100L + i, 999L, 1
            );
            Event event = Event.create(
                System.nanoTime(), orderIdCounter, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell
            );
            eventBus.publish(event);
        }
        
        // Give time for processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Benchmark: Publish order that matches immediately (hot path).
     * Target: < 5 µs (5000 ns)
     */
    @Benchmark
    public void matchingOrder(Blackhole bh) {
        long orderId = orderIdCounter++;
        OrderEvent order = OrderEvent.newOrder(
            orderId, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            50L, 15100L, 888L, 1
        );
        Event event = Event.create(
            System.nanoTime(), orderId, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, order
        );
        bh.consume(eventBus.publish(event));
    }
    
    /**
     * Benchmark: Publish order that does not match (adds to book).
     */
    @Benchmark
    public void nonMatchingOrder(Blackhole bh) {
        long orderId = orderIdCounter++;
        OrderEvent order = OrderEvent.newOrder(
            orderId, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            50L, 14800L, 888L, 1
        );
        Event event = Event.create(
            System.nanoTime(), orderId, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, order
        );
        bh.consume(eventBus.publish(event));
    }
    
    /**
     * Benchmark: Get order book for symbol (cache hit).
     */
    @Benchmark
    public void getOrderBook(Blackhole bh) {
        bh.consume(engine.getOrderBook("AAPL"));
    }
    
    /**
     * Benchmark: Get metrics.
     */
    @Benchmark
    public void getMetrics(Blackhole bh) {
        bh.consume(engine.getMetrics());
    }
    
    /**
     * Benchmark: Process order through onNewOrder interface (with OrderEnvelope).
     * Target: < 8 µs (8000 ns) median
     */
    @Benchmark
    public void onNewOrderInterface(Blackhole bh) {
        long orderId = orderIdCounter++;
        OrderEvent order = OrderEvent.newOrder(
            orderId, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            50L, 15100L, 888L, 1
        );
        OrderEnvelope envelope = OrderEnvelope.wrap(order, orderId, SourceId.OMS);
        engine.onNewOrder(envelope);
        bh.consume(orderId);
    }
    
    /**
     * Benchmark: Cancel order through onCancel interface.
     * Target: < 5 µs (5000 ns) median
     */
    @Benchmark
    public void onCancelInterface(Blackhole bh) {
        // Add an order first
        long orderId = orderIdCounter++;
        OrderEvent order = OrderEvent.newOrder(
            orderId, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            50L, 14800L, 888L, 1
        );
        OrderEnvelope envelope = OrderEnvelope.wrap(order, orderId, SourceId.OMS);
        engine.onNewOrder(envelope);
        
        // Now cancel it
        OrderCancel cancel = OrderCancel.create(orderId, "AAPL", 888L, orderId);
        engine.onCancel(cancel);
        bh.consume(orderId);
    }
    
    /**
     * Benchmark: Modify order through onReplace interface.
     * Target: < 10 µs (10000 ns) median
     */
    @Benchmark
    public void onReplaceInterface(Blackhole bh) {
        // Add an order first
        long orderId = orderIdCounter++;
        OrderEvent order = OrderEvent.newOrder(
            orderId, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            50L, 14800L, 888L, 1
        );
        OrderEnvelope envelope = OrderEnvelope.wrap(order, orderId, SourceId.OMS);
        engine.onNewOrder(envelope);
        
        // Now modify it
        OrderModify modify = OrderModify.modifyPrice(orderId, "AAPL", 14850L, 888L, orderId);
        engine.onReplace(modify);
        bh.consume(orderId);
    }
    
    /**
     * Benchmark: Process market data tick through onMarketDataUpdate interface.
     * Target: < 3 µs (3000 ns) median
     */
    @Benchmark
    public void onMarketDataUpdateInterface(Blackhole bh) {
        TickEvent tick = TickEvent.create(
            "AAPL", 14900L, 15100L, 15000L,
            1000L, 1000L, orderIdCounter++, 1
        );
        engine.onMarketDataUpdate(tick);
        bh.consume(tick);
    }
    
    /**
     * Simple synchronous event bus for benchmarking (no queue overhead).
     */
    private static class SimpleEventBus implements EventBus {
        private final java.util.List<HandlerRegistration>[] handlers;
        private final java.util.concurrent.atomic.AtomicLong publishedCount = new java.util.concurrent.atomic.AtomicLong(0);
        private volatile boolean running = false;
        
        @SuppressWarnings("unchecked")
        public SimpleEventBus() {
            this.handlers = new java.util.List[10000];
            for (int i = 0; i < handlers.length; i++) {
                handlers[i] = new java.util.ArrayList<>();
            }
        }
        
        @Override
        public boolean publish(Event event) {
            if (!running || event == null) return false;
            publishedCount.incrementAndGet();
            
            int eventType = event.eventType();
            if (eventType >= 0 && eventType < handlers.length) {
                for (HandlerRegistration reg : handlers[eventType]) {
                    try {
                        reg.handler.onEvent(event);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            return true;
        }
        
        @Override
        public Subscription subscribe(int eventType, EventHandler<?> handler) {
            HandlerRegistration reg = new HandlerRegistration(handler);
            handlers[eventType].add(reg);
            return new Subscription() {
                @Override
                public void unsubscribe() {
                    handlers[eventType].remove(reg);
                }
                
                @Override
                public boolean isActive() {
                    return true;
                }
            };
        }
        
        @Override
        public long getPublishedEventCount() {
            return publishedCount.get();
        }
        
        @Override
        public int getSubscriberCount(int eventType) {
            return handlers[eventType].size();
        }
        
        @Override
        public void start() {
            running = true;
        }
        
        @Override
        public void stop() {
            running = false;
        }
        
        private static class HandlerRegistration {
            final EventHandler<?> handler;
            HandlerRegistration(EventHandler<?> h) { this.handler = h; }
        }
    }
}
