package com.thelastwar.eventbus;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventHandlerTest {

    @Test
    void testHandlerReceivesEvent() {
        AtomicInteger callCount = new AtomicInteger(0);
        EventHandler<String> handler = event -> callCount.incrementAndGet();

        Event event = Event.create(System.nanoTime(), 1L, SourceId.FEED_HANDLER, EventType.MARKET_DATA_UPDATE, 0L, "test");
        handler.onEvent(event);

        assertEquals(1, callCount.get());
    }

    @Test
    void testHandlerCanAccessEventProperties() {
        AtomicInteger receivedEventType = new AtomicInteger(0);
        EventHandler<String> handler = event -> receivedEventType.set(event.getEventType());

        Event event = Event.create(System.nanoTime(), 1L, SourceId.MATCHING_ENGINE, EventType.ORDER_FILLED, 0L, "order data");
        handler.onEvent(event);

        assertEquals(EventType.ORDER_FILLED, receivedEventType.get());
    }

    @Test
    void testHandlerDefaultOnError() {
        EventHandler<String> handler = event -> {
            throw new RuntimeException("Test exception");
        };

        Event event = Event.create(System.nanoTime(), 1L, SourceId.RISK_MANAGER, EventType.RISK_CHECK_FAILED, 0L, "risk data");
        
        // Default onError does nothing, so it should not throw
        assertDoesNotThrow(() -> handler.onError(event, new RuntimeException("Test")));
    }

    @Test
    void testHandlerCustomOnError() {
        AtomicInteger errorCount = new AtomicInteger(0);
        
        EventHandler<String> handler = new EventHandler<String>() {
            @Override
            public void onEvent(Event event) {
                throw new RuntimeException("Processing error");
            }

            @Override
            public void onError(Event event, Throwable exception) {
                errorCount.incrementAndGet();
            }
        };

        Event event = Event.create(System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, "order");
        
        assertThrows(RuntimeException.class, () -> handler.onEvent(event));
        handler.onError(event, new RuntimeException("Test"));
        
        assertEquals(1, errorCount.get());
    }

    @Test
    void testHandlerIsStateless() {
        // Handlers should be stateless or use thread-local state
        // This test verifies a handler can be called multiple times
        AtomicInteger count = new AtomicInteger(0);
        EventHandler<String> handler = event -> count.incrementAndGet();

        for (int i = 0; i < 100; i++) {
            Event event = Event.create(System.nanoTime(), i, SourceId.ANALYTICS, EventType.LATENCY_SAMPLE, 0L, "sample");
            handler.onEvent(event);
        }

        assertEquals(100, count.get());
    }
}
