package com.thelastwar.matching.shard;

import com.thelastwar.matching.IMatchingEngine;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.model.*;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Wrapper around a MatchingEngine instance representing a single shard.
 * 
 * Responsibilities:
 * - Manages a subset of trading instruments
 * - Tracks shard-specific metrics
 * - Handles recovery and replay
 * - Reports status to ShardRegistry
 * 
 * Thread Safety: Delegates to underlying MatchingEngine (single-threaded).
 */
public class Shard {
    
    private final ShardId shardId;
    private final IMatchingEngine engine;
    private final Set<String> assignedInstruments;
    private final ShardMetrics metrics;
    private volatile ShardStatus status;
    private final int cpuCore;
    
    /**
     * Creates a new shard.
     * 
     * @param shardId Unique identifier for this shard
     * @param engine Matching engine instance for this shard
     * @param cpuCore CPU core to pin this shard to (-1 for no pinning)
     */
    public Shard(ShardId shardId, IMatchingEngine engine, int cpuCore) {
        this.shardId = shardId;
        this.engine = engine;
        this.assignedInstruments = new CopyOnWriteArraySet<>();
        this.metrics = new ShardMetrics(shardId);
        this.status = ShardStatus.INITIALIZING;
        this.cpuCore = cpuCore;
    }
    
    /**
     * Starts the shard and transitions to ACTIVE status.
     */
    public void start() {
        engine.start();
        status = ShardStatus.ACTIVE;
    }
    
    /**
     * Stops the shard gracefully.
     */
    public void stop() {
        status = ShardStatus.DRAINING;
        engine.stop();
        status = ShardStatus.STOPPED;
    }
    
    /**
     * Processes a new order.
     */
    public void processOrder(OrderEnvelope envelope) {
        checkStatus();
        engine.onNewOrder(envelope);
        metrics.recordOrderProcessed();
    }
    
    /**
     * Processes an order cancellation.
     */
    public void processCancel(OrderCancel cancel) {
        checkStatus();
        engine.onCancel(cancel);
        metrics.recordCancelProcessed();
    }
    
    /**
     * Processes an order modification.
     */
    public void processModify(OrderModify modify) {
        checkStatus();
        engine.onReplace(modify);
        metrics.recordModifyProcessed();
    }
    
    /**
     * Processes a market data update.
     */
    public void processMarketData(TickEvent tick) {
        checkStatus();
        engine.onMarketDataUpdate(tick);
    }
    
    /**
     * Assigns an instrument to this shard.
     */
    public void assignInstrument(String instrument) {
        assignedInstruments.add(instrument);
    }
    
    /**
     * Removes an instrument from this shard.
     */
    public void unassignInstrument(String instrument) {
        assignedInstruments.remove(instrument);
    }
    
    /**
     * Checks if this shard handles the given instrument.
     */
    public boolean handlesInstrument(String instrument) {
        return assignedInstruments.contains(instrument);
    }
    
    /**
     * Recovers the shard from a specific sequence number.
     * 
     * @param fromSequence Starting sequence number for replay
     * @param eventBus EventBus for replaying events
     */
    public void recover(long fromSequence, EventBus eventBus) {
        status = ShardStatus.RECOVERING;
        metrics.startRecovery();
        
        try {
            // Replay events from the specified sequence
            // Note: Actual replay implementation depends on EventBus persistence
            long currentSequence = engine.getCurrentSequence();
            if (currentSequence < fromSequence) {
                // In a real implementation, replay events from persistent log
                // For now, just log the recovery attempt
                System.out.println("Shard " + shardId + " recovering from sequence " + 
                                 fromSequence + " to " + currentSequence);
            }
            
            status = ShardStatus.ACTIVE;
            metrics.endRecovery();
        } catch (Exception e) {
            status = ShardStatus.STOPPED;
            throw new RuntimeException("Failed to recover shard " + shardId, e);
        }
    }
    
    public ShardId getShardId() {
        return shardId;
    }
    
    public ShardStatus getStatus() {
        return status;
    }
    
    public Set<String> getAssignedInstruments() {
        return Set.copyOf(assignedInstruments);
    }
    
    public ShardMetrics getMetrics() {
        return metrics;
    }
    
    public int getCpuCore() {
        return cpuCore;
    }
    
    public long getCurrentSequence() {
        return engine.getCurrentSequence();
    }
    
    /**
     * Creates ShardInfo for registry registration.
     */
    public ShardInfo toShardInfo() {
        return new ShardInfo(
            shardId,
            cpuCore,
            getAssignedInstruments(),
            status,
            getCurrentSequence()
        );
    }
    
    private void checkStatus() {
        if (status != ShardStatus.ACTIVE) {
            throw new IllegalStateException(
                "Shard " + shardId + " is not active (status: " + status + ")"
            );
        }
    }
}
