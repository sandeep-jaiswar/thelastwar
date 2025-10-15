package com.thelastwar.matching;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProfilingHooks.
 */
class ProfilingHooksTest {
    
    @Test
    void testDisabledByDefault() {
        ProfilingHooks hooks = new ProfilingHooks();
        assertFalse(hooks.isEnabled());
    }
    
    @Test
    void testEnabledProfiling() {
        ProfilingHooks hooks = new ProfilingHooks(true);
        assertTrue(hooks.isEnabled());
    }
    
    @Test
    void testOrderProcessingEvents() {
        ProfilingHooks hooks = new ProfilingHooks(true);
        
        long eventId = hooks.onOrderProcessingStart(1L, "AAPL");
        assertTrue(eventId > 0);
        
        hooks.onOrderProcessingEnd(eventId, 1L);
        // No assertion - just verify it doesn't throw
    }
    
    @Test
    void testMatchingEvents() {
        ProfilingHooks hooks = new ProfilingHooks(true);
        
        long eventId = hooks.onMatchingStart(1L, "AAPL");
        assertTrue(eventId > 0);
        
        hooks.onMatchingEnd(eventId, 1L, 2);
        // No assertion - just verify it doesn't throw
    }
    
    @Test
    void testTradeGeneratedEvent() {
        ProfilingHooks hooks = new ProfilingHooks(true);
        
        hooks.onTradeGenerated(100L, 1L, 100L, 15000L);
        // No assertion - just verify it doesn't throw
    }
    
    @Test
    void testOrderRejectedEvent() {
        ProfilingHooks hooks = new ProfilingHooks(true);
        
        hooks.onOrderRejected(1L, 404);
        // No assertion - just verify it doesn't throw
    }
    
    @Test
    void testOrderCancelledEvent() {
        ProfilingHooks hooks = new ProfilingHooks(true);
        
        hooks.onOrderCancelled(1L, "AAPL");
        // No assertion - just verify it doesn't throw
    }
    
    @Test
    void testOrderBookOperationEvents() {
        ProfilingHooks hooks = new ProfilingHooks(true);
        
        long eventId = hooks.onOrderBookOperationStart("AAPL", "add");
        assertTrue(eventId > 0);
        
        hooks.onOrderBookOperationEnd(eventId, "AAPL");
        // No assertion - just verify it doesn't throw
    }
    
    @Test
    void testFlush() {
        ProfilingHooks hooks = new ProfilingHooks(true);
        
        hooks.flush();
        // No assertion - just verify it doesn't throw
    }
    
    @Test
    void testDisabledProfilingNoOp() {
        ProfilingHooks hooks = new ProfilingHooks(false);
        
        // All operations should return 0 or do nothing when disabled
        assertEquals(0, hooks.onOrderProcessingStart(1L, "AAPL"));
        assertEquals(0, hooks.onMatchingStart(1L, "AAPL"));
        assertEquals(0, hooks.onOrderBookOperationStart("AAPL", "add"));
        
        // Should not throw
        hooks.onOrderProcessingEnd(0, 1L);
        hooks.onMatchingEnd(0, 1L, 0);
        hooks.onTradeGenerated(100L, 1L, 100L, 15000L);
        hooks.onOrderRejected(1L, 404);
        hooks.onOrderCancelled(1L, "AAPL");
        hooks.onOrderBookOperationEnd(0, "AAPL");
        hooks.flush();
    }
    
    @Test
    void testSequentialEventIds() {
        ProfilingHooks hooks = new ProfilingHooks(true);
        
        long eventId1 = hooks.onOrderProcessingStart(1L, "AAPL");
        long eventId2 = hooks.onMatchingStart(2L, "GOOGL");
        long eventId3 = hooks.onOrderBookOperationStart("MSFT", "add");
        
        // Event IDs should be sequential
        assertTrue(eventId2 > eventId1);
        assertTrue(eventId3 > eventId2);
    }
    
    @Test
    void testZeroOverheadWhenDisabled() {
        ProfilingHooks hooks = new ProfilingHooks(false);
        
        // Measure time for many no-op calls
        long iterations = 10000;
        long start = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            hooks.onOrderProcessingStart(i, "AAPL");
            hooks.onOrderProcessingEnd(0, i);
            hooks.onMatchingStart(i, "AAPL");
            hooks.onMatchingEnd(0, i, 0);
            hooks.onTradeGenerated(i, i, 100L, 15000L);
        }
        
        long end = System.nanoTime();
        long totalNanos = end - start;
        double avgNanosPerOp = totalNanos / (iterations * 5.0);
        
        // Should be nearly zero overhead (< 50ns per operation when disabled)
        // Note: Increased threshold to account for CI environment variability
        System.out.println("Average time per disabled profiling call: " + avgNanosPerOp + " ns");
        assertTrue(avgNanosPerOp < 50, "Disabled profiling should have < 50ns overhead per call");
    }
}
