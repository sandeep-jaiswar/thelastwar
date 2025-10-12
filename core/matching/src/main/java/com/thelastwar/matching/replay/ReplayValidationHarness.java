package com.thelastwar.matching.replay;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.matching.MatchingEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Harness for validating deterministic replay of matching engine operations.
 * 
 * Features:
 * - Records events to persistent store during baseline run
 * - Replays events and compares state checksums
 * - Measures replay throughput and latency
 * - Generates comprehensive validation reports
 */
public class ReplayValidationHarness implements AutoCloseable {
    
    private final EventBus eventBus;
    private final MatchingEngine engine;
    private final ReplayEventStore eventStore;
    private final DeterminismValidator validator;
    private final LatencyHistogram replayLatency;
    private final LatencyHistogram eventProcessingLatency;
    
    public ReplayValidationHarness(EventBus eventBus, MatchingEngine engine, Path storeDirectory) throws IOException {
        this.eventBus = eventBus;
        this.engine = engine;
        this.eventStore = new FileBasedEventStore(storeDirectory.resolve("replay-events.dat"));
        this.validator = new DeterminismValidator();
        this.replayLatency = new LatencyHistogram("Replay Operation");
        this.eventProcessingLatency = new LatencyHistogram("Event Processing");
    }
    
    /**
     * Captures a baseline run by recording all events to persistent store.
     * 
     * @return Baseline snapshot and checksum
     */
    public BaselineSnapshot captureBaseline() throws InterruptedException {
        long startSequence = eventBus.getCurrentSequence();
        
        // Subscribe to all events and record them
        AtomicLong recordedEvents = new AtomicLong(0);
        CountDownLatch recordingLatch = new CountDownLatch(1);
        
        eventBus.subscribe(com.thelastwar.eventbus.EventType.ORDER_SUBMITTED, event -> {
            try {
                eventStore.append(event);
                recordedEvents.incrementAndGet();
            } catch (IOException e) {
                throw new RuntimeException("Failed to record event", e);
            }
        });
        
        // Wait for events to settle
        Thread.sleep(1000);
        
        long endSequence = eventBus.getCurrentSequence();
        MatchingEngine.MatchingEngineSnapshot snapshot = engine.createSnapshot();
        String checksum = validator.computeChecksum(snapshot);
        
        return new BaselineSnapshot(startSequence, endSequence, snapshot, checksum, recordedEvents.get());
    }
    
    /**
     * Replays events and validates against baseline.
     * 
     * @param baseline Baseline to validate against
     * @return Validation result
     */
    public ValidationResult validateReplay(BaselineSnapshot baseline) throws IOException, InterruptedException {
        long startTime = System.nanoTime();
        
        // Reset engine
        engine.stop();
        engine.start();
        
        // Replay events
        AtomicLong replayedCount = new AtomicLong(0);
        AtomicLong eventCount = new AtomicLong(0);
        
        long replayStartTime = System.nanoTime();
        
        long replayed = eventStore.replay(baseline.startSequence(), baseline.endSequence(), event -> {
            long eventStart = System.nanoTime();
            
            // Republish event through event bus
            eventBus.publish(event);
            replayedCount.incrementAndGet();
            
            long eventEnd = System.nanoTime();
            eventProcessingLatency.record(eventEnd - eventStart);
        });
        
        long replayEndTime = System.nanoTime();
        replayLatency.record(replayEndTime - replayStartTime);
        
        // Wait for processing to complete
        Thread.sleep(1000);
        
        // Capture snapshot and validate
        MatchingEngine.MatchingEngineSnapshot replaySnapshot = engine.createSnapshot();
        String replayChecksum = validator.computeChecksum(replaySnapshot);
        
        long endTime = System.nanoTime();
        long totalDurationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        
        boolean deterministic = baseline.checksum().equals(replayChecksum);
        double throughput = replayed > 0 ? (replayed * 1000.0 / totalDurationMs) * 1000.0 : 0.0; // events/sec
        
        return new ValidationResult(
            replayed,
            deterministic,
            baseline.checksum(),
            replayChecksum,
            throughput,
            totalDurationMs,
            replayLatency,
            eventProcessingLatency
        );
    }
    
    /**
     * Generates a comprehensive validation report.
     * 
     * @param result Validation result
     * @return Formatted report
     */
    public String generateReport(ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║           MATCHING ENGINE REPLAY VALIDATION REPORT                    ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════════════╝\n");
        sb.append("\n");
        
        // Determinism Status
        sb.append("┌─ DETERMINISM VALIDATION ─────────────────────────────────────────────┐\n");
        if (result.isDeterministic()) {
            sb.append("│ Status:           ✅ PASSED - Bit-for-bit identical state            │\n");
        } else {
            sb.append("│ Status:           ❌ FAILED - State mismatch detected                │\n");
        }
        sb.append(String.format("│ Events Replayed:  %-54d │\n", result.eventsReplayed()));
        sb.append(String.format("│ Baseline Checksum: %-53s │\n", 
            result.baselineChecksum().substring(0, Math.min(50, result.baselineChecksum().length()))));
        sb.append(String.format("│ Replay Checksum:   %-53s │\n", 
            result.replayChecksum().substring(0, Math.min(50, result.replayChecksum().length()))));
        sb.append("└──────────────────────────────────────────────────────────────────────┘\n");
        sb.append("\n");
        
        // Throughput
        sb.append("┌─ REPLAY PERFORMANCE ─────────────────────────────────────────────────┐\n");
        sb.append(String.format("│ Total Duration:   %,d ms                                          │\n", result.durationMs()));
        sb.append(String.format("│ Throughput:       %,.0f events/sec                                │\n", result.throughput()));
        
        boolean throughputMet = result.throughput() >= 1_000_000;
        if (throughputMet) {
            sb.append("│ Target (≥1M/s):   ✅ MET                                              │\n");
        } else {
            sb.append("│ Target (≥1M/s):   ❌ NOT MET                                          │\n");
        }
        sb.append("└──────────────────────────────────────────────────────────────────────┘\n");
        sb.append("\n");
        
        // Latency Histogram
        sb.append("┌─ LATENCY HISTOGRAM ──────────────────────────────────────────────────┐\n");
        sb.append("│ Replay Operation:                                                    │\n");
        sb.append(String.format("│   Mean:     %10.2f µs                                          │\n", result.replayLatency().getMeanMicros()));
        sb.append(String.format("│   P50:      %10.2f µs                                          │\n", result.replayLatency().getPercentileMicros(50)));
        sb.append(String.format("│   P99:      %10.2f µs                                          │\n", result.replayLatency().getPercentileMicros(99)));
        sb.append("│                                                                      │\n");
        sb.append("│ Event Processing:                                                    │\n");
        sb.append(String.format("│   Mean:     %10.2f µs                                          │\n", result.eventProcessingLatency().getMeanMicros()));
        sb.append(String.format("│   P50:      %10.2f µs                                          │\n", result.eventProcessingLatency().getPercentileMicros(50)));
        sb.append(String.format("│   P99:      %10.2f µs                                          │\n", result.eventProcessingLatency().getPercentileMicros(99)));
        sb.append("└──────────────────────────────────────────────────────────────────────┘\n");
        sb.append("\n");
        
        // Summary
        sb.append("┌─ SUMMARY ────────────────────────────────────────────────────────────┐\n");
        boolean passed = result.isDeterministic() && throughputMet;
        if (passed) {
            sb.append("│ Overall Result:   ✅ PASSED                                          │\n");
            sb.append("│                                                                      │\n");
            sb.append("│ All validation criteria met:                                         │\n");
            sb.append("│  ✅ Bit-for-bit deterministic replay                                 │\n");
            sb.append("│  ✅ Throughput ≥ 1M events/sec                                       │\n");
        } else {
            sb.append("│ Overall Result:   ❌ FAILED                                          │\n");
            sb.append("│                                                                      │\n");
            sb.append("│ Validation criteria not met:                                         │\n");
            if (!result.isDeterministic()) {
                sb.append("│  ❌ Bit-for-bit deterministic replay                                 │\n");
            }
            if (!throughputMet) {
                sb.append("│  ❌ Throughput ≥ 1M events/sec                                       │\n");
            }
        }
        sb.append("└──────────────────────────────────────────────────────────────────────┘\n");
        sb.append("\n");
        
        return sb.toString();
    }
    
    /**
     * Closes the replay harness and releases resources.
     */
    public void close() throws IOException {
        if (eventStore != null) {
            eventStore.close();
        }
    }
    
    /**
     * Baseline snapshot captured during initial run.
     */
    public record BaselineSnapshot(
        long startSequence,
        long endSequence,
        MatchingEngine.MatchingEngineSnapshot snapshot,
        String checksum,
        long eventCount
    ) {}
    
    /**
     * Result of replay validation.
     */
    public record ValidationResult(
        long eventsReplayed,
        boolean isDeterministic,
        String baselineChecksum,
        String replayChecksum,
        double throughput,
        long durationMs,
        LatencyHistogram replayLatency,
        LatencyHistogram eventProcessingLatency
    ) {
        public boolean passed() {
            return isDeterministic && throughput >= 1_000_000;
        }
    }
}
