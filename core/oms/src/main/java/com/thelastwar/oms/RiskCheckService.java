package com.thelastwar.oms;

import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.risk.RiskDecision;
import com.thelastwar.risk.RiskValidator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Synchronous pre-trade risk check service integrated into OMS request path.
 * 
 * Features:
 * - Synchronous inline risk validation
 * - Configurable fail-open/fail-closed behavior
 * - Retry with exponential backoff for transient failures
 * - Metrics tracking for latency and decision distribution
 * - Audit logging for risk decisions
 * 
 * Performance targets:
 * - p99 latency: < 10ms (combined OMS + risk check)
 * - Individual risk check: < 5µs
 */
public class RiskCheckService {
    
    private final RiskValidator riskValidator;
    private final RiskCheckConfig config;
    private final RiskMetrics metrics;
    
    public RiskCheckService(RiskValidator riskValidator, RiskCheckConfig config) {
        this.riskValidator = riskValidator;
        this.config = config;
        this.metrics = new RiskMetrics();
    }
    
    /**
     * Performs synchronous pre-trade risk check with retry logic.
     * 
     * @param orderEvent Order to validate
     * @return RiskCheckResult containing decision and metrics
     */
    public RiskCheckResult check(OrderEvent orderEvent) {
        long startNanos = System.nanoTime();
        int attempt = 0;
        Exception lastException = null;
        
        // Retry loop with exponential backoff
        while (attempt < config.maxRetries()) {
            try {
                RiskDecision decision = riskValidator.validate(orderEvent);
                long latencyNanos = System.nanoTime() - startNanos;
                
                // Record metrics
                metrics.recordCheck(latencyNanos, decision.approved());
                
                return RiskCheckResult.success(decision, latencyNanos, attempt);
                
            } catch (Exception e) {
                lastException = e;
                attempt++;
                
                // If we haven't exhausted retries, wait before next attempt
                if (attempt < config.maxRetries()) {
                    long backoffMillis = calculateBackoff(attempt);
                    try {
                        Thread.sleep(backoffMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        // All retries exhausted - apply fail-open/fail-closed policy
        long latencyNanos = System.nanoTime() - startNanos;
        metrics.recordFailure(latencyNanos);
        
        if (config.isFailOpen()) {
            // Fail-open: allow order when risk service is unavailable
            return RiskCheckResult.failedOpen(latencyNanos, attempt, lastException);
        } else {
            // Fail-closed: reject order when risk service is unavailable
            return RiskCheckResult.failedClosed(latencyNanos, attempt, lastException);
        }
    }
    
    /**
     * Calculates exponential backoff delay.
     * 
     * @param attempt Retry attempt number (1-based)
     * @return Backoff delay in milliseconds
     */
    private long calculateBackoff(int attempt) {
        // Exponential backoff: baseDelay * 2^(attempt-1)
        // Capped at maxBackoffMillis
        long delay = config.baseBackoffMillis() * (1L << (attempt - 1));
        return Math.min(delay, config.maxBackoffMillis());
    }
    
    /**
     * Gets current risk check metrics.
     */
    public RiskMetrics getMetrics() {
        return metrics;
    }
    
    /**
     * Configuration for risk check service.
     */
    public record RiskCheckConfig(
        boolean isFailOpen,
        int maxRetries,
        long baseBackoffMillis,
        long maxBackoffMillis
    ) {
        /**
         * Default configuration with fail-closed and minimal retries.
         */
        public static RiskCheckConfig defaultConfig() {
            return new RiskCheckConfig(false, 3, 10, 100);
        }
        
        /**
         * Fail-open configuration (allows orders when risk service unavailable).
         */
        public static RiskCheckConfig failOpen() {
            return new RiskCheckConfig(true, 3, 10, 100);
        }
        
        /**
         * Fail-closed configuration (rejects orders when risk service unavailable).
         */
        public static RiskCheckConfig failClosed() {
            return new RiskCheckConfig(false, 3, 10, 100);
        }
        
        /**
         * No retry configuration (single attempt only).
         */
        public static RiskCheckConfig noRetry(boolean isFailOpen) {
            return new RiskCheckConfig(isFailOpen, 1, 0, 0);
        }
    }
    
    /**
     * Result of a risk check operation.
     */
    public record RiskCheckResult(
        RiskDecision decision,
        long latencyNanos,
        int attempts,
        boolean serviceAvailable,
        Exception exception
    ) {
        public static RiskCheckResult success(RiskDecision decision, long latencyNanos, int attempts) {
            return new RiskCheckResult(decision, latencyNanos, attempts, true, null);
        }
        
        public static RiskCheckResult failedOpen(long latencyNanos, int attempts, Exception exception) {
            // When failing open, we approve the order despite service unavailability
            return new RiskCheckResult(
                RiskDecision.APPROVED,
                latencyNanos,
                attempts,
                false,
                exception
            );
        }
        
        public static RiskCheckResult failedClosed(long latencyNanos, int attempts, Exception exception) {
            // When failing closed, we reject the order due to service unavailability
            return new RiskCheckResult(
                RiskDecision.reject(999, "Risk service unavailable"),
                latencyNanos,
                attempts,
                false,
                exception
            );
        }
        
        public boolean approved() {
            return decision.approved();
        }
    }
    
    /**
     * Metrics for risk check operations.
     */
    public static class RiskMetrics {
        private final AtomicLong totalChecks = new AtomicLong(0);
        private final AtomicLong approvedCount = new AtomicLong(0);
        private final AtomicLong rejectedCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);
        private final AtomicLong totalLatencyNanos = new AtomicLong(0);
        private final AtomicLong maxLatencyNanos = new AtomicLong(0);
        
        void recordCheck(long latencyNanos, boolean approved) {
            totalChecks.incrementAndGet();
            if (approved) {
                approvedCount.incrementAndGet();
            } else {
                rejectedCount.incrementAndGet();
            }
            totalLatencyNanos.addAndGet(latencyNanos);
            updateMaxLatency(latencyNanos);
        }
        
        void recordFailure(long latencyNanos) {
            totalChecks.incrementAndGet();
            failureCount.incrementAndGet();
            totalLatencyNanos.addAndGet(latencyNanos);
            updateMaxLatency(latencyNanos);
        }
        
        private void updateMaxLatency(long latencyNanos) {
            long current;
            do {
                current = maxLatencyNanos.get();
                if (latencyNanos <= current) {
                    break;
                }
            } while (!maxLatencyNanos.compareAndSet(current, latencyNanos));
        }
        
        public long getTotalChecks() {
            return totalChecks.get();
        }
        
        public long getApprovedCount() {
            return approvedCount.get();
        }
        
        public long getRejectedCount() {
            return rejectedCount.get();
        }
        
        public long getFailureCount() {
            return failureCount.get();
        }
        
        public long getAverageLatencyNanos() {
            long total = totalChecks.get();
            return total == 0 ? 0 : totalLatencyNanos.get() / total;
        }
        
        public long getMaxLatencyNanos() {
            return maxLatencyNanos.get();
        }
        
        public double getApprovalRate() {
            long total = totalChecks.get();
            return total == 0 ? 0.0 : (double) approvedCount.get() / total;
        }
        
        public double getRejectionRate() {
            long total = totalChecks.get();
            return total == 0 ? 0.0 : (double) rejectedCount.get() / total;
        }
        
        public double getFailureRate() {
            long total = totalChecks.get();
            return total == 0 ? 0.0 : (double) failureCount.get() / total;
        }
    }
}
