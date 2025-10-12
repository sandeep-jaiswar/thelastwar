package com.thelastwar.matching.shard;

import com.thelastwar.matching.IMatchingEngine;
import com.thelastwar.matching.MatchingEngine;
import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Integration tests for ShardManager ensuring proper routing and scaling.
 */
class ShardManagerTest {
    
    private EventBus eventBus;
    private ShardRegistry registry;
    private ShardRouter router;
    private ShardManager manager;
    
    @BeforeEach
    void setUp() {
        eventBus = new InMemoryEventBus();
        eventBus.start();
        registry = new InMemoryShardRegistry();
        router = new ConsistentHashRouter();
        manager = new ShardManager(eventBus, registry, router);
    }
    
    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
    }
    
    @Test
    void testAddSingleShard() {
        manager.start();
        
        Set<String> instruments = Set.of("AAPL", "GOOGL");
        Shard shard = manager.addShard(new ShardId(0), 0, instruments);
        
        assertNotNull(shard);
        assertEquals(new ShardId(0), shard.getShardId());
        assertEquals(ShardStatus.ACTIVE, shard.getStatus());
        assertEquals(instruments, shard.getAssignedInstruments());
    }
    
    @Test
    void testAddMultipleShards() {
        manager.start();
        
        manager.addShard(new ShardId(0), 0, Set.of("AAPL", "GOOGL"));
        manager.addShard(new ShardId(1), 1, Set.of("MSFT", "AMZN"));
        manager.addShard(new ShardId(2), 2, Set.of("TSLA", "META"));
        
        List<Shard> shards = manager.listShards();
        assertEquals(3, shards.size());
    }
    
    @Test
    void testRemoveShard() {
        manager.start();
        
        ShardId shardId = new ShardId(0);
        manager.addShard(shardId, 0, Set.of("AAPL"));
        
        assertEquals(1, manager.listShards().size());
        
        manager.removeShard(shardId);
        
        assertEquals(0, manager.listShards().size());
        assertFalse(manager.getShard(shardId).isPresent());
    }
    
    @Test
    void testGetShard() {
        manager.start();
        
        ShardId shardId = new ShardId(0);
        manager.addShard(shardId, 0, Set.of("AAPL"));
        
        Optional<Shard> retrieved = manager.getShard(shardId);
        assertTrue(retrieved.isPresent());
        assertEquals(shardId, retrieved.get().getShardId());
    }
    
    @Test
    void testMetricsAggregation() throws InterruptedException {
        manager.start();
        
        // Add shards with empty instrument sets (router will use consistent hashing)
        manager.addShard(new ShardId(0), 0, Set.of());
        manager.addShard(new ShardId(1), 1, Set.of());
        manager.addShard(new ShardId(2), 2, Set.of());
        
        // Process orders for different instruments
        for (int i = 0; i < 30; i++) {
            publishOrder("SYM" + i, 1000 + i);
        }
        
        // Wait for processing
        Thread.sleep(200);
        
        // Check metrics - at least some orders should be processed
        Map<ShardId, ShardMetrics> metrics = manager.getMetrics();
        assertEquals(3, metrics.size());
        
        // Verify total orders processed across all shards
        long totalOrders = metrics.values().stream()
            .mapToLong(ShardMetrics::getOrdersProcessed)
            .sum();
        assertTrue(totalOrders > 0, "Expected some orders processed, got " + totalOrders);
    }
    
    @Test
    void testLoadDistribution() throws InterruptedException {
        manager.start();
        
        // Add 4 shards
        for (int i = 0; i < 4; i++) {
            manager.addShard(new ShardId(i), i, Set.of());
        }
        
        // Process many orders to test distribution
        for (int i = 0; i < 100; i++) {
            publishOrder("SYM" + i, 1000 + i);
        }
        
        Thread.sleep(300);
        
        ShardManager.LoadDistribution dist = manager.getLoadDistribution();
        
        // Check basic statistics - at least some orders should be processed
        assertTrue(dist.totalOrders() > 0, "Expected some orders processed");
        
        // If orders were processed, balance factor should be reasonable
        if (dist.totalOrders() > 20) {
            assertTrue(dist.balanceFactor() > 0.3, 
                "Balance factor too low: " + dist.balanceFactor());
        }
    }
    
    @Test
    void testDynamicScaling() throws InterruptedException {
        manager.start();
        
        // Start with 2 shards
        manager.addShard(new ShardId(0), 0, Set.of());
        manager.addShard(new ShardId(1), 1, Set.of());
        
        // Process some orders
        for (int i = 0; i < 50; i++) {
            publishOrder("SYM" + i, 1000 + i);
        }
        
        Thread.sleep(100);
        
        ShardManager.LoadDistribution initialDist = manager.getLoadDistribution();
        long initialTotal = initialDist.totalOrders();
        
        // Add 2 more shards for scale-out
        manager.addShard(new ShardId(2), 2, Set.of());
        manager.addShard(new ShardId(3), 3, Set.of());
        
        // Process more orders
        for (int i = 50; i < 100; i++) {
            publishOrder("SYM" + i, 1000 + i);
        }
        
        Thread.sleep(100);
        
        ShardManager.LoadDistribution finalDist = manager.getLoadDistribution();
        
        // Verify total orders increased
        assertTrue(finalDist.totalOrders() > initialTotal);
        
        // Verify all 4 shards are now active
        assertEquals(4, manager.listShards().size());
    }
    
    @Test
    void testShardRecovery() {
        manager.start();
        
        Shard shard = manager.addShard(new ShardId(0), 0, Set.of("AAPL"));
        
        // Simulate recovery
        shard.recover(0, eventBus);
        
        assertEquals(ShardStatus.ACTIVE, shard.getStatus());
        assertTrue(shard.getMetrics().getRecoveryTimeMs() >= 0);
    }
    
    private void publishOrder(String symbol, long orderId) {
        OrderEvent order = OrderEvent.newOrder(
            orderId,
            symbol,
            OrderEvent.SIDE_BUY,
            OrderEvent.TYPE_LIMIT,
            100L,
            10000L,
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
    
    /**
     * Simple in-memory event bus for testing.
     */
    static class InMemoryEventBus implements EventBus {
        private final Map<Integer, List<EventHandler<?>>> handlers = new ConcurrentHashMap<>();
        private volatile boolean running = false;
        private final ExecutorService executor = Executors.newFixedThreadPool(4);
        
        @Override
        public boolean publish(Event event) {
            if (!running) return false;
            
            List<EventHandler<?>> eventHandlers = handlers.get(event.eventType());
            if (eventHandlers != null) {
                for (EventHandler<?> handler : eventHandlers) {
                    executor.submit(() -> {
                        try {
                            @SuppressWarnings("unchecked")
                            EventHandler<Object> h = (EventHandler<Object>) handler;
                            h.onEvent(event);
                        } catch (Exception e) {
                            // Ignore
                        }
                    });
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
            executor.shutdown();
        }
    }
}
