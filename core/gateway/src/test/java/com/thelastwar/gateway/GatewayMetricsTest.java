package com.thelastwar.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GatewayMetrics.
 */
class GatewayMetricsTest {
    
    private GatewayMetrics metrics;
    
    @BeforeEach
    void setUp() {
        metrics = new GatewayMetrics("test-gateway");
    }
    
    @Test
    void testInitialState() {
        assertEquals(0, metrics.getInboundMessageCount());
        assertEquals(0, metrics.getOutboundMessageCount());
        assertEquals(0, metrics.getDecodeErrorCount());
        assertEquals(0, metrics.getEncodeErrorCount());
        assertEquals(0, metrics.getActiveSessionCount());
    }
    
    @Test
    void testRecordInboundMessage() {
        metrics.recordInboundMessage();
        assertEquals(1, metrics.getInboundMessageCount());
        
        metrics.recordInboundMessage();
        assertEquals(2, metrics.getInboundMessageCount());
    }
    
    @Test
    void testRecordOutboundMessage() {
        metrics.recordOutboundMessage();
        assertEquals(1, metrics.getOutboundMessageCount());
        
        metrics.recordOutboundMessage();
        assertEquals(2, metrics.getOutboundMessageCount());
    }
    
    @Test
    void testRecordErrors() {
        metrics.recordDecodeError();
        assertEquals(1, metrics.getDecodeErrorCount());
        
        metrics.recordEncodeError();
        assertEquals(1, metrics.getEncodeErrorCount());
    }
    
    @Test
    void testSessionTracking() {
        assertEquals(0, metrics.getActiveSessionCount());
        
        metrics.recordSessionConnect();
        assertEquals(1, metrics.getActiveSessionCount());
        
        metrics.recordSessionConnect();
        assertEquals(2, metrics.getActiveSessionCount());
        
        metrics.recordSessionDisconnect();
        assertEquals(1, metrics.getActiveSessionCount());
        
        metrics.recordSessionDisconnect();
        assertEquals(0, metrics.getActiveSessionCount());
    }
    
    @Test
    void testQueueDepth() {
        metrics.setInboundQueueDepth(100);
        metrics.setOutboundQueueDepth(50);
        
        // Just verify no exceptions are thrown
        // Actual values are gauges and may not be immediately retrievable
    }
    
    @Test
    void testLatencyTimers() {
        // Test decode latency
        var decodeSample = metrics.startDecodeTimer();
        metrics.recordDecodeLatency(decodeSample);
        
        // Test encode latency
        var encodeSample = metrics.startEncodeTimer();
        metrics.recordEncodeLatency(encodeSample);
        
        // Test round trip latency
        var rtSample = metrics.startRoundTripTimer();
        metrics.recordRoundTripLatency(rtSample);
        
        // Verify no exceptions and latencies are >= 0
        assertTrue(metrics.getP99DecodeLatencyNanos() >= 0);
        assertTrue(metrics.getP99EncodeLatencyNanos() >= 0);
    }
    
    @Test
    void testThroughput() {
        metrics.recordInboundMessage();
        metrics.recordOutboundMessage();
        
        assertEquals(2.0, metrics.getThroughput());
    }
    
    @Test
    void testMetricsRegistry() {
        assertNotNull(metrics.getRegistry());
    }
}
