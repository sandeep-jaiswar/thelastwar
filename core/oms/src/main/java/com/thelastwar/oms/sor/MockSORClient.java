package com.thelastwar.oms.sor;

import com.thelastwar.oms.InternalOrderId;
import com.thelastwar.oms.ParentOrder;
import com.thelastwar.oms.RoutingDecision;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MockSORClient is a test implementation of the SOR client that simulates
 * routing decisions for testing purposes.
 * 
 * This implementation:
 * - Splits orders across multiple mock venues (NYSE, NASDAQ, BATS)
 * - Uses simple quantity-based splitting strategies
 * - Provides configurable latency simulation
 * - Tracks metrics for testing
 * 
 * NOT for production use - this is a mock for development and testing only.
 */
public class MockSORClient implements SORClient {
    
    private final List<RoutingInstructionListener> listeners;
    private final Random random;
    private final long simulatedLatencyNanos;
    
    // Metrics tracking
    private final AtomicLong totalRequests;
    private final AtomicLong successfulRoutings;
    private final AtomicLong failedRoutings;
    private final AtomicLong totalLatencyNanos;
    
    // Mock venues
    private static final String[] VENUES = {"NYSE", "NASDAQ", "BATS"};
    
    /**
     * Creates a MockSORClient with default settings.
     * Simulates 50 µs latency.
     */
    public MockSORClient() {
        this(50_000); // 50 microseconds
    }
    
    /**
     * Creates a MockSORClient with custom simulated latency.
     * 
     * @param simulatedLatencyNanos Simulated routing latency in nanoseconds
     */
    public MockSORClient(long simulatedLatencyNanos) {
        this.listeners = new CopyOnWriteArrayList<>();
        this.random = new Random();
        this.simulatedLatencyNanos = simulatedLatencyNanos;
        this.totalRequests = new AtomicLong(0);
        this.successfulRoutings = new AtomicLong(0);
        this.failedRoutings = new AtomicLong(0);
        this.totalLatencyNanos = new AtomicLong(0);
    }
    
    @Override
    public CompletableFuture<RoutingDecision> requestRouting(ParentOrder parentOrder) {
        long startTime = System.nanoTime();
        totalRequests.incrementAndGet();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Simulate SOR processing time
                if (simulatedLatencyNanos > 0) {
                    long sleepMicros = simulatedLatencyNanos / 1000;
                    Thread.sleep(sleepMicros / 1000, (int) (sleepMicros % 1000) * 1000);
                }
                
                // Create routing decision
                RoutingDecision decision = createRoutingDecision(parentOrder);
                
                // Validate quantities
                decision.validateQuantities(parentOrder.getTotalQuantity());
                
                // Notify listeners
                for (RoutingInstructionListener listener : listeners) {
                    try {
                        listener.onRoutingDecision(decision);
                    } catch (Exception e) {
                        // Log error but continue
                        System.err.println("Error notifying listener: " + e.getMessage());
                    }
                }
                
                long latency = System.nanoTime() - startTime;
                totalLatencyNanos.addAndGet(latency);
                successfulRoutings.incrementAndGet();
                
                return decision;
                
            } catch (InterruptedException e) {
                failedRoutings.incrementAndGet();
                
                // Notify listeners of failure
                for (RoutingInstructionListener listener : listeners) {
                    try {
                        listener.onRoutingFailure(parentOrder.getParentOrderId(), e.getMessage());
                    } catch (Exception ex) {
                        System.err.println("Error notifying listener of failure: " + ex.getMessage());
                    }
                }
                
                throw new RuntimeException("Failed to create routing decision", e);
            }
        });
    }
    
    /**
     * Creates a routing decision using a simple splitting strategy.
     * 
     * Strategy:
     * - Small orders (< 100): Single venue
     * - Medium orders (100-1000): Split across 2 venues
     * - Large orders (> 1000): Split across 3 venues
     */
    private RoutingDecision createRoutingDecision(ParentOrder parentOrder) {
        long totalQty = parentOrder.getTotalQuantity();
        List<RoutingDecision.RoutingInstruction> instructions = new ArrayList<>();
        
        if (totalQty < 100) {
            // Small order - route to single venue
            instructions.add(RoutingDecision.RoutingInstruction.create(
                VENUES[0],
                totalQty,
                parentOrder.getPrice(),
                1
            ));
        } else if (totalQty < 1000) {
            // Medium order - split across 2 venues
            long qty1 = totalQty * 60 / 100; // 60%
            long qty2 = totalQty - qty1;      // 40%
            
            instructions.add(RoutingDecision.RoutingInstruction.create(
                VENUES[0],
                qty1,
                parentOrder.getPrice(),
                1
            ));
            instructions.add(RoutingDecision.RoutingInstruction.create(
                VENUES[1],
                qty2,
                parentOrder.getPrice(),
                2
            ));
        } else {
            // Large order - split across 3 venues
            long qty1 = totalQty * 50 / 100; // 50%
            long qty2 = totalQty * 30 / 100; // 30%
            long qty3 = totalQty - qty1 - qty2; // 20%
            
            instructions.add(RoutingDecision.RoutingInstruction.create(
                VENUES[0],
                qty1,
                parentOrder.getPrice(),
                1
            ));
            instructions.add(RoutingDecision.RoutingInstruction.create(
                VENUES[1],
                qty2,
                parentOrder.getPrice(),
                2
            ));
            instructions.add(RoutingDecision.RoutingInstruction.create(
                VENUES[2],
                qty3,
                parentOrder.getPrice(),
                3
            ));
        }
        
        return RoutingDecision.create(
            parentOrder.getParentOrderId(),
            instructions,
            "MOCK_SPLIT_STRATEGY",
            "Mock routing for testing"
        );
    }
    
    @Override
    public void subscribeToRoutingInstructions(RoutingInstructionListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    @Override
    public void unsubscribeFromRoutingInstructions(RoutingInstructionListener listener) {
        listeners.remove(listener);
    }
    
    @Override
    public SORMetrics getMetrics() {
        long total = totalRequests.get();
        long avgLatency = total > 0 ? totalLatencyNanos.get() / total : 0;
        
        return new SORMetrics(
            total,
            successfulRoutings.get(),
            failedRoutings.get(),
            avgLatency,
            avgLatency, // p99 same as avg for mock
            listeners.size()
        );
    }
    
    @Override
    public void close() {
        listeners.clear();
    }
}
