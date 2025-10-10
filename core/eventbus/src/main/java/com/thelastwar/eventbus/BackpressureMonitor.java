package com.thelastwar.eventbus;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Backpressure monitor for EventBus using ring buffer watermarks.
 * 
 * Monitors the ring buffer position and signals backpressure when:
 * - High watermark is exceeded (typically 80% full)
 * - Low watermark is dropped below (typically 50% full)
 * 
 * This allows the system to gracefully handle load spikes by:
 * - Signaling producers to slow down when pressure is high
 * - Allowing resumption when pressure drops
 * - Preventing message loss under sustained load
 */
public class BackpressureMonitor {
    
    private final int ringBufferSize;
    private final int highWatermark;
    private final int lowWatermark;
    
    private final AtomicLong producerPosition = new AtomicLong(0);
    private final AtomicLong consumerPosition = new AtomicLong(0);
    private final AtomicBoolean backpressureActive = new AtomicBoolean(false);
    
    /**
     * Creates a backpressure monitor with default watermarks.
     * High watermark: 80% of ring buffer size
     * Low watermark: 50% of ring buffer size
     * 
     * @param ringBufferSize the size of the ring buffer to monitor
     */
    public BackpressureMonitor(int ringBufferSize) {
        this(ringBufferSize, (int)(ringBufferSize * 0.8), (int)(ringBufferSize * 0.5));
    }
    
    /**
     * Creates a backpressure monitor with custom watermarks.
     * 
     * @param ringBufferSize the size of the ring buffer to monitor
     * @param highWatermark threshold for activating backpressure (messages pending)
     * @param lowWatermark threshold for deactivating backpressure (messages pending)
     */
    public BackpressureMonitor(int ringBufferSize, int highWatermark, int lowWatermark) {
        if (ringBufferSize <= 0) {
            throw new IllegalArgumentException("Ring buffer size must be positive");
        }
        if (highWatermark < lowWatermark) {
            throw new IllegalArgumentException("High watermark must be >= low watermark");
        }
        if (highWatermark > ringBufferSize) {
            throw new IllegalArgumentException("High watermark cannot exceed ring buffer size");
        }
        
        this.ringBufferSize = ringBufferSize;
        this.highWatermark = highWatermark;
        this.lowWatermark = lowWatermark;
    }
    
    /**
     * Updates the producer position (called after publish).
     * 
     * @param position the new producer position
     * @return true if backpressure was activated due to this update
     */
    public boolean updateProducerPosition(long position) {
        producerPosition.set(position);
        return checkBackpressure();
    }
    
    /**
     * Updates the consumer position (called after consumption).
     * 
     * @param position the new consumer position
     * @return true if backpressure was deactivated due to this update
     */
    public boolean updateConsumerPosition(long position) {
        consumerPosition.set(position);
        return checkBackpressure();
    }
    
    /**
     * Checks current backpressure state and updates if necessary.
     * 
     * @return true if backpressure is currently active
     */
    private boolean checkBackpressure() {
        long pending = getPendingMessages();
        
        if (pending >= highWatermark) {
            if (backpressureActive.compareAndSet(false, true)) {
                // Backpressure activated
                return true;
            }
        } else if (pending <= lowWatermark) {
            if (backpressureActive.compareAndSet(true, false)) {
                // Backpressure deactivated
            }
        }
        
        return backpressureActive.get();
    }
    
    /**
     * Gets the number of pending messages (producer - consumer).
     * 
     * @return number of messages pending in the buffer
     */
    public long getPendingMessages() {
        return Math.max(0, producerPosition.get() - consumerPosition.get());
    }
    
    /**
     * Checks if backpressure is currently active.
     * 
     * @return true if backpressure is active
     */
    public boolean isBackpressureActive() {
        return backpressureActive.get();
    }
    
    /**
     * Gets the current utilization percentage (0-100).
     * 
     * @return utilization percentage
     */
    public int getUtilizationPercent() {
        long pending = getPendingMessages();
        return (int)((pending * 100L) / ringBufferSize);
    }
    
    /**
     * Gets the high watermark threshold.
     * 
     * @return high watermark in messages
     */
    public int getHighWatermark() {
        return highWatermark;
    }
    
    /**
     * Gets the low watermark threshold.
     * 
     * @return low watermark in messages
     */
    public int getLowWatermark() {
        return lowWatermark;
    }
    
    /**
     * Gets the ring buffer size.
     * 
     * @return ring buffer size in messages
     */
    public int getRingBufferSize() {
        return ringBufferSize;
    }
    
    /**
     * Resets the monitor state.
     */
    public void reset() {
        producerPosition.set(0);
        consumerPosition.set(0);
        backpressureActive.set(false);
    }
}
