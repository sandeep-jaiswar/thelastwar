package com.thelastwar.eventbus.model;

/**
 * OrderEnvelope wraps an OrderEvent with additional metadata for matching engine processing.
 * This envelope pattern provides context and routing information without modifying the core OrderEvent.
 * 
 * Performance characteristics:
 * - Compact memory layout via record
 * - Zero-copy design with references
 * - Suitable for high-throughput processing
 * 
 * @param orderEvent    The actual order event being processed
 * @param sequenceId    Sequence number for deterministic replay
 * @param sourceId      Source identifier (gateway, OMS, etc.)
 * @param receivedTime  Time when envelope was created (nanoseconds)
 * @param routingKey    Optional routing key for sharding/partitioning
 */
public record OrderEnvelope(
        OrderEvent orderEvent,
        long sequenceId,
        int sourceId,
        long receivedTime,
        int routingKey) {
    
    /**
     * Compact constructor with validation.
     */
    public OrderEnvelope {
        if (orderEvent == null) {
            throw new IllegalArgumentException("OrderEvent cannot be null");
        }
        if (sequenceId < 0) {
            throw new IllegalArgumentException("Sequence ID cannot be negative");
        }
        if (receivedTime < 0) {
            throw new IllegalArgumentException("Received time cannot be negative");
        }
    }
    
    /**
     * Factory method for creating an envelope from an order event.
     * 
     * @param orderEvent  The order event to wrap
     * @param sequenceId  Sequence number
     * @param sourceId    Source identifier
     * @return new OrderEnvelope with current timestamp
     */
    public static OrderEnvelope wrap(OrderEvent orderEvent, long sequenceId, int sourceId) {
        return new OrderEnvelope(orderEvent, sequenceId, sourceId, System.nanoTime(), 0);
    }
    
    /**
     * Factory method with routing key for sharded processing.
     * 
     * @param orderEvent  The order event to wrap
     * @param sequenceId  Sequence number
     * @param sourceId    Source identifier
     * @param routingKey  Routing key for partitioning
     * @return new OrderEnvelope with current timestamp
     */
    public static OrderEnvelope wrap(OrderEvent orderEvent, long sequenceId, int sourceId, int routingKey) {
        return new OrderEnvelope(orderEvent, sequenceId, sourceId, System.nanoTime(), routingKey);
    }
    
    /**
     * Gets the order ID from the wrapped event.
     * 
     * @return order ID
     */
    public long orderId() {
        return orderEvent.orderId();
    }
    
    /**
     * Gets the symbol from the wrapped event.
     * 
     * @return symbol
     */
    public String symbol() {
        return orderEvent.symbol();
    }
}
