package com.thelastwar.oms.persistence;

import com.thelastwar.oms.OrderState;

/**
 * Record representing the persistent state of an order.
 * 
 * This record contains all necessary information to restore an order's state
 * from the database or snapshot.
 */
public record OrderStateRecord(
    long internalOrderId,
    String clientOrderId,
    String symbol,
    short side,
    short orderType,
    long quantity,
    long price,
    long account,
    OrderState currentState,
    long filledQuantity,
    long remainingQuantity,
    long version
) {
    public OrderStateRecord {
        if (internalOrderId <= 0) {
            throw new IllegalArgumentException("Internal order ID must be positive");
        }
        if (clientOrderId == null || clientOrderId.isBlank()) {
            throw new IllegalArgumentException("Client order ID cannot be null or blank");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (remainingQuantity < 0) {
            throw new IllegalArgumentException("Remaining quantity cannot be negative");
        }
        if (filledQuantity < 0) {
            throw new IllegalArgumentException("Filled quantity cannot be negative");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Version must be at least 1");
        }
    }
    
    public boolean isActive() {
        return currentState == OrderState.NEW ||
               currentState == OrderState.ACCEPTED ||
               currentState == OrderState.WORKING ||
               currentState == OrderState.PARTIAL_FILL;
    }
    
    public boolean isTerminal() {
        return currentState == OrderState.FILLED ||
               currentState == OrderState.CANCELLED ||
               currentState == OrderState.REJECTED ||
               currentState == OrderState.EXPIRED;
    }
}
