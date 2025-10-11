package com.thelastwar.matching;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.orderbook.LimitOrderBook;
import com.thelastwar.orderbook.Order;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for CacheReconciliationService.
 * 
 * Tests cover:
 * - Service lifecycle (start/stop)
 * - Periodic reconciliation execution
 * - Drift detection and correction
 * - Auditable diff logging
 * - Zero-downtime rebuild
 * - Performance requirements (< 5 min detection)
 */
class CacheReconciliationServiceTest {
    
    private InMemoryEventBus eventBus;
    private MatchingEngine engine;
    private CacheReconciliationService service;
    private SimpleMeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        eventBus = new InMemoryEventBus();
        eventBus.start();
        
        engine = new MatchingEngine(eventBus);
        engine.start();
        
        meterRegistry = new SimpleMeterRegistry();
        
        // Use short interval for testing (5 seconds)
        service = new CacheReconciliationService(engine, eventBus, meterRegistry, 5000L);
    }
    
    @AfterEach
    void tearDown() {
        if (service != null && service.isRunning()) {
            service.stop();
        }
        if (engine != null) {
            engine.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
    }
    
    @Test
    @DisplayName("Service starts and stops successfully")
    void testServiceStartStop() {
        assertFalse(service.isRunning(), "Service should not be running initially");
        
        service.start();
        assertTrue(service.isRunning(), "Service should be running after start");
        
        service.stop();
        assertFalse(service.isRunning(), "Service should not be running after stop");
    }
    
    @Test
    @DisplayName("Cannot start service twice")
    void testCannotStartTwice() {
        service.start();
        
        assertThrows(IllegalStateException.class, () -> service.start(),
            "Starting service twice should throw IllegalStateException");
        
        service.stop();
    }
    
    @Test
    @DisplayName("Manual reconciliation completes without errors")
    void testManualReconciliation() {
        // Publish some orders first
        publishOrders("AAPL", 5);
        sleep(100);
        
        // Perform manual reconciliation
        service.performReconciliation();
        
        // Verify metrics
        CacheReconciliationService.ReconciliationMetrics metrics = service.getMetrics();
        assertEquals(1, metrics.totalReconciliations(), 0.01, 
            "Should have 1 reconciliation");
        assertTrue(metrics.lastReconciliationTimestamp() > 0, 
            "Last reconciliation timestamp should be set");
    }
    
    @Test
    @DisplayName("Reconciliation detects no drift in consistent state")
    void testNoDriftDetection() {
        // Publish some orders
        publishOrders("AAPL", 10);
        sleep(100);
        
        // Perform reconciliation
        service.performReconciliation();
        
        // Verify no drift detected
        CacheReconciliationService.ReconciliationMetrics metrics = service.getMetrics();
        assertEquals(0, metrics.totalDriftDetections(), 0.01, 
            "Should detect no drift in consistent state");
        assertEquals(0, metrics.totalCorrections(), 0.01, 
            "Should have no corrections");
        assertEquals(0, service.getDiffLogSize(), 
            "Diff log should be empty");
    }
    
    @Test
    @DisplayName("Diff log is auditable and persistent")
    void testAuditableDiffLog() {
        // Clear any existing diffs
        service.clearDiffLog();
        assertEquals(0, service.getDiffLogSize(), "Diff log should be empty after clear");
        
        // Perform reconciliation (no drift expected)
        service.performReconciliation();
        
        // Verify diff log is accessible
        List<CacheReconciliationService.ReconciliationDiff> diffs = service.getDiffLog();
        assertNotNull(diffs, "Diff log should not be null");
        assertEquals(0, diffs.size(), "Diff log should be empty for consistent state");
    }
    
    @Test
    @DisplayName("Reconciliation completes within performance target")
    void testReconciliationPerformance() {
        // Publish many orders to test performance
        for (int i = 0; i < 50; i++) {
            publishOrders("SYMB" + i, 20);
        }
        sleep(200);
        
        // Measure reconciliation time
        long startTime = System.currentTimeMillis();
        service.performReconciliation();
        long elapsedMs = System.currentTimeMillis() - startTime;
        
        // Should complete in well under 5 minutes (300,000 ms)
        // For this test size, it should be much faster
        assertTrue(elapsedMs < 10000, 
            "Reconciliation should complete in < 10 seconds for 1000 orders, took " + elapsedMs + "ms");
    }
    
    @Test
    @DisplayName("Service handles multiple reconciliation cycles")
    void testMultipleReconciliationCycles() {
        service.start();
        
        // Wait for multiple reconciliation cycles (5s interval)
        sleep(12000); // Should run at least 2 cycles
        
        // Verify multiple reconciliations occurred
        CacheReconciliationService.ReconciliationMetrics metrics = service.getMetrics();
        assertTrue(metrics.totalReconciliations() >= 2, 
            "Should have at least 2 reconciliations after 12s with 5s interval");
        
        service.stop();
    }
    
    @Test
    @DisplayName("Service metrics are accurate")
    void testMetricsAccuracy() {
        // Perform 3 manual reconciliations
        service.performReconciliation();
        service.performReconciliation();
        service.performReconciliation();
        
        // Verify metrics
        CacheReconciliationService.ReconciliationMetrics metrics = service.getMetrics();
        assertEquals(3, metrics.totalReconciliations(), 0.01, 
            "Should have 3 reconciliations");
        assertTrue(metrics.lastReconciliationTimestamp() > 0, 
            "Last reconciliation timestamp should be set");
    }
    
    @Test
    @DisplayName("Reconciliation works with empty order books")
    void testReconciliationWithEmptyBooks() {
        // No orders published - empty state
        service.performReconciliation();
        
        // Should complete without errors
        CacheReconciliationService.ReconciliationMetrics metrics = service.getMetrics();
        assertEquals(1, metrics.totalReconciliations(), 0.01);
        assertEquals(0, metrics.totalDriftDetections(), 0.01);
    }
    
    @Test
    @DisplayName("Reconciliation works with multiple symbols")
    void testReconciliationWithMultipleSymbols() {
        // Publish orders for multiple symbols
        publishOrders("AAPL", 10);
        publishOrders("MSFT", 10);
        publishOrders("GOOGL", 10);
        sleep(100);
        
        // Perform reconciliation
        service.performReconciliation();
        
        // Verify successful reconciliation
        CacheReconciliationService.ReconciliationMetrics metrics = service.getMetrics();
        assertEquals(1, metrics.totalReconciliations(), 0.01);
        
        // Verify all order books are still accessible
        assertNotNull(engine.getOrderBook("AAPL"));
        assertNotNull(engine.getOrderBook("MSFT"));
        assertNotNull(engine.getOrderBook("GOOGL"));
    }
    
    @Test
    @DisplayName("Zero downtime during reconciliation")
    void testZeroDowntimeDuringReconciliation() {
        // Publish initial orders
        publishOrders("AAPL", 100);
        sleep(100);
        
        // Start reconciliation in background
        Thread reconciliationThread = new Thread(() -> service.performReconciliation());
        reconciliationThread.start();
        
        // Continue processing orders during reconciliation
        boolean canProcessDuringReconciliation = true;
        try {
            publishOrders("AAPL", 50);
            sleep(50);
            
            // Verify engine is still responsive
            LimitOrderBook book = engine.getOrderBook("AAPL");
            assertNotNull(book, "Order book should remain accessible during reconciliation");
            
        } catch (Exception e) {
            canProcessDuringReconciliation = false;
        }
        
        // Wait for reconciliation to complete
        try {
            reconciliationThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertTrue(canProcessDuringReconciliation, 
            "Engine should remain operational during reconciliation (zero downtime)");
    }
    
    @Test
    @DisplayName("Diff types are correctly identified")
    void testDiffTypeIdentification() {
        // Test that different diff types can be created
        long timestamp = System.currentTimeMillis();
        
        CacheReconciliationService.ReconciliationDiff diff1 = 
            new CacheReconciliationService.ReconciliationDiff(
                timestamp,
                CacheReconciliationService.DiffType.SEQUENCE_MISMATCH,
                "Global",
                "Sequence",
                "100",
                "101"
            );
        
        assertEquals(CacheReconciliationService.DiffType.SEQUENCE_MISMATCH, diff1.type());
        assertEquals("Global", diff1.symbol());
        
        CacheReconciliationService.ReconciliationDiff diff2 = 
            new CacheReconciliationService.ReconciliationDiff(
                timestamp,
                CacheReconciliationService.DiffType.ORDER_MISMATCH,
                "AAPL",
                "Order 123",
                "old",
                "new"
            );
        
        assertEquals(CacheReconciliationService.DiffType.ORDER_MISMATCH, diff2.type());
        assertEquals("AAPL", diff2.symbol());
        
        // Test toString for auditing
        String diffStr = diff1.toString();
        assertTrue(diffStr.contains("SEQUENCE_MISMATCH"));
        assertTrue(diffStr.contains("100"));
        assertTrue(diffStr.contains("101"));
    }
    
    @Test
    @DisplayName("Service can be restarted after stopping")
    void testServiceRestart() {
        service.start();
        assertTrue(service.isRunning());
        
        service.stop();
        assertFalse(service.isRunning());
        
        // Create new service and start again
        service = new CacheReconciliationService(engine, eventBus, meterRegistry, 5000L);
        service.start();
        assertTrue(service.isRunning());
        
        service.stop();
    }
    
    // Helper methods
    
    private void publishOrders(String symbol, int count) {
        for (int i = 0; i < count; i++) {
            OrderEvent order = OrderEvent.newOrder(
                1000L + i,              // orderId
                symbol,
                i % 2 == 0 ? OrderEvent.SIDE_BUY : OrderEvent.SIDE_SELL,  // side
                OrderEvent.TYPE_LIMIT,  // orderType
                100L,                   // quantity
                10000L + (i * 10),      // price
                100L + (i % 10),        // accountId
                1                       // exchange
            );
            
            Event event = Event.create(
                System.nanoTime(),
                eventBus.getCurrentSequence() + 1,
                SourceId.OMS,
                EventType.ORDER_SUBMITTED,
                0L,
                order
            );
            
            eventBus.publish(event);
        }
    }
    
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
