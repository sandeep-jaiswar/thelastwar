package com.thelastwar.matching.benchmark;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.matching.CacheWarmingService;
import com.thelastwar.matching.MatchingEngine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for CacheWarmingService performance.
 * 
 * Target: < 1 µs latency impact (< 1000 ns)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class CacheWarmingServiceBenchmark {
    
    private EventBus eventBus;
    private MatchingEngine engine;
    private CacheWarmingService cacheWarming;
    private SimpleMeterRegistry meterRegistry;
    private long orderIdCounter;
    
    @Setup(Level.Trial)
    public void setupTrial() {
        eventBus = new SimpleEventBus();
        eventBus.start();
        
        engine = new MatchingEngine(eventBus);
        engine.start();
        
        meterRegistry = new SimpleMeterRegistry();
        cacheWarming = new CacheWarmingService(engine, eventBus, meterRegistry);
        cacheWarming.start();
        
        orderIdCounter = 0;
    }
    
    @TearDown(Level.Trial)
    public void tearDownTrial() {
        if (cacheWarming != null) {
            cacheWarming.stop();
        }
        if (engine != null) {
            engine.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
    }
    
    /**
     * Benchmark: Publish order event with cache warming service active.
     * Target: < 1 µs (1000 ns) latency impact from cache warming
     */
    @Benchmark
    public void publishOrderWithCacheWarming(Blackhole bh) {
        OrderEvent orderEvent = OrderEvent.newOrder(
            ++orderIdCounter,
            "AAPL",
            OrderEvent.SIDE_BUY,
            OrderEvent.TYPE_LIMIT,
            100L,
            15000L,
            1000L,
            1
        );
        
        Event event = Event.create(
            System.nanoTime(),
            eventBus.getCurrentSequence() + 1,
            SourceId.OMS,
            EventType.ORDER_SUBMITTED,
            0L,
            orderEvent
        );
        
        bh.consume(eventBus.publish(event));
    }
    
    /**
     * Benchmark: Publish order event without cache warming (baseline).
     */
    @Benchmark
    public void publishOrderBaseline(Blackhole bh) {
        OrderEvent orderEvent = OrderEvent.newOrder(
            ++orderIdCounter,
            "AAPL",
            OrderEvent.SIDE_BUY,
            OrderEvent.TYPE_LIMIT,
            100L,
            15000L,
            1000L,
            1
        );
        
        Event event = Event.create(
            System.nanoTime(),
            eventBus.getCurrentSequence() + 1,
            SourceId.OMS,
            EventType.ORDER_SUBMITTED,
            0L,
            orderEvent
        );
        
        bh.consume(eventBus.publish(event));
    }
    
    /**
     * Benchmark: Get cache warming metrics.
     */
    @Benchmark
    public void getMetrics(Blackhole bh) {
        bh.consume(cacheWarming.getMetrics());
    }
    
    /**
     * Simple synchronous event bus for benchmarking (no queue overhead).
     */
    private static class SimpleEventBus implements EventBus {
        private final java.util.List<EventHandler<?>>[] handlers;
        private final java.util.concurrent.atomic.AtomicLong publishedCount = 
            new java.util.concurrent.atomic.AtomicLong(0);
        private volatile boolean running;
        
        @SuppressWarnings("unchecked")
        public SimpleEventBus() {
            this.handlers = new java.util.List[10000];
            for (int i = 0; i < handlers.length; i++) {
                handlers[i] = new java.util.concurrent.CopyOnWriteArrayList<>();
            }
        }
        
        @Override
        public boolean publish(Event event) {
            if (!running) return false;
            
            publishedCount.incrementAndGet();
            
            int eventType = event.eventType();
            if (eventType < 0 || eventType >= handlers.length) {
                return false;
            }
            
            for (EventHandler<?> handler : handlers[eventType]) {
                try {
                    @SuppressWarnings("unchecked")
                    EventHandler<Object> h = (EventHandler<Object>) handler;
                    h.onEvent(event);
                } catch (Exception e) {
                    // Swallow exceptions
                }
            }
            
            return true;
        }
        
        @Override
        public Subscription subscribe(int eventType, EventHandler<?> handler) {
            if (eventType < 0 || eventType >= handlers.length) {
                throw new IllegalArgumentException("Invalid event type");
            }
            
            handlers[eventType].add(handler);
            
            return new Subscription() {
                private volatile boolean active = true;
                
                @Override
                public void unsubscribe() {
                    active = false;
                    handlers[eventType].remove(handler);
                }
                
                @Override
                public boolean isActive() {
                    return active;
                }
            };
        }
        
        @Override
        public long getPublishedEventCount() {
            return publishedCount.get();
        }
        
        @Override
        public int getSubscriberCount(int eventType) {
            if (eventType < 0 || eventType >= handlers.length) {
                return 0;
            }
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
    }
}
