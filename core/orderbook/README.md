# Order Book Module

High-performance in-memory limit order book implementations for ultra-low latency trading systems.

## Implementations

### 1. OffHeapOrderBook - Ultra-Efficient O(1) Operations
**NEW**: Ultra-efficient order book with O(1) operations using Agrona UnsafeBuffer and struct-of-arrays layout.

- **O(1) Add/Update/Remove**: Hash-based indexing with array storage
- **< 1 ns Best Bid/Ask**: Cached values for instant access (0.6 ns measured)
- **< 10 MB Heap for 1M Orders**: Off-heap storage with direct memory
- **Instrument Support**: Equity, Bond, and Derivative order books
- **Price-Time Priority**: Deterministic FIFO ordering within price levels
- **Cache-Line Optimized**: Struct-of-arrays layout prevents false sharing

See [OFFHEAP_ORDERBOOK.md](OFFHEAP_ORDERBOOK.md) for detailed documentation.

### 2. LimitOrderBook - Traditional TreeMap Implementation
High-performance order book with O(log n) insert and remove operations.

- **Price-Time Priority**: Orders at the same price level are matched in FIFO order
- **O(log n) Operations**: Add, remove, and modify operations using TreeMap
- **O(1) Order Lookup**: Fast order lookup by ID using HashMap
- **Single-Threaded Design**: Optimized for single-writer principle (caller must synchronize)
- **Allocation-Free Hot Path**: Minimal allocations after warm-up period

### 3. Off-Heap State Store
Optional memory-mapped persistence with < 2 µs latency.

See [OFF_HEAP_STORE.md](OFF_HEAP_STORE.md) for detailed documentation.

## Performance Comparison

| Feature | LimitOrderBook | OffHeapOrderBook |
|---------|---------------|------------------|
| Add/Remove | O(log n) ~150 ns | O(1) ~60 ns |
| Best Bid/Ask | O(1) ~5 ns | O(1) ~0.6 ns |
| Memory Location | Heap | Off-heap (direct) |
| Heap Usage (1M orders) | ~60 MB | < 7 MB |
| Cache Locality | Moderate | Excellent |
| False Sharing | Possible | Prevented |

## Performance

### OffHeapOrderBook Performance (NEW)

Based on JMH benchmarks on Java 25 with 1000 pre-populated orders:

| Operation | Average Latency | Target | Status |
|-----------|----------------|--------|--------|
| Get Best Bid | **0.637 ns** | < 200 ns | ✅ **314x faster** |
| Get Best Ask | **0.632 ns** | < 200 ns | ✅ **316x faster** |
| Update Order | **2.6 ns** | < 1000 ns | ✅ **385x faster** |
| Get Order | **9.1 ns** | < 1000 ns | ✅ **110x faster** |
| Remove Order | **61 ns** | < 1000 ns | ✅ **16x faster** |
| Add Order | **15.8 µs** | < 100 µs | ✅ **6.3x faster** |

**All operations meet or exceed performance targets** ✅

### LimitOrderBook Performance

Based on JMH benchmarks on Java 25:

| Operation | Average Latency | Target |
|-----------|----------------|--------|
| Add Order | ~153 ns | < 1 µs |
| Remove Order | ~144 ns | < 1 µs |
| Modify Order | ~44 ns | < 1 µs |
| Get Best Bid/Ask | ~5 ns | < 1 µs |

All operations meet the **< 1 µs p99 latency** requirement.

### Off-Heap Store Performance

Based on JMH benchmarks on Java 25:

| Operation | Average Latency | Target |
|-----------|----------------|--------|
| Get | 0.013 µs (13 ns) | < 2 µs |
| Put | 0.671 µs (671 ns) | < 2 µs |
| Update | 0.054 µs (54 ns) | < 2 µs |

All operations meet the **< 2 µs latency** requirement. ✅

See [OFF_HEAP_STORE.md](OFF_HEAP_STORE.md) and [OFFHEAP_ORDERBOOK.md](OFFHEAP_ORDERBOOK.md) for detailed documentation.

## Architecture

### Core Components

1. **Order**: Immutable record representing an order
   - Uses primitives for cache-friendly layout
   - Minimal memory footprint (~64 bytes)

2. **PriceLevel**: FIFO queue of orders at a single price
   - Uses ArrayDeque for optimal cache locality
   - Maintains total quantity aggregation

3. **LimitOrderBook**: Main order book implementation
   - TreeMap for bids (descending order)
   - TreeMap for asks (ascending order)
   - HashMap for O(1) order lookup

## Usage

### Basic Operations

```java
// Create order book
LimitOrderBook book = new LimitOrderBook("AAPL");

// Add buy order
Order buyOrder = new Order(
    1L,           // orderId
    "AAPL",       // symbol
    Order.SIDE_BUY,
    15000L,       // price ($150.00 in cents)
    100L,         // quantity
    System.nanoTime()
);
book.addOrder(buyOrder);

// Add sell order
Order sellOrder = new Order(
    2L,
    "AAPL",
    Order.SIDE_SELL,
    15100L,
    100L,
    System.nanoTime()
);
book.addOrder(sellOrder);

// Get market data
long bestBid = book.getBestBid();      // 15000
long bestAsk = book.getBestAsk();      // 15100
long spread = book.getSpread();        // 100

// Modify order
book.modifyOrder(1L, 0, 150L);  // Change quantity to 150

// Remove order
Order removed = book.removeOrder(2L);

// Get market depth
List<PriceLevel> topBids = book.getBidLevels(5);
List<PriceLevel> topAsks = book.getAskLevels(5);
```

### Snapshot and Recovery

Create snapshots for state recovery and disaster recovery:

```java
// Create snapshot of current order book state
LimitOrderBook.OrderBookSnapshot snapshot = book.createSnapshot();

// Store snapshot (serialize to disk, database, etc.)
persistSnapshot(snapshot);

// Later: restore from snapshot
LimitOrderBook newBook = new LimitOrderBook("AAPL");
LimitOrderBook.OrderBookSnapshot loadedSnapshot = loadSnapshot();
newBook.restoreFromSnapshot(loadedSnapshot);

// Order book is now in identical state
```

Snapshots capture:
- All orders in the book (both buy and sell sides)
- Price-time priority ordering
- Order quantities and prices
- Complete state for deterministic recovery

Use cases:
- **Disaster recovery**: Quickly restore state after system failure
- **Testing**: Capture production state for test scenarios
- **Audit**: Historical state snapshots for compliance
- **Load balancing**: Clone order book state to new instances
```

### Price-Time Priority Example

```java
// Add three orders at the same price
book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L));
book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 15000L, 200L, 2000L));
book.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, 15000L, 150L, 3000L));

// Orders at the same price are processed in time priority (FIFO)
// Order 1 (timestamp 1000) will be matched first
// Order 2 (timestamp 2000) will be matched second
// Order 3 (timestamp 3000) will be matched third
```

## Thread Safety

The order book is **NOT thread-safe** by design. This follows the single-writer principle for maximum performance:

- Each order book should be owned by a single thread
- Use external synchronization if multiple threads need access
- For distributed systems, consider using one order book per symbol per thread

## Testing

### Unit Tests

```bash
./gradlew :core:orderbook:test
```

Tests include:
- Order validation and immutability
- Price level FIFO ordering
- Order book operations (add, remove, modify)
- Price-time priority correctness

### Integration Tests

Integration tests simulate realistic trading scenarios:
- Market making
- Order cancellations and modifications
- High-volume order flow (1000+ orders)
- Stress testing with random operations (5000+ operations)

### Benchmarks

Run JMH benchmarks:

```bash
# All benchmarks
./gradlew :core:orderbook:jmh

# Specific benchmark
./gradlew :core:orderbook:jmh -Pargs="OrderBookBenchmark.addBuyOrder"

# With custom iterations
./gradlew :core:orderbook:jmh -Pargs="OrderBookBenchmark -wi 5 -i 10 -f 1"
```

Available benchmarks:
- `addBuyOrder`: Add buy order performance
- `addSellOrder`: Add sell order performance
- `removeOrder`: Remove order performance
- `modifyOrderQuantity`: Modify order quantity
- `modifyOrderPrice`: Modify order price
- `getBestBidAsk`: Get best bid/ask performance
- `getOrderById`: Order lookup performance
- `getMarketDepth`: Get market depth performance
- `mixedWorkload`: Realistic mixed operations

## Design Decisions

### Why TreeMap?

- O(log n) insert/remove/modify operations
- Built-in ordering (descending for bids, ascending for asks)
- Efficient iteration for market depth
- JDK implementation is well-optimized

### Why HashMap for Order Lookup?

- O(1) lookup by order ID
- Essential for fast cancellations and modifications
- Low memory overhead

### Why ArrayDeque for Price Levels?

- Optimal cache locality for FIFO queue
- O(1) add/remove from both ends
- Minimal memory overhead
- No node allocation overhead (unlike LinkedList)

### Why Single-Threaded?

- Eliminates synchronization overhead
- Enables CPU cache optimization
- Follows mechanical sympathy principles
- Aligns with LMAX Disruptor single-writer pattern

## Future Enhancements

- [ ] Off-heap storage using Chronicle Map
- [ ] Lock-free concurrent version
- [ ] Market order matching engine
- [x] Order book snapshots for replay (implemented)
- [x] Integration with event bus for order updates (via MatchingEngine)
- [ ] NUMA-aware memory layout
- [ ] SBE encoding for serialization
- [ ] Snapshot compression for efficient storage

## Integration with Trading System

### With EventBus

```java
// Subscribe to order events
eventBus.subscribe(EventType.ORDER_SUBMITTED, event -> {
    OrderEvent orderEvent = (OrderEvent) event.payload();
    Order order = new Order(
        orderEvent.orderId(),
        orderEvent.symbol(),
        orderEvent.side(),
        orderEvent.price(),
        orderEvent.quantity(),
        orderEvent.timestamp()
    );
    orderBook.addOrder(order);
});
```

### With Matching Engine

The order book provides the foundation for a matching engine:

1. Maintain separate order books per symbol
2. On new order arrival, check for matching orders
3. Execute matches using price-time priority
4. Emit trade events to event bus
5. Update order quantities or remove filled orders

## Performance Tuning

### JVM Options

```bash
java \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=1 \
  -XX:+AlwaysPreTouch \
  -XX:+UseStringDeduplication \
  -Xms2g -Xmx2g \
  -jar orderbook.jar
```

### CPU Pinning

For ultra-low latency, pin order book threads to dedicated CPU cores:

```java
// Set thread affinity (requires Affinity library)
AffinitySupport.setAffinity(1L << 2); // Pin to core 2
```

### Object Pooling

Consider object pooling for Order instances in high-frequency scenarios:

```java
// Pre-allocate order pool
ObjectPool<Order> orderPool = new OrderPool(10000);
```

## License

Copyright © 2024 The Last War. All rights reserved.
