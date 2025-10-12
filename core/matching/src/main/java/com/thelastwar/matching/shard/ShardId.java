package com.thelastwar.matching.shard;

/**
 * Represents a unique identifier for a shard in the matching engine cluster.
 * 
 * Each shard is responsible for a subset of trading instruments and runs
 * on a dedicated CPU core for optimal cache locality.
 */
public record ShardId(int id) implements Comparable<ShardId> {
    
    public ShardId {
        if (id < 0) {
            throw new IllegalArgumentException("Shard ID must be non-negative");
        }
    }
    
    @Override
    public int compareTo(ShardId other) {
        return Integer.compare(this.id, other.id);
    }
    
    @Override
    public String toString() {
        return "Shard-" + id;
    }
}
