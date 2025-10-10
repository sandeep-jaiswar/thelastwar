package com.thelastwar.eventbus.example;

import com.thelastwar.eventbus.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating EventBus metrics and backpressure control.
 * 
 * This example shows:
 * 1. How to enable metrics collection
 * 2. How to monitor backpressure
 * 3. How to handle backpressure in publish logic
 * 4. How to access metrics programmatically
 */
public class MetricsBackpressureExample {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== EventBus Metrics & Backpressure Example ===\n");
        
        // Create EventBus with metrics
        MeterRegistry registry = new SimpleMeterRegistry();
        AeronEventBus eventBus = new AeronEventBus(registry, "example-bus");
        eventBus.start();
        
        // Give Aeron time to initialize
        Thread.sleep(100);
        
        // Example 1: Normal operation
        System.out.println("Example 1: Normal Operation");
        normalOperation(eventBus);
        
        // Example 2: Slow subscriber with backpressure
        System.out.println("\nExample 2: Slow Subscriber (Backpressure)");
        slowSubscriberExample(eventBus);
        
        // Example 3: Accessing metrics
        System.out.println("\nExample 3: Metrics Summary");
        printMetrics(eventBus);
        
        // Cleanup
        eventBus.stop();
        System.out.println("\n=== Example Complete ===");
    }
    
    /**
     * Normal operation - fast producer and consumer.
     */
    private static void normalOperation(AeronEventBus eventBus) throws InterruptedException {
        int eventCount = 1000;
        CountDownLatch latch = new CountDownLatch(eventCount);
        
        // Fast subscriber
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {
            latch.countDown();
        });
        
        // Publish events
        long startTime = System.nanoTime();
        for (int i = 0; i < eventCount; i++) {
            Event event = Event.create(
                System.nanoTime(),
                i,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "Market data " + i
            );
            eventBus.publish(event);
        }
        
        // Wait for completion
        latch.await(5, TimeUnit.SECONDS);
        long endTime = System.nanoTime();
        
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = eventCount / (durationMs / 1000.0);
        
        System.out.printf("  Published: %d events%n", eventCount);
        System.out.printf("  Duration: %.2f ms%n", durationMs);
        System.out.printf("  Throughput: %,.0f events/sec%n", throughput);
        
        // Check metrics
        EventBusMetrics metrics = eventBus.getMetrics();
        System.out.printf("  Backpressure events: %d%n", metrics.getBackpressureEventCount());
        System.out.printf("  Dropped messages: %d%n", metrics.getDroppedMessageCount());
    }
    
    /**
     * Slow subscriber example - demonstrates backpressure handling.
     */
    private static void slowSubscriberExample(AeronEventBus eventBus) throws InterruptedException {
        int eventCount = 200;
        CountDownLatch latch = new CountDownLatch(eventCount);
        
        // Slow subscriber
        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            try {
                Thread.sleep(10); // Simulate slow processing
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Publish events with backpressure handling
        int successfulPublishes = 0;
        int retriedPublishes = 0;
        
        BackpressureMonitor monitor = eventBus.getBackpressureMonitor();
        
        for (int i = 0; i < eventCount; i++) {
            Event event = Event.create(
                System.nanoTime(),
                i,
                SourceId.MATCHING_ENGINE,
                EventType.ORDER_FILLED,
                0L,
                "Order " + i
            );
            
            // Retry logic for backpressure
            boolean published = false;
            int retries = 0;
            while (!published && retries < 5) {
                if (eventBus.publish(event)) {
                    published = true;
                    successfulPublishes++;
                } else {
                    retries++;
                    retriedPublishes++;
                    Thread.onSpinWait(); // Brief pause
                }
            }
            
            // Print backpressure status every 50 events
            if (i > 0 && i % 50 == 0) {
                System.out.printf("  Progress: %d/%d, Queue depth: %d%%, Backpressure: %s%n",
                    i, eventCount, 
                    monitor.getUtilizationPercent(),
                    monitor.isBackpressureActive() ? "ACTIVE" : "inactive");
            }
        }
        
        System.out.printf("  Successful publishes: %d%n", successfulPublishes);
        System.out.printf("  Retried publishes: %d%n", retriedPublishes);
        
        // Wait a bit for processing
        Thread.sleep(500);
        
        // Check final metrics
        EventBusMetrics metrics = eventBus.getMetrics();
        System.out.printf("  Backpressure events: %d%n", metrics.getBackpressureEventCount());
        System.out.printf("  Dropped messages: %d%n", metrics.getDroppedMessageCount());
    }
    
    /**
     * Print comprehensive metrics summary.
     */
    private static void printMetrics(AeronEventBus eventBus) {
        EventBusMetrics metrics = eventBus.getMetrics();
        BackpressureMonitor monitor = eventBus.getBackpressureMonitor();
        
        System.out.println("  Event Bus Metrics:");
        System.out.printf("    Published events: %d%n", metrics.getPublishedEventCount());
        System.out.printf("    Backpressure events: %d%n", metrics.getBackpressureEventCount());
        System.out.printf("    Dropped messages: %d%n", metrics.getDroppedMessageCount());
        System.out.printf("    Active subscribers: %d%n", metrics.getActiveSubscribers());
        System.out.printf("    P99 latency: %.2f µs%n", metrics.getP99LatencyNanos() / 1000.0);
        
        System.out.println("\n  Backpressure Monitor:");
        System.out.printf("    Current state: %s%n", 
            monitor.isBackpressureActive() ? "ACTIVE" : "inactive");
        System.out.printf("    Queue depth: %d%%%n", monitor.getUtilizationPercent());
        System.out.printf("    Pending messages: %d%n", monitor.getPendingMessages());
        System.out.printf("    High watermark: %d%n", monitor.getHighWatermark());
        System.out.printf("    Low watermark: %d%n", monitor.getLowWatermark());
    }
}
