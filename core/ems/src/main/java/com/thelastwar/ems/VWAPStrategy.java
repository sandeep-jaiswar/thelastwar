package com.thelastwar.ems;

import com.thelastwar.oms.ChildOrder;
import com.thelastwar.oms.InternalOrderId;
import com.thelastwar.oms.ParentOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * VWAPStrategy implements Volume-Weighted Average Price execution.
 * 
 * VWAP aims to execute the parent order in proportion to market volume distribution.
 * This strategy monitors market volume and adjusts child order sizes dynamically to
 * track the volume-weighted average price.
 * 
 * <p>Algorithm:</p>
 * <pre>
 * 1. Monitor cumulative market volume
 * 2. Calculate target execution: totalQuantity * (currentVolume / expectedDayVolume)
 * 3. Submit child orders to catch up to target if behind
 * 4. Slow down if ahead of target
 * </pre>
 * 
 * <p>Note:</p>
 * This implementation uses a simplified model with uniform volume distribution.
 * Production VWAP would use historical volume profiles for better accuracy.
 */
public class VWAPStrategy extends AbstractStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(VWAPStrategy.class);
    
    private final ScheduledExecutorService scheduler;
    
    private ScheduledFuture<?> executionTask;
    private volatile long lastVolume;
    private volatile long currentPrice;
    private AtomicLong targetExecuted;
    private long expectedDayVolume;
    private Instant startInstant;
    private final AtomicLong childOrderIdGenerator;
    
    public VWAPStrategy(StrategyConfig config, EMSService emsService, ScheduledExecutorService scheduler) {
        super(config, emsService);
        this.scheduler = scheduler;
        this.lastVolume = 0;
        this.currentPrice = 0;
        this.targetExecuted = new AtomicLong(0);
        this.childOrderIdGenerator = new AtomicLong(System.currentTimeMillis() * 1000);
    }
    
    @Override
    protected void doStart(ParentOrder parentOrder) throws StrategyException {
        // Validate configuration
        if (config.duration() == null) {
            throw new StrategyException("VWAP strategy requires duration to be specified");
        }
        
        this.startInstant = Instant.now();
        
        // Estimate expected day volume (in production, use historical data)
        // For this implementation, we use a configurable parameter or default
        Object expectedVolumeParam = config.parameters().get("expectedDayVolume");
        if (expectedVolumeParam != null) {
            this.expectedDayVolume = ((Number) expectedVolumeParam).longValue();
        } else {
            // Default: assume parent order is 1% of expected day volume
            this.expectedDayVolume = parentOrder.getTotalQuantity() * 100;
        }
        
        logger.info("VWAP started: duration={}, expectedDayVolume={}",
            config.duration(), expectedDayVolume);
        
        // Start periodic execution checks (every second)
        executionTask = scheduler.scheduleAtFixedRate(
            this::checkAndExecute,
            0,
            1000,
            TimeUnit.MILLISECONDS
        );
    }
    
    @Override
    protected void doOnTick(MarketTick tick) {
        // Update current market state
        this.currentPrice = tick.getMidPrice();
        this.lastVolume = tick.cumulativeVolume();
        
        // Log every 100 ticks
        if (ticksProcessed.get() % 100 == 0) {
            logger.debug("VWAP tick: price={}, volume={}", currentPrice, lastVolume);
        }
    }
    
    @Override
    protected void doOnFill(FillEvent fill) {
        // Update target executed amount
        targetExecuted.addAndGet(fill.fillQuantity());
        
        logger.debug("VWAP received fill: {} @ {} (total executed: {})",
            fill.fillQuantity(), fill.fillPrice(), targetExecuted.get());
    }
    
    @Override
    protected void doPause() {
        if (executionTask != null && !executionTask.isDone()) {
            executionTask.cancel(false);
        }
    }
    
    @Override
    protected void doResume() {
        executionTask = scheduler.scheduleAtFixedRate(
            this::checkAndExecute,
            0,
            1000,
            TimeUnit.MILLISECONDS
        );
    }
    
    @Override
    protected void doStop() {
        if (executionTask != null && !executionTask.isDone()) {
            executionTask.cancel(false);
        }
    }
    
    @Override
    protected void doComplete() {
        if (executionTask != null && !executionTask.isDone()) {
            executionTask.cancel(false);
        }
    }
    
    /**
     * Check if we need to execute more and submit child orders accordingly.
     */
    private void checkAndExecute() {
        if (getState() != StrategyState.RUNNING) {
            return;
        }
        
        ParentOrder parent = parentOrder.get();
        if (parent == null) {
            logger.error("Parent order is null in VWAP strategy");
            return;
        }
        
        if (parent.isFullyFilled()) {
            complete();
            return;
        }
        
        // Calculate target execution based on volume progress
        long totalQuantity = parent.getTotalQuantity();
        long currentlyFilled = parent.getFilledQuantity();
        
        // Calculate how much we should have executed based on volume
        double volumeProgress = expectedDayVolume > 0 
            ? (double) lastVolume / expectedDayVolume 
            : 0.0;
        
        // Also consider time progress
        Duration elapsed = Duration.between(startInstant, Instant.now());
        double timeProgress = (double) elapsed.toMillis() / config.duration().toMillis();
        
        // Use weighted average of volume and time progress
        double targetProgress = (volumeProgress * 0.7) + (timeProgress * 0.3);
        
        // Clamp to [0, 1]
        targetProgress = Math.max(0.0, Math.min(1.0, targetProgress));
        
        long targetFilled = (long) (totalQuantity * targetProgress);
        long shortfall = targetFilled - currentlyFilled;
        
        // Only execute if we're behind by a significant amount
        long minChildSize = config.minChildSize() != null ? config.minChildSize() : 100;
        if (shortfall >= minChildSize) {
            long childQuantity = Math.min(shortfall, parent.getRemainingQuantity());
            
            // Cap at max child size if configured
            if (config.maxChildSize() != null) {
                childQuantity = Math.min(childQuantity, config.maxChildSize());
            }
            
            executeChildOrder(parent, childQuantity);
        }
    }
    
    /**
     * Execute a child order with the specified quantity.
     */
    private void executeChildOrder(ParentOrder parent, long quantity) {
        // Determine price for child order
        long price = currentPrice > 0 ? currentPrice : parent.getPrice();
        
        // Apply price limit if configured
        if (config.priceLimit() != null) {
            byte side = parent.getSide();
            if (side == 1) { // BUY
                price = Math.min(price, config.priceLimit());
            } else { // SELL
                price = Math.max(price, config.priceLimit());
            }
        }
        
        // Create and submit child order
        InternalOrderId childOrderId = new InternalOrderId(childOrderIdGenerator.incrementAndGet());
        ChildOrder childOrder = ChildOrder.fromParentWithPrice(
            childOrderId,
            parent,
            quantity,
            price,
            "SMART",
            "VWAP"
        );
        
        logger.info("VWAP submitting child order: {} shares @ {} (target progress: {:.1f}%)",
            quantity, price, 
            100.0 * parent.getFilledQuantity() / parent.getTotalQuantity());
        
        submitChildOrder(childOrder);
    }
    
    /**
     * Get the current target execution quantity based on volume progress.
     * 
     * @return Target quantity that should be executed
     */
    public long getTargetExecutedQuantity() {
        ParentOrder parent = parentOrder.get();
        if (parent == null) {
            return 0;
        }
        
        double volumeProgress = expectedDayVolume > 0 
            ? (double) lastVolume / expectedDayVolume 
            : 0.0;
        
        return (long) (parent.getTotalQuantity() * volumeProgress);
    }
}
