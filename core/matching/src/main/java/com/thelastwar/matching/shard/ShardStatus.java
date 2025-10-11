package com.thelastwar.matching.shard;

/**
 * Status of a shard in the matching engine cluster.
 */
public enum ShardStatus {
    /**
     * Shard is initializing and not yet ready to process orders.
     */
    INITIALIZING,
    
    /**
     * Shard is active and processing orders normally.
     */
    ACTIVE,
    
    /**
     * Shard is recovering from a failure and replaying events.
     */
    RECOVERING,
    
    /**
     * Shard is being drained before shutdown or rebalancing.
     */
    DRAINING,
    
    /**
     * Shard has been stopped and is no longer processing orders.
     */
    STOPPED
}
