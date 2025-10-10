# Aeron EventBus Configuration Guide

## Quick Reference

### System Requirements

- **Java**: 25+ (OpenJDK or similar)
- **Memory**: 2GB+ heap, 500MB+ direct memory
- **CPU**: 2+ cores recommended
- **OS**: Linux (best), macOS, Windows

### JVM Arguments

Required for Aeron on Java 17+:

```bash
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
--add-opens java.base/java.util.zip=ALL-UNNAMED
```

Recommended JVM settings for production:

```bash
-Xms4G -Xmx4G                    # Fixed heap size
-XX:+UseG1GC                      # G1 garbage collector
-XX:MaxGCPauseMillis=1            # Target 1ms GC pause
-XX:+AlwaysPreTouch               # Pre-touch memory
-XX:+UseLargePages                # Use large pages if available
-Daeron.client.liveness.timeout=5000000000  # 5s liveness timeout
```

### Gradle Configuration

In `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.aeron:aeron-all:1.44.1")
}

tasks.test {
    jvmArgs(
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.zip=ALL-UNNAMED"
    )
}
```

## Performance Tuning

### Network Configuration

For IPC (in-process):
- No network tuning required
- Uses shared memory

For UDP transport (future):
```bash
# Increase socket buffer sizes
sudo sysctl -w net.core.rmem_max=2097152
sudo sysctl -w net.core.wmem_max=2097152
```

### CPU Affinity

Pin threads to specific cores:

```bash
# Example: Pin Java process to cores 0-3
taskset -c 0-3 java -jar your-app.jar
```

Or programmatically in Java:
```java
// Requires JNA or similar
Thread pollingThread = new Thread(...);
// Set affinity using OS-specific APIs
```

### Memory Configuration

```java
MediaDriver.Context ctx = new MediaDriver.Context()
    // Increase for high throughput
    .publicationTermBufferLength(2 * 1024 * 1024)  // 2MB
    .ipcTermBufferLength(2 * 1024 * 1024)          // 2MB
    
    // For low latency
    .conductorIdleStrategy(new BusySpinIdleStrategy())
    .senderIdleStrategy(new BusySpinIdleStrategy())
    .receiverIdleStrategy(new BusySpinIdleStrategy())
    ;
```

### Threading Modes

```java
// SHARED: Best for single-threaded or low-contention (DEFAULT)
.threadingMode(ThreadingMode.SHARED)

// DEDICATED: Separate threads for each component (higher throughput)
.threadingMode(ThreadingMode.DEDICATED)

// SHARED_NETWORK: Shared network, dedicated conductor
.threadingMode(ThreadingMode.SHARED_NETWORK)
```

## Monitoring

### Enable Aeron Metrics

```bash
# Add to JVM args
-Daeron.event.log=admin
-Daeron.event.log.filename=/tmp/aeron-events.log
```

### Access Counters

```java
// Via Aeron API
CountersReader counters = aeron.countersReader();
counters.forEach((counterId, typeId, keyBuffer, label) -> {
    long value = counters.getCounterValue(counterId);
    System.out.println(label + ": " + value);
});
```

### Key Metrics

- `bytes-sent`: Total bytes published
- `bytes-received`: Total bytes consumed
- `back-pressure-events`: Count of back pressure occurrences
- `errors`: Error count

## Production Deployment

### Health Checks

```java
public boolean isHealthy(AeronEventBus eventBus) {
    return eventBus.getPublishedEventCount() >= 0;  // Simple check
}
```

### Graceful Shutdown

```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    System.out.println("Shutting down EventBus...");
    eventBus.stop();
    System.out.println("EventBus stopped");
}));
```

### Resource Limits

```bash
# Increase file descriptors
ulimit -n 65536

# Increase locked memory (for large pages)
ulimit -l unlimited
```

## Troubleshooting

### Debug Logging

Enable Aeron debug output:

```java
System.setProperty("aeron.debug.timeout", "5000");
System.setProperty("aeron.client.liveness.timeout", "5000000000");
```

### Common Warnings

**Warning**: "Could not allocate large pages"
- Solution: Configure OS for large pages or ignore (minor performance impact)

**Warning**: "Publication back pressure"
- Solution: Increase term buffer or reduce publishing rate

**Error**: "Aeron client inactive"
- Solution: Check liveness timeout, ensure proper cleanup

## Examples

See:
- `AeronEventBusTest.java` - Functional examples
- `AeronBenchmark.java` - Performance benchmarking
- `AERON_README.md` - Complete documentation
