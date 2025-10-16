package com.thelastwar.oms;

import java.util.Objects;

/**
 * ChildOrder represents an order created by the Smart Order Router (SOR) as a result
 * of splitting a parent order across multiple venues or routing strategies.
 * 
 * Each child order:
 * - Has a unique internal order ID
 * - Correlates back to a parent order
 * - Targets a specific venue/destination
 * - Contains a portion of the parent order quantity
 * - Tracks its own fill state
 * 
 * Immutability: This class is immutable for thread-safety. Fill state tracking
 * should be done externally via OrderStateMachine and Allocation records.
 * 
 * @param childOrderId     Internal order ID for the child order
 * @param parentOrderId    Internal order ID of the parent order
 * @param symbol           Trading symbol (inherited from parent)
 * @param side             Order side (inherited from parent)
 * @param orderType        Order type (inherited from parent)
 * @param quantity         Quantity allocated to this child order
 * @param price            Order price (may differ from parent for market-making strategies)
 * @param venue            Target venue/exchange for routing
 * @param routingStrategy  SOR strategy used (e.g., "VWAP", "TWAP", "DMA")
 * @param timestamp        Child order creation timestamp
 */
public record ChildOrder(
        InternalOrderId childOrderId,
        InternalOrderId parentOrderId,
        String symbol,
        byte side,
        byte orderType,
        long quantity,
        long price,
        String venue,
        String routingStrategy,
        long timestamp) {
    
    /**
     * Compact constructor with validation.
     */
    public ChildOrder {
        Objects.requireNonNull(childOrderId, "Child order ID cannot be null");
        Objects.requireNonNull(parentOrderId, "Parent order ID cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be empty");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        Objects.requireNonNull(venue, "Venue cannot be null");
        if (venue.isEmpty()) {
            throw new IllegalArgumentException("Venue cannot be empty");
        }
        Objects.requireNonNull(routingStrategy, "Routing strategy cannot be null");
        if (timestamp <= 0) {
            throw new IllegalArgumentException("Timestamp must be positive");
        }
    }
    
    /**
     * Factory method to create a child order from a parent order.
     * 
     * @param childOrderId     Child order ID
     * @param parentOrder      Parent order to split from
     * @param quantity         Quantity for this child
     * @param venue            Target venue
     * @param routingStrategy  Routing strategy
     * @return new ChildOrder instance
     */
    public static ChildOrder fromParent(
            InternalOrderId childOrderId,
            ParentOrder parentOrder,
            long quantity,
            String venue,
            String routingStrategy) {
        
        return new ChildOrder(
            childOrderId,
            parentOrder.getParentOrderId(),
            parentOrder.getSymbol(),
            parentOrder.getSide(),
            parentOrder.getOrderType(),
            quantity,
            parentOrder.getPrice(),
            venue,
            routingStrategy,
            System.nanoTime()
        );
    }
    
    /**
     * Factory method to create a child order with custom price.
     * Useful for smart routing strategies that adjust prices.
     * 
     * @param childOrderId     Child order ID
     * @param parentOrder      Parent order to split from
     * @param quantity         Quantity for this child
     * @param price            Custom price for this child
     * @param venue            Target venue
     * @param routingStrategy  Routing strategy
     * @return new ChildOrder instance
     */
    public static ChildOrder fromParentWithPrice(
            InternalOrderId childOrderId,
            ParentOrder parentOrder,
            long quantity,
            long price,
            String venue,
            String routingStrategy) {
        
        return new ChildOrder(
            childOrderId,
            parentOrder.getParentOrderId(),
            parentOrder.getSymbol(),
            parentOrder.getSide(),
            parentOrder.getOrderType(),
            quantity,
            price,
            venue,
            routingStrategy,
            System.nanoTime()
        );
    }
    
    /**
     * Creates a formatted string representation.
     * 
     * @return human-readable child order details
     */
    public String toDisplayString() {
        return String.format(
            "ChildOrder[id=%s, parent=%s, symbol=%s, qty=%d, venue=%s, strategy=%s]",
            childOrderId, parentOrderId, symbol, quantity, venue, routingStrategy
        );
    }
}
