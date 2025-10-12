package com.thelastwar.matching.shard;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics for monitoring shard performance and load distribution.
 * 
 * Tracks:
 * - Per-shard order processing throughput
 * - Load distribution across shards
 * - Shard health and recovery status
 * - Rebalancing operations
 */
public class ShardMetrics {
    
    private final ShardId shardId;
    private final LongAdder ordersProcessed;
    private final LongAdder tradesGenerated;
    private final LongAdder cancelsProcessed;
    private final LongAdder modifiesProcessed;
    private final AtomicLong lastProcessedTimestamp;
    private final AtomicLong recoveryStartTime;
    private final AtomicLong recoveryEndTime;
    
    public ShardMetrics(ShardId shardId) {
        this.shardId = shardId;
        this.ordersProcessed = new LongAdder();
        this.tradesGenerated = new LongAdder();
        this.cancelsProcessed = new LongAdder();
        this.modifiesProcessed = new LongAdder();
        this.lastProcessedTimestamp = new AtomicLong(0);
        this.recoveryStartTime = new AtomicLong(0);
        this.recoveryEndTime = new AtomicLong(0);
    }
    
    public ShardId getShardId() {
        return shardId;
    }
    
    public long getOrdersProcessed() {
        return ordersProcessed.sum();
    }
    
    public long getTradesGenerated() {
        return tradesGenerated.sum();
    }
    
    public long getCancelsProcessed() {
        return cancelsProcessed.sum();
    }
    
    public long getModifiesProcessed() {
        return modifiesProcessed.sum();
    }
    
    public long getLastProcessedTimestamp() {
        return lastProcessedTimestamp.get();
    }
    
    public long getRecoveryTimeMs() {
        long start = recoveryStartTime.get();
        long end = recoveryEndTime.get();
        return (start > 0 && end > start) ? (end - start) / 1_000_000 : 0;
    }
    
    public void recordOrderProcessed() {
        ordersProcessed.increment();
        lastProcessedTimestamp.set(System.nanoTime());
    }
    
    public void recordTradeGenerated() {
        tradesGenerated.increment();
    }
    
    public void recordCancelProcessed() {
        cancelsProcessed.increment();
        lastProcessedTimestamp.set(System.nanoTime());
    }
    
    public void recordModifyProcessed() {
        modifiesProcessed.increment();
        lastProcessedTimestamp.set(System.nanoTime());
    }
    
    public void startRecovery() {
        recoveryStartTime.set(System.nanoTime());
        recoveryEndTime.set(0);
    }
    
    public void endRecovery() {
        recoveryEndTime.set(System.nanoTime());
    }
    
    public void reset() {
        ordersProcessed.reset();
        tradesGenerated.reset();
        cancelsProcessed.reset();
        modifiesProcessed.reset();
        lastProcessedTimestamp.set(0);
        recoveryStartTime.set(0);
        recoveryEndTime.set(0);
    }
    
    @Override
    public String toString() {
        return String.format(
            "ShardMetrics[shard=%s, orders=%d, trades=%d, cancels=%d, modifies=%d, recoveryTimeMs=%d]",
            shardId, getOrdersProcessed(), getTradesGenerated(), 
            getCancelsProcessed(), getModifiesProcessed(), getRecoveryTimeMs()
        );
    }
}
