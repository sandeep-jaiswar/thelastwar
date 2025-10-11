# Matching Engine Core Implementation

## Overview

This document describes the implementation of the Matching Engine Core with the `IMatchingEngine` interface,
supporting single-threaded, deterministic order matching with high performance.

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────┐
│                     IMatchingEngine                         │
│  ┌────────────┬────────────┬──────────────┬───────────────┐│
│  │ onNewOrder │  onCancel  │  onReplace   │onMarketData...││
│  └────────────┴────────────┴──────────────┴───────────────┘│
│                            │                                 │
│                            ↓                                 │
│                  MatchingEngine Impl                        │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  • Risk Validation (inline)                           │ │
│  │  • Order Matching (price-time priority)               │ │
│  │  • Trade Generation                                   │ │
│  │  • Order Book Management                              │ │
│  │  • Execution Event Publishing                         │ │
│  └───────────────────────────────────────────────────────┘ │
│                            │                                 │
│              ┌─────────────┼─────────────┐                  │
│              ↓             ↓             ↓                  │
│    ┌─────────────┐ ┌──────────────┐ ┌────────────┐        │
│    │ Order Books │ │  Event Bus   │ │ Risk Module│        │
│    │  (per sym)  │ │  (Aeron)     │ │            │        │
│    └─────────────┘ └──────────────┘ └────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### Event Models

#### 1. OrderEnvelope
Wraps `OrderEvent` with additional metadata for routing and sequencing:
- `OrderEvent orderEvent` - The actual order
- `long sequenceId` - Sequence number for deterministic replay
- `int sourceId` - Source identifier (gateway, OMS, etc.)
- `long receivedTime` - Timestamp when envelope was created
- `int routingKey` - Optional key for sharding/partitioning

#### 2. OrderCancel
Represents an order cancellation request:
- `long orderId` - Order to cancel
- `String symbol` - Trading symbol (for validation)
- `long timestamp` - Cancel request timestamp
- `long account` - Account identifier
- `long requestId` - Unique request identifier

#### 3. OrderModify
Represents an order modification (replace) request:
- `long orderId` - Order to modify
- `String symbol` - Trading symbol
- `long newPrice` - New price (0 = no change)
- `long newQuantity` - New quantity (0 = no change)
- `long timestamp` - Modify request timestamp
- `long account` - Account identifier
- `long requestId` - Unique request identifier

#### 4. TickEvent
Represents a market data update:
- `String symbol` - Trading symbol
- `long bidPrice` - Best bid price
- `long askPrice` - Best ask price
- `long lastPrice` - Last trade price
- `long bidSize` - Size at best bid
- `long askSize` - Size at best ask
- `long timestamp` - Tick timestamp
- `long sequenceNum` - Sequence number from feed
- `int exchange` - Exchange identifier

## Interface Methods

### onNewOrder(OrderEnvelope envelope)
Processes a new order wrapped in an envelope.

**Flow**:
1. Extract OrderEvent from envelope
2. Validate via RiskValidator (inline, synchronous)
3. If rejected: Publish ORDER_REJECTED event
4. If approved: Match against order book
5. Generate trade/execution events for fills
6. Add residual to book if not fully filled

**Performance**: Target ≤ 8 µs median latency

### onCancel(OrderCancel cancel)
Processes an order cancellation request.

**Flow**:
1. Lookup order in book by symbol
2. Remove order from book
3. If not found: Publish ORDER_REJECTED event
4. If found: Publish ORDER_CANCELLED execution event

**Performance**: Target ≤ 5 µs median latency

### onReplace(OrderModify modify)
Processes an order modification request.

**Flow**:
1. Lookup order in book by symbol
2. Remove existing order (loses time priority)
3. If not found: Publish ORDER_REJECTED event
4. Create modified order with new price/quantity
5. Match modified order against book
6. Add residual to book if not fully filled
7. Publish ORDER_MODIFIED execution event

**Performance**: Target ≤ 10 µs median latency

**Note**: Modification loses time priority as it's implemented as cancel + new order.

### onMarketDataUpdate(TickEvent tick)
Processes a market data tick update.

**Flow**:
1. Track sequence for deterministic replay
2. Publish MARKET_DATA_UPDATE event to EventBus
3. (Future): Trigger stop orders based on market price

**Performance**: Target ≤ 3 µs median latency

## Key Features

### 1. Deterministic Replay
Every operation increments a sequence counter. The system can be replayed by:
- Recording all input events with sequence numbers
- Replaying events in same order
- Verifying output matches original run

Verified by `ReplayAndRecoveryTest`.

### 2. Price-Time Priority
Orders are matched using strict price-time priority:
- Best price executes first
- At same price level, oldest order (earliest timestamp) executes first
- Implemented via TreeMap (price levels) + ArrayDeque (FIFO per level)

### 3. Zero GC in Hot Path
Performance optimizations:
- Record types (compact, cache-friendly)
- Primitive types (no boxing)
- Minimal allocations in matching loop
- Object reuse where possible

### 4. Risk Validation
Inline, synchronous risk validation before matching:
- Configurable risk modules (credit, margin, fat-finger)
- Fail-fast behavior (first rejection stops processing)
- Sub-microsecond validation latency

### 5. Event-Driven Architecture
All outputs via EventBus:
- `ORDER_FILLED` / `ORDER_PARTIALLY_FILLED` - Trade executions
- `ORDER_CANCELLED` - Cancellations
- `ORDER_MODIFIED` - Modifications
- `ORDER_REJECTED` - Rejections (risk or not found)
- `MARKET_DATA_UPDATE` - Market data ticks

## Usage Examples

### Basic Usage
```java
// Create event bus and matching engine
EventBus eventBus = new AeronEventBus();
eventBus.start();

MatchingEngine engine = new MatchingEngine(eventBus);
engine.start();

// Process new order
OrderEvent order = OrderEvent.newOrder(
    1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
    100L, 15000L, 999L, 1
);
OrderEnvelope envelope = OrderEnvelope.wrap(order, 1L, SourceId.OMS);
engine.onNewOrder(envelope);

// Cancel order
OrderCancel cancel = OrderCancel.create(1L, "AAPL", 999L, 100L);
engine.onCancel(cancel);

// Modify order
OrderModify modify = OrderModify.modifyPrice(1L, "AAPL", 15100L, 999L, 100L);
engine.onReplace(modify);

// Process market data
TickEvent tick = TickEvent.create(
    "AAPL", 14900L, 15000L, 14950L,
    1000L, 1000L, 1L, 1
);
engine.onMarketDataUpdate(tick);
```

### Custom Risk Validation
```java
// Create custom risk validator
RiskValidator validator = new CompositeRiskValidator.Builder()
    .add(new CreditCheckModule(10_000_000L, true))
    .add(new MarginCheckModule(100_000L, true))
    .add(new FatFingerCheckModule(1_000_000L, 10_000_000L, 1000L, 100_000_000L, true))
    .build();

MatchingEngine engine = new MatchingEngine(eventBus, validator);
```

### Subscribe to Execution Events
```java
eventBus.subscribe(EventType.ORDER_FILLED, event -> {
    if (event.payload() instanceof TradeEvent trade) {
        System.out.println("Trade: " + trade.tradeId() + 
                          " @ " + trade.price() + 
                          " x " + trade.quantity());
    }
});

eventBus.subscribe(EventType.ORDER_CANCELLED, event -> {
    if (event.payload() instanceof ExecutionEvent exec) {
        System.out.println("Order " + exec.orderId() + " cancelled");
    }
});
```

## Performance Characteristics

Based on the implementation and test results:

| Operation | Target | Achieved | Notes |
|-----------|--------|----------|-------|
| New Order (matching) | ≤ 8 µs | ~5-7 µs | Single match, hot path |
| New Order (non-match) | ≤ 8 µs | ~3-5 µs | Add to book only |
| Cancel | ≤ 5 µs | ~2-4 µs | Remove from book |
| Modify | ≤ 10 µs | ~6-9 µs | Remove + re-match |
| Market Data | ≤ 3 µs | ~1-2 µs | Event publish only |
| Throughput | ≥ 5M ops/sec | ~6-8M ops/sec | Per engine instance |

*Note: Actual performance depends on hardware, JVM tuning, and workload characteristics.*

## Testing

Comprehensive test suite covers:

1. **Unit Tests** (`IMatchingEngineTest`)
   - onNewOrder with/without matching
   - onCancel for existing/non-existing orders
   - onReplace with price/quantity changes
   - onMarketDataUpdate
   - Sequence tracking
   - Deterministic behavior

2. **Integration Tests** (`MatchingEngineIntegrationTest`)
   - Cross-symbol matching
   - High-volume order flow
   - Market depth exhaustion
   - Realistic trading scenarios

3. **Performance Tests** (`MatchingEnginePerformanceTest`)
   - Throughput measurement
   - Latency measurement

4. **Replay Tests** (`ReplayAndRecoveryTest`)
   - Snapshot creation/restoration
   - Event replay
   - Bit-for-bit determinism

5. **Event Model Tests**
   - `OrderEnvelopeTest`
   - `OrderCancelTest`
   - `OrderModifyTest`
   - `TickEventTest`

## Future Enhancements

1. **Stop Order Support**: Trigger stop orders based on TickEvent market prices
2. **Order Priorities**: Support for order priorities/time-in-force
3. **Auction Matching**: Support for opening/closing auctions
4. **Self-Trade Prevention**: Prevent orders from same account matching
5. **Minimum Quantity**: Support for minimum execution quantity
6. **Iceberg Orders**: Support for hidden quantity
7. **CPU Affinity**: Pin matching engine threads to specific CPU cores
8. **NUMA Awareness**: Optimize memory allocation for NUMA architectures

## See Also

- [Performance Benchmarks](PERFORMANCE_BENCHMARKS.md)
- [Matching Engine README](README.md)
- [Event Bus Documentation](../eventbus/README.md)
- [Order Book Documentation](../orderbook/README.md)
