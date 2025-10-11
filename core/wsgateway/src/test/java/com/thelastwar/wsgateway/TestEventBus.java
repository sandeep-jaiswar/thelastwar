package com.thelastwar.wsgateway;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test implementation of EventBus for unit testing.
 */
public class TestEventBus implements EventBus {
    
    private final Map<Integer, List<EventHandler<?>>> handlers;
    private final AtomicLong publishCount;
    private final AtomicBoolean started;
    
    public TestEventBus() {
        this.handlers = new ConcurrentHashMap<>();
        this.publishCount = new AtomicLong(0);
        this.started = new AtomicBoolean(false);
    }
    
    @Override
    public boolean publish(Event event) {
        publishCount.incrementAndGet();
        List<EventHandler<?>> eventHandlers = handlers.get(event.eventType());
        if (eventHandlers != null) {
            for (EventHandler<?> handler : eventHandlers) {
                try {
                    ((EventHandler<Event>) handler).onEvent(event);
                } catch (Exception e) {
                    // Ignore errors in test
                }
            }
        }
        return true;
    }
    
    @Override
    public Subscription subscribe(int eventType, EventHandler<?> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
        return new Subscription() {
            @Override
            public void unsubscribe() {
                List<EventHandler<?>> list = handlers.get(eventType);
                if (list != null) {
                    list.remove(handler);
                }
            }
            
            @Override
            public boolean isActive() {
                List<EventHandler<?>> list = handlers.get(eventType);
                return list != null && list.contains(handler);
            }
        };
    }
    
    @Override
    public long getPublishedEventCount() {
        return publishCount.get();
    }
    
    @Override
    public int getSubscriberCount(int eventType) {
        List<EventHandler<?>> list = handlers.get(eventType);
        return list == null ? 0 : list.size();
    }
    
    @Override
    public void start() {
        started.set(true);
    }
    
    @Override
    public void stop() {
        started.set(false);
        handlers.clear();
    }
}
