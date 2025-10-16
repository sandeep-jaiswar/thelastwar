package com.thelastwar.oms;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ParentOrder represents an order submitted by a client that may be split into
 * multiple child orders based on Smart Order Router (SOR) decisions.
 * 
 * A parent order tracks:
 * - Original order details (quantity, price, symbol, etc.)
 * - All child orders created from routing decisions
 * - Aggregate fill state across all children
 * - Correlation to client order ID
 * 
 * Thread-safety: This class uses CopyOnWriteArrayList for child orders to support
 * concurrent reads and writes. State updates should be synchronized externally.
 * 
 * @param parentOrderId    Internal order ID for the parent order
 * @param clientOrderId    Client-assigned order identifier
 * @param symbol           Trading symbol
 * @param side             Order side (1=Buy, 2=Sell)
 * @param orderType        Order type (1=Market, 2=Limit, etc.)
 * @param totalQuantity    Total quantity of the parent order
 * @param price            Order price
 * @param account          Account identifier
 * @param timestamp        Order creation timestamp
 */
public final class ParentOrder {
    
    private final InternalOrderId parentOrderId;
    private final ClientOrderId clientOrderId;
    private final String symbol;
    private final byte side;
    private final byte orderType;
    private final long totalQuantity;
    private final long price;
    private final long account;
    private final long timestamp;
    
    // Child order tracking - thread-safe for concurrent updates
    private final List<InternalOrderId> childOrderIds;
    
    // Aggregate fill tracking
    private volatile long filledQuantity;
    private volatile long remainingQuantity;
    
    /**
     * Creates a new ParentOrder.
     * 
     * @param parentOrderId    Internal order ID
     * @param clientOrderId    Client order ID
     * @param symbol           Trading symbol
     * @param side             Order side
     * @param orderType        Order type
     * @param totalQuantity    Total quantity
     * @param price            Order price
     * @param account          Account identifier
     * @param timestamp        Creation timestamp
     */
    public ParentOrder(
            InternalOrderId parentOrderId,
            ClientOrderId clientOrderId,
            String symbol,
            byte side,
            byte orderType,
            long totalQuantity,
            long price,
            long account,
            long timestamp) {
        
        // Validation
        Objects.requireNonNull(parentOrderId, "Parent order ID cannot be null");
        Objects.requireNonNull(clientOrderId, "Client order ID cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be empty");
        }
        if (totalQuantity <= 0) {
            throw new IllegalArgumentException("Total quantity must be positive");
        }
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        
        this.parentOrderId = parentOrderId;
        this.clientOrderId = clientOrderId;
        this.symbol = symbol;
        this.side = side;
        this.orderType = orderType;
        this.totalQuantity = totalQuantity;
        this.price = price;
        this.account = account;
        this.timestamp = timestamp;
        this.childOrderIds = new CopyOnWriteArrayList<>();
        this.filledQuantity = 0;
        this.remainingQuantity = totalQuantity;
    }
    
    /**
     * Adds a child order to this parent order.
     * 
     * @param childOrderId The child order ID to add
     * @throws IllegalArgumentException if child order ID is null
     */
    public void addChildOrder(InternalOrderId childOrderId) {
        Objects.requireNonNull(childOrderId, "Child order ID cannot be null");
        childOrderIds.add(childOrderId);
    }
    
    /**
     * Updates the fill state based on a child order fill.
     * 
     * @param fillQuantity The quantity filled
     * @throws IllegalArgumentException if fill quantity is invalid
     */
    public synchronized void updateFill(long fillQuantity) {
        if (fillQuantity <= 0) {
            throw new IllegalArgumentException("Fill quantity must be positive");
        }
        if (fillQuantity > remainingQuantity) {
            throw new IllegalArgumentException(
                "Fill quantity (" + fillQuantity + ") exceeds remaining quantity (" + remainingQuantity + ")"
            );
        }
        
        filledQuantity += fillQuantity;
        remainingQuantity -= fillQuantity;
    }
    
    /**
     * Checks if the parent order is fully filled.
     * 
     * @return true if all quantity is filled
     */
    public boolean isFullyFilled() {
        return remainingQuantity == 0;
    }
    
    /**
     * Checks if the parent order is partially filled.
     * 
     * @return true if some but not all quantity is filled
     */
    public boolean isPartiallyFilled() {
        return filledQuantity > 0 && remainingQuantity > 0;
    }
    
    /**
     * Gets the number of child orders created from this parent.
     * 
     * @return child order count
     */
    public int getChildOrderCount() {
        return childOrderIds.size();
    }
    
    /**
     * Gets an immutable view of child order IDs.
     * 
     * @return list of child order IDs
     */
    public List<InternalOrderId> getChildOrderIds() {
        return Collections.unmodifiableList(childOrderIds);
    }
    
    // Getters
    
    public InternalOrderId getParentOrderId() {
        return parentOrderId;
    }
    
    public ClientOrderId getClientOrderId() {
        return clientOrderId;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public byte getSide() {
        return side;
    }
    
    public byte getOrderType() {
        return orderType;
    }
    
    public long getTotalQuantity() {
        return totalQuantity;
    }
    
    public long getPrice() {
        return price;
    }
    
    public long getAccount() {
        return account;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public long getFilledQuantity() {
        return filledQuantity;
    }
    
    public long getRemainingQuantity() {
        return remainingQuantity;
    }
    
    /**
     * Gets the fill percentage (0-100).
     * 
     * @return fill percentage
     */
    public double getFillPercentage() {
        return (filledQuantity * 100.0) / totalQuantity;
    }
    
    @Override
    public String toString() {
        return String.format(
            "ParentOrder[id=%s, clientId=%s, symbol=%s, side=%d, qty=%d, filled=%d, remaining=%d, children=%d]",
            parentOrderId, clientOrderId, symbol, side, totalQuantity, filledQuantity, remainingQuantity, childOrderIds.size()
        );
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParentOrder that = (ParentOrder) o;
        return parentOrderId.equals(that.parentOrderId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(parentOrderId);
    }
}
