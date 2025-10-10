package com.thelastwar.eventbus.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TradeEvent immutable record.
 */
class TradeEventTest {
    
    @Test
    void testTradeEventCreation() {
        TradeEvent trade = new TradeEvent(
                67890L,
                12345L,
                "AAPL",
                TradeEvent.SIDE_BUY,
                100L,
                15050L, // $150.50 in cents
                System.nanoTime(),
                999L,
                1,
                888L,
                50L // $0.50 fees
        );
        
        assertEquals(67890L, trade.tradeId());
        assertEquals(12345L, trade.orderId());
        assertEquals("AAPL", trade.symbol());
        assertEquals(TradeEvent.SIDE_BUY, trade.side());
        assertEquals(100L, trade.quantity());
        assertEquals(15050L, trade.price());
        assertEquals(999L, trade.account());
        assertEquals(1, trade.exchange());
        assertEquals(888L, trade.counterparty());
        assertEquals(50L, trade.fees());
    }
    
    @Test
    void testCreateFactoryMethod() {
        TradeEvent trade = TradeEvent.create(
                67890L,
                12345L,
                "AAPL",
                TradeEvent.SIDE_BUY,
                100L,
                15050L,
                999L,
                1,
                888L,
                50L
        );
        
        assertEquals(67890L, trade.tradeId());
        assertEquals(12345L, trade.orderId());
        assertTrue(trade.timestamp() > 0);
    }
    
    @Test
    void testFromOrderFactoryMethod() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        TradeEvent trade = TradeEvent.fromOrder(67890L, order, 15050L, 50L);
        
        assertEquals(67890L, trade.tradeId());
        assertEquals(12345L, trade.orderId());
        assertEquals("AAPL", trade.symbol());
        assertEquals(TradeEvent.SIDE_BUY, trade.side());
        assertEquals(100L, trade.quantity());
        assertEquals(15050L, trade.price());
        assertEquals(999L, trade.account());
        assertEquals(1, trade.exchange());
        assertEquals(50L, trade.fees());
        assertEquals(0L, trade.counterparty());
    }
    
    @Test
    void testInvalidTradeId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TradeEvent(
                    0L, // invalid
                    12345L,
                    "AAPL",
                    TradeEvent.SIDE_BUY,
                    100L,
                    15050L,
                    System.nanoTime(),
                    999L,
                    1,
                    888L,
                    50L
            );
        });
    }
    
    @Test
    void testInvalidOrderId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TradeEvent(
                    67890L,
                    0L, // invalid
                    "AAPL",
                    TradeEvent.SIDE_BUY,
                    100L,
                    15050L,
                    System.nanoTime(),
                    999L,
                    1,
                    888L,
                    50L
            );
        });
    }
    
    @Test
    void testInvalidSymbol() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TradeEvent(
                    67890L,
                    12345L,
                    null, // invalid
                    TradeEvent.SIDE_BUY,
                    100L,
                    15050L,
                    System.nanoTime(),
                    999L,
                    1,
                    888L,
                    50L
            );
        });
    }
    
    @Test
    void testInvalidSide() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TradeEvent(
                    67890L,
                    12345L,
                    "AAPL",
                    (byte) 99, // invalid
                    100L,
                    15050L,
                    System.nanoTime(),
                    999L,
                    1,
                    888L,
                    50L
            );
        });
    }
    
    @Test
    void testInvalidQuantity() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TradeEvent(
                    67890L,
                    12345L,
                    "AAPL",
                    TradeEvent.SIDE_BUY,
                    0L, // invalid
                    15050L,
                    System.nanoTime(),
                    999L,
                    1,
                    888L,
                    50L
            );
        });
    }
    
    @Test
    void testInvalidPrice() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TradeEvent(
                    67890L,
                    12345L,
                    "AAPL",
                    TradeEvent.SIDE_BUY,
                    100L,
                    -1L, // invalid
                    System.nanoTime(),
                    999L,
                    1,
                    888L,
                    50L
            );
        });
    }
    
    @Test
    void testInvalidFees() {
        assertThrows(IllegalArgumentException.class, () -> {
            new TradeEvent(
                    67890L,
                    12345L,
                    "AAPL",
                    TradeEvent.SIDE_BUY,
                    100L,
                    15050L,
                    System.nanoTime(),
                    999L,
                    1,
                    888L,
                    -1L // invalid
            );
        });
    }
    
    @Test
    void testIsBuy() {
        TradeEvent buyTrade = TradeEvent.create(
                67890L, 12345L, "AAPL", TradeEvent.SIDE_BUY,
                100L, 15050L, 999L, 1, 888L, 50L
        );
        
        assertTrue(buyTrade.isBuy());
        assertFalse(buyTrade.isSell());
    }
    
    @Test
    void testIsSell() {
        TradeEvent sellTrade = TradeEvent.create(
                67890L, 12345L, "AAPL", TradeEvent.SIDE_SELL,
                100L, 15050L, 999L, 1, 888L, 50L
        );
        
        assertTrue(sellTrade.isSell());
        assertFalse(sellTrade.isBuy());
    }
    
    @Test
    void testGetTradeValue() {
        TradeEvent trade = TradeEvent.create(
                67890L, 12345L, "AAPL", TradeEvent.SIDE_BUY,
                100L, 15050L, 999L, 1, 888L, 50L
        );
        
        // 100 shares * $150.50 = $15,050.00 (in cents: 1,505,000)
        assertEquals(1505000L, trade.getTradeValue());
    }
    
    @Test
    void testGetNetProceeds() {
        TradeEvent trade = TradeEvent.create(
                67890L, 12345L, "AAPL", TradeEvent.SIDE_BUY,
                100L, 15050L, 999L, 1, 888L, 50L
        );
        
        // Trade value: 1,505,000 - fees: 50 = 1,504,950
        assertEquals(1504950L, trade.getNetProceeds());
    }
    
    @Test
    void testZeroFees() {
        TradeEvent trade = TradeEvent.create(
                67890L, 12345L, "AAPL", TradeEvent.SIDE_BUY,
                100L, 15050L, 999L, 1, 888L, 0L
        );
        
        assertEquals(0L, trade.fees());
        assertEquals(trade.getTradeValue(), trade.getNetProceeds());
    }
    
    @Test
    void testSideConstants() {
        assertEquals(1, TradeEvent.SIDE_BUY);
        assertEquals(2, TradeEvent.SIDE_SELL);
    }
    
    @Test
    void testImmutability() {
        TradeEvent trade = TradeEvent.create(
                67890L, 12345L, "AAPL", TradeEvent.SIDE_BUY,
                100L, 15050L, 999L, 1, 888L, 50L
        );
        
        // Verify record is immutable by checking all accessors work
        assertEquals(67890L, trade.tradeId());
        assertEquals(12345L, trade.orderId());
        assertEquals("AAPL", trade.symbol());
    }
}
