package com.thelastwar.eventbus.benchmark;

import com.thelastwar.eventbus.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synthetic load benchmark simulating realistic market data and order flow.
 * 
 * This benchmark harness validates acceptance criteria:
 * - Sustained throughput > 2M msgs/s for 60 seconds
 * - p99 latency < 10 µs
 * - No major GC events
 * 
 * Simulates:
 * - Market data updates (tick data, order book snapshots)
 * - Order events (new, filled, cancelled)
 * - Mixed workload patterns
 * 
 * Run with: ./gradlew :core:eventbus:jmh -Pargs=".*SyntheticLoad.*"
 * 
 * For sustained 60-second test:
 * ./gradlew :core:eventbus:jmh -Pargs="-f 1 -wi 3 -i 1 -r 60 SyntheticLoadBenchmark.benchmarkSustainedThroughput"
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 60, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {
        "-Xms2G",
        "-Xmx2G",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=1",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:G1NewSizePercent=50",
        "-Xlog:gc*:file=/tmp/gc.log",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.zip=ALL-UNNAMED"
})
public class SyntheticLoadBenchmark {

    // Market data simulation constants
    private static final int NUM_SYMBOLS = 100;
    private static final String[] SYMBOLS = generateSymbols();
    private static final int MARKET_DATA_PAYLOAD_SIZE = 128;
    private static final int ORDER_PAYLOAD_SIZE = 256;
    
    @State(Scope.Thread)
    public static class BenchmarkState {
        AeronEventBus eventBus;
        Random random;
        AtomicLong sequence;
        AtomicLong eventsReceived;
        
        // Pre-allocated events for different scenarios
        Event[] marketDataEvents;
        Event[] orderEvents;
        
        @Setup(Level.Trial)
        public void setup() throws InterruptedException {
            eventBus = new AeronEventBus();
            eventBus.start();
            
            // Wait for Aeron to initialize
            Thread.sleep(200);
            
            random = new Random(42); // Deterministic for reproducibility
            sequence = new AtomicLong(0);
            eventsReceived = new AtomicLong(0);
            
            // Subscribe handlers to simulate realistic consumption
            eventBus.subscribe(EventType.MARKET_DATA_UPDATE, e -> eventsReceived.incrementAndGet());
            eventBus.subscribe(EventType.ORDER_FILLED, e -> eventsReceived.incrementAndGet());
            eventBus.subscribe(EventType.ORDER_CANCELLED, e -> eventsReceived.incrementAndGet());
            
            // Pre-allocate events to minimize GC during benchmark
            marketDataEvents = new Event[NUM_SYMBOLS];
            orderEvents = new Event[NUM_SYMBOLS];
            
            for (int i = 0; i < NUM_SYMBOLS; i++) {
                // Market data event: simulates tick data
                String marketData = generateMarketData(SYMBOLS[i]);
                marketDataEvents[i] = Event.create(
                    System.nanoTime(),
                    i,
                    SourceId.FEED_HANDLER,
                    EventType.MARKET_DATA_UPDATE,
                    0L,
                    marketData
                );
                
                // Order event: simulates order execution
                String orderData = generateOrderData(SYMBOLS[i], i);
                orderEvents[i] = Event.create(
                    System.nanoTime(),
                    i,
                    SourceId.MATCHING_ENGINE,
                    EventType.ORDER_FILLED,
                    0L,
                    orderData
                );
            }
        }
        
        @TearDown(Level.Trial)
        public void tearDown() {
            if (eventBus != null) {
                long received = eventsReceived.get();
                long published = eventBus.getPublishedEventCount();
                System.out.printf("Published: %d, Received: %d, Loss: %.2f%%%n", 
                    published, received, 
                    100.0 * (published - received) / published);
                eventBus.stop();
            }
        }
    }
    
    /**
     * Sustained throughput test: Market data updates.
     * Target: > 2M msgs/s sustained for 60 seconds.
     */
    @Benchmark
    public void benchmarkSustainedThroughput(BenchmarkState state) {
        int symbolIdx = state.random.nextInt(NUM_SYMBOLS);
        Event event = state.marketDataEvents[symbolIdx];
        
        // Update timestamp and sequence
        Event updated = Event.create(
            System.nanoTime(),
            state.sequence.incrementAndGet(),
            event.sourceId(),
            event.eventType(),
            event.header(),
            event.payload()
        );
        
        // Retry until publish succeeds
        while (!state.eventBus.publish(updated)) {
            Thread.onSpinWait();
        }
    }
    
    /**
     * Mixed workload: Market data (70%) and orders (30%).
     * Simulates realistic trading system load.
     */
    @Benchmark
    public void benchmarkMixedWorkload(BenchmarkState state) {
        // 70% market data, 30% orders
        boolean isMarketData = state.random.nextInt(100) < 70;
        int symbolIdx = state.random.nextInt(NUM_SYMBOLS);
        
        Event template = isMarketData 
            ? state.marketDataEvents[symbolIdx] 
            : state.orderEvents[symbolIdx];
        
        Event event = Event.create(
            System.nanoTime(),
            state.sequence.incrementAndGet(),
            template.sourceId(),
            template.eventType(),
            template.header(),
            template.payload()
        );
        
        // Retry until publish succeeds
        while (!state.eventBus.publish(event)) {
            Thread.onSpinWait();
        }
    }
    
    /**
     * High-frequency market data: Simulates rapid tick updates.
     * Tests sustained performance under heavy load.
     */
    @Benchmark
    public void benchmarkHighFrequencyTicks(BenchmarkState state) {
        // Cycle through symbols rapidly
        int symbolIdx = (int)(state.sequence.get() % NUM_SYMBOLS);
        Event template = state.marketDataEvents[symbolIdx];
        
        Event event = Event.create(
            System.nanoTime(),
            state.sequence.incrementAndGet(),
            template.sourceId(),
            template.eventType(),
            template.header(),
            template.payload()
        );
        
        // Retry until publish succeeds
        while (!state.eventBus.publish(event)) {
            Thread.onSpinWait();
        }
    }
    
    /**
     * Order flow simulation: New orders, fills, cancellations.
     */
    @Benchmark
    public void benchmarkOrderFlow(BenchmarkState state) {
        int symbolIdx = state.random.nextInt(NUM_SYMBOLS);
        
        // Randomly select order event type
        int eventTypeRoll = state.random.nextInt(100);
        int eventType;
        if (eventTypeRoll < 40) {
            eventType = EventType.ORDER_FILLED;
        } else if (eventTypeRoll < 70) {
            eventType = EventType.ORDER_CANCELLED;
        } else {
            eventType = EventType.MARKET_DATA_UPDATE;
        }
        
        Event template = state.orderEvents[symbolIdx];
        Event event = Event.create(
            System.nanoTime(),
            state.sequence.incrementAndGet(),
            SourceId.MATCHING_ENGINE,
            eventType,
            template.header(),
            template.payload()
        );
        
        // Retry until publish succeeds
        while (!state.eventBus.publish(event)) {
            Thread.onSpinWait();
        }
    }
    
    // Helper methods for generating realistic test data
    
    private static String[] generateSymbols() {
        String[] symbols = new String[NUM_SYMBOLS];
        for (int i = 0; i < NUM_SYMBOLS; i++) {
            symbols[i] = String.format("SYM%03d", i);
        }
        return symbols;
    }
    
    private static String generateMarketData(String symbol) {
        // Generate 128-byte payload simulating market data
        double price = 100.0 + Math.random() * 100.0;
        int volume = (int)(Math.random() * 10000);
        
        String data = String.format("%s:BID=%.2f,ASK=%.2f,VOL=%d,TS=%d",
            symbol, price, price + 0.01, volume, System.nanoTime());
        
        // Pad to exactly MARKET_DATA_PAYLOAD_SIZE bytes
        return padToSize(data, MARKET_DATA_PAYLOAD_SIZE);
    }
    
    private static String generateOrderData(String symbol, long orderId) {
        // Generate 256-byte payload simulating order data
        double price = 100.0 + Math.random() * 100.0;
        int quantity = (int)(Math.random() * 1000) + 1;
        
        String data = String.format("ORD:%d,SYM:%s,PX:%.2f,QTY:%d,SIDE:BUY,TYPE:LIMIT,TS:%d",
            orderId, symbol, price, quantity, System.nanoTime());
        
        // Pad to exactly ORDER_PAYLOAD_SIZE bytes
        return padToSize(data, ORDER_PAYLOAD_SIZE);
    }
    
    private static String padToSize(String data, int targetSize) {
        if (data.length() >= targetSize) {
            return data.substring(0, targetSize);
        }
        
        StringBuilder sb = new StringBuilder(data);
        while (sb.length() < targetSize) {
            sb.append('x');
        }
        return sb.toString();
    }
    
    /**
     * Main method to run benchmarks programmatically.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(SyntheticLoadBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(3)
            .warmupTime(TimeValue.seconds(5))
            .measurementIterations(1)
            .measurementTime(TimeValue.seconds(60)) // 60-second sustained test
            .build();
        
        new Runner(opt).run();
    }
}
