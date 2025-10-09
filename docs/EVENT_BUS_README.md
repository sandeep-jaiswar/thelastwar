# Event Bus Abstraction Layer

Core Event Bus abstraction for The Last War trading system, providing ultra-low latency decoupling between subsystems.

## Overview

The Event Bus provides a GC-neutral publish/subscribe mechanism with sub-5 microsecond latency for inter-subsystem communication. It decouples:

- **Feed Handler** - Market data processing
- **Matching Engine** - Order matching and execution
- **Risk Manager** - Risk checks and position management
- **OMS** - Order Management System
- **Analytics** - Performance metrics and analytics

## Architecture

### Core Components

1. **EventBus Interface** - Main abstraction for publish/subscribe operations
2. **Event Model** - Immutable event with metadata (timestamp, sequence, sourceId, eventType, header)
3. **EventHandler** - Functional interface for event consumers
4. **EventType** - Integer constants for event types (avoids autoboxing)
5. **SourceId** - Integer constants for subsystem identification

### Design Principles

- **GC-Neutral**: No autoboxing, minimal allocations in hot path
- **Ultra-Low Latency**: Target < 5 microseconds publish/subscribe
- **Type Safety**: Compile-time type checking with generics
- **Extensibility**: Clean abstraction allows multiple implementations

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
mvn clean compile
```

### Run unit tests

```bash
mvn test
```

### Run microbenchmarks

```bash
mvn clean test-compile
mvn exec:java -Dexec.mainClass="org.openjdk.jmh.Main" \
              -Dexec.classpathScope=test \
              -Dexec.args="EventBusBenchmark"
```

Or run specific benchmark:
```bash
mvn exec:java -Dexec.mainClass="org.openjdk.jmh.Main" \
              -Dexec.classpathScope=test \
              -Dexec.args="EventBusBenchmark.benchmarkPublish"
```

## Performance Characteristics

### Target Metrics

- **Publish Latency**: < 5 microseconds (99th percentile)
- **Subscribe Latency**: O(1) operation
- **GC Pressure**: Zero allocation in hot path
- **Throughput**: Millions of events per second

### Benchmark Results

Run the benchmark to verify performance on your hardware:

```bash
mvn clean test-compile
java -cp "target/test-classes:target/classes:$HOME/.m2/repository/org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar" \
     org.openjdk.jmh.Main EventBusBenchmark
```

Expected results (will vary by hardware):
- `benchmarkPublish`: < 5000 ns (< 5 µs)
- `benchmarkPublishAndReceive`: < 5000 ns
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
src/
├── main/java/com/thelastwar/eventbus/
│   ├── EventBus.java          # Main interface
│   ├── Event.java             # Event model
│   ├── EventType.java         # Event type constants
│   ├── EventHandler.java      # Handler interface
│   └── SourceId.java          # Source identifiers
└── test/java/com/thelastwar/eventbus/
    ├── EventTest.java         # Event unit tests
    ├── EventTypeTest.java     # EventType unit tests
    ├── EventHandlerTest.java  # Handler unit tests
    ├── EventBusTest.java      # EventBus unit tests
    ├── SourceIdTest.java      # SourceId unit tests
    ├── InMemoryEventBus.java  # Test implementation
    └── benchmark/
        └── EventBusBenchmark.java  # JMH benchmarks

docs/
├── uml/
│   └── eventbus-message-flow.puml  # UML sequence diagram
└── adr/
    └── 001-event-bus-technology-selection.md  # Architecture decision record
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

## Future Enhancements

1. **Priority Queues**: Support event prioritization
2. **Batching**: Batch multiple events for higher throughput
3. **Backpressure**: Handle slow consumers gracefully
4. **Monitoring**: JMX metrics and health checks
5. **Replay**: Chronicle Queue integration for event replay

## License

Copyright © 2024 The Last War. All rights reserved.

## Contributing

1. Follow the existing code style
2. Add unit tests for new features
3. Run benchmarks to validate performance
4. Update documentation as needed
