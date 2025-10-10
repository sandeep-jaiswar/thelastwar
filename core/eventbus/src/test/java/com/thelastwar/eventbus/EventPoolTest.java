package com.thelastwar.eventbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventPoolTest {

    @Test
    void testPoolCreation() {
        EventPool pool = new EventPool(10);
        assertEquals(10, pool.capacity());
        assertEquals(10, pool.available());
    }

    @Test
    void testAcquireAndRelease() {
        EventPool pool = new EventPool(5);

        EventPool.MutableEvent event = pool.acquire();
        assertNotNull(event);
        assertEquals(4, pool.available());

        pool.release(event);
        assertEquals(5, pool.available());
    }

    @Test
    void testMutableEventSet() {
        EventPool.MutableEvent mutable = new EventPool.MutableEvent();

        mutable.set(System.nanoTime(), 1L, SourceId.FEED_HANDLER, EventType.MARKET_DATA_UPDATE, 0xABCDL, "test");

        assertEquals(1L, mutable.getSequence());
        assertEquals(SourceId.FEED_HANDLER, mutable.getSourceId());
        assertEquals(EventType.MARKET_DATA_UPDATE, mutable.getEventType());
        assertEquals(0xABCDL, mutable.getHeader());
        assertEquals("test", mutable.getPayload());
    }

    @Test
    void testMutableEventToEvent() {
        long timestamp = System.nanoTime();
        EventPool.MutableEvent mutable = new EventPool.MutableEvent();
        mutable.set(timestamp, 42L, SourceId.MATCHING_ENGINE, EventType.ORDER_FILLED, 0L, "order");

        Event immutable = mutable.toEvent();

        assertEquals(timestamp, immutable.timestamp());
        assertEquals(42L, immutable.sequence());
        assertEquals(SourceId.MATCHING_ENGINE, immutable.sourceId());
        assertEquals(EventType.ORDER_FILLED, immutable.eventType());
        assertEquals("order", immutable.payload());
    }

    @Test
    void testPoolExpansion() {
        EventPool pool = new EventPool(2);

        EventPool.MutableEvent event1 = pool.acquire();
        EventPool.MutableEvent event2 = pool.acquire();
        assertNotNull(event2); // Use event2 to avoid unused variable warning
        assertEquals(0, pool.available());

        // Pool is empty, should create new instance
        EventPool.MutableEvent event3 = pool.acquire();
        assertNotNull(event3);
        assertEquals(0, pool.available());

        // Release one
        pool.release(event1);
        assertEquals(1, pool.available());
    }

    @Test
    void testReleaseNullEvent() {
        EventPool pool = new EventPool(5);
        int before = pool.available();

        pool.release(null);

        assertEquals(before, pool.available());
    }

    @Test
    void testEventReuse() {
        EventPool pool = new EventPool(1);

        EventPool.MutableEvent event = pool.acquire();
        event.set(100L, 1L, SourceId.FEED_HANDLER, EventType.MARKET_DATA_UPDATE, 0L, "first");
        assertEquals("first", event.getPayload());

        pool.release(event);

        EventPool.MutableEvent reused = pool.acquire();
        assertSame(event, reused);
        // Should be reset
        assertEquals(0L, reused.getTimestamp());
        assertEquals(0L, reused.getSequence());
        assertNull(reused.getPayload());
    }

    @Test
    void testZeroAllocationAfterWarmup() {
        // This test demonstrates the zero-allocation pattern
        EventPool pool = new EventPool(10);

        // Warmup - these acquisitions don't allocate
        for (int i = 0; i < 10; i++) {
            EventPool.MutableEvent event = pool.acquire();
            event.set(System.nanoTime(), i, SourceId.ANALYTICS, EventType.LATENCY_SAMPLE, 0L, null);
            pool.release(event);
        }

        assertEquals(10, pool.available());

        // After warmup, pool is stable - no allocations needed
        EventPool.MutableEvent event = pool.acquire();
        assertNotNull(event);
        pool.release(event);
        assertEquals(10, pool.available());
    }
}
