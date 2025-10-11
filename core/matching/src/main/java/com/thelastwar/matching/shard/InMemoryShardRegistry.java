package com.thelastwar.matching.shard;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory implementation of ShardRegistry for development and testing.
 * 
 * This implementation:
 * - Stores shard information in memory (not persistent)
 * - Is thread-safe using concurrent collections
 * - Supports shard change notifications
 * - Suitable for single-node deployments
 * 
 * For production multi-node deployments, use a distributed registry
 * implementation backed by etcd, ZooKeeper, or Consul.
 */
public class InMemoryShardRegistry implements ShardRegistry {
    
    private final ConcurrentHashMap<ShardId, ShardInfo> shards;
    private final CopyOnWriteArrayList<ShardChangeListener> listeners;
    
    public InMemoryShardRegistry() {
        this.shards = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }
    
    @Override
    public void registerShard(ShardInfo shardInfo) {
        ShardInfo existing = shards.putIfAbsent(shardInfo.shardId(), shardInfo);
        if (existing != null) {
            throw new IllegalStateException(
                "Shard " + shardInfo.shardId() + " is already registered"
            );
        }
        notifyShardAdded(shardInfo);
    }
    
    @Override
    public void updateShard(ShardInfo shardInfo) {
        ShardInfo existing = shards.replace(shardInfo.shardId(), shardInfo);
        if (existing == null) {
            throw new IllegalArgumentException(
                "Shard " + shardInfo.shardId() + " is not registered"
            );
        }
        notifyShardUpdated(shardInfo);
    }
    
    @Override
    public void unregisterShard(ShardId shardId) {
        ShardInfo removed = shards.remove(shardId);
        if (removed != null) {
            notifyShardRemoved(shardId);
        }
    }
    
    @Override
    public java.util.Optional<ShardInfo> getShard(ShardId shardId) {
        return java.util.Optional.ofNullable(shards.get(shardId));
    }
    
    @Override
    public List<ShardInfo> listShards() {
        return List.copyOf(shards.values());
    }
    
    @Override
    public java.util.Optional<ShardId> getShardForInstrument(String instrument) {
        // Find the shard that contains this instrument
        return shards.values().stream()
            .filter(info -> info.instruments().contains(instrument))
            .map(ShardInfo::shardId)
            .findFirst();
    }
    
    @Override
    public void addShardChangeListener(ShardChangeListener listener) {
        listeners.add(listener);
    }
    
    @Override
    public void removeShardChangeListener(ShardChangeListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyShardAdded(ShardInfo shardInfo) {
        for (ShardChangeListener listener : listeners) {
            try {
                listener.onShardAdded(shardInfo);
            } catch (Exception e) {
                // Log but don't propagate listener exceptions
                System.err.println("Error notifying listener of shard addition: " + e.getMessage());
            }
        }
    }
    
    private void notifyShardUpdated(ShardInfo shardInfo) {
        for (ShardChangeListener listener : listeners) {
            try {
                listener.onShardUpdated(shardInfo);
            } catch (Exception e) {
                System.err.println("Error notifying listener of shard update: " + e.getMessage());
            }
        }
    }
    
    private void notifyShardRemoved(ShardId shardId) {
        for (ShardChangeListener listener : listeners) {
            try {
                listener.onShardRemoved(shardId);
            } catch (Exception e) {
                System.err.println("Error notifying listener of shard removal: " + e.getMessage());
            }
        }
    }
}
