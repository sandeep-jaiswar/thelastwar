package com.thelastwar.eventbus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BackpressureMonitor.
 */
class BackpressureMonitorTest {

    @Test
    void testMonitorCreation() {
        BackpressureMonitor monitor = new BackpressureMonitor(1000);
        
        assertEquals(1000, monitor.getRingBufferSize());
        assertEquals(800, monitor.getHighWatermark()); // 80% default
        assertEquals(500, monitor.getLowWatermark()); // 50% default
        assertFalse(monitor.isBackpressureActive());
        assertEquals(0, monitor.getPendingMessages());
    }

    @Test
    void testCustomWatermarks() {
        BackpressureMonitor monitor = new BackpressureMonitor(1000, 900, 300);
        
        assertEquals(900, monitor.getHighWatermark());
        assertEquals(300, monitor.getLowWatermark());
    }

    @Test
    void testInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BackpressureMonitor(0); // Invalid size
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new BackpressureMonitor(1000, 300, 500); // High < Low
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new BackpressureMonitor(1000, 1100, 500); // High > Size
        });
    }

    @Test
    void testBackpressureActivation() {
        BackpressureMonitor monitor = new BackpressureMonitor(1000, 800, 500);
        
        // Publish events up to just below high watermark
        for (int i = 1; i <= 799; i++) {
            monitor.updateProducerPosition(i);
            assertFalse(monitor.isBackpressureActive(), "Should not activate at " + i);
        }
        
        // Publish one more to exceed high watermark
        monitor.updateProducerPosition(800);
        assertTrue(monitor.isBackpressureActive(), "Should activate at high watermark");
    }

    @Test
    void testBackpressureDeactivation() {
        BackpressureMonitor monitor = new BackpressureMonitor(1000, 800, 500);
        
        // Activate backpressure
        monitor.updateProducerPosition(800);
        assertTrue(monitor.isBackpressureActive());
        
        // Consume down to just above low watermark (501 pending)
        monitor.updateConsumerPosition(299);
        assertTrue(monitor.isBackpressureActive(), "Should still be active");
        
        // Consume down to low watermark (500 pending)
        monitor.updateConsumerPosition(300);
        assertFalse(monitor.isBackpressureActive(), "Should deactivate at low watermark");
    }

    @Test
    void testPendingMessages() {
        BackpressureMonitor monitor = new BackpressureMonitor(1000);
        
        monitor.updateProducerPosition(100);
        assertEquals(100, monitor.getPendingMessages());
        
        monitor.updateConsumerPosition(30);
        assertEquals(70, monitor.getPendingMessages());
        
        monitor.updateConsumerPosition(100);
        assertEquals(0, monitor.getPendingMessages());
    }

    @Test
    void testUtilizationPercent() {
        BackpressureMonitor monitor = new BackpressureMonitor(1000);
        
        monitor.updateProducerPosition(0);
        assertEquals(0, monitor.getUtilizationPercent());
        
        monitor.updateProducerPosition(500);
        assertEquals(50, monitor.getUtilizationPercent());
        
        monitor.updateProducerPosition(1000);
        assertEquals(100, monitor.getUtilizationPercent());
        
        monitor.updateConsumerPosition(500);
        assertEquals(50, monitor.getUtilizationPercent());
    }

    @Test
    void testReset() {
        BackpressureMonitor monitor = new BackpressureMonitor(1000);
        
        monitor.updateProducerPosition(800);
        monitor.updateConsumerPosition(100);
        assertTrue(monitor.isBackpressureActive());
        
        monitor.reset();
        
        assertEquals(0, monitor.getPendingMessages());
        assertFalse(monitor.isBackpressureActive());
    }

    @Test
    void testBackpressureHysteresis() {
        // Test that backpressure doesn't flap between active and inactive
        BackpressureMonitor monitor = new BackpressureMonitor(1000, 800, 500);
        
        // Activate backpressure
        monitor.updateProducerPosition(850);
        assertTrue(monitor.isBackpressureActive());
        
        // Consume a bit, but still above low watermark
        monitor.updateConsumerPosition(200); // 650 pending
        assertTrue(monitor.isBackpressureActive(), "Should remain active above low watermark");
        
        // Consume to exactly low watermark
        monitor.updateConsumerPosition(350); // 500 pending
        assertFalse(monitor.isBackpressureActive(), "Should deactivate at low watermark");
        
        // Publish a bit more, but below high watermark
        monitor.updateProducerPosition(900); // 550 pending
        assertFalse(monitor.isBackpressureActive(), "Should remain inactive below high watermark");
    }
}
