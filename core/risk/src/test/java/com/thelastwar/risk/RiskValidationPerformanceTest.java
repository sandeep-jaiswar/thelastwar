package com.thelastwar.risk;

import com.thelastwar.eventbus.model.OrderEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test for risk validation.
 * Target: < 5µs per validation (p99)
 */
class RiskValidationPerformanceTest {
    
    private static final int WARMUP_ITERATIONS = 100_000;
    private static final int TEST_ITERATIONS = 1_000_000;
    
    @Test
    void testCreditCheckPerformance() {
        CreditCheckModule module = new CreditCheckModule(10_000_000L, true);
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            module.validate(order);
        }
        
        // Measure
        List<Long> timings = new ArrayList<>();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            RiskDecision decision = module.validate(order);
            long end = System.nanoTime();
            timings.add(end - start);
            
            // Consume result to prevent optimization
            if (!decision.approved()) {
                fail("Unexpected rejection");
            }
        }
        
        printPerformanceStats("CreditCheck", timings);
    }
    
    @Test
    void testMarginCheckPerformance() {
        MarginCheckModule module = new MarginCheckModule(100_000L, true);
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            module.validate(order);
        }
        
        // Measure
        List<Long> timings = new ArrayList<>();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            RiskDecision decision = module.validate(order);
            long end = System.nanoTime();
            timings.add(end - start);
            
            if (!decision.approved()) {
                fail("Unexpected rejection");
            }
        }
        
        printPerformanceStats("MarginCheck", timings);
    }
    
    @Test
    void testFatFingerCheckPerformance() {
        FatFingerCheckModule module = new FatFingerCheckModule();
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            module.validate(order);
        }
        
        // Measure
        List<Long> timings = new ArrayList<>();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            RiskDecision decision = module.validate(order);
            long end = System.nanoTime();
            timings.add(end - start);
            
            if (!decision.approved()) {
                fail("Unexpected rejection");
            }
        }
        
        printPerformanceStats("FatFingerCheck", timings);
    }
    
    @Test
    void testCompositeValidatorPerformance() {
        CompositeRiskValidator validator = new CompositeRiskValidator.Builder()
            .add(new CreditCheckModule(10_000_000L, true))
            .add(new MarginCheckModule(100_000L, true))
            .add(new FatFingerCheckModule())
            .build();
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            validator.validate(order);
        }
        
        // Measure
        List<Long> timings = new ArrayList<>();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            RiskDecision decision = validator.validate(order);
            long end = System.nanoTime();
            timings.add(end - start);
            
            if (!decision.approved()) {
                fail("Unexpected rejection");
            }
        }
        
        printPerformanceStats("CompositeValidator", timings);
        
        // Verify p99 latency is under 5µs
        long p99Nanos = getPercentile(timings, 99.0);
        double p99Micros = p99Nanos / 1000.0;
        
        System.out.printf("p99 latency: %.2f µs (target: < 5 µs)%n", p99Micros);
        assertTrue(p99Micros < 5.0, 
            String.format("p99 latency %.2f µs exceeds target of 5 µs", p99Micros));
    }
    
    private void printPerformanceStats(String name, List<Long> timingsNanos) {
        timingsNanos.sort(Long::compareTo);
        
        long min = timingsNanos.get(0);
        long max = timingsNanos.get(timingsNanos.size() - 1);
        long p50 = getPercentile(timingsNanos, 50.0);
        long p95 = getPercentile(timingsNanos, 95.0);
        long p99 = getPercentile(timingsNanos, 99.0);
        long p999 = getPercentile(timingsNanos, 99.9);
        
        double avg = timingsNanos.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
        
        System.out.printf("%n=== %s Performance ===%n", name);
        System.out.printf("Iterations: %,d%n", timingsNanos.size());
        System.out.printf("Min:  %6d ns (%.2f µs)%n", min, min / 1000.0);
        System.out.printf("Avg:  %6.0f ns (%.2f µs)%n", avg, avg / 1000.0);
        System.out.printf("p50:  %6d ns (%.2f µs)%n", p50, p50 / 1000.0);
        System.out.printf("p95:  %6d ns (%.2f µs)%n", p95, p95 / 1000.0);
        System.out.printf("p99:  %6d ns (%.2f µs)%n", p99, p99 / 1000.0);
        System.out.printf("p999: %6d ns (%.2f µs)%n", p999, p999 / 1000.0);
        System.out.printf("Max:  %6d ns (%.2f µs)%n", max, max / 1000.0);
    }
    
    private long getPercentile(List<Long> sortedTimings, double percentile) {
        int index = (int) Math.ceil(sortedTimings.size() * percentile / 100.0) - 1;
        return sortedTimings.get(Math.max(0, Math.min(index, sortedTimings.size() - 1)));
    }
}
