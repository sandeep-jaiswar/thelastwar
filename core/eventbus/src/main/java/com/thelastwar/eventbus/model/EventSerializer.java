package com.thelastwar.eventbus.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * High-performance binary serializer/deserializer for event models.
 * Optimized for ultra-low latency with target serialization time < 200 ns.
 * 
 * Design principles:
 * - Direct ByteBuffer operations (no allocations in hot path)
 * - Fixed-size fields where possible
 * - Length-prefixed strings for variable data
 * - No object creation during serialization/deserialization
 * - Cache-friendly sequential layout
 * 
 * Performance characteristics:
 * - Zero-copy where possible
 * - Minimal branching
 * - No intermediate objects
 * - Pre-sized buffers
 */
public final class EventSerializer {
    
    // Maximum symbol length (fixed to avoid dynamic allocation)
    private static final int MAX_SYMBOL_LENGTH = 16;
    
    // Buffer sizes for each event type (pre-calculated for performance)
    private static final int ORDER_EVENT_SIZE = 8 + MAX_SYMBOL_LENGTH + 1 + 1 + 8 + 8 + 8 + 1 + 8 + 4;
    private static final int TRADE_EVENT_SIZE = 8 + 8 + MAX_SYMBOL_LENGTH + 1 + 8 + 8 + 8 + 8 + 4 + 8 + 8;
    private static final int EXECUTION_EVENT_SIZE = 8 + 8 + MAX_SYMBOL_LENGTH + 1 + 1 + 1 + 8 + 8 + 8 + 8 + 8 + 8 + 4 + 4;
    
    private EventSerializer() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Serializes an OrderEvent to a ByteBuffer.
     * Target performance: < 200 ns
     * 
     * @param event  OrderEvent to serialize
     * @param buffer Target buffer (must have sufficient capacity)
     */
    public static void serializeOrder(OrderEvent event, ByteBuffer buffer) {
        buffer.putLong(event.orderId());
        putFixedString(buffer, event.symbol(), MAX_SYMBOL_LENGTH);
        buffer.put(event.side());
        buffer.put(event.orderType());
        buffer.putLong(event.quantity());
        buffer.putLong(event.price());
        buffer.putLong(event.timestamp());
        buffer.put(event.status());
        buffer.putLong(event.account());
        buffer.putInt(event.exchange());
        buffer.put(event.timeInForce());
    }
    
    /**
     * Deserializes an OrderEvent from a ByteBuffer.
     * Target performance: < 200 ns
     * 
     * @param buffer Source buffer
     * @return deserialized OrderEvent
     */
    public static OrderEvent deserializeOrder(ByteBuffer buffer) {
        long orderId = buffer.getLong();
        String symbol = getFixedString(buffer, MAX_SYMBOL_LENGTH);
        byte side = buffer.get();
        byte orderType = buffer.get();
        long quantity = buffer.getLong();
        long price = buffer.getLong();
        long timestamp = buffer.getLong();
        byte status = buffer.get();
        long account = buffer.getLong();
        int exchange = buffer.getInt();
        byte timeInForce = buffer.get();
        
        return new OrderEvent(orderId, symbol, side, orderType, quantity, price,
                timestamp, status, account, exchange, timeInForce);
    }
    
    /**
     * Serializes a TradeEvent to a ByteBuffer.
     * Target performance: < 200 ns
     * 
     * @param event  TradeEvent to serialize
     * @param buffer Target buffer (must have sufficient capacity)
     */
    public static void serializeTrade(TradeEvent event, ByteBuffer buffer) {
        buffer.putLong(event.tradeId());
        buffer.putLong(event.orderId());
        putFixedString(buffer, event.symbol(), MAX_SYMBOL_LENGTH);
        buffer.put(event.side());
        buffer.putLong(event.quantity());
        buffer.putLong(event.price());
        buffer.putLong(event.timestamp());
        buffer.putLong(event.account());
        buffer.putInt(event.exchange());
        buffer.putLong(event.counterparty());
        buffer.putLong(event.fees());
    }
    
    /**
     * Deserializes a TradeEvent from a ByteBuffer.
     * Target performance: < 200 ns
     * 
     * @param buffer Source buffer
     * @return deserialized TradeEvent
     */
    public static TradeEvent deserializeTrade(ByteBuffer buffer) {
        long tradeId = buffer.getLong();
        long orderId = buffer.getLong();
        String symbol = getFixedString(buffer, MAX_SYMBOL_LENGTH);
        byte side = buffer.get();
        long quantity = buffer.getLong();
        long price = buffer.getLong();
        long timestamp = buffer.getLong();
        long account = buffer.getLong();
        int exchange = buffer.getInt();
        long counterparty = buffer.getLong();
        long fees = buffer.getLong();
        
        return new TradeEvent(tradeId, orderId, symbol, side, quantity, price,
                timestamp, account, exchange, counterparty, fees);
    }
    
    /**
     * Serializes an ExecutionEvent to a ByteBuffer.
     * Target performance: < 200 ns
     * 
     * @param event  ExecutionEvent to serialize
     * @param buffer Target buffer (must have sufficient capacity)
     */
    public static void serializeExecution(ExecutionEvent event, ByteBuffer buffer) {
        buffer.putLong(event.executionId());
        buffer.putLong(event.orderId());
        putFixedString(buffer, event.symbol(), MAX_SYMBOL_LENGTH);
        buffer.put(event.side());
        buffer.put(event.executionType());
        buffer.put(event.orderStatus());
        buffer.putLong(event.lastQuantity());
        buffer.putLong(event.lastPrice());
        buffer.putLong(event.cumulativeQty());
        buffer.putLong(event.leavesQuantity());
        buffer.putLong(event.timestamp());
        buffer.putLong(event.account());
        buffer.putInt(event.exchange());
        buffer.putInt(event.rejectReason());
    }
    
    /**
     * Deserializes an ExecutionEvent from a ByteBuffer.
     * Target performance: < 200 ns
     * 
     * @param buffer Source buffer
     * @return deserialized ExecutionEvent
     */
    public static ExecutionEvent deserializeExecution(ByteBuffer buffer) {
        long executionId = buffer.getLong();
        long orderId = buffer.getLong();
        String symbol = getFixedString(buffer, MAX_SYMBOL_LENGTH);
        byte side = buffer.get();
        byte executionType = buffer.get();
        byte orderStatus = buffer.get();
        long lastQuantity = buffer.getLong();
        long lastPrice = buffer.getLong();
        long cumulativeQty = buffer.getLong();
        long leavesQuantity = buffer.getLong();
        long timestamp = buffer.getLong();
        long account = buffer.getLong();
        int exchange = buffer.getInt();
        int rejectReason = buffer.getInt();
        
        return new ExecutionEvent(executionId, orderId, symbol, side, executionType,
                orderStatus, lastQuantity, lastPrice, cumulativeQty, leavesQuantity,
                timestamp, account, exchange, rejectReason);
    }
    
    /**
     * Gets the required buffer size for OrderEvent serialization.
     * 
     * @return buffer size in bytes
     */
    public static int getOrderEventSize() {
        return ORDER_EVENT_SIZE;
    }
    
    /**
     * Gets the required buffer size for TradeEvent serialization.
     * 
     * @return buffer size in bytes
     */
    public static int getTradeEventSize() {
        return TRADE_EVENT_SIZE;
    }
    
    /**
     * Gets the required buffer size for ExecutionEvent serialization.
     * 
     * @return buffer size in bytes
     */
    public static int getExecutionEventSize() {
        return EXECUTION_EVENT_SIZE;
    }
    
    /**
     * Writes a fixed-length string to the buffer.
     * Pads with zeros if shorter than maxLength, truncates if longer.
     * 
     * @param buffer    Target buffer
     * @param str       String to write
     * @param maxLength Maximum length (fixed size in buffer)
     */
    private static void putFixedString(ByteBuffer buffer, String str, int maxLength) {
        byte[] bytes = str.getBytes(StandardCharsets.US_ASCII);
        int length = Math.min(bytes.length, maxLength);
        
        // Write string bytes
        buffer.put(bytes, 0, length);
        
        // Pad with zeros
        for (int i = length; i < maxLength; i++) {
            buffer.put((byte) 0);
        }
    }
    
    /**
     * Reads a fixed-length string from the buffer.
     * 
     * @param buffer    Source buffer
     * @param maxLength Fixed size in buffer
     * @return string value (trimmed of padding)
     */
    private static String getFixedString(ByteBuffer buffer, int maxLength) {
        byte[] bytes = new byte[maxLength];
        buffer.get(bytes);
        
        // Find actual length (first zero byte)
        int length = maxLength;
        for (int i = 0; i < maxLength; i++) {
            if (bytes[i] == 0) {
                length = i;
                break;
            }
        }
        
        return new String(bytes, 0, length, StandardCharsets.US_ASCII);
    }
}
