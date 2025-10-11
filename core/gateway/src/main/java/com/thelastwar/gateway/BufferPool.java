package com.thelastwar.gateway;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Object pool for DirectByteBuffers to enable zero-copy message processing.
 * 
 * DirectByteBuffers are allocated off-heap, reducing GC pressure in the hot path.
 * Pool pre-allocates buffers at startup to eliminate allocation during message processing.
 * 
 * Performance characteristics:
 * - Zero allocation after warmup
 * - O(1) acquire/release operations
 * - Thread-safe using concurrent queue
 * - Configurable buffer size and pool capacity
 * 
 * Usage:
 * <pre>
 * BufferPool pool = new BufferPool(1000, 8192); // 1000 buffers of 8KB each
 * ByteBuffer buffer = pool.acquire();
 * try {
 *     // Use buffer for zero-copy operations
 * } finally {
 *     pool.release(buffer);
 * }
 * </pre>
 */
public class BufferPool {
    
    private final int bufferSize;
    private final int poolCapacity;
    private final BlockingQueue<ByteBuffer> pool;
    private volatile boolean closed = false;
    
    /**
     * Creates a new DirectByteBuffer pool.
     * 
     * @param poolCapacity Maximum number of buffers in the pool
     * @param bufferSize Size of each buffer in bytes
     * @throws IllegalArgumentException if parameters are invalid
     */
    public BufferPool(int poolCapacity, int bufferSize) {
        if (poolCapacity <= 0) {
            throw new IllegalArgumentException("Pool capacity must be positive");
        }
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }
        
        this.bufferSize = bufferSize;
        this.poolCapacity = poolCapacity;
        this.pool = new ArrayBlockingQueue<>(poolCapacity);
        
        // Pre-allocate all buffers
        for (int i = 0; i < poolCapacity; i++) {
            pool.offer(ByteBuffer.allocateDirect(bufferSize));
        }
    }
    
    /**
     * Acquires a buffer from the pool.
     * Blocks if no buffers are available.
     * 
     * @return A cleared DirectByteBuffer ready for use
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalStateException if pool is closed
     */
    public ByteBuffer acquire() throws InterruptedException {
        if (closed) {
            throw new IllegalStateException("BufferPool is closed");
        }
        
        ByteBuffer buffer = pool.take();
        buffer.clear();
        return buffer;
    }
    
    /**
     * Acquires a buffer from the pool with timeout.
     * 
     * @param timeout Timeout value
     * @param unit Timeout unit
     * @return A cleared DirectByteBuffer, or null if timeout expires
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalStateException if pool is closed
     */
    public ByteBuffer acquire(long timeout, TimeUnit unit) throws InterruptedException {
        if (closed) {
            throw new IllegalStateException("BufferPool is closed");
        }
        
        ByteBuffer buffer = pool.poll(timeout, unit);
        if (buffer != null) {
            buffer.clear();
        }
        return buffer;
    }
    
    /**
     * Tries to acquire a buffer without blocking.
     * 
     * @return A cleared DirectByteBuffer, or null if none available
     * @throws IllegalStateException if pool is closed
     */
    public ByteBuffer tryAcquire() {
        if (closed) {
            throw new IllegalStateException("BufferPool is closed");
        }
        
        ByteBuffer buffer = pool.poll();
        if (buffer != null) {
            buffer.clear();
        }
        return buffer;
    }
    
    /**
     * Returns a buffer to the pool.
     * The buffer should not be used after calling this method.
     * 
     * @param buffer Buffer to return (must be from this pool)
     * @return true if successfully returned, false otherwise
     * @throws IllegalArgumentException if buffer is null
     */
    public boolean release(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        
        if (closed) {
            return false;
        }
        
        // Verify buffer capacity matches pool configuration
        if (buffer.capacity() != bufferSize) {
            throw new IllegalArgumentException(
                "Buffer capacity " + buffer.capacity() + " does not match pool size " + bufferSize);
        }
        
        return pool.offer(buffer);
    }
    
    /**
     * Gets the number of buffers currently available in the pool.
     * 
     * @return Available buffer count
     */
    public int available() {
        return pool.size();
    }
    
    /**
     * Gets the total pool capacity.
     * 
     * @return Pool capacity
     */
    public int capacity() {
        return poolCapacity;
    }
    
    /**
     * Gets the size of each buffer in bytes.
     * 
     * @return Buffer size
     */
    public int bufferSize() {
        return bufferSize;
    }
    
    /**
     * Gets the current utilization as a percentage (0-100).
     * 
     * @return Utilization percentage
     */
    public double utilization() {
        return 100.0 * (1.0 - (double) available() / poolCapacity);
    }
    
    /**
     * Checks if the pool is closed.
     * 
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }
    
    /**
     * Closes the pool and releases all buffers.
     * After calling this, no further operations are allowed.
     */
    public void close() {
        closed = true;
        pool.clear();
    }
}
