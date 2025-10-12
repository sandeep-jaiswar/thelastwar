package com.thelastwar.eventbus.model;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventSerializer.
 */
class EventSerializerTest {
    
    @Test
    void testSerializeDeserializeOrderEvent() {
        OrderEvent original = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getOrderEventSize());
        EventSerializer.serializeOrder(original, buffer);
        
        buffer.flip();
        OrderEvent deserialized = EventSerializer.deserializeOrder(buffer);
        
        assertEquals(original.orderId(), deserialized.orderId());
        assertEquals(original.symbol(), deserialized.symbol());
        assertEquals(original.side(), deserialized.side());
        assertEquals(original.orderType(), deserialized.orderType());
        assertEquals(original.quantity(), deserialized.quantity());
        assertEquals(original.price(), deserialized.price());
        assertEquals(original.timestamp(), deserialized.timestamp());
        assertEquals(original.status(), deserialized.status());
        assertEquals(original.account(), deserialized.account());
        assertEquals(original.exchange(), deserialized.exchange());
    }
    
    @Test
    void testSerializeDeserializeTradeEvent() {
        TradeEvent original = TradeEvent.create(
                67890L, 12345L, "AAPL", TradeEvent.SIDE_BUY,
                100L, 15050L, 999L, 1, 888L, 50L
        );
        
        ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getTradeEventSize());
        EventSerializer.serializeTrade(original, buffer);
        
        buffer.flip();
        TradeEvent deserialized = EventSerializer.deserializeTrade(buffer);
        
        assertEquals(original.tradeId(), deserialized.tradeId());
        assertEquals(original.orderId(), deserialized.orderId());
        assertEquals(original.symbol(), deserialized.symbol());
        assertEquals(original.side(), deserialized.side());
        assertEquals(original.quantity(), deserialized.quantity());
        assertEquals(original.price(), deserialized.price());
        assertEquals(original.timestamp(), deserialized.timestamp());
        assertEquals(original.account(), deserialized.account());
        assertEquals(original.exchange(), deserialized.exchange());
        assertEquals(original.counterparty(), deserialized.counterparty());
        assertEquals(original.fees(), deserialized.fees());
    }
    
    @Test
    void testSerializeDeserializeExecutionEvent() {
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        ExecutionEvent original = ExecutionEvent.fill(11111L, order, 50L, 15050L, 50L, 50L);
        
        ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getExecutionEventSize());
        EventSerializer.serializeExecution(original, buffer);
        
        buffer.flip();
        ExecutionEvent deserialized = EventSerializer.deserializeExecution(buffer);
        
        assertEquals(original.executionId(), deserialized.executionId());
        assertEquals(original.orderId(), deserialized.orderId());
        assertEquals(original.symbol(), deserialized.symbol());
        assertEquals(original.side(), deserialized.side());
        assertEquals(original.executionType(), deserialized.executionType());
        assertEquals(original.orderStatus(), deserialized.orderStatus());
        assertEquals(original.lastQuantity(), deserialized.lastQuantity());
        assertEquals(original.lastPrice(), deserialized.lastPrice());
        assertEquals(original.cumulativeQty(), deserialized.cumulativeQty());
        assertEquals(original.leavesQuantity(), deserialized.leavesQuantity());
        assertEquals(original.timestamp(), deserialized.timestamp());
        assertEquals(original.account(), deserialized.account());
        assertEquals(original.exchange(), deserialized.exchange());
        assertEquals(original.rejectReason(), deserialized.rejectReason());
    }
    
    @Test
    void testOrderEventBufferSize() {
        int size = EventSerializer.getOrderEventSize();
        assertTrue(size > 0);
        assertTrue(size < 100); // Should be compact
    }
    
    @Test
    void testTradeEventBufferSize() {
        int size = EventSerializer.getTradeEventSize();
        assertTrue(size > 0);
        assertTrue(size < 120); // Should be compact
    }
    
    @Test
    void testExecutionEventBufferSize() {
        int size = EventSerializer.getExecutionEventSize();
        assertTrue(size > 0);
        assertTrue(size < 150); // Should be compact
    }
    
    @Test
    void testSymbolTruncation() {
        // Test with a very long symbol (should be truncated to 16 chars)
        OrderEvent original = OrderEvent.newOrder(
                12345L, "VERYLONGSYMBOLNAME123456", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getOrderEventSize());
        EventSerializer.serializeOrder(original, buffer);
        
        buffer.flip();
        OrderEvent deserialized = EventSerializer.deserializeOrder(buffer);
        
        // Symbol should be truncated to 16 characters
        assertEquals("VERYLONGSYMBOLNA", deserialized.symbol());
    }
    
    @Test
    void testShortSymbol() {
        // Test with a short symbol (should be padded with zeros)
        OrderEvent original = OrderEvent.newOrder(
                12345L, "IBM", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getOrderEventSize());
        EventSerializer.serializeOrder(original, buffer);
        
        buffer.flip();
        OrderEvent deserialized = EventSerializer.deserializeOrder(buffer);
        
        assertEquals("IBM", deserialized.symbol());
    }
    
    @Test
    void testMultipleSerializationsInSameBuffer() {
        OrderEvent order1 = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        OrderEvent order2 = OrderEvent.newOrder(
                67890L, "MSFT", OrderEvent.SIDE_SELL, OrderEvent.TYPE_MARKET,
                200L, 0L, 888L, 2
        );
        
        ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getOrderEventSize() * 2);
        
        // Serialize two orders
        EventSerializer.serializeOrder(order1, buffer);
        EventSerializer.serializeOrder(order2, buffer);
        
        // Deserialize both
        buffer.flip();
        OrderEvent deserialized1 = EventSerializer.deserializeOrder(buffer);
        OrderEvent deserialized2 = EventSerializer.deserializeOrder(buffer);
        
        assertEquals(order1.orderId(), deserialized1.orderId());
        assertEquals(order1.symbol(), deserialized1.symbol());
        
        assertEquals(order2.orderId(), deserialized2.orderId());
        assertEquals(order2.symbol(), deserialized2.symbol());
    }
    
    @Test
    void testRoundTripWithAllFields() {
        // Test OrderEvent with all fields
        OrderEvent order = new OrderEvent(
                Long.MAX_VALUE,
                "TESTSYMBOL",
                OrderEvent.SIDE_SELL,
                OrderEvent.TYPE_STOP_LIMIT,
                999999999L,
                123456789L,
                System.nanoTime(),
                OrderEvent.STATUS_PARTIALLY_FILLED,
                888888888L,
                9999,
                OrderEvent.TIF_FOK
        );
        
        ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getOrderEventSize());
        EventSerializer.serializeOrder(order, buffer);
        buffer.flip();
        OrderEvent deserialized = EventSerializer.deserializeOrder(buffer);
        
        assertEquals(order.orderId(), deserialized.orderId());
        assertEquals(order.side(), deserialized.side());
        assertEquals(order.orderType(), deserialized.orderType());
        assertEquals(order.quantity(), deserialized.quantity());
        assertEquals(order.price(), deserialized.price());
        assertEquals(order.status(), deserialized.status());
        assertEquals(order.account(), deserialized.account());
        assertEquals(order.exchange(), deserialized.exchange());
    }
    
    @Test
    void testZeroValues() {
        // Test with zero values where allowed
        TradeEvent trade = new TradeEvent(
                1L,
                1L,
                "A",
                TradeEvent.SIDE_BUY,
                1L,
                0L, // zero price is allowed
                0L, // zero timestamp is allowed
                0L, // zero account
                0,  // zero exchange
                0L, // zero counterparty
                0L  // zero fees
        );
        
        ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getTradeEventSize());
        EventSerializer.serializeTrade(trade, buffer);
        buffer.flip();
        TradeEvent deserialized = EventSerializer.deserializeTrade(buffer);
        
        assertEquals(0L, deserialized.price());
        assertEquals(0L, deserialized.timestamp());
        assertEquals(0L, deserialized.account());
        assertEquals(0, deserialized.exchange());
        assertEquals(0L, deserialized.counterparty());
        assertEquals(0L, deserialized.fees());
    }
}
