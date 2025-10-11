package com.thelastwar.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BufferPool.
 */
class BufferPoolTest {
    
    private BufferPool pool;
    
    @BeforeEach
    void setUp() {
        pool = new BufferPool(10, 1024); // 10 buffers of 1KB each
    }
    
    @AfterEach
    void tearDown() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }
    
    @Test
    void testPoolCreation() {
        assertEquals(10, pool.capacity());
        assertEquals(1024, pool.bufferSize());
        assertEquals(10, pool.available());
    }
    
    @Test
    void testAcquireAndRelease() throws InterruptedException {
        ByteBuffer buffer = pool.acquire();
        assertNotNull(buffer);
        assertEquals(1024, buffer.capacity());
        assertEquals(9, pool.available());
        
        assertTrue(pool.release(buffer));
        assertEquals(10, pool.available());
    }
    
    @Test
    void testTryAcquire() {
        ByteBuffer buffer = pool.tryAcquire();
        assertNotNull(buffer);
        assertEquals(9, pool.available());
        
        pool.release(buffer);
    }
    
    @Test
    void testAcquireWithTimeout() throws InterruptedException {
        ByteBuffer buffer = pool.acquire(100, TimeUnit.MILLISECONDS);
        assertNotNull(buffer);
        assertEquals(9, pool.available());
        
        pool.release(buffer);
    }
    
    @Test
    void testBufferCleared() throws InterruptedException {
        ByteBuffer buffer = pool.acquire();
        buffer.putInt(12345);
        pool.release(buffer);
        
        // Acquire again - should be cleared
        ByteBuffer buffer2 = pool.acquire();
        assertEquals(0, buffer2.position());
        assertEquals(1024, buffer2.limit());
        
        pool.release(buffer2);
    }
    
    @Test
    void testUtilization() throws InterruptedException {
        assertEquals(0.0, pool.utilization(), 0.01);
        
        ByteBuffer b1 = pool.acquire();
        assertEquals(10.0, pool.utilization(), 0.01);
        
        ByteBuffer b2 = pool.acquire();
        assertEquals(20.0, pool.utilization(), 0.01);
        
        pool.release(b1);
        assertEquals(10.0, pool.utilization(), 0.01);
        
        pool.release(b2);
        assertEquals(0.0, pool.utilization(), 0.01);
    }
    
    @Test
    void testExhaustion() throws InterruptedException {
        // Acquire all buffers
        for (int i = 0; i < 10; i++) {
            pool.acquire();
        }
        assertEquals(0, pool.available());
        
        // Try to acquire - should return null
        ByteBuffer buffer = pool.tryAcquire();
        assertNull(buffer);
    }
    
    @Test
    void testInvalidBuffer() throws InterruptedException {
        ByteBuffer wrongSizeBuffer = ByteBuffer.allocateDirect(2048);
        assertThrows(IllegalArgumentException.class, () -> pool.release(wrongSizeBuffer));
    }
    
    @Test
    void testNullBuffer() {
        assertThrows(IllegalArgumentException.class, () -> pool.release(null));
    }
    
    @Test
    void testClosedPool() {
        pool.close();
        assertTrue(pool.isClosed());
        
        assertThrows(IllegalStateException.class, () -> pool.acquire());
        assertThrows(IllegalStateException.class, () -> pool.tryAcquire());
    }
    
    @Test
    void testInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new BufferPool(0, 1024));
        assertThrows(IllegalArgumentException.class, () -> new BufferPool(10, 0));
        assertThrows(IllegalArgumentException.class, () -> new BufferPool(-1, 1024));
        assertThrows(IllegalArgumentException.class, () -> new BufferPool(10, -1));
    }
}
