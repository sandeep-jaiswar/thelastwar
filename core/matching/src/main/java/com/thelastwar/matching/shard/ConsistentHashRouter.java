package com.thelastwar.matching.shard;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Consistent hashing-based router for distributing instruments across shards.
 * 
 * Uses a hash ring with virtual nodes to ensure:
 * - Even distribution of load across shards
 * - Minimal reassignment (only K/N keys move when adding/removing shards)
 * - Deterministic routing for replay consistency
 * 
 * Algorithm:
 * 1. Each shard is placed on the hash ring multiple times (virtual nodes)
 * 2. Instruments are hashed and placed on the ring
 * 3. An instrument is routed to the next shard clockwise on the ring
 * 
 * Thread Safety: Uses read-write lock for concurrent access.
 */
public class ConsistentHashRouter implements ShardRouter {
    
    private static final int VIRTUAL_NODES_PER_SHARD = 150;
    
    private final ReadWriteLock lock;
    private final TreeMap<Integer, ShardId> hashRing;
    private volatile int shardCount;
    
    public ConsistentHashRouter() {
        this.lock = new ReentrantReadWriteLock();
        this.hashRing = new TreeMap<>();
        this.shardCount = 0;
    }
    
    @Override
    public ShardId routeToShard(String instrument) {
        if (instrument == null || instrument.isEmpty()) {
            throw new IllegalArgumentException("Instrument cannot be null or empty");
        }
        
        lock.readLock().lock();
        try {
            if (hashRing.isEmpty()) {
                throw new IllegalStateException("No shards available for routing");
            }
            
            int hash = hashInstrument(instrument);
            
            // Find the next shard on the ring (clockwise)
            SortedMap<Integer, ShardId> tailMap = hashRing.tailMap(hash);
            int key = tailMap.isEmpty() ? hashRing.firstKey() : tailMap.firstKey();
            
            return hashRing.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void updateTopology(List<ShardInfo> shardInfos) {
        lock.writeLock().lock();
        try {
            // Clear existing ring
            hashRing.clear();
            
            // Add each shard with virtual nodes
            for (ShardInfo info : shardInfos) {
                if (info.status() == ShardStatus.ACTIVE || 
                    info.status() == ShardStatus.RECOVERING) {
                    addShardToRing(info.shardId());
                }
            }
            
            shardCount = hashRing.isEmpty() ? 0 : 
                (int) hashRing.values().stream().distinct().count();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public int getShardCount() {
        return shardCount;
    }
    
    /**
     * Adds a shard to the hash ring with virtual nodes.
     */
    private void addShardToRing(ShardId shardId) {
        for (int i = 0; i < VIRTUAL_NODES_PER_SHARD; i++) {
            String virtualNodeKey = shardId.toString() + "-vnode-" + i;
            int hash = hashString(virtualNodeKey);
            hashRing.put(hash, shardId);
        }
    }
    
    /**
     * Hashes an instrument symbol to an integer.
     * Uses a simple but effective hash function for distribution.
     */
    private int hashInstrument(String instrument) {
        return hashString(instrument);
    }
    
    /**
     * Hash function for consistent hashing.
     * Uses MurmurHash3-like algorithm for good distribution.
     */
    private int hashString(String key) {
        int hash = 0;
        for (int i = 0; i < key.length(); i++) {
            hash = 31 * hash + key.charAt(i);
        }
        // Mix the bits for better distribution
        hash ^= (hash >>> 16);
        hash *= 0x85ebca6b;
        hash ^= (hash >>> 13);
        hash *= 0xc2b2ae35;
        hash ^= (hash >>> 16);
        return hash;
    }
}
