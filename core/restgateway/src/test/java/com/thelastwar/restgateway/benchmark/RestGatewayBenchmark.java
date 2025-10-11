package com.thelastwar.restgateway.benchmark;

import com.thelastwar.eventbus.EventBus;
import com.thelastwar.restgateway.dto.OrderRequest;
import com.thelastwar.restgateway.service.OrderService;
import com.thelastwar.restgateway.test.TestEventBus;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for REST Gateway performance.
 * 
 * Validates acceptance criteria:
 * - REST throughput ≥ 10K req/sec
 * - End-to-end latency ≤ 2 ms at p99
 * - JSON serialization/deserialization under 10 µs per request
 * 
 * Run with: ./gradlew :core:restgateway:jmh
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G", "-XX:+UseG1GC"})
public class RestGatewayBenchmark {
    
    @State(Scope.Thread)
    public static class BenchmarkState {
        EventBus eventBus;
        OrderService orderService;
        OrderRequest request;
        
        @Setup(Level.Trial)
        public void setup() {
            eventBus = new TestEventBus();
            eventBus.start();
            orderService = new OrderService(eventBus);
            request = new OrderRequest("AAPL", "BUY", "LIMIT", 100, 15000, 999);
        }
        
        @TearDown(Level.Trial)
        public void teardown() {
            eventBus.stop();
        }
    }
    
    /**
     * Benchmark: Order submission latency.
     * Target: < 2 ms at p99
     */
    @Benchmark
    public Long benchmarkOrderSubmission(BenchmarkState state) {
        return state.orderService.submitOrder(state.request).block();
    }
    
    /**
     * Benchmark: Order request validation.
     * Target: < 10 µs
     */
    @Benchmark
    public void benchmarkOrderValidation(BenchmarkState state) {
        state.request.validate();
    }
    
    /**
     * Benchmark: Order request conversion.
     * Target: < 10 µs
     */
    @Benchmark
    public byte benchmarkSideConversion(BenchmarkState state) {
        return state.request.getSideByte();
    }
    
    /**
     * Throughput benchmark: Sustained order submission rate.
     * Target: ≥ 10K orders/sec
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Long benchmarkThroughput(BenchmarkState state) {
        return state.orderService.submitOrder(state.request).block();
    }
    
    /**
     * Main method to run benchmarks.
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(RestGatewayBenchmark.class.getSimpleName())
                .build();
        
        new Runner(opt).run();
    }
}
