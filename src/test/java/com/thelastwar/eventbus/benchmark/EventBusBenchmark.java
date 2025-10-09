package com.thelastwar.eventbus.benchmark;

import com.thelastwar.eventbus.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark for EventBus latency.
 * 
 * Run with: mvn test-compile exec:java -Dexec.mainClass="org.openjdk.jmh.Main" -Dexec.classpathScope=test
 * 
 * Or manually compile and run:
 * mvn clean test-compile
 * java -cp target/test-classes:target/classes:~/.m2/repository/org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar:~/.m2/repository/org/openjdk/jmh/jmh-generator-annprocess/1.37/jmh-generator-annprocess-1.37.jar org.openjdk.jmh.Main EventBusBenchmark
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G", "-XX:+UseG1GC"})
public class EventBusBenchmark {

    @State(Scope.Thread)
    public static class BenchmarkState {
        EventBus eventBus;
        Event event;
        volatile boolean eventReceived;

        @Setup(Level.Trial)
        public void setup() {
            eventBus = new InMemoryEventBus();
            eventBus.start();
            
            // Subscribe a simple handler
            eventBus.subscribe(EventType.MARKET_DATA_UPDATE, e -> eventReceived = true);
            
            // Pre-create event to avoid allocation in benchmark
            event = Event.create(
                System.nanoTime(),
                1L,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "benchmark"
            );
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            eventBus.stop();
        }
    }

    /**
     * Benchmark: Publish an event and measure latency.
     * Target: < 5000 nanoseconds (5 microseconds)
     */
    @Benchmark
    public boolean benchmarkPublish(BenchmarkState state) {
        return state.eventBus.publish(state.event);
    }

    /**
     * Benchmark: Publish and receive an event end-to-end.
     * Measures the full publish + dispatch + handler latency.
     */
    @Benchmark
    public void benchmarkPublishAndReceive(BenchmarkState state, Blackhole blackhole) {
        state.eventReceived = false;
        state.eventBus.publish(state.event);
        blackhole.consume(state.eventReceived);
    }

    /**
     * Benchmark: Subscribe operation.
     */
    @Benchmark
    public EventBus.Subscription benchmarkSubscribe(BenchmarkState state) {
        return state.eventBus.subscribe(EventType.ORDER_FILLED, e -> {});
    }

    /**
     * Benchmark: Event creation.
     * Measures the cost of creating an Event instance.
     */
    @Benchmark
    public Event benchmarkEventCreation() {
        return Event.create(
            System.nanoTime(),
            1L,
            SourceId.FEED_HANDLER,
            EventType.MARKET_DATA_UPDATE,
            0L,
            "data"
        );
    }

    /**
     * Baseline: Measure System.nanoTime() overhead.
     */
    @Benchmark
    public long benchmarkNanoTime() {
        return System.nanoTime();
    }

    /**
     * Benchmark: EventType validation.
     */
    @Benchmark
    public boolean benchmarkEventTypeValidation() {
        return EventType.isValid(EventType.MARKET_DATA_UPDATE);
    }

    /**
     * Main method to run the benchmark programmatically.
     */
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
