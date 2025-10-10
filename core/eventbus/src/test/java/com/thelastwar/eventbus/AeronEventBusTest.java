package com.thelastwar.eventbus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AeronEventBus implementation.
 */
class AeronEventBusTest {

    // Test configuration constants
    private static final int INITIALIZATION_WAIT_MS = 100;
    private static final int PROCESSING_WAIT_MS = 200;
    private static final int HIGH_THROUGHPUT_EVENT_COUNT = 10000;
    private static final int MAX_PROCESSING_TIME_MS = 5000;
    private static final int INVALID_EVENT_TYPE = 10000;

    private AeronEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new AeronEventBus();
        eventBus.start();
        // Give Aeron time to initialize
        try {
            Thread.sleep(INITIALIZATION_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        if (eventBus != null) {
            eventBus.stop();
        }
    }

    @Test
    void testPublishAndSubscribe() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Event> receivedEvent = new AtomicReference<>();

        // Subscribe
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {
            receivedEvent.set(event);
            latch.countDown();
        });

        // Publish
        Event event = Event.create(
                System.nanoTime(),
                1L,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "AAPL: $150.00");

        assertTrue(eventBus.publish(event));

        // Wait for event
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Event should be received within timeout");
        assertNotNull(receivedEvent.get());
        assertEquals(EventType.MARKET_DATA_UPDATE, receivedEvent.get().eventType());
        assertEquals("AAPL: $150.00", receivedEvent.get().payload());
    }

    @Test
    void testMultipleSubscribers() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger count = new AtomicInteger(0);

        // Subscribe twice
        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            count.incrementAndGet();
            latch.countDown();
        });

        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            count.incrementAndGet();
            latch.countDown();
        });

        // Publish
        Event event = Event.create(
                System.nanoTime(),
                1L,
                SourceId.MATCHING_ENGINE,
                EventType.ORDER_FILLED,
                0L,
                "Order #12345");

        eventBus.publish(event);

        // Wait for both handlers
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(2, count.get());
    }

    @Test
    void testEventTypeFiltering() throws InterruptedException {
        CountDownLatch marketDataLatch = new CountDownLatch(1);
        CountDownLatch orderLatch = new CountDownLatch(1);
        AtomicInteger marketDataCount = new AtomicInteger(0);
        AtomicInteger orderCount = new AtomicInteger(0);

        // Subscribe to different event types
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {
            marketDataCount.incrementAndGet();
            marketDataLatch.countDown();
        });

        eventBus.subscribe(EventType.ORDER_FILLED, event -> {
            orderCount.incrementAndGet();
            orderLatch.countDown();
        });

        // Publish market data event
        eventBus.publish(Event.create(
                System.nanoTime(),
                1L,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "Market data"));

        // Publish order event
        eventBus.publish(Event.create(
                System.nanoTime(),
                2L,
                SourceId.MATCHING_ENGINE,
                EventType.ORDER_FILLED,
                0L,
                "Order filled"));

        // Wait for both events
        assertTrue(marketDataLatch.await(5, TimeUnit.SECONDS));
        assertTrue(orderLatch.await(5, TimeUnit.SECONDS));

        // Verify correct routing
        assertEquals(1, marketDataCount.get());
        assertEquals(1, orderCount.get());
    }

    @Test
    void testUnsubscribe() throws InterruptedException {
        AtomicInteger count = new AtomicInteger(0);

        // Subscribe
        EventBus.Subscription subscription = eventBus.subscribe(
                EventType.MARKET_DATA_UPDATE,
                event -> count.incrementAndGet());

        // Publish first event
        eventBus.publish(Event.create(
                System.nanoTime(),
                1L,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "Event 1"));

        Thread.sleep(PROCESSING_WAIT_MS); // Wait for processing

        // Unsubscribe
        subscription.unsubscribe();
        assertFalse(subscription.isActive());

        // Publish second event
        eventBus.publish(Event.create(
                System.nanoTime(),
                2L,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "Event 2"));

        Thread.sleep(PROCESSING_WAIT_MS); // Wait for processing

        // Should only have received first event
        assertEquals(1, count.get());
    }

    @Test
    void testHighThroughput() throws InterruptedException {
        final int eventCount = HIGH_THROUGHPUT_EVENT_COUNT;
        CountDownLatch latch = new CountDownLatch(eventCount);

        // Subscribe
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> latch.countDown());

        // Publish many events
        long startTime = System.nanoTime();
        for (int i = 0; i < eventCount; i++) {
            Event event = Event.create(
                    System.nanoTime(),
                    i,
                    SourceId.FEED_HANDLER,
                    EventType.MARKET_DATA_UPDATE,
                    0L,
                    "Event " + i);
            eventBus.publish(event);
        }

        // Wait for all events
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Should receive all events");
        long endTime = System.nanoTime();

        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = eventCount / (durationMs / 1000.0);

        System.out.printf("Throughput: %.0f events/sec (%.2f ms for %d events)%n",
                throughput, durationMs, eventCount);

        // Should be reasonably fast (this is a basic sanity check, not the full
        // benchmark)
        assertTrue(durationMs < MAX_PROCESSING_TIME_MS, "Should process events in reasonable time");
    }

    @Test
    void testPublishedEventCount() throws InterruptedException {
        // Publish a few events
        for (int i = 0; i < 5; i++) {
            eventBus.publish(Event.create(
                    System.nanoTime(),
                    i,
                    SourceId.FEED_HANDLER,
                    EventType.MARKET_DATA_UPDATE,
                    0L,
                    "Event " + i));
        }

        Thread.sleep(INITIALIZATION_WAIT_MS); // Allow processing

        assertEquals(5, eventBus.getPublishedEventCount());
    }

    @Test
    void testGetSubscriberCount() {
        assertEquals(0, eventBus.getSubscriberCount(EventType.MARKET_DATA_UPDATE));

        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {
        });
        assertEquals(1, eventBus.getSubscriberCount(EventType.MARKET_DATA_UPDATE));

        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {
        });
        assertEquals(2, eventBus.getSubscriberCount(EventType.MARKET_DATA_UPDATE));
    }

    @Test
    void testSubscribeWithInvalidEventType() {
        assertThrows(IllegalArgumentException.class, () -> {
            eventBus.subscribe(-1, event -> {
            });
        });

        assertThrows(IllegalArgumentException.class, () -> {
            eventBus.subscribe(INVALID_EVENT_TYPE, event -> {
            });
        });
    }

    @Test
    void testSubscribeWithNullHandler() {
        assertThrows(IllegalArgumentException.class, () -> {
            eventBus.subscribe(EventType.MARKET_DATA_UPDATE, null);
        });
    }

    @Test
    void testPublishNullEvent() {
        assertThrows(IllegalArgumentException.class, () -> {
            eventBus.publish(null);
        });
    }

    @Test
    void testPublishBeforeStart() {
        // Create but don't start
        AeronEventBus uninitializedBus = new AeronEventBus();

        Event event = Event.create(
                System.nanoTime(),
                1L,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "Test");

        assertFalse(uninitializedBus.publish(event));

        uninitializedBus.stop();
    }

    @Test
    void testHandlerException() throws InterruptedException {
        CountDownLatch errorLatch = new CountDownLatch(1);
        CountDownLatch successLatch = new CountDownLatch(1);

        // Subscribe with a handler that throws
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, new EventHandler() {
            @Override
            public void onEvent(Event event) {
                throw new RuntimeException("Test exception");
            }

            @Override
            public void onError(Event event, Throwable error) {
                errorLatch.countDown();
            }
        });

        // Subscribe with a normal handler
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> successLatch.countDown());

        // Publish event
        eventBus.publish(Event.create(
                System.nanoTime(),
                1L,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "Test"));

        // Both handlers should be called
        assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
        assertTrue(successLatch.await(5, TimeUnit.SECONDS));
    }
}
