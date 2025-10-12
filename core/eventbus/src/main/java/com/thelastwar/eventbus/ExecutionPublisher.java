package com.thelastwar.eventbus;

import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.TradeEvent;

/**
 * High-performance publisher for execution and trade events from Matching Engine
 * to downstream services (OMS, P&L, Reconciliation, Position Management).
 * 
 * This publisher is optimized for ultra-low latency and high throughput with the
 * following performance targets:
 * - Event propagation latency < 10 µs
 * - Zero event loss under 1M msg/sec load
 * - Serialization speed ≥ 5M msgs/sec
 * - Consistent ordering of executions per instrument
 * 
 * Architecture:
 * - Uses Aeron multicast for efficient fanout to multiple subscribers
 * - Binary serialization using EventSerializer for ultra-fast encoding
 * - GC-neutral design with object pooling and pre-allocated buffers
 * - Per-instrument ordering guarantee
 * - Back-pressure handling for downstream subscriber protection
 * 
 * Topic Integration:
 * - positions.in: Position updates derived from execution events
 * - ledger.in: Trade events for P&L and accounting
 * - executions.out: Execution reports for OMS and risk systems
 * 
 * Thread Safety:
 * - Thread-safe for concurrent publishing from multiple matching engine threads
 * - Each symbol can be published from a single thread for ordering guarantees
 */
public interface ExecutionPublisher extends AutoCloseable {
    
    /**
     * Publishes an execution event to downstream subscribers.
     * This is a non-blocking call that returns immediately.
     * 
     * @param event ExecutionEvent to publish
     * @return true if published successfully, false if back-pressure or error
     * @throws IllegalStateException if publisher is not started
     */
    boolean publishExecution(ExecutionEvent event);
    
    /**
     * Publishes a trade event to downstream subscribers.
     * This is a non-blocking call that returns immediately.
     * 
     * @param event TradeEvent to publish
     * @return true if published successfully, false if back-pressure or error
     * @throws IllegalStateException if publisher is not started
     */
    boolean publishTrade(TradeEvent event);
    
    /**
     * Publishes an execution event with retry on back-pressure.
     * This call may block briefly if the subscriber is slower.
     * 
     * @param event ExecutionEvent to publish
     * @param maxRetries Maximum number of retries on back-pressure (0 = no retry)
     * @return true if published successfully, false if all retries exhausted
     * @throws IllegalStateException if publisher is not started
     */
    boolean publishExecutionWithRetry(ExecutionEvent event, int maxRetries);
    
    /**
     * Publishes a trade event with retry on back-pressure.
     * This call may block briefly if the subscriber is slower.
     * 
     * @param event TradeEvent to publish
     * @param maxRetries Maximum number of retries on back-pressure (0 = no retry)
     * @return true if published successfully, false if all retries exhausted
     * @throws IllegalStateException if publisher is not started
     */
    boolean publishTradeWithRetry(TradeEvent event, int maxRetries);
    
    /**
     * Starts the publisher and initializes connections to downstream topics.
     * Must be called before publishing any events.
     * 
     * @throws IllegalStateException if already started
     */
    void start();
    
    /**
     * Stops the publisher and releases all resources.
     * Ensures all pending events are flushed before shutdown.
     */
    void stop();
    
    /**
     * Checks if the publisher is currently running.
     * 
     * @return true if started and ready to publish
     */
    boolean isRunning();
    
    /**
     * Gets the total number of execution events published since start.
     * 
     * @return execution event count
     */
    long getPublishedExecutionCount();
    
    /**
     * Gets the total number of trade events published since start.
     * 
     * @return trade event count
     */
    long getPublishedTradeCount();
    
    /**
     * Gets the number of events that could not be published due to back-pressure.
     * 
     * @return back-pressure event count
     */
    long getBackPressureCount();
    
    /**
     * Flushes any buffered events to ensure delivery.
     * Should be called periodically or before shutdown.
     * 
     * @return true if flush completed successfully
     */
    boolean flush();
    
    /**
     * Builder for creating ExecutionPublisher instances with custom configuration.
     */
    interface Builder {
        /**
         * Sets the positions topic channel (default: aeron:udp?endpoint=224.0.1.1:40123).
         * 
         * @param channel Aeron channel for position updates
         * @return this builder
         */
        Builder positionsChannel(String channel);
        
        /**
         * Sets the ledger topic channel (default: aeron:udp?endpoint=224.0.1.2:40124).
         * 
         * @param channel Aeron channel for ledger/trade events
         * @return this builder
         */
        Builder ledgerChannel(String channel);
        
        /**
         * Sets the executions output channel (default: aeron:udp?endpoint=224.0.1.3:40125).
         * 
         * @param channel Aeron channel for execution reports
         * @return this builder
         */
        Builder executionsChannel(String channel);
        
        /**
         * Sets the stream ID for all channels (default: 1002).
         * 
         * @param streamId Stream ID
         * @return this builder
         */
        Builder streamId(int streamId);
        
        /**
         * Enables or disables metrics collection (default: true).
         * 
         * @param enabled true to enable metrics
         * @return this builder
         */
        Builder metricsEnabled(boolean enabled);
        
        /**
         * Builds the ExecutionPublisher instance.
         * 
         * @return configured ExecutionPublisher
         */
        ExecutionPublisher build();
    }
}
