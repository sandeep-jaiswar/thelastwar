package com.thelastwar.matching;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.OrderEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Example demonstrating the Cache Warming Service integration.
 * 
 * This example shows how to:
 * 1. Set up the matching engine and event bus
 * 2. Initialize the cache warming service
 * 3. Submit orders and observe cache warming in action
 * 4. Monitor metrics
 */
public class CacheWarmingExample {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Cache Warming Service Example ===\n");
        
        // 1. Create and start Event Bus
        System.out.println("1. Creating Event Bus...");
        EventBus eventBus = new InMemoryEventBus();
        eventBus.start();
        
        // 2. Create and start Matching Engine
        System.out.println("2. Creating Matching Engine...");
        MatchingEngine engine = new MatchingEngine(eventBus);
        engine.start();
        
        // 3. Create Metrics Registry
        System.out.println("3. Creating Metrics Registry...");
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        // 4. Create and start Cache Warming Service
        // Using short window (5 seconds) and low threshold (3) for demonstration
        System.out.println("4. Creating Cache Warming Service...");
        CacheWarmingService cacheWarming = new CacheWarmingService(
            engine,
            eventBus,
            meterRegistry,
            5000L,  // 5-second window for demo
            3       // Low threshold for demo
        );
        cacheWarming.start();
        
        System.out.println("\n=== Submitting Orders ===\n");
        
        // 5. Submit orders for AAPL (will become "hot")
        System.out.println("Submitting 10 orders for AAPL...");
        for (int i = 0; i < 10; i++) {
            submitOrder(eventBus, "AAPL", 1000L + i, 15000L + i);
        }
        
        // 6. Submit a few orders for GOOGL
        System.out.println("Submitting 2 orders for GOOGL...");
        for (int i = 0; i < 2; i++) {
            submitOrder(eventBus, "GOOGL", 2000L + i, 280000L);
        }
        
        // 7. Submit orders for MSFT
        System.out.println("Submitting 5 orders for MSFT...");
        for (int i = 0; i < 5; i++) {
            submitOrder(eventBus, "MSFT", 3000L + i, 35000L);
        }
        
        // Wait for activity to be tracked
        Thread.sleep(500);
        
        // 8. Display initial metrics
        System.out.println("\n=== Initial Metrics (before warming) ===");
        displayMetrics(cacheWarming);
        
        // 9. Wait for cache warming to occur (first run is after 5 seconds)
        System.out.println("\nWaiting for cache warming (6 seconds)...");
        Thread.sleep(6000);
        
        // 10. Display metrics after warming
        System.out.println("\n=== Metrics After Warming ===");
        displayMetrics(cacheWarming);
        
        // 11. Submit more orders - these should be cache hits
        System.out.println("\nSubmitting more orders (should be cache hits)...");
        for (int i = 0; i < 10; i++) {
            submitOrder(eventBus, "AAPL", 1100L + i, 15000L);
        }
        
        Thread.sleep(500);
        
        // 12. Display final metrics
        System.out.println("\n=== Final Metrics ===");
        displayMetrics(cacheWarming);
        
        // 13. Cleanup
        System.out.println("\n=== Shutting Down ===");
        cacheWarming.stop();
        engine.stop();
        eventBus.stop();
        
        System.out.println("\nExample completed successfully!");
    }
    
    /**
     * Helper method to submit an order event.
     */
    private static void submitOrder(EventBus eventBus, String symbol, long account, long price) {
        OrderEvent orderEvent = OrderEvent.newOrder(
            System.nanoTime(),  // orderId
            symbol,
            OrderEvent.SIDE_BUY,
            OrderEvent.TYPE_LIMIT,
            100L,  // quantity
            price,
            account,
            1  // exchange
        );
        
        Event event = Event.create(
            System.nanoTime(),
            eventBus.getCurrentSequence() + 1,
            SourceId.OMS,
            EventType.ORDER_SUBMITTED,
            0L,
            orderEvent
        );
        
        eventBus.publish(event);
    }
    
    /**
     * Helper method to display cache warming metrics.
     */
    private static void displayMetrics(CacheWarmingService service) {
        CacheWarmingService.CacheWarmingMetrics metrics = service.getMetrics();
        
        System.out.println("  Tracked Symbols:  " + metrics.trackedSymbols());
        System.out.println("  Tracked Accounts: " + metrics.trackedAccounts());
        System.out.println("  Warmed Symbols:   " + metrics.warmedSymbols());
        System.out.println("  Warmed Accounts:  " + metrics.warmedAccounts());
        System.out.println("  Total Hits:       " + metrics.totalHits());
        System.out.println("  Total Misses:     " + metrics.totalMisses());
        System.out.println("  Hit Ratio:        " + String.format("%.2f%%", metrics.hitRatio() * 100));
    }
}
