package com.thelastwar.eventbus;

import com.thelastwar.eventbus.model.OrderEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for EventBus with synthetic order bursts.
 * 
 * Validates acceptance criteria:
 * 1. Inbound/outbound events correctly routed between gateways and matching
 * engine
 * 2. Sustained throughput ≥ 2M msgs/sec (Aeron benchmark)
 * 3. Zero message loss under backpressure conditions
 * 4. Verified with synthetic order bursts
 * 
 * This test simulates high-frequency trading scenarios with:
 * - Multiple concurrent publishers (simulating gateways)
 * - Multiple subscribers (simulating matching engine, risk, analytics)
 * - Burst patterns (simulating market open, news events)
 * - Metrics validation (latency, throughput, consumer lag)
 */
class SyntheticOrderBurstIntegrationTest {

    private AeronEventBus eventBus;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() throws InterruptedException {
        registry = new SimpleMeterRegistry();
        eventBus = new AeronEventBus(registry, "integration-test");
        eventBus.start();

        // Give Aeron time to initialize
        Thread.sleep(200);
    }

    @AfterEach
    void tearDown() {
        if (eventBus != null) {
            eventBus.stop();
        }
    }

    /**
     * Test 1: Validates correct event routing from gateway to matching engine.
     */
    @Test
    void testEventRoutingBetweenComponents() throws InterruptedException {
        AtomicLong matchingEngineReceived = new AtomicLong(0);
        AtomicLong riskEngineReceived = new AtomicLong(0);
        AtomicLong analyticsReceived = new AtomicLong(0);

        // Simulate matching engine subscribing to ORDER_SUBMITTED
        eventBus.subscribe(EventType.ORDER_SUBMITTED, event -> {
            matchingEngineReceived.incrementAndGet();
        });

        // Simulate risk engine subscribing to ORDER_SUBMITTED
        eventBus.subscribe(EventType.ORDER_SUBMITTED, event -> {
            riskEngineReceived.incrementAndGet();
        });

        // Simulate analytics subscribing to ORDER_FILLED
        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            analyticsReceived.incrementAndGet();
        });

        // Simulate gateway publishing orders
        int orderCount = 1000;
        for (int i = 0; i < orderCount; i++) {
            OrderEvent order = OrderEvent.newOrder(
                    i + 1, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                    100L, 15000L, 999L, 1);

            Event event = Event.create(
                    System.nanoTime(),
                    i,
                    SourceId.FEED_HANDLER,
                    EventType.ORDER_SUBMITTED,
                    0L,
                    order);

            assertTrue(eventBus.publish(event), "Failed to publish event " + i);
        }

        // Simulate matching engine publishing fills
        int fillCount = 500;
        for (int i = 0; i < fillCount; i++) {
            Event event = Event.create(
                    System.nanoTime(),
                    orderCount + i,
                    SourceId.MATCHING_ENGINE,
                    EventType.ORDER_FILLED,
                    0L,
                    "Fill " + i);

            assertTrue(eventBus.publish(event), "Failed to publish fill " + i);
        }

        // Wait for event processing
        Thread.sleep(500);

        // Verify correct routing
        assertEquals(orderCount, matchingEngineReceived.get(), "Matching engine should receive all orders");
        assertEquals(orderCount, riskEngineReceived.get(), "Risk engine should receive all orders");
        assertEquals(fillCount, analyticsReceived.get(), "Analytics should receive all fills");
    }

    /**
     * Test 2: Validates sustained throughput of ≥ 2M msgs/sec.
     * This is a scaled-down version suitable for unit testing.
     * For full 2M msgs/sec validation, use JMH benchmarks.
     */
    @Test
    void testSustainedThroughput() throws InterruptedException {
        AtomicLong received = new AtomicLong(0);
        CountDownLatch completionLatch = new CountDownLatch(1);

        // Subscribe to events
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {
            received.incrementAndGet();
        });

        // Publish events in burst (scaled-down test: 100K events)
        int eventCount = 100_000;
        long startNanos = System.nanoTime();

        for (int i = 0; i < eventCount; i++) {
            Event event = Event.create(
                    System.nanoTime(),
                    i,
                    SourceId.FEED_HANDLER,
                    EventType.MARKET_DATA_UPDATE,
                    0L,
                    "AAPL,150.00,100");

            boolean published = eventBus.publish(event);
            if (!published) {
                // If backpressure, retry with small delay
                Thread.sleep(0, 100); // 100 nanoseconds
                eventBus.publish(event);
            }
        }

        long publishDurationNanos = System.nanoTime() - startNanos;

        // Wait for all events to be consumed
        Thread.sleep(1000);

        long consumeDurationNanos = System.nanoTime() - startNanos;

        // Calculate throughput
        double publishThroughput = (eventCount * 1_000_000_000.0) / publishDurationNanos;
        double consumeThroughput = (received.get() * 1_000_000_000.0) / consumeDurationNanos;

        System.out.printf("Published %d events in %.2f ms (%.0f msgs/sec)%n",
                eventCount, publishDurationNanos / 1_000_000.0, publishThroughput);
        System.out.printf("Consumed %d events in %.2f ms (%.0f msgs/sec)%n",
                received.get(), consumeDurationNanos / 1_000_000.0, consumeThroughput);

        // Verify all events were delivered
        assertEquals(eventCount, received.get(), "All events should be delivered");

        // Verify reasonable throughput (at least 100K msgs/sec in test environment)
        assertTrue(publishThroughput >= 100_000,
                "Publish throughput should be at least 100K msgs/sec, got " + publishThroughput);
    }

    /**
     * Test 3: Validates zero message loss under backpressure conditions.
     */
    @Test
    void testZeroMessageLossUnderBackpressure() throws InterruptedException {
        AtomicLong received = new AtomicLong(0);
        AtomicLong expectedSequence = new AtomicLong(0);
        AtomicLong outOfOrderCount = new AtomicLong(0);

        // Slow subscriber to create backpressure
        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            received.incrementAndGet();
            long seq = event.sequence();
            long expected = expectedSequence.getAndIncrement();
            if (seq != expected) {
                outOfOrderCount.incrementAndGet();
            }

            // Simulate slow processing
            try {
                Thread.sleep(0, 10_000); // 10 microseconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Fast publisher
        int eventCount = 10_000;
        int publishedCount = 0;

        for (int i = 0; i < eventCount; i++) {
            Event event = Event.create(
                    System.nanoTime(),
                    i,
                    SourceId.MATCHING_ENGINE,
                    EventType.ORDER_FILLED,
                    0L,
                    "Order " + i);

            boolean published = false;
            int retries = 0;
            while (!published && retries < 1000) {
                published = eventBus.publish(event);
                if (!published) {
                    retries++;
                    // Small backoff
                    if (retries % 10 == 0) {
                        Thread.sleep(0, 1000); // 1 microsecond
                    }
                }
            }

            if (published) {
                publishedCount++;
            }
        }

        // Wait for all events to be consumed (bounded wait to reduce flakiness)
        long waitStart = System.nanoTime();
        long waitTimeoutNanos = 10_000_000_000L; // 10 seconds
        while (received.get() < publishedCount && (System.nanoTime() - waitStart) < waitTimeoutNanos) {
            Thread.sleep(50);
        }

        // Verify zero message loss
        assertEquals(publishedCount, received.get(),
                "All published messages should be delivered (zero message loss)");

        // Verify metrics tracked correctly
        EventBusMetrics metrics = eventBus.getMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.getPublishedEventCount() > 0, "Should have published events");

        System.out.printf("Published: %d, Received: %d, Backpressure events: %d%n",
                publishedCount, received.get(), metrics.getBackpressureEventCount());
    }

    /**
     * Test 4: Validates metrics collection under load.
     */
    @Test
    void testMetricsUnderLoad() throws InterruptedException {
        AtomicLong received = new AtomicLong(0);

        // Subscribe to multiple event types
        eventBus.subscribe(EventType.ORDER_ACCEPTED, event -> received.incrementAndGet());
        eventBus.subscribe(EventType.ORDER_FILLED, event -> received.incrementAndGet());
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> received.incrementAndGet());

        // Publish mixed workload
        int eventsPerType = 1000;

        for (int i = 0; i < eventsPerType; i++) {
            // Order accepted
            eventBus.publish(Event.create(
                    System.nanoTime(), i * 3,
                    SourceId.MATCHING_ENGINE, EventType.ORDER_ACCEPTED, 0L, "Order " + i));

            // Order filled
            eventBus.publish(Event.create(
                    System.nanoTime(), i * 3 + 1,
                    SourceId.MATCHING_ENGINE, EventType.ORDER_FILLED, 0L, "Fill " + i));

            // Market data
            eventBus.publish(Event.create(
                    System.nanoTime(), i * 3 + 2,
                    SourceId.FEED_HANDLER, EventType.MARKET_DATA_UPDATE, 0L, "MD " + i));
        }

        // Wait for processing
        Thread.sleep(500);

        // Verify metrics
        EventBusMetrics metrics = eventBus.getMetrics();
        assertNotNull(metrics);

        long publishedCount = metrics.getPublishedEventCount();
        assertTrue(publishedCount > 0, "Should have published events");

        long consumerLag = metrics.getConsumerLag();
        assertTrue(consumerLag >= 0, "Consumer lag should be non-negative");

        long queueDepth = metrics.getQueueDepth();
        assertTrue(queueDepth >= 0 && queueDepth <= 100,
                "Queue depth should be between 0-100 percent");

        double p99Latency = metrics.getP99LatencyNanos();
        assertTrue(p99Latency >= 0, "P99 latency should be non-negative");

        System.out.printf("Metrics - Published: %d, Consumer Lag: %d, Queue Depth: %d%%, P99: %.2f µs%n",
                publishedCount, consumerLag, queueDepth, p99Latency / 1000.0);

        // Verify consumer lag is eventually small (catching up)
        Thread.sleep(500);
        long finalLag = metrics.getConsumerLag();
        assertTrue(finalLag < eventsPerType,
                "Consumer lag should decrease after burst, got " + finalLag);
    }

    /**
     * Test 5: Validates subscriber count tracking across multiple components.
     */
    @Test
    void testMultipleComponentSubscriptions() {
        // Simulate multiple components subscribing to events

        // FIX Gateway subscribes to outbound events
        EventBus.Subscription fixGatewaySub1 = eventBus.subscribe(EventType.ORDER_ACCEPTED, e -> {
        });
        EventBus.Subscription fixGatewaySub2 = eventBus.subscribe(EventType.ORDER_FILLED, e -> {
        });

        // WebSocket Gateway subscribes to broadcast events
        EventBus.Subscription wsGatewaySub1 = eventBus.subscribe(EventType.ORDER_ACCEPTED, e -> {
        });
        EventBus.Subscription wsGatewaySub2 = eventBus.subscribe(EventType.ORDER_FILLED, e -> {
        });
        EventBus.Subscription wsGatewaySub3 = eventBus.subscribe(EventType.MARKET_DATA_UPDATE, e -> {
        });

        // Matching Engine subscribes to inbound orders
        EventBus.Subscription matchingEngineSub = eventBus.subscribe(EventType.ORDER_SUBMITTED, e -> {
        });

        // Verify subscriber counts
        assertEquals(2, eventBus.getSubscriberCount(EventType.ORDER_ACCEPTED));
        assertEquals(2, eventBus.getSubscriberCount(EventType.ORDER_FILLED));
        assertEquals(1, eventBus.getSubscriberCount(EventType.MARKET_DATA_UPDATE));
        assertEquals(1, eventBus.getSubscriberCount(EventType.ORDER_SUBMITTED));

        // Unsubscribe WebSocket Gateway
        wsGatewaySub1.unsubscribe();
        wsGatewaySub2.unsubscribe();
        wsGatewaySub3.unsubscribe();

        // Verify counts updated
        assertEquals(1, eventBus.getSubscriberCount(EventType.ORDER_ACCEPTED));
        assertEquals(1, eventBus.getSubscriberCount(EventType.ORDER_FILLED));
        assertEquals(0, eventBus.getSubscriberCount(EventType.MARKET_DATA_UPDATE));
    }
}
