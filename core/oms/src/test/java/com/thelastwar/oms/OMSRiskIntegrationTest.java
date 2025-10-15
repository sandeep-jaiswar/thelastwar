package com.thelastwar.oms;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventHandler;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.risk.CompositeRiskValidator;
import com.thelastwar.risk.CreditCheckModule;
import com.thelastwar.risk.RiskDecision;
import com.thelastwar.risk.RiskReasonCode;
import com.thelastwar.risk.RiskValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OMS with pre-trade risk checking.
 */
class OMSRiskIntegrationTest {
    
    private TestEventBus eventBus;
    private OMSService omsService;
    
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
    void setUp() {
        eventBus = new TestEventBus();
        eventBus.start();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (omsService != null) {
            omsService.close();
        }
        eventBus.stop();
    }
    
    @Test
    void testOrderApprovedByRiskCheck() throws Exception {
        // Setup OMS with always-approve risk validator
        RiskValidator alwaysApprove = order -> RiskDecision.APPROVED;
        RiskCheckService riskService = new RiskCheckService(
            alwaysApprove,
            RiskCheckService.RiskCheckConfig.defaultConfig()
        );
        
        omsService = new OMSService(eventBus, tempDir.resolve("commands.log"), riskService);
        
        // Submit order
        OMSService.SubmissionResult result = omsService.submitOrder(
            "TEST-ORDER-1",
            "AAPL",
            (byte) 1,  // BUY
            (byte) 2,  // LIMIT
            100L,
            15000L,
            999L,
            UUID.randomUUID().toString()
        );
        
        // Verify order was accepted
        assertTrue(result.success());
        assertFalse(result.riskRejected());
        assertEquals(0, result.riskReasonCode());
        assertTrue(result.latencyNanos() > 0);
        
        // Verify event was published
        assertEquals(1, eventBus.getPublishedEventCount());
        
        // Verify metrics
        RiskCheckService.RiskMetrics metrics = omsService.getRiskMetrics();
        assertEquals(1, metrics.getTotalChecks());
        assertEquals(1, metrics.getApprovedCount());
        assertEquals(0, metrics.getRejectedCount());
    }
    
    @Test
    void testOrderRejectedByRiskCheck() throws Exception {
        // Setup OMS with always-reject risk validator
        RiskValidator alwaysReject = order -> RiskDecision.reject(
            RiskReasonCode.INSUFFICIENT_CREDIT,
            "Insufficient credit limit"
        );
        RiskCheckService riskService = new RiskCheckService(
            alwaysReject,
            RiskCheckService.RiskCheckConfig.defaultConfig()
        );
        
        omsService = new OMSService(eventBus, tempDir.resolve("commands.log"), riskService);
        
        // Submit order
        OMSService.SubmissionResult result = omsService.submitOrder(
            "TEST-ORDER-2",
            "AAPL",
            (byte) 1,  // BUY
            (byte) 2,  // LIMIT
            100L,
            15000L,
            999L,
            UUID.randomUUID().toString()
        );
        
        // Verify order was rejected
        assertFalse(result.success());
        assertTrue(result.riskRejected());
        assertEquals(RiskReasonCode.INSUFFICIENT_CREDIT, result.riskReasonCode());
        assertTrue(result.message().contains("Insufficient credit limit"));
        
        // Verify NO event was published (risk rejected before EventBus)
        assertEquals(0, eventBus.getPublishedEventCount());
        
        // Verify metrics
        RiskCheckService.RiskMetrics metrics = omsService.getRiskMetrics();
        assertEquals(1, metrics.getTotalChecks());
        assertEquals(0, metrics.getApprovedCount());
        assertEquals(1, metrics.getRejectedCount());
    }
    
    @Test
    void testFailOpenBehavior() throws Exception {
        // Setup OMS with failing risk validator and fail-open config
        RiskValidator throwingValidator = order -> {
            throw new RuntimeException("Risk service unavailable");
        };
        RiskCheckService riskService = new RiskCheckService(
            throwingValidator,
            RiskCheckService.RiskCheckConfig.failOpen()
        );
        
        omsService = new OMSService(eventBus, tempDir.resolve("commands.log"), riskService);
        
        // Submit order
        OMSService.SubmissionResult result = omsService.submitOrder(
            "TEST-ORDER-3",
            "AAPL",
            (byte) 1,  // BUY
            (byte) 2,  // LIMIT
            100L,
            15000L,
            999L,
            UUID.randomUUID().toString()
        );
        
        // Verify order was accepted (fail-open allows it)
        assertTrue(result.success());
        assertFalse(result.riskRejected());
        
        // Verify event was published
        assertEquals(1, eventBus.getPublishedEventCount());
        
        // Verify metrics show failure
        RiskCheckService.RiskMetrics metrics = omsService.getRiskMetrics();
        assertEquals(1, metrics.getTotalChecks());
        assertEquals(1, metrics.getFailureCount());
    }
    
    @Test
    void testFailClosedBehavior() throws Exception {
        // Setup OMS with failing risk validator and fail-closed config
        RiskValidator throwingValidator = order -> {
            throw new RuntimeException("Risk service unavailable");
        };
        RiskCheckService riskService = new RiskCheckService(
            throwingValidator,
            RiskCheckService.RiskCheckConfig.failClosed()
        );
        
        omsService = new OMSService(eventBus, tempDir.resolve("commands.log"), riskService);
        
        // Submit order
        OMSService.SubmissionResult result = omsService.submitOrder(
            "TEST-ORDER-4",
            "AAPL",
            (byte) 1,  // BUY
            (byte) 2,  // LIMIT
            100L,
            15000L,
            999L,
            UUID.randomUUID().toString()
        );
        
        // Verify order was rejected (fail-closed rejects it)
        assertFalse(result.success());
        assertTrue(result.riskRejected());
        assertEquals(999, result.riskReasonCode());
        assertTrue(result.message().contains("Risk service unavailable"));
        
        // Verify NO event was published
        assertEquals(0, eventBus.getPublishedEventCount());
        
        // Verify metrics show failure
        RiskCheckService.RiskMetrics metrics = omsService.getRiskMetrics();
        assertEquals(1, metrics.getTotalChecks());
        assertEquals(1, metrics.getFailureCount());
    }
    
    @Test
    void testRealRiskValidatorIntegration() throws Exception {
        // Setup OMS with real composite risk validator
        RiskValidator validator = new CompositeRiskValidator.Builder()
            .add(new CreditCheckModule(1_000_000L, true)) // Max notional: 1M
            .build();
        RiskCheckService riskService = new RiskCheckService(
            validator,
            RiskCheckService.RiskCheckConfig.defaultConfig()
        );
        
        omsService = new OMSService(eventBus, tempDir.resolve("commands.log"), riskService);
        
        // Test 1: Order within limit (should pass)
        OMSService.SubmissionResult result1 = omsService.submitOrder(
            "TEST-ORDER-5",
            "AAPL",
            (byte) 1,  // BUY
            (byte) 2,  // LIMIT
            100L,      // quantity
            5000L,     // price = 50.00 * 100 = 5,000 notional (within limit)
            999L,
            UUID.randomUUID().toString()
        );
        
        assertTrue(result1.success());
        assertFalse(result1.riskRejected());
        
        // Test 2: Order exceeding limit (should fail)
        OMSService.SubmissionResult result2 = omsService.submitOrder(
            "TEST-ORDER-6",
            "AAPL",
            (byte) 1,  // BUY
            (byte) 2,  // LIMIT
            100L,      // quantity
            20000L,    // price = 200.00, notional = 100 * 20000 = 2,000,000 (exceeds 1M limit)
            999L,
            UUID.randomUUID().toString()
        );
        
        // This should be rejected as notional (2M) exceeds credit limit (1M)
        assertFalse(result2.success());
        assertTrue(result2.riskRejected());
        assertEquals(RiskReasonCode.CREDIT_LIMIT_EXCEEDED, result2.riskReasonCode());
        
        // Verify metrics
        RiskCheckService.RiskMetrics metrics = omsService.getRiskMetrics();
        assertEquals(2, metrics.getTotalChecks());
        assertEquals(1, metrics.getApprovedCount());
        assertEquals(1, metrics.getRejectedCount());
    }
    
    @Test
    void testLatencyRequirement() throws Exception {
        // Test that OMS + risk check completes within 10ms p99
        RiskValidator fastValidator = order -> RiskDecision.APPROVED;
        RiskCheckService riskService = new RiskCheckService(
            fastValidator,
            RiskCheckService.RiskCheckConfig.defaultConfig()
        );
        
        omsService = new OMSService(eventBus, tempDir.resolve("commands.log"), riskService);
        
        // Warm up
        for (int i = 0; i < 100; i++) {
            omsService.submitOrder(
                "WARMUP-" + i,
                "AAPL",
                (byte) 1,
                (byte) 2,
                100L,
                15000L,
                999L,
                UUID.randomUUID().toString()
            );
        }
        
        // Measure latencies
        long[] latencies = new long[1000];
        for (int i = 0; i < 1000; i++) {
            long start = System.nanoTime();
            OMSService.SubmissionResult result = omsService.submitOrder(
                "PERF-TEST-" + i,
                "AAPL",
                (byte) 1,
                (byte) 2,
                100L,
                15000L,
                999L,
                UUID.randomUUID().toString()
            );
            latencies[i] = System.nanoTime() - start;
            assertTrue(result.success());
        }
        
        // Calculate p99
        java.util.Arrays.sort(latencies);
        long p99Nanos = latencies[990];
        long p99Millis = p99Nanos / 1_000_000;
        
        // p99 should be less than 10ms
        assertTrue(p99Millis < 10, "p99 latency was " + p99Millis + "ms, expected < 10ms");
        
        System.out.println("OMS + Risk Check Performance:");
        System.out.println("  p50: " + (latencies[500] / 1_000_000) + "ms");
        System.out.println("  p95: " + (latencies[950] / 1_000_000) + "ms");
        System.out.println("  p99: " + p99Millis + "ms");
    }
    
    @Test
    void testMultipleOrdersWithMixedResults() throws Exception {
        // Setup validator that rejects every 3rd order
        int[] counter = {0};
        RiskValidator selectiveValidator = order -> {
            counter[0]++;
            if (counter[0] % 3 == 0) {
                return RiskDecision.reject(100, "Every 3rd order rejected");
            }
            return RiskDecision.APPROVED;
        };
        
        RiskCheckService riskService = new RiskCheckService(
            selectiveValidator,
            RiskCheckService.RiskCheckConfig.defaultConfig()
        );
        
        omsService = new OMSService(eventBus, tempDir.resolve("commands.log"), riskService);
        
        int successCount = 0;
        int rejectCount = 0;
        
        // Submit 10 orders
        for (int i = 0; i < 10; i++) {
            OMSService.SubmissionResult result = omsService.submitOrder(
                "MIXED-TEST-" + i,
                "AAPL",
                (byte) 1,
                (byte) 2,
                100L,
                15000L,
                999L,
                UUID.randomUUID().toString()
            );
            
            if (result.success()) {
                successCount++;
            } else if (result.riskRejected()) {
                rejectCount++;
            }
        }
        
        // Should have 7 successes and 3 rejections (every 3rd rejected)
        assertEquals(7, successCount);
        assertEquals(3, rejectCount);
        
        // Verify metrics
        RiskCheckService.RiskMetrics metrics = omsService.getRiskMetrics();
        assertEquals(10, metrics.getTotalChecks());
        assertEquals(7, metrics.getApprovedCount());
        assertEquals(3, metrics.getRejectedCount());
        assertEquals(0.7, metrics.getApprovalRate(), 0.01);
        assertEquals(0.3, metrics.getRejectionRate(), 0.01);
    }
}
