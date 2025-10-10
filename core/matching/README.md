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

## Deterministic Replay & Recovery

The matching engine supports deterministic replay and state recovery for disaster recovery scenarios:

### Snapshot-Based Recovery

Create and restore from snapshots for fast state recovery:

```java
// Take a snapshot of current state
MatchingEngine.MatchingEngineSnapshot snapshot = engine.createSnapshot();

// Later: recover from snapshot
MatchingEngine newEngine = new MatchingEngine(eventBus);
newEngine.start();
newEngine.restoreFromSnapshot(snapshot);
```

Snapshots include:
- All order books for all symbols
- Internal counters (execution ID, trade ID, sequence tracker)
- Complete order book state (all orders with price-time priority)

### Event Replay

Replay events from the event log for incremental recovery:

```java
// Record sequence before snapshot
long snapshotSequence = engine.getCurrentSequence();

// Later: replay events after snapshot
eventBus.replay(snapshotSequence + 1, currentSequence, event -> {
    // Events replayed in order
    eventBus.publish(event);
});
```

### Bit-for-Bit Determinism

All operations are deterministic - replaying the same sequence of events produces identical results:
- Same order matching decisions
- Same trade prices and quantities
- Same order book state
- Same internal counters

This is critical for:
- State verification after recovery
- Audit and compliance requirements
- Testing and debugging production issues

### Performance

Recovery performance benchmarks:
- **100K orders**: < 2s from snapshot
- **1M orders**: < 20s from snapshot (extrapolated)
- **Snapshot size**: ~100 bytes per order
- **Memory overhead**: Minimal (snapshots are created on-demand)

### Example: Full Recovery Flow

```java
// 1. Normal operation with periodic snapshots
MatchingEngine.MatchingEngineSnapshot snapshot = engine.createSnapshot();
long snapshotSequence = engine.getCurrentSequence();
persistSnapshot(snapshot, snapshotSequence); // Store to disk/database

// 2. System crash and restart
MatchingEngine.MatchingEngineSnapshot snapshot = loadSnapshot(); // Load from disk
long snapshotSequence = getSnapshotSequence();
long currentSequence = getLatestSequence();

// 3. Recovery
MatchingEngine newEngine = new MatchingEngine(eventBus);
newEngine.start();
newEngine.restoreFromSnapshot(snapshot); // Restore to snapshot point

// 4. Replay events after snapshot
eventBus.replay(snapshotSequence + 1, currentSequence, event -> {
    eventBus.publish(event);
});

// System is now fully recovered with identical state
```

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
- [x] Order book snapshots for faster replay (implemented)
- [ ] Persistent snapshot storage (disk/database)
- [ ] Incremental snapshots (delta compression)
- [ ] NUMA-aware memory layout
- [ ] Off-heap order book storage
- [ ] Lock-free matching algorithm
- [ ] Multi-threaded matching (parallel symbols)

## See Also

- [Event Bus Documentation](../eventbus/README.md)
- [Order Book Documentation](../orderbook/README.md)
- [Architecture Overview](../../docs/ARCHITECTURE.md)
