package com.thelastwar.eventbus.benchmark;

import com.thelastwar.eventbus.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for AeronEventBus.
 * 
 * Validates acceptance criteria:
 * - Sustained throughput ≥ 2M msgs/s on loopback
 * - p99 latency < 10 µs for small (128 B) payloads
 * - Zero heap allocations in publish path
 * 
 * Run with: ./gradlew :core:eventbus:jmh -Pargs=".*AeronBenchmark.*"
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {
    "-Xms2G", 
    "-Xmx2G", 
    "-XX:+UseG1GC",
    "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens", "java.base/java.util.zip=ALL-UNNAMED"
})
public class AeronBenchmark {
    
    @State(Scope.Thread)
    public static class BenchmarkState {
        AeronEventBus eventBus;
        Event event;
        Event smallEvent128B;
        volatile boolean eventReceived;
        CountDownLatch latch;
        
        @Setup(Level.Trial)
        public void setup() throws InterruptedException {
            eventBus = new AeronEventBus();
            eventBus.start();
            
            // Wait for Aeron to initialize
            Thread.sleep(200);
            
            // Subscribe a simple handler
            eventBus.subscribe(EventType.MARKET_DATA_UPDATE, e -> eventReceived = true);
            
            // Pre-create events to avoid allocation in benchmark
            event = Event.create(
                System.nanoTime(),
                1L,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "AAPL: $150.00"
            );
            
            // Create 128-byte payload event (approximately)
            StringBuilder payload = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                payload.append("x");
            }
            smallEvent128B = Event.create(
                System.nanoTime(),
                1L,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                payload.toString()
            );
        }
        
        @TearDown(Level.Trial)
        public void tearDown() {
            if (eventBus != null) {
                eventBus.stop();
            }
        }
    }
    
    /**
     * Benchmark: Publish a single event.
     * Target: Sub-10 microseconds (10000 ns)
     */
    @Benchmark
    public boolean benchmarkPublish(BenchmarkState state) {
        return state.eventBus.publish(state.event);
    }
    
    /**
     * Benchmark: Publish a 128-byte payload event.
     * Target: < 10 µs latency (per acceptance criteria)
     */
    @Benchmark
    public boolean benchmarkPublish128B(BenchmarkState state) {
        return state.eventBus.publish(state.smallEvent128B);
    }
    
    /**
     * Benchmark: Publish and receive end-to-end.
     * Measures full round-trip latency.
     */
    @Benchmark
    public boolean benchmarkPublishAndReceive(BenchmarkState state) throws InterruptedException {
        state.eventReceived = false;
        boolean published = state.eventBus.publish(state.event);
        
        // Spin-wait for event (more accurate than sleep)
        long spinStart = System.nanoTime();
        while (!state.eventReceived && (System.nanoTime() - spinStart) < 100_000) {
            Thread.onSpinWait();
        }
        
        return published && state.eventReceived;
    }
    
    /**
     * Benchmark: Event creation overhead.
     * Should be minimal (< 100 ns).
     */
    @Benchmark
    public Event benchmarkEventCreation(Blackhole blackhole) {
        return Event.create(
            System.nanoTime(),
            1L,
            SourceId.FEED_HANDLER,
            EventType.MARKET_DATA_UPDATE,
            0L,
            "AAPL: $150.00"
        );
    }
    
    /**
     * Benchmark: Subscribe operation.
     * Should be very fast.
     */
    @Benchmark
    public EventBus.Subscription benchmarkSubscribe(BenchmarkState state) {
        return state.eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {});
    }
    
    /**
     * Throughput benchmark: Sustained publishing rate.
     * Target: ≥ 2M msgs/s
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkThroughput(BenchmarkState state) {
        state.eventBus.publish(state.event);
    }
    
    /**
     * Throughput benchmark for 128B payloads.
     * Target: ≥ 2M msgs/s
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkThroughput128B(BenchmarkState state) {
        state.eventBus.publish(state.smallEvent128B);
    }
    
    /**
     * Latency distribution benchmark.
     * Measures p99 latency explicitly.
     */
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public boolean benchmarkLatencyDistribution(BenchmarkState state) {
        return state.eventBus.publish(state.smallEvent128B);
    }
    
    /**
     * Main method to run benchmarks programmatically.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(AeronBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(5)
            .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .measurementIterations(10)
            .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .build();
        
        new Runner(opt).run();
    }
}
