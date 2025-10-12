package com.thelastwar.oms;

/**
 * ClientOrderId is a value object representing a unique identifier assigned by the client
 * for an order. This is used for order tracking, reconciliation, and idempotency.
 * 
 * The client order ID is:
 * - Immutable: Once created, it cannot be changed
 * - Unique per client: Each client must ensure uniqueness within their namespace
 * - Human-readable: Can include alphanumeric characters for easier tracking
 * 
 * Performance characteristics:
 * - Compact representation using String interning for memory efficiency
 * - Fast equality checks via string comparison
 * - Suitable for use as HashMap keys
 * 
 * @param value The client-assigned order identifier (max 64 characters)
 */
public record ClientOrderId(String value) {
    
    /**
     * Maximum allowed length for client order IDs.
     */
    public static final int MAX_LENGTH = 64;
    
    /**
     * Compact constructor with validation.
     */
    public ClientOrderId {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Client order ID cannot be null or empty");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Client order ID exceeds maximum length of %d characters: %s", 
                    MAX_LENGTH, value)
            );
        }
        // Validate format: alphanumeric, hyphen, underscore only
        if (!value.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException(
                "Client order ID must contain only alphanumeric characters, hyphens, or underscores: " + value
            );
        }
        // Intern the string for memory efficiency when many IDs are created
        value = value.intern();
    }
    
    /**
     * Creates a ClientOrderId from a string value.
     * 
     * @param value The client order identifier
     * @return new ClientOrderId instance
     * @throws IllegalArgumentException if value is invalid
     */
    public static ClientOrderId of(String value) {
        return new ClientOrderId(value);
    }
    
    /**
     * Gets the string representation of the client order ID.
     * 
     * @return the client order ID value
     */
    @Override
    public String toString() {
        return value;
    }
}
