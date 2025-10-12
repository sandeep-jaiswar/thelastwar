package com.thelastwar.oms;

/**
 * OrderCommand represents a command to be executed on an order.
 * Commands are persisted to a write-ahead log before being processed.
 * 
 * This is an immutable value object that captures the intent to perform an operation on an order.
 * 
 * @param commandId     Unique command identifier (monotonically increasing)
 * @param timestamp     Command timestamp in nanoseconds
 * @param commandType   Type of command (SUBMIT, MODIFY, CANCEL)
 * @param clientOrderId Client-assigned order identifier
 * @param internalOrderId Internal order identifier (null for new orders)
 * @param symbol        Trading symbol
 * @param side          Order side (1=Buy, 2=Sell)
 * @param orderType     Order type (1=Market, 2=Limit, etc.)
 * @param quantity      Order quantity
 * @param price         Order price
 * @param account       Account identifier
 */
public record OrderCommand(
    long commandId,
    long timestamp,
    CommandType commandType,
    String clientOrderId,
    Long internalOrderId,
    String symbol,
    byte side,
    byte orderType,
    long quantity,
    long price,
    long account
) {
    
    /**
     * Command types for order operations.
     */
    public enum CommandType {
        SUBMIT,   // Submit new order
        MODIFY,   // Modify existing order
        CANCEL    // Cancel existing order
    }
    
    /**
     * Compact constructor with validation.
     */
    public OrderCommand {
        if (commandId < 0) {
            throw new IllegalArgumentException("Command ID cannot be negative");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative");
        }
        if (commandType == null) {
            throw new IllegalArgumentException("Command type cannot be null");
        }
        if (clientOrderId == null || clientOrderId.isEmpty()) {
            throw new IllegalArgumentException("Client order ID cannot be null or empty");
        }
    }
    
    /**
     * Creates a SUBMIT command for a new order.
     */
    public static OrderCommand submit(long commandId, String clientOrderId, String symbol, 
                                     byte side, byte orderType, long quantity, long price, long account) {
        return new OrderCommand(
            commandId,
            System.nanoTime(),
            CommandType.SUBMIT,
            clientOrderId,
            null,
            symbol,
            side,
            orderType,
            quantity,
            price,
            account
        );
    }
    
    /**
     * Creates a CANCEL command for an existing order.
     */
    public static OrderCommand cancel(long commandId, String clientOrderId, long internalOrderId) {
        return new OrderCommand(
            commandId,
            System.nanoTime(),
            CommandType.CANCEL,
            clientOrderId,
            internalOrderId,
            null,
            (byte) 0,
            (byte) 0,
            0,
            0,
            0
        );
    }
    
    /**
     * Creates a MODIFY command for an existing order.
     */
    public static OrderCommand modify(long commandId, String clientOrderId, long internalOrderId,
                                     long newQuantity, long newPrice) {
        return new OrderCommand(
            commandId,
            System.nanoTime(),
            CommandType.MODIFY,
            clientOrderId,
            internalOrderId,
            null,
            (byte) 0,
            (byte) 0,
            newQuantity,
            newPrice,
            0
        );
    }
}
