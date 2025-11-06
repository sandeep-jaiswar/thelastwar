package com.thelastwar.ems;

import com.thelastwar.oms.ParentOrder;

/**
 * Strategy is the core abstraction for pluggable execution strategies in the EMS.
 * 
 * Each strategy implements an algorithm for executing parent orders over time,
 * generating child orders according to its specific logic (TWAP, VWAP, IOC, etc.).
 * 
 * <p>Lifecycle:</p>
 * <pre>
 * 1. start(ParentOrder) - Initialize strategy with parent order
 * 2. onTick(MarketTick) - React to market data updates
 * 3. onFill(FillEvent) - React to child order fills
 * 4. pause() - Temporarily pause execution
 * 5. resume() - Resume paused execution
 * 6. stop() - Terminate strategy execution
 * </pre>
 * 
 * <p>Thread-Safety:</p>
 * Strategy implementations must be thread-safe as they may receive callbacks
 * from multiple threads (market data, execution reports, control commands).
 */
public interface Strategy {
    
    /**
     * Start the strategy execution for the given parent order.
     * 
     * @param parentOrder The parent order to execute
     * @throws StrategyException if strategy cannot be started
     */
    void start(ParentOrder parentOrder) throws StrategyException;
    
    /**
     * Handle market tick update.
     * 
     * Strategies use market ticks to make execution decisions based on
     * current market conditions (price, volume, spread, etc.).
     * 
     * @param tick The market tick update
     */
    void onTick(MarketTick tick);
    
    /**
     * Handle fill event from a child order.
     * 
     * Strategies adjust their execution based on fills received from
     * previously submitted child orders.
     * 
     * @param fill The fill event from a child order
     */
    void onFill(FillEvent fill);
    
    /**
     * Pause strategy execution.
     * 
     * When paused, the strategy stops generating new child orders but
     * continues to track fills from existing orders.
     */
    void pause();
    
    /**
     * Resume strategy execution after being paused.
     */
    void resume();
    
    /**
     * Stop strategy execution completely.
     * 
     * After stopping, the strategy will not generate any more child orders
     * and will not accept any more ticks or fills.
     */
    void stop();
    
    /**
     * Get the current state of the strategy.
     * 
     * @return The current strategy state
     */
    StrategyState getState();
    
    /**
     * Get the configuration for this strategy.
     * 
     * @return The strategy configuration
     */
    StrategyConfig getConfig();
    
    /**
     * Get metrics for this strategy's performance.
     * 
     * @return The strategy metrics
     */
    StrategyMetrics getMetrics();
}
