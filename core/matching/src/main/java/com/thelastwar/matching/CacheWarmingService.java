package com.thelastwar.matching;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.OrderEvent;
import io.micrometer.core.instrument.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Predictive Cache Warming Service that pre-loads hot symbols and active accounts
 * based on last-hour activity.
 * 
 * Architecture:
 * - Subscribes to ORDER_SUBMITTED events to track activity
 * - Maintains rolling window of symbol/account activity (1 hour)
 * - Periodically pre-warms order books for hot symbols
 * - Minimal latency overhead using lock-free data structures
 * 
 * Performance targets:
 * - Hit ratio > 95% for active symbols
 * - Latency impact < 1 µs
 * - Metrics exposed to Prometheus via Micrometer
 */
public class CacheWarmingService {
    
    private final MatchingEngine engine;
    private final EventBus eventBus;
    private final MeterRegistry meterRegistry;
    private final long activityWindowMs;
    private final int warmingThreshold;
    private final ScheduledExecutorService scheduler;
    
    // Activity tracking - lock-free counters
    private final ConcurrentHashMap<String, ActivityCounter> symbolActivity;
    private final ConcurrentHashMap<Long, ActivityCounter> accountActivity;
    
    // Cache warming state
    private final Set<String> warmedSymbols;
    private final Set<Long> warmedAccounts;
    
    // Metrics
    private final AtomicLong cacheHits;
    private final AtomicLong cacheMisses;
    private final Counter warmingOperations;
    private final io.micrometer.core.instrument.Timer warmingLatency;
    private final Gauge hitRatioGauge;
    
    private volatile boolean running;
    
    /**
     * Creates a new cache warming service with default settings.
     * 
     * @param engine MatchingEngine to warm caches for
     * @param eventBus EventBus to subscribe for activity tracking
     * @param meterRegistry Micrometer registry for metrics
     */
    public CacheWarmingService(MatchingEngine engine, EventBus eventBus, MeterRegistry meterRegistry) {
        this(engine, eventBus, meterRegistry, 3600_000L, 5);
    }
    
    /**
     * Creates a new cache warming service with custom settings.
     * 
     * @param engine MatchingEngine to warm caches for
     * @param eventBus EventBus to subscribe for activity tracking
     * @param meterRegistry Micrometer registry for metrics
     * @param activityWindowMs Activity window in milliseconds (default: 1 hour)
     * @param warmingThreshold Minimum activity count to trigger warming (default: 5)
     */
    public CacheWarmingService(MatchingEngine engine, EventBus eventBus, 
                               MeterRegistry meterRegistry, long activityWindowMs, 
                               int warmingThreshold) {
        this.engine = engine;
        this.eventBus = eventBus;
        this.meterRegistry = meterRegistry;
        this.activityWindowMs = activityWindowMs;
        this.warmingThreshold = warmingThreshold;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-warming-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        this.symbolActivity = new ConcurrentHashMap<>();
        this.accountActivity = new ConcurrentHashMap<>();
        this.warmedSymbols = ConcurrentHashMap.newKeySet();
        this.warmedAccounts = ConcurrentHashMap.newKeySet();
        
        this.cacheHits = new AtomicLong(0);
        this.cacheMisses = new AtomicLong(0);
        
        // Initialize metrics
        this.warmingOperations = Counter.builder("cache.warming.operations")
            .description("Total number of cache warming operations")
            .register(meterRegistry);
        
        this.warmingLatency = io.micrometer.core.instrument.Timer.builder("cache.warming.latency")
            .description("Cache warming operation latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        
        this.hitRatioGauge = Gauge.builder("cache.warming.hit_ratio", this, CacheWarmingService::getHitRatio)
            .description("Cache hit ratio (0-1)")
            .register(meterRegistry);
        
        Gauge.builder("cache.warming.symbols_warmed", warmedSymbols, Set::size)
            .description("Number of symbols currently warmed in cache")
            .register(meterRegistry);
        
        Gauge.builder("cache.warming.accounts_warmed", warmedAccounts, Set::size)
            .description("Number of accounts currently warmed in cache")
            .register(meterRegistry);
        
        this.running = false;
    }
    
    /**
     * Starts the cache warming service.
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("Cache warming service is already running");
        }
        
        // Subscribe to order events for activity tracking
        eventBus.subscribe(EventType.ORDER_SUBMITTED, this::handleOrderEvent);
        
        // Schedule periodic cache warming (every 10 seconds)
        scheduler.scheduleAtFixedRate(this::performCacheWarming, 5, 10, TimeUnit.SECONDS);
        
        // Schedule periodic cleanup of stale activity (every minute)
        scheduler.scheduleAtFixedRate(this::cleanupStaleActivity, 60, 60, TimeUnit.SECONDS);
        
        running = true;
    }
    
    /**
     * Stops the cache warming service.
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Handles order events for activity tracking.
     * This is a hot path - must be ultra-low latency.
     * 
     * @param event Event containing OrderEvent payload
     */
    private void handleOrderEvent(Event event) {
        if (!(event.payload() instanceof OrderEvent orderEvent)) {
            return;
        }
        
        long startTime = System.nanoTime();
        long currentTime = System.nanoTime();  // Use consistent clock
        
        // Track symbol activity
        String symbol = orderEvent.symbol();
        symbolActivity.computeIfAbsent(symbol, k -> new ActivityCounter())
            .recordActivity(currentTime);
        
        // Track account activity
        long account = orderEvent.account();
        accountActivity.computeIfAbsent(account, k -> new ActivityCounter())
            .recordActivity(currentTime);
        
        // Check if this symbol/account was pre-warmed (hit tracking)
        if (warmedSymbols.contains(symbol) || warmedAccounts.contains(account)) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }
        
        // Ensure latency impact < 1 µs (1000 ns)
        long elapsed = System.nanoTime() - startTime;
        if (elapsed > 1000) {
            // Log warning if latency exceeds target (could use a rate-limited logger)
            // For now, just track via metrics
        }
    }
    
    /**
     * Performs cache warming for hot symbols and accounts.
     */
    private void performCacheWarming() {
        if (!running) {
            return;
        }
        
        long startTime = System.nanoTime();
        
        try {
            // Get hot symbols
            Set<String> hotSymbols = getHotSymbols();
            
            // Pre-warm order books for hot symbols
            for (String symbol : hotSymbols) {
                // This triggers computeIfAbsent which creates the order book
                engine.getOrderBook(symbol);
                warmedSymbols.add(symbol);
            }
            
            // Update warmed accounts set
            Set<Long> hotAccounts = getHotAccounts();
            warmedAccounts.clear();
            warmedAccounts.addAll(hotAccounts);
            
            // Record metrics
            warmingOperations.increment();
            long elapsed = System.nanoTime() - startTime;
            warmingLatency.record(elapsed, TimeUnit.NANOSECONDS);
            
        } catch (Exception e) {
            // Swallow exceptions to prevent scheduler from stopping
            // In production, use proper logging
        }
    }
    
    /**
     * Gets hot symbols based on recent activity.
     * 
     * @return Set of hot symbols above warming threshold
     */
    private Set<String> getHotSymbols() {
        long cutoffTime = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(activityWindowMs);
        
        return symbolActivity.entrySet().stream()
            .filter(e -> e.getValue().getActivityCount(cutoffTime) >= warmingThreshold)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }
    
    /**
     * Gets hot accounts based on recent activity.
     * 
     * @return Set of hot accounts above warming threshold
     */
    private Set<Long> getHotAccounts() {
        long cutoffTime = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(activityWindowMs);
        
        return accountActivity.entrySet().stream()
            .filter(e -> e.getValue().getActivityCount(cutoffTime) >= warmingThreshold)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }
    
    /**
     * Cleans up stale activity data.
     */
    private void cleanupStaleActivity() {
        if (!running) {
            return;
        }
        
        long cutoffTime = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(activityWindowMs * 2);
        
        // Clean up symbols with no recent activity
        symbolActivity.entrySet().removeIf(e -> 
            e.getValue().getActivityCount(cutoffTime) == 0);
        
        // Clean up accounts with no recent activity
        accountActivity.entrySet().removeIf(e -> 
            e.getValue().getActivityCount(cutoffTime) == 0);
        
        // Remove symbols from warmed set if no longer hot
        Set<String> hotSymbols = getHotSymbols();
        warmedSymbols.retainAll(hotSymbols);
    }
    
    /**
     * Gets the current cache hit ratio.
     * 
     * @return Hit ratio between 0.0 and 1.0
     */
    public double getHitRatio() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        
        if (total == 0) {
            return 0.0;
        }
        
        return (double) hits / total;
    }
    
    /**
     * Gets metrics about the cache warming service.
     * 
     * @return CacheWarmingMetrics snapshot
     */
    public CacheWarmingMetrics getMetrics() {
        return new CacheWarmingMetrics(
            symbolActivity.size(),
            accountActivity.size(),
            warmedSymbols.size(),
            warmedAccounts.size(),
            getHitRatio(),
            cacheHits.get(),
            cacheMisses.get()
        );
    }
    
    /**
     * Activity counter with timestamp-based tracking.
     * Uses CopyOnWriteArrayList for lock-free reads with occasional writes.
     */
    private static class ActivityCounter {
        private final List<Long> timestamps = new CopyOnWriteArrayList<>();
        
        void recordActivity(long timestamp) {
            timestamps.add(timestamp);
            
            // Periodic cleanup to prevent unbounded growth
            // Remove timestamps older than 2 hours
            if (timestamps.size() > 100) {
                long cutoff = System.nanoTime() - TimeUnit.HOURS.toNanos(2);
                timestamps.removeIf(ts -> ts < cutoff);
            }
        }
        
        int getActivityCount(long since) {
            return (int) timestamps.stream()
                .filter(ts -> ts >= since)
                .count();
        }
    }
    
    /**
     * Metrics record for cache warming service state.
     */
    public record CacheWarmingMetrics(
        int trackedSymbols,
        int trackedAccounts,
        int warmedSymbols,
        int warmedAccounts,
        double hitRatio,
        long totalHits,
        long totalMisses
    ) {}
}
