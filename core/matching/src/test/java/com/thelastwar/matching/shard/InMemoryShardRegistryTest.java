package com.thelastwar.matching.shard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Tests for InMemoryShardRegistry ensuring thread-safe shard management.
 */
class InMemoryShardRegistryTest {
    
    private InMemoryShardRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new InMemoryShardRegistry();
    }
    
    @Test
    void testRegisterShard() {
        ShardId shardId = new ShardId(0);
        ShardInfo info = new ShardInfo(shardId, 0, Set.of("AAPL"), ShardStatus.ACTIVE, 0);
        
        registry.registerShard(info);
        
        Optional<ShardInfo> retrieved = registry.getShard(shardId);
        assertTrue(retrieved.isPresent());
        assertEquals(info, retrieved.get());
    }
    
    @Test
    void testRegisterDuplicateShardThrows() {
        ShardId shardId = new ShardId(0);
        ShardInfo info = new ShardInfo(shardId, 0, Set.of(), ShardStatus.ACTIVE, 0);
        
        registry.registerShard(info);
        
        assertThrows(IllegalStateException.class, () -> registry.registerShard(info));
    }
    
    @Test
    void testUpdateShard() {
        ShardId shardId = new ShardId(0);
        ShardInfo initial = new ShardInfo(shardId, 0, Set.of("AAPL"), ShardStatus.INITIALIZING, 0);
        
        registry.registerShard(initial);
        
        ShardInfo updated = initial.withStatus(ShardStatus.ACTIVE);
        registry.updateShard(updated);
        
        Optional<ShardInfo> retrieved = registry.getShard(shardId);
        assertTrue(retrieved.isPresent());
        assertEquals(ShardStatus.ACTIVE, retrieved.get().status());
    }
    
    @Test
    void testUpdateNonexistentShardThrows() {
        ShardId shardId = new ShardId(0);
        ShardInfo info = new ShardInfo(shardId, 0, Set.of(), ShardStatus.ACTIVE, 0);
        
        assertThrows(IllegalArgumentException.class, () -> registry.updateShard(info));
    }
    
    @Test
    void testUnregisterShard() {
        ShardId shardId = new ShardId(0);
        ShardInfo info = new ShardInfo(shardId, 0, Set.of(), ShardStatus.ACTIVE, 0);
        
        registry.registerShard(info);
        registry.unregisterShard(shardId);
        
        Optional<ShardInfo> retrieved = registry.getShard(shardId);
        assertFalse(retrieved.isPresent());
    }
    
    @Test
    void testListShards() {
        ShardInfo shard0 = new ShardInfo(new ShardId(0), 0, Set.of(), ShardStatus.ACTIVE, 0);
        ShardInfo shard1 = new ShardInfo(new ShardId(1), 1, Set.of(), ShardStatus.ACTIVE, 0);
        ShardInfo shard2 = new ShardInfo(new ShardId(2), 2, Set.of(), ShardStatus.STOPPED, 0);
        
        registry.registerShard(shard0);
        registry.registerShard(shard1);
        registry.registerShard(shard2);
        
        List<ShardInfo> allShards = registry.listShards();
        assertEquals(3, allShards.size());
    }
    
    @Test
    void testListActiveShards() {
        ShardInfo shard0 = new ShardInfo(new ShardId(0), 0, Set.of(), ShardStatus.ACTIVE, 0);
        ShardInfo shard1 = new ShardInfo(new ShardId(1), 1, Set.of(), ShardStatus.RECOVERING, 0);
        ShardInfo shard2 = new ShardInfo(new ShardId(2), 2, Set.of(), ShardStatus.STOPPED, 0);
        ShardInfo shard3 = new ShardInfo(new ShardId(3), 3, Set.of(), ShardStatus.DRAINING, 0);
        
        registry.registerShard(shard0);
        registry.registerShard(shard1);
        registry.registerShard(shard2);
        registry.registerShard(shard3);
        
        List<ShardInfo> activeShards = registry.listActiveShards();
        assertEquals(2, activeShards.size());
    }
    
    @Test
    void testGetShardForInstrument() {
        ShardId shard0 = new ShardId(0);
        ShardId shard1 = new ShardId(1);
        
        ShardInfo info0 = new ShardInfo(shard0, 0, Set.of("AAPL", "GOOGL"), ShardStatus.ACTIVE, 0);
        ShardInfo info1 = new ShardInfo(shard1, 1, Set.of("MSFT", "AMZN"), ShardStatus.ACTIVE, 0);
        
        registry.registerShard(info0);
        registry.registerShard(info1);
        
        assertEquals(Optional.of(shard0), registry.getShardForInstrument("AAPL"));
        assertEquals(Optional.of(shard0), registry.getShardForInstrument("GOOGL"));
        assertEquals(Optional.of(shard1), registry.getShardForInstrument("MSFT"));
        assertEquals(Optional.of(shard1), registry.getShardForInstrument("AMZN"));
        assertEquals(Optional.empty(), registry.getShardForInstrument("TSLA"));
    }
    
    @Test
    void testShardChangeListeners() throws InterruptedException {
        CountDownLatch addedLatch = new CountDownLatch(1);
        CountDownLatch updatedLatch = new CountDownLatch(1);
        CountDownLatch removedLatch = new CountDownLatch(1);
        
        List<ShardInfo> addedShards = new ArrayList<>();
        List<ShardInfo> updatedShards = new ArrayList<>();
        List<ShardId> removedShards = new ArrayList<>();
        
        ShardRegistry.ShardChangeListener listener = new ShardRegistry.ShardChangeListener() {
            @Override
            public void onShardAdded(ShardInfo shardInfo) {
                addedShards.add(shardInfo);
                addedLatch.countDown();
            }
            
            @Override
            public void onShardUpdated(ShardInfo shardInfo) {
                updatedShards.add(shardInfo);
                updatedLatch.countDown();
            }
            
            @Override
            public void onShardRemoved(ShardId shardId) {
                removedShards.add(shardId);
                removedLatch.countDown();
            }
        };
        
        registry.addShardChangeListener(listener);
        
        ShardId shardId = new ShardId(0);
        ShardInfo info = new ShardInfo(shardId, 0, Set.of(), ShardStatus.INITIALIZING, 0);
        
        // Test add notification
        registry.registerShard(info);
        assertTrue(addedLatch.await(1, TimeUnit.SECONDS));
        assertEquals(1, addedShards.size());
        assertEquals(info, addedShards.get(0));
        
        // Test update notification
        ShardInfo updated = info.withStatus(ShardStatus.ACTIVE);
        registry.updateShard(updated);
        assertTrue(updatedLatch.await(1, TimeUnit.SECONDS));
        assertEquals(1, updatedShards.size());
        assertEquals(updated, updatedShards.get(0));
        
        // Test remove notification
        registry.unregisterShard(shardId);
        assertTrue(removedLatch.await(1, TimeUnit.SECONDS));
        assertEquals(1, removedShards.size());
        assertEquals(shardId, removedShards.get(0));
        
        registry.removeShardChangeListener(listener);
    }
    
    @Test
    void testConcurrentAccess() throws InterruptedException {
        int numThreads = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int t = 0; t < numThreads; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        ShardId shardId = new ShardId(threadId * operationsPerThread + i);
                        ShardInfo info = new ShardInfo(shardId, 0, Set.of(), ShardStatus.ACTIVE, 0);
                        
                        registry.registerShard(info);
                        registry.getShard(shardId);
                        registry.listShards();
                        registry.unregisterShard(shardId);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        
        // All shards should be unregistered
        assertEquals(0, registry.listShards().size());
        
        executor.shutdown();
    }
}
