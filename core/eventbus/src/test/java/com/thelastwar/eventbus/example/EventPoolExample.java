package com.thelastwar.eventbus.example;

import com.thelastwar.eventbus.*;

/**
 * Example demonstrating object pooling for zero-allocation event bus usage.
 * This pattern is critical for achieving microsecond-level latency.
 */
public class EventPoolExample {
    
    public static void main(String[] args) {
        // Create event bus
        EventBus eventBus = new InMemoryEventBus();
        eventBus.start();
        
        // Create event pool with 100 pre-allocated events
        EventPool pool = new EventPool(100);
        System.out.println("=== Event Pool Example ===");
        System.out.println("Pool capacity: " + pool.capacity());
        System.out.println("Pool available: " + pool.available());
        
        // Subscribe to market data events
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {
            System.out.println("Market data received: " + event.payload() + 
                             " (latency: " + event.getLatencyNanos() + " ns)");
        });
        
        // Example 1: Using pooled events for zero allocation
        System.out.println("\n=== Example 1: Zero-Allocation Publishing ===");
        for (int i = 0; i < 5; i++) {
            // Acquire from pool (zero allocation after warmup)
            EventPool.MutableEvent mutable = pool.acquire();
            
            // Set event data
            mutable.set(
                System.nanoTime(),
                i,
                SourceId.FEED_HANDLER,
                EventType.MARKET_DATA_UPDATE,
                0L,
                "AAPL: $" + (150.0 + i)
            );
            
            // Publish as immutable event
            eventBus.publish(mutable.toEvent());
            
            // Return to pool for reuse
            pool.release(mutable);
        }
        
        System.out.println("Pool available after 5 publishes: " + pool.available());
        
        // Example 2: High-throughput scenario
        System.out.println("\n=== Example 2: High-Throughput Scenario ===");
        long startTime = System.nanoTime();
        int iterations = 10000;
        
        for (int i = 0; i < iterations; i++) {
            EventPool.MutableEvent mutable = pool.acquire();
            mutable.set(
                System.nanoTime(),
                i,
                SourceId.ANALYTICS,
                EventType.LATENCY_SAMPLE,
                0L,
                null
            );
            pool.release(mutable);
        }
        
        long endTime = System.nanoTime();
        double throughput = (iterations * 1_000_000_000.0) / (endTime - startTime);
        System.out.println("Processed " + iterations + " events");
        System.out.println("Throughput: " + String.format("%.2f", throughput / 1_000_000) + " million ops/sec");
        System.out.println("Average latency: " + ((endTime - startTime) / iterations) + " ns");
        
        // Example 3: Using Event.now() helper
        System.out.println("\n=== Example 3: Event Helper Methods ===");
        Event event = Event.now(1L, SourceId.MATCHING_ENGINE, EventType.ORDER_FILLED, 0L, "Order #999");
        System.out.println("Event created with timestamp: " + event.timestamp());
        System.out.println("Is from Matching Engine: " + event.isFromSource(SourceId.MATCHING_ENGINE));
        System.out.println("Is ORDER_FILLED type: " + event.isOfType(EventType.ORDER_FILLED));
        
        // Example 4: Deterministic replay support
        System.out.println("\n=== Example 4: Deterministic Replay ===");
        long currentSequence = eventBus.getCurrentSequence();
        System.out.println("Current sequence: " + currentSequence);
        System.out.println("Total events published: " + eventBus.getPublishedEventCount());
        
        // Cleanup
        eventBus.stop();
        System.out.println("\n=== Example Complete ===");
        System.out.println("Final pool status - Available: " + pool.available() + "/" + pool.capacity());
    }
}
