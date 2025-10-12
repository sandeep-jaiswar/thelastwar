package com.thelastwar.matching.shard;

import java.util.List;
import java.util.Optional;

/**
 * Registry for managing shard membership in the matching engine cluster.
 * 
 * This interface abstracts cluster coordination mechanisms like etcd, ZooKeeper,
 * or Consul. Implementations should provide:
 * - Shard registration and discovery
 * - Health monitoring
 * - Leader election for rebalancing decisions
 * - Atomic updates for consistency
 * 
 * Thread Safety: All methods must be thread-safe.
 */
public interface ShardRegistry {
    
    /**
     * Registers a new shard in the cluster.
     * 
     * @param shardInfo Information about the shard to register
     * @throws IllegalStateException if the shard is already registered
     */
    void registerShard(ShardInfo shardInfo);
    
    /**
     * Updates shard information in the registry.
     * 
     * @param shardInfo Updated shard information
     * @throws IllegalArgumentException if the shard is not registered
     */
    void updateShard(ShardInfo shardInfo);
    
    /**
     * Unregisters a shard from the cluster.
     * 
     * @param shardId ID of the shard to unregister
     */
    void unregisterShard(ShardId shardId);
    
    /**
     * Gets information about a specific shard.
     * 
     * @param shardId ID of the shard
     * @return ShardInfo if found, empty otherwise
     */
    Optional<ShardInfo> getShard(ShardId shardId);
    
    /**
     * Lists all active shards in the cluster.
     * 
     * @return List of all registered shards
     */
    List<ShardInfo> listShards();
    
    /**
     * Lists all active shards (excluding STOPPED and DRAINING).
     * 
     * @return List of active shards
     */
    default List<ShardInfo> listActiveShards() {
        return listShards().stream()
            .filter(info -> info.status() == ShardStatus.ACTIVE || 
                           info.status() == ShardStatus.RECOVERING)
            .toList();
    }
    
    /**
     * Finds which shard is responsible for a given instrument.
     * 
     * @param instrument Trading instrument symbol
     * @return ShardId responsible for the instrument, empty if not assigned
     */
    Optional<ShardId> getShardForInstrument(String instrument);
    
    /**
     * Adds a watch for shard changes in the cluster.
     * 
     * @param listener Listener to be notified of changes
     */
    void addShardChangeListener(ShardChangeListener listener);
    
    /**
     * Removes a shard change listener.
     * 
     * @param listener Listener to remove
     */
    void removeShardChangeListener(ShardChangeListener listener);
    
    /**
     * Listener for shard topology changes.
     */
    interface ShardChangeListener {
        /**
         * Called when a new shard joins the cluster.
         */
        void onShardAdded(ShardInfo shardInfo);
        
        /**
         * Called when a shard's information is updated.
         */
        void onShardUpdated(ShardInfo shardInfo);
        
        /**
         * Called when a shard leaves the cluster.
         */
        void onShardRemoved(ShardId shardId);
    }
}
