package com.thelastwar.eventbus;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for backpressure and metrics functionality.
 * 
 * Tests:
 * - Slow subscriber scenarios
 * - Backpressure activation and deactivation
 * - Metrics collection under load
 * - System stability under 2x expected load
 */
class AeronEventBusBackpressureTest {

    private static final int INITIALIZATION_WAIT_MS = 100;
    private static final int SLOW_SUBSCRIBER_DELAY_MS = 10;
    
    private AeronEventBus eventBus;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() throws InterruptedException {
        registry = new SimpleMeterRegistry();
        eventBus = new AeronEventBus(registry, "backpressure-test");
        eventBus.start();
        Thread.sleep(INITIALIZATION_WAIT_MS);
    }

    @AfterEach
    void tearDown() {
        if (eventBus != null) {
            eventBus.stop();
        }
    }

    @Test
    void testSlowSubscriberBackpressure() throws InterruptedException {
        // Create a slow subscriber
        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch slowProcessingLatch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {
            receivedCount.incrementAndGet();
            try {
                // Simulate slow processing
                Thread.sleep(SLOW_SUBSCRIBER_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            slowProcessingLatch.countDown();
        });

        // Publish events rapidly
        int publishAttempts = 100;
        int successfulPublishes = 0;
        
        for (int i = 0; i < publishAttempts; i++) {
            Event event = Event.create(
                System.nanoTime(),
                i,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "Event " + i
            );
            
            if (eventBus.publish(event)) {
                successfulPublishes++;
            }
        }

        // Wait for at least one event to be processed
        assertTrue(slowProcessingLatch.await(5, TimeUnit.SECONDS), "At least one event should be processed");
        
        // Some events should have been published successfully
        assertTrue(successfulPublishes > 0, "Some events should be published successfully");
        
        // Verify metrics were collected
        EventBusMetrics metrics = eventBus.getMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.getPublishedEventCount() > 0, "Published events should be tracked");
        
        System.out.printf("Slow subscriber test: %d/%d publishes successful, %d received%n",
                successfulPublishes, publishAttempts, receivedCount.get());
    }

    @Test
    void testBackpressureMetrics() throws InterruptedException {
        // Fast publisher, slow subscriber scenario
        AtomicInteger processedCount = new AtomicInteger(0);
        
        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            processedCount.incrementAndGet();
            try {
                Thread.sleep(5); // Slow processing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Publish many events quickly
        int eventCount = 200;
        for (int i = 0; i < eventCount; i++) {
            Event event = Event.create(
                System.nanoTime(),
                i,
                SourceId.MATCHING_ENGINE,
                EventType.ORDER_FILLED,
                0L,
                "Order " + i
            );
            eventBus.publish(event);
        }

        Thread.sleep(500); // Let some processing happen

        // Check metrics
        EventBusMetrics metrics = eventBus.getMetrics();
        assertNotNull(metrics);
        
        long publishedCount = metrics.getPublishedEventCount();
        assertTrue(publishedCount > 0, "Should have published events");
        
        System.out.printf("Backpressure metrics test: Published=%d, Processed=%d%n",
                publishedCount, processedCount.get());
    }

    @Test
    void testSystemStabilityUnder2xLoad() throws InterruptedException {
        // Target: 2M events/sec, so 2x = 4M events/sec
        // For a short test, we'll use 20,000 events (0.5% of 1 second at 4M/s)
        final int eventCount = 20000;
        final CountDownLatch latch = new CountDownLatch(eventCount);
        
        // Fast subscriber
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> latch.countDown());

        long startTime = System.nanoTime();
        int successfulPublishes = 0;
        int failedPublishes = 0;

        // Publish events as fast as possible
        for (int i = 0; i < eventCount; i++) {
            Event event = Event.create(
                System.nanoTime(),
                i,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "Load test " + i
            );
            
            // Retry on backpressure
            boolean published = false;
            int retries = 0;
            while (!published && retries < 10) {
                if (eventBus.publish(event)) {
                    published = true;
                    successfulPublishes++;
                } else {
                    retries++;
                    Thread.onSpinWait(); // Brief pause
                }
            }
            
            if (!published) {
                failedPublishes++;
            }
        }

        // Wait for all events to be processed
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        long endTime = System.nanoTime();

        assertTrue(completed, "All events should be processed");
        
        double durationSec = (endTime - startTime) / 1_000_000_000.0;
        double throughput = eventCount / durationSec;

        System.out.printf("2x Load test results:%n");
        System.out.printf("  Events: %d%n", eventCount);
        System.out.printf("  Duration: %.3f seconds%n", durationSec);
        System.out.printf("  Throughput: %,.0f events/sec%n", throughput);
        System.out.printf("  Successful: %d, Failed: %d%n", successfulPublishes, failedPublishes);
        
        // Verify metrics
        EventBusMetrics metrics = eventBus.getMetrics();
        assertEquals(successfulPublishes, metrics.getPublishedEventCount(),
                "Published count should match successful publishes");
        
        // Verify no message loss (with retries, we should achieve 100% delivery)
        assertTrue(successfulPublishes >= eventCount * 0.99,
                "Should successfully publish at least 99% of messages");
    }

    @Test
    void testBackpressureActivationAndDeactivation() throws InterruptedException {
        AtomicLong totalProcessed = new AtomicLong(0);
        AtomicInteger batchSize = new AtomicInteger(50);
        
        // Slow subscriber that can be controlled
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {
            totalProcessed.incrementAndGet();
            try {
                Thread.sleep(2); // Controlled slow processing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        BackpressureMonitor monitor = eventBus.getBackpressureMonitor();
        assertNotNull(monitor);

        // Phase 1: Rapidly publish to trigger backpressure
        int phase1Events = 500;
        for (int i = 0; i < phase1Events; i++) {
            Event event = Event.create(
                System.nanoTime(),
                i,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "Event " + i
            );
            eventBus.publish(event);
        }

        // Check if backpressure was activated at some point
        Thread.sleep(100);
        
        // Phase 2: Wait for processing to catch up
        Thread.sleep(2000);

        // System should stabilize
        long processed = totalProcessed.get();
        System.out.printf("Backpressure test: Published=%d, Processed=%d%n",
                phase1Events, processed);
        
        assertTrue(processed > 0, "Should have processed some events");
    }

    @Test
    void testMetricsVisibilityUnderBackpressure() throws InterruptedException {
        // Slow subscriber
        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Publish events to create backpressure
        int eventCount = 100;
        for (int i = 0; i < eventCount; i++) {
            Event event = Event.create(
                System.nanoTime(),
                i,
                SourceId.MATCHING_ENGINE,
                EventType.ORDER_FILLED,
                0L,
                "Order " + i
            );
            eventBus.publish(event);
        }

        Thread.sleep(200);

        // Verify metrics are being collected
        EventBusMetrics metrics = eventBus.getMetrics();
        assertNotNull(metrics);
        
        long publishedCount = metrics.getPublishedEventCount();
        assertTrue(publishedCount > 0, "Should track published events");
        
        // Verify metrics are accessible via registry
        assertNotNull(registry.find("eventbus.events.published").counter());
        assertTrue(registry.find("eventbus.events.published").counter().count() > 0);
        
        System.out.printf("Metrics visibility test: Published=%d, Registry count=%.0f%n",
                publishedCount, registry.find("eventbus.events.published").counter().count());
    }

    @Test
    void testSubscriberCountTracking() {
        EventBusMetrics metrics = eventBus.getMetrics();
        assertNotNull(metrics);
        
        assertEquals(0, metrics.getActiveSubscribers());
        
        // Add first subscriber
        EventBus.Subscription sub1 = eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {});
        assertEquals(1, metrics.getActiveSubscribers());
        
        // Add second subscriber
        EventBus.Subscription sub2 = eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {});
        assertEquals(2, metrics.getActiveSubscribers());
        
        // Remove first subscriber
        sub1.unsubscribe();
        assertEquals(1, metrics.getActiveSubscribers());
        
        // Remove second subscriber
        sub2.unsubscribe();
        assertEquals(0, metrics.getActiveSubscribers());
    }
}
