package com.thelastwar.oms;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OMSService.
 */
class OMSServiceTest {
    
    private TestEventBus eventBus;
    private OMSService omsService;
    private List<Event> publishedEvents;
    
    @TempDir
    Path tempDir;
    
    // Simple test EventBus implementation
    static class TestEventBus implements EventBus {
        private final Map<Integer, List<EventHandler<?>>> handlers = new ConcurrentHashMap<>();
        private final AtomicLong publishedCount = new AtomicLong(0);
        private volatile boolean running = false;
        
        @Override
        public boolean publish(Event event) {
            if (!running) return false;
            publishedCount.incrementAndGet();
            List<EventHandler<?>> eventHandlers = handlers.get(event.eventType());
            if (eventHandlers != null) {
                for (EventHandler<?> handler : eventHandlers) {
                    try {
                        @SuppressWarnings("unchecked")
                        EventHandler<Object> h = (EventHandler<Object>) handler;
                        h.onEvent(event);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            return true;
        }
        
        @Override
        public Subscription subscribe(int eventType, EventHandler<?> handler) {
            handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
            return new Subscription() {
                @Override
                public void unsubscribe() {
                    List<EventHandler<?>> list = handlers.get(eventType);
                    if (list != null) list.remove(handler);
                }
                
                @Override
                public boolean isActive() {
                    return handlers.getOrDefault(eventType, List.of()).contains(handler);
                }
            };
        }
        
        @Override
        public long getPublishedEventCount() {
            return publishedCount.get();
        }
        
        @Override
        public int getSubscriberCount(int eventType) {
            return handlers.getOrDefault(eventType, List.of()).size();
        }
        
        @Override
        public void start() {
            running = true;
        }
        
        @Override
        public void stop() {
            running = false;
        }
    }
    
    @BeforeEach
    void setUp() throws Exception {
        eventBus = new TestEventBus();
        eventBus.start();
        
        publishedEvents = new ArrayList<>();
        
        // Subscribe to all order events
        eventBus.subscribe(4000, event -> publishedEvents.add(event)); // ORDER_SUBMITTED
        eventBus.subscribe(2004, event -> publishedEvents.add(event)); // ORDER_CANCELLED
        
        Path commandLogPath = tempDir.resolve("commands.log");
        omsService = new OMSService(eventBus, commandLogPath);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (omsService != null) {
            omsService.close();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
    }
    
    @Test
    void testSubmitOrder_Success() {
        String clientOrderId = "CLIENT-ORDER-001";
        String correlationId = UUID.randomUUID().toString();
        
        OMSService.SubmissionResult result = omsService.submitOrder(
            clientOrderId, "AAPL", (byte) 1, (byte) 2, 100, 15000, 999, correlationId
        );
        
        assertTrue(result.success());
        assertTrue(result.orderId() > 0);
        assertEquals(correlationId, result.correlationId());
        assertEquals("Order submitted successfully", result.message());
        
        // Verify event was published
        assertEquals(1, publishedEvents.size());
        Event event = publishedEvents.get(0);
        assertEquals(4000, event.eventType()); // ORDER_SUBMITTED
        assertEquals(result.orderId(), event.sequence());
    }
    
    @Test
    void testSubmitOrder_Idempotency() {
        String clientOrderId = "CLIENT-ORDER-002";
        String correlationId = UUID.randomUUID().toString();
        
        // First submission
        OMSService.SubmissionResult result1 = omsService.submitOrder(
            clientOrderId, "AAPL", (byte) 1, (byte) 2, 100, 15000, 999, correlationId
        );
        
        assertTrue(result1.success());
        long orderId = result1.orderId();
        
        // Second submission with same clientOrderId (idempotent)
        OMSService.SubmissionResult result2 = omsService.submitOrder(
            clientOrderId, "AAPL", (byte) 1, (byte) 2, 100, 15000, 999, correlationId
        );
        
        assertTrue(result2.success());
        assertEquals(orderId, result2.orderId());
        assertTrue(result2.message().contains("already exists"));
        
        // Only one event should be published
        assertEquals(1, publishedEvents.size());
    }
    
    @Test
    void testSubmitOrder_InvalidClientOrderId() {
        String correlationId = UUID.randomUUID().toString();
        
        // Empty client order ID
        OMSService.SubmissionResult result = omsService.submitOrder(
            "", "AAPL", (byte) 1, (byte) 2, 100, 15000, 999, correlationId
        );
        
        assertFalse(result.success());
        assertTrue(result.message().contains("Validation error"));
    }
    
    @Test
    void testSubmitOrder_InvalidClientOrderIdFormat() {
        String correlationId = UUID.randomUUID().toString();
        
        // Invalid characters
        OMSService.SubmissionResult result = omsService.submitOrder(
            "ORDER@123", "AAPL", (byte) 1, (byte) 2, 100, 15000, 999, correlationId
        );
        
        assertFalse(result.success());
        assertTrue(result.message().contains("Validation error"));
    }
    
    @Test
    void testCancelOrder_Success() {
        String clientOrderId = "CLIENT-ORDER-003";
        String correlationId = UUID.randomUUID().toString();
        
        // Submit order first
        OMSService.SubmissionResult submitResult = omsService.submitOrder(
            clientOrderId, "AAPL", (byte) 1, (byte) 2, 100, 15000, 999, correlationId
        );
        
        assertTrue(submitResult.success());
        publishedEvents.clear();
        
        // Cancel order
        OMSService.CancellationResult cancelResult = omsService.cancelOrder(clientOrderId, correlationId);
        
        assertTrue(cancelResult.success());
        assertEquals(submitResult.orderId(), cancelResult.orderId());
        assertEquals("Order cancelled successfully", cancelResult.message());
        
        // Verify cancellation event was published
        assertEquals(1, publishedEvents.size());
        Event event = publishedEvents.get(0);
        assertEquals(2004, event.eventType()); // ORDER_CANCELLED
    }
    
    @Test
    void testCancelOrder_NotFound() {
        String correlationId = UUID.randomUUID().toString();
        
        OMSService.CancellationResult result = omsService.cancelOrder("NON-EXISTENT", correlationId);
        
        assertFalse(result.success());
        assertEquals("Order not found", result.message());
    }
    
    @Test
    void testCancelOrder_AlreadyCancelled() {
        String clientOrderId = "CLIENT-ORDER-004";
        String correlationId = UUID.randomUUID().toString();
        
        // Submit order
        omsService.submitOrder(clientOrderId, "AAPL", (byte) 1, (byte) 2, 100, 15000, 999, correlationId);
        
        // Cancel once
        OMSService.CancellationResult result1 = omsService.cancelOrder(clientOrderId, correlationId);
        assertTrue(result1.success());
        
        // Try to cancel again
        OMSService.CancellationResult result2 = omsService.cancelOrder(clientOrderId, correlationId);
        assertFalse(result2.success());
        assertTrue(result2.message().contains("cannot be cancelled"));
    }
    
    @Test
    void testQueryOrder_Found() {
        String clientOrderId = "CLIENT-ORDER-005";
        String correlationId = UUID.randomUUID().toString();
        
        // Submit order
        OMSService.SubmissionResult submitResult = omsService.submitOrder(
            clientOrderId, "AAPL", (byte) 1, (byte) 2, 100, 15000, 999, correlationId
        );
        
        assertTrue(submitResult.success());
        
        // Query order
        OMSService.OrderStatus status = omsService.queryOrder(clientOrderId);
        
        assertTrue(status.found());
        assertEquals(submitResult.orderId(), status.orderId());
        assertEquals(OrderState.NEW, status.currentState());
        assertFalse(status.isTerminal());
    }
    
    @Test
    void testQueryOrder_NotFound() {
        OMSService.OrderStatus status = omsService.queryOrder("NON-EXISTENT");
        
        assertFalse(status.found());
        assertEquals("Order not found", status.message());
    }
    
    @Test
    void testMultipleOrders() {
        // Submit multiple orders
        for (int i = 0; i < 10; i++) {
            String clientOrderId = "CLIENT-ORDER-" + i;
            OMSService.SubmissionResult result = omsService.submitOrder(
                clientOrderId, "AAPL", (byte) 1, (byte) 2, 100, 15000, 999, null
            );
            assertTrue(result.success());
        }
        
        // Verify stats
        OMSService.OMSStats stats = omsService.getStats();
        assertEquals(10, stats.totalCommands());
        assertEquals(10, stats.activeOrders());
        assertEquals(10, stats.totalOrders());
    }
    
    @Test
    void testLatencyRequirement() {
        String clientOrderId = "CLIENT-ORDER-LATENCY";
        String correlationId = UUID.randomUUID().toString();
        
        OMSService.SubmissionResult result = omsService.submitOrder(
            clientOrderId, "AAPL", (byte) 1, (byte) 2, 100, 15000, 999, correlationId
        );
        
        assertTrue(result.success());
        
        // Verify latency is within 2ms (2,000,000 nanoseconds)
        long latencyNanos = result.latencyNanos();
        assertTrue(latencyNanos < 2_000_000, 
            "Latency " + latencyNanos + " ns exceeds 2ms requirement");
    }
    
    @Test
    void testPersistenceAndRecovery() throws Exception {
        String clientOrderId1 = "CLIENT-ORDER-PERSIST-1";
        String clientOrderId2 = "CLIENT-ORDER-PERSIST-2";
        
        // Submit orders
        omsService.submitOrder(clientOrderId1, "AAPL", (byte) 1, (byte) 2, 100, 15000, 999, null);
        omsService.submitOrder(clientOrderId2, "MSFT", (byte) 2, (byte) 2, 50, 30000, 999, null);
        
        // Close OMS
        omsService.close();
        
        // Reopen OMS with same command log
        Path commandLogPath = tempDir.resolve("commands.log");
        OMSService recoveredOMS = new OMSService(eventBus, commandLogPath);
        
        // Verify orders are recovered
        OMSService.OrderStatus status1 = recoveredOMS.queryOrder(clientOrderId1);
        assertTrue(status1.found());
        
        OMSService.OrderStatus status2 = recoveredOMS.queryOrder(clientOrderId2);
        assertTrue(status2.found());
        
        // Verify idempotency still works
        OMSService.SubmissionResult result = recoveredOMS.submitOrder(
            clientOrderId1, "AAPL", (byte) 1, (byte) 2, 100, 15000, 999, null
        );
        assertTrue(result.success());
        assertTrue(result.message().contains("already exists"));
        
        recoveredOMS.close();
    }
}
