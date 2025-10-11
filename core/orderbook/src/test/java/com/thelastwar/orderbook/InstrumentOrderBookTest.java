package com.thelastwar.orderbook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for instrument-specific order books.
 */
class InstrumentOrderBookTest {
    
    @Test
    void testEquityOrderBook() {
        EquityOrderBook book = new EquityOrderBook("AAPL");
        
        assertEquals("AAPL", book.getSymbol());
        assertEquals(InstrumentType.EQUITY, book.getInstrumentType());
        
        // Test basic operations
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime());
        assertTrue(book.addOrder(order));
        assertEquals(1, book.getOrderCount());
        assertEquals(15000L, book.getBestBid());
    }
    
    @Test
    void testBondOrderBook() {
        BondOrderBook book = new BondOrderBook("US10Y");
        
        assertEquals("US10Y", book.getSymbol());
        assertEquals(InstrumentType.BOND, book.getInstrumentType());
        
        // Test basic operations
        Order order = new Order(1L, "US10Y", Order.SIDE_SELL, 9500L, 1000000L, System.nanoTime());
        assertTrue(book.addOrder(order));
        assertEquals(1, book.getOrderCount());
        assertEquals(9500L, book.getBestAsk());
    }
    
    @Test
    void testDerivativeOrderBook() {
        DerivativeOrderBook book = new DerivativeOrderBook("ES_MAR25");
        
        assertEquals("ES_MAR25", book.getSymbol());
        assertEquals(InstrumentType.DERIVATIVE, book.getInstrumentType());
        
        // Test basic operations
        Order order = new Order(1L, "ES_MAR25", Order.SIDE_BUY, 450000L, 10L, System.nanoTime());
        assertTrue(book.addOrder(order));
        assertEquals(1, book.getOrderCount());
        assertEquals(450000L, book.getBestBid());
    }
    
    @Test
    void testMultipleInstrumentTypes() {
        // Test that different instrument types can coexist
        EquityOrderBook equity = new EquityOrderBook("AAPL");
        BondOrderBook bond = new BondOrderBook("US10Y");
        DerivativeOrderBook derivative = new DerivativeOrderBook("ES_MAR25");
        
        equity.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime()));
        bond.addOrder(new Order(2L, "US10Y", Order.SIDE_BUY, 9500L, 1000000L, System.nanoTime()));
        derivative.addOrder(new Order(3L, "ES_MAR25", Order.SIDE_BUY, 450000L, 10L, System.nanoTime()));
        
        assertEquals(1, equity.getOrderCount());
        assertEquals(1, bond.getOrderCount());
        assertEquals(1, derivative.getOrderCount());
        
        assertEquals(InstrumentType.EQUITY, equity.getInstrumentType());
        assertEquals(InstrumentType.BOND, bond.getInstrumentType());
        assertEquals(InstrumentType.DERIVATIVE, derivative.getInstrumentType());
    }
}
