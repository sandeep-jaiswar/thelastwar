package com.thelastwar.wsgateway.integration;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.SourceId;
import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods for WebSocket integration tests.
 */
final class WebSocketTestUtils {
    
    private WebSocketTestUtils() {}
    
    static void waitFor(long timeoutMs) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(timeoutMs);
    }
    
    static Event createOrderEvent(long id) {
        OrderEvent orderEvent = OrderEvent.newOrder(
            id, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        return Event.create(
            System.nanoTime(), id, SourceId.MATCHING_ENGINE,
            EventType.ORDER_ACCEPTED, 0L, orderEvent
        );
    }
    
    static Event createExecutionEvent(long id) {
        OrderEvent orderEvent = OrderEvent.newOrder(
            id, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        ExecutionEvent execEvent = ExecutionEvent.fill(
            id, orderEvent, 100L, 15000L, 100L, 0L
        );
        
        return Event.create(
            System.nanoTime(), id, SourceId.MATCHING_ENGINE,
            EventType.ORDER_FILLED, 0L, execEvent
        );
    }
    
    static LatencyStats calculateStats(List<Long> latencies) {
        if (latencies.isEmpty()) {
            return new LatencyStats(0, 0, 0, 0, 0, 0);
        }
        
        latencies.sort(Long::compareTo);
        
        return new LatencyStats(
            latencies.get(0),
            latencies.get(latencies.size() - 1),
            latencies.get(latencies.size() / 2),
            latencies.get((int) (latencies.size() * 0.95)),
            latencies.get((int) (latencies.size() * 0.99)),
            latencies.stream().mapToLong(Long::longValue).average().orElse(0)
        );
    }
    
    static void generateReport(String testName, int totalEvents, long durationMs, 
                              int publishedCount, LatencyStats stats) throws IOException {
        File reportDir = new File("build/reports/load-tests");
        if (!reportDir.exists() && !reportDir.mkdirs()) {
            throw new IOException("Failed to create report directory");
        }
        
        File reportFile = new File(reportDir, testName + "_report.txt");
        
        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write("=== WebSocket Gateway Load Test Report ===\n");
            writer.write("Test: " + testName + "\n\n");
            
            double throughput = (totalEvents * 1000.0) / durationMs;
            writer.write("Results:\n");
            writer.write("  Throughput: " + String.format("%.0f", throughput) + " events/s\n");
            writer.write("  Published: " + publishedCount + "\n\n");
            
            writer.write("Latency Distribution:\n");
            writer.write("  Average: " + String.format("%.3f", stats.avg / 1_000_000.0) + " ms\n");
            writer.write("  P50: " + String.format("%.3f", stats.p50 / 1_000_000.0) + " ms\n");
            writer.write("  P95: " + String.format("%.3f", stats.p95 / 1_000_000.0) + " ms\n");
            writer.write("  P99: " + String.format("%.3f", stats.p99 / 1_000_000.0) + " ms\n");
        }
    }
    
    static class LatencyStats {
        final long min;
        final long max;
        final long p50;
        final long p95;
        final long p99;
        final double avg;
        
        LatencyStats(long min, long max, long p50, long p95, long p99, double avg) {
            this.min = min;
            this.max = max;
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
            this.avg = avg;
        }
    }
}
