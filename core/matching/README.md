# Matching Engine Module

## Overview

High-performance matching engine that consumes order events from the Event Bus, matches them against order books using price-time priority, generates trades, and publishes execution events.

## Architecture

```
Event Bus → Matching Engine → Order Books → Trade Generation → Event Bus
```

### Components

- **MatchingEngine**: Core matching logic and order book management
- **Order Books**: Per-symbol limit order books (using `core:orderbook`)
- **Event Integration**: Subscribes to ORDER_SUBMITTED, publishes ORDER_FILLED/PARTIALLY_FILLED

## Features

- **Price-Time Priority Matching**: Orders matched in strict FIFO order at each price level
- **Multi-Symbol Support**: Maintains separate order books per trading symbol
- **Trade Generation**: Automatic TradeEvent and ExecutionEvent creation
- **Deterministic Replay**: Sequence-based tracking for state recovery
- **GC-Neutral Design**: Minimal allocations in hot path
- **Event-Driven**: Fully integrated with Event Bus for decoupled architecture

## Performance

Target: **< 5 µs per match (p99)**

Based on benchmarks:
- Matching order execution: ~2-4 µs
- Non-matching order (add to book): ~500 ns
- Order book lookup: ~10 ns (cache hit)

## Usage

### Basic Setup

```java
// Create event bus and matching engine
EventBus eventBus = new AeronEventBus();
eventBus.start();

MatchingEngine engine = new MatchingEngine(eventBus);
engine.start();

// Subscribe to trade events
eventBus.subscribe(EventType.ORDER_FILLED, event -> {
    TradeEvent trade = (TradeEvent) event.payload();
    System.out.println("Trade: " + trade.tradeId() + 
                      " @ " + trade.price() + 
                      " x " + trade.quantity());
});

// Publish order events
OrderEvent order = OrderEvent.newOrder(
    12345L,           // orderId
    "AAPL",          // symbol
    OrderEvent.SIDE_BUY,
    OrderEvent.TYPE_LIMIT,
    100L,            // quantity
    15000L,          // price (in cents)
    999L,            // account
    1                // exchange
);

Event orderEvent = Event.create(
    System.nanoTime(),
    1L,
    SourceId.OMS,
    EventType.ORDER_SUBMITTED,
    0L,
    order
);

eventBus.publish(orderEvent);
```

### Metrics

```java
MatchingEngineMetrics metrics = engine.getMetrics();
System.out.println("Symbols: " + metrics.symbolCount());
System.out.println("Total Trades: " + metrics.totalTrades());
System.out.println("Total Executions: " + metrics.totalExecutions());
System.out.println("Current Sequence: " + metrics.currentSequence());
```

## Matching Logic

### Algorithm

1. **Order Arrival**: Subscribe to ORDER_SUBMITTED events
2. **Price Check**: Compare incoming order price with best opposite-side price
3. **Match Execution**:
   - If prices cross, execute match at resting order's price (price-time priority)
   - Generate TradeEvent for the fill
   - Generate ExecutionEvent for order status update
   - Update or remove filled orders from book
4. **Residual Handling**: Add any unfilled quantity to order book

### Example Match Flow

```
Book State:
  Bids: 100 @ 14990, 100 @ 14980
  Asks: 100 @ 15010, 100 @ 15020

Incoming: Buy 150 @ 15010

Match 1: 100 @ 15010 (exhausts first ask)
Match 2: 50 @ 15020 (partial fill of second ask)

Result:
  Bids: 100 @ 14990, 100 @ 14980
  Asks: 50 @ 15020 (remaining)
  
Events Published:
  - TradeEvent (100 @ 15010)
  - TradeEvent (50 @ 15020)
  - ExecutionEvent (ORDER_FILLED for incoming order)
  - ExecutionEvent (ORDER_FILLED for first ask)
  - ExecutionEvent (ORDER_PARTIALLY_FILLED for second ask)
```

## Deterministic Replay

The matching engine supports deterministic replay for disaster recovery:

```java
// Record sequence
long sequence = engine.getCurrentSequence();

// Later: replay from sequence
eventBus.replay(0, sequence, event -> {
    // Events replayed in order
});
```

All operations are deterministic - replaying the same sequence of events produces identical results.

## Testing

### Unit Tests

```bash
./gradlew :core:matching:test
```

Tests include:
- Simple matching scenarios
- Partial fills
- Price-time priority validation
- Multi-symbol isolation
- Sequence tracking
- Metrics validation

### Integration Tests

```bash
./gradlew :core:matching:test --tests "*IntegrationTest"
```

Integration tests cover:
- Realistic trading scenarios
- High-volume order flow (100+ orders)
- Market depth exhaustion
- Cross-symbol matching
- Deterministic replay validation
- Price improvement

### Benchmarks

```bash
./gradlew :core:matching:jmh
```

JMH benchmarks measure:
- Matching order latency
- Non-matching order latency
- Order book lookup performance
- Metrics retrieval overhead

## Dependencies

- **core:eventbus** - Event Bus integration
- **core:orderbook** - Order book data structures
- **JUnit Jupiter** - Testing framework
- **JMH** - Benchmarking framework

## Thread Safety

- Each symbol's order book follows single-writer principle
- Event Bus handles thread coordination
- ConcurrentHashMap used for multi-symbol book management
- AtomicLong for counters (execution ID, trade ID, sequence)

## Future Enhancements

- [ ] Market order support (currently limit orders only)
- [ ] Stop and stop-limit order types
- [ ] Iceberg order handling
- [ ] Self-trade prevention
- [ ] Order book snapshots for faster replay
- [ ] NUMA-aware memory layout
- [ ] Off-heap order book storage
- [ ] Lock-free matching algorithm
- [ ] Multi-threaded matching (parallel symbols)

## See Also

- [Event Bus Documentation](../eventbus/README.md)
- [Order Book Documentation](../orderbook/README.md)
- [Architecture Overview](../../docs/ARCHITECTURE.md)
