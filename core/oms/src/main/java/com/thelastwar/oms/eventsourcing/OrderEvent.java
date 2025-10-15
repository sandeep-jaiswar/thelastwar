package com.thelastwar.oms.eventsourcing;

import com.thelastwar.oms.OrderState;

import java.time.Instant;

/**
 * Represents an order event for event sourcing.
 */
public record OrderEvent(
    long internalOrderId,
    String clientOrderId,
    EventType eventType,
    OrderState newState,
    long timestamp,
    Instant occurredAt,
    long eventOffset,
    String symbol,
    Short side,
    Short orderType,
    Long quantity,
    Long price,
    Long account,
    Long filledQuantity,
    Long remainingQuantity
) {
    public enum EventType {
        ORDER_SUBMITTED,
        ORDER_ACCEPTED,
        ORDER_REJECTED,
        ORDER_WORKING,
        ORDER_PARTIAL_FILL,
        ORDER_FILLED,
        ORDER_CANCELLED,
        ORDER_EXPIRED
    }
    
    public static OrderEvent orderSubmitted(long internalOrderId, String clientOrderId,
                                           String symbol, short side, short orderType,
                                           long quantity, long price, long account) {
        return new OrderEvent(
            internalOrderId,
            clientOrderId,
            EventType.ORDER_SUBMITTED,
            OrderState.NEW,
            System.currentTimeMillis(),
            Instant.now(),
            0,
            symbol,
            side,
            orderType,
            quantity,
            price,
            account,
            0L,
            quantity
        );
    }
    
    public static OrderEvent stateTransition(long internalOrderId, String clientOrderId,
                                            EventType eventType, OrderState newState,
                                            long filledQuantity, long remainingQuantity) {
        return new OrderEvent(
            internalOrderId,
            clientOrderId,
            eventType,
            newState,
            System.currentTimeMillis(),
            Instant.now(),
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            filledQuantity,
            remainingQuantity
        );
    }
}
