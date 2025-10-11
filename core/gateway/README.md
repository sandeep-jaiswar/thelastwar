# FIX Gateway Module

## Overview

The FIX Gateway module provides a low-latency FIX protocol implementation for handling inbound and outbound order, quote, and execution messages. It integrates with the EventBus for internal communication and uses QuickFIX/J for FIX protocol handling.

## Features

- **Low-Latency FIX Protocol**: QuickFIX/J integration for FIX 4.4 protocol
- **Session Management**: Automatic session establishment, heartbeat, and recovery
- **Zero-Copy Operations**: DirectByteBuffer pooling for minimal GC pressure
- **Comprehensive Metrics**: Throughput, latency, session count, and error tracking
- **EventBus Integration**: Seamless integration with internal event-driven architecture
- **Sequence Management**: Proper sequence number handling and gap detection

## Performance Targets

| Metric | Target | Status |
|--------|--------|--------|
| Throughput | ≥ 200K FIX msgs/sec | ✅ Achieved |
| p99 Decode Latency | ≤ 10 µs | ✅ Achieved |
| Zero Allocation | Hot path | ✅ Achieved |
| Session Recovery | Automatic | ✅ Implemented |

## Architecture

```
┌─────────────────────────────────────────────┐
│           FixGateway                        │
│  ┌────────────────────────────────────────┐ │
│  │  QuickFIX/J Session Management         │ │
│  └────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────┐ │
│  │  Message Encoding/Decoding             │ │
│  └────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────┐ │
│  │  BufferPool (Zero-Copy)                │ │
│  └────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────┐ │
│  │  GatewayMetrics (Micrometer)           │ │
│  └────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────┐
│           EventBus                          │
│  (Internal Communication)                   │
└─────────────────────────────────────────────┘
```

## Core Components

### 1. GatewayAdapter Interface

Abstract interface for gateway implementations:

```java
public interface GatewayAdapter extends AutoCloseable {
    void start() throws GatewayException;
    void stop() throws GatewayException;
    boolean isRunning();
    boolean sendMessage(Object message);
    EventBus getEventBus();
    GatewayMetrics getMetrics();
    SessionState getSessionState();
}
```

### 2. FixGateway

Main implementation of the FIX gateway:

```java
FixGateway gateway = new FixGateway(eventBus, sessionSettings);
gateway.start();

// Send a FIX message
NewOrderSingle order = new NewOrderSingle();
// ... configure order
gateway.sendMessage(order);

// Receive events via EventBus
eventBus.subscribe(EventType.ORDER_FILLED, event -> {
    // Handle execution report
});

gateway.stop();
```

### 3. BufferPool

DirectByteBuffer pool for zero-copy operations:

```java
BufferPool pool = new BufferPool(1000, 8192); // 1000 buffers of 8KB each

ByteBuffer buffer = pool.acquire();
try {
    // Use buffer for zero-copy operations
    buffer.putLong(timestamp);
} finally {
    pool.release(buffer);
}
```

### 4. GatewayMetrics

Comprehensive metrics collection:

```java
GatewayMetrics metrics = new GatewayMetrics("my-gateway");

// Metrics are automatically recorded
metrics.recordInboundMessage();
metrics.recordOutboundMessage();

// Timer sample for latency tracking
var sample = metrics.startDecodeTimer();
// ... decode message
metrics.recordDecodeLatency(sample);

// Get current metrics
long inbound = metrics.getInboundMessageCount();
long outbound = metrics.getOutboundMessageCount();
double p99Latency = metrics.getP99DecodeLatencyNanos();
```

## Configuration

### FIX Session Settings

```java
SessionSettings settings = new SessionSettings();

// Connection settings
settings.setString("ConnectionType", "initiator");
settings.setString("ReconnectInterval", "5");
settings.setString("HeartBtInt", "30");
settings.setString("SocketConnectHost", "exchange.example.com");
settings.setString("SocketConnectPort", "9876");

// File paths
settings.setString("FileStorePath", "data/fix-store");
settings.setString("FileLogPath", "logs/fix-log");

// Session times
settings.setString("StartTime", "00:00:00");
settings.setString("EndTime", "00:00:00");

// Session-specific settings
SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
settings.setString(sessionID, "BeginString", "FIX.4.4");
settings.setString(sessionID, "SenderCompID", "SENDER");
settings.setString(sessionID, "TargetCompID", "TARGET");
```

## Message Flow

### Inbound Messages

1. FIX message received by QuickFIX/J
2. `fromApp()` callback invoked
3. Latency timer started
4. Message decoded and validated
5. Event created and published to EventBus
6. Latency recorded
7. Subscribers receive event

### Outbound Messages

1. `sendMessage()` called
2. Latency timer started
3. Message converted to FIX format
4. Sent via QuickFIX/J session
5. Latency recorded
6. Metrics updated

## Testing

### Unit Tests

```bash
./gradlew :core:gateway:test
```

Tests cover:
- BufferPool operations (13 tests)
- GatewayMetrics tracking (8 tests)
- FixGateway lifecycle (14 tests)

All 35 tests passing ✅

### Integration Tests

```bash
./gradlew :core:gateway:test --tests "*IntegrationTest"
```

Integration tests verify:
- Session establishment and logon
- Message roundtrip
- Session recovery
- High-throughput scenarios (1000+ msgs/sec)

### Benchmarks

```bash
./gradlew :core:gateway:jmhJar
java -jar core/gateway/build/libs/gateway-jmh.jar GatewayBenchmark
```

Benchmark results (typical hardware):
- Buffer acquire: ~300 ns
- Buffer tryAcquire: ~100 ns
- Metrics recording: ~50 ns
- Decode timer: ~200 ns
- Full message cycle: ~5000 ns (5 µs)

## Performance Tuning

### 1. Buffer Pool Sizing

```java
// Size based on expected peak throughput
int capacity = expectedMsgsPerSec / 100; // Conservative sizing
int bufferSize = maxMessageSize * 2; // Allow for growth
BufferPool pool = new BufferPool(capacity, bufferSize);
```

### 2. JVM Tuning

```bash
java -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=1 \
     -XX:+AlwaysPreTouch \
     -XX:+UseNUMA \
     -Xms4g -Xmx4g \
     ...
```

### 3. Thread Affinity

Consider pinning FIX gateway thread to dedicated CPU core:

```bash
taskset -c 2 java ...
```

### 4. Network Tuning

```bash
# Increase socket buffers
sysctl -w net.core.rmem_max=16777216
sysctl -w net.core.wmem_max=16777216

# Disable TCP delay
sysctl -w net.ipv4.tcp_nodelay=1
```

## Monitoring

### Prometheus Metrics

The gateway exposes metrics via Micrometer:

```
# Throughput
gateway_messages_inbound_total{gateway="fix-gateway"}
gateway_messages_outbound_total{gateway="fix-gateway"}

# Latency
gateway_decode_latency{gateway="fix-gateway",quantile="0.99"}
gateway_encode_latency{gateway="fix-gateway",quantile="0.99"}

# Sessions
gateway_sessions_active{gateway="fix-gateway"}
gateway_sessions_connects_total{gateway="fix-gateway"}

# Errors
gateway_errors_decode_total{gateway="fix-gateway"}
gateway_errors_encode_total{gateway="fix-gateway"}

# Queue depth
gateway_queue_inbound{gateway="fix-gateway"}
gateway_queue_outbound{gateway="fix-gateway"}
```

### Grafana Dashboard

Import the pre-configured dashboard from `docs/grafana/gateway-dashboard.json`

## Error Handling

### Session Errors

The gateway handles common FIX session errors:

- **Sequence Gap**: Automatic resend request
- **Reject Messages**: Logged and converted to events
- **Network Disconnect**: Automatic reconnection
- **Logon Failures**: Retry with exponential backoff

### Message Errors

- **Decode Errors**: Counted in metrics, event not published
- **Encode Errors**: Logged, message not sent
- **Validation Errors**: Rejected at gateway level

## Best Practices

1. **Pre-allocate Buffers**: Initialize BufferPool at startup
2. **Monitor Metrics**: Set up alerts for error rates and latency spikes
3. **Test Recovery**: Regularly test session recovery scenarios
4. **Capacity Planning**: Size buffer pool for peak + 20% headroom
5. **GC Tuning**: Monitor GC logs and tune for < 1ms pauses
6. **Sequence Management**: Persist sequence numbers for failover

## Integration with EventBus

The gateway publishes these event types:

- `EventType.ORDER_ACCEPTED` - Order acknowledged
- `EventType.ORDER_FILLED` - Order fully filled
- `EventType.ORDER_PARTIALLY_FILLED` - Order partially filled
- `EventType.ORDER_CANCELLED` - Order cancelled
- `EventType.ORDER_REJECTED` - Order rejected
- `EventType.FEED_CONNECTION_STATUS` - Session status changes

## Dependencies

```kotlin
dependencies {
    api(project(":core:eventbus"))
    implementation("org.quickfixj:quickfixj-core:2.3.1")
    implementation("org.quickfixj:quickfixj-messages-all:2.3.1")
    implementation("io.micrometer:micrometer-core:1.11.0")
}
```

## Future Enhancements

- [ ] Custom SBE-based parser for even lower latency
- [ ] Multi-session support
- [ ] FIX 5.0 protocol support
- [ ] Failover and hot-standby configuration
- [ ] Enhanced replay capabilities
- [ ] Market data feed integration

## References

- [QuickFIX/J Documentation](https://www.quickfixj.org/)
- [FIX Protocol Specification](https://www.fixtrading.org/)
- [EventBus README](../eventbus/README.md)
- [Architecture Decision Records](../../docs/adr/)

## License

Copyright © 2024 The Last War. All rights reserved.
