# MessageEnvelope API Documentation

## Overview

The MessageEnvelope API provides a unified message format for all gateway implementations (FIX, REST, WebSocket) in The Last War trading system. It enables zero-copy message passing using Agrona DirectBuffers and object pooling to eliminate GC pressure in high-frequency trading scenarios.

## Key Features

- **Protocol-agnostic**: Supports FIX, REST, and WebSocket protocols
- **Zero-copy**: Uses Agrona DirectBuffer for payload storage
- **Object pooling**: Eliminates heap allocation in hot path
- **High performance**: < 0.1 µs serialization/deserialization
- **Type-safe**: Strong typing with enum protocol types
- **Thread-safe**: Lock-free object pool implementation

## Core Components

### 1. MessageEnvelope

A POJO representing a unified message format across all gateways.

**Fields:**
- `protocolType`: Protocol type (FIX, REST, WEBSOCKET)
- `correlationId`: Unique identifier for request-response tracking
- `payload`: Message payload as DirectBuffer (zero-copy)
- `timestamp`: Message timestamp in nanoseconds
- `clientId`: Client identifier string

**Example Usage:**

```java
// Create an envelope
MessageEnvelope envelope = new MessageEnvelope();

// Set fields using fluent API
envelope.setProtocolType(MessageEnvelope.ProtocolType.FIX)
    .setCorrelationId(12345L)
    .setTimestamp(System.nanoTime())
    .setClientId("client-001")
    .setPayload(payloadBytes, 0, payloadBytes.length);

// Read fields
ProtocolType type = envelope.getProtocolType();
long correlationId = envelope.getCorrelationId();
DirectBuffer payload = envelope.getPayload();
int payloadLength = envelope.getPayloadLength();
```

### 2. MessageEnvelopePool

Lock-free object pool for MessageEnvelope instances to eliminate GC pressure.

**Example Usage:**

```java
// Create pool with 1024 envelopes, 8KB buffer each
MessageEnvelopePool pool = new MessageEnvelopePool(1024, 8192);

// Acquire from pool
MessageEnvelope envelope = pool.acquire();

try {
    // Use the envelope
    envelope.setProtocolType(MessageEnvelope.ProtocolType.FIX);
    envelope.setCorrelationId(correlationId);
    envelope.setPayload(data, 0, data.length);
    
    // Process message
    gateway.send(envelope);
    
} finally {
    // Always return to pool
    pool.release(envelope);
}

// Check pool statistics
int available = pool.size();
int capacity = pool.capacity();
```

### 3. MessageEnvelopeSerializer

High-performance serializer/deserializer for MessageEnvelope.

**Binary Format (Little-Endian):**
```
+------------------+--------+
| Protocol Type    | 1 byte |
+------------------+--------+
| Correlation ID   | 8 bytes|
+------------------+--------+
| Timestamp        | 8 bytes|
+------------------+--------+
| Client ID Length | 4 bytes|
+------------------+--------+
| Client ID        | N bytes|
+------------------+--------+
| Payload Length   | 4 bytes|
+------------------+--------+
| Payload          | N bytes|
+------------------+--------+
```

**Example Usage:**

```java
// Serialize
UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(8192));
int bytesWritten = MessageEnvelopeSerializer.serialize(envelope, buffer);

// Deserialize
MessageEnvelope target = new MessageEnvelope();
int bytesRead = MessageEnvelopeSerializer.deserialize(buffer, 0, target);

// Calculate size before serialization
int size = MessageEnvelopeSerializer.getSerializedSize(envelope);
```

### 4. GatewayAdapter Interface Updates

The GatewayAdapter interface now includes methods for MessageEnvelope-based communication:

```java
public interface GatewayAdapter extends AutoCloseable {
    // ... existing methods ...
    
    /**
     * Sends an outbound message using MessageEnvelope.
     * This is the preferred method for zero-copy message passing.
     */
    boolean send(MessageEnvelope envelope);
    
    /**
     * Callback invoked when a message is received.
     * Implementations should process quickly to avoid blocking.
     */
    void onMessage(MessageEnvelope envelope);
}
```

## Performance Characteristics

### Benchmarks (JMH Results)

| Operation | Average Time | Target | Status |
|-----------|--------------|--------|--------|
| Serialize Small Payload (128B) | 0.031 µs | < 5 µs | ✅ 160x better |
| Deserialize Small Payload | 0.031 µs | < 5 µs | ✅ 160x better |
| Full Cycle (acquire → serialize → deserialize → release) | 0.123 µs | N/A | ✅ Excellent |
| Pool Acquire/Release | < 0.1 µs | N/A | ✅ Excellent |

### Test Coverage

| Component | Line Coverage | Branch Coverage | Method Coverage |
|-----------|---------------|-----------------|-----------------|
| MessageEnvelope | 97.8% | 75.0% | 100% |
| MessageEnvelopePool | 100% | 100% | 100% |
| MessageEnvelopeSerializer | 98.4% | 100% | 85.7% |

## Best Practices

### 1. Always Use Object Pooling

```java
// ❌ Bad: Creates new objects, causes GC pressure
MessageEnvelope envelope = new MessageEnvelope();
envelope.setPayload(data, 0, data.length);
// ... use envelope

// ✅ Good: Reuses objects from pool
MessageEnvelope envelope = pool.acquire();
try {
    envelope.setPayload(data, 0, data.length);
    // ... use envelope
} finally {
    pool.release(envelope);
}
```

### 2. Set All Fields

Always set all required fields to avoid stale data from pooled objects:

```java
envelope.setProtocolType(type)        // Required
    .setCorrelationId(id)              // Required
    .setTimestamp(System.nanoTime())   // Required
    .setClientId(clientId)             // Optional
    .setPayload(data, 0, length);      // Required
```

### 3. Handle Errors Gracefully

```java
MessageEnvelope envelope = pool.acquire();
try {
    // Use envelope
    if (!gateway.send(envelope)) {
        logger.warn("Failed to send message");
    }
} catch (Exception e) {
    logger.error("Error processing message", e);
} finally {
    // Always release, even on error
    pool.release(envelope);
}
```

### 4. Choose Appropriate Pool Size

```java
// For high-frequency trading: Large pool, power of 2
MessageEnvelopePool pool = new MessageEnvelopePool(2048, 8192);

// For moderate traffic: Medium pool
MessageEnvelopePool pool = new MessageEnvelopePool(256, 4096);

// Pool size must be power of 2: 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, etc.
```

### 5. Monitor Pool Utilization

```java
// Periodically check pool health
int available = pool.size();
int capacity = pool.capacity();
double utilization = (capacity - available) / (double) capacity * 100;

if (utilization > 80) {
    logger.warn("Pool utilization high: {}%", utilization);
}
```

## Integration Examples

### FIX Gateway Integration

```java
public class FixGateway implements GatewayAdapter {
    private final MessageEnvelopePool pool;
    
    public FixGateway() {
        this.pool = new MessageEnvelopePool(1024, 8192);
    }
    
    @Override
    public boolean send(MessageEnvelope envelope) {
        // Convert envelope to FIX message
        Message fixMessage = convertToFix(envelope);
        return Session.sendToTarget(fixMessage, sessionID);
    }
    
    @Override
    public void onMessage(MessageEnvelope envelope) {
        // Process incoming FIX message
        Event event = Event.create(
            envelope.getTimestamp(),
            sequenceNumber++,
            SourceId.FEED_HANDLER,
            EventType.ORDER_FILLED,
            envelope.getCorrelationId(),
            "FIX message received"
        );
        eventBus.publish(event);
    }
}
```

### REST Gateway Integration

```java
public class RestGateway implements GatewayAdapter {
    private final MessageEnvelopePool pool;
    
    @PostMapping("/api/orders")
    public Mono<ResponseEntity<String>> submitOrder(@RequestBody OrderRequest request) {
        MessageEnvelope envelope = pool.acquire();
        try {
            envelope.setProtocolType(MessageEnvelope.ProtocolType.REST)
                .setCorrelationId(generateCorrelationId())
                .setTimestamp(System.nanoTime())
                .setClientId(request.getClientId())
                .setPayload(serializeOrder(request), 0, serialized.length);
            
            send(envelope);
            return Mono.just(ResponseEntity.ok("Order submitted"));
            
        } finally {
            pool.release(envelope);
        }
    }
}
```

## Troubleshooting

### Pool Exhaustion

**Symptom:** Pool.acquire() creates new objects instead of reusing from pool

**Solution:**
1. Increase pool size
2. Check for leaked envelopes (not released)
3. Add monitoring to track acquire/release patterns

### Performance Degradation

**Symptom:** Serialization/deserialization slower than expected

**Solution:**
1. Ensure using DirectBuffer (not HeapBuffer)
2. Check payload size - large payloads (> 8KB) may trigger buffer expansion
3. Verify pool warmup - first few operations may be slower

### Memory Leaks

**Symptom:** Memory usage grows over time

**Solution:**
1. Always use try-finally to ensure release
2. Check for exceptions preventing release
3. Monitor pool.size() - should remain stable under load

## API Reference

See JavaDoc for detailed API documentation:
- [MessageEnvelope](../src/main/java/com/thelastwar/gateway/MessageEnvelope.java)
- [MessageEnvelopePool](../src/main/java/com/thelastwar/gateway/MessageEnvelopePool.java)
- [MessageEnvelopeSerializer](../src/main/java/com/thelastwar/gateway/MessageEnvelopeSerializer.java)
- [GatewayAdapter](../src/main/java/com/thelastwar/gateway/GatewayAdapter.java)

## Running Benchmarks

```bash
# Run all MessageEnvelope benchmarks
./gradlew :core:gateway:jmh -Pargs="MessageEnvelopeBenchmark"

# Run specific benchmark
./gradlew :core:gateway:jmh -Pargs="MessageEnvelopeBenchmark.serializeSmallPayload"

# Run with profiler
./gradlew :core:gateway:jmh -Pargs="MessageEnvelopeBenchmark -prof gc"
```

## Running Tests

```bash
# Run all tests
./gradlew :core:gateway:test

# Run specific test class
./gradlew :core:gateway:test --tests MessageEnvelopeTest

# Run with coverage
./gradlew :core:gateway:test :core:gateway:jacocoTestReport
```
