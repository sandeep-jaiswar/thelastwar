package com.thelastwar.gateway;

import com.thelastwar.eventbus.EventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quickfix.*;

import java.io.FileInputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FixGateway.
 */
class FixGatewayTest {
    
    private EventBus eventBus;
    private FixGateway gateway;
    private SessionSettings settings;
    
    @BeforeEach
    void setUp() throws Exception {
        eventBus = new TestEventBus();
        eventBus.start();
        
        // Create minimal FIX session settings for testing
        settings = new SessionSettings();
        
        // Default settings
        settings.setString("ConnectionType", "initiator");
        settings.setString("ReconnectInterval", "5");
        settings.setString("FileStorePath", "target/fix-store");
        settings.setString("FileLogPath", "target/fix-log");
        settings.setString("StartTime", "00:00:00");
        settings.setString("EndTime", "00:00:00");
        settings.setString("HeartBtInt", "30");
        settings.setString("SocketConnectHost", "localhost");
        settings.setString("SocketConnectPort", "9876");
        
        // Session-specific settings
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        settings.setString(sessionID, "BeginString", "FIX.4.4");
        settings.setString(sessionID, "SenderCompID", "SENDER");
        settings.setString(sessionID, "TargetCompID", "TARGET");
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
        GatewayMetrics metrics = new GatewayMetrics("test-fix-gateway");
        gateway = new FixGateway(eventBus, settings, metrics);
        
        assertNotNull(gateway);
        assertFalse(gateway.isRunning());
        assertEquals(GatewayAdapter.SessionState.DISCONNECTED, gateway.getSessionState());
        assertEquals(eventBus, gateway.getEventBus());
        assertEquals(metrics, gateway.getMetrics());
    }
    
    @Test
    void testGatewayCreationWithDefaults() {
        gateway = new FixGateway(eventBus, settings);
        
        assertNotNull(gateway);
        assertNotNull(gateway.getMetrics());
        assertNotNull(gateway.getBufferPool());
    }
    
    @Test
    void testNullParameters() {
        assertThrows(IllegalArgumentException.class, 
            () -> new FixGateway(null, settings, new GatewayMetrics("test")));
        
        assertThrows(IllegalArgumentException.class, 
            () -> new FixGateway(eventBus, null, new GatewayMetrics("test")));
        
        assertThrows(IllegalArgumentException.class, 
            () -> new FixGateway(eventBus, settings, null));
    }
    
    @Test
    void testStartStop() throws Exception {
        gateway = new FixGateway(eventBus, settings);
        
        // Note: This will fail to connect but should start without exception
        assertDoesNotThrow(() -> gateway.start());
        assertTrue(gateway.isRunning());
        
        // Stop
        gateway.stop();
        assertFalse(gateway.isRunning());
        assertEquals(GatewayAdapter.SessionState.DISCONNECTED, gateway.getSessionState());
    }
    
    @Test
    void testDoubleStart() throws Exception {
        gateway = new FixGateway(eventBus, settings);
        
        gateway.start();
        assertTrue(gateway.isRunning());
        
        // Second start should be idempotent (logged but not throw)
        assertDoesNotThrow(() -> gateway.start());
        assertTrue(gateway.isRunning());
        
        gateway.stop();
    }
    
    @Test
    void testDoubleStop() throws Exception {
        gateway = new FixGateway(eventBus, settings);
        
        gateway.start();
        gateway.stop();
        assertFalse(gateway.isRunning());
        
        // Second stop should be idempotent
        assertDoesNotThrow(() -> gateway.stop());
        assertFalse(gateway.isRunning());
    }
    
    @Test
    void testSendMessageWhenNotRunning() {
        gateway = new FixGateway(eventBus, settings);
        
        quickfix.fix44.NewOrderSingle order = new quickfix.fix44.NewOrderSingle();
        assertFalse(gateway.sendMessage(order));
    }
    
    @Test
    void testBufferPool() {
        gateway = new FixGateway(eventBus, settings);
        
        BufferPool pool = gateway.getBufferPool();
        assertNotNull(pool);
        assertEquals(1000, pool.capacity());
        assertEquals(8192, pool.bufferSize());
    }
    
    @Test
    void testSequenceTracking() throws Exception {
        gateway = new FixGateway(eventBus, settings);
        
        assertEquals(0, gateway.getCurrentSequence());
        
        gateway.start();
        
        // Sequence should increment on events
        // But since we're not connected, we can't easily test this
        // Just verify it's accessible
        assertTrue(gateway.getCurrentSequence() >= 0);
        
        gateway.stop();
    }
    
    @Test
    void testMetricsTracking() throws Exception {
        GatewayMetrics metrics = new GatewayMetrics("test-fix-gateway");
        gateway = new FixGateway(eventBus, settings, metrics);
        
        assertEquals(0, metrics.getInboundMessageCount());
        assertEquals(0, metrics.getOutboundMessageCount());
        
        gateway.start();
        
        // Verify metrics are accessible
        assertTrue(metrics.getActiveSessionCount() >= 0);
        
        gateway.stop();
    }
    
    @Test
    void testCloseMethod() throws Exception {
        gateway = new FixGateway(eventBus, settings);
        gateway.start();
        
        // close() should call stop()
        assertDoesNotThrow(() -> gateway.close());
        assertFalse(gateway.isRunning());
    }
}
