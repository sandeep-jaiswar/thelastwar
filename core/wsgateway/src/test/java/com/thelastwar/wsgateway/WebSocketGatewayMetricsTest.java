package com.thelastwar.wsgateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebSocketGatewayMetrics.
 */
class WebSocketGatewayMetricsTest {
    
    private WebSocketGatewayMetrics metrics;
    
    @BeforeEach
    void setUp() {
        metrics = new WebSocketGatewayMetrics("test-gateway");
    }
    
    @Test
    void testInitialState() {
        assertEquals(0, metrics.getInboundMessageCount());
        assertEquals(0, metrics.getOutboundMessageCount());
        assertEquals(0, metrics.getConcurrentClients());
        assertEquals(0, metrics.getTotalSubscriptions());
        assertEquals(0, metrics.getBackpressureEventCount());
    }
    
    @Test
    void testMessageCounting() {
        metrics.recordInboundMessage();
        assertEquals(1, metrics.getInboundMessageCount());
        
        metrics.recordOutboundMessage();
        assertEquals(1, metrics.getOutboundMessageCount());
        
        metrics.recordInboundMessage();
        metrics.recordOutboundMessage();
        assertEquals(2, metrics.getInboundMessageCount());
        assertEquals(2, metrics.getOutboundMessageCount());
    }
    
    @Test
    void testConnectionTracking() {
        metrics.recordConnectionOpened();
        assertEquals(1, metrics.getConcurrentClients());
        
        metrics.recordConnectionOpened();
        assertEquals(2, metrics.getConcurrentClients());
        
        metrics.recordConnectionClosed();
        assertEquals(1, metrics.getConcurrentClients());
        
        metrics.recordConnectionClosed();
        assertEquals(0, metrics.getConcurrentClients());
    }
    
    @Test
    void testSubscriptionTracking() {
        metrics.recordSubscription();
        assertEquals(1, metrics.getTotalSubscriptions());
        
        metrics.recordSubscription();
        assertEquals(2, metrics.getTotalSubscriptions());
        
        metrics.recordUnsubscription();
        assertEquals(1, metrics.getTotalSubscriptions());
    }
    
    @Test
    void testBackpressureTracking() {
        metrics.recordBackpressureEvent();
        assertEquals(1, metrics.getBackpressureEventCount());
        
        metrics.recordBackpressureEvent();
        assertEquals(2, metrics.getBackpressureEventCount());
    }
    
    @Test
    void testLatencyTimers() {
        var broadcastTimer = metrics.startBroadcastTimer();
        assertNotNull(broadcastTimer);
        metrics.recordBroadcastLatency(broadcastTimer);
        
        var serializationTimer = metrics.startSerializationTimer();
        assertNotNull(serializationTimer);
        metrics.recordSerializationLatency(serializationTimer);
    }
    
    @Test
    void testRegistry() {
        assertNotNull(metrics.getRegistry());
    }
}
