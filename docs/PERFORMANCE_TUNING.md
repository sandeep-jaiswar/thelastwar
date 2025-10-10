# EventBus Performance Tuning Guide

## Overview

This guide provides detailed tuning recommendations for optimizing the EventBus (particularly AeronEventBus) to meet the following acceptance criteria:

- **p99 latency**: < 10 µs
- **Sustained throughput**: > 2M msgs/s for 60+ seconds
- **GC behavior**: No major GC events during sustained load

## System-Level Tuning

### CPU Affinity

Pin critical threads to dedicated CPU cores to reduce context switching and cache misses.

#### Using taskset (Linux)

```bash
# Pin JVM process to cores 0-3
taskset -c 0-3 java -jar your-app.jar

# Pin to specific isolated cores (recommended for production)
taskset -c 4,5,6,7 java -jar your-app.jar
```

#### Using numactl (NUMA systems)

```bash
# Pin to NUMA node 0, cores 0-3
numactl --cpunodebind=0 --membind=0 -- java -jar your-app.jar
```

#### Isolate CPUs (Linux kernel parameter)

Add to `/etc/default/grub`:
```
GRUB_CMDLINE_LINUX="isolcpus=4,5,6,7"
```

Then update grub and reboot:
```bash
sudo update-grub
sudo reboot
```

### Disable CPU Frequency Scaling

Set CPU governor to "performance" mode:

```bash
# Check current governor
cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor

# Set to performance mode (all CPUs)
for i in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
    echo performance | sudo tee $i
done

# Or use cpupower tool
sudo cpupower frequency-set -g performance
```

### Disable Hyper-Threading (Optional)

For lowest latency, disable hyper-threading to avoid sharing physical cores:

```bash
# Check hyper-threading status
lscpu | grep "Thread(s) per core"

# Disable via BIOS, or dynamically:
echo 0 | sudo tee /sys/devices/system/cpu/cpu*/topology/thread_siblings_list
```

### Network Stack Tuning (for UDP/IPC)

Even for IPC, these settings can improve performance:

```bash
# Increase socket buffer sizes
sudo sysctl -w net.core.rmem_max=134217728
sudo sysctl -w net.core.wmem_max=134217728
sudo sysctl -w net.core.rmem_default=134217728
sudo sysctl -w net.core.wmem_default=134217728

# Increase network device backlog
sudo sysctl -w net.core.netdev_max_backlog=5000

# Make permanent by adding to /etc/sysctl.conf
```

### Huge Pages (Optional)

For large memory allocations:

```bash
# Check current huge page configuration
cat /proc/meminfo | grep Huge

# Allocate 1000 huge pages (2MB each = 2GB)
echo 1000 | sudo tee /proc/sys/vm/nr_hugepages

# Make permanent in /etc/sysctl.conf
echo "vm.nr_hugepages = 1000" | sudo tee -a /etc/sysctl.conf
```

## JVM Tuning

### Recommended JVM Flags

For ultra-low latency (< 10 µs p99):

```bash
java \
  -Xms4G -Xmx4G \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=1 \
  -XX:G1NewSizePercent=50 \
  -XX:G1MaxNewSizePercent=80 \
  -XX:G1HeapRegionSize=32M \
  -XX:InitiatingHeapOccupancyPercent=35 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:G1MixedGCLiveThresholdPercent=85 \
  -XX:G1MixedGCCountTarget=8 \
  -XX:G1OldCSetRegionThresholdPercent=10 \
  -XX:+ParallelRefProcEnabled \
  -XX:+UseStringDeduplication \
  -XX:+AlwaysPreTouch \
  -Xlog:gc*:file=gc.log:time,uptime,level,tags \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.base/java.util.zip=ALL-UNNAMED \
  -jar your-app.jar
```

### Alternative: ZGC (Java 17+)

For sub-millisecond pause times with larger heaps:

```bash
java \
  -Xms8G -Xmx8G \
  -XX:+UseZGC \
  -XX:ZCollectionInterval=5 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+AlwaysPreTouch \
  -Xlog:gc*:file=gc_zgc.log:time,uptime,level,tags \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.base/java.util.zip=ALL-UNNAMED \
  -jar your-app.jar
```

### Alternative: Shenandoah GC

```bash
java \
  -Xms4G -Xmx4G \
  -XX:+UseShenandoahGC \
  -XX:ShenandoahGCHeuristics=compact \
  -XX:+AlwaysPreTouch \
  -Xlog:gc*:file=gc_shenandoah.log:time,uptime,level,tags \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.base/java.util.zip=ALL-UNNAMED \
  -jar your-app.jar
```

### GC Flag Explanations

| Flag | Purpose |
|------|---------|
| `-Xms4G -Xmx4G` | Fixed heap size (prevents resizing overhead) |
| `-XX:+UseG1GC` | Use G1 garbage collector (good balance) |
| `-XX:MaxGCPauseMillis=1` | Target max GC pause time (aggressive) |
| `-XX:G1NewSizePercent=50` | Min young generation size (50% of heap) |
| `-XX:G1MaxNewSizePercent=80` | Max young generation size (80% of heap) |
| `-XX:G1HeapRegionSize=32M` | Size of G1 regions (tune based on heap) |
| `-XX:InitiatingHeapOccupancyPercent=35` | Start concurrent cycle early |
| `-XX:+AlwaysPreTouch` | Pre-allocate all memory at startup |
| `-XX:+ParallelRefProcEnabled` | Parallel reference processing |
| `-Xlog:gc*:file=gc.log` | Enable detailed GC logging |

## Aeron-Specific Tuning

### MediaDriver Configuration

Create custom MediaDriver for optimal performance:

```java
MediaDriver.Context ctx = new MediaDriver.Context()
    // Threading mode
    .threadingMode(ThreadingMode.SHARED)  // or DEDICATED for max throughput
    
    // Buffer sizes (tune based on workload)
    .publicationTermBufferLength(2 * 1024 * 1024)  // 2MB
    .ipcTermBufferLength(2 * 1024 * 1024)          // 2MB
    .publicationWindowLength(1024 * 1024)           // 1MB
    
    // Performance optimizations
    .termBufferSparseFile(false)  // Pre-allocate buffers
    .conductorIdleStrategy(new BackoffIdleStrategy(
        100, 10, 1, 1000))  // Tuned idle strategy
    
    // Cleanup
    .dirDeleteOnStart(true)
    .dirDeleteOnShutdown(true);

MediaDriver driver = MediaDriver.launch(ctx);
```

### Idle Strategy Tuning

Choose idle strategy based on latency requirements:

```java
// Lowest latency (high CPU usage)
new BusySpinIdleStrategy()

// Balanced (recommended)
new BackoffIdleStrategy(100, 10, 1, 1000)  // 100 spins, 10 yields, 1µs-1ms park

// Lower CPU usage
new SleepingIdleStrategy(1000)  // 1ms sleep
```

### Fragment Limit

Tune polling batch size:

```java
// Higher throughput (batch processing)
int fragmentLimit = 1024;

// Lower latency (smaller batches)
int fragmentLimit = 64;
```

## Application-Level Tuning

### Object Pooling

Use EventPool to eliminate allocations:

```java
EventPool pool = new EventPool(10000);  // Pre-allocate 10k events

// In hot path
EventPool.MutableEvent event = pool.acquire();
event.set(timestamp, seq, sourceId, eventType, header, payload);
eventBus.publish(event.toEvent());
pool.release(event);
```

### Batch Publishing

Publish multiple events in tight loops:

```java
// Instead of single publishes
for (Event event : events) {
    while (!eventBus.publish(event)) {
        Thread.onSpinWait();  // Spin-wait for back-pressure
    }
}
```

### Handler Optimization

Keep event handlers lightweight:

```java
// Good: Fast processing
eventBus.subscribe(EventType.MARKET_DATA, event -> {
    orderBook.update(event);  // Direct, fast update
});

// Bad: Blocking operations
eventBus.subscribe(EventType.MARKET_DATA, event -> {
    database.save(event);  // I/O in event handler!
});
```

## Monitoring and Profiling

### Java Flight Recorder

Enable JFR for production profiling with minimal overhead:

```bash
# Start with JFR enabled
java \
  -XX:StartFlightRecording=disk=true,dumponexit=true,filename=flight.jfr \
  -XX:FlightRecorderOptions=stackdepth=256 \
  -jar your-app.jar

# Or start recording dynamically
jcmd <pid> JFR.start name=my-recording settings=profile duration=60s filename=recording.jfr
```

### JFR Settings for Low-Latency Systems

Create custom JFR profile `low-latency.jfc`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0" label="Low Latency">
  <!-- Reduce overhead -->
  <event name="jdk.ThreadAllocationStatistics">
    <setting name="enabled">true</setting>
    <setting name="period">10 s</setting>
  </event>
  <event name="jdk.GCPhasePause">
    <setting name="enabled">true</setting>
    <setting name="threshold">1 ms</setting>
  </event>
  <event name="jdk.ObjectAllocationInNewTLAB">
    <setting name="enabled">true</setting>
    <setting name="stackTrace">false</setting>
  </event>
</configuration>
```

Use with:
```bash
java -XX:StartFlightRecording=settings=low-latency.jfc,filename=app.jfr -jar your-app.jar
```

### GC Logging Analysis

Analyze GC logs to identify issues:

```bash
# View GC summary
grep "Pause" gc.log | sort -k9 -n | tail -20

# Or use GC log analyzers
java -jar gceasy.jar gc.log
```

### Metrics to Monitor

Track these metrics during benchmarks:

```java
// Throughput
long published = eventBus.getPublishedEventCount();
long rate = published / durationSeconds;
System.out.println("Throughput: " + rate + " msgs/s");

// Latency (use JMH for accurate percentiles)
// p50, p90, p99, p99.9, p99.99, max

// GC pauses
// Minor GC count, Major GC count, Max pause time

// System
// CPU usage, Context switches, Memory usage
```

## Running Benchmarks

### Quick Benchmark Run

```bash
# Run all benchmarks
./gradlew :core:eventbus:jmh

# Run specific benchmark
./gradlew :core:eventbus:jmh -Pargs="AeronBenchmark.benchmarkPublish"
```

### Sustained Load Test (60 seconds)

```bash
# Sustained throughput test
./gradlew :core:eventbus:jmh -Pargs="-f 1 -wi 3 -i 1 -r 60 AeronBenchmark.benchmarkSustained60Seconds"

# Synthetic load benchmark
./gradlew :core:eventbus:jmh -Pargs="-f 1 -wi 3 -i 1 -r 60 SyntheticLoadBenchmark.benchmarkSustainedThroughput"
```

### Latency Distribution Test

```bash
# Sample-based latency measurement (includes percentiles)
./gradlew :core:eventbus:jmh -Pargs="-f 1 -wi 5 -i 1 -r 60 AeronBenchmark.benchmarkLatencyDistribution"
```

### With Flight Recorder

```bash
# Run benchmark with JFR enabled
./gradlew :core:eventbus:jmh -Pargs="AeronBenchmark" \
  -Djvm.args="-XX:StartFlightRecording=disk=true,filename=/tmp/bench.jfr"
```

## Validation Checklist

Use this checklist to validate acceptance criteria:

- [ ] **p99 Latency < 10 µs**
  - Run: `./gradlew :core:eventbus:jmh -Pargs=".*benchmarkLatencyDistribution"`
  - Check: p99 value in benchmark output
  - Expected: < 10,000 ns

- [ ] **Throughput > 2M msgs/s (60 seconds)**
  - Run: `./gradlew :core:eventbus:jmh -Pargs="-r 60 .*benchmarkSustained60Seconds"`
  - Check: Score (ops/s) in benchmark output
  - Expected: > 2,000,000 ops/s

- [ ] **No Major GC Events**
  - Check: `/tmp/aeron_gc.log` or `/tmp/gc.log`
  - Analyze: GC pause times, frequency
  - Expected: No Full GC, all pauses < 10ms (preferably < 1ms)

## Troubleshooting

### High Latency

**Symptoms**: p99 > 10 µs, inconsistent latency

**Solutions**:
1. Check CPU affinity: `taskset -pc <pid>`
2. Verify CPU governor: `cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor`
3. Check for GC pauses in logs
4. Reduce fragment limit to 64-128
5. Use BusySpinIdleStrategy for lowest latency

### Low Throughput

**Symptoms**: < 2M msgs/s sustained

**Solutions**:
1. Increase term buffer size to 4MB
2. Increase fragment limit to 512-1024
3. Use DEDICATED threading mode
4. Check for handler bottlenecks
5. Verify no I/O in hot path

### GC Issues

**Symptoms**: Frequent GC pauses, Full GC events

**Solutions**:
1. Increase heap size: `-Xms8G -Xmx8G`
2. Use object pooling for events
3. Pre-allocate collections
4. Switch to ZGC or Shenandoah
5. Review allocation with JFR

### Back Pressure

**Symptoms**: `publish()` returns false frequently

**Solutions**:
1. Increase term buffer length
2. Reduce handler processing time
3. Use multiple streams/channels
4. Add flow control to publisher
5. Monitor subscriber lag

## Production Deployment

### Recommended Configuration

For production deployment with latency-critical workloads:

```bash
#!/bin/bash
# production-start.sh

# System tuning
sudo sysctl -w net.core.rmem_max=134217728
sudo sysctl -w net.core.wmem_max=134217728

# CPU governor
for i in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
    echo performance | sudo tee $i
done

# Start application with optimized JVM
taskset -c 4,5,6,7 java \
  -Xms4G -Xmx4G \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=1 \
  -XX:G1NewSizePercent=50 \
  -XX:+AlwaysPreTouch \
  -XX:StartFlightRecording=disk=true,maxsize=1G,dumponexit=true,filename=/var/log/app/flight.jfr \
  -Xlog:gc*:file=/var/log/app/gc.log:time,uptime,level,tags \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.base/java.util.zip=ALL-UNNAMED \
  -jar /opt/thelastwar/app.jar
```

### Monitoring in Production

Set up monitoring for:
- EventBus throughput (msgs/s)
- Publish latency (p50, p99, p99.9)
- GC pause frequency and duration
- CPU usage and context switches
- Aeron buffer utilization

## References

- [Aeron Documentation](https://github.com/real-logic/aeron/wiki)
- [G1GC Tuning Guide](https://www.oracle.com/technical-resources/articles/java/g1gc.html)
- [Java Flight Recorder](https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/about.htm)
- [Linux Performance Tools](http://www.brendangregg.com/linuxperf.html)
