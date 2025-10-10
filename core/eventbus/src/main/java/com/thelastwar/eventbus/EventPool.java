package com.thelastwar.eventbus;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Object pool for Event instances to minimize heap allocations in hot path.
 * Follows the "no heap allocations in hot path" principle from the
 * architecture.
 * 
 * Usage:
 * 
 * <pre>
 * EventPool pool = new EventPool(1000);
 * Event event = pool.acquire();
 * // ... use event ...
 * pool.release(event);
 * </pre>
 * 
 * Note: This is a simple implementation. Production use should consider:
 * - ThreadLocal pools for lock-free access
 * - Off-heap storage for very large pools
 * - Per-thread object pools to avoid contention
 */
public class EventPool {
    private final BlockingQueue<MutableEvent> pool;
    private final int capacity;

    /**
     * Creates an event pool with specified capacity.
     * 
     * @param capacity Maximum number of pooled events
     */
    public EventPool(int capacity) {
        this.capacity = capacity;
        this.pool = new ArrayBlockingQueue<>(capacity);

        // Pre-populate pool
        for (int i = 0; i < capacity; i++) {
            boolean offered = pool.offer(new MutableEvent());
            if (!offered) {
                throw new IllegalStateException("Failed to pre-populate event pool");
            }
        }
    }

    /**
     * Acquires a mutable event from the pool.
     * If pool is empty, creates a new instance (pool expansion).
     * 
     * @return mutable event instance
     */
    public MutableEvent acquire() {
        MutableEvent event = pool.poll();
        if (event == null) {
            // Pool exhausted - create new instance
            return new MutableEvent();
        }
        return event;
    }

    /**
     * Returns a mutable event to the pool.
     * The event is reset for reuse.
     * 
     * @param event Event to return
     */
    public void release(MutableEvent event) {
        if (event != null) {
            event.reset();
            boolean offered = pool.offer(event); // If pool is full, event will be GC'd
            // Optionally log or handle if not offered
        }
    }

    /**
     * Returns current pool size (available events).
     * 
     * @return number of available events
     */
    public int available() {
        return pool.size();
    }

    /**
     * Returns pool capacity.
     * 
     * @return maximum pool size
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Mutable event for use with object pooling.
     * This allows reusing event instances to avoid allocations.
     */
    public static class MutableEvent {
        private long timestamp;
        private long sequence;
        private int sourceId;
        private int eventType;
        private long header;
        private Object payload;

        /**
         * Sets all event fields.
         */
        public MutableEvent set(long timestamp, long sequence, int sourceId, int eventType, long header,
                Object payload) {
            this.timestamp = timestamp;
            this.sequence = sequence;
            this.sourceId = sourceId;
            this.eventType = eventType;
            this.header = header;
            this.payload = payload;
            return this;
        }

        /**
         * Converts to immutable Event record.
         * 
         * @return immutable Event
         */
        public Event toEvent() {
            return new Event(timestamp, sequence, sourceId, eventType, header, payload);
        }

        /**
         * Resets all fields for reuse.
         */
        void reset() {
            this.timestamp = 0;
            this.sequence = 0;
            this.sourceId = 0;
            this.eventType = 0;
            this.header = 0;
            this.payload = null;
        }

        // Getters
        public long getTimestamp() {
            return timestamp;
        }

        public long getSequence() {
            return sequence;
        }

        public int getSourceId() {
            return sourceId;
        }

        public int getEventType() {
            return eventType;
        }

        public long getHeader() {
            return header;
        }

        public Object getPayload() {
            return payload;
        }
    }
}
