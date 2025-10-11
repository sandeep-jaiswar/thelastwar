package com.thelastwar.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageEnvelopePool.
 */
class MessageEnvelopePoolTest {
    
    private MessageEnvelopePool pool;
    
    @BeforeEach
    void setUp() {
        pool = new MessageEnvelopePool(16); // Small pool for testing
    }
    
    @Test
    void testPoolCreationWithDefaultCapacity() {
        MessageEnvelopePool p = new MessageEnvelopePool(8);
        assertEquals(8, p.capacity());
        assertEquals(8192, p.getBufferCapacity());
    }
    
    @Test
    void testPoolCreationWithCustomCapacity() {
        MessageEnvelopePool p = new MessageEnvelopePool(16, 4096);
        assertEquals(16, p.capacity());
        assertEquals(4096, p.getBufferCapacity());
    }
    
    @Test
    void testPoolCreationWithInvalidSize() {
        // Not power of 2
        assertThrows(IllegalArgumentException.class, () -> new MessageEnvelopePool(15));
        
        // Too small
        assertThrows(IllegalArgumentException.class, () -> new MessageEnvelopePool(1));
        
        // Zero
        assertThrows(IllegalArgumentException.class, () -> new MessageEnvelopePool(0));
    }
    
    @Test
    void testPoolCreationWithInvalidBufferCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new MessageEnvelopePool(8, 0));
        assertThrows(IllegalArgumentException.class, () -> new MessageEnvelopePool(8, -1));
    }
    
    @Test
    void testAcquireAndRelease() {
        MessageEnvelope envelope = pool.acquire();
        assertNotNull(envelope);
        assertEquals(15, pool.size()); // One less available
        
        boolean released = pool.release(envelope);
        assertTrue(released);
        assertEquals(16, pool.size()); // Back to full
    }
    
    @Test
    void testAcquireReturnsResetEnvelope() {
        MessageEnvelope envelope = pool.acquire();
        
        // Modify the envelope
        envelope.setProtocolType(MessageEnvelope.ProtocolType.FIX);
        envelope.setCorrelationId(999L);
        envelope.setClientId("test");
        
        // Return to pool
        pool.release(envelope);
        
        // Acquire again - should be reset
        MessageEnvelope reacquired = pool.acquire();
        assertNull(reacquired.getProtocolType());
        assertEquals(0, reacquired.getCorrelationId());
        assertNull(reacquired.getClientId());
    }
    
    @Test
    void testAcquireWhenPoolEmpty() {
        // Drain the pool
        MessageEnvelope[] envelopes = new MessageEnvelope[16];
        for (int i = 0; i < 16; i++) {
            envelopes[i] = pool.acquire();
        }
        assertEquals(0, pool.size());
        
        // Acquire beyond pool capacity - should create new instance
        MessageEnvelope extra = pool.acquire();
        assertNotNull(extra);
        assertEquals(0, pool.size()); // Pool still empty
    }
    
    @Test
    void testReleaseNullEnvelope() {
        assertThrows(IllegalArgumentException.class, () -> pool.release(null));
    }
    
    @Test
    void testMultipleAcquireRelease() {
        MessageEnvelope env1 = pool.acquire();
        MessageEnvelope env2 = pool.acquire();
        MessageEnvelope env3 = pool.acquire();
        
        assertEquals(13, pool.size());
        
        pool.release(env1);
        assertEquals(14, pool.size());
        
        pool.release(env2);
        assertEquals(15, pool.size());
        
        pool.release(env3);
        assertEquals(16, pool.size());
    }
    
    @Test
    void testReleaseWhenPoolFull() {
        // Pool is already full at start
        assertEquals(16, pool.size());
        
        // Create a new envelope (not from pool)
        MessageEnvelope external = new MessageEnvelope(8192);
        
        // Try to release it - should fail because pool is full
        boolean released = pool.release(external);
        assertFalse(released);
        assertEquals(16, pool.size());
    }
    
    @Test
    void testClear() {
        pool.acquire();
        pool.acquire();
        assertEquals(14, pool.size());
        
        pool.clear();
        assertEquals(0, pool.size());
    }
    
    @Test
    void testPoolReuse() {
        MessageEnvelope env1 = pool.acquire();
        byte[] data = "test data".getBytes();
        env1.setPayload(data, 0, data.length);
        env1.setCorrelationId(123L);
        
        pool.release(env1);
        
        // Acquire should return a reset envelope
        MessageEnvelope env2 = pool.acquire();
        assertNotNull(env2);
        assertEquals(0, env2.getCorrelationId());
        assertEquals(0, env2.getPayloadLength());
    }
    
    @Test
    void testConcurrentAcquire() throws InterruptedException {
        final int threadCount = 4;
        final int acquisitionsPerThread = 100;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < acquisitionsPerThread; j++) {
                    MessageEnvelope env = pool.acquire();
                    assertNotNull(env);
                    // Simulate work
                    env.setCorrelationId(j);
                    // Return to pool
                    pool.release(env);
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Pool should be operational (size might vary due to concurrent access)
        assertTrue(pool.size() >= 0);
        assertTrue(pool.size() <= pool.capacity());
    }
    
    @Test
    void testPoolCapacity() {
        assertEquals(16, pool.capacity());
        
        MessageEnvelopePool largePool = new MessageEnvelopePool(256);
        assertEquals(256, largePool.capacity());
    }
    
    @Test
    void testBufferCapacity() {
        assertEquals(8192, pool.getBufferCapacity());
        
        MessageEnvelopePool customPool = new MessageEnvelopePool(8, 2048);
        assertEquals(2048, customPool.getBufferCapacity());
    }
    
    @Test
    void testPowerOfTwoSizes() {
        // Valid power of 2 sizes
        assertDoesNotThrow(() -> new MessageEnvelopePool(2));
        assertDoesNotThrow(() -> new MessageEnvelopePool(4));
        assertDoesNotThrow(() -> new MessageEnvelopePool(8));
        assertDoesNotThrow(() -> new MessageEnvelopePool(16));
        assertDoesNotThrow(() -> new MessageEnvelopePool(32));
        assertDoesNotThrow(() -> new MessageEnvelopePool(64));
        assertDoesNotThrow(() -> new MessageEnvelopePool(128));
        assertDoesNotThrow(() -> new MessageEnvelopePool(256));
        assertDoesNotThrow(() -> new MessageEnvelopePool(512));
        assertDoesNotThrow(() -> new MessageEnvelopePool(1024));
    }
}
