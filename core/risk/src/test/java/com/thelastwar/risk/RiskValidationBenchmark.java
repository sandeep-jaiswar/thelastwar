package com.thelastwar.risk;

import com.thelastwar.eventbus.model.OrderEvent;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for risk validation performance.
 * 
 * Target: < 5µs per validation (p99)
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class RiskValidationBenchmark {
    
    private OrderEvent normalOrder;
    private CreditCheckModule creditCheck;
    private MarginCheckModule marginCheck;
    private FatFingerCheckModule fatFingerCheck;
    private CompositeRiskValidator compositeValidator;
    
    @Setup
    public void setup() {
        normalOrder = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        creditCheck = new CreditCheckModule(10_000_000L, true);
        marginCheck = new MarginCheckModule(100_000L, true);
        fatFingerCheck = new FatFingerCheckModule();
        
        compositeValidator = new CompositeRiskValidator.Builder()
            .add(creditCheck)
            .add(marginCheck)
            .add(fatFingerCheck)
            .build();
    }
    
    @Benchmark
    public void benchmarkCreditCheck(Blackhole bh) {
        RiskDecision decision = creditCheck.validate(normalOrder);
        bh.consume(decision);
    }
    
    @Benchmark
    public void benchmarkMarginCheck(Blackhole bh) {
        RiskDecision decision = marginCheck.validate(normalOrder);
        bh.consume(decision);
    }
    
    @Benchmark
    public void benchmarkFatFingerCheck(Blackhole bh) {
        RiskDecision decision = fatFingerCheck.validate(normalOrder);
        bh.consume(decision);
    }
    
    @Benchmark
    public void benchmarkCompositeValidator(Blackhole bh) {
        RiskDecision decision = compositeValidator.validate(normalOrder);
        bh.consume(decision);
    }
    
    @Benchmark
    public void benchmarkApprovedDecisionAllocation(Blackhole bh) {
        RiskDecision decision = RiskDecision.approve();
        bh.consume(decision);
    }
    
    @Benchmark
    public void benchmarkRejectionDecisionAllocation(Blackhole bh) {
        RiskDecision decision = RiskDecision.reject(
            RiskReasonCode.QUANTITY_TOO_LARGE,
            "Test rejection"
        );
        bh.consume(decision);
    }
}
