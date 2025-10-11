package com.thelastwar.gateway;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory EventBus stub for gateway testing.
 */
class TestEventBus implements EventBus {
    
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
        
        // Dispatch to handlers
        for (HandlerRegistration reg : handlers[eventType]) {
            try {
                reg.handler.onEvent(event);
            } catch (Exception e) {
                reg.handler.onError(event, e);
            }
        }
        
        return true;
    }
    
    @Override
    public Subscription subscribe(int eventType, EventHandler<?> handler) {
        if (eventType < 0 || eventType >= handlers.length || handler == null) {
            throw new IllegalArgumentException("Invalid eventType or handler");
        }
        
        HandlerRegistration reg = new HandlerRegistration(handler);
        handlers[eventType].add(reg);
        
        return new Subscription() {
            @Override
            public void unsubscribe() {
                handlers[eventType].remove(reg);
            }
            
            @Override
            public boolean isActive() {
                return handlers[eventType].contains(reg);
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
        return handlers[eventType].size();
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
        final EventHandler<?> handler;
        
        HandlerRegistration(EventHandler<?> handler) {
            this.handler = handler;
        }
    }
}
