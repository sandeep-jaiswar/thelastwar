package com.thelastwar.eventbus;

/**
 * Core Event Bus abstraction for decoupling producers and consumers.
 * 
 * This interface provides a GC-neutral publish/subscribe mechanism with the following guarantees:
 * - No autoboxing in the hot path
 * - Minimal allocations during publish/subscribe operations
 * - Sub-5 microsecond publish/subscribe latency
 * 
 * Thread-safety: Implementations must be thread-safe for concurrent publishing and subscribing.
 */
public interface EventBus {
    
    /**
     * Publishes an event to all subscribed handlers for the event type.
     * This is a hot-path method designed for minimal latency.
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
     * Starts the event bus and any background processing threads.
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
