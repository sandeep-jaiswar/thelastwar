package com.thelastwar.orderbook;

import java.io.Closeable;

/**
 * Interface for persisting and retrieving order state.
 * Implementations should provide thread-safe access for single writer, multiple readers.
 * 
 * Performance requirements:
 * - Read/write latency < 2 µs
 * - Crash-safe persistence (memory-mapped files)
 * 
 * Thread Safety:
 * - Single writer, multiple readers
 * - Write operations are mutually exclusive
 * - Read operations can happen concurrently with other reads
 */
public interface OrderStateStore extends Closeable {
    
    /**
     * Stores or updates an order in the state store.
     * Thread-safe for single writer.
     * 
     * @param order Order to store
     * @throws IllegalStateException if store is closed
     */
    void put(Order order);
    
    /**
     * Retrieves an order from the state store.
     * Thread-safe for multiple concurrent readers.
     * 
     * @param orderId Order ID
     * @return Order or null if not found
     * @throws IllegalStateException if store is closed
     */
    Order get(long orderId);
    
    /**
     * Removes an order from the state store.
     * Thread-safe for single writer.
     * 
     * @param orderId Order ID to remove
     * @return Removed order or null if not found
     * @throws IllegalStateException if store is closed
     */
    Order remove(long orderId);
    
    /**
     * Checks if an order exists in the store.
     * Thread-safe for multiple concurrent readers.
     * 
     * @param orderId Order ID
     * @return true if order exists
     * @throws IllegalStateException if store is closed
     */
    boolean containsKey(long orderId);
    
    /**
     * Gets the number of orders in the store.
     * Thread-safe for multiple concurrent readers.
     * 
     * @return Number of orders
     * @throws IllegalStateException if store is closed
     */
    long size();
    
    /**
     * Clears all orders from the store.
     * Thread-safe for single writer.
     * 
     * @throws IllegalStateException if store is closed
     */
    void clear();
    
    /**
     * Forces any pending writes to be persisted to disk.
     * Ensures crash-safety by flushing memory-mapped buffers.
     * 
     * @throws IllegalStateException if store is closed
     */
    void flush();
    
    /**
     * Checks if the store is closed.
     * 
     * @return true if closed
     */
    boolean isClosed();
}
