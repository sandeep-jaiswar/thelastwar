package com.thelastwar.eventbus.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OrderCancel event model.
 */
class OrderCancelTest {
    
    @Test
    void testCreateCancel() {
        OrderCancel cancel = OrderCancel.create(1L, "AAPL", 999L, 100L);
        
        assertNotNull(cancel);
        assertEquals(1L, cancel.orderId());
        assertEquals("AAPL", cancel.symbol());
        assertEquals(999L, cancel.account());
        assertEquals(100L, cancel.requestId());
        assertTrue(cancel.timestamp() > 0);
    }
    
    @Test
    void testInvalidOrderId() {
        assertThrows(IllegalArgumentException.class, () -> {
            OrderCancel.create(0L, "AAPL", 999L, 100L);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            OrderCancel.create(-1L, "AAPL", 999L, 100L);
        });
    }
    
    @Test
    void testInvalidSymbol() {
        assertThrows(IllegalArgumentException.class, () -> {
            OrderCancel.create(1L, null, 999L, 100L);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            OrderCancel.create(1L, "", 999L, 100L);
        });
    }
    
    @Test
    void testInvalidRequestId() {
        assertThrows(IllegalArgumentException.class, () -> {
            OrderCancel.create(1L, "AAPL", 999L, 0L);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            OrderCancel.create(1L, "AAPL", 999L, -1L);
        });
    }
    
    @Test
    void testNegativeTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderCancel(1L, "AAPL", -1L, 999L, 100L);
        });
    }
}
