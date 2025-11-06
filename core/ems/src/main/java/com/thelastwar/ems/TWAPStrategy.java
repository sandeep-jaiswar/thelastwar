package com.thelastwar.ems;

import com.thelastwar.oms.ChildOrder;
import com.thelastwar.oms.InternalOrderId;
import com.thelastwar.oms.ParentOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TWAPStrategy implements Time-Weighted Average Price execution.
 * 
 * TWAP splits the parent order into equal-sized child orders and submits them
 * at regular intervals over the specified duration. This strategy aims to
 * minimize market impact by spreading execution evenly over time.
 * 
 * <p>Algorithm:</p>
 * <pre>
 * 1. Calculate slice size: totalQuantity / numSlices
 * 2. Calculate interval: duration / numSlices
 * 3. Submit one slice every interval until all quantity is executed
 * </pre>
 * 
 * <p>Example:</p>
 * - Parent order: 10,000 shares
 * - Duration: 10 minutes
 * - Slices: 10
 * - Result: 1,000 shares every 1 minute
 */
public class TWAPStrategy extends AbstractStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(TWAPStrategy.class);
    
    private final ScheduledExecutorService scheduler;
    
    private List<ChildOrderSchedule> schedule;
    private ScheduledFuture<?> executionTask;
    private int currentSlice;
    private volatile long currentPrice;
    private final AtomicLong childOrderIdGenerator;
    
    public TWAPStrategy(StrategyConfig config, EMSService emsService, ScheduledExecutorService scheduler) {
        super(config, emsService);
        this.scheduler = scheduler;
        this.schedule = new ArrayList<>();
        this.currentSlice = 0;
        this.currentPrice = 0;
        this.childOrderIdGenerator = new AtomicLong(System.currentTimeMillis() * 1000);
    }
    
    @Override
    protected void doStart(ParentOrder parentOrder) throws StrategyException {
        // Validate configuration
        if (config.duration() == null) {
            throw new StrategyException("TWAP strategy requires duration to be specified");
        }
        if (config.numSlices() == null || config.numSlices() <= 0) {
            throw new StrategyException("TWAP strategy requires positive number of slices");
        }
        
        // Calculate schedule
        long totalQuantity = parentOrder.getTotalQuantity();
        int numSlices = config.numSlices();
        long sliceSize = totalQuantity / numSlices;
        long remainder = totalQuantity % numSlices;
        
        Duration totalDuration = config.duration();
        long intervalMillis = totalDuration.toMillis() / numSlices;
        
        logger.info("TWAP schedule: {} slices of {} shares every {}ms",
            numSlices, sliceSize, intervalMillis);
        
        // Build schedule
        Instant startTime = Instant.now();
        for (int i = 0; i < numSlices; i++) {
            long quantity = sliceSize;
            // Add remainder to last slice
            if (i == numSlices - 1) {
                quantity += remainder;
            }
            
            Instant scheduledTime = startTime.plusMillis(intervalMillis * i);
            schedule.add(new ChildOrderSchedule(i, quantity, scheduledTime));
        }
        
        // Start scheduled execution
        executionTask = scheduler.scheduleAtFixedRate(
            this::executeNextSlice,
            0,
            intervalMillis,
            TimeUnit.MILLISECONDS
        );
    }
    
    @Override
    protected void doOnTick(MarketTick tick) {
        // Track current market price for child orders
        this.currentPrice = tick.getMidPrice();
    }
    
    @Override
    protected void doOnFill(FillEvent fill) {
        // TWAP doesn't adjust based on fills - continues with schedule
        logger.debug("TWAP received fill: {} @ {} (remaining: {})",
            fill.fillQuantity(), fill.fillPrice(), fill.remainingQuantity());
    }
    
    @Override
    protected void doPause() {
        if (executionTask != null && !executionTask.isDone()) {
            executionTask.cancel(false);
        }
    }
    
    @Override
    protected void doResume() {
        if (currentSlice < schedule.size()) {
            long intervalMillis = config.duration().toMillis() / config.numSlices();
            executionTask = scheduler.scheduleAtFixedRate(
                this::executeNextSlice,
                0,
                intervalMillis,
                TimeUnit.MILLISECONDS
            );
        }
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
     * Execute the next slice in the TWAP schedule.
     */
    private void executeNextSlice() {
        if (currentSlice >= schedule.size()) {
            complete();
            return;
        }
        
        if (getState() != StrategyState.RUNNING) {
            return; // Don't execute if not running
        }
        
        ParentOrder parent = parentOrder.get();
        if (parent == null) {
            logger.error("Parent order is null in TWAP strategy");
            return;
        }
        
        // Check if parent is already fully filled
        if (parent.isFullyFilled()) {
            complete();
            return;
        }
        
        ChildOrderSchedule slice = schedule.get(currentSlice);
        currentSlice++;
        
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
            slice.quantity(),
            price,
            "SMART", // Route to SOR
            "TWAP"
        );
        
        logger.info("TWAP submitting slice {}/{}: {} shares @ {} (scheduled: {})",
            currentSlice, schedule.size(), slice.quantity(), price, slice.scheduledTime());
        
        submitChildOrder(childOrder);
    }
    
    /**
     * Get the execution schedule.
     * 
     * @return List of scheduled child orders
     */
    public List<ChildOrderSchedule> getSchedule() {
        return List.copyOf(schedule);
    }
    
    /**
     * Represents a scheduled child order in the TWAP execution plan.
     */
    public record ChildOrderSchedule(
        int sliceNumber,
        long quantity,
        Instant scheduledTime
    ) {}
}
