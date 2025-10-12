package com.thelastwar.matching.replay;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Collects latency measurements and generates histogram statistics.
 * 
 * Used for replay performance analysis and reporting.
 */
public class LatencyHistogram {
    
    private final List<Long> latencies = new ArrayList<>();
    private final String operationName;
    private long totalLatencyNs = 0;
    
    public LatencyHistogram(String operationName) {
        this.operationName = operationName;
    }
    
    /**
     * Records a latency measurement in nanoseconds.
     * 
     * @param latencyNs Latency in nanoseconds
     */
    public void record(long latencyNs) {
        latencies.add(latencyNs);
        totalLatencyNs += latencyNs;
    }
    
    /**
     * Gets the mean latency in microseconds.
     * 
     * @return Mean latency
     */
    public double getMeanMicros() {
        if (latencies.isEmpty()) return 0.0;
        return (totalLatencyNs / (double) latencies.size()) / 1000.0;
    }
    
    /**
     * Gets the median latency in microseconds.
     * 
     * @return Median latency
     */
    public double getMedianMicros() {
        if (latencies.isEmpty()) return 0.0;
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2000.0;
        } else {
            return sorted.get(mid) / 1000.0;
        }
    }
    
    /**
     * Gets a percentile latency in microseconds.
     * 
     * @param percentile Percentile (0.0 to 100.0)
     * @return Percentile latency
     */
    public double getPercentileMicros(double percentile) {
        if (latencies.isEmpty()) return 0.0;
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index) / 1000.0;
    }
    
    /**
     * Gets the minimum latency in microseconds.
     * 
     * @return Minimum latency
     */
    public double getMinMicros() {
        if (latencies.isEmpty()) return 0.0;
        return latencies.stream().mapToLong(l -> l).min().orElse(0) / 1000.0;
    }
    
    /**
     * Gets the maximum latency in microseconds.
     * 
     * @return Maximum latency
     */
    public double getMaxMicros() {
        if (latencies.isEmpty()) return 0.0;
        return latencies.stream().mapToLong(l -> l).max().orElse(0) / 1000.0;
    }
    
    /**
     * Gets the number of samples recorded.
     * 
     * @return Sample count
     */
    public int getCount() {
        return latencies.size();
    }
    
    /**
     * Generates a formatted histogram report.
     * 
     * @return Formatted report string
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(operationName).append(" Latency Histogram ===\n");
        sb.append(String.format("Samples:    %,d\n", getCount()));
        sb.append(String.format("Mean:       %.2f µs\n", getMeanMicros()));
        sb.append(String.format("Median:     %.2f µs\n", getMedianMicros()));
        sb.append(String.format("Min:        %.2f µs\n", getMinMicros()));
        sb.append(String.format("Max:        %.2f µs\n", getMaxMicros()));
        sb.append(String.format("P50:        %.2f µs\n", getPercentileMicros(50)));
        sb.append(String.format("P95:        %.2f µs\n", getPercentileMicros(95)));
        sb.append(String.format("P99:        %.2f µs\n", getPercentileMicros(99)));
        sb.append(String.format("P99.9:      %.2f µs\n", getPercentileMicros(99.9)));
        return sb.toString();
    }
    
    /**
     * Clears all recorded latencies.
     */
    public void reset() {
        latencies.clear();
        totalLatencyNs = 0;
    }
}
