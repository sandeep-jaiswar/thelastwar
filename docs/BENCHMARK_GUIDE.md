# EventBus Benchmark Suite

This document describes the performance benchmark harness for the EventBus implementation, designed to validate the following acceptance criteria:

- **p99 Latency**: < 10 µs
- **Sustained Throughput**: > 2M msgs/s for 60 seconds
- **GC Behavior**: No major GC events

## Overview

The benchmark suite includes:

1. **AeronBenchmark**: Core latency and throughput benchmarks for AeronEventBus
2. **SyntheticLoadBenchmark**: Realistic simulations of market data and order flow
3. **EventBusBenchmark**: Basic benchmarks for the InMemoryEventBus implementation

## Quick Start

### Running All Benchmarks

```bash
# Using the benchmark script
./scripts/run-benchmarks.sh --full

# Or directly with Gradle
./gradlew :core:eventbus:jmh
```

### Running Specific Tests

```bash
# Quick validation (short duration)
./scripts/run-benchmarks.sh --quick

# 60-second sustained load tests
./scripts/run-benchmarks.sh --sustained

# Full suite with Java Flight Recorder
./scripts/run-benchmarks.sh --full --with-jfr
```

### Running Individual Benchmarks

```bash
# Latency test
./gradlew :core:eventbus:jmh -Pargs="AeronBenchmark.benchmarkPublish"

# Throughput test
./gradlew :core:eventbus:jmh -Pargs="AeronBenchmark.benchmarkThroughput"

# 60-second sustained test
./gradlew :core:eventbus:jmh -Pargs="-f 1 -wi 3 -i 1 -r 60 AeronBenchmark.benchmarkSustained60Seconds"

# Latency distribution (percentiles)
./gradlew :core:eventbus:jmh -Pargs="AeronBenchmark.benchmarkLatencyDistribution"
```

## Benchmark Descriptions

### AeronBenchmark

**Purpose**: Validate core performance metrics for the Aeron-based EventBus.

**Benchmarks**:

- `benchmarkPublish`: Single event publish latency (average time)
- `benchmarkPublish128B`: 128-byte payload publish latency
- `benchmarkPublishAndReceive`: Full round-trip latency (publish + receive)
- `benchmarkEventCreation`: Event object creation overhead
- `benchmarkSubscribe`: Subscription operation cost
- `benchmarkThroughput`: Sustained publishing throughput
- `benchmarkThroughput128B`: Throughput with 128-byte payloads
- `benchmarkLatencyDistribution`: Sample-based latency for percentile calculation
- `benchmarkSustained60Seconds`: **60-second sustained throughput test** (NEW)

**Configuration**:
- Warmup: 5 iterations × 2 seconds
- Measurement: 10 iterations × 2 seconds (except sustained tests)
- JVM: 2GB heap, G1GC with aggressive pause time targets
- GC logging enabled to `/tmp/aeron_gc.log`

### SyntheticLoadBenchmark (NEW)

**Purpose**: Simulate realistic trading system workloads with market data and orders.

**Benchmarks**:

- `benchmarkSustainedThroughput`: Sustained market data updates (60 seconds)
- `benchmarkMixedWorkload`: 70% market data + 30% orders (realistic mix)
- `benchmarkHighFrequencyTicks`: Rapid tick updates across multiple symbols
- `benchmarkOrderFlow`: Order lifecycle events (new, filled, cancelled)

**Configuration**:
- Warmup: 3 iterations × 5 seconds
- Measurement: 1 iteration × 60 seconds
- Simulates: 100 symbols with varying payload sizes
- Event types: Market data (128B), Orders (256B)

**Key Features**:
- Pre-allocated events to minimize GC
- Random distribution to simulate realistic patterns
- Counters for published vs. received events (message loss detection)

### EventBusBenchmark

**Purpose**: Baseline benchmarks for the InMemoryEventBus implementation.

**Benchmarks**:

- `benchmarkPublish`: In-memory publish latency
- `benchmarkPublishAndReceive`: In-memory round-trip
- `benchmarkEventCreation`: Event creation overhead
- `benchmarkSubscribe`: Subscription cost
- `benchmarkNanoTime`: System timer overhead (baseline)

## Interpreting Results

### JMH Output Format

```
Benchmark                                      Mode  Cnt    Score   Error  Units
AeronBenchmark.benchmarkPublish               avgt   10    3245   ± 152  ns/op
AeronBenchmark.benchmarkThroughput           thrpt   10  305254   ± 15234  ops/s
AeronBenchmark.benchmarkLatencyDistribution sample  10000  3567   ±  89  ns/op
  p99.99                                                      9123          ns/op
```

### Validation Criteria

#### 1. p99 Latency < 10 µs

Check the `benchmarkLatencyDistribution` results:
```
AeronBenchmark.benchmarkLatencyDistribution:p99.99    9123 ns/op
```
**PASS**: 9,123 ns < 10,000 ns (10 µs)

#### 2. Throughput > 2M msgs/s (60 seconds)

Check the `benchmarkSustained60Seconds` results:
```
AeronBenchmark.benchmarkSustained60Seconds  thrpt  2534821 ops/s
```
**PASS**: 2,534,821 ops/s > 2,000,000 ops/s

#### 3. No Major GC Events

Check the GC log at `/tmp/aeron_gc.log`:
```bash
grep "Pause Full" /tmp/aeron_gc.log
```
**PASS**: No output (no Full GC events)

Or check max GC pause:
```bash
grep "Pause" /tmp/aeron_gc.log | grep -oP '\d+\.\d+ms' | sort -n | tail -1
```
**PASS**: < 10ms (preferably < 1ms)

## System Requirements

### Minimum Requirements

- **CPU**: 4 cores (modern x86_64)
- **RAM**: 4GB available
- **OS**: Linux (for best performance)
- **Java**: Java 17+ (targeting Java 25)

### Recommended Setup

- **CPU**: 8+ cores with isolated cores for benchmarks
- **CPU Governor**: Set to "performance" mode
- **RAM**: 16GB+
- **Hyper-Threading**: Disabled for lowest latency
- **Swap**: Disabled
- **CPU Affinity**: Pin JVM to dedicated cores

## Performance Tuning

For detailed tuning recommendations, see:

- **[PERFORMANCE_TUNING.md](../docs/PERFORMANCE_TUNING.md)**: Comprehensive tuning guide
- **[AERON_README.md](../core/eventbus/AERON_README.md)**: Aeron-specific configuration
- **[AERON_CONFIG.md](../core/eventbus/AERON_CONFIG.md)**: Advanced Aeron settings

### Quick Tuning Tips

```bash
# Set CPU governor to performance
sudo cpupower frequency-set -g performance

# Increase network buffers (even for IPC)
sudo sysctl -w net.core.rmem_max=134217728
sudo sysctl -w net.core.wmem_max=134217728

# Pin benchmark to specific cores
taskset -c 4,5,6,7 ./gradlew :core:eventbus:jmh
```

## Java Flight Recorder

### Enable JFR During Benchmarks

```bash
# Using the benchmark script
./scripts/run-benchmarks.sh --full --with-jfr

# Or manually
./gradlew :core:eventbus:jmh -Pargs="AeronBenchmark" \
  -Djvm.args="-XX:StartFlightRecording=disk=true,filename=/tmp/bench.jfr"
```

### Analyze JFR Recording

```bash
# Open with Java Mission Control
jmc /tmp/bench.jfr

# Or use command-line tools
jfr print --events jdk.GCPhasePause /tmp/bench.jfr
jfr print --events jdk.ObjectAllocationInNewTLAB /tmp/bench.jfr
```

### What to Look For in JFR

- **GC Pauses**: Should be < 1ms, no Full GC
- **Allocations**: Minimal allocations in hot path
- **CPU Usage**: High CPU usage during benchmark (expected)
- **Thread States**: Polling thread should be mostly running
- **Lock Contention**: Should be minimal/zero

## Troubleshooting

### Low Throughput

**Symptoms**: < 2M msgs/s

**Common Causes**:
1. CPU frequency scaling enabled
2. Competing processes on CPU
3. GC pauses
4. Small term buffers

**Solutions**:
```bash
# Check CPU frequency
cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor

# Set to performance
sudo cpupower frequency-set -g performance

# Check for competing processes
top -H -p $(pgrep -f java)
```

### High Latency

**Symptoms**: p99 > 10 µs

**Common Causes**:
1. Context switching
2. Cache misses
3. Hyper-threading interference
4. System interrupts

**Solutions**:
```bash
# Pin to dedicated cores
taskset -c 4,5 ./gradlew :core:eventbus:jmh

# Isolate cores at boot (add to kernel params)
isolcpus=4,5,6,7

# Check context switches
pidstat -w -p $(pgrep -f java) 1
```

### GC Issues

**Symptoms**: Full GC events, high pause times

**Common Causes**:
1. Heap too small
2. High allocation rate
3. Fragmentation
4. Wrong GC algorithm

**Solutions**:
```bash
# Increase heap
-Xms8G -Xmx8G

# Try ZGC (Java 17+)
-XX:+UseZGC

# Enable detailed GC logging
-Xlog:gc*:file=gc.log:time,uptime,level,tags

# Analyze logs
java -jar gceasy.jar gc.log
```

## Continuous Integration

### Running in CI/CD

For CI environments with limited resources:

```bash
# Quick validation (5 minutes)
./scripts/run-benchmarks.sh --quick

# Or specific quick tests
./gradlew :core:eventbus:jmh -Pargs="-f 1 -wi 2 -i 3 -r 1 AeronBenchmark.benchmarkPublish"
```

### Baseline Comparison

Save baseline results and compare:

```bash
# Save baseline
./scripts/run-benchmarks.sh --quick > baseline.txt

# After changes, compare
./scripts/run-benchmarks.sh --quick > current.txt
diff baseline.txt current.txt
```

## Example Results

### Expected Output (Modern Hardware)

```
Benchmark                                          Mode  Cnt      Score     Error  Units
AeronBenchmark.benchmarkPublish                   avgt   10   3245.321 ± 152.431  ns/op
AeronBenchmark.benchmarkPublish128B               avgt   10   3412.156 ± 178.623  ns/op
AeronBenchmark.benchmarkPublishAndReceive         avgt   10   6823.459 ± 342.891  ns/op
AeronBenchmark.benchmarkThroughput               thrpt   10 305254.123 ± 15234.567  ops/s
AeronBenchmark.benchmarkSustained60Seconds       thrpt    1 2534821.456              ops/s
AeronBenchmark.benchmarkLatencyDistribution     sample 10000   3567.890 ±  89.234  ns/op
  p50                                                           3123.000            ns/op
  p90                                                           4567.000            ns/op
  p99                                                           7890.000            ns/op
  p99.9                                                         9123.000            ns/op
  p99.99                                                        9823.000            ns/op
```

**Analysis**:
- ✅ p99.99 = 9,823 ns < 10,000 ns (PASS)
- ✅ Sustained throughput = 2.5M msgs/s > 2M msgs/s (PASS)
- ✅ No Full GC events (check gc.log)

## Contributing

When adding new benchmarks:

1. Follow existing naming conventions
2. Include clear documentation
3. Set appropriate warmup/measurement iterations
4. Add to the benchmark script if relevant
5. Update this README

## References

- [JMH Documentation](https://github.com/openjdk/jmh)
- [Aeron Performance Guide](https://github.com/real-logic/aeron/wiki/Performance-Testing)
- [Java Flight Recorder Guide](https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/)
- [Performance Tuning Guide](../docs/PERFORMANCE_TUNING.md)
