package com.thelastwar.gateway.integration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for integration tests.
 */
final class IntegrationTestUtils {
    
    private IntegrationTestUtils() {}
    
    static void waitFor(long timeoutMs) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(timeoutMs);
    }
    
    static LatencyStats calculateStats(List<Long> latencies) {
        if (latencies.isEmpty()) {
            return new LatencyStats(0, 0, 0, 0, 0, 0);
        }
        
        latencies.sort(Long::compareTo);
        
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        long p50 = latencies.get(latencies.size() / 2);
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        
        return new LatencyStats(min, max, p50, p95, p99, avg);
    }
    
    static void generateReport(String testName, ReportData data) throws IOException {
        File reportDir = new File("build/reports/load-tests");
        if (!reportDir.exists() && !reportDir.mkdirs()) {
            throw new IOException("Failed to create report directory");
        }
        
        File reportFile = new File(reportDir, testName + "_report.txt");
        
        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write("=== Integration Test Report ===\n");
            writer.write("Test: " + testName + "\n");
            writer.write("Timestamp: " + System.currentTimeMillis() + "\n\n");
            
            writer.write("Configuration:\n");
            writer.write("  Total items: " + data.totalItems + "\n");
            writer.write("  Duration: " + data.durationMs + " ms\n\n");
            
            double throughput = (data.totalItems * 1000.0) / data.durationMs;
            writer.write("Results:\n");
            writer.write("  Throughput: " + String.format("%.0f", throughput) + " items/s\n");
            writer.write("  Processed: " + data.processedItems + "\n\n");
            
            if (data.stats != null) {
                writer.write("Latency Distribution:\n");
                writer.write("  Average: " + String.format("%.3f", data.stats.avg / 1_000_000.0) + " ms\n");
                writer.write("  P50: " + String.format("%.3f", data.stats.p50 / 1_000_000.0) + " ms\n");
                writer.write("  P95: " + String.format("%.3f", data.stats.p95 / 1_000_000.0) + " ms\n");
                writer.write("  P99: " + String.format("%.3f", data.stats.p99 / 1_000_000.0) + " ms\n\n");
            }
        }
    }
    
    static void cleanupDirectory(File dir) {
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        cleanupDirectory(file);
                    } else if (!file.delete()) {
                        // Ignore deletion failures in tests
                    }
                }
            }
            dir.delete(); // Ignore result
        }
    }
    
    static void closeAll(AutoCloseable... closeables) throws Exception {
        for (AutoCloseable closeable : closeables) {
            if (closeable != null) {
                try {
                    if (closeable instanceof com.thelastwar.gateway.FixGateway) {
                        com.thelastwar.gateway.FixGateway gw = (com.thelastwar.gateway.FixGateway) closeable;
                        if (gw.isRunning()) {
                            gw.stop();
                        }
                    } else if (closeable instanceof com.thelastwar.eventbus.EventBus) {
                        ((com.thelastwar.eventbus.EventBus) closeable).stop();
                    } else {
                        closeable.close();
                    }
                } catch (Exception e) {
                    // Log but don't fail test cleanup
                }
            }
        }
    }
    
    static void cleanupTestFiles(String... suffixes) {
        for (String suffix : suffixes) {
            cleanupDirectory(new File("build/tmp/fix-" + suffix + "-store"));
            cleanupDirectory(new File("build/tmp/fix-" + suffix + "-log"));
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
    
    static class ReportData {
        final int totalItems;
        final long durationMs;
        final int processedItems;
        final LatencyStats stats;
        
        ReportData(int totalItems, long durationMs, int processedItems, LatencyStats stats) {
            this.totalItems = totalItems;
            this.durationMs = durationMs;
            this.processedItems = processedItems;
            this.stats = stats;
        }
    }
}
