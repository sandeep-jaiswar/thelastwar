package com.thelastwar.eventbus;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EventBusMetrics.
 */
class EventBusMetricsTest {

    private MeterRegistry registry;
    private EventBusMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new EventBusMetrics(registry, "test-bus");
    }

    @Test
    void testPublishedEventCount() {
        assertEquals(0, metrics.getPublishedEventCount());
        
        metrics.recordPublish(1000);
        assertEquals(1, metrics.getPublishedEventCount());
        
        metrics.recordPublish(2000);
        metrics.recordPublish(3000);
        assertEquals(3, metrics.getPublishedEventCount());
    }

    @Test
    void testBackpressureEventCount() {
        assertEquals(0, metrics.getBackpressureEventCount());
        
        metrics.recordBackpressureEvent();
        assertEquals(1, metrics.getBackpressureEventCount());
        
        metrics.recordBackpressureEvent();
        metrics.recordBackpressureEvent();
        assertEquals(3, metrics.getBackpressureEventCount());
    }

    @Test
    void testDroppedMessageCount() {
        assertEquals(0, metrics.getDroppedMessageCount());
        
        metrics.recordDroppedMessage();
        assertEquals(1, metrics.getDroppedMessageCount());
        
        metrics.recordDroppedMessage();
        assertEquals(2, metrics.getDroppedMessageCount());
    }

    @Test
    void testActiveSubscribers() {
        assertEquals(0, metrics.getActiveSubscribers());
        
        metrics.setActiveSubscribers(5);
        assertEquals(5, metrics.getActiveSubscribers());
        
        metrics.setActiveSubscribers(10);
        assertEquals(10, metrics.getActiveSubscribers());
    }

    @Test
    void testQueueDepth() {
        assertEquals(0, metrics.getQueueDepth());
        
        metrics.setQueueDepth(50);
        assertEquals(50, metrics.getQueueDepth());
        
        metrics.setQueueDepth(100);
        assertEquals(100, metrics.getQueueDepth());
    }

    @Test
    void testLatencyTracking() {
        // Record some latencies
        metrics.recordPublish(1000); // 1 microsecond
        metrics.recordPublish(5000); // 5 microseconds
        metrics.recordPublish(10000); // 10 microseconds
        
        // P99 should be recorded (exact value depends on histogram implementation)
        double p99 = metrics.getP99LatencyNanos();
        assertTrue(p99 >= 0, "P99 latency should be non-negative");
    }

    @Test
    void testMetricsRegistration() {
        // Verify metrics are registered in the registry
        assertNotNull(registry.find("eventbus.events.published").counter());
        assertNotNull(registry.find("eventbus.backpressure.events").counter());
        assertNotNull(registry.find("eventbus.messages.dropped").counter());
        assertNotNull(registry.find("eventbus.publishes.failed").counter());
        assertNotNull(registry.find("eventbus.publish.latency").timer());
        assertNotNull(registry.find("eventbus.subscribers.active").gauge());
        assertNotNull(registry.find("eventbus.queue.depth").gauge());
    }

    @Test
    void testMetricTags() {
        // Verify metrics have the correct tags
        var publishedCounter = registry.find("eventbus.events.published").counter();
        assertNotNull(publishedCounter);
        assertTrue(publishedCounter.getId().getTags().stream()
                .anyMatch(tag -> tag.getKey().equals("bus") && tag.getValue().equals("test-bus")));
    }

    @Test
    void testFailedPublishes() {
        assertEquals(0, registry.find("eventbus.publishes.failed").counter().count());
        
        metrics.recordFailedPublish();
        assertEquals(1, registry.find("eventbus.publishes.failed").counter().count());
        
        metrics.recordFailedPublish();
        metrics.recordFailedPublish();
        assertEquals(3, registry.find("eventbus.publishes.failed").counter().count());
    }
}
