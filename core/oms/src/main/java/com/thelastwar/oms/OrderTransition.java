package com.thelastwar.oms;

/**
 * OrderTransition represents valid state transitions in the order lifecycle state machine.
 * Each transition captures the before and after states, along with the trigger event.
 * 
 * Valid transitions are explicitly enumerated to ensure deterministic behavior and
 * prevent invalid state changes. Any attempt to perform a transition not in this enum
 * will be rejected by the state machine.
 * 
 * Transition Flow Diagram:
 * 
 *                    NEW
 *                     |
 *           +---------+---------+
 *           |                   |
 *       ACCEPTED            REJECTED
 *           |
 *       WORKING
 *           |
 *     +-----+-----+-----+
 *     |     |     |     |
 * PARTIAL  FILLED CANCELLED EXPIRED
 *  FILL     |
 *     |     |
 *     +-----+
 *        |
 *     FILLED
 */
public enum OrderTransition {
    /**
     * NEW -> ACCEPTED: Order passes validation and risk checks.
     * Triggered when OMS accepts the order for processing.
     */
    NEW_TO_ACCEPTED(OrderState.NEW, OrderState.ACCEPTED, "Order accepted by OMS"),
    
    /**
     * NEW -> REJECTED: Order fails validation or risk checks.
     * Triggered when order is rejected at entry.
     */
    NEW_TO_REJECTED(OrderState.NEW, OrderState.REJECTED, "Order rejected"),
    
    /**
     * ACCEPTED -> WORKING: Order routed to venue and entered in order book.
     * Triggered when order is successfully placed on the order book.
     */
    ACCEPTED_TO_WORKING(OrderState.ACCEPTED, OrderState.WORKING, "Order working on venue"),
    
    /**
     * ACCEPTED -> REJECTED: Order rejected by venue.
     * Triggered when venue rejects the order after OMS acceptance.
     */
    ACCEPTED_TO_REJECTED(OrderState.ACCEPTED, OrderState.REJECTED, "Order rejected by venue"),
    
    /**
     * ACCEPTED -> CANCELLED: Order cancelled before reaching venue.
     * Triggered when order is cancelled while in routing.
     */
    ACCEPTED_TO_CANCELLED(OrderState.ACCEPTED, OrderState.CANCELLED, "Order cancelled before routing"),
    
    /**
     * WORKING -> PARTIAL_FILL: Order partially executed.
     * Triggered when the first partial fill occurs.
     */
    WORKING_TO_PARTIAL_FILL(OrderState.WORKING, OrderState.PARTIAL_FILL, "Order partially filled"),
    
    /**
     * WORKING -> FILLED: Order completely executed.
     * Triggered when order is fully filled in a single execution.
     */
    WORKING_TO_FILLED(OrderState.WORKING, OrderState.FILLED, "Order filled"),
    
    /**
     * WORKING -> CANCELLED: Order cancelled while working.
     * Triggered by user cancel request or system cancel.
     */
    WORKING_TO_CANCELLED(OrderState.WORKING, OrderState.CANCELLED, "Order cancelled"),
    
    /**
     * WORKING -> EXPIRED: Order expired while working.
     * Triggered when order reaches expiry time (DAY, GTD orders).
     */
    WORKING_TO_EXPIRED(OrderState.WORKING, OrderState.EXPIRED, "Order expired"),
    
    /**
     * PARTIAL_FILL -> PARTIAL_FILL: Subsequent partial fills.
     * Triggered when additional quantity is filled but order not complete.
     */
    PARTIAL_FILL_TO_PARTIAL_FILL(OrderState.PARTIAL_FILL, OrderState.PARTIAL_FILL, "Additional partial fill"),
    
    /**
     * PARTIAL_FILL -> FILLED: Order completely executed after partial fills.
     * Triggered when the final fill completes the order.
     */
    PARTIAL_FILL_TO_FILLED(OrderState.PARTIAL_FILL, OrderState.FILLED, "Order filled completely"),
    
    /**
     * PARTIAL_FILL -> CANCELLED: Partially filled order cancelled.
     * Triggered when user cancels a partially filled order.
     */
    PARTIAL_FILL_TO_CANCELLED(OrderState.PARTIAL_FILL, OrderState.CANCELLED, "Partially filled order cancelled"),
    
    /**
     * PARTIAL_FILL -> EXPIRED: Partially filled order expired.
     * Triggered when a partially filled order reaches expiry time.
     */
    PARTIAL_FILL_TO_EXPIRED(OrderState.PARTIAL_FILL, OrderState.EXPIRED, "Partially filled order expired");
    
    private final OrderState fromState;
    private final OrderState toState;
    private final String description;
    
    /**
     * Constructor for OrderTransition.
     * 
     * @param fromState The source state
     * @param toState The destination state
     * @param description Human-readable description of the transition
     */
    OrderTransition(OrderState fromState, OrderState toState, String description) {
        this.fromState = fromState;
        this.toState = toState;
        this.description = description;
    }
    
    /**
     * Gets the source state of this transition.
     * 
     * @return the from state
     */
    public OrderState getFromState() {
        return fromState;
    }
    
    /**
     * Gets the destination state of this transition.
     * 
     * @return the to state
     */
    public OrderState getToState() {
        return toState;
    }
    
    /**
     * Gets the description of this transition.
     * 
     * @return human-readable description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Checks if this transition is valid from the given state to the target state.
     * 
     * @param from The current state
     * @param to The target state
     * @return true if a transition exists from the given state to target state
     */
    public static boolean isValidTransition(OrderState from, OrderState to) {
        for (OrderTransition transition : values()) {
            if (transition.fromState == from && transition.toState == to) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Finds the transition for the given state change.
     * 
     * @param from The current state
     * @param to The target state
     * @return the OrderTransition if valid, null otherwise
     */
    public static OrderTransition findTransition(OrderState from, OrderState to) {
        for (OrderTransition transition : values()) {
            if (transition.fromState == from && transition.toState == to) {
                return transition;
            }
        }
        return null;
    }
}
