package com.thelastwar.matching.shard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Tests for ConsistentHashRouter ensuring even distribution and minimal reassignment.
 */
class ConsistentHashRouterTest {
    
    @Test
    void testSingleShardRouting() {
        ConsistentHashRouter router = new ConsistentHashRouter();
        ShardId shard0 = new ShardId(0);
        
        List<ShardInfo> shards = List.of(
            new ShardInfo(shard0, 0, Set.of(), ShardStatus.ACTIVE, 0)
        );
        
        router.updateTopology(shards);
        
        assertEquals(1, router.getShardCount());
        assertEquals(shard0, router.routeToShard("AAPL"));
        assertEquals(shard0, router.routeToShard("GOOGL"));
        assertEquals(shard0, router.routeToShard("MSFT"));
    }
    
    @Test
    void testMultipleShardRouting() {
        ConsistentHashRouter router = new ConsistentHashRouter();
        ShardId shard0 = new ShardId(0);
        ShardId shard1 = new ShardId(1);
        
        List<ShardInfo> shards = List.of(
            new ShardInfo(shard0, 0, Set.of(), ShardStatus.ACTIVE, 0),
            new ShardInfo(shard1, 1, Set.of(), ShardStatus.ACTIVE, 0)
        );
        
        router.updateTopology(shards);
        
        assertEquals(2, router.getShardCount());
        
        // Each instrument should consistently route to the same shard
        ShardId apple1 = router.routeToShard("AAPL");
        ShardId apple2 = router.routeToShard("AAPL");
        assertEquals(apple1, apple2);
    }
    
    @Test
    void testEvenDistribution() {
        ConsistentHashRouter router = new ConsistentHashRouter();
        int numShards = 4;
        
        List<ShardInfo> shards = new ArrayList<>();
        for (int i = 0; i < numShards; i++) {
            shards.add(new ShardInfo(new ShardId(i), i, Set.of(), ShardStatus.ACTIVE, 0));
        }
        
        router.updateTopology(shards);
        
        // Generate many instruments and check distribution
        Map<ShardId, Integer> distribution = new HashMap<>();
        int numInstruments = 1000;
        
        for (int i = 0; i < numInstruments; i++) {
            String instrument = "SYM" + i;
            ShardId shard = router.routeToShard(instrument);
            distribution.merge(shard, 1, Integer::sum);
        }
        
        // Check that all shards got some instruments
        assertEquals(numShards, distribution.size());
        
        // Check that distribution is reasonably even (within 30% of average)
        double expected = numInstruments / (double) numShards;
        for (int count : distribution.values()) {
            assertTrue(count > expected * 0.7, "Shard has too few instruments: " + count);
            assertTrue(count < expected * 1.3, "Shard has too many instruments: " + count);
        }
    }
    
    @Test
    void testMinimalReassignment() {
        ConsistentHashRouter router = new ConsistentHashRouter();
        
        // Start with 3 shards
        List<ShardInfo> initialShards = List.of(
            new ShardInfo(new ShardId(0), 0, Set.of(), ShardStatus.ACTIVE, 0),
            new ShardInfo(new ShardId(1), 1, Set.of(), ShardStatus.ACTIVE, 0),
            new ShardInfo(new ShardId(2), 2, Set.of(), ShardStatus.ACTIVE, 0)
        );
        
        router.updateTopology(initialShards);
        
        // Map 100 instruments
        Map<String, ShardId> initialMapping = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            String instrument = "SYM" + i;
            initialMapping.put(instrument, router.routeToShard(instrument));
        }
        
        // Add a 4th shard
        List<ShardInfo> expandedShards = new ArrayList<>(initialShards);
        expandedShards.add(new ShardInfo(new ShardId(3), 3, Set.of(), ShardStatus.ACTIVE, 0));
        
        router.updateTopology(expandedShards);
        
        // Check how many instruments moved
        int moved = 0;
        for (Map.Entry<String, ShardId> entry : initialMapping.entrySet()) {
            ShardId newShard = router.routeToShard(entry.getKey());
            if (!newShard.equals(entry.getValue())) {
                moved++;
            }
        }
        
        // With consistent hashing, approximately 25% should move (100/4)
        // Allow some variance
        assertTrue(moved > 15, "Too few instruments moved: " + moved);
        assertTrue(moved < 35, "Too many instruments moved: " + moved);
    }
    
    @Test
    void testIgnoresNonActiveShards() {
        ConsistentHashRouter router = new ConsistentHashRouter();
        
        List<ShardInfo> shards = List.of(
            new ShardInfo(new ShardId(0), 0, Set.of(), ShardStatus.ACTIVE, 0),
            new ShardInfo(new ShardId(1), 1, Set.of(), ShardStatus.STOPPED, 0),
            new ShardInfo(new ShardId(2), 2, Set.of(), ShardStatus.DRAINING, 0),
            new ShardInfo(new ShardId(3), 3, Set.of(), ShardStatus.RECOVERING, 0)
        );
        
        router.updateTopology(shards);
        
        // Should only count ACTIVE and RECOVERING shards
        assertEquals(2, router.getShardCount());
    }
    
    @Test
    void testEmptyInstrumentThrows() {
        ConsistentHashRouter router = new ConsistentHashRouter();
        List<ShardInfo> shards = List.of(
            new ShardInfo(new ShardId(0), 0, Set.of(), ShardStatus.ACTIVE, 0)
        );
        router.updateTopology(shards);
        
        assertThrows(IllegalArgumentException.class, () -> router.routeToShard(""));
        assertThrows(IllegalArgumentException.class, () -> router.routeToShard(null));
    }
    
    @Test
    void testNoShardsThrows() {
        ConsistentHashRouter router = new ConsistentHashRouter();
        router.updateTopology(List.of());
        
        assertThrows(IllegalStateException.class, () -> router.routeToShard("AAPL"));
    }
    
    @Test
    void testConsistentRoutingAcrossUpdates() {
        ConsistentHashRouter router = new ConsistentHashRouter();
        
        List<ShardInfo> shards = List.of(
            new ShardInfo(new ShardId(0), 0, Set.of(), ShardStatus.ACTIVE, 0),
            new ShardInfo(new ShardId(1), 1, Set.of(), ShardStatus.ACTIVE, 0)
        );
        
        // Update topology multiple times
        router.updateTopology(shards);
        ShardId first = router.routeToShard("AAPL");
        
        router.updateTopology(shards);
        ShardId second = router.routeToShard("AAPL");
        
        router.updateTopology(shards);
        ShardId third = router.routeToShard("AAPL");
        
        // Should always route to the same shard
        assertEquals(first, second);
        assertEquals(second, third);
    }
}
