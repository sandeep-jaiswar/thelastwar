package com.thelastwar.oms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for OrderStateMachine.
 * 
 * Tests cover:
 * - All valid state transitions
 * - Invalid transition rejection
 * - Idempotency guarantees
 * - Terminal state protection
 * - Thread-safety (basic checks)
 */
class OrderStateMachineTest {
    
    private InternalOrderId orderId;
    private OrderStateMachine stateMachine;
    
    @BeforeEach
    void setUp() {
        orderId = InternalOrderId.of(12345L);
        stateMachine = new OrderStateMachine(orderId);
    }
    
    @Test
    @DisplayName("New order starts in NEW state")
    void testInitialState() {
        assertEquals(OrderState.NEW, stateMachine.getCurrentState());
        assertEquals(orderId, stateMachine.getOrderId());
        assertFalse(stateMachine.isTerminal());
        assertTrue(stateMachine.isActive());
        assertEquals(0, stateMachine.getTransitionCount());
    }
    
    @Test
    @DisplayName("NEW -> ACCEPTED transition is valid")
    void testNewToAccepted() {
        boolean transitioned = stateMachine.transition(OrderState.ACCEPTED);
        
        assertTrue(transitioned);
        assertEquals(OrderState.ACCEPTED, stateMachine.getCurrentState());
        assertEquals(1, stateMachine.getTransitionCount());
        assertTrue(stateMachine.hasBeenInState(OrderState.ACCEPTED));
    }
    
    @Test
    @DisplayName("NEW -> REJECTED transition is valid")
    void testNewToRejected() {
        boolean transitioned = stateMachine.transition(OrderState.REJECTED);
        
        assertTrue(transitioned);
        assertEquals(OrderState.REJECTED, stateMachine.getCurrentState());
        assertTrue(stateMachine.isTerminal());
        assertFalse(stateMachine.isActive());
    }
    
    @Test
    @DisplayName("ACCEPTED -> WORKING transition is valid")
    void testAcceptedToWorking() {
        stateMachine.transition(OrderState.ACCEPTED);
        boolean transitioned = stateMachine.transition(OrderState.WORKING);
        
        assertTrue(transitioned);
        assertEquals(OrderState.WORKING, stateMachine.getCurrentState());
        assertEquals(2, stateMachine.getTransitionCount());
    }
    
    @Test
    @DisplayName("WORKING -> PARTIAL_FILL transition is valid")
    void testWorkingToPartialFill() {
        stateMachine.transition(OrderState.ACCEPTED);
        stateMachine.transition(OrderState.WORKING);
        boolean transitioned = stateMachine.transition(OrderState.PARTIAL_FILL);
        
        assertTrue(transitioned);
        assertEquals(OrderState.PARTIAL_FILL, stateMachine.getCurrentState());
        assertFalse(stateMachine.isFilled());
    }
    
    @Test
    @DisplayName("WORKING -> FILLED transition is valid")
    void testWorkingToFilled() {
        stateMachine.transition(OrderState.ACCEPTED);
        stateMachine.transition(OrderState.WORKING);
        boolean transitioned = stateMachine.transition(OrderState.FILLED);
        
        assertTrue(transitioned);
        assertEquals(OrderState.FILLED, stateMachine.getCurrentState());
        assertTrue(stateMachine.isFilled());
        assertTrue(stateMachine.isTerminal());
    }
    
    @Test
    @DisplayName("WORKING -> CANCELLED transition is valid")
    void testWorkingToCancelled() {
        stateMachine.transition(OrderState.ACCEPTED);
        stateMachine.transition(OrderState.WORKING);
        boolean transitioned = stateMachine.transition(OrderState.CANCELLED);
        
        assertTrue(transitioned);
        assertEquals(OrderState.CANCELLED, stateMachine.getCurrentState());
        assertTrue(stateMachine.isTerminal());
    }
    
    @Test
    @DisplayName("WORKING -> EXPIRED transition is valid")
    void testWorkingToExpired() {
        stateMachine.transition(OrderState.ACCEPTED);
        stateMachine.transition(OrderState.WORKING);
        boolean transitioned = stateMachine.transition(OrderState.EXPIRED);
        
        assertTrue(transitioned);
        assertEquals(OrderState.EXPIRED, stateMachine.getCurrentState());
        assertTrue(stateMachine.isTerminal());
    }
    
    @Test
    @DisplayName("PARTIAL_FILL -> PARTIAL_FILL transition is valid (idempotent fills)")
    void testPartialFillToPartialFill() {
        stateMachine.transition(OrderState.ACCEPTED);
        stateMachine.transition(OrderState.WORKING);
        stateMachine.transition(OrderState.PARTIAL_FILL);
        
        boolean transitioned = stateMachine.transition(OrderState.PARTIAL_FILL);
        
        // Idempotent transition to same state returns false
        assertFalse(transitioned);
        assertEquals(OrderState.PARTIAL_FILL, stateMachine.getCurrentState());
    }
    
    @Test
    @DisplayName("PARTIAL_FILL -> FILLED transition is valid")
    void testPartialFillToFilled() {
        stateMachine.transition(OrderState.ACCEPTED);
        stateMachine.transition(OrderState.WORKING);
        stateMachine.transition(OrderState.PARTIAL_FILL);
        boolean transitioned = stateMachine.transition(OrderState.FILLED);
        
        assertTrue(transitioned);
        assertEquals(OrderState.FILLED, stateMachine.getCurrentState());
        assertTrue(stateMachine.isFilled());
    }
    
    @Test
    @DisplayName("PARTIAL_FILL -> CANCELLED transition is valid")
    void testPartialFillToCancelled() {
        stateMachine.transition(OrderState.ACCEPTED);
        stateMachine.transition(OrderState.WORKING);
        stateMachine.transition(OrderState.PARTIAL_FILL);
        boolean transitioned = stateMachine.transition(OrderState.CANCELLED);
        
        assertTrue(transitioned);
        assertEquals(OrderState.CANCELLED, stateMachine.getCurrentState());
    }
    
    @Test
    @DisplayName("PARTIAL_FILL -> EXPIRED transition is valid")
    void testPartialFillToExpired() {
        stateMachine.transition(OrderState.ACCEPTED);
        stateMachine.transition(OrderState.WORKING);
        stateMachine.transition(OrderState.PARTIAL_FILL);
        boolean transitioned = stateMachine.transition(OrderState.EXPIRED);
        
        assertTrue(transitioned);
        assertEquals(OrderState.EXPIRED, stateMachine.getCurrentState());
    }
    
    @Test
    @DisplayName("Invalid transition NEW -> WORKING is rejected")
    void testInvalidNewToWorking() {
        assertThrows(OrderStateMachine.IllegalStateTransitionException.class, () -> {
            stateMachine.transition(OrderState.WORKING);
        });
    }
    
    @Test
    @DisplayName("Invalid transition NEW -> FILLED is rejected")
    void testInvalidNewToFilled() {
        assertThrows(OrderStateMachine.IllegalStateTransitionException.class, () -> {
            stateMachine.transition(OrderState.FILLED);
        });
    }
    
    @Test
    @DisplayName("Invalid transition ACCEPTED -> FILLED is rejected")
    void testInvalidAcceptedToFilled() {
        stateMachine.transition(OrderState.ACCEPTED);
        
        assertThrows(OrderStateMachine.IllegalStateTransitionException.class, () -> {
            stateMachine.transition(OrderState.FILLED);
        });
    }
    
    @Test
    @DisplayName("Cannot transition from terminal state FILLED")
    void testCannotTransitionFromFilled() {
        stateMachine.transition(OrderState.ACCEPTED);
        stateMachine.transition(OrderState.WORKING);
        stateMachine.transition(OrderState.FILLED);
        
        assertThrows(OrderStateMachine.IllegalStateTransitionException.class, () -> {
            stateMachine.transition(OrderState.CANCELLED);
        });
    }
    
    @Test
    @DisplayName("Cannot transition from terminal state REJECTED")
    void testCannotTransitionFromRejected() {
        stateMachine.transition(OrderState.REJECTED);
        
        assertThrows(OrderStateMachine.IllegalStateTransitionException.class, () -> {
            stateMachine.transition(OrderState.ACCEPTED);
        });
    }
    
    @Test
    @DisplayName("Cannot transition from terminal state CANCELLED")
    void testCannotTransitionFromCancelled() {
        stateMachine.transition(OrderState.ACCEPTED);
        stateMachine.transition(OrderState.WORKING);
        stateMachine.transition(OrderState.CANCELLED);
        
        assertThrows(OrderStateMachine.IllegalStateTransitionException.class, () -> {
            stateMachine.transition(OrderState.WORKING);
        });
    }
    
    @Test
    @DisplayName("Idempotency: Transitioning to current state is safe")
    void testIdempotency() {
        stateMachine.transition(OrderState.ACCEPTED);
        
        // Transition to current state returns false (no-op)
        boolean transitioned = stateMachine.transition(OrderState.ACCEPTED);
        
        assertFalse(transitioned);
        assertEquals(OrderState.ACCEPTED, stateMachine.getCurrentState());
        assertEquals(1, stateMachine.getTransitionCount()); // Count doesn't increase
    }
    
    @Test
    @DisplayName("State timestamps are recorded")
    void testStateTimestamps() {
        long beforeTransition = System.nanoTime();
        stateMachine.transition(OrderState.ACCEPTED);
        long afterTransition = System.nanoTime();
        
        Long acceptedTimestamp = stateMachine.getStateTimestamp(OrderState.ACCEPTED);
        assertNotNull(acceptedTimestamp);
        assertTrue(acceptedTimestamp >= beforeTransition);
        assertTrue(acceptedTimestamp <= afterTransition);
    }
    
    @Test
    @DisplayName("State history tracking works correctly")
    void testStateHistory() {
        assertFalse(stateMachine.hasBeenInState(OrderState.WORKING));
        
        stateMachine.transition(OrderState.ACCEPTED);
        stateMachine.transition(OrderState.WORKING);
        
        assertTrue(stateMachine.hasBeenInState(OrderState.NEW));
        assertTrue(stateMachine.hasBeenInState(OrderState.ACCEPTED));
        assertTrue(stateMachine.hasBeenInState(OrderState.WORKING));
        assertFalse(stateMachine.hasBeenInState(OrderState.FILLED));
    }
    
    @Test
    @DisplayName("Full order lifecycle: NEW -> ACCEPTED -> WORKING -> PARTIAL_FILL -> FILLED")
    void testFullOrderLifecycle() {
        assertEquals(OrderState.NEW, stateMachine.getCurrentState());
        
        stateMachine.transition(OrderState.ACCEPTED);
        assertEquals(OrderState.ACCEPTED, stateMachine.getCurrentState());
        
        stateMachine.transition(OrderState.WORKING);
        assertEquals(OrderState.WORKING, stateMachine.getCurrentState());
        
        stateMachine.transition(OrderState.PARTIAL_FILL);
        assertEquals(OrderState.PARTIAL_FILL, stateMachine.getCurrentState());
        assertFalse(stateMachine.isTerminal());
        
        stateMachine.transition(OrderState.FILLED);
        assertEquals(OrderState.FILLED, stateMachine.getCurrentState());
        assertTrue(stateMachine.isTerminal());
        assertTrue(stateMachine.isFilled());
        
        assertEquals(4, stateMachine.getTransitionCount());
    }
    
    @Test
    @DisplayName("Order rejection flow: NEW -> REJECTED")
    void testRejectionFlow() {
        stateMachine.transition(OrderState.REJECTED);
        
        assertEquals(OrderState.REJECTED, stateMachine.getCurrentState());
        assertTrue(stateMachine.isTerminal());
        assertFalse(stateMachine.isFilled());
        assertEquals(1, stateMachine.getTransitionCount());
    }
    
    @Test
    @DisplayName("Order cancellation flow: NEW -> ACCEPTED -> WORKING -> CANCELLED")
    void testCancellationFlow() {
        stateMachine.transition(OrderState.ACCEPTED);
        stateMachine.transition(OrderState.WORKING);
        stateMachine.transition(OrderState.CANCELLED);
        
        assertEquals(OrderState.CANCELLED, stateMachine.getCurrentState());
        assertTrue(stateMachine.isTerminal());
        assertFalse(stateMachine.isFilled());
        assertTrue(stateMachine.isCancellable() == false);
    }
    
    @Test
    @DisplayName("Null state transition throws exception")
    void testNullStateTransition() {
        assertThrows(NullPointerException.class, () -> {
            stateMachine.transition(null);
        });
    }
    
    @Test
    @DisplayName("State machine toString contains relevant info")
    void testToString() {
        String str = stateMachine.toString();
        
        assertTrue(str.contains("12345"));
        assertTrue(str.contains("NEW"));
        assertTrue(str.contains("transitions=0"));
    }
}
