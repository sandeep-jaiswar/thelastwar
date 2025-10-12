package com.thelastwar.matching;

import com.thelastwar.eventbus.model.OrderCancel;
import com.thelastwar.eventbus.model.OrderEnvelope;
import com.thelastwar.eventbus.model.OrderModify;
import com.thelastwar.eventbus.model.TickEvent;

/**
 * IMatchingEngine interface defines the core contract for order matching operations.
 * 
 * Each implementation should:
 * - Be pinned to a single CPU core for optimal cache locality
 * - Process messages sequentially (single-threaded per instance)
 * - Operate lock-free for deterministic behavior
 * - Maintain price-time priority for order matching
 * - Emit execution events for downstream processing
 * 
 * Performance targets:
 * - Sustained throughput ≥ 5M orders/sec per engine instance
 * - Median latency ≤ 8 µs
 * - p99 latency < 15 µs
 * - Zero GC in matching loop
 * - Deterministic replay (same input → same output)
 * 
 * Thread Safety:
 * - Implementations should be single-threaded by design
 * - No locking required within the matching loop
 * - External synchronization needed for concurrent access
 */
public interface IMatchingEngine {
    
    /**
     * Processes a new order request wrapped in an envelope.
     * 
     * The order will be:
     * 1. Validated for basic correctness
     * 2. Matched against the opposite side of the order book
     * 3. Added to the book if not fully filled
     * 4. Generate trade/execution events for fills
     * 
     * Performance: Target ≤ 8 µs median latency
     * 
     * @param envelope Order envelope containing order event and metadata
     */
    void onNewOrder(OrderEnvelope envelope);
    
    /**
     * Processes an order cancellation request.
     * 
     * The order will be:
     * 1. Located in the order book
     * 2. Removed if found and valid
     * 3. Generate cancellation execution event
     * 
     * Performance: Target ≤ 5 µs median latency
     * 
     * @param cancel Cancel request with order identifier
     */
    void onCancel(OrderCancel cancel);
    
    /**
     * Processes an order modification (replace) request.
     * 
     * The order will be:
     * 1. Located in the order book
     * 2. Removed and replaced with modified order (loses time priority)
     * 3. Modified order matched if price crosses
     * 4. Generate appropriate execution events
     * 
     * Performance: Target ≤ 10 µs median latency
     * 
     * @param modify Modification request with new price/quantity
     */
    void onReplace(OrderModify modify);
    
    /**
     * Processes a market data tick update.
     * 
     * Used for:
     * 1. Updating reference prices for stop orders
     * 2. Triggering stop orders that have become executable
     * 3. Market data validation and monitoring
     * 
     * Performance: Target ≤ 3 µs median latency
     * 
     * @param tick Market data tick event
     */
    void onMarketDataUpdate(TickEvent tick);
    
    /**
     * Starts the matching engine.
     * This typically involves:
     * - Subscribing to event streams
     * - Initializing internal state
     * - Starting processing threads (if applicable)
     */
    void start();
    
    /**
     * Stops the matching engine gracefully.
     * This should:
     * - Complete processing of pending events
     * - Unsubscribe from event streams
     * - Shut down processing threads
     */
    void stop();
    
    /**
     * Checks if the matching engine is currently running.
     * 
     * @return true if running, false otherwise
     */
    boolean isRunning();
    
    /**
     * Gets the current sequence number for deterministic replay.
     * 
     * @return current sequence number
     */
    long getCurrentSequence();
}
