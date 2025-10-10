package com.thelastwar.eventbus.model;

/**
 * Immutable Execution Event record representing an execution report from the matching engine.
 * Uses Java record for compact, efficient representation optimized for low-latency systems.
 * 
 * An ExecutionEvent is generated whenever an order state changes (new, partial fill, fill, cancel, reject).
 * This is the canonical event that flows through the system for order lifecycle tracking.
 * 
 * Performance characteristics:
 * - Compact memory layout via record
 * - All fields are primitives or immutable strings (no autoboxing in hot path)
 * - Binary serialization target: < 200 ns
 * - Suitable for deterministic replay
 * 
 * @param executionId      Unique execution identifier
 * @param orderId          Associated order identifier
 * @param symbol           Trading symbol/instrument
 * @param side             Execution side: 1=Buy, 2=Sell
 * @param executionType    Type: 0=New, 1=PartialFill, 2=Fill, 3=Cancelled, 4=Rejected, 5=Replaced
 * @param orderStatus      Current order status after this execution
 * @param lastQuantity     Quantity executed in this event (0 for non-fill events)
 * @param lastPrice        Price of this execution (0 for non-fill events)
 * @param cumulativeQty    Total quantity filled so far
 * @param leavesQuantity   Remaining unfilled quantity
 * @param timestamp        Execution timestamp in nanoseconds
 * @param account          Trading account identifier
 * @param exchange         Exchange identifier
 * @param rejectReason     Rejection reason code (0 if not rejected)
 */
public record ExecutionEvent(
        long executionId,
        long orderId,
        String symbol,
        byte side,
        byte executionType,
        byte orderStatus,
        long lastQuantity,
        long lastPrice,
        long cumulativeQty,
        long leavesQuantity,
        long timestamp,
        long account,
        int exchange,
        int rejectReason) {
    
    // Side constants (aligned with OrderEvent and TradeEvent)
    public static final byte SIDE_BUY = 1;
    public static final byte SIDE_SELL = 2;
    
    // Execution Type constants
    public static final byte EXEC_TYPE_NEW = 0;
    public static final byte EXEC_TYPE_PARTIAL_FILL = 1;
    public static final byte EXEC_TYPE_FILL = 2;
    public static final byte EXEC_TYPE_CANCELLED = 3;
    public static final byte EXEC_TYPE_REJECTED = 4;
    public static final byte EXEC_TYPE_REPLACED = 5;
    
    // Order Status constants (aligned with OrderEvent)
    public static final byte STATUS_NEW = 0;
    public static final byte STATUS_PARTIALLY_FILLED = 1;
    public static final byte STATUS_FILLED = 2;
    public static final byte STATUS_CANCELLED = 3;
    public static final byte STATUS_REJECTED = 4;
    
    /**
     * Compact constructor with validation.
     */
    public ExecutionEvent {
        if (executionId <= 0) {
            throw new IllegalArgumentException("Execution ID must be positive");
        }
        if (orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (side != SIDE_BUY && side != SIDE_SELL) {
            throw new IllegalArgumentException("Invalid side: " + side);
        }
        if (executionType < EXEC_TYPE_NEW || executionType > EXEC_TYPE_REPLACED) {
            throw new IllegalArgumentException("Invalid execution type: " + executionType);
        }
        if (lastQuantity < 0) {
            throw new IllegalArgumentException("Last quantity cannot be negative");
        }
        if (lastPrice < 0) {
            throw new IllegalArgumentException("Last price cannot be negative");
        }
        if (cumulativeQty < 0) {
            throw new IllegalArgumentException("Cumulative quantity cannot be negative");
        }
        if (leavesQuantity < 0) {
            throw new IllegalArgumentException("Leaves quantity cannot be negative");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative");
        }
    }
    
    /**
     * Factory method for creating a new order execution.
     * 
     * @param executionId Execution identifier
     * @param order       Source order event
     * @return new ExecutionEvent for order acceptance
     */
    public static ExecutionEvent newOrder(long executionId, OrderEvent order) {
        return new ExecutionEvent(
                executionId,
                order.orderId(),
                order.symbol(),
                order.side(),
                EXEC_TYPE_NEW,
                STATUS_NEW,
                0L, // no fill yet
                0L, // no fill yet
                0L, // cumulative
                order.quantity(), // all quantity remains
                System.nanoTime(),
                order.account(),
                order.exchange(),
                0 // no rejection
        );
    }
    
    /**
     * Factory method for creating a fill execution.
     * 
     * @param executionId    Execution identifier
     * @param order          Source order event
     * @param fillQuantity   Quantity filled in this execution
     * @param fillPrice      Fill price
     * @param cumulativeQty  Total cumulative filled quantity
     * @param leavesQuantity Remaining unfilled quantity
     * @return new ExecutionEvent for fill
     */
    public static ExecutionEvent fill(long executionId, OrderEvent order, long fillQuantity,
                                      long fillPrice, long cumulativeQty, long leavesQuantity) {
        byte execType = (leavesQuantity > 0) ? EXEC_TYPE_PARTIAL_FILL : EXEC_TYPE_FILL;
        byte status = (leavesQuantity > 0) ? STATUS_PARTIALLY_FILLED : STATUS_FILLED;
        
        return new ExecutionEvent(
                executionId,
                order.orderId(),
                order.symbol(),
                order.side(),
                execType,
                status,
                fillQuantity,
                fillPrice,
                cumulativeQty,
                leavesQuantity,
                System.nanoTime(),
                order.account(),
                order.exchange(),
                0
        );
    }
    
    /**
     * Factory method for creating a rejection execution.
     * 
     * @param executionId  Execution identifier
     * @param order        Source order event
     * @param rejectReason Rejection reason code
     * @return new ExecutionEvent for rejection
     */
    public static ExecutionEvent reject(long executionId, OrderEvent order, int rejectReason) {
        return new ExecutionEvent(
                executionId,
                order.orderId(),
                order.symbol(),
                order.side(),
                EXEC_TYPE_REJECTED,
                STATUS_REJECTED,
                0L,
                0L,
                0L,
                order.quantity(), // all quantity remains unfilled
                System.nanoTime(),
                order.account(),
                order.exchange(),
                rejectReason
        );
    }
    
    /**
     * Checks if this execution represents a fill (partial or complete).
     * 
     * @return true if this is a fill execution
     */
    public boolean isFill() {
        return executionType == EXEC_TYPE_PARTIAL_FILL || executionType == EXEC_TYPE_FILL;
    }
    
    /**
     * Checks if this execution represents a complete fill.
     * 
     * @return true if fully filled
     */
    public boolean isCompleteFill() {
        return executionType == EXEC_TYPE_FILL;
    }
    
    /**
     * Checks if this execution represents a partial fill.
     * 
     * @return true if partially filled
     */
    public boolean isPartialFill() {
        return executionType == EXEC_TYPE_PARTIAL_FILL;
    }
    
    /**
     * Checks if this execution represents a rejection.
     * 
     * @return true if rejected
     */
    public boolean isRejected() {
        return executionType == EXEC_TYPE_REJECTED;
    }
    
    /**
     * Checks if order is in a terminal state after this execution.
     * 
     * @return true if order is filled, cancelled, or rejected
     */
    public boolean isTerminal() {
        return orderStatus == STATUS_FILLED || 
               orderStatus == STATUS_CANCELLED || 
               orderStatus == STATUS_REJECTED;
    }
}
