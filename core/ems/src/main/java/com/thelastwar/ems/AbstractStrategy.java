package com.thelastwar.ems;

import com.thelastwar.oms.ChildOrder;
import com.thelastwar.oms.ParentOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AbstractStrategy provides a base implementation for execution strategies.
 * 
 * This class handles common functionality like state management, metrics tracking,
 * and lifecycle control, allowing concrete strategies to focus on their specific
 * execution logic.
 */
public abstract class AbstractStrategy implements Strategy {
    
    private static final Logger logger = LoggerFactory.getLogger(AbstractStrategy.class);
    
    protected final StrategyConfig config;
    protected final EMSService emsService;
    
    protected final AtomicReference<StrategyState> state;
    protected final AtomicReference<ParentOrder> parentOrder;
    protected final AtomicReference<Instant> startTime;
    protected final AtomicReference<Instant> endTime;
    
    // Metrics tracking
    protected final AtomicInteger childOrdersGenerated;
    protected final AtomicInteger childOrdersFilled;
    protected final AtomicInteger childOrdersPartiallyFilled;
    protected final AtomicInteger childOrdersRejected;
    protected final AtomicLong ticksProcessed;
    protected final AtomicLong fillsProcessed;
    protected final AtomicLong totalFillValue;
    protected final AtomicLong totalSlippage;
    protected final AtomicLong bestPrice;
    protected final AtomicLong worstPrice;
    
    protected AbstractStrategy(StrategyConfig config, EMSService emsService) {
        this.config = config;
        this.emsService = emsService;
        this.state = new AtomicReference<>(StrategyState.CREATED);
        this.parentOrder = new AtomicReference<>();
        this.startTime = new AtomicReference<>();
        this.endTime = new AtomicReference<>();
        
        this.childOrdersGenerated = new AtomicInteger(0);
        this.childOrdersFilled = new AtomicInteger(0);
        this.childOrdersPartiallyFilled = new AtomicInteger(0);
        this.childOrdersRejected = new AtomicInteger(0);
        this.ticksProcessed = new AtomicLong(0);
        this.fillsProcessed = new AtomicLong(0);
        this.totalFillValue = new AtomicLong(0);
        this.totalSlippage = new AtomicLong(0);
        this.bestPrice = new AtomicLong(Long.MAX_VALUE);
        this.worstPrice = new AtomicLong(0);
    }
    
    @Override
    public void start(ParentOrder parentOrder) throws StrategyException {
        if (!state.compareAndSet(StrategyState.CREATED, StrategyState.RUNNING)) {
            throw new StrategyException("Strategy can only be started from CREATED state");
        }
        
        this.parentOrder.set(parentOrder);
        this.startTime.set(Instant.now());
        
        logger.info("Starting {} strategy {} for order {} (qty: {})",
            config.strategyType(), config.strategyId(), 
            parentOrder.getParentOrderId(), parentOrder.getTotalQuantity());
        
        try {
            doStart(parentOrder);
        } catch (Exception e) {
            state.set(StrategyState.ERROR);
            throw new StrategyException("Failed to start strategy", e);
        }
    }
    
    @Override
    public void onTick(MarketTick tick) {
        if (state.get() != StrategyState.RUNNING) {
            return; // Ignore ticks when not running
        }
        
        ticksProcessed.incrementAndGet();
        
        try {
            doOnTick(tick);
        } catch (Exception e) {
            logger.error("Error processing tick in strategy {}", config.strategyId(), e);
            state.set(StrategyState.ERROR);
        }
    }
    
    @Override
    public void onFill(FillEvent fill) {
        StrategyState currentState = state.get();
        if (currentState != StrategyState.RUNNING && currentState != StrategyState.PAUSED) {
            return; // Still track fills when paused
        }
        
        fillsProcessed.incrementAndGet();
        
        // Update metrics
        totalFillValue.addAndGet(fill.fillPrice() * fill.fillQuantity());
        
        // Update best/worst prices
        bestPrice.updateAndGet(current -> Math.min(current, fill.fillPrice()));
        worstPrice.updateAndGet(current -> Math.max(current, fill.fillPrice()));
        
        if (fill.isFullFill()) {
            childOrdersFilled.incrementAndGet();
        } else if (fill.isPartialFill()) {
            childOrdersPartiallyFilled.incrementAndGet();
        }
        
        try {
            doOnFill(fill);
            
            // Check if parent order is fully filled
            ParentOrder parent = parentOrder.get();
            if (parent != null && parent.isFullyFilled()) {
                complete();
            }
        } catch (Exception e) {
            logger.error("Error processing fill in strategy {}", config.strategyId(), e);
            state.set(StrategyState.ERROR);
        }
    }
    
    @Override
    public void pause() {
        if (state.compareAndSet(StrategyState.RUNNING, StrategyState.PAUSED)) {
            logger.info("Paused strategy {}", config.strategyId());
            doPause();
        }
    }
    
    @Override
    public void resume() {
        if (state.compareAndSet(StrategyState.PAUSED, StrategyState.RUNNING)) {
            logger.info("Resumed strategy {}", config.strategyId());
            doResume();
        }
    }
    
    @Override
    public void stop() {
        StrategyState currentState = state.get();
        if (currentState == StrategyState.STOPPED || currentState == StrategyState.COMPLETED) {
            return;
        }
        
        state.set(StrategyState.STOPPED);
        endTime.set(Instant.now());
        
        logger.info("Stopped strategy {}", config.strategyId());
        doStop();
    }
    
    protected void complete() {
        if (state.compareAndSet(StrategyState.RUNNING, StrategyState.COMPLETED)) {
            endTime.set(Instant.now());
            logger.info("Completed strategy {}", config.strategyId());
            doComplete();
        }
    }
    
    @Override
    public StrategyState getState() {
        return state.get();
    }
    
    @Override
    public StrategyConfig getConfig() {
        return config;
    }
    
    @Override
    public StrategyMetrics getMetrics() {
        ParentOrder parent = parentOrder.get();
        long totalQuantity = parent != null ? parent.getTotalQuantity() : 0;
        long filledQuantity = parent != null ? parent.getFilledQuantity() : 0;
        
        long avgFillPrice = filledQuantity > 0 ? totalFillValue.get() / filledQuantity : 0;
        
        return new StrategyMetrics(
            config.strategyId(),
            config.strategyType(),
            startTime.get(),
            endTime.get(),
            totalQuantity,
            filledQuantity,
            childOrdersGenerated.get(),
            childOrdersFilled.get(),
            childOrdersPartiallyFilled.get(),
            childOrdersRejected.get(),
            avgFillPrice,
            avgFillPrice, // VWAP same as avg for now
            bestPrice.get() == Long.MAX_VALUE ? 0 : bestPrice.get(),
            worstPrice.get(),
            totalSlippage.get(),
            ticksProcessed.get(),
            fillsProcessed.get()
        );
    }
    
    /**
     * Submit a child order to the EMS for execution.
     * 
     * @param childOrder The child order to submit
     */
    protected void submitChildOrder(ChildOrder childOrder) {
        childOrdersGenerated.incrementAndGet();
        emsService.submitChildOrder(childOrder);
    }
    
    // Abstract methods for concrete strategies to implement
    
    /**
     * Initialize strategy-specific logic.
     * 
     * @param parentOrder The parent order to execute
     * @throws StrategyException if initialization fails
     */
    protected abstract void doStart(ParentOrder parentOrder) throws StrategyException;
    
    /**
     * Handle market tick in strategy-specific way.
     * 
     * @param tick The market tick
     */
    protected abstract void doOnTick(MarketTick tick);
    
    /**
     * Handle fill in strategy-specific way.
     * 
     * @param fill The fill event
     */
    protected abstract void doOnFill(FillEvent fill);
    
    /**
     * Handle pause logic (optional override).
     */
    protected void doPause() {
        // Default: no-op
    }
    
    /**
     * Handle resume logic (optional override).
     */
    protected void doResume() {
        // Default: no-op
    }
    
    /**
     * Handle stop logic (optional override).
     */
    protected void doStop() {
        // Default: no-op
    }
    
    /**
     * Handle completion logic (optional override).
     */
    protected void doComplete() {
        // Default: no-op
    }
}
