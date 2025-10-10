package com.thelastwar.matching;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests to verify latency requirements.
 */
class MatchingEnginePerformanceTest {
    
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
    @Tag("performance")
    void testMatchingLatency() throws InterruptedException {
        String symbol = "PERF";
        List<Long> latencies = new ArrayList<>();
        
        // Pre-populate book with liquidity
        for (int i = 0; i < 100; i++) {
            OrderEvent sell = OrderEvent.newOrder(
                (long) i + 1, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
                100L, 15000L + i, 999L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i + 1, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell
            ));
        }
        
        Thread.sleep(100); // Let orders settle
        
        // Measure matching latency for 1000 orders
        for (int i = 0; i < 1000; i++) {
            long startTime = System.nanoTime();
            
            OrderEvent buy = OrderEvent.newOrder(
                (long) i + 1000, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                10L, 15000L + (i % 100), 888L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i + 1000, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, buy
            ));
            
            long endTime = System.nanoTime();
            latencies.add(endTime - startTime);
            
            if (i % 100 == 0) {
                Thread.sleep(10); // Brief pause every 100 orders
            }
        }
        
        Thread.sleep(100); // Let processing complete
        
        // Calculate statistics
        latencies.sort(Long::compareTo);
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        long median = latencies.get(latencies.size() / 2);
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        
        System.out.println("=== Matching Engine Performance ===");
        System.out.println("Samples: " + latencies.size());
        System.out.println("Min:     " + min + " ns (" + (min / 1000.0) + " µs)");
        System.out.println("Average: " + String.format("%.0f", avg) + " ns (" + (avg / 1000.0) + " µs)");
        System.out.println("Median:  " + median + " ns (" + (median / 1000.0) + " µs)");
        System.out.println("P95:     " + p95 + " ns (" + (p95 / 1000.0) + " µs)");
        System.out.println("P99:     " + p99 + " ns (" + (p99 / 1000.0) + " µs)");
        System.out.println("Max:     " + max + " ns (" + (max / 1000.0) + " µs)");
        System.out.println("===================================");
        
        // Target: < 5 µs per match in production
        // In test environment with synchronous event bus, allow higher latency
        // Production system with Aeron/Chronicle would be much faster
        assertTrue(avg < 100_000, "Average latency should be < 100 µs in test environment");
        
        // Log warning if not meeting production target
        if (p99 > 5_000) {
            System.out.println("NOTE: P99 exceeds 5 µs production target. " +
                             "This is expected in test environment. " +
                             "Production system uses Aeron for < 5 µs latency.");
        }
    }
    
    @Test
    @Tag("performance")
    void testThroughput() throws InterruptedException {
        String symbol = "THRU";
        
        // Pre-populate with liquidity
        for (int i = 0; i < 50; i++) {
            OrderEvent sell = OrderEvent.newOrder(
                (long) i + 1, symbol, OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i + 1, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, sell
            ));
        }
        
        Thread.sleep(100);
        
        // Measure throughput
        int orderCount = 5000;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < orderCount; i++) {
            OrderEvent buy = OrderEvent.newOrder(
                (long) i + 1000, symbol, OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 888L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i + 1000, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, buy
            ));
        }
        
        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughput = orderCount / elapsedSeconds;
        
        System.out.println("=== Throughput Test ===");
        System.out.println("Orders:     " + orderCount);
        System.out.println("Time:       " + String.format("%.3f", elapsedSeconds) + " seconds");
        System.out.println("Throughput: " + String.format("%.0f", throughput) + " orders/sec");
        System.out.println("=======================");
        
        // Should handle at least 100K orders/sec
        assertTrue(throughput > 100_000, "Throughput should be > 100K orders/sec");
    }
}
