package com.thelastwar.eventbus;

/**
 * Immutable Event model with metadata for the Event Bus.
 * Designed to be GC-neutral by using primitive types where possible.
 */
public final class Event {
    private final long timestamp;
    private final long sequence;
    private final int sourceId;
    private final int eventType;
    private final long header;
    private final Object payload; // Actual payload - can be reused object pool reference

    private Event(long timestamp, long sequence, int sourceId, int eventType, long header, Object payload) {
        this.timestamp = timestamp;
        this.sequence = sequence;
        this.sourceId = sourceId;
        this.eventType = eventType;
        this.header = header;
        this.payload = payload;
    }

    /**
     * Creates a new Event instance.
     * 
     * @param timestamp Event timestamp in nanoseconds
     * @param sequence Sequence number for ordering
     * @param sourceId Source subsystem identifier (use constants)
     * @param eventType Type of event (use EventType constants)
     * @param header Additional header metadata packed as long
     * @param payload Event payload
     * @return new Event instance
     */
    public static Event create(long timestamp, long sequence, int sourceId, int eventType, long header, Object payload) {
        return new Event(timestamp, sequence, sourceId, eventType, header, payload);
    }

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

    @Override
    public String toString() {
        return "Event{" +
                "timestamp=" + timestamp +
                ", sequence=" + sequence +
                ", sourceId=" + sourceId +
                ", eventType=" + eventType +
                ", header=" + header +
                ", payload=" + payload +
                '}';
    }
}
