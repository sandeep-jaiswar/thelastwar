package com.thelastwar.oms.sor;

import com.thelastwar.oms.ParentOrder;
import com.thelastwar.oms.RoutingDecision;

import java.util.concurrent.CompletableFuture;

/**
 * SORClient is the interface for interacting with the Smart Order Router (SOR).
 * 
 * The SOR is responsible for:
 * - Analyzing parent orders
 * - Making routing decisions based on market conditions, liquidity, and strategy
 * - Splitting orders across multiple venues
 * - Optimizing execution quality (price, speed, fill rate)
 * 
 * Implementations may be:
 * - Synchronous (for testing or simple strategies)
 * - Asynchronous (for production with complex analysis)
 * - Remote (calling external SOR service via gRPC or REST)
 * 
 * Performance targets:
 * - Routing decision: < 100 µs for simple strategies
 * - Routing decision: < 10 ms for complex ML-based strategies
 */
public interface SORClient {
    
    /**
     * Requests a routing decision for a parent order.
     * 
     * The SOR will analyze the order and return a routing decision specifying
     * how to split the order into child orders across venues.
     * 
     * @param parentOrder The parent order to route
     * @return CompletableFuture with routing decision
     * @throws IllegalArgumentException if parent order is invalid
     */
    CompletableFuture<RoutingDecision> requestRouting(ParentOrder parentOrder);
    
    /**
     * Subscribes to routing instruction updates.
     * 
     * Some SOR implementations may push routing instruction updates
     * asynchronously (e.g., when market conditions change or IOC orders timeout).
     * 
     * @param listener The listener to receive routing updates
     */
    void subscribeToRoutingInstructions(RoutingInstructionListener listener);
    
    /**
     * Unsubscribes a routing instruction listener.
     * 
     * @param listener The listener to unsubscribe
     */
    void unsubscribeFromRoutingInstructions(RoutingInstructionListener listener);
    
    /**
     * Gets SOR metrics for monitoring.
     * 
     * @return SOR metrics
     */
    SORMetrics getMetrics();
    
    /**
     * Closes the SOR client and releases resources.
     */
    void close();
    
    /**
     * Listener interface for receiving routing instruction updates.
     */
    interface RoutingInstructionListener {
        /**
         * Called when a new routing decision is available.
         * 
         * @param decision The routing decision
         */
        void onRoutingDecision(RoutingDecision decision);
        
        /**
         * Called when a routing decision fails or is rejected.
         * 
         * @param parentOrderId The parent order ID
         * @param reason        Failure reason
         */
        void onRoutingFailure(com.thelastwar.oms.InternalOrderId parentOrderId, String reason);
    }
    
    /**
     * Metrics for SOR client monitoring.
     */
    record SORMetrics(
            long totalRoutingRequests,
            long successfulRoutings,
            long failedRoutings,
            long averageRoutingLatencyNanos,
            long p99RoutingLatencyNanos,
            int activeSubscribers) {
        
        /**
         * Gets the success rate as a percentage.
         * 
         * @return success rate (0-100)
         */
        public double getSuccessRate() {
            if (totalRoutingRequests == 0) {
                return 0.0;
            }
            return (successfulRoutings * 100.0) / totalRoutingRequests;
        }
    }
}
