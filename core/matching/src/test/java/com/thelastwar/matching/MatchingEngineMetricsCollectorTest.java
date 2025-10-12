package com.thelastwar.matching;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MatchingEngineMetricsCollector.
 */
class MatchingEngineMetricsCollectorTest {
    
    private MatchingEngineMetricsCollector metrics;
    private MeterRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MatchingEngineMetricsCollector(registry, "test-engine");
    }
    
    @Test
    void testOrdersProcessedCounter() {
        // Initially zero
        assertEquals(0, metrics.getOrdersProcessedCount());
        
        // Record some orders
        metrics.recordOrderProcessed();
        metrics.recordOrderProcessed();
        metrics.recordOrderProcessed();
        
        // Verify count
        assertEquals(3, metrics.getOrdersProcessedCount());
    }
    
    @Test
    void testTradesGeneratedCounter() {
        // Initially zero
        assertEquals(0, metrics.getTradesGeneratedCount());
        
        // Record some trades
        metrics.recordTradeGenerated();
        metrics.recordTradeGenerated();
        
        // Verify count
        assertEquals(2, metrics.getTradesGeneratedCount());
    }
    
    @Test
    void testOrdersRejectedCounter() {
        // Initially zero
        assertEquals(0, metrics.getOrdersRejectedCount());
        
        // Record some rejections
        metrics.recordOrderRejected();
        
        // Verify count
        assertEquals(1, metrics.getOrdersRejectedCount());
    }
    
    @Test
    void testOrdersCancelledCounter() {
        // Initially zero
        assertEquals(0, metrics.getOrdersCancelledCount());
        
        // Record some cancellations
        metrics.recordOrderCancelled();
        metrics.recordOrderCancelled();
        
        // Verify count
        assertEquals(2, metrics.getOrdersCancelledCount());
    }
    
    @Test
    void testOrdersModifiedCounter() {
        // Initially zero
        assertEquals(0, metrics.getOrdersModifiedCount());
        
        // Record some modifications
        metrics.recordOrderModified();
        
        // Verify count
        assertEquals(1, metrics.getOrdersModifiedCount());
    }
    
    @Test
    void testMatchLatencyRecording() {
        // Record some latencies
        metrics.recordMatchLatency(5000); // 5 microseconds
        metrics.recordMatchLatency(10000); // 10 microseconds
        metrics.recordMatchLatency(15000); // 15 microseconds
        
        // Verify percentiles are available (exact values depend on histogram implementation)
        double p50 = metrics.getP50MatchLatencyNanos();
        double p95 = metrics.getP95MatchLatencyNanos();
        double p99 = metrics.getP99MatchLatencyNanos();
        
        // Sanity checks
        assertTrue(p50 >= 0);
        assertTrue(p95 >= p50);
        assertTrue(p99 >= p95);
    }
    
    @Test
    void testCancelLatencyRecording() {
        // Record some latencies
        metrics.recordCancelLatency(3000); // 3 microseconds
        metrics.recordCancelLatency(4000); // 4 microseconds
        
        // No assertion - just verify it doesn't throw
        // (SimpleMeterRegistry doesn't provide snapshot for testing easily)
    }
    
    @Test
    void testModifyLatencyRecording() {
        // Record some latencies
        metrics.recordModifyLatency(6000); // 6 microseconds
        metrics.recordModifyLatency(7000); // 7 microseconds
        
        // No assertion - just verify it doesn't throw
    }
    
    @Test
    void testQueueDepthGauge() {
        // Initially zero
        assertEquals(0, metrics.getQueueDepth());
        
        // Update queue depth
        metrics.updateQueueDepth(10);
        assertEquals(10, metrics.getQueueDepth());
        
        // Update again
        metrics.updateQueueDepth(25);
        assertEquals(25, metrics.getQueueDepth());
        
        // Can go back down
        metrics.updateQueueDepth(5);
        assertEquals(5, metrics.getQueueDepth());
    }
    
    @Test
    void testThroughput() {
        // Initially zero
        assertEquals(0.0, metrics.getThroughput(), 0.001);
        
        // Process some orders
        metrics.recordOrderProcessed();
        metrics.recordOrderProcessed();
        metrics.recordOrderProcessed();
        
        // Throughput should equal processed count
        assertEquals(3.0, metrics.getThroughput(), 0.001);
    }
    
    @Test
    void testGetRegistry() {
        assertNotNull(metrics.getRegistry());
        assertEquals(registry, metrics.getRegistry());
    }
    
    @Test
    void testGetEngineName() {
        assertEquals("test-engine", metrics.getEngineName());
    }
    
    @Test
    void testConstructorWithDefaultRegistry() {
        MatchingEngineMetricsCollector metricsWithDefault = new MatchingEngineMetricsCollector("default-engine");
        
        assertNotNull(metricsWithDefault);
        assertNotNull(metricsWithDefault.getRegistry());
        assertEquals("default-engine", metricsWithDefault.getEngineName());
        
        // Test that it works
        metricsWithDefault.recordOrderProcessed();
        assertEquals(1, metricsWithDefault.getOrdersProcessedCount());
    }
    
    @Test
    void testHighLatencyScenario() {
        // Simulate high latency scenario (> 15 microseconds)
        metrics.recordMatchLatency(20000); // 20 µs
        metrics.recordMatchLatency(25000); // 25 µs
        metrics.recordMatchLatency(30000); // 30 µs
        
        // Record that we processed orders
        metrics.recordOrderProcessed();
        metrics.recordOrderProcessed();
        metrics.recordOrderProcessed();
        
        // Verify
        assertEquals(3, metrics.getOrdersProcessedCount());
        
        // p99 should be measurable
        double p99 = metrics.getP99MatchLatencyNanos();
        assertTrue(p99 >= 0);
    }
    
    @Test
    void testConcurrentOperations() {
        // Simulate concurrent order processing
        for (int i = 0; i < 1000; i++) {
            metrics.recordOrderProcessed();
            metrics.recordMatchLatency(5000 + i);
            
            if (i % 10 == 0) {
                metrics.recordTradeGenerated();
            }
            
            if (i % 50 == 0) {
                metrics.recordOrderCancelled();
            }
        }
        
        // Verify counts
        assertEquals(1000, metrics.getOrdersProcessedCount());
        assertEquals(100, metrics.getTradesGeneratedCount());
        assertEquals(20, metrics.getOrdersCancelledCount());
    }
}
