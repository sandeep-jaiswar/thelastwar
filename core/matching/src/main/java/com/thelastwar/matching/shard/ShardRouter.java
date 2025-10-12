package com.thelastwar.matching.shard;

/**
 * Routes orders to appropriate shards based on instrument symbol.
 * 
 * Uses consistent hashing to ensure:
 * - Even distribution of instruments across shards
 * - Minimal reassignment when shards are added/removed
 * - Deterministic routing (same instrument always goes to same shard)
 * 
 * Thread Safety: All methods must be thread-safe.
 */
public interface ShardRouter {
    
    /**
     * Determines which shard should handle an order for the given instrument.
     * 
     * @param instrument Trading instrument symbol (e.g., "AAPL", "BTCUSD")
     * @return ShardId that should handle this instrument
     * @throws IllegalStateException if no shards are available
     */
    ShardId routeToShard(String instrument);
    
    /**
     * Updates the routing table based on current shard topology.
     * Should be called when shards are added or removed.
     * 
     * @param shardInfos Current list of active shards
     */
    void updateTopology(java.util.List<ShardInfo> shardInfos);
    
    /**
     * Gets the total number of shards in the routing table.
     * 
     * @return Number of active shards
     */
    int getShardCount();
}
