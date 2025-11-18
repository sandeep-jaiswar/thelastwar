package com.thelastwar.matching;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.OrderEvent;
import io.micrometer.core.instrument.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;
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
        
        // Pre-size maps for HFT workloads to avoid resizing overhead
        // Assume ~1000 active symbols and ~10000 active accounts
        this.symbolActivity = new ConcurrentHashMap<>(1024, 0.75f, 16);
        this.accountActivity = new ConcurrentHashMap<>(16384, 0.75f, 16);
        this.warmedSymbols = ConcurrentHashMap.newKeySet(1024);
        this.warmedAccounts = ConcurrentHashMap.newKeySet(16384);
        
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

        // Single timestamp for all operations - minimize system calls
        long currentTime = System.nanoTime();

        // Track symbol activity - optimized lookup
        String symbol = orderEvent.symbol();
        ActivityCounter symbolCounter = symbolActivity.get(symbol);
        if (symbolCounter == null) {
            // Only use computeIfAbsent if not present - avoid lambda allocation on hot path
            symbolCounter = symbolActivity.computeIfAbsent(symbol, k -> new ActivityCounter());
        }
        symbolCounter.recordActivity(currentTime);

        // Track account activity - optimized lookup  
        long account = orderEvent.account();
        ActivityCounter accountCounter = accountActivity.get(account);
        if (accountCounter == null) {
            accountCounter = accountActivity.computeIfAbsent(account, k -> new ActivityCounter());
        }
        accountCounter.recordActivity(currentTime);

        // Check if this symbol/account was pre-warmed (hit tracking)
        // Use single conditional to minimize branching
        if (warmedSymbols.contains(symbol) || warmedAccounts.contains(account)) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
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
     * Ultra-fast activity counter optimized for HFT latency requirements.
     * 
     * Uses a rolling window approach with circular buffer to avoid expensive 
     * collection operations in the hot path.
     */
    private static class ActivityCounter {
        private static final int WINDOW_SIZE = 3600; // 1 hour in seconds
        private static final long SECOND_NS = 1_000_000_000L;
        
        // Circular buffer for per-second counters
        private final AtomicIntegerArray secondCounters = new AtomicIntegerArray(WINDOW_SIZE);
        private volatile long lastSecond = -1;
        
        void recordActivity(long timestamp) {
            // Convert to seconds to reduce granularity
            long currentSecond = timestamp / SECOND_NS;
            
            // Update circular buffer index
            int index = (int) (currentSecond % WINDOW_SIZE);
            
            // Reset counter if we've moved to a new second
            if (currentSecond != lastSecond) {
                // Clear old entries (lazy approach - only clear current slot)
                secondCounters.set(index, 0);
                lastSecond = currentSecond;
            }
            
            // Increment counter for current second
            secondCounters.incrementAndGet(index);
        }
        
        int getActivityCount(long since) {
            long currentTime = System.nanoTime();
            long sinceSecond = since / SECOND_NS;
            long currentSecond = currentTime / SECOND_NS;
            
            int count = 0;
            // Sum up counters for the time window
            for (long sec = sinceSecond; sec <= currentSecond; sec++) {
                int index = (int) (sec % WINDOW_SIZE);
                count += secondCounters.get(index);
            }
            return count;
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
