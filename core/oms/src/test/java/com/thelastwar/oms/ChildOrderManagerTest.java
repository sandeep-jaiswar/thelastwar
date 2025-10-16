package com.thelastwar.oms;

import com.thelastwar.eventbus.AeronEventBus;
import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventType;
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
 * Test suite for ChildOrderManager.
 */
class ChildOrderManagerTest {
    
    private EventBus eventBus;
    private SORClient sorClient;
    private ChildOrderManager childOrderManager;
    private List<Event> publishedEvents;
    
    @BeforeEach
    void setUp() {
        // Create event bus with mock publisher
        eventBus = new MockEventBus();
        publishedEvents = new ArrayList<>();
        
        // Create SOR client with minimal latency for testing
        sorClient = new MockSORClient(1000); // 1 microsecond
        
        // Create child order manager
        childOrderManager = new ChildOrderManager(eventBus, sorClient);
    }
    
    @AfterEach
    void tearDown() {
        sorClient.close();
    }
    
    @Test
    void testRegisterParentOrder() {
        ParentOrder parentOrder = createTestParentOrder(500L);
        
        childOrderManager.registerParentOrder(parentOrder);
        
        ParentOrder retrieved = childOrderManager.getParentOrder(parentOrder.getParentOrderId());
        assertNotNull(retrieved);
        assertEquals(parentOrder.getParentOrderId(), retrieved.getParentOrderId());
    }
    
    @Test
    void testCreateChildOrdersFromRoutingDecision() throws Exception {
        // Create and register parent order
        ParentOrder parentOrder = createTestParentOrder(500L);
        childOrderManager.registerParentOrder(parentOrder);
        
        // Request routing decision
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        
        // Create child orders
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        
        // Verify child orders created
        assertNotNull(childOrders);
        assertFalse(childOrders.isEmpty());
        assertEquals(decision.getChildOrderCount(), childOrders.size());
        
        // Verify quantities sum to parent quantity
        long totalChildQuantity = childOrders.stream()
            .mapToLong(ChildOrder::quantity)
            .sum();
        assertEquals(parentOrder.getTotalQuantity(), totalChildQuantity);
        
        // Verify child orders are registered
        for (ChildOrder child : childOrders) {
            ChildOrder retrieved = childOrderManager.getChildOrder(child.childOrderId());
            assertNotNull(retrieved);
            assertEquals(child.childOrderId(), retrieved.childOrderId());
            
            // Verify parent-child mapping
            InternalOrderId retrievedParentId = childOrderManager.getParentOrderId(child.childOrderId());
            assertEquals(parentOrder.getParentOrderId(), retrievedParentId);
        }
        
        // Verify parent order knows about children
        assertEquals(childOrders.size(), parentOrder.getChildOrderCount());
    }
    
    @Test
    void testCreateChildOrdersPublishesToEventBus() throws Exception {
        ParentOrder parentOrder = createTestParentOrder(500L);
        childOrderManager.registerParentOrder(parentOrder);
        
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        
        int initialEventCount = publishedEvents.size();
        
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        
        // Should publish one event per child order
        assertEquals(childOrders.size(), publishedEvents.size() - initialEventCount);
        
        // Verify events are ORDER_SUBMITTED
        for (int i = initialEventCount; i < publishedEvents.size(); i++) {
            Event event = publishedEvents.get(i);
            assertEquals(EventType.ORDER_SUBMITTED, event.eventType());
        }
    }
    
    @Test
    void testReconcileFillToParent() throws Exception {
        // Create and register parent order
        ParentOrder parentOrder = createTestParentOrder(500L);
        childOrderManager.registerParentOrder(parentOrder);
        
        // Create child orders
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        
        // Simulate fill on first child order
        ChildOrder firstChild = childOrders.get(0);
        long fillQuantity = 50L;
        
        childOrderManager.reconcileFill(firstChild.childOrderId(), fillQuantity);
        
        // Verify parent order updated
        assertEquals(fillQuantity, parentOrder.getFilledQuantity());
        assertEquals(parentOrder.getTotalQuantity() - fillQuantity, parentOrder.getRemainingQuantity());
        assertTrue(parentOrder.isPartiallyFilled());
        assertFalse(parentOrder.isFullyFilled());
    }
    
    @Test
    void testMultipleChildFillsReconciliation() throws Exception {
        // Create and register parent order
        ParentOrder parentOrder = createTestParentOrder(500L);
        childOrderManager.registerParentOrder(parentOrder);
        
        // Create child orders
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        
        // Simulate fills on multiple child orders
        long totalFilled = 0L;
        for (ChildOrder child : childOrders) {
            long fillQuantity = child.quantity() / 2; // Fill half of each child
            childOrderManager.reconcileFill(child.childOrderId(), fillQuantity);
            totalFilled += fillQuantity;
        }
        
        // Verify sum(child_fills) == parent_fill
        assertEquals(totalFilled, parentOrder.getFilledQuantity());
        assertEquals(parentOrder.getTotalQuantity() - totalFilled, parentOrder.getRemainingQuantity());
    }
    
    @Test
    void testFullFillReconciliation() throws Exception {
        // Create and register parent order
        ParentOrder parentOrder = createTestParentOrder(500L);
        childOrderManager.registerParentOrder(parentOrder);
        
        // Create child orders
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        
        // Fully fill all child orders
        for (ChildOrder child : childOrders) {
            childOrderManager.reconcileFill(child.childOrderId(), child.quantity());
        }
        
        // Verify parent order fully filled
        assertEquals(parentOrder.getTotalQuantity(), parentOrder.getFilledQuantity());
        assertEquals(0L, parentOrder.getRemainingQuantity());
        assertTrue(parentOrder.isFullyFilled());
        assertFalse(parentOrder.isPartiallyFilled());
    }
    
    @Test
    void testReconcileFillInvalidChildOrder() {
        InternalOrderId nonExistentChildId = InternalOrderId.of(999999L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            childOrderManager.reconcileFill(nonExistentChildId, 100L);
        });
    }
    
    @Test
    void testReconcileFillInvalidQuantity() throws Exception {
        ParentOrder parentOrder = createTestParentOrder(500L);
        childOrderManager.registerParentOrder(parentOrder);
        
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        
        ChildOrder firstChild = childOrders.get(0);
        
        assertThrows(IllegalArgumentException.class, () -> {
            childOrderManager.reconcileFill(firstChild.childOrderId(), 0L);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            childOrderManager.reconcileFill(firstChild.childOrderId(), -100L);
        });
    }
    
    @Test
    void testCreateChildOrdersWithoutParentThrowsException() throws Exception {
        InternalOrderId nonExistentParentId = InternalOrderId.of(999999L);
        
        List<RoutingDecision.RoutingInstruction> instructions = List.of(
            RoutingDecision.RoutingInstruction.create("NYSE", 300L, 15000L, 1)
        );
        
        RoutingDecision decision = RoutingDecision.create(
            nonExistentParentId,
            instructions,
            "TEST_STRATEGY",
            "Test"
        );
        
        assertThrows(IllegalStateException.class, () -> {
            childOrderManager.createChildOrders(decision);
        });
    }
    
    @Test
    void testMetrics() throws Exception {
        // Create and register parent order
        ParentOrder parentOrder = createTestParentOrder(500L);
        childOrderManager.registerParentOrder(parentOrder);
        
        // Create child orders
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        
        // Reconcile some fills
        for (ChildOrder child : childOrders) {
            childOrderManager.reconcileFill(child.childOrderId(), child.quantity() / 2);
        }
        
        // Check metrics
        ChildOrderManager.ChildOrderMetrics metrics = childOrderManager.getMetrics();
        assertEquals(childOrders.size(), metrics.totalChildOrdersCreated());
        assertEquals(childOrders.size(), metrics.totalFillsReconciled());
        assertEquals(0, metrics.totalReconciliationErrors());
        assertEquals(1, metrics.activeParentOrders());
        assertEquals(childOrders.size(), metrics.activeChildOrders());
        assertTrue(metrics.getAverageChildOrdersPerParent() > 0);
        assertEquals(0.0, metrics.getReconciliationErrorRate());
    }
    
    @Test
    void testConcurrentFillReconciliation() throws Exception {
        // Create and register parent order
        ParentOrder parentOrder = createTestParentOrder(1000L);
        childOrderManager.registerParentOrder(parentOrder);
        
        // Create child orders
        CompletableFuture<RoutingDecision> future = sorClient.requestRouting(parentOrder);
        RoutingDecision decision = future.get(1, TimeUnit.SECONDS);
        List<ChildOrder> childOrders = childOrderManager.createChildOrders(decision);
        
        // Simulate concurrent fills from multiple threads
        List<Thread> threads = new ArrayList<>();
        for (ChildOrder child : childOrders) {
            Thread t = new Thread(() -> {
                long fillQuantity = child.quantity() / 10;
                for (int i = 0; i < 10; i++) {
                    childOrderManager.reconcileFill(child.childOrderId(), fillQuantity);
                }
            });
            threads.add(t);
            t.start();
        }
        
        // Wait for all threads
        for (Thread t : threads) {
            t.join();
        }
        
        // Verify parent order fully filled
        assertEquals(parentOrder.getTotalQuantity(), parentOrder.getFilledQuantity());
        assertTrue(parentOrder.isFullyFilled());
    }
    
    private ParentOrder createTestParentOrder(long quantity) {
        return new ParentOrder(
            InternalOrderId.of(1000L),
            ClientOrderId.of("PARENT-ORDER-001"),
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
     * Mock EventBus for testing that captures published events.
     */
    private class MockEventBus implements EventBus {
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
            // Not needed for this test
        }
        
        @Override
        public void stop() {
            // Not needed for this test
        }
    }
}
