# ExecutionPublisher Implementation Guide

## Overview

The ExecutionPublisher is a high-performance event publishing system designed to distribute trade and execution events from the Matching Engine to downstream services including OMS, P&L calculation, Position Management, and Reconciliation systems.

## Architecture

```
┌─────────────────┐
│ Matching Engine │
└────────┬────────┘
         │ publishes
         ↓
┌─────────────────────┐
│ ExecutionPublisher  │
└───┬─────────┬───────┘
    │         │
    ↓         ↓
┌────────┐ ┌─────────┐
│ Event  │ │ Aeron   │
│ Bus    │ │ Multicast│
└────────┘ └────┬────┘
                │
        ┌───────┼───────┐
        ↓       ↓       ↓
   positions  ledger  executions
     .in       .in      .out
        │       │       │
        ↓       ↓       ↓
    ┌────┐  ┌────┐  ┌────┐
    │P&L │  │Rec │  │OMS │
    │Mgr │  │Sys │  │    │
    └────┘  └────┘  └────┘
```

## Key Features

### Performance Characteristics

- **Ultra-Low Latency**: < 10 µs end-to-end event propagation
- **High Throughput**: > 1M msgs/sec sustained throughput
- **Fast Serialization**: > 5M msgs/sec serialization speed using EventSerializer
- **Zero Allocations**: GC-neutral design with thread-local buffers
- **Consistent Ordering**: Sequential publishing guarantees order per instrument

### Reliability Features

- **Back-pressure Handling**: Automatic detection and retry logic
- **Zero Event Loss**: Configurable retry mechanism ensures delivery
- **Multi-channel Publishing**: Separate channels for different downstream systems
- **Lifecycle Management**: Automatic start/stop with MatchingEngine

## Topic Design

### positions.in
**Purpose**: Position updates for risk and P&L systems

**Publishes**: ExecutionEvent (fills, partial fills)

**Subscribers**: 
- Position Management Service
- Real-time P&L Calculator
- Risk Management System

### ledger.in
**Purpose**: Trade events for accounting and reconciliation

**Publishes**: TradeEvent (complete trade records)

**Subscribers**:
- Accounting Ledger
- Reconciliation System
- Trade Reporting

### executions.out
**Purpose**: Execution reports for OMS and clients

**Publishes**: ExecutionEvent (all types)

**Subscribers**:
- Order Management System
- Client Gateways (FIX, REST, WebSocket)
- Audit System

## Usage

### Basic Setup

```java
// 1. Create ExecutionPublisher
ExecutionPublisher publisher = AeronExecutionPublisher.builder()
    .positionsChannel("aeron:udp?endpoint=224.0.1.1:40123")
    .ledgerChannel("aeron:udp?endpoint=224.0.1.3:40124")
    .executionsChannel("aeron:udp?endpoint=224.0.1.5:40125")
    .streamId(1002)
    .metricsEnabled(true)
    .build();

// 2. Create MatchingEngine with publisher
RiskValidator riskValidator = createRiskValidator();
MatchingEngine engine = new MatchingEngine(
    eventBus,
    riskValidator,
    publisher  // Optional - can be null
);

// 3. Start engine (automatically starts publisher)
engine.start();

// 4. Publisher now automatically publishes all executions and trades
```

### Without External Publisher

The MatchingEngine works perfectly fine without an ExecutionPublisher. In this case, events are only published to the internal EventBus:

```java
// Create MatchingEngine without external publisher
MatchingEngine engine = new MatchingEngine(eventBus);
engine.start();

// All events still go to EventBus, just not to external topics
```

### Custom Channel Configuration

For testing or different deployment scenarios, you can customize the channels:

```java
// Use IPC channels for single-machine deployment
ExecutionPublisher publisher = AeronExecutionPublisher.builder()
    .positionsChannel("aeron:ipc")
    .ledgerChannel("aeron:ipc")
    .executionsChannel("aeron:ipc")
    .streamId(1002)
    .build();

// Use UDP unicast for point-to-point
ExecutionPublisher publisher = AeronExecutionPublisher.builder()
    .positionsChannel("aeron:udp?endpoint=10.0.1.100:40123")
    .ledgerChannel("aeron:udp?endpoint=10.0.1.101:40124")
    .executionsChannel("aeron:udp?endpoint=10.0.1.102:40125")
    .streamId(1002)
    .build();
```

### Direct Publishing

You can also use ExecutionPublisher directly outside of MatchingEngine:

```java
ExecutionPublisher publisher = AeronExecutionPublisher.createDefault();
publisher.start();

// Publish execution event
ExecutionEvent execution = ExecutionEvent.fill(...);
boolean published = publisher.publishExecution(execution);

// Publish trade event
TradeEvent trade = new TradeEvent(...);
published = publisher.publishTrade(trade);

// Publish with retry
published = publisher.publishExecutionWithRetry(execution, 3);

publisher.stop();
```

## Subscribing to Events

### Basic Subscriber

```java
// Setup Aeron media driver and client
MediaDriver mediaDriver = MediaDriver.launch(new MediaDriver.Context()
    .threadingMode(ThreadingMode.SHARED)
    .dirDeleteOnStart(true));

Aeron aeron = Aeron.connect(new Aeron.Context()
    .aeronDirectoryName(mediaDriver.aeronDirectoryName()));

// Subscribe to positions channel
Subscription positionsSub = aeron.addSubscription(
    "aeron:udp?endpoint=224.0.1.1:40123",
    1002
);

// Poll for events
FragmentHandler handler = new FragmentAssembler((buffer, offset, length, header) -> {
    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer.byteArray(), offset, length);
    ExecutionEvent event = EventSerializer.deserializeExecution(byteBuffer);
    
    // Process execution event
    processExecution(event);
});

// Poll continuously
while (running) {
    int fragments = positionsSub.poll(handler, 10);
    idleStrategy.idle(fragments);
}
```

### Integration Example

See `ExecutionPublisherIntegrationExample.java` for a complete working example with publisher and subscriber.

## Event Serialization

### Binary Format

Events are serialized using the high-performance EventSerializer:

```java
// Serialize execution event
ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getExecutionEventSize());
EventSerializer.serializeExecution(execution, buffer);

// Deserialize
buffer.flip();
ExecutionEvent event = EventSerializer.deserializeExecution(buffer);
```

### Event Sizes

- **ExecutionEvent**: 91 bytes
- **TradeEvent**: 85 bytes
- **Serialization time**: < 200 ns per event

## Monitoring and Metrics

### Publisher Metrics

```java
// Get statistics
long executionCount = publisher.getPublishedExecutionCount();
long tradeCount = publisher.getPublishedTradeCount();
long backPressure = publisher.getBackPressureCount();

// Check health
boolean isRunning = publisher.isRunning();

// Flush pending events
publisher.flush();
```

### Key Metrics to Monitor

1. **Published Counts**: Track message throughput
2. **Back-pressure Count**: Detect subscriber slowness
3. **Latency**: Measure end-to-end propagation time
4. **Event Loss**: Should always be zero with retries

## Performance Tuning

### Publisher Optimization

```java
// Disable metrics for maximum performance
ExecutionPublisher publisher = AeronExecutionPublisher.builder()
    .metricsEnabled(false)
    .build();

// Adjust retry count based on requirements
boolean published = publisher.publishExecutionWithRetry(event, 5); // More retries
```

### Subscriber Optimization

```java
// Use dedicated media driver thread
MediaDriver.Context context = new MediaDriver.Context()
    .threadingMode(ThreadingMode.DEDICATED)
    .conductorIdleStrategy(new BusySpinIdleStrategy())
    .senderIdleStrategy(new BusySpinIdleStrategy())
    .receiverIdleStrategy(new BusySpinIdleStrategy());

// Increase fragment limit for higher throughput
int fragments = subscription.poll(handler, 1024); // Process more per poll
```

### Network Tuning

For multicast deployments:
- Ensure multicast is enabled on network switches
- Use odd last octet for multicast addresses (Aeron requirement)
- Monitor network buffers: `sysctl net.core.rmem_max`
- Consider dedicated network interface for trading traffic

## Testing

### Unit Tests

Run ExecutionPublisher tests:
```bash
./gradlew :core:eventbus:test --tests "*AeronExecutionPublisherTest"
```

### Integration Tests

Run MatchingEngine integration tests:
```bash
./gradlew :core:matching:test --tests "*MatchingEngineExecutionPublisherTest"
```

### Benchmarks

Run JMH performance benchmarks:
```bash
./gradlew :core:eventbus:jmh -Pargs="ExecutionPublisherBenchmark"
```

Expected results:
- Throughput: > 1M ops/sec
- Latency: < 10 µs (p99)

## Troubleshooting

### Issue: Events Not Received

**Symptoms**: Subscriber polls return 0 fragments

**Solutions**:
1. Verify channels match exactly (including ports)
2. Check stream IDs match
3. Ensure multicast routing is configured
4. Verify no firewalls blocking UDP multicast
5. Check Aeron media driver is running

### Issue: High Back-pressure

**Symptoms**: `getBackPressureCount()` increasing

**Solutions**:
1. Check subscriber processing speed
2. Increase subscriber poll frequency
3. Increase Aeron term buffer size
4. Use dedicated threading mode
5. Add more subscriber instances

### Issue: Out of Order Events

**Symptoms**: Events received in wrong order

**Solutions**:
1. Ensure single publisher per instrument
2. Check network packet reordering
3. Verify subscriber processes fragments in order
4. Use sequence numbers for verification

## Migration Guide

### From EventBus Only

Before:
```java
MatchingEngine engine = new MatchingEngine(eventBus);
```

After:
```java
ExecutionPublisher publisher = AeronExecutionPublisher.createDefault();
MatchingEngine engine = new MatchingEngine(eventBus, riskValidator, publisher);
```

Events now go to both EventBus (for internal components) and ExecutionPublisher (for external systems).

## Best Practices

1. **Lifecycle Management**: Always start/stop publisher with matching engine
2. **Error Handling**: Monitor back-pressure count and handle appropriately
3. **Network Configuration**: Use dedicated network interface for production
4. **Testing**: Test with realistic load before production deployment
5. **Monitoring**: Set up alerts on back-pressure and event loss
6. **Capacity Planning**: Size subscriber systems for peak load + 50%

## References

- [Aeron Documentation](https://github.com/real-logic/aeron/wiki)
- [EventSerializer API](../core/eventbus/src/main/java/com/thelastwar/eventbus/model/EventSerializer.java)
- [ExecutionPublisher Interface](../core/eventbus/src/main/java/com/thelastwar/eventbus/ExecutionPublisher.java)
- [Integration Example](../core/eventbus/src/test/java/com/thelastwar/eventbus/example/ExecutionPublisherIntegrationExample.java)
