# EventBus Metrics & Backpressure Guide

## Overview

The EventBus implementation includes comprehensive observability and safety mechanisms to handle production workloads reliably. This guide covers metrics collection, backpressure control, and monitoring best practices.

## Metrics Collection

### Architecture

Metrics are collected using **Micrometer**, which provides a vendor-neutral facade for metrics instrumentation. Metrics can be exported to:
- Prometheus (recommended for production)
- Grafana Cloud
- DataDog
- New Relic
- CloudWatch
- Any other Micrometer-supported backend

### Available Metrics

#### Counters

| Metric | Description | Type |
|--------|-------------|------|
| `eventbus.events.published` | Total events successfully published | Counter |
| `eventbus.backpressure.events` | Backpressure activations detected | Counter |
| `eventbus.messages.dropped` | Messages dropped due to backpressure | Counter |
| `eventbus.publishes.failed` | Failed publish attempts | Counter |

#### Timers

| Metric | Description | Percentiles |
|--------|-------------|-------------|
| `eventbus.publish.latency` | Publish operation latency | p50, p95, p99 |

#### Gauges

| Metric | Description | Range |
|--------|-------------|-------|
| `eventbus.subscribers.active` | Current active subscribers | 0+ |
| `eventbus.queue.depth` | Ring buffer utilization | 0-100% |

### Setup

#### Using SimpleMeterRegistry (Development)

```java
// Automatic - metrics are collected but not exported
AeronEventBus eventBus = new AeronEventBus();
eventBus.start();
```

#### Using Prometheus (Production)

Add dependency:
```kotlin
dependencies {
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
}
```

Configure EventBus:
```java
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

// Create Prometheus registry
PrometheusMeterRegistry prometheusRegistry = 
    new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

// Create EventBus with metrics
AeronEventBus eventBus = new AeronEventBus(
    prometheusRegistry, 
    "production-eventbus"
);
eventBus.start();

// Expose metrics endpoint (using your web framework)
// Spring Boot example:
// @GetMapping("/metrics")
// public String metrics() {
//     return prometheusRegistry.scrape();
// }
```

### Accessing Metrics Programmatically

```java
EventBusMetrics metrics = eventBus.getMetrics();

// Get counter values
long totalPublished = metrics.getPublishedEventCount();
long backpressureEvents = metrics.getBackpressureEventCount();
long droppedMessages = metrics.getDroppedMessageCount();

// Get latency statistics
double p99LatencyNanos = metrics.getP99LatencyNanos();
double p99LatencyMicros = p99LatencyNanos / 1000.0;

// Get current state
long activeSubscribers = metrics.getActiveSubscribers();
long queueDepth = metrics.getQueueDepth(); // 0-100%

System.out.printf("Published: %d, Backpressure: %d, Dropped: %d%n",
    totalPublished, backpressureEvents, droppedMessages);
System.out.printf("P99 Latency: %.2f µs%n", p99LatencyMicros);
System.out.printf("Queue Depth: %d%%, Subscribers: %d%n",
    queueDepth, activeSubscribers);
```

## Backpressure Control

### What is Backpressure?

Backpressure occurs when consumers cannot keep up with the rate of incoming events. Without backpressure control, this leads to:
- Memory exhaustion
- Queue overflow
- System crashes
- Message loss

The EventBus backpressure system prevents these issues by:
1. **Detecting** when the ring buffer fills up
2. **Signaling** producers to slow down
3. **Maintaining** system stability
4. **Recording** metrics for observability

### Ring Buffer Watermarks

The backpressure system uses two watermarks:

```
Ring Buffer Utilization:
0%                    50%                    80%                   100%
|---------------------|----------------------|----------------------|
      Normal              Low Watermark         High Watermark      Full
      Operation           (Deactivate BP)       (Activate BP)
```

#### Default Configuration

- **High Watermark**: 80% - Activates backpressure
- **Low Watermark**: 50% - Deactivates backpressure
- **Ring Buffer Size**: 1MB (TERM_BUFFER_LENGTH)

#### How It Works

1. **Producer publishes** → Buffer fills up
2. **80% reached** → Backpressure activates, `publish()` returns `false`
3. **Consumer processes** → Buffer empties
4. **50% reached** → Backpressure deactivates, normal operation resumes

This creates **hysteresis** - preventing rapid on/off switching (flapping).

### Monitoring Backpressure

```java
BackpressureMonitor monitor = eventBus.getBackpressureMonitor();

// Check if backpressure is active
if (monitor.isBackpressureActive()) {
    System.out.println("⚠️  Backpressure active - slow down!");
}

// Get detailed stats
long pending = monitor.getPendingMessages();
int utilization = monitor.getUtilizationPercent();
int highWatermark = monitor.getHighWatermark();
int lowWatermark = monitor.getLowWatermark();

System.out.printf("Pending: %d, Utilization: %d%%, HW: %d, LW: %d%n",
    pending, utilization, highWatermark, lowWatermark);
```

### Handling Backpressure in Code

#### Strategy 1: Retry with Backoff

Best for critical events that must be delivered.

```java
Event event = Event.create(...);

boolean published = false;
int retries = 0;
int maxRetries = 10;
long backoffNanos = 100; // Start with 100ns

while (!published && retries < maxRetries) {
    if (eventBus.publish(event)) {
        published = true;
    } else {
        retries++;
        // Exponential backoff
        LockSupport.parkNanos(backoffNanos);
        backoffNanos = Math.min(backoffNanos * 2, 1_000_000); // Max 1ms
    }
}

if (!published) {
    logger.error("Failed to publish after {} retries: {}", maxRetries, event);
}
```

#### Strategy 2: Drop Non-Critical Events

Best for high-frequency, non-critical updates (e.g., market data ticks).

```java
if (!eventBus.publish(event)) {
    logger.debug("Dropped market data update due to backpressure");
    metrics.recordDroppedMarketDataUpdate();
}
```

#### Strategy 3: Apply Flow Control Upstream

Best when you control the event source.

```java
RateLimiter rateLimiter = RateLimiter.create(2_000_000); // 2M/sec

while (hasMoreEvents()) {
    rateLimiter.acquire();
    
    Event event = nextEvent();
    if (!eventBus.publish(event)) {
        // Backpressure detected - reduce rate
        rateLimiter.setRate(rateLimiter.getRate() * 0.8);
        logger.warn("Reducing rate due to backpressure");
    }
}
```

#### Strategy 4: Batch and Compress

Best for bursty workloads.

```java
List<Event> batch = new ArrayList<>();
int batchSize = 100;

for (int i = 0; i < eventCount; i++) {
    batch.add(createEvent(i));
    
    if (batch.size() >= batchSize) {
        // Publish batch with backpressure handling
        for (Event event : batch) {
            while (!eventBus.publish(event)) {
                Thread.onSpinWait();
            }
        }
        batch.clear();
        
        // Small pause between batches
        Thread.sleep(1);
    }
}
```

## Performance Targets

### Target Metrics (Under Normal Load)

- **Throughput**: ≥ 2M events/sec
- **P99 Latency**: < 10 µs
- **Backpressure Events**: 0/sec
- **Dropped Messages**: 0/sec
- **Queue Depth**: < 50%

### Target Metrics (Under 2× Load)

- **Throughput**: ≥ 2M events/sec (with backpressure)
- **P99 Latency**: < 20 µs
- **Backpressure Events**: < 100/sec
- **Dropped Messages**: 0/sec (with retry logic)
- **Queue Depth**: 50-80%

### Alerting Thresholds

```yaml
# Recommended Prometheus alerts
groups:
  - name: eventbus
    interval: 30s
    rules:
      - alert: HighBackpressure
        expr: rate(eventbus_backpressure_events_total[1m]) > 10
        for: 2m
        annotations:
          summary: "High backpressure detected"
          description: "EventBus backpressure rate is {{ $value }}/sec"
      
      - alert: MessageLoss
        expr: rate(eventbus_messages_dropped_total[5m]) > 1
        for: 1m
        annotations:
          summary: "Messages being dropped"
          description: "{{ $value }} messages/sec are being dropped"
      
      - alert: HighLatency
        expr: histogram_quantile(0.99, rate(eventbus_publish_latency_seconds_bucket[1m])) > 0.00005
        for: 5m
        annotations:
          summary: "P99 latency above 50µs"
          description: "P99 publish latency is {{ $value }}s"
      
      - alert: QueueDepthHigh
        expr: eventbus_queue_depth > 80
        for: 5m
        annotations:
          summary: "Queue depth above 80%"
          description: "Ring buffer utilization is {{ $value }}%"
```

## Grafana Dashboard

### Import

1. Copy `docs/grafana-eventbus-dashboard.json`
2. In Grafana: **Dashboards** → **Import** → Paste JSON
3. Select your Prometheus data source
4. Click **Import**

### Panels Included

1. **Event Throughput** - Events/sec published
2. **Publish Latency Distribution** - p50, p95, p99 latency
3. **Backpressure Events** - Rate of backpressure activations
4. **Dropped Messages** - Rate of message loss
5. **Queue Depth** - Ring buffer utilization %
6. **Active Subscribers** - Current subscriber count
7. **Failed Publishes** - Total failed publish attempts
8. **Total Events Published** - Cumulative event count
9. **P99 Latency Target** - Current p99 vs 10µs target
10. **Message Loss Rate** - Dropped messages/sec

### Customization

Edit the dashboard to:
- Adjust time ranges
- Add additional panels
- Configure alerts
- Modify thresholds
- Add annotations

## Testing

### Unit Tests

```bash
# Run backpressure tests
./gradlew :core:eventbus:test --tests BackpressureMonitorTest

# Run metrics tests
./gradlew :core:eventbus:test --tests EventBusMetricsTest
```

### Integration Tests

```bash
# Run integration tests with slow subscribers
./gradlew :core:eventbus:test --tests AeronEventBusBackpressureTest
```

### Load Testing

```bash
# Run JMH benchmarks
./gradlew :core:eventbus:jmh -Pargs=".*AeronBenchmark.*"
```

## Troubleshooting

### High Backpressure Events

**Symptoms**: `eventbus_backpressure_events_total` increasing rapidly

**Causes**:
- Slow subscriber processing
- Insufficient consumer threads
- Blocking operations in event handlers
- Network/IO delays

**Solutions**:
1. Profile slow handlers: `jstack` or async-profiler
2. Add more consumer threads
3. Move blocking operations off hot path
4. Increase ring buffer size (if memory allows)

### Message Loss

**Symptoms**: `eventbus_messages_dropped_total` > 0

**Causes**:
- No retry logic on publish failure
- Sustained load above capacity
- Consumer completely stuck

**Solutions**:
1. Implement retry with backoff
2. Add circuit breaker for failing consumers
3. Scale horizontally (more instances)
4. Prioritize critical events

### High Latency

**Symptoms**: P99 latency > 50µs

**Causes**:
- GC pressure
- CPU contention
- Lock contention
- System load

**Solutions**:
1. Tune GC: Use ZGC or Shenandoah
2. Pin threads to CPU cores
3. Reduce system load
4. Check for lock contention with JProfiler

### Queue Depth Stuck High

**Symptoms**: `eventbus_queue_depth` stays > 80%

**Causes**:
- Consumer stopped processing
- Consumer much slower than producer
- Deadlock in handler

**Solutions**:
1. Check consumer thread status
2. Add timeout to handlers
3. Add circuit breaker
4. Scale consumers

## Best Practices

### 1. Always Check Publish Result

```java
// ✅ Good
if (!eventBus.publish(event)) {
    handleBackpressure(event);
}

// ❌ Bad
eventBus.publish(event); // Ignores backpressure!
```

### 2. Implement Retry for Critical Events

```java
// ✅ Good
publishWithRetry(event, maxRetries);

// ❌ Bad
eventBus.publish(event); // May be dropped
```

### 3. Monitor Metrics Continuously

```java
// ✅ Good - Periodic health check
scheduler.scheduleAtFixedRate(() -> {
    long backpressure = metrics.getBackpressureEventCount();
    if (backpressure > threshold) {
        alert("High backpressure detected");
    }
}, 1, 1, TimeUnit.MINUTES);
```

### 4. Avoid Blocking in Handlers

```java
// ✅ Good
eventBus.subscribe(EventType.ORDER_FILLED, event -> {
    queue.offer(event); // Non-blocking
});

// ❌ Bad
eventBus.subscribe(EventType.ORDER_FILLED, event -> {
    database.save(event); // Blocking!
});
```

### 5. Use Separate EventBus for Different Priorities

```java
// ✅ Good
AeronEventBus criticalBus = new AeronEventBus(registry, "critical");
AeronEventBus normalBus = new AeronEventBus(registry, "normal");

// Critical events won't be affected by normal load
criticalBus.publish(criticalEvent);
normalBus.publish(normalEvent);
```

## See Also

- [EVENT_BUS_README.md](EVENT_BUS_README.md) - General EventBus documentation
- [AERON_README.md](../core/eventbus/AERON_README.md) - Aeron implementation details
- [AERON_CONFIG.md](../core/eventbus/AERON_CONFIG.md) - Configuration guide
- [Micrometer Documentation](https://micrometer.io/docs)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/)
