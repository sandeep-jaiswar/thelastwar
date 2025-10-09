package com.thelastwar.eventbus;

/**
 * Core Event Bus abstraction for decoupling producers and consumers.
 * 
 * This interface provides a GC-neutral publish/subscribe mechanism with the following guarantees:
 * - No autoboxing in the hot path
 * - Minimal allocations during publish/subscribe operations
 * - Sub-10 microsecond round-trip latency (per architecture targets)
 * - Support for deterministic replay via sequence numbers
 * - Event log as system-of-record for state recovery
 * 
 * Architecture alignment:
 * - Follows single-writer principle where possible
 * - Supports mechanical sympathy patterns
 * - Enables lock-free implementations
 * - Append-only event flow for replay determinism
 * 
 * Thread-safety: Implementations must be thread-safe for concurrent publishing and subscribing.
 */
public interface EventBus {
    
    /**
     * Publishes an event to all subscribed handlers for the event type.
     * This is a hot-path method designed for minimal latency.
     * 
     * Performance target: < 10 µs round-trip (architecture requirement)
     * 
     * @param event The event to publish (must not be null)
     * @return true if the event was successfully published, false otherwise
     */
    boolean publish(Event event);
    
    /**
     * Subscribes a handler to a specific event type.
     * 
     * @param eventType The type of events to subscribe to (use EventType constants)
     * @param handler The handler that will process events of this type (must not be null)
     * @return A subscription handle that can be used to unsubscribe
     */
    Subscription subscribe(int eventType, EventHandler<?> handler);
    
    /**
     * Publishes an event with a specific priority.
     * Higher priority events may be processed before lower priority ones.
     * 
     * @param event The event to publish
     * @param priority Priority level (higher = more urgent)
     * @return true if the event was successfully published, false otherwise
     */
    default boolean publish(Event event, int priority) {
        // Default implementation ignores priority
        return publish(event);
    }
    
    /**
     * Attempts to publish an event without blocking.
     * If the event bus is full, this method returns false immediately.
     * 
     * @param event The event to publish
     * @return true if published, false if the bus was full
     */
    default boolean tryPublish(Event event) {
        return publish(event);
    }
    
    /**
     * Returns the number of events published since startup.
     * Useful for monitoring and diagnostics.
     * 
     * @return total event count
     */
    long getPublishedEventCount();
    
    /**
     * Returns the number of handlers subscribed to a specific event type.
     * 
     * @param eventType The event type to query
     * @return number of subscribed handlers
     */
    int getSubscriberCount(int eventType);
    
    /**
     * Returns the current sequence number for event ordering.
     * Critical for deterministic replay - each event should have an incrementing sequence.
     * 
     * @return current sequence number
     */
    default long getCurrentSequence() {
        return getPublishedEventCount();
    }
    
    /**
     * Replays events from a specific sequence number.
     * Enables deterministic replay for state recovery.
     * 
     * @param fromSequence Starting sequence number
     * @param toSequence Ending sequence number (inclusive)
     * @param handler Handler to receive replayed events
     * @return number of events replayed
     */
    default long replay(long fromSequence, long toSequence, EventHandler<?> handler) {
        // Default implementation - override for replay support
        throw new UnsupportedOperationException("Replay not supported by this implementation");
    }
    
    /**
     * Starts the event bus and any background processing threads.
     * May pin threads to CPU cores for mechanical sympathy.
     */
    void start();
    
    /**
     * Stops the event bus gracefully, ensuring all queued events are processed.
     */
    void stop();
    
    /**
     * Subscription handle that can be used to unsubscribe a handler.
     */
    interface Subscription {
        /**
         * Unsubscribes the handler from receiving events.
         * After calling this method, the handler will no longer receive events.
         */
        void unsubscribe();
        
        /**
         * Checks if this subscription is still active.
         * 
         * @return true if subscribed, false if unsubscribed
         */
        boolean isActive();
    }
}
