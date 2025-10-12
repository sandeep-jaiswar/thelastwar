package com.thelastwar.matching.shard;

import com.thelastwar.matching.IMatchingEngine;
import com.thelastwar.matching.MatchingEngine;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages multiple matching engine shards for horizontal scaling.
 * 
 * Key responsibilities:
 * - Routes orders to appropriate shards based on instrument
 * - Manages shard lifecycle (start, stop, recovery)
 * - Handles dynamic shard addition/removal
 * - Aggregates metrics across all shards
 * - Coordinates with ShardRegistry for cluster awareness
 * 
 * Architecture:
 * - Each shard processes a subset of instruments
 * - Shards can be pinned to CPU cores for cache locality
 * - Uses consistent hashing for even load distribution
 * - Supports scale-out: N shards → N× throughput
 * 
 * Thread Safety: All public methods are thread-safe.
 */
public class ShardManager implements ShardRegistry.ShardChangeListener {
    
    private final EventBus eventBus;
    private final ShardRegistry registry;
    private final ShardRouter router;
    private final ConcurrentHashMap<ShardId, Shard> shards;
    private volatile boolean running;
    
    /**
     * Creates a new ShardManager.
     * 
     * @param eventBus Event bus for order routing
     * @param registry Shard registry for cluster coordination
     * @param router Shard router for instrument assignment
     */
    public ShardManager(EventBus eventBus, ShardRegistry registry, ShardRouter router) {
        this.eventBus = eventBus;
        this.registry = registry;
        this.router = router;
        this.shards = new ConcurrentHashMap<>();
        this.running = false;
    }
    
    /**
     * Starts the shard manager and subscribes to order events.
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("ShardManager is already running");
        }
        
        // Subscribe to order events for routing
        eventBus.subscribe(EventType.ORDER_SUBMITTED, event -> routeOrder(event));
        
        // Register as listener for shard changes
        registry.addShardChangeListener(this);
        
        // Update router with current topology
        updateRouterTopology();
        
        running = true;
    }
    
    /**
     * Stops the shard manager and all shards.
     */
    public void stop() {
        running = false;
        
        // Stop all shards
        shards.values().forEach(Shard::stop);
        
        // Unregister all shards
        shards.keySet().forEach(registry::unregisterShard);
        
        // Clear local state
        shards.clear();
        
        registry.removeShardChangeListener(this);
    }
    
    /**
     * Adds a new shard to the cluster.
     * 
     * @param shardId Unique identifier for the shard
     * @param cpuCore CPU core to pin the shard to (-1 for no pinning)
     * @param instruments Initial set of instruments to assign
     * @return The created Shard instance
     */
    public Shard addShard(ShardId shardId, int cpuCore, Set<String> instruments) {
        if (shards.containsKey(shardId)) {
            throw new IllegalArgumentException("Shard " + shardId + " already exists");
        }
        
        // Create matching engine for this shard
        IMatchingEngine engine = new MatchingEngine(eventBus);
        
        // Create shard wrapper
        Shard shard = new Shard(shardId, engine, cpuCore);
        
        // Assign instruments
        instruments.forEach(shard::assignInstrument);
        
        // Add to local registry
        shards.put(shardId, shard);
        
        // Start the shard
        shard.start();
        
        // Register in cluster registry
        registry.registerShard(shard.toShardInfo());
        
        // Update routing topology
        updateRouterTopology();
        
        return shard;
    }
    
    /**
     * Removes a shard from the cluster.
     * 
     * @param shardId ID of the shard to remove
     */
    public void removeShard(ShardId shardId) {
        Shard shard = shards.remove(shardId);
        if (shard == null) {
            throw new IllegalArgumentException("Shard " + shardId + " does not exist");
        }
        
        // Stop the shard
        shard.stop();
        
        // Unregister from cluster
        registry.unregisterShard(shardId);
        
        // Update routing topology
        updateRouterTopology();
    }
    
    /**
     * Gets a shard by ID.
     * 
     * @param shardId ID of the shard
     * @return Optional containing the shard if found
     */
    public Optional<Shard> getShard(ShardId shardId) {
        return Optional.ofNullable(shards.get(shardId));
    }
    
    /**
     * Lists all active shards.
     * 
     * @return List of all shards
     */
    public List<Shard> listShards() {
        return List.copyOf(shards.values());
    }
    
    /**
     * Gets aggregated metrics across all shards.
     * 
     * @return Map of shard ID to metrics
     */
    public Map<ShardId, ShardMetrics> getMetrics() {
        return shards.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getMetrics()
            ));
    }
    
    /**
     * Gets load distribution statistics.
     * 
     * @return LoadDistribution containing balance metrics
     */
    public LoadDistribution getLoadDistribution() {
        Map<ShardId, Long> orderCounts = shards.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getMetrics().getOrdersProcessed()
            ));
        
        if (orderCounts.isEmpty()) {
            return new LoadDistribution(0, 0, 0, 0, 1.0);
        }
        
        long total = orderCounts.values().stream().mapToLong(Long::longValue).sum();
        long min = orderCounts.values().stream().mapToLong(Long::longValue).min().orElse(0);
        long max = orderCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        double avg = total / (double) orderCounts.size();
        
        // Calculate coefficient of variation (std dev / mean)
        double variance = orderCounts.values().stream()
            .mapToDouble(count -> Math.pow(count - avg, 2))
            .average()
            .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        double balanceFactor = avg > 0 ? (1.0 - stdDev / avg) : 1.0;
        
        return new LoadDistribution(total, min, max, avg, Math.max(0.0, balanceFactor));
    }
    
    /**
     * Routes an incoming order event to the appropriate shard.
     */
    private void routeOrder(com.thelastwar.eventbus.Event event) {
        if (!running) {
            return;
        }
        
        // Extract order information
        Object payload = event.payload();
        String instrument = null;
        
        if (payload instanceof OrderEvent orderEvent) {
            instrument = orderEvent.symbol();
        } else if (payload instanceof OrderEnvelope envelope) {
            instrument = envelope.symbol();
        }
        
        if (instrument == null) {
            return;
        }
        
        try {
            // Route to appropriate shard
            ShardId shardId = router.routeToShard(instrument);
            Shard shard = shards.get(shardId);
            
            if (shard != null && shard.getStatus() == ShardStatus.ACTIVE) {
                // Process based on payload type
                if (payload instanceof OrderEnvelope envelope) {
                    shard.processOrder(envelope);
                } else if (payload instanceof OrderEvent orderEvent) {
                    // Wrap in envelope if needed
                    OrderEnvelope envelope = OrderEnvelope.wrap(
                        orderEvent, 
                        event.sequence(), 
                        event.sourceId()
                    );
                    shard.processOrder(envelope);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to route order for instrument " + instrument + ": " + e.getMessage());
        }
    }
    
    /**
     * Updates the router with current shard topology.
     */
    private void updateRouterTopology() {
        List<ShardInfo> activeShards = registry.listActiveShards();
        router.updateTopology(activeShards);
    }
    
    @Override
    public void onShardAdded(ShardInfo shardInfo) {
        updateRouterTopology();
    }
    
    @Override
    public void onShardUpdated(ShardInfo shardInfo) {
        updateRouterTopology();
    }
    
    @Override
    public void onShardRemoved(ShardId shardId) {
        updateRouterTopology();
    }
    
    /**
     * Load distribution statistics.
     */
    public record LoadDistribution(
        long totalOrders,
        long minShardOrders,
        long maxShardOrders,
        double avgShardOrders,
        double balanceFactor  // 1.0 = perfectly balanced, 0.0 = completely imbalanced
    ) {}
}
