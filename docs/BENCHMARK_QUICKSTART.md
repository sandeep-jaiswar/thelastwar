# EventBus Performance Validation - Quick Start

This guide provides a quick reference for validating the EventBus performance against acceptance criteria.

## Acceptance Criteria

- ✅ **p99 Latency**: < 10 µs
- ✅ **Sustained Throughput**: > 2M msgs/s for 60 seconds
- ✅ **GC Behavior**: No major GC events

## Quick Validation (5 minutes)

Run the quick benchmark suite to get preliminary results:

```bash
# Quick validation (3-5 minutes)
./scripts/run-benchmarks.sh --quick

# Results will be in: benchmark-results/<timestamp>/
```

This runs shortened versions of key benchmarks and provides a quick pass/fail indication.

## Full Validation (60+ minutes)

Run the complete benchmark suite including 60-second sustained tests:

```bash
# Full benchmark suite (60-90 minutes)
./scripts/run-benchmarks.sh --full

# Results will be in: benchmark-results/<timestamp>/
```

## Sustained Load Only (20 minutes)

Run only the 60-second sustained throughput tests:

```bash
# Sustained load tests (15-20 minutes)
./scripts/run-benchmarks.sh --sustained

# Results will be in: benchmark-results/<timestamp>/
```

## Individual Benchmark Commands

### Test 1: p99 Latency < 10 µs

```bash
./gradlew :core:eventbus:jmh -Pargs="AeronBenchmark.benchmarkLatencyDistribution -f 1 -wi 5 -i 1"
```

**What to check**: Look for the p99.99 value in the output:
```
AeronBenchmark.benchmarkLatencyDistribution  sample  10000  3567 ±  89  ns/op
  p99.99                                                     9123         ns/op
```

**Pass criteria**: p99.99 < 10,000 ns (10 µs)

### Test 2: Throughput > 2M msgs/s (60 seconds)

```bash
./gradlew :core:eventbus:jmh -Pargs="AeronBenchmark.benchmarkSustained60Seconds -f 1 -wi 3 -i 1"
```

**What to check**: Look for the Score in throughput (thrpt) mode:
```
AeronBenchmark.benchmarkSustained60Seconds  thrpt  2534821.456  ops/s
```

**Pass criteria**: Score > 2,000,000 ops/s

### Test 3: No Major GC Events

GC logging is automatically enabled. After running any benchmark, check:

```bash
# Check for Full GC events (should be empty)
grep "Pause Full" /tmp/aeron_gc.log

# Check max GC pause time
grep "Pause" /tmp/aeron_gc.log | grep -oP '\d+\.\d+ms' | sort -n | tail -1
```

**Pass criteria**: 
- No "Pause Full" events
- Max pause < 10ms (preferably < 1ms)

## With Java Flight Recorder

To capture detailed profiling data:

```bash
./scripts/run-benchmarks.sh --full --with-jfr
```

Then analyze with Java Mission Control:
```bash
jmc benchmark-results/<timestamp>/flight-recording.jfr
```

## Interpreting Results

### Sample Output (PASSING)

```
Benchmark                                          Mode  Cnt      Score     Error  Units
AeronBenchmark.benchmarkLatencyDistribution     sample 10000   3567.890 ±  89.234  ns/op
  p50                                                           3123.000            ns/op
  p90                                                           4567.000            ns/op
  p99                                                           7890.000            ns/op
  p99.9                                                         9123.000            ns/op
  p99.99                                                        9823.000            ns/op  ✅ < 10,000

AeronBenchmark.benchmarkSustained60Seconds       thrpt    1 2534821.456              ops/s  ✅ > 2,000,000

GC Analysis:
  Minor GC events: 12
  Major GC events: 0  ✅
  Max GC pause: 0.8ms  ✅ < 10ms
```

All criteria PASSED ✅

### Sample Output (FAILING)

```
Benchmark                                          Mode  Cnt      Score     Error  Units
AeronBenchmark.benchmarkLatencyDistribution     sample 10000  12345.678 ± 234.567  ns/op
  p99.99                                                       15234.000            ns/op  ❌ > 10,000

AeronBenchmark.benchmarkSustained60Seconds       thrpt    1 1234567.890              ops/s  ❌ < 2,000,000

GC Analysis:
  Major GC events: 3  ❌
  Max GC pause: 45.2ms  ❌ > 10ms
```

Criteria FAILED - see [PERFORMANCE_TUNING.md](PERFORMANCE_TUNING.md) for optimization

## Before Running Benchmarks

### System Preparation (Recommended)

```bash
# Set CPU governor to performance
sudo cpupower frequency-set -g performance

# Increase network buffers (even for IPC)
sudo sysctl -w net.core.rmem_max=134217728
sudo sysctl -w net.core.wmem_max=134217728

# Pin to specific cores (optional, adjust core numbers)
taskset -c 4,5,6,7 ./scripts/run-benchmarks.sh --full
```

### Minimum System Requirements

- CPU: 4+ cores (modern x86_64)
- RAM: 4GB available
- OS: Linux preferred (for best performance)
- Java: Java 17+ (targeting Java 25)

### Recommended Setup

- CPU: 8+ cores with isolated cores
- RAM: 16GB+
- CPU Governor: "performance" mode
- Hyper-Threading: Disabled for lowest latency
- CPU Affinity: Pin to dedicated cores

## Troubleshooting

### Issue: Low Throughput (< 2M msgs/s)

**Check**:
```bash
# CPU frequency scaling
cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor

# Should be "performance", not "powersave"
```

**Fix**:
```bash
sudo cpupower frequency-set -g performance
```

### Issue: High Latency (p99 > 10 µs)

**Common causes**:
1. Context switching (check with `pidstat -w`)
2. CPU frequency scaling
3. Competing processes
4. GC pauses

**Fix**: See [PERFORMANCE_TUNING.md](PERFORMANCE_TUNING.md) section on "High Latency"

### Issue: Major GC Events

**Common causes**:
1. Heap too small
2. High allocation rate
3. Wrong GC algorithm

**Fix**: 
```bash
# Try larger heap
./gradlew :core:eventbus:jmh -Djvm.args="-Xms8G -Xmx8G"

# Or use ZGC (Java 17+)
./gradlew :core:eventbus:jmh -Djvm.args="-XX:+UseZGC -Xms8G -Xmx8G"
```

## Documentation

For detailed information:

- **[PERFORMANCE_TUNING.md](PERFORMANCE_TUNING.md)**: Comprehensive system and JVM tuning
- **[BENCHMARK_GUIDE.md](BENCHMARK_GUIDE.md)**: Complete benchmark documentation
- **[AERON_README.md](../core/eventbus/AERON_README.md)**: Aeron implementation details
- **[AERON_CONFIG.md](../core/eventbus/AERON_CONFIG.md)**: Advanced Aeron configuration

## CI/CD Integration

For continuous integration environments:

```bash
# Quick validation in CI (5 minutes)
./scripts/run-benchmarks.sh --quick

# Set performance threshold checks
./gradlew :core:eventbus:jmh -Pargs="AeronBenchmark.benchmarkPublish" | \
  grep "Score" | awk '{if ($4 > 10000) exit 1}'  # Fail if > 10µs
```

## Next Steps

1. Run `./scripts/run-benchmarks.sh --quick` for initial validation
2. If passing, run `./scripts/run-benchmarks.sh --full` for complete validation
3. Review results in `benchmark-results/<timestamp>/`
4. Tune system/JVM if not meeting criteria (see PERFORMANCE_TUNING.md)
5. Re-run benchmarks after tuning

## Support

If benchmarks consistently fail to meet criteria:

1. Check system requirements are met
2. Verify no other processes are competing for resources
3. Follow the tuning guide recommendations
4. Check GC logs for excessive GC activity
5. Use Flight Recorder to identify bottlenecks
6. Review the troubleshooting section in PERFORMANCE_TUNING.md
