# Execution Management System (EMS)

## Overview

The Execution Management System (EMS) is a sophisticated order execution framework that implements algorithmic trading strategies for optimal order execution. The EMS orchestrates child orders via the Smart Order Router (SOR) and Order Management System (OMS), enabling institutional-quality execution algorithms.

## Features

### Core Components

- **Pluggable Strategy Interface**: Extensible framework for implementing custom execution strategies
- **Built-in Strategies**: TWAP, VWAP, IOC, and Iceberg implementations
- **YAML Configuration**: Declarative strategy configuration via YAML/JSON
- **Runtime Controls**: Pause, resume, and stop strategies during execution
- **Performance Metrics**: Comprehensive metrics and monitoring for strategy performance
- **Event-Driven Architecture**: Reactive design based on market ticks and fill events

### Strategy Lifecycle

```
CREATED → RUNNING → [PAUSED ⇄ RUNNING] → COMPLETED/STOPPED
```

## Supported Strategies

### TWAP (Time-Weighted Average Price)

Executes orders by splitting them into equal-sized child orders at regular time intervals.

**Configuration Example:**
```yaml
strategyId: "twap-aapl-001"
strategyType: "TWAP"
symbol: "AAPL"
duration: "PT10M"  # 10 minutes
numSlices: 20      # 20 child orders
minChildSize: 100
maxChildSize: 5000
```

**Algorithm:**
- Splits parent order into N equal slices
- Submits one slice every T seconds (duration / numSlices)
- Continues until all quantity executed or time expires

### VWAP (Volume-Weighted Average Price)

Executes orders in proportion to historical volume patterns to minimize market impact.

**Configuration Example:**
```yaml
strategyId: "vwap-googl-001"
strategyType: "VWAP"
symbol: "GOOGL"
duration: "PT30M"  # 30 minutes
minChildSize: 50
maxChildSize: 2000
parameters:
  expectedDayVolume: 5000000
  volumeParticipationRate: 0.05
```

**Algorithm:**
- Monitors cumulative market volume
- Calculates target execution: totalQty × (currentVol / expectedDayVol)
- Submits child orders to maintain target pace
- Adjusts dynamically based on volume profile

## Architecture

### Strategy Interface

All strategies implement the `Strategy` interface:

```java
public interface Strategy {
    void start(ParentOrder parentOrder) throws StrategyException;
    void onTick(MarketTick tick);
    void onFill(FillEvent fill);
    void pause();
    void resume();
    void stop();
    StrategyState getState();
    StrategyMetrics getMetrics();
}
```

### EMSService

The `EMSService` orchestrates strategy execution:

```java
EMSService ems = new EMSService(eventBus, childOrderManager);

// Start a strategy
Strategy strategy = ems.startStrategy(config, parentOrder);

// Control strategy
ems.pauseStrategy("strategy-id");
ems.resumeStrategy("strategy-id");
ems.stopStrategy("strategy-id");

// Get metrics
StrategyMetrics metrics = ems.getMetrics("strategy-id");
```

### Integration with OMS/SOR

```
ParentOrder → EMSService → Strategy → ChildOrder → ChildOrderManager → SOR → Venues
                ↓                         ↑
            MarketData              FillEvents
```

## Usage

### 1. Create Strategy Configuration

**Option A: Programmatic**
```java
StrategyConfig config = new StrategyConfig.Builder()
    .strategyId("twap-001")
    .strategyType(StrategyType.TWAP)
    .symbol("AAPL")
    .duration(Duration.ofMinutes(10))
    .numSlices(20)
    .minChildSize(100L)
    .build();
```

**Option B: YAML Configuration**
```yaml
# twap-config.yaml
strategyId: "twap-001"
strategyType: "TWAP"
symbol: "AAPL"
duration: "PT10M"
numSlices: 20
minChildSize: 100
```

```java
StrategyConfigLoader loader = new StrategyConfigLoader();
StrategyConfig config = loader.loadFromFile(Path.of("twap-config.yaml"));
```

### 2. Start Strategy Execution

```java
// Create parent order
ParentOrder parentOrder = new ParentOrder(
    new InternalOrderId(1L),
    new ClientOrderId("client-001"),
    "AAPL",
    Side.BUY,
    OrderType.LIMIT,
    10000L,  // quantity
    15000L,  // price
    Instant.now()
);

// Start strategy
Strategy strategy = emsService.startStrategy(config, parentOrder);
```

### 3. Feed Market Data

```java
MarketTick tick = new MarketTick(
    "AAPL",
    Instant.now(),
    14950L,  // bid
    15050L,  // ask
    1000L,   // bid size
    1000L,   // ask size
    15000L,  // last price
    100L,    // last size
    1000000L // cumulative volume
);

emsService.onMarketTick(tick);
```

### 4. Handle Fill Events

```java
FillEvent fill = new FillEvent(
    childOrderId,
    parentOrderId,
    Instant.now(),
    15000L,  // fill price
    100L,    // fill quantity
    900L,    // remaining
    "NYSE",
    "exec-001"
);

emsService.onFill(fill);
```

### 5. Monitor Performance

```java
StrategyMetrics metrics = emsService.getMetrics("twap-001");

System.out.println("Fill rate: " + metrics.getFillRate() * 100 + "%");
System.out.println("Child orders: " + metrics.childOrdersGenerated());
System.out.println("Avg fill price: " + metrics.averageFillPrice());
System.out.println("Duration: " + metrics.getExecutionDuration());
```

## Runtime Controls

### Pause/Resume

```java
// Pause strategy (stops generating new orders, tracks existing fills)
emsService.pauseStrategy("strategy-id");

// Resume strategy
emsService.resumeStrategy("strategy-id");
```

### Stop

```java
// Stop strategy permanently
emsService.stopStrategy("strategy-id");
```

## Metrics & Dashboard

### Available Metrics

- **Execution Progress**: Total/filled/remaining quantity
- **Child Order Stats**: Generated/filled/rejected counts
- **Price Performance**: Average/VWAP/best/worst fill prices
- **Slippage**: Total and average slippage from benchmark
- **Timing**: Start/end time, execution duration
- **Throughput**: Ticks processed, fills processed

### Accessing Metrics

```java
StrategyMetrics metrics = strategy.getMetrics();

// Execution progress
double fillRate = metrics.getFillRate();  // 0.0 to 1.0
long filled = metrics.filledQuantity();
long remaining = metrics.totalQuantity() - metrics.filledQuantity();

// Performance
long avgPrice = metrics.averageFillPrice();
long vwap = metrics.vwap();
long slippage = metrics.getAverageSlippage();

// Timing
Duration executionTime = metrics.getExecutionDuration();
```

## Testing

### Unit Tests

```bash
./gradlew :core:ems:test
```

Tests include:
- Strategy lifecycle (start/pause/resume/stop)
- TWAP child order scheduling and cadence
- VWAP volume-based execution
- Market tick processing
- Fill event handling
- Metrics calculation

### Integration Tests

```bash
./gradlew :core:ems:test --tests "*.integration.*"
```

Integration tests validate:
- End-to-end strategy execution
- TWAP producing expected child order cadence
- Strategy reaction to market ticks and fills
- Control endpoint behavior
- Concurrent strategy execution

## Performance Characteristics

| Operation | Target | Notes |
|-----------|--------|-------|
| Strategy start | < 1 ms | Initialization and scheduling |
| Tick processing | < 10 µs | Per strategy per tick |
| Fill processing | < 10 µs | Per strategy per fill |
| Child order generation | < 100 µs | Create and submit child order |
| Metrics calculation | < 1 µs | Read-only metrics access |

## Configuration Reference

### Common Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `strategyId` | String | Yes | Unique identifier |
| `strategyType` | Enum | Yes | TWAP, VWAP, IOC, ICEBERG |
| `symbol` | String | Yes | Trading symbol |
| `duration` | Duration | Yes | Total execution duration |
| `minChildSize` | Long | No | Minimum child order size |
| `maxChildSize` | Long | No | Maximum child order size |
| `priceLimit` | Long | No | Price limit for child orders |
| `allowPartialFills` | Boolean | No | Allow partial fills (default: true) |
| `parameters` | Map | No | Strategy-specific parameters |

### TWAP Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `numSlices` | Integer | Yes | Number of child orders |

### VWAP Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `expectedDayVolume` | Long | No | Expected total daily volume |
| `volumeParticipationRate` | Double | No | Target % of market volume |

## Future Enhancements

- **Additional Strategies**: POV (Percentage of Volume), Implementation Shortfall, Adaptive algorithms
- **Advanced VWAP**: Historical volume profiles, intraday patterns
- **Smart Routing**: Integration with SOR for optimal venue selection
- **Risk Controls**: Position limits, price bands, max participation rate
- **Machine Learning**: ML-based execution optimization
- **Dashboard UI**: Real-time strategy monitoring and control
- **Backtesting**: Historical simulation of strategy performance

## Dependencies

- `core:oms` - Order Management System
- `core:eventbus` - Event Bus for messaging
- `core:orderbook` - Order book data structures
- Jackson YAML - Configuration parsing
- SLF4J - Logging

## License

Copyright © 2024 The Last War. All rights reserved.
