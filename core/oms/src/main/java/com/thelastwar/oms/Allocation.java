package com.thelastwar.oms;

import java.util.Objects;

/**
 * Allocation represents a fill execution allocated to a specific order.
 * Each allocation records details of a trade execution including price, quantity,
 * and counterparty information.
 * 
 * Allocations are immutable records of execution events. They form the audit trail
 * of how an order was filled and at what prices.
 * 
 * An order may have multiple allocations:
 * - Partial fills: Each partial fill creates a new allocation
 * - Multiple counterparties: Different executions against different orders
 * 
 * Performance characteristics:
 * - Immutable: Thread-safe by design
 * - Compact: Uses primitives where possible
 * - Cache-friendly: All fields stored inline
 * 
 * @param allocationId Unique identifier for this allocation
 * @param orderId The internal order ID this allocation belongs to
 * @param executionId The execution ID from the matching engine
 * @param fillPrice The price at which this allocation was executed
 * @param fillQuantity The quantity filled in this allocation
 * @param fillTimestamp The timestamp when this allocation occurred (nanoseconds)
 * @param venue The venue/exchange where execution occurred
 * @param counterpartyOrderId The order ID of the counterparty (for audit)
 */
public record Allocation(
        long allocationId,
        InternalOrderId orderId,
        long executionId,
        long fillPrice,
        long fillQuantity,
        long fillTimestamp,
        String venue,
        long counterpartyOrderId) {
    
    /**
     * Compact constructor with validation.
     */
    public Allocation {
        if (allocationId <= 0) {
            throw new IllegalArgumentException("Allocation ID must be positive");
        }
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        if (executionId <= 0) {
            throw new IllegalArgumentException("Execution ID must be positive");
        }
        if (fillPrice < 0) {
            throw new IllegalArgumentException("Fill price cannot be negative");
        }
        if (fillQuantity <= 0) {
            throw new IllegalArgumentException("Fill quantity must be positive");
        }
        if (fillTimestamp <= 0) {
            throw new IllegalArgumentException("Fill timestamp must be positive");
        }
        Objects.requireNonNull(venue, "Venue cannot be null");
    }
    
    /**
     * Creates a new Allocation with the current timestamp.
     * 
     * @param allocationId Unique allocation identifier
     * @param orderId The internal order ID
     * @param executionId The execution ID
     * @param fillPrice The fill price
     * @param fillQuantity The fill quantity
     * @param venue The venue identifier
     * @param counterpartyOrderId The counterparty order ID
     * @return new Allocation instance
     */
    public static Allocation create(
            long allocationId,
            InternalOrderId orderId,
            long executionId,
            long fillPrice,
            long fillQuantity,
            String venue,
            long counterpartyOrderId) {
        return new Allocation(
            allocationId,
            orderId,
            executionId,
            fillPrice,
            fillQuantity,
            System.nanoTime(),
            venue,
            counterpartyOrderId
        );
    }
    
    /**
     * Calculates the total value of this allocation.
     * Value = fillPrice * fillQuantity
     * 
     * @return the total execution value
     */
    public long calculateValue() {
        return fillPrice * fillQuantity;
    }
    

    
    /**
     * Creates a formatted string representation of this allocation.
     * 
     * @return human-readable allocation details
     */
    public String toDisplayString() {
        return String.format(
            "Allocation[id=%d, order=%s, exec=%d, price=%d, qty=%d, venue=%s]",
            allocationId, orderId, executionId, fillPrice, fillQuantity, venue
        );
    }
}
