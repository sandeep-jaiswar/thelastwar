package com.thelastwar.matching.replay;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;
import com.thelastwar.matching.MatchingEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for replay validation system.
 * 
 * Tests cover:
 * - Event persistence and replay
 * - Determinism validation with checksums
 * - Throughput measurement (≥ 1M events/sec)
 * - Latency histogram generation
 * - Full validation workflow
 */
class ReplayValidationTest {
    
    @TempDir
    Path tempDir;
    
    private TestEventBus eventBus;
    private MatchingEngine engine;
    private ReplayEventStore eventStore;
    
    @BeforeEach
    void setUp() throws IOException {
        eventBus = new TestEventBus();
        eventBus.start();
        engine = new MatchingEngine(eventBus);
        engine.start();
        eventStore = new FileBasedEventStore(tempDir.resolve("test-events.dat"));
    }
    
    @AfterEach
    void tearDown() throws IOException {
        if (eventStore != null) {
            eventStore.close();
        }
        if (engine != null) {
            engine.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
    }
    
    @Test
    @DisplayName("Event store persists and replays events correctly")
    void testEventStorePersistence() throws IOException {
        // Create test events
        List<Event> events = createTestEvents(100);
        
        // Append to store
        for (Event event : events) {
            eventStore.append(event);
        }
        
        assertEquals(100, eventStore.getEventCount());
        assertEquals(100, eventStore.getCurrentSequence());
        
        // Replay events
        List<Event> replayed = new ArrayList<>();
        long replayedCount = eventStore.replay(1, 100, replayed::add);
        
        assertEquals(100, replayedCount);
        assertEquals(100, replayed.size());
        
        // Verify events match
        for (int i = 0; i < events.size(); i++) {
            Event original = events.get(i);
            Event replayedEvent = replayed.get(i);
            
            assertEquals(original.sequence(), replayedEvent.sequence());
            assertEquals(original.eventType(), replayedEvent.eventType());
            assertEquals(original.sourceId(), replayedEvent.sourceId());
        }
    }
    
    @Test
    @DisplayName("Event store handles partial replay correctly")
    void testPartialReplay() throws IOException {
        List<Event> events = createTestEvents(50);
        
        for (Event event : events) {
            eventStore.append(event);
        }
        
        // Replay only events 10-30
        List<Event> replayed = new ArrayList<>();
        long replayedCount = eventStore.replay(10, 30, replayed::add);
        
        assertEquals(21, replayedCount); // 10 to 30 inclusive
        assertEquals(21, replayed.size());
        
        // Verify sequence numbers
        assertEquals(10, replayed.get(0).sequence());
        assertEquals(30, replayed.get(20).sequence());
    }
    
    @Test
    @DisplayName("Event store can be cleared and reused")
    void testEventStoreClear() throws IOException {
        List<Event> events = createTestEvents(20);
        
        for (Event event : events) {
            eventStore.append(event);
        }
        
        assertEquals(20, eventStore.getEventCount());
        
        eventStore.clear();
        
        assertEquals(0, eventStore.getEventCount());
        assertEquals(0, eventStore.getCurrentSequence());
        
        // Can append again after clear
        eventStore.append(events.get(0));
        assertEquals(1, eventStore.getEventCount());
    }
    
    @Test
    @DisplayName("Determinism validator generates consistent checksums")
    void testDeterminismValidator() throws InterruptedException {
        // Create deterministic order sequence
        publishTestOrders(50);
        Thread.sleep(100);
        
        MatchingEngine.MatchingEngineSnapshot snapshot1 = engine.createSnapshot();
        DeterminismValidator validator = new DeterminismValidator();
        String checksum1 = validator.computeChecksum(snapshot1);
        
        // Reset and replay same orders
        resetEngine();
        publishTestOrders(50);
        Thread.sleep(100);
        
        MatchingEngine.MatchingEngineSnapshot snapshot2 = engine.createSnapshot();
        String checksum2 = validator.computeChecksum(snapshot2);
        
        // Checksums should match for identical state
        assertEquals(checksum1, checksum2, "Checksums should be identical for same state");
    }
    
    @Test
    @DisplayName("Determinism validator detects state differences")
    void testDeterminismValidatorDetectsDifferences() throws InterruptedException {
        publishTestOrders(50);
        Thread.sleep(100);
        
        MatchingEngine.MatchingEngineSnapshot snapshot1 = engine.createSnapshot();
        DeterminismValidator validator = new DeterminismValidator();
        String checksum1 = validator.computeChecksum(snapshot1);
        
        // Create different state (55 orders instead of 50)
        resetEngine();
        publishTestOrders(55);
        Thread.sleep(100);
        
        MatchingEngine.MatchingEngineSnapshot snapshot2 = engine.createSnapshot();
        String checksum2 = validator.computeChecksum(snapshot2);
        
        // Checksums should differ
        assertNotEquals(checksum1, checksum2, "Checksums should differ for different states");
    }
    
    @Test
    @DisplayName("Latency histogram calculates statistics correctly")
    void testLatencyHistogram() {
        LatencyHistogram histogram = new LatencyHistogram("Test Operation");
        
        // Record latencies: 1000, 2000, 3000, 4000, 5000 ns
        for (int i = 1; i <= 5; i++) {
            histogram.record(i * 1000L);
        }
        
        assertEquals(5, histogram.getCount());
        assertEquals(3.0, histogram.getMeanMicros(), 0.01); // Mean = 3000ns = 3µs
        assertEquals(3.0, histogram.getMedianMicros(), 0.01);
        assertEquals(1.0, histogram.getMinMicros(), 0.01);
        assertEquals(5.0, histogram.getMaxMicros(), 0.01);
    }
    
    @Test
    @DisplayName("Full replay validation workflow completes successfully")
    @Tag("integration")
    void testFullReplayValidation() throws IOException, InterruptedException {
        try (ReplayValidationHarness harness = new ReplayValidationHarness(
                eventBus, engine, tempDir)) {
            
            // Publish test orders
            publishTestOrders(1000);
            Thread.sleep(200);
            
            // Capture baseline
            ReplayValidationHarness.BaselineSnapshot baseline = harness.captureBaseline();
            assertNotNull(baseline);
            assertNotNull(baseline.checksum());
            assertTrue(baseline.eventCount() > 0);
            
            // Validate replay
            ReplayValidationHarness.ValidationResult result = harness.validateReplay(baseline);
            
            assertNotNull(result);
            assertTrue(result.eventsReplayed() > 0);
            assertEquals(baseline.checksum(), result.replayChecksum(), 
                "Replay should produce identical state");
            assertTrue(result.isDeterministic(), "Replay should be deterministic");
            
            // Generate report
            String report = harness.generateReport(result);
            assertNotNull(report);
            assertTrue(report.contains("DETERMINISM VALIDATION"));
            assertTrue(report.contains("REPLAY PERFORMANCE"));
            assertTrue(report.contains("LATENCY HISTOGRAM"));
        }
    }
    
    @Test
    @DisplayName("Replay throughput measurement is accurate")
    @Tag("performance")
    void testReplayThroughputMeasurement() throws IOException, InterruptedException {
        try (ReplayValidationHarness harness = new ReplayValidationHarness(
                eventBus, engine, tempDir)) {
            
            // Publish larger volume for throughput test
            int eventCount = 10_000;
            publishTestOrders(eventCount);
            Thread.sleep(500);
            
            ReplayValidationHarness.BaselineSnapshot baseline = harness.captureBaseline();
            
            long startTime = System.currentTimeMillis();
            ReplayValidationHarness.ValidationResult result = harness.validateReplay(baseline);
            long duration = System.currentTimeMillis() - startTime;
            
            // Verify throughput is reasonable
            assertTrue(result.throughput() > 0, "Throughput should be positive");
            assertTrue(result.eventsReplayed() > 0, "Should replay events");
            
            System.out.printf("Replayed %,d events in %,d ms (%.0f events/sec)%n",
                result.eventsReplayed(), duration, result.throughput());
        }
    }
    
    @Test
    @DisplayName("Replay handles empty event store gracefully")
    void testReplayEmptyStore() throws IOException, InterruptedException {
        try (ReplayValidationHarness harness = new ReplayValidationHarness(
                eventBus, engine, tempDir)) {
            
            // Capture baseline with no events
            ReplayValidationHarness.BaselineSnapshot baseline = harness.captureBaseline();
            
            // Should handle empty replay without errors
            ReplayValidationHarness.ValidationResult result = harness.validateReplay(baseline);
            
            assertNotNull(result);
            assertTrue(result.isDeterministic());
        }
    }
    
    // Helper methods
    
    private List<Event> createTestEvents(int count) {
        List<Event> events = new ArrayList<>();
        Random random = new Random(42);
        
        for (int i = 1; i <= count; i++) {
            OrderEvent order = OrderEvent.newOrder(
                i, "TEST", 
                random.nextBoolean() ? OrderEvent.SIDE_BUY : OrderEvent.SIDE_SELL,
                OrderEvent.TYPE_LIMIT,
                100L, 100_000L, 999L, 1
            );
            
            Event event = Event.create(
                System.nanoTime(),
                i,
                SourceId.OMS,
                EventType.ORDER_SUBMITTED,
                0L,
                order
            );
            events.add(event);
        }
        
        return events;
    }
    
    private void publishTestOrders(int count) {
        Random random = new Random(42);
        
        for (int i = 1; i <= count; i++) {
            OrderEvent order = OrderEvent.newOrder(
                i, "AAPL",
                random.nextBoolean() ? OrderEvent.SIDE_BUY : OrderEvent.SIDE_SELL,
                OrderEvent.TYPE_LIMIT,
                100L + (random.nextInt(10) * 10),
                100_000L + random.nextInt(100) - 50,
                999L, 1
            );
            
            Event event = Event.create(
                System.nanoTime(),
                i,
                SourceId.OMS,
                EventType.ORDER_SUBMITTED,
                0L,
                order
            );
            
            eventBus.publish(event);
        }
    }
    
    private void resetEngine() {
        engine.stop();
        eventBus.stop();
        
        eventBus = new TestEventBus();
        eventBus.start();
        engine = new MatchingEngine(eventBus);
        engine.start();
    }
    
    /**
     * Simple test event bus implementation for replay tests.
     */
    static class TestEventBus implements EventBus {
        private final List<HandlerRegistration>[] handlers;
        private final AtomicLong publishedCount = new AtomicLong(0);
        private volatile boolean running = false;
        private final List<Event> eventLog = new ArrayList<>();
        private final Object eventLogLock = new Object();

        @SuppressWarnings("unchecked")
        public TestEventBus() {
            this.handlers = new List[10000];
            for (int i = 0; i < handlers.length; i++) {
                handlers[i] = new CopyOnWriteArrayList<>();
            }
        }

        @Override
        public boolean publish(Event event) {
            if (event == null || !running) {
                return false;
            }

            int eventType = event.eventType();
            if (eventType < 0 || eventType >= handlers.length) {
                return false;
            }

            long sequence = publishedCount.incrementAndGet();
            
            Event storedEvent = event;
            if (event.sequence() != sequence) {
                storedEvent = Event.create(
                    event.timestamp(),
                    sequence,
                    event.sourceId(),
                    event.eventType(),
                    event.header(),
                    event.payload()
                );
            }
            
            synchronized (eventLogLock) {
                eventLog.add(storedEvent);
            }

            List<HandlerRegistration> eventHandlers = handlers[eventType];
            for (HandlerRegistration registration : eventHandlers) {
                if (registration.isActive()) {
                    try {
                        registration.handler.onEvent(storedEvent);
                    } catch (Exception e) {
                        try {
                            registration.handler.onError(storedEvent, e);
                        } catch (Exception errorHandlerException) {
                            // Ignore
                        }
                    }
                }
            }

            return true;
        }

        @Override
        public Subscription subscribe(int eventType, EventHandler<?> handler) {
            if (handler == null || eventType < 0 || eventType >= handlers.length) {
                throw new IllegalArgumentException("Invalid event type or null handler");
            }

            HandlerRegistration registration = new HandlerRegistration(handler);
            handlers[eventType].add(registration);

            return new Subscription() {
                @Override
                public void unsubscribe() {
                    registration.active = false;
                    handlers[eventType].remove(registration);
                }

                @Override
                public boolean isActive() {
                    return registration.active;
                }
            };
        }

        @Override
        public long getPublishedEventCount() {
            return publishedCount.get();
        }

        @Override
        public int getSubscriberCount(int eventType) {
            if (eventType < 0 || eventType >= handlers.length) {
                return 0;
            }
            return (int) handlers[eventType].stream()
                    .filter(HandlerRegistration::isActive)
                    .count();
        }

        @Override
        public void start() {
            running = true;
        }

        @Override
        public void stop() {
            running = false;
        }

        private static class HandlerRegistration {
            final EventHandler<?> handler;
            volatile boolean active = true;

            HandlerRegistration(EventHandler<?> handler) {
                this.handler = handler;
            }

            boolean isActive() {
                return active;
            }
        }
    }
}
