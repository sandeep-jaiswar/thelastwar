# Matching Engine Module

## Overview

High-performance matching engine that consumes order events from the Event Bus, matches them against order books using price-time priority, generates trades, and publishes execution events.

The engine implements the `IMatchingEngine` interface with support for:
- **Order Processing**: `onNewOrder(OrderEnvelope)` - Process new orders with matching
- **Order Cancellation**: `onCancel(OrderCancel)` - Cancel existing orders
- **Order Modification**: `onReplace(OrderModify)` - Modify order price/quantity
- **Market Data**: `onMarketDataUpdate(TickEvent)` - Process market data ticks

## Architecture

```
Event Bus → IMatchingEngine → Order Books → Trade Generation → Event Bus
```

### Components

- **IMatchingEngine**: Core interface defining matching operations
  - `onNewOrder(OrderEnvelope)` - Process new orders
  - `onCancel(OrderCancel)` - Cancel orders
  - `onReplace(OrderModify)` - Modify orders
  - `onMarketDataUpdate(TickEvent)` - Process market data
- **MatchingEngine**: Implementation of IMatchingEngine with order book management
- **Order Books**: Per-symbol limit order books (using `core:orderbook`)
- **Event Integration**: Subscribes to ORDER_SUBMITTED, publishes ORDER_FILLED/PARTIALLY_FILLED/CANCELLED/MODIFIED

## Features

- **Price-Time Priority Matching**: Orders matched in strict FIFO order at each price level
- **Multi-Symbol Support**: Maintains separate order books per trading symbol
- **Trade Generation**: Automatic TradeEvent and ExecutionEvent creation
- **Order Management**: Support for cancel and modify operations
- **Market Data Integration**: Process market data ticks for stop orders (future)
- **Deterministic Replay**: Sequence-based tracking for state recovery
- **GC-Neutral Design**: Minimal allocations in hot path
- **Event-Driven**: Fully integrated with Event Bus for decoupled architecture
- **Risk Validation**: Inline pre-trade risk checks

## Performance

Target: **< 5 µs per match (p99)**

Based on benchmarks:
- New order processing (onNewOrder): ~5-7 µs
- Order cancellation (onCancel): ~2-4 µs
- Order modification (onReplace): ~6-9 µs
- Market data update (onMarketDataUpdate): ~1-2 µs
- Matching order execution: ~2-4 µs
- Non-matching order (add to book): ~500 ns
- Order book lookup: ~10 ns (cache hit)

See [PERFORMANCE_BENCHMARKS.md](PERFORMANCE_BENCHMARKS.md) for detailed benchmark results.

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
```

### Using IMatchingEngine Interface

```java
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

// Modify order price
OrderModify modify = OrderModify.modifyPrice(1L, "AAPL", 15100L, 999L, 100L);
engine.onReplace(modify);

// Process market data tick
TickEvent tick = TickEvent.create(
    "AAPL", 14900L, 15000L, 14950L,
    1000L, 1000L, 1L, 1
);
engine.onMarketDataUpdate(tick);
```

### Event Bus Usage (Legacy Pattern)

You can also use the traditional event bus pattern:

```java
// Publish order events through EventBus
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

### Real-Time Performance Metrics

The matching engine provides comprehensive real-time metrics via Micrometer for monitoring:

```java
// Create metrics collector with Prometheus registry
MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
MatchingEngineMetricsCollector metrics = new MatchingEngineMetricsCollector(registry, "engine-1");

// Create engine with metrics
MatchingEngine engine = new MatchingEngine(eventBus, riskValidator, null, metrics);
engine.start();

// Access metrics
long ordersProcessed = metrics.getOrdersProcessedCount();
long tradesGenerated = metrics.getTradesGeneratedCount();
long ordersRejected = metrics.getOrdersRejectedCount();
long ordersCancelled = metrics.getOrdersCancelledCount();
long ordersModified = metrics.getOrdersModifiedCount();

// Latency metrics (in nanoseconds)
double p50 = metrics.getP50MatchLatencyNanos();
double p95 = metrics.getP95MatchLatencyNanos();
double p99 = metrics.getP99MatchLatencyNanos();

// Queue depth
long queueDepth = metrics.getQueueDepth();

// Export to Prometheus
String prometheusMetrics = ((PrometheusMeterRegistry) metrics.getRegistry()).scrape();
```

#### Available Metrics

| Metric Name | Type | Description |
|-------------|------|-------------|
| `matching.orders.processed` | Counter | Total orders processed by the engine |
| `matching.trades.generated` | Counter | Total trades generated |
| `matching.orders.rejected` | Counter | Total orders rejected (risk/validation) |
| `matching.orders.cancelled` | Counter | Total orders cancelled |
| `matching.orders.modified` | Counter | Total orders modified |
| `matching.latency.match` | Timer | Order matching latency (p50, p95, p99) |
| `matching.latency.cancel` | Timer | Order cancellation latency (p50, p95, p99) |
| `matching.latency.modify` | Timer | Order modification latency (p50, p95, p99) |
| `matching.queue.depth` | Gauge | Current queue depth for pending orders |

All metrics are tagged with `engine=<name>` for multi-instance deployments.

#### Performance Targets

- **p99 latency**: < 15 µs
- **Profiling overhead**: < 1%
- **Throughput**: ≥ 5M orders/sec per instance

#### Prometheus Integration

Metrics can be scraped by Prometheus at `/metrics` endpoint when integrated with REST gateway:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'matching-engine'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/metrics'
    scrape_interval: 5s
```

#### Grafana Dashboard

Example Grafana queries:

```promql
# Orders per second
rate(matching_orders_processed_total[1m])

# p99 match latency
matching_latency_match{quantile="0.99"}

# Trade rate
rate(matching_trades_generated_total[1m])

# Rejection rate
rate(matching_orders_rejected_total[1m]) / rate(matching_orders_processed_total[1m])

# Queue depth
matching_queue_depth
```

#### Alerts

Configure alerts for SLA violations:

```yaml
# Alert if p99 latency exceeds 15 µs
- alert: HighMatchingLatency
  expr: matching_latency_match{quantile="0.99"} > 15000
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "Matching engine p99 latency exceeds 15 µs"
    description: "Engine {{ $labels.engine }} latency is {{ $value }}ns"

# Alert on high rejection rate  
- alert: HighRejectionRate
  expr: rate(matching_orders_rejected_total[5m]) / rate(matching_orders_processed_total[5m]) > 0.1
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "Matching engine rejection rate > 10%"
```

### Optional Profiling Hooks

For microsecond-level profiling with < 1% overhead:

```java
// Enable profiling (disabled by default)
ProfilingHooks profiling = new ProfilingHooks(true);

// Instrument critical sections
long eventId = profiling.onOrderProcessingStart(orderId, symbol);
try {
    // Process order
    processOrder(order);
} finally {
    profiling.onOrderProcessingEnd(eventId, orderId);
}

// Record specific events
profiling.onTradeGenerated(tradeId, orderId, fillQty, fillPrice);
profiling.onOrderRejected(orderId, reasonCode);
profiling.onOrderCancelled(orderId, symbol);

// Flush periodically
profiling.flush();
```

The `ProfilingHooks` class provides stub implementations ready for integration with:
- Chronicle Flight Recorder (CFR)
- Java Flight Recorder (JFR)
- Custom profiling tools

When disabled (default), all operations are no-ops with zero overhead.

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
- **core:risk** - Risk validation modules
- **io.micrometer:micrometer-core** - Metrics collection framework
- **io.micrometer:micrometer-registry-prometheus** - Prometheus metrics export
- **JUnit Jupiter** - Testing framework
- **JMH** - Benchmarking framework

## Thread Safety

- Each symbol's order book follows single-writer principle
- Event Bus handles thread coordination
- ConcurrentHashMap used for multi-symbol book management
- AtomicLong for counters (execution ID, trade ID, sequence)

## Future Enhancements

- [ ] Market order support (currently limit orders only)
- [ ] Stop and stop-limit order types (with TickEvent integration)
- [ ] Iceberg order handling
- [ ] Self-trade prevention
- [x] Order book snapshots for faster replay (implemented)
- [ ] Persistent snapshot storage (disk/database)
- [ ] Incremental snapshots (delta compression)
- [ ] NUMA-aware memory layout
- [ ] Off-heap order book storage
- [ ] Lock-free matching algorithm
- [ ] Multi-threaded matching (parallel symbols)
- [ ] CPU affinity for matching threads

## Documentation

- [Matching Engine Core Implementation](MATCHING_ENGINE_CORE_IMPLEMENTATION.md) - Detailed implementation guide
- [Performance Benchmarks](PERFORMANCE_BENCHMARKS.md) - Benchmark results and methodology
- [Cache Warming Service](CACHE_WARMING_SERVICE.md) - Cache optimization
- [Cache Reconciliation](CACHE_RECONCILIATION.md) - State reconciliation
- [Acceptance Criteria Verification](ACCEPTANCE_CRITERIA_VERIFICATION.md) - Requirements validation

## See Also

- [Event Bus Documentation](../eventbus/README.md)
- [Order Book Documentation](../orderbook/README.md)
- [Risk Module Documentation](../risk/README.md)
- [Architecture Overview](../../docs/ARCHITECTURE.md)

