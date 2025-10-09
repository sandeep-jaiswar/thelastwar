package com.thelastwar.eventbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    @Test
    void testEventCreation() {
        long timestamp = System.nanoTime();
        long sequence = 1L;
        int sourceId = SourceId.FEED_HANDLER;
        int eventType = EventType.MARKET_DATA_UPDATE;
        long header = 0xDEADBEEFL;
        String payload = "test payload";

        Event event = Event.create(timestamp, sequence, sourceId, eventType, header, payload);

        assertNotNull(event);
        assertEquals(timestamp, event.getTimestamp());
        assertEquals(sequence, event.getSequence());
        assertEquals(sourceId, event.getSourceId());
        assertEquals(eventType, event.getEventType());
        assertEquals(header, event.getHeader());
        assertEquals(payload, event.getPayload());
    }

    @Test
    void testEventImmutability() {
        long timestamp = System.nanoTime();
        Event event1 = Event.create(timestamp, 1L, SourceId.MATCHING_ENGINE, EventType.ORDER_FILLED, 0L, "payload1");
        Event event2 = Event.create(timestamp, 1L, SourceId.MATCHING_ENGINE, EventType.ORDER_FILLED, 0L, "payload1");

        // Events with same values should be equal in their properties
        assertEquals(event1.getTimestamp(), event2.getTimestamp());
        assertEquals(event1.getSequence(), event2.getSequence());
        assertEquals(event1.getSourceId(), event2.getSourceId());
        assertEquals(event1.getEventType(), event2.getEventType());
    }

    @Test
    void testEventToString() {
        Event event = Event.create(1000L, 42L, SourceId.RISK_MANAGER, EventType.RISK_CHECK_PASSED, 123L, "test");
        String str = event.toString();

        assertNotNull(str);
        assertTrue(str.contains("timestamp=1000"));
        assertTrue(str.contains("sequence=42"));
        assertTrue(str.contains("sourceId=" + SourceId.RISK_MANAGER));
        assertTrue(str.contains("eventType=" + EventType.RISK_CHECK_PASSED));
    }

    @Test
    void testEventWithNullPayload() {
        Event event = Event.create(System.nanoTime(), 1L, SourceId.OMS, EventType.ORDER_SUBMITTED, 0L, null);

        assertNotNull(event);
        assertNull(event.getPayload());
    }

    @Test
    void testEventTypesAreDistinct() {
        Event event1 = Event.create(1L, 1L, SourceId.FEED_HANDLER, EventType.MARKET_DATA_UPDATE, 0L, "data1");
        Event event2 = Event.create(1L, 1L, SourceId.FEED_HANDLER, EventType.MARKET_DATA_SNAPSHOT, 0L, "data2");

        assertNotEquals(event1.getEventType(), event2.getEventType());
    }

    @Test
    void testEventWithDifferentSources() {
        Event event1 = Event.create(1L, 1L, SourceId.FEED_HANDLER, EventType.HEARTBEAT, 0L, null);
        Event event2 = Event.create(1L, 1L, SourceId.ANALYTICS, EventType.HEARTBEAT, 0L, null);

        assertEquals(event1.getEventType(), event2.getEventType());
        assertNotEquals(event1.getSourceId(), event2.getSourceId());
    }
}
