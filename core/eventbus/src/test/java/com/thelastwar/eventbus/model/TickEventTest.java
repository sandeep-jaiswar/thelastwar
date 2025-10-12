package com.thelastwar.eventbus.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TickEvent event model.
 */
class TickEventTest {
    
    @Test
    void testCreateTick() {
        TickEvent tick = TickEvent.create(
            "AAPL", 14900L, 15000L, 14950L,
            1000L, 1000L, 1L, 1
        );
        
        assertNotNull(tick);
        assertEquals("AAPL", tick.symbol());
        assertEquals(14900L, tick.bidPrice());
        assertEquals(15000L, tick.askPrice());
        assertEquals(14950L, tick.lastPrice());
        assertEquals(1000L, tick.bidSize());
        assertEquals(1000L, tick.askSize());
        assertEquals(1L, tick.sequenceNum());
        assertEquals(1, tick.exchange());
        assertTrue(tick.timestamp() > 0);
    }
    
    @Test
    void testGetSpread() {
        TickEvent tick = TickEvent.create(
            "AAPL", 14900L, 15000L, 14950L,
            1000L, 1000L, 1L, 1
        );
        
        assertEquals(100L, tick.getSpread());
    }
    
    @Test
    void testGetSpreadWithMissingSide() {
        TickEvent tick1 = TickEvent.create(
            "AAPL", 0L, 15000L, 14950L,
            0L, 1000L, 1L, 1
        );
        assertEquals(0L, tick1.getSpread());
        
        TickEvent tick2 = TickEvent.create(
            "AAPL", 14900L, 0L, 14950L,
            1000L, 0L, 1L, 1
        );
        assertEquals(0L, tick2.getSpread());
    }
    
    @Test
    void testGetMidPrice() {
        TickEvent tick = TickEvent.create(
            "AAPL", 14900L, 15000L, 14950L,
            1000L, 1000L, 1L, 1
        );
        
        assertEquals(14950L, tick.getMidPrice());
    }
    
    @Test
    void testGetMidPriceWithMissingSide() {
        TickEvent tick1 = TickEvent.create(
            "AAPL", 0L, 15000L, 14950L,
            0L, 1000L, 1L, 1
        );
        assertEquals(0L, tick1.getMidPrice());
        
        TickEvent tick2 = TickEvent.create(
            "AAPL", 14900L, 0L, 14950L,
            1000L, 0L, 1L, 1
        );
        assertEquals(0L, tick2.getMidPrice());
    }
    
    @Test
    void testIsTwoSided() {
        TickEvent tick = TickEvent.create(
            "AAPL", 14900L, 15000L, 14950L,
            1000L, 1000L, 1L, 1
        );
        assertTrue(tick.isTwoSided());
        
        TickEvent tickOneSided1 = TickEvent.create(
            "AAPL", 0L, 15000L, 14950L,
            0L, 1000L, 1L, 1
        );
        assertFalse(tickOneSided1.isTwoSided());
        
        TickEvent tickOneSided2 = TickEvent.create(
            "AAPL", 14900L, 0L, 14950L,
            1000L, 0L, 1L, 1
        );
        assertFalse(tickOneSided2.isTwoSided());
    }
    
    @Test
    void testInvalidSymbol() {
        assertThrows(IllegalArgumentException.class, () -> {
            TickEvent.create(null, 14900L, 15000L, 14950L, 1000L, 1000L, 1L, 1);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            TickEvent.create("", 14900L, 15000L, 14950L, 1000L, 1000L, 1L, 1);
        });
    }
    
    @Test
    void testNegativePrices() {
        assertThrows(IllegalArgumentException.class, () -> {
            TickEvent.create("AAPL", -1L, 15000L, 14950L, 1000L, 1000L, 1L, 1);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            TickEvent.create("AAPL", 14900L, -1L, 14950L, 1000L, 1000L, 1L, 1);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            TickEvent.create("AAPL", 14900L, 15000L, -1L, 1000L, 1000L, 1L, 1);
        });
    }
    
    @Test
    void testNegativeSizes() {
        assertThrows(IllegalArgumentException.class, () -> {
            TickEvent.create("AAPL", 14900L, 15000L, 14950L, -1L, 1000L, 1L, 1);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            TickEvent.create("AAPL", 14900L, 15000L, 14950L, 1000L, -1L, 1L, 1);
        });
    }
    
    @Test
    void testNegativeTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TickEvent("AAPL", 14900L, 15000L, 14950L, 1000L, 1000L, -1L, 1L, 1);
        });
    }
    
    @Test
    void testNegativeSequenceNum() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TickEvent("AAPL", 14900L, 15000L, 14950L, 1000L, 1000L, 1L, -1L, 1);
        });
    }
    
    @Test
    void testCrossedMarket() {
        assertThrows(IllegalArgumentException.class, () -> {
            TickEvent.create("AAPL", 15000L, 14900L, 14950L, 1000L, 1000L, 1L, 1);
        });
    }
}
