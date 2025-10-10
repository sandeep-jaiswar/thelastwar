package com.thelastwar.eventbus;

/**
 * Immutable Event record with metadata for the Event Bus.
 * Uses Java 25 record for compact, efficient representation.
 * Designed for GC-neutral operation with primitive types and object pooling
 * support.
 * 
 * Performance characteristics:
 * - Zero allocation in hot path when using object pools
 * - Compact memory layout via record
 * - All metadata as primitives (no autoboxing)
 * - Suitable for deterministic replay
 * 
 * @param timestamp Event timestamp in nanoseconds (System.nanoTime())
 * @param sequence  Sequence number for ordering and deterministic replay
 * @param sourceId  Source subsystem identifier (use SourceId constants)
 * @param eventType Type of event (use EventType constants)
 * @param header    Additional header metadata packed as long (8 bytes of
 *                  flags/metadata)
 * @param payload   Event payload (prefer pooled objects or off-heap references)
 */
public record Event(
        long timestamp,
        long sequence,
        int sourceId,
        int eventType,
        long header,
        Object payload) {
    /**
     * Factory method for creating events.
     * Use this for clarity and to support future object pooling.
     * 
     * @param timestamp Event timestamp in nanoseconds
     * @param sequence  Sequence number for ordering
     * @param sourceId  Source subsystem identifier
     * @param eventType Type of event
     * @param header    Additional header metadata
     * @param payload   Event payload (can be pooled object reference)
     * @return new Event instance
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static Event create(long timestamp, long sequence, int sourceId, int eventType, long header,
            Object payload) {
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("Sequence cannot be negative");
        }
        if (!EventType.isValid(eventType)) {
            throw new IllegalArgumentException("Invalid event type: " + eventType);
        }
        return new Event(timestamp, sequence, sourceId, eventType, header, payload);
    }

    /**
     * Creates an event with current timestamp.
     * Useful for immediate event creation.
     * 
     * @param sequence  Sequence number
     * @param sourceId  Source identifier
     * @param eventType Event type
     * @param header    Header metadata
     * @param payload   Payload
     * @return new Event with current timestamp
     */
    public static Event now(long sequence, int sourceId, int eventType, long header, Object payload) {
        return new Event(System.nanoTime(), sequence, sourceId, eventType, header, payload);
    }

    /**
     * Extracts latency from current time to event timestamp.
     * Useful for monitoring and latency tracking.
     * 
     * @return latency in nanoseconds
     */
    public long getLatencyNanos() {
        return System.nanoTime() - timestamp;
    }

    /**
     * Checks if this event is from a specific source.
     * 
     * @param expectedSourceId Source ID to check
     * @return true if matches
     */
    public boolean isFromSource(int expectedSourceId) {
        return this.sourceId == expectedSourceId;
    }

    /**
     * Checks if this event is of a specific type.
     * 
     * @param expectedEventType Event type to check
     * @return true if matches
     */
    public boolean isOfType(int expectedEventType) {
        return this.eventType == expectedEventType;
    }
}
