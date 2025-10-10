package com.thelastwar.eventbus;

/**
 * Handler interface for processing events.
 * Implementations should be stateless or use thread-local state to ensure
 * GC-neutrality.
 * 
 * @param <T> The type of payload this handler processes
 */
@FunctionalInterface
public interface EventHandler {

    /**
     * Handles an event.
     * This method should be fast and non-blocking to maintain low latency.
     * 
     * @param event The event to handle
     */
    void onEvent(Event event);

    /**
     * Optional callback for handler errors.
     * Default implementation does nothing.
     * 
     * @param event     The event that caused the error
     * @param exception The exception that occurred
     */
    default void onError(Event event, Throwable exception) {
        // Default: no-op
        // Implementations can override for custom error handling
    }
}
