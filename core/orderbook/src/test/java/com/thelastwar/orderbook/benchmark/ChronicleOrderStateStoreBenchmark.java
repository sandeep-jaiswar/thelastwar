package com.thelastwar.orderbook.benchmark;

import com.thelastwar.orderbook.ChronicleOrderStateStore;
import com.thelastwar.orderbook.Order;
import com.thelastwar.orderbook.OrderStateStore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for ChronicleOrderStateStore performance.
 * 
 * Verifies acceptance criteria:
 * - Read latency < 2 µs
 * - Write latency < 2 µs
 * 
 * Run with:
 * ./gradlew :core:orderbook:jmh -Pargs="ChronicleOrderStateStoreBenchmark"
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ChronicleOrderStateStoreBenchmark {
    
    private OrderStateStore store;
    private Order testOrder;
    private long orderId;
    
    @Setup(Level.Trial)
    public void setup() throws IOException {
        store = ChronicleOrderStateStore.createInMemory(100000);
        
        // Pre-populate with test data
        for (long i = 1; i <= 10000; i++) {
            store.put(new Order(i, "AAPL", Order.SIDE_BUY, 15000L + i, 100L, i * 1000));
        }
        
        testOrder = new Order(50000L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        orderId = 5000L;
    }
    
    @TearDown(Level.Trial)
    public void teardown() {
        if (store != null && !store.isClosed()) {
            try {
                store.close();
            } catch (IOException e) {
                // Ignore in teardown
            }
        }
    }
    
    @Benchmark
    public void benchmarkPut(Blackhole blackhole) {
        long id = System.nanoTime() & 0x7FFFFFFFL; // Ensure positive
        Order order = new Order(id, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        store.put(order);
        blackhole.consume(order);
    }
    
    @Benchmark
    public void benchmarkGet(Blackhole blackhole) {
        Order order = store.get(orderId);
        blackhole.consume(order);
    }
    
    @Benchmark
    public void benchmarkContainsKey(Blackhole blackhole) {
        boolean exists = store.containsKey(orderId);
        blackhole.consume(exists);
    }
    
    @Benchmark
    public void benchmarkUpdate(Blackhole blackhole) {
        Order updated = new Order(orderId, "AAPL", Order.SIDE_BUY, 15000L, 200L, System.nanoTime());
        store.put(updated);
        blackhole.consume(updated);
    }
    
    @Benchmark
    public void benchmarkSize(Blackhole blackhole) {
        long size = store.size();
        blackhole.consume(size);
    }
}
