package com.thelastwar.oms;

import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.risk.RiskDecision;
import com.thelastwar.risk.RiskValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RiskCheckService.
 */
class RiskCheckServiceTest {
    
    @Test
    void testApprovedOrder() {
        // Always approve
        RiskValidator alwaysApprove = order -> RiskDecision.APPROVED;
        RiskCheckService service = new RiskCheckService(
            alwaysApprove,
            RiskCheckService.RiskCheckConfig.defaultConfig()
        );
        
        OrderEvent order = createTestOrder();
        RiskCheckService.RiskCheckResult result = service.check(order);
        
        assertTrue(result.approved());
        assertTrue(result.serviceAvailable());
        assertEquals(0, result.attempts());
        assertTrue(result.latencyNanos() > 0);
    }
    
    @Test
    void testRejectedOrder() {
        // Always reject
        RiskValidator alwaysReject = order -> RiskDecision.reject(100, "Test rejection");
        RiskCheckService service = new RiskCheckService(
            alwaysReject,
            RiskCheckService.RiskCheckConfig.defaultConfig()
        );
        
        OrderEvent order = createTestOrder();
        RiskCheckService.RiskCheckResult result = service.check(order);
        
        assertFalse(result.approved());
        assertTrue(result.serviceAvailable());
        assertEquals(0, result.attempts());
        assertEquals(100, result.decision().reasonCode());
        assertEquals("Test rejection", result.decision().message());
    }
    
    @Test
    void testFailOpenBehavior() {
        // Validator that throws exception
        RiskValidator throwingValidator = order -> {
            throw new RuntimeException("Service unavailable");
        };
        
        RiskCheckService service = new RiskCheckService(
            throwingValidator,
            RiskCheckService.RiskCheckConfig.failOpen()
        );
        
        OrderEvent order = createTestOrder();
        RiskCheckService.RiskCheckResult result = service.check(order);
        
        // Fail-open: should approve despite exception
        assertTrue(result.approved());
        assertFalse(result.serviceAvailable());
        assertEquals(3, result.attempts()); // maxRetries = 3
        assertNotNull(result.exception());
    }
    
    @Test
    void testFailClosedBehavior() {
        // Validator that throws exception
        RiskValidator throwingValidator = order -> {
            throw new RuntimeException("Service unavailable");
        };
        
        RiskCheckService service = new RiskCheckService(
            throwingValidator,
            RiskCheckService.RiskCheckConfig.failClosed()
        );
        
        OrderEvent order = createTestOrder();
        RiskCheckService.RiskCheckResult result = service.check(order);
        
        // Fail-closed: should reject due to exception
        assertFalse(result.approved());
        assertFalse(result.serviceAvailable());
        assertEquals(3, result.attempts()); // maxRetries = 3
        assertEquals(999, result.decision().reasonCode());
        assertEquals("Risk service unavailable", result.decision().message());
        assertNotNull(result.exception());
    }
    
    @Test
    void testRetryWithEventualSuccess() {
        // Validator that fails first 2 times, then succeeds
        int[] attemptCount = {0};
        RiskValidator intermittentValidator = order -> {
            attemptCount[0]++;
            if (attemptCount[0] < 3) {
                throw new RuntimeException("Temporary failure");
            }
            return RiskDecision.APPROVED;
        };
        
        RiskCheckService service = new RiskCheckService(
            intermittentValidator,
            RiskCheckService.RiskCheckConfig.defaultConfig()
        );
        
        OrderEvent order = createTestOrder();
        RiskCheckService.RiskCheckResult result = service.check(order);
        
        // Should succeed after retries
        assertTrue(result.approved());
        assertTrue(result.serviceAvailable());
        assertEquals(2, result.attempts()); // 2 retries before success
        assertEquals(3, attemptCount[0]); // Total calls made
    }
    
    @Test
    void testNoRetryConfiguration() {
        // Validator that always throws
        RiskValidator throwingValidator = order -> {
            throw new RuntimeException("Service unavailable");
        };
        
        RiskCheckService service = new RiskCheckService(
            throwingValidator,
            RiskCheckService.RiskCheckConfig.noRetry(true)
        );
        
        OrderEvent order = createTestOrder();
        RiskCheckService.RiskCheckResult result = service.check(order);
        
        // Should fail after single attempt
        assertTrue(result.approved()); // fail-open
        assertFalse(result.serviceAvailable());
        assertEquals(1, result.attempts()); // No retries
    }
    
    @Test
    void testMetricsTracking() {
        RiskValidator alwaysApprove = order -> RiskDecision.APPROVED;
        RiskCheckService service = new RiskCheckService(
            alwaysApprove,
            RiskCheckService.RiskCheckConfig.defaultConfig()
        );
        
        // Perform multiple checks
        for (int i = 0; i < 10; i++) {
            service.check(createTestOrder());
        }
        
        RiskCheckService.RiskMetrics metrics = service.getMetrics();
        
        assertEquals(10, metrics.getTotalChecks());
        assertEquals(10, metrics.getApprovedCount());
        assertEquals(0, metrics.getRejectedCount());
        assertEquals(0, metrics.getFailureCount());
        assertEquals(1.0, metrics.getApprovalRate(), 0.001);
        assertTrue(metrics.getAverageLatencyNanos() > 0);
        assertTrue(metrics.getMaxLatencyNanos() > 0);
    }
    
    @Test
    void testMetricsWithRejections() {
        RiskValidator alwaysReject = order -> RiskDecision.reject(100, "Test");
        RiskCheckService service = new RiskCheckService(
            alwaysReject,
            RiskCheckService.RiskCheckConfig.defaultConfig()
        );
        
        // Perform multiple checks
        for (int i = 0; i < 5; i++) {
            service.check(createTestOrder());
        }
        
        RiskCheckService.RiskMetrics metrics = service.getMetrics();
        
        assertEquals(5, metrics.getTotalChecks());
        assertEquals(0, metrics.getApprovedCount());
        assertEquals(5, metrics.getRejectedCount());
        assertEquals(0, metrics.getFailureCount());
        assertEquals(1.0, metrics.getRejectionRate(), 0.001);
    }
    
    @Test
    void testMetricsWithFailures() {
        RiskValidator throwingValidator = order -> {
            throw new RuntimeException("Error");
        };
        RiskCheckService service = new RiskCheckService(
            throwingValidator,
            RiskCheckService.RiskCheckConfig.failOpen()
        );
        
        // Perform multiple checks
        for (int i = 0; i < 3; i++) {
            service.check(createTestOrder());
        }
        
        RiskCheckService.RiskMetrics metrics = service.getMetrics();
        
        assertEquals(3, metrics.getTotalChecks());
        assertEquals(0, metrics.getRejectedCount());
        assertEquals(3, metrics.getFailureCount());
        assertEquals(1.0, metrics.getFailureRate(), 0.001);
    }
    
    @Test
    void testBackoffCalculation() {
        // Test that backoff increases exponentially
        RiskValidator throwingValidator = order -> {
            throw new RuntimeException("Error");
        };
        
        RiskCheckService.RiskCheckConfig config = new RiskCheckService.RiskCheckConfig(
            false, 5, 10, 1000
        );
        
        RiskCheckService service = new RiskCheckService(throwingValidator, config);
        
        long startTime = System.nanoTime();
        service.check(createTestOrder());
        long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms
        
        // With 5 retries and exponential backoff (10ms base):
        // Attempt 1: 10ms, Attempt 2: 20ms, Attempt 3: 40ms, Attempt 4: 80ms
        // Total should be at least 150ms but less than 200ms (with processing overhead)
        assertTrue(duration >= 100, "Duration was " + duration + "ms, expected >= 100ms");
        assertTrue(duration < 300, "Duration was " + duration + "ms, expected < 300ms");
    }
    
    @Test
    void testLatencyRequirement() {
        // Test that risk check completes quickly
        RiskValidator fastValidator = order -> RiskDecision.APPROVED;
        RiskCheckService service = new RiskCheckService(
            fastValidator,
            RiskCheckService.RiskCheckConfig.defaultConfig()
        );
        
        // Warm up
        for (int i = 0; i < 1000; i++) {
            service.check(createTestOrder());
        }
        
        // Measure latency
        long[] latencies = new long[1000];
        for (int i = 0; i < 1000; i++) {
            long start = System.nanoTime();
            service.check(createTestOrder());
            latencies[i] = System.nanoTime() - start;
        }
        
        // Calculate p99
        java.util.Arrays.sort(latencies);
        long p99 = latencies[990]; // 99th percentile
        
        // p99 should be less than 10µs (10,000 ns)
        assertTrue(p99 < 10_000, "p99 latency was " + p99 + "ns, expected < 10,000ns");
    }
    
    private OrderEvent createTestOrder() {
        return OrderEvent.newOrder(
            1L,           // orderId
            "AAPL",       // symbol
            (byte) 1,     // side (BUY)
            (byte) 2,     // orderType (LIMIT)
            100L,         // quantity
            15000L,       // price
            999L,         // account
            1             // status
        );
    }
}
