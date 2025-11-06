package com.thelastwar.orderbook;

/**
 * Order type enumeration.
 * 
 * This enum provides type-safe representation of order types while mapping
 * to the efficient byte representation used in the Order record.
 */
public enum OrderType {
    MARKET((byte) 1),
    LIMIT((byte) 2),
    STOP((byte) 3),
    STOP_LIMIT((byte) 4);
    
    private final byte value;
    
    OrderType(byte value) {
        this.value = value;
    }
    
    /**
     * Get the byte value for this order type.
     * 
     * @return byte representation
     */
    public byte getValue() {
        return value;
    }
    
    /**
     * Convert byte value to OrderType enum.
     * 
     * @param value byte value
     * @return corresponding OrderType
     * @throws IllegalArgumentException if value is invalid
     */
    public static OrderType fromByte(byte value) {
        return switch (value) {
            case 1 -> MARKET;
            case 2 -> LIMIT;
            case 3 -> STOP;
            case 4 -> STOP_LIMIT;
            default -> throw new IllegalArgumentException("Invalid order type value: " + value);
        };
    }
}
