package com.thelastwar.oms;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OrderStateMachine implements a deterministic state machine for order lifecycle management.
 * 
 * This state machine provides:
 * - Deterministic state transitions: Same input always produces same output
 * - Idempotency: Repeated transitions to the same state are safe
 * - Thread-safety: Can be safely accessed from multiple threads
 * - Invalid transition rejection: Prevents illegal state changes
 * - State history tracking: Records all state changes for audit
 * 
 * The state machine enforces strict transition rules defined in OrderTransition enum.
 * Any attempt to perform an invalid transition will throw an IllegalStateTransitionException.
 * 
 * Performance characteristics:
 * - O(1) state transition validation via enum lookup
 * - Lock-free for read operations
 * - Minimal contention for write operations (per-order locking)
 * - Cache-friendly state representation
 * 
 * Thread-safety:
 * This class is thread-safe. Multiple threads can safely call transition() on the same
 * state machine instance without external synchronization.
 */
public class OrderStateMachine {
    
    private final InternalOrderId orderId;
    private volatile OrderState currentState;
    private final ConcurrentHashMap<OrderState, Long> stateTimestamps;
    private volatile long lastTransitionTime;
    private volatile int transitionCount;
    
    /**
     * Creates a new OrderStateMachine for the given order ID.
     * The initial state is set to NEW.
     * 
     * @param orderId The internal order ID
     */
    public OrderStateMachine(InternalOrderId orderId) {
        this.orderId = Objects.requireNonNull(orderId, "Order ID cannot be null");
        this.currentState = OrderState.NEW;
        this.stateTimestamps = new ConcurrentHashMap<>();
        this.lastTransitionTime = System.nanoTime();
        this.transitionCount = 0;
        recordStateEntry(OrderState.NEW);
    }
    
    /**
     * Creates a new OrderStateMachine with a specific initial state.
     * This is useful for restoring state from persistence.
     * 
     * @param orderId The internal order ID
     * @param initialState The initial state
     */
    public OrderStateMachine(InternalOrderId orderId, OrderState initialState) {
        this.orderId = Objects.requireNonNull(orderId, "Order ID cannot be null");
        this.currentState = Objects.requireNonNull(initialState, "Initial state cannot be null");
        this.stateTimestamps = new ConcurrentHashMap<>();
        this.lastTransitionTime = System.nanoTime();
        this.transitionCount = 0;
        recordStateEntry(initialState);
    }
    
    /**
     * Attempts to transition to a new state.
     * 
     * This method is idempotent: transitioning to the current state is allowed and is a no-op.
     * This ensures that duplicate events (due to retries or network issues) do not cause errors.
     * 
     * @param newState The target state
     * @return true if transition occurred, false if already in target state (idempotent)
     * @throws IllegalStateTransitionException if the transition is invalid
     */
    public synchronized boolean transition(OrderState newState) {
        Objects.requireNonNull(newState, "Target state cannot be null");
        
        // Idempotency check: if already in target state, return false (no-op)
        if (currentState == newState) {
            return false;
        }
        
        // Validate transition
        if (!OrderTransition.isValidTransition(currentState, newState)) {
            throw new IllegalStateTransitionException(
                String.format("Invalid transition for order %s: %s -> %s", 
                    orderId, currentState, newState)
            );
        }
        
        // Check if trying to transition from a terminal state
        if (currentState.isTerminal()) {
            throw new IllegalStateTransitionException(
                String.format("Cannot transition from terminal state %s for order %s", 
                    currentState, orderId)
            );
        }
        
        // Perform transition
        OrderState oldState = currentState;
        currentState = newState;
        lastTransitionTime = System.nanoTime();
        transitionCount++;
        recordStateEntry(newState);
        
        return true;
    }
    
    /**
     * Gets the current state of the order.
     * 
     * @return the current OrderState
     */
    public OrderState getCurrentState() {
        return currentState;
    }
    
    /**
     * Gets the order ID associated with this state machine.
     * 
     * @return the internal order ID
     */
    public InternalOrderId getOrderId() {
        return orderId;
    }
    
    /**
     * Checks if the order is in a terminal state.
     * 
     * @return true if the order is in a terminal state
     */
    public boolean isTerminal() {
        return currentState.isTerminal();
    }
    
    /**
     * Checks if the order is filled.
     * 
     * @return true if the order is completely filled
     */
    public boolean isFilled() {
        return currentState.isFilled();
    }
    
    /**
     * Checks if the order is in an active state.
     * 
     * @return true if the order is active (not terminal)
     */
    public boolean isActive() {
        return currentState.isActive();
    }
    
    /**
     * Checks if the order can be cancelled from the current state.
     * 
     * @return true if cancellation is allowed
     */
    public boolean isCancellable() {
        return currentState.isCancellable();
    }
    
    /**
     * Gets the timestamp when the order entered the current state.
     * 
     * @return timestamp in nanoseconds
     */
    public long getLastTransitionTime() {
        return lastTransitionTime;
    }
    
    /**
     * Gets the total number of state transitions that have occurred.
     * 
     * @return transition count
     */
    public int getTransitionCount() {
        return transitionCount;
    }
    
    /**
     * Gets the timestamp when the order entered a specific state.
     * Returns null if the order has never been in that state.
     * 
     * @param state The state to query
     * @return timestamp in nanoseconds, or null if never in that state
     */
    public Long getStateTimestamp(OrderState state) {
        return stateTimestamps.get(state);
    }
    
    /**
     * Checks if the order has ever been in the specified state.
     * 
     * @param state The state to check
     * @return true if the order has been in this state
     */
    public boolean hasBeenInState(OrderState state) {
        return stateTimestamps.containsKey(state);
    }
    
    /**
     * Records the timestamp when the order enters a state.
     * 
     * @param state The state being entered
     */
    private void recordStateEntry(OrderState state) {
        stateTimestamps.put(state, System.nanoTime());
    }
    
    /**
     * Returns a string representation of the state machine.
     * 
     * @return string representation
     */
    @Override
    public String toString() {
        return String.format("OrderStateMachine[orderId=%s, state=%s, transitions=%d, terminal=%s]",
            orderId, currentState, transitionCount, currentState.isTerminal());
    }
    
    /**
     * Exception thrown when an invalid state transition is attempted.
     */
    public static class IllegalStateTransitionException extends RuntimeException {
        public IllegalStateTransitionException(String message) {
            super(message);
        }
    }
}
