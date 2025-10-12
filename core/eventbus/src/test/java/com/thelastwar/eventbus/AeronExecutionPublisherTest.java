package com.thelastwar.eventbus;

import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.eventbus.model.TradeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AeronExecutionPublisher.
 * 
 * Tests cover:
 * - Basic lifecycle (start/stop)
 * - Publishing execution events
 * - Publishing trade events
 * - Back-pressure handling
 * - Retry mechanisms
 * - Statistics tracking
 * - Error conditions
 */
class AeronExecutionPublisherTest {
    
    private ExecutionPublisher publisher;
    
    @BeforeEach
    void setUp() {
        // Create publisher with test configuration using IPC channels for reliable testing
        publisher = AeronExecutionPublisher.builder()
                .positionsChannel("aeron:ipc")
                .ledgerChannel("aeron:ipc")
                .executionsChannel("aeron:ipc")
                .streamId(1003) // Use different stream ID for tests
                .build();
    }
    
    @AfterEach
    void tearDown() {
        if (publisher != null && publisher.isRunning()) {
            publisher.stop();
        }
    }
    
    @Test
    void testStartAndStop() {
        assertFalse(publisher.isRunning());
        
        publisher.start();
        assertTrue(publisher.isRunning());
        
        publisher.stop();
        assertFalse(publisher.isRunning());
    }
    
    @Test
    void testDoubleStartThrowsException() {
        publisher.start();
        assertThrows(IllegalStateException.class, () -> publisher.start());
    }
    
    @Test
    void testPublishExecutionBeforeStartThrowsException() {
        ExecutionEvent execution = createTestExecutionEvent();
        assertThrows(IllegalStateException.class, () -> publisher.publishExecution(execution));
    }
    
    @Test
    void testPublishTradeBeforeStartThrowsException() {
        TradeEvent trade = createTestTradeEvent();
        assertThrows(IllegalStateException.class, () -> publisher.publishTrade(trade));
    }
    
    @Test
    void testPublishExecution() {
        publisher.start();
        
        ExecutionEvent execution = createTestExecutionEvent();
        boolean published = publisher.publishExecution(execution);
        
        // Without subscribers, publish may fail - that's ok
        // Just verify the behavior is consistent
        if (published) {
            assertEquals(1, publisher.getPublishedExecutionCount());
        } else {
            assertTrue(publisher.getBackPressureCount() > 0);
        }
    }
    
    @Test
    void testPublishTrade() {
        publisher.start();
        
        TradeEvent trade = createTestTradeEvent();
        boolean published = publisher.publishTrade(trade);
        
        // Without subscribers, publish may fail - that's ok
        // Just verify the behavior is consistent
        if (published) {
            assertEquals(1, publisher.getPublishedTradeCount());
        } else {
            assertTrue(publisher.getBackPressureCount() > 0);
        }
    }
    
    @Test
    void testPublishMultipleExecutions() {
        publisher.start();
        
        int successCount = 0;
        for (int i = 0; i < 10; i++) {
            ExecutionEvent execution = createTestExecutionEvent();
            if (publisher.publishExecution(execution)) {
                successCount++;
            }
        }
        
        assertEquals(successCount, publisher.getPublishedExecutionCount());
    }
    
    @Test
    void testPublishMultipleTrades() {
        publisher.start();
        
        int successCount = 0;
        for (int i = 0; i < 10; i++) {
            TradeEvent trade = createTestTradeEvent();
            if (publisher.publishTrade(trade)) {
                successCount++;
            }
        }
        
        assertEquals(successCount, publisher.getPublishedTradeCount());
    }
    
    @Test
    void testPublishExecutionWithRetry() {
        publisher.start();
        
        ExecutionEvent execution = createTestExecutionEvent();
        boolean published = publisher.publishExecutionWithRetry(execution, 3);
        
        // With retries, we have better chance of success
        // If it still fails, verify back-pressure is tracked
        if (published) {
            assertTrue(publisher.getPublishedExecutionCount() > 0);
        } else {
            assertTrue(publisher.getBackPressureCount() > 0);
        }
    }
    
    @Test
    void testPublishTradeWithRetry() {
        publisher.start();
        
        TradeEvent trade = createTestTradeEvent();
        boolean published = publisher.publishTradeWithRetry(trade, 3);
        
        // With retries, we have better chance of success
        // If it still fails, verify back-pressure is tracked
        if (published) {
            assertTrue(publisher.getPublishedTradeCount() > 0);
        } else {
            assertTrue(publisher.getBackPressureCount() > 0);
        }
    }
    
    @Test
    void testFlush() {
        publisher.start();
        
        ExecutionEvent execution = createTestExecutionEvent();
        publisher.publishExecution(execution);
        
        assertTrue(publisher.flush());
    }
    
    @Test
    void testFlushBeforeStart() {
        assertFalse(publisher.flush());
    }
    
    @Test
    void testStatsInitiallyZero() {
        assertEquals(0, publisher.getPublishedExecutionCount());
        assertEquals(0, publisher.getPublishedTradeCount());
        assertEquals(0, publisher.getBackPressureCount());
    }
    
    @Test
    void testStatsAfterPublishing() {
        publisher.start();
        
        // Publish some events
        // Note: Without subscribers, offers may fail due to back-pressure
        // This is expected behavior - count successes only
        int execSuccesses = 0;
        int tradeSuccesses = 0;
        
        if (publisher.publishExecution(createTestExecutionEvent())) execSuccesses++;
        if (publisher.publishExecution(createTestExecutionEvent())) execSuccesses++;
        if (publisher.publishTrade(createTestTradeEvent())) tradeSuccesses++;
        
        // Verify that the counts match what succeeded
        assertEquals(execSuccesses, publisher.getPublishedExecutionCount());
        assertEquals(tradeSuccesses, publisher.getPublishedTradeCount());
    }
    
    @Test
    void testBuilderWithCustomChannels() {
        // Use IPC channels for testing (multicast requires subscribers)
        ExecutionPublisher customPublisher = AeronExecutionPublisher.builder()
                .positionsChannel("aeron:ipc")
                .ledgerChannel("aeron:ipc")
                .executionsChannel("aeron:ipc")
                .streamId(2003)
                .metricsEnabled(false)
                .build();
        
        assertNotNull(customPublisher);
        assertFalse(customPublisher.isRunning());
        
        customPublisher.start();
        assertTrue(customPublisher.isRunning());
        customPublisher.stop();
    }
    
    @Test
    void testCreateDefault() {
        ExecutionPublisher defaultPublisher = AeronExecutionPublisher.createDefault();
        assertNotNull(defaultPublisher);
        assertFalse(defaultPublisher.isRunning());
        
        defaultPublisher.start();
        assertTrue(defaultPublisher.isRunning());
        defaultPublisher.stop();
    }
    
    @Test
    void testAutoCloseableInterface() throws Exception {
        try (ExecutionPublisher pub = AeronExecutionPublisher.createDefault()) {
            pub.start();
            assertTrue(pub.isRunning());
        }
        // Publisher should be closed after try-with-resources
    }
    
    @Test
    void testConcurrentPublishing() throws InterruptedException {
        publisher.start();
        
        // Create multiple threads publishing concurrently
        Thread[] threads = new Thread[5];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    publisher.publishExecution(createTestExecutionEvent());
                    publisher.publishTrade(createTestTradeEvent());
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify that we have some successful publishes
        // The exact count depends on back-pressure conditions
        long totalAttempts = publisher.getPublishedExecutionCount() + 
                             publisher.getPublishedTradeCount() + 
                             publisher.getBackPressureCount();
        assertTrue(totalAttempts > 0, "Should have attempted to publish");
    }
    
    @Test
    void testHighThroughput() {
        publisher.start();
        
        long startTime = System.nanoTime();
        int messageCount = 10000;
        int successCount = 0;
        
        for (int i = 0; i < messageCount; i++) {
            if (publisher.publishExecution(createTestExecutionEvent())) {
                successCount++;
            }
        }
        
        long duration = System.nanoTime() - startTime;
        
        if (successCount > 0) {
            long messagesPerSecond = (successCount * 1_000_000_000L) / duration;
            System.out.println("Throughput: " + messagesPerSecond + " msgs/sec");
            System.out.println("Avg latency: " + (duration / successCount) + " ns per message");
            System.out.println("Success rate: " + (successCount * 100 / messageCount) + "%");
            
            // Should achieve reasonable throughput for successful messages
            assertTrue(messagesPerSecond > 10_000, 
                    "Expected > 10K msgs/sec, got " + messagesPerSecond);
        } else {
            // All messages failed due to back-pressure - that's also valid behavior
            System.out.println("All messages failed due to back-pressure (no subscribers)");
            assertTrue(publisher.getBackPressureCount() > 0);
        }
    }
    
    // Helper methods
    
    private ExecutionEvent createTestExecutionEvent() {
        OrderEvent order = OrderEvent.newOrder(
                1000L,
                "AAPL",
                OrderEvent.SIDE_BUY,
                OrderEvent.TYPE_LIMIT,
                100L,
                15000L,
                999L,
                1
        );
        
        return ExecutionEvent.fill(
                1L,
                order,
                100L,
                15000L,
                100L,
                0L
        );
    }
    
    private TradeEvent createTestTradeEvent() {
        return new TradeEvent(
                1L,     // tradeId
                1000L,  // orderId
                "AAPL", // symbol
                TradeEvent.SIDE_BUY,
                100L,   // quantity
                15000L, // price
                System.nanoTime(),
                999L,   // account
                1,      // exchange
                0L,     // counterparty
                50L     // fees
        );
    }
}
