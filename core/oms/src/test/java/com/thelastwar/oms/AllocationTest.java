package com.thelastwar.oms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Allocation domain model.
 */
class AllocationTest {
    
    @Test
    @DisplayName("Valid allocation is created successfully")
    void testValidAllocation() {
        InternalOrderId orderId = InternalOrderId.of(12345L);
        Allocation allocation = new Allocation(
            1L,         // allocationId
            orderId,    // orderId
            999L,       // executionId
            15000L,     // fillPrice
            100L,       // fillQuantity
            System.nanoTime(), // fillTimestamp
            "NASDAQ",   // venue
            888L        // counterpartyOrderId
        );
        
        assertEquals(1L, allocation.allocationId());
        assertEquals(orderId, allocation.orderId());
        assertEquals(999L, allocation.executionId());
        assertEquals(15000L, allocation.fillPrice());
        assertEquals(100L, allocation.fillQuantity());
        assertEquals("NASDAQ", allocation.venue());
        assertEquals(888L, allocation.counterpartyOrderId());
    }
    
    @Test
    @DisplayName("Factory create method works correctly")
    void testCreateFactory() {
        InternalOrderId orderId = InternalOrderId.of(12345L);
        Allocation allocation = Allocation.create(
            1L,
            orderId,
            999L,
            15000L,
            100L,
            "NYSE",
            888L
        );
        
        assertNotNull(allocation);
        assertEquals(orderId, allocation.orderId());
        assertTrue(allocation.fillTimestamp() > 0);
    }
    
    @Test
    @DisplayName("Calculate value works correctly")
    void testCalculateValue() {
        InternalOrderId orderId = InternalOrderId.of(12345L);
        Allocation allocation = new Allocation(
            1L, orderId, 999L, 15000L, 100L, System.nanoTime(), "NASDAQ", 888L
        );
        
        assertEquals(1500000L, allocation.calculateValue());
    }
    
    @Test
    @DisplayName("Invalid allocation ID throws exception")
    void testInvalidAllocationId() {
        InternalOrderId orderId = InternalOrderId.of(12345L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Allocation(0L, orderId, 999L, 15000L, 100L, System.nanoTime(), "NASDAQ", 888L);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Allocation(-1L, orderId, 999L, 15000L, 100L, System.nanoTime(), "NASDAQ", 888L);
        });
    }
    
    @Test
    @DisplayName("Null order ID throws exception")
    void testNullOrderId() {
        assertThrows(NullPointerException.class, () -> {
            new Allocation(1L, null, 999L, 15000L, 100L, System.nanoTime(), "NASDAQ", 888L);
        });
    }
    
    @Test
    @DisplayName("Invalid execution ID throws exception")
    void testInvalidExecutionId() {
        InternalOrderId orderId = InternalOrderId.of(12345L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Allocation(1L, orderId, 0L, 15000L, 100L, System.nanoTime(), "NASDAQ", 888L);
        });
    }
    
    @Test
    @DisplayName("Negative fill price throws exception")
    void testNegativeFillPrice() {
        InternalOrderId orderId = InternalOrderId.of(12345L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Allocation(1L, orderId, 999L, -100L, 100L, System.nanoTime(), "NASDAQ", 888L);
        });
    }
    
    @Test
    @DisplayName("Zero fill quantity throws exception")
    void testZeroFillQuantity() {
        InternalOrderId orderId = InternalOrderId.of(12345L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Allocation(1L, orderId, 999L, 15000L, 0L, System.nanoTime(), "NASDAQ", 888L);
        });
    }
    
    @Test
    @DisplayName("Null venue throws exception")
    void testNullVenue() {
        InternalOrderId orderId = InternalOrderId.of(12345L);
        
        assertThrows(NullPointerException.class, () -> {
            new Allocation(1L, orderId, 999L, 15000L, 100L, System.nanoTime(), null, 888L);
        });
    }
    
    @Test
    @DisplayName("Display string contains relevant info")
    void testToDisplayString() {
        InternalOrderId orderId = InternalOrderId.of(12345L);
        Allocation allocation = new Allocation(
            1L, orderId, 999L, 15000L, 100L, System.nanoTime(), "NASDAQ", 888L
        );
        
        String display = allocation.toDisplayString();
        
        assertTrue(display.contains("1"));
        assertTrue(display.contains("12345"));
        assertTrue(display.contains("999"));
        assertTrue(display.contains("15000"));
        assertTrue(display.contains("100"));
        assertTrue(display.contains("NASDAQ"));
    }
}
