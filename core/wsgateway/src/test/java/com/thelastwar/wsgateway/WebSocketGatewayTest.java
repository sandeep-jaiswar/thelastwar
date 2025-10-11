package com.thelastwar.wsgateway;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.SourceId;
import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.gateway.GatewayAdapter;
import com.thelastwar.gateway.GatewayMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebSocketGateway.
 */
class WebSocketGatewayTest {
    
    private TestEventBus eventBus;
    private WebSocketGateway gateway;
    private GatewayMetrics metrics;
    
    @BeforeEach
    void setUp() {
        eventBus = new TestEventBus();
        eventBus.start();
        metrics = new GatewayMetrics("test-ws-gateway");
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (gateway != null && gateway.isRunning()) {
            gateway.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
    }
    
    @Test
    void testGatewayCreation() {
        gateway = new WebSocketGateway(eventBus, 8080, TransportFormat.JSON, metrics);
        
        assertNotNull(gateway);
        assertFalse(gateway.isRunning());
        assertEquals(eventBus, gateway.getEventBus());
        assertEquals(metrics, gateway.getMetrics());
        assertNotNull(gateway.getWebSocketMetrics());
    }
    
    @Test
    void testGatewayCreationWithDefaults() {
        gateway = new WebSocketGateway(eventBus, 8081, TransportFormat.JSON);
        
        assertNotNull(gateway);
        assertNotNull(gateway.getMetrics());
        assertNotNull(gateway.getWebSocketMetrics());
    }
    
    @Test
    void testNullParameters() {
        assertThrows(IllegalArgumentException.class, 
            () -> new WebSocketGateway(null, 8080, TransportFormat.JSON, metrics));
        
        assertThrows(IllegalArgumentException.class, 
            () -> new WebSocketGateway(eventBus, 8080, TransportFormat.JSON, null));
    }
    
    @Test
    void testStartAndStop() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8082, TransportFormat.JSON, metrics);
        
        assertFalse(gateway.isRunning());
        gateway.start();
        assertTrue(gateway.isRunning());
        
        gateway.stop();
        assertFalse(gateway.isRunning());
    }
    
    @Test
    void testMultipleStarts() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8083, TransportFormat.JSON, metrics);
        
        gateway.start();
        assertTrue(gateway.isRunning());
        
        // Second start should throw exception
        assertThrows(Exception.class, () -> gateway.start());
    }
    
    @Test
    void testStopWhenNotRunning() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8084, TransportFormat.JSON, metrics);
        
        // Should not throw exception
        assertDoesNotThrow(() -> gateway.stop());
    }
    
    @Test
    void testSessionState() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8085, TransportFormat.JSON, metrics);
        
        assertEquals(GatewayAdapter.SessionState.DISCONNECTED, gateway.getSessionState());
        
        gateway.start();
        assertEquals(GatewayAdapter.SessionState.CONNECTED, gateway.getSessionState());
        
        gateway.stop();
        assertEquals(GatewayAdapter.SessionState.DISCONNECTED, gateway.getSessionState());
    }
    
    @Test
    void testTransportFormats() {
        WebSocketGateway jsonGateway = new WebSocketGateway(eventBus, 8086, TransportFormat.JSON, metrics);
        assertNotNull(jsonGateway);
        
        WebSocketGateway msgpackGateway = new WebSocketGateway(eventBus, 8087, TransportFormat.MESSAGEPACK, metrics);
        assertNotNull(msgpackGateway);
    }
    
    @Test
    void testMetricsIntegration() {
        gateway = new WebSocketGateway(eventBus, 8088, TransportFormat.JSON, metrics);
        
        WebSocketGatewayMetrics wsMetrics = gateway.getWebSocketMetrics();
        assertNotNull(wsMetrics);
        assertEquals(0, wsMetrics.getConcurrentClients());
        assertEquals(0, wsMetrics.getTotalSubscriptions());
    }
}
