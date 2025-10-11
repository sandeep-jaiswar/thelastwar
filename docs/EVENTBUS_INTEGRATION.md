# EventBus Gateway Integration Guide

## Overview

This document describes how the gateways (FIX, WebSocket, REST) integrate with the EventBus for unified inter-component messaging. The EventBus uses Aeron IPC transport for ultra-low latency communication with sustained throughput ≥ 2M msgs/sec.

## Architecture

```
┌──────────────┐
│ FIX Gateway  │──┐
└──────────────┘  │
                  │    ┌────────────┐       ┌─────────────────┐
┌──────────────┐  ├───→│  EventBus  │←─────→│ Matching Engine │
│  WebSocket   │──┤    │  (Aeron)   │       └─────────────────┘
│   Gateway    │  │    └────────────┘
└──────────────┘  │           ↕
                  │    ┌──────────────┐
┌──────────────┐  │    │ Risk Engine  │
│ REST Gateway │──┘    └──────────────┘
└──────────────┘              ↕
                       ┌──────────────┐
                       │  Analytics   │
                       └──────────────┘
```

## Topic Naming Conventions

### Event Type Ranges

Events are organized by subsystem using integer constants for zero-allocation performance:

- **Feed Handler Events**: `1000-1999`
  - `MARKET_DATA_UPDATE = 1000`
  - `MARKET_DATA_SNAPSHOT = 1001`
  - `FEED_CONNECTION_STATUS = 1002`

- **Matching Engine Events**: `2000-2999`
  - `ORDER_ACCEPTED = 2000`
  - `ORDER_REJECTED = 2001`
  - `ORDER_FILLED = 2002`
  - `ORDER_PARTIALLY_FILLED = 2003`
  - `ORDER_CANCELLED = 2004`

- **Risk Management Events**: `3000-3999`
  - `RISK_CHECK_PASSED = 3000`
  - `RISK_CHECK_FAILED = 3001`
  - `POSITION_UPDATE = 3002`
  - `LIMIT_BREACH = 3003`

- **OMS Events**: `4000-4999`
  - `ORDER_SUBMITTED = 4000`
  - `ORDER_MODIFIED = 4001`
  - `ORDER_STATUS_UPDATE = 4002`

- **Analytics Events**: `5000-5999`
  - `PERFORMANCE_METRIC = 5000`
  - `LATENCY_SAMPLE = 5001`
  - `TRADE_ANALYTICS = 5002`

- **System Events**: `9000-9999`
  - `SYSTEM_STARTUP = 9000`
  - `SYSTEM_SHUTDOWN = 9001`
  - `HEARTBEAT = 9002`

### Logical Topic Names

While the EventBus uses integer event types for performance, the logical topic naming follows these conventions:

- **Inbound (Gateway → Engine)**: `orders.in`, `risk.in`, `market-data.in`
- **Outbound (Engine → Gateway)**: `orders.out`, `fills.out`, `rejections.out`
- **Internal**: `risk.check`, `analytics.update`, `system.heartbeat`

## Event Routing Patterns

### 1. Inbound Order Flow (FIX/REST/WebSocket → Matching Engine)

```java
// Gateway publishes order submission
OrderEvent order = OrderEvent.newOrder(orderId, symbol, side, type, qty, price, accountId, exchangeId);
Event event = Event.create(
    System.nanoTime(),
    sequence.incrementAndGet(),
    SourceId.FEED_HANDLER,    // Gateway acts as feed handler
    EventType.ORDER_SUBMITTED, // Topic: orders.in
    correlationId,
    order
);
eventBus.publish(event);

// Matching Engine subscribes
eventBus.subscribe(EventType.ORDER_SUBMITTED, event -> {
    // Process order
    handleIncomingOrder(event);
});
```

### 2. Outbound Fill Flow (Matching Engine → Gateways)

```java
// Matching Engine publishes fill
Event fillEvent = Event.create(
    System.nanoTime(),
    sequence.incrementAndGet(),
    SourceId.MATCHING_ENGINE,
    EventType.ORDER_FILLED,    // Topic: fills.out
    orderId,
    executionEvent
);
eventBus.publish(fillEvent);

// FIX Gateway subscribes
eventBus.subscribe(EventType.ORDER_FILLED, event -> {
    sendExecutionReport(event);
});

// WebSocket Gateway subscribes
eventBus.subscribe(EventType.ORDER_FILLED, event -> {
    broadcastToClients(event);
});
```

### 3. Market Data Broadcast (Feed Handler → Gateways)

```java
// Feed Handler publishes market data
Event mdEvent = Event.create(
    System.nanoTime(),
    sequence.incrementAndGet(),
    SourceId.FEED_HANDLER,
    EventType.MARKET_DATA_UPDATE, // Topic: market-data.in
    0L,
    "AAPL,150.50,1000"
);
eventBus.publish(mdEvent);

// WebSocket Gateway subscribes
eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> {
    broadcastMarketData(event);
});
```

### 4. Risk Validation (Synchronous)

```java
// Risk validation happens synchronously before order submission
RiskDecision decision = riskValidator.validate(orderEvent);
if (!decision.isApproved()) {
    // Publish rejection
    Event rejection = Event.create(
        System.nanoTime(),
        sequence.incrementAndGet(),
        SourceId.MATCHING_ENGINE,
        EventType.ORDER_REJECTED,
        orderId,
        executionEvent
    );
    eventBus.publish(rejection);
}
```

## Backpressure Handling

The EventBus implements backpressure with hysteresis to prevent message loss:

### Publisher-Side Handling

```java
// Retry with exponential backoff on backpressure
boolean published = false;
int retries = 0;
while (!published && retries < MAX_RETRIES) {
    published = eventBus.publish(event);
    if (!published) {
        retries++;
        // Exponential backoff: 1µs, 10µs, 100µs, 1ms
        Thread.sleep(0, (int) Math.pow(10, retries) * 1000);
    }
}
```

### Backpressure Monitoring

```java
BackpressureMonitor monitor = eventBus.getBackpressureMonitor();
if (monitor.isBackpressureActive()) {
    // Take action: slow down publishers, drop low-priority events, etc.
    logger.warn("Backpressure active, current utilization: {}%", 
        monitor.getUtilizationPercent());
}
```

## Metrics

### Available Metrics

All metrics are exported via Micrometer and can be scraped by Prometheus:

1. **Throughput Metrics**
   - `eventbus.events.published` - Total events published (counter)
   - Rate calculations available via Prometheus

2. **Latency Metrics**
   - `eventbus.publish.latency` - Publish latency distribution (timer)
   - Percentiles: p50, p95, p99
   - Target: p99 < 10µs

3. **Backpressure Metrics**
   - `eventbus.backpressure.events` - Backpressure occurrences (counter)
   - `eventbus.messages.dropped` - Messages dropped (counter)
   - `eventbus.publishes.failed` - Failed publishes (counter)

4. **Queue Metrics**
   - `eventbus.queue.depth` - Ring buffer utilization 0-100% (gauge)
   - `eventbus.consumer.lag` - Events behind (gauge)

5. **Subscriber Metrics**
   - `eventbus.subscribers.active` - Active subscriber count (gauge)

### Accessing Metrics Programmatically

```java
EventBusMetrics metrics = eventBus.getMetrics();

// Check throughput
long publishedCount = metrics.getPublishedEventCount();

// Check latency
double p99LatencyNanos = metrics.getP99LatencyNanos();
double p99LatencyMicros = p99LatencyNanos / 1000.0;

// Check backpressure
long backpressureCount = metrics.getBackpressureEventCount();
long droppedCount = metrics.getDroppedMessageCount();

// Check consumer lag
long consumerLag = metrics.getConsumerLag();
long queueDepth = metrics.getQueueDepth();
```

## Replay Support

The EventBus supports deterministic replay for resilience testing and state recovery:

### Replay Markers

Events are assigned monotonically increasing sequence numbers:

```java
Event event = Event.create(
    System.nanoTime(),
    sequence.incrementAndGet(),  // Replay marker
    SourceId.MATCHING_ENGINE,
    EventType.ORDER_FILLED,
    orderId,
    payload
);
```

### Replaying Events

```java
// Replay events from sequence 1000 to 2000
long replayedCount = eventBus.replay(1000, 2000, event -> {
    // Process replayed event
    handleEvent(event);
});

System.out.printf("Replayed %d events%n", replayedCount);
```

### Current Sequence Tracking

```java
// Get current sequence for checkpoint
long currentSeq = eventBus.getCurrentSequence();
saveCheckpoint(currentSeq);

// Later, resume from checkpoint
long fromSeq = loadCheckpoint();
eventBus.replay(fromSeq, eventBus.getCurrentSequence(), this::handleEvent);
```

## Performance Targets

### Throughput

- **Target**: ≥ 2M msgs/sec sustained
- **Measurement**: Use `SyntheticOrderBurstIntegrationTest` or JMH benchmarks
- **Tuning**: Adjust Aeron term buffer size, MTU length, idle strategy

### Latency

- **Target**: p99 publish latency < 10µs
- **Measurement**: Via `EventBusMetrics.getP99LatencyNanos()`
- **Optimization**: Pin threads to CPU cores, use NUMA-aware allocation

### Message Loss

- **Target**: Zero message loss under backpressure
- **Guarantee**: Retry logic with exponential backoff
- **Validation**: `testZeroMessageLossUnderBackpressure()` test

## Integration Examples

### FIX Gateway Integration

```java
public class FixGateway implements GatewayAdapter {
    private final EventBus eventBus;
    
    public FixGateway(EventBus eventBus, SessionSettings settings) {
        this.eventBus = eventBus;
        
        // Subscribe to outbound events
        eventBus.subscribe(EventType.ORDER_ACCEPTED, this::sendExecutionReport);
        eventBus.subscribe(EventType.ORDER_FILLED, this::sendExecutionReport);
        eventBus.subscribe(EventType.ORDER_REJECTED, this::sendExecutionReport);
    }
    
    public void onMessage(MessageEnvelope envelope) {
        // Publish inbound order
        Event event = Event.create(
            envelope.getTimestamp(),
            sequence.incrementAndGet(),
            SourceId.FEED_HANDLER,
            EventType.ORDER_SUBMITTED,
            envelope.getCorrelationId(),
            parseOrder(envelope)
        );
        eventBus.publish(event);
    }
}
```

### WebSocket Gateway Integration

```java
public class WebSocketGateway implements GatewayAdapter {
    private final EventBus eventBus;
    
    public WebSocketGateway(EventBus eventBus, int port) {
        this.eventBus = eventBus;
        
        // Subscribe to broadcast events
        eventBus.subscribe(EventType.ORDER_ACCEPTED, this::broadcastToClients);
        eventBus.subscribe(EventType.ORDER_FILLED, this::broadcastToClients);
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, this::broadcastToClients);
    }
    
    private void broadcastToClients(Event event) {
        // Serialize and send to subscribed WebSocket clients
        for (WebSocketSession session : sessions.values()) {
            if (session.isSubscribed(event.eventType())) {
                session.send(serialize(event));
            }
        }
    }
}
```

### REST Gateway Integration

```java
@RestController
public class OrderController {
    private final EventBus eventBus;
    
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> submitOrder(@RequestBody OrderRequest request) {
        // Publish order to EventBus
        OrderEvent order = OrderEvent.newOrder(
            generateOrderId(),
            request.getSymbol(),
            request.getSide(),
            request.getType(),
            request.getQuantity(),
            request.getPrice(),
            request.getAccountId(),
            request.getExchangeId()
        );
        
        Event event = Event.create(
            System.nanoTime(),
            sequence.incrementAndGet(),
            SourceId.FEED_HANDLER,
            EventType.ORDER_SUBMITTED,
            order.orderId(),
            order
        );
        
        boolean published = eventBus.publish(event);
        if (!published) {
            return ResponseEntity.status(503).build(); // Service unavailable
        }
        
        return ResponseEntity.accepted().body(new OrderResponse(order.orderId()));
    }
}
```

## Testing

### Integration Tests

Run comprehensive integration tests:

```bash
# Test all EventBus integration
./gradlew :core:eventbus:test --tests "*IntegrationTest*"

# Test synthetic order bursts
./gradlew :core:eventbus:test --tests "*SyntheticOrderBurstIntegrationTest*"

# Test backpressure handling
./gradlew :core:eventbus:test --tests "*BackpressureTest*"
```

### Performance Benchmarks

Run JMH benchmarks for throughput validation:

```bash
# Run sustained throughput test (60 seconds)
./gradlew :core:eventbus:jmh -Pargs="-f 1 -wi 3 -i 1 -r 60 SyntheticLoadBenchmark"

# Run Aeron performance benchmark
./gradlew :core:eventbus:jmh -Pargs="AeronBenchmark"
```

### Load Testing

Simulate high-frequency trading scenarios:

```java
// Create synthetic order bursts
int ordersPerSecond = 2_000_000;
int durationSeconds = 60;
int totalOrders = ordersPerSecond * durationSeconds;

for (int i = 0; i < totalOrders; i++) {
    OrderEvent order = createRandomOrder();
    Event event = Event.create(
        System.nanoTime(), i,
        SourceId.FEED_HANDLER,
        EventType.ORDER_SUBMITTED,
        order.orderId(), order
    );
    eventBus.publish(event);
}
```

## Troubleshooting

### High Consumer Lag

If `eventbus.consumer.lag` is increasing:

1. Check subscriber processing time
2. Add more subscribers (parallel processing)
3. Optimize subscriber logic
4. Increase Aeron term buffer size

### Backpressure Events

If `eventbus.backpressure.events` is high:

1. Slow down publishers
2. Add retry logic with backoff
3. Drop low-priority events
4. Scale horizontally (add more EventBus instances)

### High Publish Latency

If p99 latency > 10µs:

1. Pin threads to CPU cores
2. Use NUMA-aware allocation
3. Reduce GC pressure (minimize allocations)
4. Use DEDICATED threading mode in Aeron

## Configuration

### EventBus Configuration

```java
// Default configuration (optimized for low latency)
AeronEventBus eventBus = new AeronEventBus();

// Custom configuration with metrics
MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
AeronEventBus eventBus = new AeronEventBus(registry, "my-eventbus");

// Custom MediaDriver configuration
MediaDriver.Context ctx = new MediaDriver.Context()
    .threadingMode(ThreadingMode.DEDICATED)  // Maximum performance
    .termBufferSparseFile(false)
    .publicationTermBufferLength(4 * 1024 * 1024)  // 4MB for higher throughput
    .ipcTermBufferLength(4 * 1024 * 1024);
    
MediaDriver mediaDriver = MediaDriver.launch(ctx);
AeronEventBus eventBus = new AeronEventBus(mediaDriver, registry, "my-eventbus");
```

### Gateway Configuration

All gateways follow the same pattern:

```java
// Initialize EventBus
EventBus eventBus = new AeronEventBus();
eventBus.start();

// Create gateway with EventBus
FixGateway fixGateway = new FixGateway(eventBus, fixSettings);
WebSocketGateway wsGateway = new WebSocketGateway(eventBus, 8080);
RestGateway restGateway = new RestGateway(eventBus);

// Start gateways
fixGateway.start();
wsGateway.start();
restGateway.start();
```

## Best Practices

1. **Use Integer Event Types**: Avoid autoboxing by using integer constants
2. **Pre-allocate Buffers**: Minimize GC pressure in hot paths
3. **Implement Retry Logic**: Handle backpressure gracefully
4. **Monitor Metrics**: Track latency, throughput, and consumer lag
5. **Use Sequence Numbers**: Enable deterministic replay
6. **Pin Threads to CPUs**: Improve mechanical sympathy
7. **Test Under Load**: Validate with synthetic order bursts
8. **Handle Errors Gracefully**: Use EventHandler.onError()

## References

- [Aeron Documentation](https://github.com/real-logic/aeron)
- [Micrometer Metrics](https://micrometer.io/)
- [Event Sourcing Patterns](https://martinfowler.com/eaaDev/EventSourcing.html)
- [Mechanical Sympathy](https://mechanical-sympathy.blogspot.com/)
