package com.thelastwar.restgateway.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderRequest DTO.
 */
class OrderRequestTest {
    
    @Test
    void testValidOrderRequest() {
        OrderRequest request = new OrderRequest("CLIENT-001", "AAPL", "BUY", "LIMIT", 100, 15000, 999);
        
        assertDoesNotThrow(request::validate);
        assertEquals((byte) 1, request.getSideByte());
        assertEquals((byte) 2, request.getOrderTypeByte());
    }
    
    @Test
    void testInvalidClientOrderId() {
        OrderRequest request = new OrderRequest("", "AAPL", "BUY", "LIMIT", 100, 15000, 999);
        assertThrows(IllegalArgumentException.class, request::validate);
    }
    
    @Test
    void testInvalidClientOrderIdFormat() {
        OrderRequest request = new OrderRequest("ORDER@123", "AAPL", "BUY", "LIMIT", 100, 15000, 999);
        assertThrows(IllegalArgumentException.class, request::validate);
    }
    
    @Test
    void testInvalidSymbol() {
        OrderRequest request = new OrderRequest("CLIENT-001", "", "BUY", "LIMIT", 100, 15000, 999);
        assertThrows(IllegalArgumentException.class, request::validate);
    }
    
    @Test
    void testInvalidSide() {
        OrderRequest request = new OrderRequest("CLIENT-001", "AAPL", "INVALID", "LIMIT", 100, 15000, 999);
        assertThrows(IllegalArgumentException.class, request::validate);
    }
    
    @Test
    void testInvalidQuantity() {
        OrderRequest request = new OrderRequest("CLIENT-001", "AAPL", "BUY", "LIMIT", 0, 15000, 999);
        assertThrows(IllegalArgumentException.class, request::validate);
    }
    
    @Test
    void testMarketOrderWithZeroPrice() {
        OrderRequest request = new OrderRequest("CLIENT-001", "AAPL", "BUY", "MARKET", 100, 0, 999);
        assertDoesNotThrow(request::validate);
    }
    
    @Test
    void testLimitOrderWithZeroPrice() {
        OrderRequest request = new OrderRequest("CLIENT-001", "AAPL", "BUY", "LIMIT", 100, 0, 999);
        assertThrows(IllegalArgumentException.class, request::validate);
    }
    
    @Test
    void testSellOrder() {
        OrderRequest request = new OrderRequest("CLIENT-001", "AAPL", "SELL", "LIMIT", 100, 15000, 999);
        assertEquals((byte) 2, request.getSideByte());
    }
    
    @Test
    void testOrderTypes() {
        OrderRequest market = new OrderRequest("CLIENT-001", "AAPL", "BUY", "MARKET", 100, 0, 999);
        assertEquals((byte) 1, market.getOrderTypeByte());
        
        OrderRequest limit = new OrderRequest("CLIENT-001", "AAPL", "BUY", "LIMIT", 100, 15000, 999);
        assertEquals((byte) 2, limit.getOrderTypeByte());
        
        OrderRequest stop = new OrderRequest("CLIENT-001", "AAPL", "BUY", "STOP", 100, 15000, 999);
        assertEquals((byte) 3, stop.getOrderTypeByte());
        
        OrderRequest stopLimit = new OrderRequest("CLIENT-001", "AAPL", "BUY", "STOP_LIMIT", 100, 15000, 999);
        assertEquals((byte) 4, stopLimit.getOrderTypeByte());
    }
}
