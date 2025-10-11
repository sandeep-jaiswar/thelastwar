package com.thelastwar.gateway;

import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;

import java.util.function.Supplier;

/**
 * Object pool for MessageEnvelope instances using Agrona's lock-free queue.
 * 
 * This pool eliminates heap allocation in the hot path by reusing MessageEnvelope
 * objects, significantly reducing GC pressure in high-throughput scenarios.
 * 
 * Key features:
 * - Lock-free implementation using Agrona's ManyToManyConcurrentArrayQueue
 * - Thread-safe for concurrent access
 * - Configurable pool size and buffer capacity
 * - Automatic object creation when pool is empty
 * - Zero heap allocation after warmup
 * 
 * Performance targets:
 * - Acquire/release latency: &lt; 100 ns
 * - Zero heap allocation in steady state
 * - Thread-safe for concurrent access
 * 
 * Usage:
 * <pre>
 * MessageEnvelopePool pool = new MessageEnvelopePool(1000, 8192);
 * MessageEnvelope envelope = pool.acquire();
 * try {
 *     // Use envelope
 *     envelope.setProtocolType(MessageEnvelope.ProtocolType.FIX);
 *     // ... set other fields
 * } finally {
 *     pool.release(envelope);
 * }
 * </pre>
 */
public class MessageEnvelopePool {
    
    private final ManyToManyConcurrentArrayQueue<MessageEnvelope> pool;
    private final int bufferCapacity;
    private final Supplier<MessageEnvelope> envelopeFactory;
    
    /**
     * Creates a new MessageEnvelopePool with default buffer capacity (8KB).
     * 
     * @param poolSize Maximum number of envelopes in the pool (must be power of 2)
     */
    public MessageEnvelopePool(int poolSize) {
        this(poolSize, 8192);
    }
    
    /**
     * Creates a new MessageEnvelopePool with specified buffer capacity.
     * 
     * @param poolSize Maximum number of envelopes in the pool (must be power of 2)
     * @param bufferCapacity Buffer capacity for each envelope in bytes
     * @throws IllegalArgumentException if poolSize is not a power of 2 or less than 2
     */
    public MessageEnvelopePool(int poolSize, int bufferCapacity) {
        if (poolSize < 2 || (poolSize & (poolSize - 1)) != 0) {
            throw new IllegalArgumentException("Pool size must be a power of 2 and at least 2");
        }
        if (bufferCapacity < 1) {
            throw new IllegalArgumentException("Buffer capacity must be positive");
        }
        
        this.bufferCapacity = bufferCapacity;
        this.pool = new ManyToManyConcurrentArrayQueue<>(poolSize);
        this.envelopeFactory = () -> new MessageEnvelope(bufferCapacity);
        
        // Pre-populate the pool
        for (int i = 0; i < poolSize; i++) {
            pool.offer(envelopeFactory.get());
        }
    }
    
    /**
     * Acquires a MessageEnvelope from the pool.
     * If the pool is empty, creates a new instance.
     * The acquired envelope is reset and ready for use.
     * 
     * @return A MessageEnvelope instance
     */
    public MessageEnvelope acquire() {
        MessageEnvelope envelope = pool.poll();
        if (envelope == null) {
            // Pool is empty, create new instance
            envelope = envelopeFactory.get();
        } else {
            // Reset the envelope for reuse
            envelope.reset();
        }
        return envelope;
    }
    
    /**
     * Returns a MessageEnvelope to the pool.
     * The envelope is reset before being returned to the pool.
     * 
     * @param envelope The envelope to return (must not be null)
     * @return true if successfully returned to pool, false if pool is full
     * @throws IllegalArgumentException if envelope is null
     */
    public boolean release(MessageEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("Envelope cannot be null");
        }
        
        // Reset the envelope before returning to pool
        envelope.reset();
        
        // Try to return to pool (fails if pool is full)
        return pool.offer(envelope);
    }
    
    /**
     * Gets the current number of envelopes available in the pool.
     * This is an approximate value due to concurrent access.
     * 
     * @return Approximate number of available envelopes
     */
    public int size() {
        return pool.size();
    }
    
    /**
     * Gets the maximum capacity of the pool.
     * 
     * @return Pool capacity
     */
    public int capacity() {
        return pool.capacity();
    }
    
    /**
     * Gets the buffer capacity for each envelope.
     * 
     * @return Buffer capacity in bytes
     */
    public int getBufferCapacity() {
        return bufferCapacity;
    }
    
    /**
     * Clears the pool and releases all envelopes.
     * After calling this method, the pool will be empty.
     */
    public void clear() {
        pool.clear();
    }
}
