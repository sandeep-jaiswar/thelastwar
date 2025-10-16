package com.thelastwar.oms.integration;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.oms.*;
import com.thelastwar.oms.sor.MockSORClient;
import com.thelastwar.oms.sor.SORClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for OMS → SOR Integration & Child Order Management.
 * 
 * Tests the complete flow:
 * 1. Parent order submission
 * 2. SOR routing decision
 * 3. Child order creation
 * 4. Child order fills
 * 5. Fill reconciliation to parent
 * 6. Parent order state updates
 */
class OMSSORIntegrationTest {
    
    private EventBus eventBus;
    private SORClient sorClient;
    private ChildOrderManager childOrderManager;
    private List<Event> publishedEvents;
    
    @BeforeEach
    void setUp() {
        eventBus = new TestEventBus();
        publishedEvents = new ArrayList<>();
        sorClient = new MockSORClient(50_000); // 50 microseconds
        childOrderManager = new ChildOrderManager(eventBus, sorClient);
    }
    
    @AfterEach
    void tearDown() {
        sorClient.close();
    }
    
    /**
     * Test: Parent order split into child orders per SOR decisions and published to EventBus.
     */
    @Test
    void testParentOrderSplitAndPublished() throws Exception {
        // Step 1: Create parent order
        ParentOrder parentOrder = createParentOrder(500L);
        childOrderManager.registerParentOrder(parentOrder);
        
        // Step 2: Request SOR routing decision
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        
        assertNotNull(decision);
        assertTrue(decision.getChildOrderCount() > 0);
        
        // Step 3: Create child orders from routing decision
        int initialEventCount = publishedEvents.size();
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        
        // Verify: Child orders created
        assertNotNull(childOrders);
        assertFalse(childOrders.isEmpty());
        
        // Verify: Child orders published to EventBus
        int eventsPublished = publishedEvents.size() - initialEventCount;
        assertEquals(childOrders.size(), eventsPublished);
        
        // Verify: Parent knows about children
        assertEquals(childOrders.size(), parentOrder.getChildOrderCount());
        
        // Verify: Quantities sum correctly
        long totalChildQuantity = childOrders.stream()
            .mapToLong(ChildOrder::quantity)
            .sum();
        assertEquals(parentOrder.getTotalQuantity(), totalChildQuantity);
    }
    
    /**
     * Test: Fills on child orders reflected in parent order state correctly (PARTIAL/FILL).
     */
    @Test
    void testChildFillsReflectedInParentState() throws Exception {
        // Create and route parent order
        ParentOrder parentOrder = createParentOrder(500L);
        childOrderManager.registerParentOrder(parentOrder);
        
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        
        // Initial state: unfilled
        assertEquals(0L, parentOrder.getFilledQuantity());
        assertEquals(500L, parentOrder.getRemainingQuantity());
        assertFalse(parentOrder.isPartiallyFilled());
        assertFalse(parentOrder.isFullyFilled());
        
        // Partially fill first child
        ChildOrder firstChild = childOrders.get(0);
        long partialFill = firstChild.quantity() / 2;
        childOrderManager.reconcileFill(firstChild.childOrderId(), partialFill);
        
        // State should be PARTIAL_FILL
        assertEquals(partialFill, parentOrder.getFilledQuantity());
        assertTrue(parentOrder.isPartiallyFilled());
        assertFalse(parentOrder.isFullyFilled());
        
        // Fully fill first child
        long remainingFirstChild = firstChild.quantity() - partialFill;
        childOrderManager.reconcileFill(firstChild.childOrderId(), remainingFirstChild);
        
        // State still PARTIAL_FILL (other children not filled)
        assertEquals(firstChild.quantity(), parentOrder.getFilledQuantity());
        assertTrue(parentOrder.isPartiallyFilled());
        assertFalse(parentOrder.isFullyFilled());
        
        // Fully fill remaining children
        for (int i = 1; i < childOrders.size(); i++) {
            ChildOrder child = childOrders.get(i);
            childOrderManager.reconcileFill(child.childOrderId(), child.quantity());
        }
        
        // State should be FILLED
        assertEquals(500L, parentOrder.getFilledQuantity());
        assertEquals(0L, parentOrder.getRemainingQuantity());
        assertFalse(parentOrder.isPartiallyFilled());
        assertTrue(parentOrder.isFullyFilled());
    }
    
    /**
     * Test: Reconciliation test ensuring sum(child_fills) == parent_fill.
     */
    @Test
    void testReconciliationSumChildFillsEqualsParentFill() throws Exception {
        // Create and route parent order
        ParentOrder parentOrder = createParentOrder(1000L);
        childOrderManager.registerParentOrder(parentOrder);
        
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        
        // Simulate various partial fills on child orders
        long expectedTotalFilled = 0L;
        
        // Fill different portions of each child
        for (int i = 0; i < childOrders.size(); i++) {
            ChildOrder child = childOrders.get(i);
            long fillPercentage = 25 * (i + 1); // 25%, 50%, 75%, 100%...
            long fillAmount = (child.quantity() * fillPercentage) / 100;
            
            if (fillAmount > 0) {
                childOrderManager.reconcileFill(child.childOrderId(), fillAmount);
                expectedTotalFilled += fillAmount;
            }
        }
        
        // Verify: sum(child_fills) == parent_fill
        assertEquals(expectedTotalFilled, parentOrder.getFilledQuantity());
        assertEquals(1000L - expectedTotalFilled, parentOrder.getRemainingQuantity());
    }
    
    /**
     * Test: Metrics for child order counts and latencies.
     */
    @Test
    void testMetricsForChildOrderCountsAndLatencies() throws Exception {
        // Create multiple parent orders
        List<ParentOrder> parentOrders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ParentOrder parent = createParentOrder((i + 1) * 200L);
            childOrderManager.registerParentOrder(parent);
            parentOrders.add(parent);
        }
        
        // Route and create child orders for each parent
        int totalChildOrders = 0;
        for (ParentOrder parent : parentOrders) {
            CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parent);
            RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
            List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
            totalChildOrders += childOrders.size();
            
            // Simulate fills
            for (ChildOrder child : childOrders) {
                childOrderManager.reconcileFill(child.childOrderId(), child.quantity() / 2);
            }
        }
        
        // Verify child order manager metrics
        ChildOrderManager.ChildOrderMetrics childMetrics = childOrderManager.getMetrics();
        assertEquals(totalChildOrders, childMetrics.totalChildOrdersCreated());
        assertEquals(totalChildOrders, childMetrics.totalFillsReconciled());
        assertEquals(0, childMetrics.totalReconciliationErrors());
        assertEquals(5, childMetrics.activeParentOrders());
        assertTrue(childMetrics.getAverageChildOrdersPerParent() > 0);
        
        // Verify SOR metrics
        SORClient.SORMetrics sorMetrics = sorClient.getMetrics();
        assertEquals(5, sorMetrics.totalRoutingRequests());
        assertEquals(5, sorMetrics.successfulRoutings());
        assertEquals(0, sorMetrics.failedRoutings());
        assertTrue(sorMetrics.averageRoutingLatencyNanos() > 0);
        assertEquals(100.0, sorMetrics.getSuccessRate(), 0.01);
    }
    
    /**
     * Test: Latency requirements for routing and reconciliation.
     */
    @Test
    void testLatencyRequirements() throws Exception {
        ParentOrder parentOrder = createParentOrder(500L);
        childOrderManager.registerParentOrder(parentOrder);
        
        // Measure SOR routing latency
        long startRouting = System.nanoTime();
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        long routingLatency = System.nanoTime() - startRouting;
        
        // Routing should complete in < 10 ms for mock
        assertTrue(routingLatency < 10_000_000L, 
            "Routing latency too high: " + (routingLatency / 1_000_000) + " ms");
        
        // Measure child order creation latency
        long startCreation = System.nanoTime();
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        long creationLatency = System.nanoTime() - startCreation;
        
        // Child order creation should be < 1 ms per child (relaxed for test environment)
        long avgCreationLatencyPerChild = creationLatency / childOrders.size();
        assertTrue(avgCreationLatencyPerChild < 1_000_000L,
            "Child creation latency too high: " + (avgCreationLatencyPerChild / 1000) + " µs per child");
        
        // Measure fill reconciliation latency
        ChildOrder firstChild = childOrders.get(0);
        long startReconciliation = System.nanoTime();
        childOrderManager.reconcileFill(firstChild.childOrderId(), firstChild.quantity());
        long reconciliationLatency = System.nanoTime() - startReconciliation;
        
        // Fill reconciliation should be < 100 µs (relaxed for test environment)
        assertTrue(reconciliationLatency < 100_000L,
            "Reconciliation latency too high: " + (reconciliationLatency / 1000) + " µs");
    }
    
    /**
     * Test: Multiple parent orders with different split strategies.
     */
    @Test
    void testMultipleParentOrdersWithDifferentStrategies() throws Exception {
        // Small order (< 100): Should route to single venue
        ParentOrder smallOrder = createParentOrder(50L);
        childOrderManager.registerParentOrder(smallOrder);
        
        CompletableFuture<RoutingDecision> smallFuture = sorClient.requestRouting(smallOrder);
        RoutingDecision smallDecision = smallFuture.get(1, TimeUnit.SECONDS);
        List<ChildOrder> smallChildren = childOrderManager.createChildOrders(smallDecision);
        
        assertEquals(1, smallChildren.size(), "Small order should route to single venue");
        
        // Medium order (100-1000): Should split across 2 venues
        ParentOrder mediumOrder = createParentOrder(500L);
        childOrderManager.registerParentOrder(mediumOrder);
        
        CompletableFuture<RoutingDecision> mediumFuture = sorClient.requestRouting(mediumOrder);
        RoutingDecision mediumDecision = mediumFuture.get(1, TimeUnit.SECONDS);
        List<ChildOrder> mediumChildren = childOrderManager.createChildOrders(mediumDecision);
        
        assertEquals(2, mediumChildren.size(), "Medium order should split across 2 venues");
        
        // Large order (> 1000): Should split across 3 venues
        ParentOrder largeOrder = createParentOrder(2000L);
        childOrderManager.registerParentOrder(largeOrder);
        
        CompletableFuture<RoutingDecision> largeFuture = sorClient.requestRouting(largeOrder);
        RoutingDecision largeDecision = largeFuture.get(1, TimeUnit.SECONDS);
        List<ChildOrder> largeChildren = childOrderManager.createChildOrders(largeDecision);
        
        assertEquals(3, largeChildren.size(), "Large order should split across 3 venues");
    }
    
    /**
     * Test: Error handling for invalid reconciliation.
     */
    @Test
    void testErrorHandlingInReconciliation() throws Exception {
        ParentOrder parentOrder = createParentOrder(500L);
        childOrderManager.registerParentOrder(parentOrder);
        
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        
        ChildOrder firstChild = childOrders.get(0);
        
        // Try to reconcile more than child quantity - should fail
        assertThrows(IllegalArgumentException.class, () -> {
            childOrderManager.reconcileFill(firstChild.childOrderId(), firstChild.quantity());
            childOrderManager.reconcileFill(firstChild.childOrderId(), 1L); // Overfill
        });
        
        // Metrics should track error
        ChildOrderManager.ChildOrderMetrics metrics = childOrderManager.getMetrics();
        assertTrue(metrics.totalReconciliationErrors() > 0);
    }
    
    private ParentOrder createParentOrder(long quantity) {
        long orderId = System.nanoTime() % 1_000_000; // Pseudo-unique ID
        return new ParentOrder(
            InternalOrderId.of(orderId),
            ClientOrderId.of("PARENT-" + orderId),
            "AAPL",
            (byte) 1,
            (byte) 2,
            quantity,
            15000L,
            123L,
            System.nanoTime()
        );
    }
    
    /**
     * Test EventBus implementation.
     */
    private class TestEventBus implements EventBus {
        @Override
        public boolean publish(Event event) {
            publishedEvents.add(event);
            return true;
        }
        
        @Override
        public Subscription subscribe(int eventType, com.thelastwar.eventbus.EventHandler<?> handler) {
            return new Subscription() {
                @Override
                public void unsubscribe() {}
                
                @Override
                public boolean isActive() {
                    return false;
                }
            };
        }
        
        @Override
        public long getPublishedEventCount() {
            return publishedEvents.size();
        }
        
        @Override
        public int getSubscriberCount(int eventType) {
            return 0;
        }
        
        @Override
        public void start() {
            // Not needed
        }
        
        @Override
        public void stop() {
            // Not needed
        }
    }
}
