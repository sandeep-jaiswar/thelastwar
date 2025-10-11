package com.thelastwar.restgateway.test;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory EventBus implementation for testing.
 */
public class TestEventBus implements EventBus {
    
    private final List<HandlerRegistration>[] handlers;
    private final AtomicLong publishedCount = new AtomicLong(0);
    private volatile boolean running = false;
    
    @SuppressWarnings("unchecked")
    public TestEventBus() {
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
        
        publishedCount.incrementAndGet();
        
        List<HandlerRegistration> eventHandlers = handlers[eventType];
        for (HandlerRegistration registration : eventHandlers) {
            if (registration.isActive()) {
                try {
                    registration.handler.onEvent(event);
                } catch (Exception e) {
                    try {
                        registration.handler.onError(event, e);
                    } catch (Exception ignored) {
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
        return 0;
    }
    
    @Override
    public long getCurrentSequence() {
        return publishedCount.get();
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
