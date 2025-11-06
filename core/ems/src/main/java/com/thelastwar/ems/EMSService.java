package com.thelastwar.ems;

import com.thelastwar.eventbus.EventBus;
import com.thelastwar.oms.ChildOrder;
import com.thelastwar.oms.ChildOrderManager;
import com.thelastwar.oms.ParentOrder;
import com.thelastwar.oms.RoutingDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * EMSService is the core Execution Management System service.
 * 
 * It orchestrates execution strategies (TWAP, VWAP, IOC, Iceberg) that generate
 * child orders and route them via the Smart Order Router (SOR) and Order Management
 * System (OMS).
 * 
 * <p>Responsibilities:</p>
 * - Create and manage execution strategies
 * - Route child orders to OMS/SOR
 * - Distribute market data to strategies
 * - Distribute fill events to strategies
 * - Provide runtime controls (pause/resume/stop)
 * - Track strategy metrics and performance
 */
public class EMSService implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(EMSService.class);
    
    private final EventBus eventBus;
    private final ChildOrderManager childOrderManager;
    private final ScheduledExecutorService scheduler;
    
    // Active strategies: strategyId -> Strategy
    private final Map<String, Strategy> strategies;
    
    // Strategy to parent order mapping
    private final Map<String, ParentOrder> strategyOrders;
    
    public EMSService(EventBus eventBus, ChildOrderManager childOrderManager) {
        this(eventBus, childOrderManager, Executors.newScheduledThreadPool(4));
    }
    
    public EMSService(EventBus eventBus, ChildOrderManager childOrderManager, 
                     ScheduledExecutorService scheduler) {
        this.eventBus = eventBus;
        this.childOrderManager = childOrderManager;
        this.scheduler = scheduler;
        this.strategies = new ConcurrentHashMap<>();
        this.strategyOrders = new ConcurrentHashMap<>();
    }
    
    /**
     * Start a new execution strategy.
     * 
     * @param config Strategy configuration
     * @param parentOrder Parent order to execute
     * @return The started strategy
     * @throws StrategyException if strategy cannot be started
     */
    public Strategy startStrategy(StrategyConfig config, ParentOrder parentOrder) 
            throws StrategyException {
        
        config.validate();
        
        // Check if strategy ID already exists
        if (strategies.containsKey(config.strategyId())) {
            throw new StrategyException("Strategy with ID " + config.strategyId() + " already exists");
        }
        
        // Register parent order with child order manager
        childOrderManager.registerParentOrder(parentOrder);
        
        // Create strategy instance
        Strategy strategy = createStrategy(config);
        
        // Store strategy
        strategies.put(config.strategyId(), strategy);
        strategyOrders.put(config.strategyId(), parentOrder);
        
        // Start strategy
        strategy.start(parentOrder);
        
        logger.info("Started {} strategy {} for order {}", 
            config.strategyType(), config.strategyId(), parentOrder.getParentOrderId());
        
        return strategy;
    }
    
    /**
     * Create a strategy instance based on configuration.
     */
    private Strategy createStrategy(StrategyConfig config) throws StrategyException {
        return switch (config.strategyType()) {
            case TWAP -> new TWAPStrategy(config, this, scheduler);
            case VWAP -> new VWAPStrategy(config, this, scheduler);
            case IOC, ICEBERG, DMA -> 
                throw new StrategyException("Strategy type " + config.strategyType() + " not yet implemented");
        };
    }
    
    /**
     * Get a strategy by ID.
     * 
     * @param strategyId Strategy ID
     * @return The strategy, or null if not found
     */
    public Strategy getStrategy(String strategyId) {
        return strategies.get(strategyId);
    }
    
    /**
     * Pause a strategy.
     * 
     * @param strategyId Strategy ID
     * @return true if strategy was paused
     */
    public boolean pauseStrategy(String strategyId) {
        Strategy strategy = strategies.get(strategyId);
        if (strategy != null) {
            strategy.pause();
            logger.info("Paused strategy {}", strategyId);
            return true;
        }
        return false;
    }
    
    /**
     * Resume a strategy.
     * 
     * @param strategyId Strategy ID
     * @return true if strategy was resumed
     */
    public boolean resumeStrategy(String strategyId) {
        Strategy strategy = strategies.get(strategyId);
        if (strategy != null) {
            strategy.resume();
            logger.info("Resumed strategy {}", strategyId);
            return true;
        }
        return false;
    }
    
    /**
     * Stop a strategy.
     * 
     * @param strategyId Strategy ID
     * @return true if strategy was stopped
     */
    public boolean stopStrategy(String strategyId) {
        Strategy strategy = strategies.get(strategyId);
        if (strategy != null) {
            strategy.stop();
            logger.info("Stopped strategy {}", strategyId);
            return true;
        }
        return false;
    }
    
    /**
     * Get metrics for a strategy.
     * 
     * @param strategyId Strategy ID
     * @return Strategy metrics, or null if strategy not found
     */
    public StrategyMetrics getMetrics(String strategyId) {
        Strategy strategy = strategies.get(strategyId);
        return strategy != null ? strategy.getMetrics() : null;
    }
    
    /**
     * Get all active strategies.
     * 
     * @return Map of strategy ID to strategy
     */
    public Map<String, Strategy> getAllStrategies() {
        return Map.copyOf(strategies);
    }
    
    /**
     * Distribute a market tick to all running strategies for the symbol.
     * 
     * @param tick Market tick
     */
    public void onMarketTick(MarketTick tick) {
        for (Strategy strategy : strategies.values()) {
            if (strategy.getConfig().symbol().equals(tick.symbol())) {
                strategy.onTick(tick);
            }
        }
    }
    
    /**
     * Distribute a fill event to the relevant strategy.
     * 
     * @param fill Fill event
     */
    public void onFill(FillEvent fill) {
        // Find strategy for this parent order
        for (Map.Entry<String, ParentOrder> entry : strategyOrders.entrySet()) {
            if (entry.getValue().getParentOrderId().equals(fill.parentOrderId())) {
                Strategy strategy = strategies.get(entry.getKey());
                if (strategy != null) {
                    strategy.onFill(fill);
                }
                break;
            }
        }
    }
    
    /**
     * Submit a child order to the OMS/SOR.
     * 
     * This method is called by strategies to submit child orders for execution.
     * 
     * @param childOrder The child order to submit
     */
    public void submitChildOrder(ChildOrder childOrder) {
        try {
            // Create a simple routing decision with single instruction
            RoutingDecision decision = new RoutingDecision(
                childOrder.parentOrderId(),
                java.util.List.of(
                    new RoutingDecision.RoutingInstruction(
                        childOrder.venue(),
                        childOrder.quantity(),
                        childOrder.price(),
                        1, // priority
                        5000L // timeout (5 seconds)
                    )
                ),
                childOrder.routingStrategy(),
                System.nanoTime(),
                "EMS-generated"
            );
            
            // Submit to child order manager
            childOrderManager.createChildOrders(decision);
            
            logger.debug("Submitted child order {} for parent {}", 
                childOrder.childOrderId(), childOrder.parentOrderId());
                
        } catch (Exception e) {
            logger.error("Failed to submit child order", e);
        }
    }
    
    @Override
    public void close() {
        // Stop all strategies
        for (Strategy strategy : strategies.values()) {
            try {
                strategy.stop();
            } catch (Exception e) {
                logger.error("Error stopping strategy {}", strategy.getConfig().strategyId(), e);
            }
        }
        
        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("EMSService closed");
    }
}
