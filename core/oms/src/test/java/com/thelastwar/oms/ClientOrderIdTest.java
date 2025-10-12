package com.thelastwar.oms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ClientOrderId value object.
 */
class ClientOrderIdTest {
    
    @Test
    @DisplayName("Valid client order ID is created successfully")
    void testValidClientOrderId() {
        ClientOrderId id = ClientOrderId.of("ORDER-12345");
        
        assertEquals("ORDER-12345", id.value());
        assertEquals("ORDER-12345", id.toString());
    }
    
    @Test
    @DisplayName("Alphanumeric with hyphens and underscores is valid")
    void testAlphanumericWithSpecialChars() {
        ClientOrderId id1 = ClientOrderId.of("ABC123");
        ClientOrderId id2 = ClientOrderId.of("ORDER_123");
        ClientOrderId id3 = ClientOrderId.of("ORDER-456");
        ClientOrderId id4 = ClientOrderId.of("ORD_123-456");
        
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotNull(id3);
        assertNotNull(id4);
    }
    
    @Test
    @DisplayName("Null value throws exception")
    void testNullValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            ClientOrderId.of(null);
        });
    }
    
    @Test
    @DisplayName("Empty value throws exception")
    void testEmptyValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            ClientOrderId.of("");
        });
    }
    
    @Test
    @DisplayName("Value exceeding max length throws exception")
    void testExceedsMaxLength() {
        String longValue = "A".repeat(ClientOrderId.MAX_LENGTH + 1);
        
        assertThrows(IllegalArgumentException.class, () -> {
            ClientOrderId.of(longValue);
        });
    }
    
    @Test
    @DisplayName("Value at max length is valid")
    void testAtMaxLength() {
        String maxValue = "A".repeat(ClientOrderId.MAX_LENGTH);
        ClientOrderId id = ClientOrderId.of(maxValue);
        
        assertEquals(ClientOrderId.MAX_LENGTH, id.value().length());
    }
    
    @Test
    @DisplayName("Invalid characters throw exception")
    void testInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> {
            ClientOrderId.of("ORDER@123");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            ClientOrderId.of("ORDER 123");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            ClientOrderId.of("ORDER.123");
        });
    }
    
    @Test
    @DisplayName("Equality works correctly")
    void testEquality() {
        ClientOrderId id1 = ClientOrderId.of("ORDER-123");
        ClientOrderId id2 = ClientOrderId.of("ORDER-123");
        ClientOrderId id3 = ClientOrderId.of("ORDER-456");
        
        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertEquals(id1.hashCode(), id2.hashCode());
    }
}
