package com.thelastwar.matching.shard;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating sharded matching engine usage.
 * 
 * This example shows:
 * - Setting up a sharded cluster
 * - Processing orders across multiple shards
 * - Monitoring load distribution
 * - Dynamic scaling
 */
public class ShardingExample {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Matching Engine Sharding Example ===\n");
        
        // 1. Create infrastructure
        System.out.println("1. Setting up infrastructure...");
        EventBus eventBus = new SimpleEventBus();
        eventBus.start();
        
        ShardRegistry registry = new InMemoryShardRegistry();
        ShardRouter router = new ConsistentHashRouter();
        
        ShardManager manager = new ShardManager(eventBus, registry, router);
        manager.start();
        
        // 2. Create initial shards
        System.out.println("2. Creating 4 shards...");
        for (int i = 0; i < 4; i++) {
            manager.addShard(new ShardId(i), i, Set.of());
            System.out.println("   - Shard " + i + " created (CPU " + i + ")");
        }
        
        // 3. Process orders
        System.out.println("\n3. Processing 10,000 orders...");
        String[] symbols = {"AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "META", "NVDA", "AMD"};
        
        long startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            String symbol = symbols[i % symbols.length];
            long orderId = 1000 + i;
            
            OrderEvent order = OrderEvent.newOrder(
                orderId,
                symbol,
                OrderEvent.SIDE_BUY,
                OrderEvent.TYPE_LIMIT,
                100L,
                15000L + (i % 100),
                999L,
                1
            );
            
            Event event = Event.create(
                System.nanoTime(),
                orderId,
                SourceId.REST_GATEWAY,
                EventType.ORDER_SUBMITTED,
                0L,
                order
            );
            
            eventBus.publish(event);
        }
        
        // Wait for processing
        Thread.sleep(500);
        long elapsedNs = System.nanoTime() - startTime;
        
        System.out.println("   Processing completed in " + 
            TimeUnit.NANOSECONDS.toMillis(elapsedNs) + " ms");
        
        // 4. Show metrics
        System.out.println("\n4. Per-shard metrics:");
        Map<ShardId, ShardMetrics> metrics = manager.getMetrics();
        for (Map.Entry<ShardId, ShardMetrics> entry : metrics.entrySet()) {
            ShardMetrics m = entry.getValue();
            System.out.printf("   %s: %d orders processed%n",
                entry.getKey(), m.getOrdersProcessed());
        }
        
        // 5. Show load distribution
        System.out.println("\n5. Load distribution:");
        ShardManager.LoadDistribution dist = manager.getLoadDistribution();
        System.out.printf("   Total orders: %d%n", dist.totalOrders());
        System.out.printf("   Min shard: %d orders%n", dist.minShardOrders());
        System.out.printf("   Max shard: %d orders%n", dist.maxShardOrders());
        System.out.printf("   Avg shard: %.1f orders%n", dist.avgShardOrders());
        System.out.printf("   Balance factor: %.2f (1.0 = perfect)%n", dist.balanceFactor());
        
        // 6. Scale out - add 2 more shards
        System.out.println("\n6. Scaling out - adding 2 more shards...");
        manager.addShard(new ShardId(4), 4, Set.of());
        manager.addShard(new ShardId(5), 5, Set.of());
        System.out.println("   Cluster now has " + manager.listShards().size() + " shards");
        
        // 7. Process more orders
        System.out.println("\n7. Processing 5,000 more orders...");
        for (int i = 10000; i < 15000; i++) {
            String symbol = symbols[i % symbols.length];
            long orderId = 1000 + i;
            
            OrderEvent order = OrderEvent.newOrder(
                orderId,
                symbol,
                OrderEvent.SIDE_BUY,
                OrderEvent.TYPE_LIMIT,
                100L,
                15000L + (i % 100),
                999L,
                1
            );
            
            Event event = Event.create(
                System.nanoTime(),
                orderId,
                SourceId.REST_GATEWAY,
                EventType.ORDER_SUBMITTED,
                0L,
                order
            );
            
            eventBus.publish(event);
        }
        
        Thread.sleep(300);
        
        // 8. Show updated metrics
        System.out.println("\n8. Updated metrics after scale-out:");
        metrics = manager.getMetrics();
        for (Map.Entry<ShardId, ShardMetrics> entry : metrics.entrySet()) {
            ShardMetrics m = entry.getValue();
            System.out.printf("   %s: %d orders processed%n",
                entry.getKey(), m.getOrdersProcessed());
        }
        
        dist = manager.getLoadDistribution();
        System.out.printf("\n   Updated balance factor: %.2f%n", dist.balanceFactor());
        
        // 9. Demonstrate recovery
        System.out.println("\n9. Demonstrating shard recovery...");
        Optional<Shard> shard0 = manager.getShard(new ShardId(0));
        if (shard0.isPresent()) {
            Shard shard = shard0.get();
            long sequenceBefore = shard.getCurrentSequence();
            System.out.println("   Shard 0 sequence before recovery: " + sequenceBefore);
            
            shard.recover(sequenceBefore, eventBus);
            System.out.println("   Recovery completed in " + 
                shard.getMetrics().getRecoveryTimeMs() + " ms");
            System.out.println("   Status: " + shard.getStatus());
        }
        
        // 10. Cleanup
        System.out.println("\n10. Shutting down...");
        manager.stop();
        eventBus.stop();
        
        System.out.println("\n=== Example Complete ===");
    }
    
    /**
     * Simple synchronous event bus for demonstration.
     */
    static class SimpleEventBus implements EventBus {
        private final Map<Integer, List<EventHandler<?>>> handlers = new HashMap<>();
        private volatile boolean running = false;
        
        @Override
        public synchronized boolean publish(Event event) {
            if (!running) return false;
            
            List<EventHandler<?>> eventHandlers = handlers.get(event.eventType());
            if (eventHandlers != null) {
                for (EventHandler<?> handler : eventHandlers) {
                    try {
                        @SuppressWarnings("unchecked")
                        EventHandler<Object> h = (EventHandler<Object>) handler;
                        h.onEvent(event);
                    } catch (Exception e) {
                        System.err.println("Error processing event: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            return true;
        }
        
        @Override
        public synchronized Subscription subscribe(int eventType, EventHandler<?> handler) {
            handlers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
            return new Subscription() {
                @Override
                public void unsubscribe() {
                    synchronized (SimpleEventBus.this) {
                        List<EventHandler<?>> list = handlers.get(eventType);
                        if (list != null) {
                            list.remove(handler);
                        }
                    }
                }
                
                @Override
                public boolean isActive() {
                    synchronized (SimpleEventBus.this) {
                        List<EventHandler<?>> list = handlers.get(eventType);
                        return list != null && list.contains(handler);
                    }
                }
            };
        }
        
        @Override
        public long getPublishedEventCount() {
            return 0;
        }
        
        @Override
        public int getSubscriberCount(int eventType) {
            List<EventHandler<?>> list = handlers.get(eventType);
            return list != null ? list.size() : 0;
        }
        
        @Override
        public void start() {
            running = true;
        }
        
        @Override
        public void stop() {
            running = false;
        }
    }
}
