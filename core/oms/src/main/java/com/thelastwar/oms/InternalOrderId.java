package com.thelastwar.oms;

/**
 * InternalOrderId is a value object representing a unique identifier assigned by the OMS
 * for tracking orders throughout the system lifecycle. This ID is system-generated and
 * provides a canonical identifier independent of client order IDs.
 * 
 * The internal order ID is:
 * - Immutable: Once created, it cannot be changed
 * - Globally unique: System ensures uniqueness across all orders
 * - Monotonically increasing: Higher IDs represent more recent orders (for some ID schemes)
 * - Efficient: Uses long primitive for fast comparisons and minimal memory footprint
 * 
 * Performance characteristics:
 * - Zero allocation: Uses primitive long
 * - Fast equality and comparison operations
 * - Cache-friendly: 8-byte alignment
 * 
 * ID Generation Strategies:
 * - Sequence-based: Monotonically increasing counter (1, 2, 3, ...)
 * - Timestamp-based: Epoch time in nanoseconds
 * - Snowflake-like: Timestamp + datacenter + sequence (distributed systems)
 * 
 * @param value The system-assigned internal order identifier (positive long)
 */
public record InternalOrderId(long value) implements Comparable<InternalOrderId> {
    
    /**
     * Compact constructor with validation.
     */
    public InternalOrderId {
        if (value <= 0) {
            throw new IllegalArgumentException("Internal order ID must be positive: " + value);
        }
    }
    
    /**
     * Creates an InternalOrderId from a long value.
     * 
     * @param value The internal order identifier
     * @return new InternalOrderId instance
     * @throws IllegalArgumentException if value is not positive
     */
    public static InternalOrderId of(long value) {
        return new InternalOrderId(value);
    }
    
    /**
     * Creates the next sequential InternalOrderId.
     * 
     * @return new InternalOrderId with incremented value
     * @throws ArithmeticException if incrementing would overflow
     */
    public InternalOrderId next() {
        if (value == Long.MAX_VALUE) {
            throw new ArithmeticException("Cannot increment InternalOrderId: would overflow");
        }
        return new InternalOrderId(value + 1);
    }
    
    /**
     * Compares this InternalOrderId with another for ordering.
     * 
     * @param other the InternalOrderId to compare to
     * @return negative if this < other, zero if equal, positive if this > other
     */
    @Override
    public int compareTo(InternalOrderId other) {
        return Long.compare(this.value, other.value);
    }
    
    /**
     * Checks if this InternalOrderId is before another.
     * 
     * @param other the InternalOrderId to compare to
     * @return true if this ID is before other
     */
    public boolean isBefore(InternalOrderId other) {
        return this.value < other.value;
    }
    
    /**
     * Checks if this InternalOrderId is after another.
     * 
     * @param other the InternalOrderId to compare to
     * @return true if this ID is after other
     */
    public boolean isAfter(InternalOrderId other) {
        return this.value > other.value;
    }
    
    /**
     * Gets the string representation of the internal order ID.
     * 
     * @return the internal order ID value as string
     */
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
