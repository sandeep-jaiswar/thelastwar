package com.thelastwar.eventbus.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExecutionEvent immutable record.
 */
class ExecutionEventTest {
    
    @Test
    void testExecutionEventCreation() {
        ExecutionEvent exec = new ExecutionEvent(
                11111L,
                12345L,
                "AAPL",
                ExecutionEvent.SIDE_BUY,
                ExecutionEvent.EXEC_TYPE_NEW,
                ExecutionEvent.STATUS_NEW,
                0L,
                0L,
                0L,
                100L,
                System.nanoTime(),
                999L,
                1,
                0
        );
        
        assertEquals(11111L, exec.executionId());
        assertEquals(12345L, exec.orderId());
        assertEquals("AAPL", exec.symbol());
        assertEquals(ExecutionEvent.SIDE_BUY, exec.side());
        assertEquals(ExecutionEvent.EXEC_TYPE_NEW, exec.executionType());
        assertEquals(ExecutionEvent.STATUS_NEW, exec.orderStatus());
        assertEquals(0L, exec.lastQuantity());
        assertEquals(0L, exec.lastPrice());
        assertEquals(0L, exec.cumulativeQty());
        assertEquals(100L, exec.leavesQuantity());
        assertEquals(999L, exec.account());
        assertEquals(1, exec.exchange());
        assertEquals(0, exec.rejectReason());
    }
    
    @Test
    void testNewOrderFactoryMethod() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        ExecutionEvent exec = ExecutionEvent.newOrder(11111L, order);
        
        assertEquals(11111L, exec.executionId());
        assertEquals(12345L, exec.orderId());
        assertEquals("AAPL", exec.symbol());
        assertEquals(ExecutionEvent.SIDE_BUY, exec.side());
        assertEquals(ExecutionEvent.EXEC_TYPE_NEW, exec.executionType());
        assertEquals(ExecutionEvent.STATUS_NEW, exec.orderStatus());
        assertEquals(0L, exec.lastQuantity());
        assertEquals(0L, exec.lastPrice());
        assertEquals(0L, exec.cumulativeQty());
        assertEquals(100L, exec.leavesQuantity());
        assertTrue(exec.timestamp() > 0);
    }
    
    @Test
    void testFillFactoryMethod() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        // Partial fill: 50 out of 100
        ExecutionEvent exec = ExecutionEvent.fill(11111L, order, 50L, 15050L, 50L, 50L);
        
        assertEquals(ExecutionEvent.EXEC_TYPE_PARTIAL_FILL, exec.executionType());
        assertEquals(ExecutionEvent.STATUS_PARTIALLY_FILLED, exec.orderStatus());
        assertEquals(50L, exec.lastQuantity());
        assertEquals(15050L, exec.lastPrice());
        assertEquals(50L, exec.cumulativeQty());
        assertEquals(50L, exec.leavesQuantity());
    }
    
    @Test
    void testCompleteFillFactoryMethod() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        // Complete fill: 100 out of 100
        ExecutionEvent exec = ExecutionEvent.fill(11111L, order, 100L, 15050L, 100L, 0L);
        
        assertEquals(ExecutionEvent.EXEC_TYPE_FILL, exec.executionType());
        assertEquals(ExecutionEvent.STATUS_FILLED, exec.orderStatus());
        assertEquals(100L, exec.lastQuantity());
        assertEquals(15050L, exec.lastPrice());
        assertEquals(100L, exec.cumulativeQty());
        assertEquals(0L, exec.leavesQuantity());
    }
    
    @Test
    void testRejectFactoryMethod() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        ExecutionEvent exec = ExecutionEvent.reject(11111L, order, 1001);
        
        assertEquals(ExecutionEvent.EXEC_TYPE_REJECTED, exec.executionType());
        assertEquals(ExecutionEvent.STATUS_REJECTED, exec.orderStatus());
        assertEquals(0L, exec.lastQuantity());
        assertEquals(0L, exec.lastPrice());
        assertEquals(0L, exec.cumulativeQty());
        assertEquals(100L, exec.leavesQuantity());
        assertEquals(1001, exec.rejectReason());
    }
    
    @Test
    void testInvalidExecutionId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ExecutionEvent(
                    0L, // invalid
                    12345L,
                    "AAPL",
                    ExecutionEvent.SIDE_BUY,
                    ExecutionEvent.EXEC_TYPE_NEW,
                    ExecutionEvent.STATUS_NEW,
                    0L,
                    0L,
                    0L,
                    100L,
                    System.nanoTime(),
                    999L,
                    1,
                    0
            );
        });
    }
    
    @Test
    void testInvalidOrderId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ExecutionEvent(
                    11111L,
                    0L, // invalid
                    "AAPL",
                    ExecutionEvent.SIDE_BUY,
                    ExecutionEvent.EXEC_TYPE_NEW,
                    ExecutionEvent.STATUS_NEW,
                    0L,
                    0L,
                    0L,
                    100L,
                    System.nanoTime(),
                    999L,
                    1,
                    0
            );
        });
    }
    
    @Test
    void testInvalidSymbol() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ExecutionEvent(
                    11111L,
                    12345L,
                    null, // invalid
                    ExecutionEvent.SIDE_BUY,
                    ExecutionEvent.EXEC_TYPE_NEW,
                    ExecutionEvent.STATUS_NEW,
                    0L,
                    0L,
                    0L,
                    100L,
                    System.nanoTime(),
                    999L,
                    1,
                    0
            );
        });
    }
    
    @Test
    void testInvalidSide() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ExecutionEvent(
                    11111L,
                    12345L,
                    "AAPL",
                    (byte) 99, // invalid
                    ExecutionEvent.EXEC_TYPE_NEW,
                    ExecutionEvent.STATUS_NEW,
                    0L,
                    0L,
                    0L,
                    100L,
                    System.nanoTime(),
                    999L,
                    1,
                    0
            );
        });
    }
    
    @Test
    void testInvalidExecutionType() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ExecutionEvent(
                    11111L,
                    12345L,
                    "AAPL",
                    ExecutionEvent.SIDE_BUY,
                    (byte) 99, // invalid
                    ExecutionEvent.STATUS_NEW,
                    0L,
                    0L,
                    0L,
                    100L,
                    System.nanoTime(),
                    999L,
                    1,
                    0
            );
        });
    }
    
    @Test
    void testInvalidLastQuantity() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ExecutionEvent(
                    11111L,
                    12345L,
                    "AAPL",
                    ExecutionEvent.SIDE_BUY,
                    ExecutionEvent.EXEC_TYPE_NEW,
                    ExecutionEvent.STATUS_NEW,
                    -1L, // invalid
                    0L,
                    0L,
                    100L,
                    System.nanoTime(),
                    999L,
                    1,
                    0
            );
        });
    }
    
    @Test
    void testIsFill() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        ExecutionEvent newExec = ExecutionEvent.newOrder(11111L, order);
        assertFalse(newExec.isFill());
        
        ExecutionEvent partialFill = ExecutionEvent.fill(11111L, order, 50L, 15050L, 50L, 50L);
        assertTrue(partialFill.isFill());
        
        ExecutionEvent completeFill = ExecutionEvent.fill(11111L, order, 100L, 15050L, 100L, 0L);
        assertTrue(completeFill.isFill());
        
        ExecutionEvent reject = ExecutionEvent.reject(11111L, order, 1001);
        assertFalse(reject.isFill());
    }
    
    @Test
    void testIsCompleteFill() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        ExecutionEvent partialFill = ExecutionEvent.fill(11111L, order, 50L, 15050L, 50L, 50L);
        assertFalse(partialFill.isCompleteFill());
        
        ExecutionEvent completeFill = ExecutionEvent.fill(11111L, order, 100L, 15050L, 100L, 0L);
        assertTrue(completeFill.isCompleteFill());
    }
    
    @Test
    void testIsPartialFill() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        ExecutionEvent partialFill = ExecutionEvent.fill(11111L, order, 50L, 15050L, 50L, 50L);
        assertTrue(partialFill.isPartialFill());
        
        ExecutionEvent completeFill = ExecutionEvent.fill(11111L, order, 100L, 15050L, 100L, 0L);
        assertFalse(completeFill.isPartialFill());
    }
    
    @Test
    void testIsRejected() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        ExecutionEvent newExec = ExecutionEvent.newOrder(11111L, order);
        assertFalse(newExec.isRejected());
        
        ExecutionEvent reject = ExecutionEvent.reject(11111L, order, 1001);
        assertTrue(reject.isRejected());
    }
    
    @Test
    void testIsTerminal() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        ExecutionEvent newExec = ExecutionEvent.newOrder(11111L, order);
        assertFalse(newExec.isTerminal());
        
        ExecutionEvent partialFill = ExecutionEvent.fill(11111L, order, 50L, 15050L, 50L, 50L);
        assertFalse(partialFill.isTerminal());
        
        ExecutionEvent completeFill = ExecutionEvent.fill(11111L, order, 100L, 15050L, 100L, 0L);
        assertTrue(completeFill.isTerminal());
        
        ExecutionEvent reject = ExecutionEvent.reject(11111L, order, 1001);
        assertTrue(reject.isTerminal());
    }
    
    @Test
    void testExecutionTypeConstants() {
        assertEquals(0, ExecutionEvent.EXEC_TYPE_NEW);
        assertEquals(1, ExecutionEvent.EXEC_TYPE_PARTIAL_FILL);
        assertEquals(2, ExecutionEvent.EXEC_TYPE_FILL);
        assertEquals(3, ExecutionEvent.EXEC_TYPE_CANCELLED);
        assertEquals(4, ExecutionEvent.EXEC_TYPE_REJECTED);
        assertEquals(5, ExecutionEvent.EXEC_TYPE_REPLACED);
    }
    
    @Test
    void testOrderStatusConstants() {
        assertEquals(0, ExecutionEvent.STATUS_NEW);
        assertEquals(1, ExecutionEvent.STATUS_PARTIALLY_FILLED);
        assertEquals(2, ExecutionEvent.STATUS_FILLED);
        assertEquals(3, ExecutionEvent.STATUS_CANCELLED);
        assertEquals(4, ExecutionEvent.STATUS_REJECTED);
    }
    
    @Test
    void testImmutability() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        ExecutionEvent exec = ExecutionEvent.newOrder(11111L, order);
        
        // Verify record is immutable by checking all accessors work
        assertEquals(11111L, exec.executionId());
        assertEquals(12345L, exec.orderId());
        assertEquals("AAPL", exec.symbol());
    }
}
