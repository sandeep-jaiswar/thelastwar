package com.thelastwar.oms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InternalOrderId value object.
 */
class InternalOrderIdTest {
    
    @Test
    @DisplayName("Valid internal order ID is created successfully")
    void testValidInternalOrderId() {
        InternalOrderId id = InternalOrderId.of(12345L);
        
        assertEquals(12345L, id.value());
        assertEquals("12345", id.toString());
    }
    
    @Test
    @DisplayName("Zero value throws exception")
    void testZeroValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            InternalOrderId.of(0L);
        });
    }
    
    @Test
    @DisplayName("Negative value throws exception")
    void testNegativeValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            InternalOrderId.of(-1L);
        });
    }
    
    @Test
    @DisplayName("Maximum long value is valid")
    void testMaxValue() {
        InternalOrderId id = InternalOrderId.of(Long.MAX_VALUE);
        
        assertEquals(Long.MAX_VALUE, id.value());
    }
    
    @Test
    @DisplayName("Next increments the ID")
    void testNext() {
        InternalOrderId id = InternalOrderId.of(100L);
        InternalOrderId nextId = id.next();
        
        assertEquals(101L, nextId.value());
        assertEquals(100L, id.value()); // Original is immutable
    }
    
    @Test
    @DisplayName("Next at max value throws exception")
    void testNextAtMaxValue() {
        InternalOrderId id = InternalOrderId.of(Long.MAX_VALUE);
        
        assertThrows(ArithmeticException.class, id::next);
    }
    
    @Test
    @DisplayName("Comparison works correctly")
    void testComparison() {
        InternalOrderId id1 = InternalOrderId.of(100L);
        InternalOrderId id2 = InternalOrderId.of(200L);
        InternalOrderId id3 = InternalOrderId.of(100L);
        
        assertTrue(id1.compareTo(id2) < 0);
        assertTrue(id2.compareTo(id1) > 0);
        assertEquals(0, id1.compareTo(id3));
    }
    
    @Test
    @DisplayName("isBefore works correctly")
    void testIsBefore() {
        InternalOrderId id1 = InternalOrderId.of(100L);
        InternalOrderId id2 = InternalOrderId.of(200L);
        
        assertTrue(id1.isBefore(id2));
        assertFalse(id2.isBefore(id1));
        assertFalse(id1.isBefore(id1));
    }
    
    @Test
    @DisplayName("isAfter works correctly")
    void testIsAfter() {
        InternalOrderId id1 = InternalOrderId.of(100L);
        InternalOrderId id2 = InternalOrderId.of(200L);
        
        assertTrue(id2.isAfter(id1));
        assertFalse(id1.isAfter(id2));
        assertFalse(id1.isAfter(id1));
    }
    
    @Test
    @DisplayName("Equality works correctly")
    void testEquality() {
        InternalOrderId id1 = InternalOrderId.of(12345L);
        InternalOrderId id2 = InternalOrderId.of(12345L);
        InternalOrderId id3 = InternalOrderId.of(67890L);
        
        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertEquals(id1.hashCode(), id2.hashCode());
    }
}
