# Order Event Model

This module defines canonical data structures for Order, Trade, and Execution events shared across components in the trading system.

## Overview

The Order Event Model provides immutable value objects (Java records) for representing trading events in a low-latency, deterministic manner. These data structures are designed for:

- **Ultra-low latency**: Binary serialization target < 200 ns (avg)
- **Zero allocation**: Using object pools and primitive types
- **Immutability**: All events are immutable records for thread safety
- **Deterministic replay**: Events can be reliably serialized and replayed

## Event Types

### OrderEvent
Represents an order lifecycle event (new, modified, cancelled).

```java
OrderEvent order = OrderEvent.newOrder(
    12345L,              // orderId
    "AAPL",              // symbol
    OrderEvent.SIDE_BUY, // buy side
    OrderEvent.TYPE_LIMIT, // limit order
    100L,                // quantity
    15000L,              // price (in cents)
    999L,                // account ID
    1                    // exchange ID
);
```

**Fields:**
- `orderId`: Unique order identifier
- `symbol`: Trading symbol/instrument (max 16 chars)
- `side`: Order side (1=Buy, 2=Sell)
- `orderType`: Order type (1=Market, 2=Limit, 3=Stop, 4=StopLimit)
- `quantity`: Order quantity (in lots or shares)
- `price`: Order price (in minimum price increments)
- `timestamp`: Event timestamp in nanoseconds
- `status`: Order status (0=New, 1=PartiallyFilled, 2=Filled, 3=Cancelled, 4=Rejected)
- `account`: Trading account identifier
- `exchange`: Exchange identifier

### TradeEvent
Represents a completed trade transaction.

```java
TradeEvent trade = TradeEvent.create(
    67890L,              // tradeId
    12345L,              // orderId
    "AAPL",              // symbol
    TradeEvent.SIDE_BUY, // buy side
    100L,                // quantity executed
    15050L,              // execution price
    999L,                // account ID
    1,                   // exchange ID
    888L,                // counterparty ID
    50L                  // fees (in cents)
);
```

**Fields:**
- `tradeId`: Unique trade identifier
- `orderId`: Associated order identifier
- `symbol`: Trading symbol/instrument
- `side`: Trade side (1=Buy, 2=Sell)
- `quantity`: Trade quantity (executed)
- `price`: Execution price
- `timestamp`: Trade timestamp in nanoseconds
- `account`: Trading account identifier
- `exchange`: Exchange identifier
- `counterparty`: Counterparty identifier (0 if N/A)
- `fees`: Trade fees/commissions

### ExecutionEvent
Represents an execution report from the matching engine.

```java
ExecutionEvent exec = ExecutionEvent.fill(
    11111L,    // executionId
    order,     // source order
    50L,       // filled quantity
    15050L,    // fill price
    50L,       // cumulative quantity
    50L        // leaves quantity
);
```

**Fields:**
- `executionId`: Unique execution identifier
- `orderId`: Associated order identifier
- `symbol`: Trading symbol/instrument
- `side`: Execution side (1=Buy, 2=Sell)
- `executionType`: Type (0=New, 1=PartialFill, 2=Fill, 3=Cancelled, 4=Rejected, 5=Replaced)
- `orderStatus`: Current order status after this execution
- `lastQuantity`: Quantity executed in this event
- `lastPrice`: Price of this execution
- `cumulativeQty`: Total quantity filled so far
- `leavesQuantity`: Remaining unfilled quantity
- `timestamp`: Execution timestamp in nanoseconds
- `account`: Trading account identifier
- `exchange`: Exchange identifier
- `rejectReason`: Rejection reason code (0 if not rejected)

## Serialization

The `EventSerializer` class provides high-performance binary serialization:

```java
// Serialize
ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getOrderEventSize());
EventSerializer.serializeOrder(order, buffer);

// Deserialize
buffer.flip();
OrderEvent deserialized = EventSerializer.deserializeOrder(buffer);
```

**Performance Characteristics:**
- **OrderEvent**: 63 bytes, serialization target < 200 ns
- **TradeEvent**: 85 bytes, serialization target < 200 ns
- **ExecutionEvent**: 91 bytes, serialization target < 200 ns
- Zero-copy where possible
- Fixed-size buffers for predictable performance
- No object allocation in hot path

## Schema Registry

Avro schemas are provided for Schema Registry registration:

- `src/main/resources/avro/OrderEvent.avsc`
- `src/main/resources/avro/TradeEvent.avsc`
- `src/main/resources/avro/ExecutionEvent.avsc`

These schemas enable schema evolution and validation in distributed systems using Kafka or similar event platforms.

## Testing

### Unit Tests
Run comprehensive unit tests:
```bash
./gradlew :core:eventbus:test
```

### Benchmarks
Run JMH benchmarks to validate performance:
```bash
./gradlew :core:eventbus:jmh -Pargs="EventModelBenchmark"
```

### Examples
Run usage examples:
```bash
./gradlew :core:eventbus:compileTestJava
/usr/lib/jvm/temurin-25-jdk-amd64/bin/java -cp \
  "core/eventbus/build/classes/java/test:core/eventbus/build/classes/java/main" \
  com.thelastwar.eventbus.model.example.EventModelExample
```

## Design Principles

### Immutability
All event objects are immutable Java records. Updates create new instances:

```java
OrderEvent original = OrderEvent.newOrder(/*...*/);
OrderEvent updated = original.withStatus(OrderEvent.STATUS_FILLED);
// original remains unchanged
```

### Type Safety
Using Java records provides compile-time type safety:
- No null pointer exceptions from missing fields
- Automatic equals/hashCode/toString implementations
- Compact memory layout

### Performance
Optimized for ultra-low latency:
- Primitive types (no autoboxing)
- Fixed-size serialization
- Pre-allocated buffers
- Minimal branching

### Deterministic Replay
Events contain all information needed for replay:
- Nanosecond timestamps
- Complete order state
- Execution details
- Account/exchange identifiers

## Integration

### With Event Bus
```java
Event event = Event.create(
    System.nanoTime(),
    sequence,
    SourceId.MATCHING_ENGINE,
    EventType.ORDER_FILLED,
    0L,
    orderEvent  // OrderEvent as payload
);
eventBus.publish(event);
```

### With Chronicle Queue
```java
ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getOrderEventSize());
EventSerializer.serializeOrder(order, buffer);
appender.writeBytes(buffer);
```

### With Aeron
```java
ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getOrderEventSize());
EventSerializer.serializeOrder(order, buffer);
publication.offer(buffer);
```

## Future Enhancements

- [ ] Direct buffer support for off-heap serialization
- [ ] SBE (Simple Binary Encoding) codec generation
- [ ] FIX protocol adapters
- [ ] gRPC/Protobuf schemas
- [ ] Object pool integration for zero-allocation
- [ ] NUMA-aware memory layout

## References

- [Event Bus README](../../../docs/EVENT_BUS_README.md)
- [Performance Targets](../../../../README.md#performance-targets)
- [Architecture Decision Records](../../../../docs/adr/)
