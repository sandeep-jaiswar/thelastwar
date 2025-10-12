package com.thelastwar.oms;

/**
 * OrderState represents all possible states in the order lifecycle state machine.
 * Each state represents a distinct phase in the order's journey from creation to termination.
 * 
 * State Categories:
 * - Initial: NEW
 * - Active: ACCEPTED, WORKING, PARTIAL_FILL
 * - Terminal: FILLED, CANCELLED, REJECTED, EXPIRED
 * 
 * State Descriptions:
 * - NEW: Order received but not yet validated or accepted
 * - ACCEPTED: Order validated and accepted by OMS, pending routing
 * - WORKING: Order routed to exchange/matching engine and active in the order book
 * - PARTIAL_FILL: Order partially executed, remaining quantity still working
 * - FILLED: Order completely executed (terminal state)
 * - CANCELLED: Order cancelled by user or system (terminal state)
 * - REJECTED: Order rejected due to validation or risk checks (terminal state)
 * - EXPIRED: Order expired based on time-in-force rules (terminal state)
 */
public enum OrderState {
    /**
     * NEW: Order received but not yet validated or accepted.
     * Initial state when order enters the OMS.
     */
    NEW(false, false),
    
    /**
     * ACCEPTED: Order validated and accepted by OMS, pending routing to venue.
     * Order has passed pre-trade risk checks.
     */
    ACCEPTED(false, false),
    
    /**
     * WORKING: Order routed to exchange/matching engine and active in the order book.
     * Order is available for matching against incoming orders.
     */
    WORKING(false, false),
    
    /**
     * PARTIAL_FILL: Order partially executed, remaining quantity still working.
     * At least one fill has occurred, but order is not fully executed.
     */
    PARTIAL_FILL(false, false),
    
    /**
     * FILLED: Order completely executed (terminal state).
     * All quantity has been filled, no further state changes possible.
     */
    FILLED(true, true),
    
    /**
     * CANCELLED: Order cancelled by user or system (terminal state).
     * Order removed from order book, no further matching possible.
     */
    CANCELLED(true, false),
    
    /**
     * REJECTED: Order rejected due to validation or risk checks (terminal state).
     * Order never entered the order book.
     */
    REJECTED(true, false),
    
    /**
     * EXPIRED: Order expired based on time-in-force rules (terminal state).
     * Order automatically removed from order book due to time expiry.
     */
    EXPIRED(true, false);
    
    private final boolean terminal;
    private final boolean filled;
    
    /**
     * Constructor for OrderState.
     * 
     * @param terminal true if this is a terminal state (no further transitions allowed)
     * @param filled true if this state represents a fully filled order
     */
    OrderState(boolean terminal, boolean filled) {
        this.terminal = terminal;
        this.filled = filled;
    }
    
    /**
     * Checks if this is a terminal state.
     * Terminal states do not allow any further state transitions.
     * 
     * @return true if this is a terminal state
     */
    public boolean isTerminal() {
        return terminal;
    }
    
    /**
     * Checks if this state represents a filled order.
     * 
     * @return true if the order is fully filled
     */
    public boolean isFilled() {
        return filled;
    }
    
    /**
     * Checks if this is an active state (not terminal).
     * 
     * @return true if the order can still be modified or filled
     */
    public boolean isActive() {
        return !terminal;
    }
    
    /**
     * Checks if this state allows cancellation.
     * Only non-terminal states can be cancelled.
     * 
     * @return true if the order can be cancelled from this state
     */
    public boolean isCancellable() {
        return !terminal;
    }
}
