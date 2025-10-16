package com.thelastwar.oms;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * RoutingDecision represents the Smart Order Router's decision on how to split
 * and route a parent order across multiple venues or execution strategies.
 * 
 * A routing decision contains:
 * - The parent order being routed
 * - A list of routing instructions (one per child order to create)
 * - Routing metadata (strategy used, decision timestamp)
 * - Validation that routing instructions sum to parent quantity
 * 
 * Immutability: This record is immutable for thread-safety and deterministic replay.
 * 
 * @param parentOrderId         Parent order ID being routed
 * @param routingInstructions   List of routing instructions for child orders
 * @param strategyName          Name of the routing strategy used
 * @param decisionTimestamp     Timestamp when routing decision was made
 * @param metadata              Additional routing metadata (e.g., market conditions)
 */
public record RoutingDecision(
        InternalOrderId parentOrderId,
        List<RoutingInstruction> routingInstructions,
        String strategyName,
        long decisionTimestamp,
        String metadata) {
    
    /**
     * Compact constructor with validation.
     */
    public RoutingDecision {
        Objects.requireNonNull(parentOrderId, "Parent order ID cannot be null");
        Objects.requireNonNull(routingInstructions, "Routing instructions cannot be null");
        if (routingInstructions.isEmpty()) {
            throw new IllegalArgumentException("Routing instructions cannot be empty");
        }
        Objects.requireNonNull(strategyName, "Strategy name cannot be null");
        if (decisionTimestamp <= 0) {
            throw new IllegalArgumentException("Decision timestamp must be positive");
        }
        
        // Make defensive copy and ensure immutability
        routingInstructions = List.copyOf(routingInstructions);
    }
    
    /**
     * Validates that the routing instructions sum to the expected total quantity.
     * 
     * @param expectedTotalQuantity The parent order's total quantity
     * @throws IllegalStateException if quantities don't match
     */
    public void validateQuantities(long expectedTotalQuantity) {
        long sum = routingInstructions.stream()
            .mapToLong(RoutingInstruction::quantity)
            .sum();
        
        if (sum != expectedTotalQuantity) {
            throw new IllegalStateException(
                String.format(
                    "Routing instructions sum (%d) does not match parent quantity (%d)",
                    sum, expectedTotalQuantity
                )
            );
        }
    }
    
    /**
     * Gets the number of child orders that will be created.
     * 
     * @return number of routing instructions
     */
    public int getChildOrderCount() {
        return routingInstructions.size();
    }
    
    /**
     * Returns an immutable copy of routing instructions.
     */
    @Override
    public List<RoutingInstruction> routingInstructions() {
        return Collections.unmodifiableList(routingInstructions);
    }
    
    /**
     * Creates a routing decision with current timestamp.
     * 
     * @param parentOrderId         Parent order ID
     * @param routingInstructions   List of routing instructions
     * @param strategyName          Strategy name
     * @param metadata              Optional metadata
     * @return new RoutingDecision instance
     */
    public static RoutingDecision create(
            InternalOrderId parentOrderId,
            List<RoutingInstruction> routingInstructions,
            String strategyName,
            String metadata) {
        
        return new RoutingDecision(
            parentOrderId,
            routingInstructions,
            strategyName,
            System.nanoTime(),
            metadata != null ? metadata : ""
        );
    }
    
    /**
     * RoutingInstruction specifies how a portion of the parent order should be routed.
     * 
     * @param venue            Target venue/exchange (e.g., "NYSE", "NASDAQ", "BATS")
     * @param quantity         Quantity to route to this venue
     * @param price            Price for this routing (may differ from parent)
     * @param priority         Routing priority (lower = higher priority)
     * @param timeoutMillis    Timeout for this routing in milliseconds
     */
    public record RoutingInstruction(
            String venue,
            long quantity,
            long price,
            int priority,
            long timeoutMillis) {
        
        /**
         * Compact constructor with validation.
         */
        public RoutingInstruction {
            Objects.requireNonNull(venue, "Venue cannot be null");
            if (venue.isEmpty()) {
                throw new IllegalArgumentException("Venue cannot be empty");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            if (price < 0) {
                throw new IllegalArgumentException("Price cannot be negative");
            }
            if (priority < 0) {
                throw new IllegalArgumentException("Priority cannot be negative");
            }
            if (timeoutMillis <= 0) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
        }
        
        /**
         * Creates a routing instruction with default timeout (5 seconds).
         * 
         * @param venue     Target venue
         * @param quantity  Quantity to route
         * @param price     Price
         * @param priority  Priority
         * @return new RoutingInstruction
         */
        public static RoutingInstruction create(String venue, long quantity, long price, int priority) {
            return new RoutingInstruction(venue, quantity, price, priority, 5000);
        }
    }
}
