package com.thelastwar.eventbus.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OrderModify event model.
 */
class OrderModifyTest {
    
    @Test
    void testCreateModify() {
        OrderModify modify = OrderModify.create(1L, "AAPL", 15000L, 200L, 999L, 100L);
        
        assertNotNull(modify);
        assertEquals(1L, modify.orderId());
        assertEquals("AAPL", modify.symbol());
        assertEquals(15000L, modify.newPrice());
        assertEquals(200L, modify.newQuantity());
        assertEquals(999L, modify.account());
        assertEquals(100L, modify.requestId());
        assertTrue(modify.timestamp() > 0);
        assertTrue(modify.modifiesPrice());
        assertTrue(modify.modifiesQuantity());
    }
    
    @Test
    void testModifyPriceOnly() {
        OrderModify modify = OrderModify.modifyPrice(1L, "AAPL", 15000L, 999L, 100L);
        
        assertNotNull(modify);
        assertEquals(15000L, modify.newPrice());
        assertEquals(0L, modify.newQuantity());
        assertTrue(modify.modifiesPrice());
        assertFalse(modify.modifiesQuantity());
    }
    
    @Test
    void testModifyQuantityOnly() {
        OrderModify modify = OrderModify.modifyQuantity(1L, "AAPL", 200L, 999L, 100L);
        
        assertNotNull(modify);
        assertEquals(0L, modify.newPrice());
        assertEquals(200L, modify.newQuantity());
        assertFalse(modify.modifiesPrice());
        assertTrue(modify.modifiesQuantity());
    }
    
    @Test
    void testInvalidOrderId() {
        assertThrows(IllegalArgumentException.class, () -> {
            OrderModify.create(0L, "AAPL", 15000L, 200L, 999L, 100L);
        });
    }
    
    @Test
    void testInvalidSymbol() {
        assertThrows(IllegalArgumentException.class, () -> {
            OrderModify.create(1L, null, 15000L, 200L, 999L, 100L);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            OrderModify.create(1L, "", 15000L, 200L, 999L, 100L);
        });
    }
    
    @Test
    void testNegativePrice() {
        assertThrows(IllegalArgumentException.class, () -> {
            OrderModify.create(1L, "AAPL", -1L, 200L, 999L, 100L);
        });
    }
    
    @Test
    void testNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> {
            OrderModify.create(1L, "AAPL", 15000L, -1L, 999L, 100L);
        });
    }
    
    @Test
    void testBothPriceAndQuantityZero() {
        assertThrows(IllegalArgumentException.class, () -> {
            OrderModify.create(1L, "AAPL", 0L, 0L, 999L, 100L);
        });
    }
    
    @Test
    void testInvalidRequestId() {
        assertThrows(IllegalArgumentException.class, () -> {
            OrderModify.create(1L, "AAPL", 15000L, 200L, 999L, 0L);
        });
    }
    
    @Test
    void testNegativeTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderModify(1L, "AAPL", 15000L, 200L, -1L, 999L, 100L);
        });
    }
}
