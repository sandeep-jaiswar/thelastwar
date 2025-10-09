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
        assertEquals(timestamp, event.timestamp());
        assertEquals(sequence, event.sequence());
        assertEquals(sourceId, event.sourceId());
        assertEquals(eventType, event.eventType());
        assertEquals(header, event.header());
        assertEquals(payload, event.payload());
    }

    @Test
    void testEventImmutability() {
        long timestamp = System.nanoTime();
        Event event1 = Event.create(timestamp, 1L, SourceId.MATCHING_ENGINE, EventType.ORDER_FILLED, 0L, "payload1");
        Event event2 = Event.create(timestamp, 1L, SourceId.MATCHING_ENGINE, EventType.ORDER_FILLED, 0L, "payload1");

        // Events with same values should be equal in their properties
        assertEquals(event1.timestamp(), event2.timestamp());
        assertEquals(event1.sequence(), event2.sequence());
        assertEquals(event1.sourceId(), event2.sourceId());
        assertEquals(event1.eventType(), event2.eventType());
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
        assertNull(event.payload());
    }

    @Test
    void testEventTypesAreDistinct() {
        Event event1 = Event.create(1L, 1L, SourceId.FEED_HANDLER, EventType.MARKET_DATA_UPDATE, 0L, "data1");
        Event event2 = Event.create(1L, 1L, SourceId.FEED_HANDLER, EventType.MARKET_DATA_SNAPSHOT, 0L, "data2");

        assertNotEquals(event1.eventType(), event2.eventType());
    }

    @Test
    void testEventWithDifferentSources() {
        Event event1 = Event.create(1L, 1L, SourceId.FEED_HANDLER, EventType.HEARTBEAT, 0L, null);
        Event event2 = Event.create(1L, 1L, SourceId.ANALYTICS, EventType.HEARTBEAT, 0L, null);

        assertEquals(event1.eventType(), event2.eventType());
        assertNotEquals(event1.sourceId(), event2.sourceId());
    }
}
