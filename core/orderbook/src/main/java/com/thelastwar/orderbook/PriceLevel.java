package com.thelastwar.orderbook;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Represents a single price level in the order book.
 * Maintains a FIFO queue of orders at this price for price-time priority.
 * 
 * Performance characteristics:
 * - Add order: O(1)
 * - Remove order: O(n) where n is the number of orders at this price level
 * - Peek: O(1)
 * 
 * Uses ArrayDeque for optimal cache locality and minimal memory overhead.
 */
public class PriceLevel {
    private final long price;
    private final Deque<Order> orders;
    private long totalQuantity;
    
    /**
     * Creates a new price level.
     * 
     * @param price Price level
     */
    public PriceLevel(long price) {
        this.price = price;
        this.orders = new ArrayDeque<>();
        this.totalQuantity = 0;
    }
    
    /**
     * Adds an order to the end of the queue (price-time priority).
     * 
     * @param order Order to add
     */
    public void addOrder(Order order) {
        if (order.price() != price) {
            throw new IllegalArgumentException("Order price does not match level price");
        }
        orders.addLast(order);
        totalQuantity += order.quantity();
    }
    
    /**
     * Removes a specific order from the queue.
     * 
     * @param orderId Order ID to remove
     * @return true if order was found and removed
     */
    public boolean removeOrder(long orderId) {
        for (var iterator = orders.iterator(); iterator.hasNext(); ) {
            Order order = iterator.next();
            if (order.orderId() == orderId) {
                iterator.remove();
                totalQuantity -= order.quantity();
                return true;
            }
        }
        return false;
    }
    
    /**
     * Updates an order quantity at this price level.
     * 
     * @param orderId Order ID to update
     * @param newQuantity New quantity
     * @return true if order was found and updated
     */
    public boolean updateOrder(long orderId, long newQuantity) {
        for (var iterator = orders.iterator(); iterator.hasNext(); ) {
            Order order = iterator.next();
            if (order.orderId() == orderId) {
                iterator.remove();
                totalQuantity -= order.quantity();
                Order updatedOrder = order.withQuantity(newQuantity);
                orders.addLast(updatedOrder);
                totalQuantity += newQuantity;
                return true;
            }
        }
        return false;
    }
    
    /**
     * Peeks at the first order in the queue (oldest order).
     * 
     * @return First order or null if empty
     */
    public Order peek() {
        return orders.peekFirst();
    }
    
    /**
     * Removes and returns the first order in the queue.
     * 
     * @return First order or null if empty
     */
    public Order poll() {
        Order order = orders.pollFirst();
        if (order != null) {
            totalQuantity -= order.quantity();
        }
        return order;
    }
    
    /**
     * Checks if this price level is empty.
     * 
     * @return true if no orders at this level
     */
    public boolean isEmpty() {
        return orders.isEmpty();
    }
    
    /**
     * Gets the total quantity at this price level.
     * 
     * @return Total quantity
     */
    public long getTotalQuantity() {
        return totalQuantity;
    }
    
    /**
     * Gets the price of this level.
     * 
     * @return Price
     */
    public long getPrice() {
        return price;
    }
    
    /**
     * Gets the number of orders at this level.
     * 
     * @return Number of orders
     */
    public int getOrderCount() {
        return orders.size();
    }
}
