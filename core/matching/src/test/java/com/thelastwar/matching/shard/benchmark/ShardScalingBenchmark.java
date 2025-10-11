package com.thelastwar.matching.shard.benchmark;

import com.thelastwar.matching.shard.*;
import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.*;
import java.util.concurrent.*;

/**
 * JMH benchmark to verify linear throughput scaling with sharding.
 * 
 * Tests:
 * - Single shard baseline throughput
 * - 2 shards (should be ~2x throughput)
 * - 4 shards (should be ~4x throughput)
 * - 8 shards (should be ~8x throughput)
 * 
 * Run with: ./gradlew :core:matching:jmh -Pargs="ShardScalingBenchmark"
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ShardScalingBenchmark {
    
    private EventBus eventBus;
    private ShardRegistry registry;
    private ShardRouter router;
    private ShardManager manager;
    private List<String> instruments;
    
    @Param({"1", "2", "4", "8"})
    private int numShards;
    
    @Setup(Level.Trial)
    public void setup() {
        // Create event bus
        eventBus = new TestEventBus();
        eventBus.start();
        
        // Create registry and router
        registry = new InMemoryShardRegistry();
        router = new ConsistentHashRouter();
        
        // Create shard manager
        manager = new ShardManager(eventBus, registry, router);
        manager.start();
        
        // Add shards
        for (int i = 0; i < numShards; i++) {
            manager.addShard(new ShardId(i), i, Set.of());
        }
        
        // Pre-generate instrument list for consistent routing
        instruments = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            instruments.add("SYM" + i);
        }
    }
    
    @TearDown(Level.Trial)
    public void teardown() {
        if (manager != null) {
            manager.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
    }
    
    @Benchmark
    public void orderProcessing(BenchmarkState state) {
        String instrument = instruments.get((int)(state.counter % instruments.size()));
        state.counter++;
        
        OrderEvent order = OrderEvent.newOrder(
            state.counter,
            instrument,
            OrderEvent.SIDE_BUY,
            OrderEvent.TYPE_LIMIT,
            100L,
            10000L,
            999L,
            1
        );
        
        Event event = Event.create(
            System.nanoTime(),
            state.counter,
            SourceId.REST_GATEWAY,
            EventType.ORDER_SUBMITTED,
            0L,
            order
        );
        
        eventBus.publish(event);
    }
    
    @State(Scope.Thread)
    public static class BenchmarkState {
        long counter = 0;
    }
    
    /**
     * Simple in-memory event bus for benchmarking.
     */
    static class TestEventBus implements EventBus {
        private final Map<Integer, List<EventHandler<?>>> handlers = new ConcurrentHashMap<>();
        private volatile boolean running = false;
        
        @Override
        public boolean publish(Event event) {
            if (!running) return false;
            
            List<EventHandler<?>> eventHandlers = handlers.get(event.eventType());
            if (eventHandlers != null) {
                for (EventHandler<?> handler : eventHandlers) {
                    try {
                        @SuppressWarnings("unchecked")
                        EventHandler<Object> h = (EventHandler<Object>) handler;
                        h.onEvent(event);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            return true;
        }
        
        @Override
        public Subscription subscribe(int eventType, EventHandler<?> handler) {
            handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
            return new Subscription() {
                @Override
                public void unsubscribe() {
                    List<EventHandler<?>> list = handlers.get(eventType);
                    if (list != null) {
                        list.remove(handler);
                    }
                }
                
                @Override
                public boolean isActive() {
                    List<EventHandler<?>> list = handlers.get(eventType);
                    return list != null && list.contains(handler);
                }
            };
        }
        
        @Override
        public long getPublishedEventCount() {
            return 0;
        }
        
        @Override
        public int getSubscriberCount(int eventType) {
            List<EventHandler<?>> list = handlers.get(eventType);
            return list != null ? list.size() : 0;
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
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(ShardScalingBenchmark.class.getSimpleName())
            .build();
        
        new Runner(opt).run();
    }
}
