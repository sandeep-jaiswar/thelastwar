package com.thelastwar.matching;

import com.thelastwar.eventbus.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple in-memory implementation of EventBus for testing matching engine.
 * Supports deterministic replay by storing all events in memory.
 * Copied from eventbus module to avoid test dependency issues.
 */
class InMemoryEventBus implements EventBus {

    private static final Logger LOGGER = Logger.getLogger(InMemoryEventBus.class.getName());

    private final List<HandlerRegistration>[] handlers;
    private final AtomicLong publishedCount = new AtomicLong(0);
    private volatile boolean running = false;
    
    // Event log for replay support
    private final List<Event> eventLog = new ArrayList<>();
    private final Object eventLogLock = new Object();

    @SuppressWarnings("unchecked")
    public InMemoryEventBus() {
        this.handlers = new List[10000];
        for (int i = 0; i < handlers.length; i++) {
            handlers[i] = new CopyOnWriteArrayList<>();
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

        long sequence = publishedCount.incrementAndGet();
        
        // Store event in log for replay (use event's sequence or assign new one)
        Event storedEvent = event;
        if (event.sequence() != sequence) {
            // Update sequence to match published count for consistency
            storedEvent = Event.create(
                event.timestamp(),
                sequence,
                event.sourceId(),
                event.eventType(),
                event.header(),
                event.payload()
            );
        }
        
        synchronized (eventLogLock) {
            eventLog.add(storedEvent);
        }

        List<HandlerRegistration> eventHandlers = handlers[eventType];
        for (HandlerRegistration registration : eventHandlers) {
            if (registration.isActive()) {
                try {
                    registration.handler.onEvent(storedEvent);
                } catch (Exception e) {
                    try {
                        registration.handler.onError(storedEvent, e);
                    } catch (Exception errorHandlerException) {
                        LOGGER.log(Level.WARNING, "Error handler threw exception for event type: " + event.eventType(),
                                errorHandlerException);
                    }
                }
            }
        }

        return true;
    }

    @Override
    public Subscription subscribe(int eventType, EventHandler<?> handler) {
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
    
    @Override
    public long replay(long fromSequence, long toSequence, EventHandler<?> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        
        if (fromSequence < 0 || toSequence < fromSequence) {
            throw new IllegalArgumentException("Invalid sequence range: " + fromSequence + " to " + toSequence);
        }
        
        long replayedCount = 0;
        
        // Create a snapshot of events to avoid concurrent modification
        List<Event> eventsToReplay;
        synchronized (eventLogLock) {
            eventsToReplay = new ArrayList<>(eventLog);
        }
        
        for (Event event : eventsToReplay) {
            if (event.sequence() >= fromSequence && event.sequence() <= toSequence) {
                try {
                    handler.onEvent(event);
                    replayedCount++;
                } catch (Exception e) {
                    try {
                        handler.onError(event, e);
                    } catch (Exception errorHandlerException) {
                        LOGGER.log(Level.WARNING, 
                            "Error handler threw exception during replay for sequence: " + event.sequence(),
                            errorHandlerException);
                    }
                }
            }
        }
        
        return replayedCount;
    }
    
    /**
     * Clears the event log. Useful for testing.
     */
    public void clearEventLog() {
        synchronized (eventLogLock) {
            eventLog.clear();
        }
        publishedCount.set(0);
    }
    
    /**
     * Gets the number of events stored in the event log.
     * 
     * @return event log size
     */
    public int getEventLogSize() {
        synchronized (eventLogLock) {
            return eventLog.size();
        }
    }

    private static class HandlerRegistration {
        final EventHandler<?> handler;
        volatile boolean active = true;

        HandlerRegistration(EventHandler<?> handler) {
            this.handler = handler;
        }

        boolean isActive() {
            return active;
        }
    }
}
