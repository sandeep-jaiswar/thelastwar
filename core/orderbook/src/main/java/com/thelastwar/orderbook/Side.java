package com.thelastwar.orderbook;

/**
 * Order side enumeration.
 * 
 * This enum provides type-safe representation of order sides while mapping
 * to the efficient byte representation used in the Order record.
 */
public enum Side {
    BUY((byte) 1),
    SELL((byte) 2);
    
    private final byte value;
    
    Side(byte value) {
        this.value = value;
    }
    
    /**
     * Get the byte value for this side.
     * 
     * @return byte representation (1=BUY, 2=SELL)
     */
    public byte getValue() {
        return value;
    }
    
    /**
     * Convert byte value to Side enum.
     * 
     * @param value byte value (1 or 2)
     * @return corresponding Side
     * @throws IllegalArgumentException if value is invalid
     */
    public static Side fromByte(byte value) {
        return switch (value) {
            case 1 -> BUY;
            case 2 -> SELL;
            default -> throw new IllegalArgumentException("Invalid side value: " + value);
        };
    }
}
