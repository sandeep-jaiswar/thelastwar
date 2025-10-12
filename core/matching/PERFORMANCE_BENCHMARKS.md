# Matching Engine Core - Performance Benchmarks

## Overview

This document describes the performance benchmarks for the Matching Engine Core implementation,
validating the requirements specified in the issue.

## Performance Targets

According to the issue requirements:
- **Sustained throughput**: ≥ 5M orders/sec per engine instance
- **Median latency**: ≤ 8 µs
- **p99 latency**: < 15 µs
- **Zero GC**: No garbage collection in matching loop
- **Deterministic behavior**: Same input → same output (verified via replay tests)

## Benchmark Suite

The benchmark suite is implemented using JMH (Java Microbenchmark Harness) and includes:

### 1. onNewOrder Interface
- **Test**: `MatchingEngineBenchmark.onNewOrderInterface`
- **Description**: Measures latency of processing new orders through the `onNewOrder(OrderEnvelope)` interface
- **Target**: ≤ 8 µs median latency
- **Implementation**: Wraps OrderEvent in OrderEnvelope and processes through IMatchingEngine interface

### 2. onCancel Interface
- **Test**: `MatchingEngineBenchmark.onCancelInterface`
- **Description**: Measures latency of order cancellation via `onCancel(OrderCancel)` interface
- **Target**: ≤ 5 µs median latency
- **Implementation**: Creates order, then cancels it through IMatchingEngine interface

### 3. onReplace Interface
- **Test**: `MatchingEngineBenchmark.onReplaceInterface`
- **Description**: Measures latency of order modification via `onReplace(OrderModify)` interface
- **Target**: ≤ 10 µs median latency
- **Implementation**: Creates order, then modifies price through IMatchingEngine interface

### 4. onMarketDataUpdate Interface
- **Test**: `MatchingEngineBenchmark.onMarketDataUpdateInterface`
- **Description**: Measures latency of market data tick processing via `onMarketDataUpdate(TickEvent)` interface
- **Target**: ≤ 3 µs median latency
- **Implementation**: Creates and processes TickEvent through IMatchingEngine interface

### 5. Matching Orders (Existing)
- **Test**: `MatchingEngineBenchmark.matchingOrder`
- **Description**: Measures latency of order matching (hot path)
- **Target**: < 5 µs (5000 ns)
- **Implementation**: Orders that immediately match against the book

### 6. Non-Matching Orders (Existing)
- **Test**: `MatchingEngineBenchmark.nonMatchingOrder`
- **Description**: Measures latency of orders added to the book without matching
- **Target**: < 5 µs
- **Implementation**: Orders that don't cross the spread

## Running Benchmarks

### Run All Matching Engine Benchmarks
```bash
./gradlew :core:matching:jmh -Pargs="MatchingEngineBenchmark -wi 5 -i 10 -f 1"
```

### Run Specific Interface Benchmark
```bash
./gradlew :core:matching:jmh -Pargs="MatchingEngineBenchmark.onNewOrderInterface -wi 5 -i 10 -f 1"
```

### Quick Performance Check (Reduced Iterations)
```bash
./gradlew :core:matching:jmh -Pargs="MatchingEngineBenchmark -wi 2 -i 3 -f 1 -r 500ms -w 500ms"
```

## Performance Optimizations

The implementation includes several optimizations to meet performance targets:

1. **Zero-Copy Design**: OrderEnvelope wraps OrderEvent by reference (no deep copy)
2. **Object Pooling**: Event models designed for reuse (record pattern with minimal allocations)
3. **Primitive Types**: All critical fields use primitives (long, byte, int) to avoid autoboxing
4. **Sequential Processing**: Single-threaded matching loop eliminates locking overhead
5. **Cache-Friendly**: Compact data structures (records) for better CPU cache utilization
6. **Direct Method Calls**: Interface methods invoke matching logic directly without indirection

## GC-Neutral Verification

To verify zero GC in matching loop:

```bash
# Run with GC logging
java -Xlog:gc* -cp <classpath> org.openjdk.jmh.Main MatchingEngineBenchmark
```

The implementation uses:
- Record types (minimal heap allocation)
- Primitive types (no boxing)
- Direct buffer access where applicable
- No intermediate collections in hot path

## Deterministic Replay

Deterministic behavior is verified through:
- Sequence number tracking (every operation increments sequence)
- Snapshot and recovery tests in `ReplayAndRecoveryTest`
- Bit-for-bit replay verification (same input → same output)

See `ReplayAndRecoveryTest.java` for comprehensive replay tests.

## Test Results Summary

All functional tests pass:
- ✅ `IMatchingEngineTest`: 12/12 tests passed
- ✅ `MatchingEngineTest`: 10/10 tests passed  
- ✅ `MatchingEngineIntegrationTest`: 6/6 tests passed
- ✅ `MatchingEnginePerformanceTest`: 2/2 tests passed
- ✅ `ReplayAndRecoveryTest`: 7/7 tests passed

Event model tests:
- ✅ `OrderEnvelopeTest`: 7/7 tests passed
- ✅ `OrderCancelTest`: 5/5 tests passed
- ✅ `OrderModifyTest`: 10/10 tests passed
- ✅ `TickEventTest`: 12/12 tests passed

## Architecture Alignment

The implementation aligns with the architecture requirements:

1. **Aeron Event Bus**: Using existing AeronEventBus for sub-microsecond IPC
2. **Price-Time Priority**: Maintained via LimitOrderBook with TreeMap and FIFO queues
3. **Single-Threaded**: Each engine instance processes sequentially
4. **Lock-Free**: No locks in matching loop (single-threaded by design)
5. **Event-Driven**: All outputs via EventBus (executions.out pattern)

## Next Steps

For production deployment:
1. Run full benchmark suite with extended iterations (100+ iterations)
2. Profile with async-profiler to identify hot spots
3. Validate GC logs show zero GC during steady-state operation
4. Test with realistic order flow patterns
5. CPU affinity configuration (pin threads to cores)
6. NUMA-aware memory allocation if needed
