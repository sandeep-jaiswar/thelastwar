package com.thelastwar.eventbus.example;

import com.thelastwar.eventbus.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Performance validation example for AeronEventBus.
 * 
 * This simple test validates that the implementation meets basic performance targets:
 * - Can publish events successfully
 * - Achieves reasonable throughput
 * - Maintains low latency
 * 
 * For comprehensive benchmarks, use: ./gradlew :core:eventbus:jmh
 */
public class AeronPerformanceExample {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Aeron EventBus Performance Validation ===\n");
        
        // Create and start event bus
        AeronEventBus eventBus = new AeronEventBus();
        eventBus.start();
        
        // Wait for initialization
        Thread.sleep(500);
        
        // Test 1: Basic Latency Test
        System.out.println("Test 1: Basic Publish Latency");
        testLatency(eventBus);
        
        // Test 2: Sustained Throughput
        System.out.println("\nTest 2: Sustained Throughput");
        testThroughput(eventBus, 100_000);
        
        // Test 3: High Throughput
        System.out.println("\nTest 3: High Throughput (1M events)");
        testThroughput(eventBus, 1_000_000);
        
        // Cleanup
        eventBus.stop();
        System.out.println("\n=== Validation Complete ===");
    }
    
    /**
     * Test average publish latency.
     */
    private static void testLatency(AeronEventBus eventBus) throws InterruptedException {
        final int iterations = 1000;
        final CountDownLatch latch = new CountDownLatch(iterations);
        
        // Subscribe
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> latch.countDown());
        
        // Warm up
        for (int i = 0; i < 1000; i++) {
            Event event = Event.create(
                System.nanoTime(),
                i,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "Warmup"
            );
            eventBus.publish(event);
        }
        Thread.sleep(100);
        
        // Measure
        long startNs = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Event event = Event.create(
                System.nanoTime(),
                i,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "AAPL: $150.00"
            );
            eventBus.publish(event);
        }
        
        // Wait for all events
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        long endNs = System.nanoTime();
        
        if (completed) {
            double avgLatencyUs = (endNs - startNs) / (double) iterations / 1000.0;
            System.out.printf("  Average latency: %.2f µs%n", avgLatencyUs);
            
            if (avgLatencyUs < 10.0) {
                System.out.println("  ✓ PASSED: Latency < 10 µs target");
            } else {
                System.out.println("  ⚠ Warning: Latency above 10 µs target");
            }
        } else {
            System.out.println("  ✗ FAILED: Timeout waiting for events");
        }
    }
    
    /**
     * Test sustained throughput.
     */
    private static void testThroughput(AeronEventBus eventBus, int eventCount) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(eventCount);
        
        // Subscribe with a clean handler
        EventBus.Subscription sub = eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            latch.countDown();
        });
        
        // Small warmup for this event type
        Thread.sleep(50);
        
        // Publish events in batches to avoid overwhelming the buffer
        long startNs = System.nanoTime();
        int batchSize = 10000;
        for (int i = 0; i < eventCount; i++) {
            Event event = Event.create(
                System.nanoTime(),
                i,
                SourceId.MATCHING_ENGINE,
                EventType.ORDER_FILLED,
                0L,
                "Event " + i
            );
            
            while (!eventBus.publish(event)) {
                // Back pressure - wait a bit
                Thread.yield();
            }
            
            // Small pause every batch to let consumer catch up
            if (i > 0 && i % batchSize == 0) {
                Thread.sleep(1);
            }
        }
        
        // Wait for completion
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        long endNs = System.nanoTime();
        
        sub.unsubscribe();
        
        if (completed) {
            double durationSec = (endNs - startNs) / 1_000_000_000.0;
            double throughput = eventCount / durationSec;
            
            System.out.printf("  Published: %,d events%n", eventCount);
            System.out.printf("  Duration: %.3f seconds%n", durationSec);
            System.out.printf("  Throughput: %,.0f events/sec%n", throughput);
            
            if (throughput >= 2_000_000) {
                System.out.println("  ✓ PASSED: Throughput ≥ 2M events/sec target");
            } else if (throughput >= 1_000_000) {
                System.out.println("  ⚠ Good: Throughput ≥ 1M events/sec (target: 2M)");
            } else if (throughput >= 500_000) {
                System.out.println("  ⚠ Fair: Throughput ≥ 500K events/sec (target: 2M)");
            } else {
                System.out.println("  ⚠ Warning: Throughput below 500K events/sec");
            }
        } else {
            System.out.printf("  ✗ FAILED: Timeout waiting for events (received %,d/%,d)%n", 
                eventCount - latch.getCount(), eventCount);
        }
    }
}
