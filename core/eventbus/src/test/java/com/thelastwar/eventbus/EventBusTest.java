package com.thelastwar.eventbus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new InMemoryEventBus();
        eventBus.start();
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
    }

    @Test
    void testPublishAndSubscribe() {
        AtomicInteger eventCount = new AtomicInteger(0);
        EventHandler<String> handler = event -> eventCount.incrementAndGet();

        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, handler);

        Event event = Event.create(System.nanoTime(), 1L, SourceId.FEED_HANDLER, EventType.MARKET_DATA_UPDATE, 0L, "data");
        assertTrue(eventBus.publish(event));

        assertEquals(1, eventCount.get());
        assertEquals(1, eventBus.getPublishedEventCount());
    }

    @Test
    void testMultipleSubscribers() {
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);

        eventBus.subscribe(EventType.ORDER_FILLED, event -> count1.incrementAndGet());
        eventBus.subscribe(EventType.ORDER_FILLED, event -> count2.incrementAndGet());

        assertEquals(2, eventBus.getSubscriberCount(EventType.ORDER_FILLED));

        Event event = Event.create(System.nanoTime(), 1L, SourceId.MATCHING_ENGINE, EventType.ORDER_FILLED, 0L, "order");
        eventBus.publish(event);

        assertEquals(1, count1.get());
        assertEquals(1, count2.get());
    }

    @Test
    void testUnsubscribe() {
        AtomicInteger eventCount = new AtomicInteger(0);
        EventHandler<String> handler = event -> eventCount.incrementAndGet();

        EventBus.Subscription subscription = eventBus.subscribe(EventType.RISK_CHECK_PASSED, handler);
        assertTrue(subscription.isActive());

        Event event1 = Event.create(System.nanoTime(), 1L, SourceId.RISK_MANAGER, EventType.RISK_CHECK_PASSED, 0L, "check1");
        eventBus.publish(event1);
        assertEquals(1, eventCount.get());

        subscription.unsubscribe();
        assertFalse(subscription.isActive());

        Event event2 = Event.create(System.nanoTime(), 2L, SourceId.RISK_MANAGER, EventType.RISK_CHECK_PASSED, 0L, "check2");
        eventBus.publish(event2);
        assertEquals(1, eventCount.get()); // Should still be 1
    }

    @Test
    void testEventTypeFiltering() {
        AtomicInteger marketDataCount = new AtomicInteger(0);
        AtomicInteger orderCount = new AtomicInteger(0);

        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> marketDataCount.incrementAndGet());
        eventBus.subscribe(EventType.ORDER_FILLED, event -> orderCount.incrementAndGet());

        Event marketDataEvent = Event.create(System.nanoTime(), 1L, SourceId.FEED_HANDLER, EventType.MARKET_DATA_UPDATE, 0L, "data");
        Event orderEvent = Event.create(System.nanoTime(), 2L, SourceId.MATCHING_ENGINE, EventType.ORDER_FILLED, 0L, "order");

        eventBus.publish(marketDataEvent);
        eventBus.publish(orderEvent);

        assertEquals(1, marketDataCount.get());
        assertEquals(1, orderCount.get());
    }

    @Test
    void testHandlerException() {
        AtomicInteger errorCount = new AtomicInteger(0);
        
        EventHandler<String> faultyHandler = new EventHandler<String>() {
            @Override
            public void onEvent(Event event) {
                throw new RuntimeException("Handler error");
            }

            @Override
            public void onError(Event event, Throwable exception) {
                errorCount.incrementAndGet();
            }
        };

        eventBus.subscribe(EventType.LIMIT_BREACH, faultyHandler);

        Event event = Event.create(System.nanoTime(), 1L, SourceId.RISK_MANAGER, EventType.LIMIT_BREACH, 0L, "breach");
        assertTrue(eventBus.publish(event));

        assertEquals(1, errorCount.get());
    }

    @Test
    void testPublishBeforeStart() {
        EventBus bus = new InMemoryEventBus();
        // Don't start the bus

        Event event = Event.create(System.nanoTime(), 1L, SourceId.SYSTEM, EventType.HEARTBEAT, 0L, null);
        assertFalse(bus.publish(event));
    }

    @Test
    void testPublishNullEvent() {
        assertFalse(eventBus.publish(null));
    }

    @Test
    void testSubscribeWithInvalidEventType() {
        EventHandler<String> handler = event -> {};
        assertThrows(IllegalArgumentException.class, () -> eventBus.subscribe(-1, handler));
        assertThrows(IllegalArgumentException.class, () -> eventBus.subscribe(10000, handler));
    }

    @Test
    void testSubscribeWithNullHandler() {
        assertThrows(IllegalArgumentException.class, () -> eventBus.subscribe(EventType.HEARTBEAT, null));
    }

    @Test
    void testHighThroughput() throws InterruptedException {
        int eventCount = 1000;
        CountDownLatch latch = new CountDownLatch(eventCount);
        
        EventHandler<String> handler = event -> latch.countDown();
        eventBus.subscribe(EventType.LATENCY_SAMPLE, handler);

        for (int i = 0; i < eventCount; i++) {
            Event event = Event.create(System.nanoTime(), i, SourceId.ANALYTICS, EventType.LATENCY_SAMPLE, 0L, "sample" + i);
            eventBus.publish(event);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Failed to process all events in time");
        assertEquals(eventCount, eventBus.getPublishedEventCount());
    }

    @Test
    void testGetSubscriberCount() {
        assertEquals(0, eventBus.getSubscriberCount(EventType.ORDER_ACCEPTED));

        EventBus.Subscription sub1 = eventBus.subscribe(EventType.ORDER_ACCEPTED, event -> {});
        assertEquals(1, eventBus.getSubscriberCount(EventType.ORDER_ACCEPTED));

        EventBus.Subscription sub2 = eventBus.subscribe(EventType.ORDER_ACCEPTED, event -> {});
        assertEquals(2, eventBus.getSubscriberCount(EventType.ORDER_ACCEPTED));

        sub1.unsubscribe();
        assertEquals(1, eventBus.getSubscriberCount(EventType.ORDER_ACCEPTED));

        sub2.unsubscribe();
        assertEquals(0, eventBus.getSubscriberCount(EventType.ORDER_ACCEPTED));
    }
}
