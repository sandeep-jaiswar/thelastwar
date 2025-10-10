package com.thelastwar.eventbus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple in-memory implementation of EventBus for testing.
 * This is NOT the production implementation - it's for unit tests only.
 * 
 * Production implementations should use Aeron or Chronicle Queue.
 */
public class InMemoryEventBus implements EventBus {

    private static final Logger LOGGER = Logger.getLogger(InMemoryEventBus.class.getName());

    private final List<HandlerRegistration>[] handlers;
    private final AtomicLong publishedCount = new AtomicLong(0);
    private volatile boolean running = false;

    @SuppressWarnings("unchecked")
    public InMemoryEventBus() {
        // Pre-allocate handler arrays for all possible event types
        // Using arrays indexed by event type for O(1) lookup
        this.handlers = new CopyOnWriteArrayList[10000];
        for (int i = 0; i < handlers.length; i++) {
            handlers[i] = new CopyOnWriteArrayList<HandlerRegistration>();
        }
    }

    @Override
    public boolean publish(Event event) {
        if (event == null || !running) {
            return false;
        }

        int eventType = event.eventType();
        if (eventType < 0 || eventType >= handlers.length) {
            return false;
        }

        publishedCount.incrementAndGet();

        // Dispatch to all handlers for this event type
        List<HandlerRegistration> eventHandlers = handlers[eventType];
        for (HandlerRegistration registration : eventHandlers) {
            if (registration.isActive()) {
                try {
                    registration.handler.onEvent(event);
                } catch (Exception e) {
                    try {
                        registration.handler.onError(event, e);
                    } catch (Exception errorHandlerException) {
                        // Log error handler exceptions to prevent cascade failures
                        LOGGER.log(Level.WARNING, "Error handler threw exception for event type: " + event.eventType(),
                                errorHandlerException);
                    }
                }
            }
        }

        return true;
    }

    @Override
    public Subscription subscribe(int eventType, EventHandler handler) {
        if (handler == null || eventType < 0 || eventType >= handlers.length) {
            throw new IllegalArgumentException("Invalid event type or null handler");
        }

        HandlerRegistration registration = new HandlerRegistration(handler);
        handlers[eventType].add(registration);

        return new Subscription() {
            @Override
            public void unsubscribe() {
                registration.active = false;
                handlers[eventType].remove(registration);
            }

            @Override
            public boolean isActive() {
                return registration.active;
            }
        };
    }

    @Override
    public long getPublishedEventCount() {
        return publishedCount.get();
    }

    @Override
    public int getSubscriberCount(int eventType) {
        if (eventType < 0 || eventType >= handlers.length) {
            return 0;
        }
        return (int) handlers[eventType].stream()
                .filter(HandlerRegistration::isActive)
                .count();
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    private static class HandlerRegistration {
        final EventHandler handler;
        volatile boolean active = true;

        HandlerRegistration(EventHandler handler) {
            this.handler = handler;
        }

        boolean isActive() {
            return active;
        }
    }
}
