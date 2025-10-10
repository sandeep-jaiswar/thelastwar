# Event Bus Abstraction Layer

Core Event Bus abstraction for The Last War trading system, providing ultra-low latency decoupling between subsystems.

## Overview

The Event Bus provides a GC-neutral publish/subscribe mechanism with sub-10 microsecond round-trip latency for inter-subsystem communication. It decouples:

- **Feed Handler** - Market data processing
- **Matching Engine** - Order matching and execution
- **Risk Manager** - Risk checks and position management
- **OMS** - Order Management System
- **Analytics** - Performance metrics and analytics

### Architecture Alignment

This implementation follows the core architecture principles:
- **Java 25 Target**: Using Java 17+ Records (targeting Java 25 as per architecture)
- **GC-Neutral Design**: Object pooling, primitive types, zero allocation in hot path
- **Mechanical Sympathy**: Lock-free structures, CPU affinity support
- **Deterministic Replay**: Event log as system-of-record, sequence-based ordering
- **Performance Targets**: < 10 µs round-trip, > 2M msgs/sec throughput
- **Gradle Build**: Gradle 8.8+ with Kotlin DSL as specified in architecture

## Architecture

### Core Components

1. **EventBus Interface** - Main abstraction for publish/subscribe operations
2. **Event Record** - Immutable event with metadata (timestamp, sequence, sourceId, eventType, header)
3. **EventHandler** - Functional interface for event consumers
4. **EventType** - Integer constants for event types (avoids autoboxing)
5. **SourceId** - Integer constants for subsystem identification
6. **EventPool** - Object pool for zero-allocation event creation
7. **EventBusMetrics** - Micrometer-based metrics collection (NEW)
8. **BackpressureMonitor** - Ring buffer watermark-based backpressure detection (NEW)

### Design Principles

- **GC-Neutral**: No autoboxing, minimal allocations in hot path, object pooling
- **Ultra-Low Latency**: Target < 10 microseconds round-trip (per architecture)
- **Type Safety**: Compile-time type checking with generics
- **Extensibility**: Clean abstraction allows multiple implementations
- **Java Records**: Compact, efficient event representation (Java 17+)
- **Deterministic Replay**: Sequence-based ordering for state recovery
- **Mechanical Sympathy**: Support for lock-free algorithms, CPU affinity

## Usage

### Basic Publishing and Subscribing

```java
// Create an event bus (use appropriate implementation)
EventBus eventBus = new InMemoryEventBus(); // or AeronEventBus, ChronicleEventBus
eventBus.start();

// Subscribe to events
EventBus.Subscription subscription = eventBus.subscribe(
    EventType.MARKET_DATA_UPDATE,
    event -> {
        System.out.println("Received market data: " + event.getPayload());
    }
);

// Publish an event
Event event = Event.create(
    System.nanoTime(),           // timestamp
    sequenceNumber++,            // sequence
    SourceId.FEED_HANDLER,      // sourceId
    EventType.MARKET_DATA_UPDATE, // eventType
    0L,                          // header (optional metadata)
    marketData                   // payload
);

boolean published = eventBus.publish(event);

// Later: unsubscribe
subscription.unsubscribe();

// Shutdown
eventBus.stop();
```

### Error Handling

```java
EventHandler<OrderData> handler = new EventHandler<>() {
    @Override
    public void onEvent(Event event) {
        OrderData order = (OrderData) event.getPayload();
        processOrder(order);
    }

    @Override
    public void onError(Event event, Throwable exception) {
        logger.error("Failed to process event: " + event, exception);
        // Handle error gracefully
    }
};

eventBus.subscribe(EventType.ORDER_FILLED, handler);
```

### Object Pooling for Zero Allocation

Use `EventPool` to eliminate heap allocations in hot path:

```java
// Create a pool (typically done at startup)
EventPool pool = new EventPool(1000); // 1000 pre-allocated events

// In hot path - zero allocation after warmup
EventPool.MutableEvent mutableEvent = pool.acquire();
mutableEvent.set(
    System.nanoTime(),
    sequence++,
    SourceId.MATCHING_ENGINE,
    EventType.ORDER_FILLED,
    0L,
    orderData
);

// Publish (convert to immutable Event)
eventBus.publish(mutableEvent.toEvent());

// Return to pool for reuse
pool.release(mutableEvent);
```

This pattern achieves **zero allocation in the hot path** after pool warmup, critical for microsecond-level latency.

### Event Types

Event types are organized by subsystem:

- **1000-1999**: Feed Handler (market data, connection status)
- **2000-2999**: Matching Engine (order lifecycle)
- **3000-3999**: Risk Management (risk checks, position updates)
- **4000-4999**: OMS (order submission, modifications)
- **5000-5999**: Analytics (metrics, latency samples)
- **9000-9999**: System (startup, shutdown, heartbeat)

Example event types:
```java
EventType.MARKET_DATA_UPDATE      // 1000
EventType.ORDER_FILLED             // 2002
EventType.RISK_CHECK_FAILED        // 3001
EventType.ORDER_SUBMITTED          // 4000
EventType.LATENCY_SAMPLE           // 5001
```

## Building and Testing

### Build the project

```bash
./gradlew build
```

### Run unit tests

```bash
./gradlew test
```

### Run microbenchmarks

```bash
./gradlew jmh
```

Or run specific benchmark:
```bash
./gradlew jmh --args="EventBusBenchmark.benchmarkPublish"
```

## Performance Characteristics

### Target Metrics

- **Publish Latency**: < 10 microseconds round-trip (99th percentile)
- **Subscribe Latency**: O(1) operation
- **GC Pressure**: Zero allocation in hot path with object pooling
- **Throughput**: > 2 million events per second
- **Deterministic Replay**: Bit-for-bit state recovery via sequence numbers

### Benchmark Results

Run the benchmark to verify performance on your hardware:

```bash
mvn clean test-compile
java -cp "target/test-classes:target/classes:$HOME/.m2/repository/org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar" \
     org.openjdk.jmh.Main EventBusBenchmark
```

Expected results (will vary by hardware):
- `benchmarkPublish`: < 10000 ns (< 10 µs) target
- `benchmarkPublishAndReceive`: < 10000 ns
- `benchmarkEventCreation`: ~50 ns
- `benchmarkSubscribe`: ~500 ns

## Implementation Technologies

See [ADR-001](docs/adr/001-event-bus-technology-selection.md) for detailed comparison of:

- **Aeron** - Recommended for ultra-low latency (200-500ns)
- **Chronicle Queue** - Recommended for persistence requirements
- **Kafka** - Not suitable for sub-5µs latency requirements

The current `InMemoryEventBus` is a simple implementation for testing. Production systems should use:
- **Aeron** for hot-path trading events
- **Chronicle Queue** for audit logs and replay

## UML Diagrams

See [EventBus Message Flow](docs/uml/eventbus-message-flow.puml) for detailed sequence diagrams.

To generate PNG from PlantUML:
```bash
# Install PlantUML
brew install plantuml  # macOS
apt-get install plantuml  # Ubuntu

# Generate diagram
plantuml docs/uml/eventbus-message-flow.puml
```

## Project Structure

```
thelastwar/
├── core/
│   └── eventbus/
│       ├── build.gradle.kts
│       └── src/
│           ├── main/java/com/thelastwar/eventbus/
│           │   ├── EventBus.java          # Main interface
│           │   ├── Event.java             # Event record
│           │   ├── EventType.java         # Type constants
│           │   ├── EventHandler.java      # Handler interface
│           │   ├── EventPool.java         # Object pooling
│           │   └── SourceId.java          # Source identifiers
│           └── test/java/com/thelastwar/eventbus/
│               ├── EventTest.java
│               ├── EventTypeTest.java
│               ├── EventHandlerTest.java
│               ├── EventBusTest.java
│               ├── EventPoolTest.java
│               ├── SourceIdTest.java
│               ├── InMemoryEventBus.java  # Test implementation
│               ├── benchmark/
│               │   └── EventBusBenchmark.java
│               └── example/
│                   ├── EventBusExample.java
│                   └── EventPoolExample.java
├── docs/
│   ├── EVENT_BUS_README.md
│   ├── uml/
│   │   └── eventbus-message-flow.puml
│   └── adr/
│       └── 001-event-bus-technology-selection.md
├── build.gradle.kts          # Root build file
├── settings.gradle.kts       # Project settings
└── gradlew                   # Gradle wrapper
```

## Design Goals

### GC-Neutral Design

1. **Primitive Types**: Use `int` for event types and source IDs (no `Integer` objects)
2. **Pre-allocated Structures**: Handler arrays pre-allocated to avoid resizing
3. **Object Pooling**: Event objects can be pooled (implementation-specific)
4. **Immutable Events**: Events are immutable to enable safe sharing

### Ultra-Low Latency

1. **O(1) Dispatch**: Array-indexed handler lookup by event type
2. **Lock-Free**: CopyOnWriteArrayList for concurrent subscribe/unsubscribe
3. **No Allocation**: Publish path allocates no objects
4. **Fast Path Optimization**: Common operations optimized for minimal cycles

### Thread Safety

- **Concurrent Publishing**: Multiple threads can publish simultaneously
- **Safe Subscribe/Unsubscribe**: Can modify subscriptions while publishing
- **Memory Visibility**: Volatile fields and atomic counters ensure visibility

## Performance and Tuning

For comprehensive information on performance optimization and benchmarking:

- **[Performance Tuning Guide](PERFORMANCE_TUNING.md)**: System-level and JVM tuning for ultra-low latency
- **[Benchmark Guide](BENCHMARK_GUIDE.md)**: Running and interpreting performance benchmarks
- **[Aeron README](../core/eventbus/AERON_README.md)**: Aeron-specific implementation details
- **[Aeron Configuration](../core/eventbus/AERON_CONFIG.md)**: Advanced Aeron configuration

## Future Enhancements

1. **Priority Queues**: Support event prioritization
2. **Batching**: Batch multiple events for higher throughput
3. **Replay**: Chronicle Queue integration for event replay

## Metrics & Observability

The Event Bus now includes comprehensive metrics via Micrometer for monitoring throughput, latency, and system health.

### Available Metrics

All metrics are exported to Prometheus and can be visualized in Grafana:

1. **eventbus.events.published** - Counter of successfully published events
2. **eventbus.publish.latency** - Timer tracking publish latency distribution (p50, p95, p99)
3. **eventbus.backpressure.events** - Counter of backpressure events detected
4. **eventbus.messages.dropped** - Counter of messages dropped due to backpressure
5. **eventbus.publishes.failed** - Counter of failed publish attempts
6. **eventbus.subscribers.active** - Gauge of active subscribers
7. **eventbus.queue.depth** - Gauge of ring buffer utilization (0-100%)

### Enabling Metrics

```java
// Use default SimpleMeterRegistry
AeronEventBus eventBus = new AeronEventBus();

// Or provide a custom registry (e.g., Prometheus)
MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
AeronEventBus eventBus = new AeronEventBus(registry, "my-eventbus");
eventBus.start();

// Access metrics programmatically
EventBusMetrics metrics = eventBus.getMetrics();
long publishedCount = metrics.getPublishedEventCount();
long backpressureEvents = metrics.getBackpressureEventCount();
double p99Latency = metrics.getP99LatencyNanos();
```

### Grafana Dashboard

A pre-configured Grafana dashboard is available at `docs/grafana-eventbus-dashboard.json` with the following panels:

- Event throughput (events/sec)
- Publish latency distribution (p50, p95, p99)
- Backpressure events rate
- Dropped messages rate
- Queue depth utilization
- Active subscribers count

Import the dashboard into Grafana and configure it to use your Prometheus data source.

## Backpressure Control

The Event Bus includes a sophisticated backpressure mechanism to handle load spikes and slow consumers gracefully.

### How It Works

Backpressure uses **ring buffer watermarks** to detect when consumers cannot keep up with producers:

- **High Watermark** (default 80%): Activates backpressure when ring buffer is 80% full
- **Low Watermark** (default 50%): Deactivates backpressure when buffer drops to 50% full

When backpressure is active:
1. `publish()` returns `false` instead of blocking
2. Backpressure events are recorded in metrics
3. Producers can implement retry logic or drop messages
4. System remains stable under sustained load

### Configuration

```java
// Access the backpressure monitor
BackpressureMonitor monitor = eventBus.getBackpressureMonitor();

// Check if backpressure is active
boolean isActive = monitor.isBackpressureActive();

// Get current utilization
int utilization = monitor.getUtilizationPercent(); // 0-100

// Get pending messages
long pending = monitor.getPendingMessages();

// Get watermark thresholds
int highWatermark = monitor.getHighWatermark();
int lowWatermark = monitor.getLowWatermark();
```

### Handling Backpressure

```java
Event event = Event.create(...);

// Option 1: Retry with backoff
boolean published = false;
int retries = 0;
while (!published && retries < 3) {
    if (eventBus.publish(event)) {
        published = true;
    } else {
        retries++;
        Thread.onSpinWait(); // Brief pause
    }
}

// Option 2: Drop message if not critical
if (!eventBus.publish(event)) {
    logger.warn("Dropped event due to backpressure: {}", event);
}

// Option 3: Apply flow control upstream
if (!eventBus.publish(event)) {
    // Signal upstream producer to slow down
    rateLimiter.backoff();
}
```

### Performance Under Load

The backpressure system is designed to:
- **Stabilize without message loss** under 2× expected load (4M events/sec)
- **Signal backpressure** via metrics before dropping messages
- **Resume normal operation** when consumer catches up
- **Maintain low latency** for successfully published events

Testing has validated:
- ✅ System remains stable under sustained 2× load
- ✅ Backpressure events are visible in metrics
- ✅ No message loss with proper retry logic
- ✅ p99 latency stays below 10µs for published events

## License

Copyright © 2024 The Last War. All rights reserved.

## Contributing

1. Follow the existing code style
2. Add unit tests for new features
3. Run benchmarks to validate performance
4. Update documentation as needed
