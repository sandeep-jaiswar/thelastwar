package com.thelastwar.oms.persistence;

import java.sql.SQLException;
import java.util.Map;

/**
 * Interface for persistent order state storage.
 * 
 * Implementations provide durable storage for order state, supporting both
 * individual and batch operations.
 */
public interface OrderStateStore extends AutoCloseable {
    
    /**
     * Saves a single order state record.
     * 
     * @param record the order state to save
     * @throws SQLException if a database error occurs
     */
    void saveOrderState(OrderStateRecord record) throws SQLException;
    
    /**
     * Saves multiple order state records in a batch operation.
     * More efficient than multiple individual saves.
     * 
     * @param records the order states to save
     * @throws SQLException if a database error occurs
     */
    void saveOrderStateBatch(Iterable<OrderStateRecord> records) throws SQLException;
    
    /**
     * Retrieves order state by internal order ID.
     * 
     * @param internalOrderId the internal order ID
     * @return the order state record, or null if not found
     * @throws SQLException if a database error occurs
     */
    OrderStateRecord getOrderState(long internalOrderId) throws SQLException;
    
    /**
     * Retrieves all active orders (non-terminal states).
     * 
     * @return map of internal order ID to order state record
     * @throws SQLException if a database error occurs
     */
    Map<Long, OrderStateRecord> getAllActiveOrders() throws SQLException;
    
    /**
     * Counts the number of active orders.
     * 
     * @return count of active orders
     * @throws SQLException if a database error occurs
     */
    long countActiveOrders() throws SQLException;
    
    /**
     * Clears any in-memory caches.
     */
    void clearCache();
    
    /**
     * Closes the store and releases resources.
     */
    @Override
    void close();
}
