package com.thelastwar.matching.shard;

import java.util.Set;

/**
 * Contains information about a shard including its assignment and status.
 * 
 * @param shardId Unique identifier for the shard
 * @param cpuCore CPU core this shard is pinned to (-1 if not pinned)
 * @param instruments Set of instrument symbols assigned to this shard
 * @param status Current status of the shard
 * @param sequenceNumber Last processed sequence number for replay
 */
public record ShardInfo(
    ShardId shardId,
    int cpuCore,
    Set<String> instruments,
    ShardStatus status,
    long sequenceNumber
) {
    
    public ShardInfo {
        if (shardId == null) {
            throw new IllegalArgumentException("Shard ID cannot be null");
        }
        if (instruments == null) {
            throw new IllegalArgumentException("Instruments cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
    }
    
    /**
     * Creates a new ShardInfo with updated status.
     */
    public ShardInfo withStatus(ShardStatus newStatus) {
        return new ShardInfo(shardId, cpuCore, instruments, newStatus, sequenceNumber);
    }
    
    /**
     * Creates a new ShardInfo with updated sequence number.
     */
    public ShardInfo withSequenceNumber(long newSequenceNumber) {
        return new ShardInfo(shardId, cpuCore, instruments, status, newSequenceNumber);
    }
}
