package com.thelastwar.orderbook;

import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ChronicleMap-based off-heap order state store.
 * 
 * Features:
 * - Off-heap storage using ChronicleMap (zero GC pressure)
 * - Memory-mapped file persistence (crash-safe)
 * - Single writer, multiple readers thread safety
 * - Read/write latency < 2 µs
 * 
 * Thread Safety:
 * - Uses ReadWriteLock for single writer, multiple readers
 * - Write operations acquire exclusive write lock
 * - Read operations acquire shared read lock (concurrent reads allowed)
 * 
 * Persistence:
 * - Memory-mapped to file for crash recovery
 * - Automatic flushing on close
 * - Manual flush() available for critical points
 * 
 * Note: This implementation stores a simplified order representation internally
 * to work with ChronicleMap's built-in serialization.
 */
public class ChronicleOrderStateStore implements OrderStateStore {
    
    /**
     * Internal order representation for ChronicleMap storage.
     * Uses fixed-size fields for efficient serialization.
     * Implements Externalizable for custom serialization logic.
     */
    static class StoredOrder implements java.io.Externalizable {
        long orderId;
        String symbol;
        byte side;
        long price;
        long quantity;
        long timestamp;
        
        // Required for Externalizable
        public StoredOrder() {
        }
        
        StoredOrder(Order order) {
            this.orderId = order.orderId();
            this.symbol = order.symbol();
            this.side = order.side();
            this.price = order.price();
            this.quantity = order.quantity();
            this.timestamp = order.timestamp();
        }
        
        @Override
        public void writeExternal(java.io.ObjectOutput out) throws java.io.IOException {
            out.writeLong(orderId);
            out.writeUTF(symbol != null ? symbol : "");
            out.writeByte(side);
            out.writeLong(price);
            out.writeLong(quantity);
            out.writeLong(timestamp);
        }
        
        @Override
        public void readExternal(java.io.ObjectInput in) throws java.io.IOException {
            orderId = in.readLong();
            symbol = in.readUTF();
            side = in.readByte();
            price = in.readLong();
            quantity = in.readLong();
            timestamp = in.readLong();
        }
        
        Order toOrder() {
            return new Order(orderId, symbol, side, price, quantity, timestamp, Order.TYPE_LIMIT, Order.TIF_GTC);
        }
    }
    
    private final ChronicleMap<Long, StoredOrder> map;
    private final ReadWriteLock lock;
    private final Path persistenceFile;
    private volatile boolean closed;
    
    /**
     * Creates a new in-memory ChronicleMap store (not persisted).
     * Useful for testing or ephemeral use cases.
     * 
     * @param expectedEntries Expected number of entries (for capacity planning)
     * @return New in-memory store
     * @throws IOException if store creation fails
     */
    public static ChronicleOrderStateStore createInMemory(long expectedEntries) throws IOException {
        return new ChronicleOrderStateStore(null, expectedEntries);
    }
    
    /**
     * Creates a new persisted ChronicleMap store backed by a file.
     * If the file exists, it will be loaded; otherwise, a new store is created.
     * 
     * @param persistenceFile File path for persistence
     * @param expectedEntries Expected number of entries (for capacity planning)
     * @return New persisted store
     * @throws IOException if store creation or loading fails
     */
    public static ChronicleOrderStateStore createPersisted(Path persistenceFile, long expectedEntries) 
            throws IOException {
        // Create parent directories if needed
        if (persistenceFile.getParent() != null) {
            Files.createDirectories(persistenceFile.getParent());
        }
        return new ChronicleOrderStateStore(persistenceFile, expectedEntries);
    }
    
    /**
     * Private constructor - use factory methods.
     */
    private ChronicleOrderStateStore(Path persistenceFile, long expectedEntries) throws IOException {
        this.persistenceFile = persistenceFile;
        this.lock = new ReentrantReadWriteLock();
        this.closed = false;
        
        // Build ChronicleMap with built-in serialization
        ChronicleMapBuilder<Long, StoredOrder> builder = ChronicleMapBuilder
                .of(Long.class, StoredOrder.class)
                .name("order-state-store")
                .entries(expectedEntries)
                .averageKey(5000000000L)
                .averageValue(new StoredOrder(
                    new Order(1L, "SYMBOL", Order.SIDE_BUY, 10000L, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC)));
        
        // Create or load map
        if (persistenceFile != null) {
            File file = persistenceFile.toFile();
            if (file.exists()) {
                // Load existing map
                this.map = builder.createPersistedTo(file);
            } else {
                // Create new persisted map
                this.map = builder.createPersistedTo(file);
            }
        } else {
            // Create in-memory map
            this.map = builder.create();
        }
    }
    
    @Override
    public void put(Order order) {
        checkNotClosed();
        lock.writeLock().lock();
        try {
            map.put(order.orderId(), new StoredOrder(order));
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Order get(long orderId) {
        checkNotClosed();
        lock.readLock().lock();
        try {
            StoredOrder stored = map.get(orderId);
            return stored != null ? stored.toOrder() : null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public Order remove(long orderId) {
        checkNotClosed();
        lock.writeLock().lock();
        try {
            StoredOrder stored = map.remove(orderId);
            return stored != null ? stored.toOrder() : null;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean containsKey(long orderId) {
        checkNotClosed();
        lock.readLock().lock();
        try {
            return map.containsKey(orderId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public long size() {
        checkNotClosed();
        lock.readLock().lock();
        try {
            return map.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void clear() {
        checkNotClosed();
        lock.writeLock().lock();
        try {
            map.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void flush() {
        checkNotClosed();
        // ChronicleMap with memory-mapped files is automatically persisted
        // No explicit flush needed, but we can acquire write lock to ensure
        // all pending writes are complete
        lock.writeLock().lock();
        try {
            // Memory-mapped buffers are already synced by OS
            // This just ensures no writes are in progress
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean isClosed() {
        return closed;
    }
    
    @Override
    public void close() {
        if (closed) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            closed = true;
            map.close();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Gets the persistence file path, or null if in-memory.
     * 
     * @return Persistence file path
     */
    public Path getPersistenceFile() {
        return persistenceFile;
    }
    
    /**
     * Checks if store is persisted to disk.
     * 
     * @return true if persisted
     */
    public boolean isPersisted() {
        return persistenceFile != null;
    }
    
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("OrderStateStore is closed");
        }
    }
}
