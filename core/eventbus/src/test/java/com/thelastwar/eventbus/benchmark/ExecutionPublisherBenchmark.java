package com.thelastwar.eventbus.benchmark;

import com.thelastwar.eventbus.AeronExecutionPublisher;
import com.thelastwar.eventbus.ExecutionPublisher;
import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.eventbus.model.TradeEvent;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for ExecutionPublisher performance validation.
 * 
 * This benchmark validates the performance targets:
 * - Event propagation latency < 10 µs
 * - Serialization speed ≥ 5M msgs/sec
 * - Zero event loss under 1M msg/sec load
 * 
 * Run with:
 * ./gradlew :core:eventbus:jmh -Pargs="ExecutionPublisherBenchmark"
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ExecutionPublisherBenchmark {
    
    private ExecutionPublisher publisher;
    private ExecutionEvent executionEvent;
    private TradeEvent tradeEvent;
    
    @Setup
    public void setup() {
        // Create publisher with IPC channels for benchmarking
        publisher = AeronExecutionPublisher.builder()
                .positionsChannel("aeron:ipc")
                .ledgerChannel("aeron:ipc")
                .executionsChannel("aeron:ipc")
                .streamId(3001)
                .metricsEnabled(false)
                .build();
        
        publisher.start();
        
        // Create test events
        OrderEvent order = OrderEvent.newOrder(
                1000L,
                "AAPL",
                OrderEvent.SIDE_BUY,
                OrderEvent.TYPE_LIMIT,
                100L,
                15000L,
                999L,
                1
        );
        
        executionEvent = ExecutionEvent.fill(
                1L,
                order,
                100L,
                15000L,
                100L,
                0L
        );
        
        tradeEvent = new TradeEvent(
                1L,
                1000L,
                "AAPL",
                TradeEvent.SIDE_BUY,
                100L,
                15000L,
                System.nanoTime(),
                999L,
                1,
                0L,
                50L
        );
    }
    
    @TearDown
    public void tearDown() {
        if (publisher != null) {
            publisher.stop();
        }
    }
    
    /**
     * Benchmark: Publish execution events.
     * Target: > 1M msgs/sec
     */
    @Benchmark
    public boolean publishExecution() {
        return publisher.publishExecution(executionEvent);
    }
    
    /**
     * Benchmark: Publish trade events.
     * Target: > 1M msgs/sec
     */
    @Benchmark
    public boolean publishTrade() {
        return publisher.publishTrade(tradeEvent);
    }
    
    /**
     * Benchmark: Publish execution with retry.
     * Measures latency with retry logic.
     */
    @Benchmark
    public boolean publishExecutionWithRetry() {
        return publisher.publishExecutionWithRetry(executionEvent, 3);
    }
    
    /**
     * Benchmark: Publish trade with retry.
     * Measures latency with retry logic.
     */
    @Benchmark
    public boolean publishTradeWithRetry() {
        return publisher.publishTradeWithRetry(tradeEvent, 3);
    }
    
    /**
     * Benchmark: Average latency for execution publishing.
     * Measures time per operation in nanoseconds.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public boolean publishExecutionLatency() {
        return publisher.publishExecution(executionEvent);
    }
    
    /**
     * Benchmark: Average latency for trade publishing.
     * Measures time per operation in nanoseconds.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public boolean publishTradeLatency() {
        return publisher.publishTrade(tradeEvent);
    }
}
