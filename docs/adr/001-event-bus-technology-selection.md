# ADR-001: Event Bus Implementation Technology Selection

## Status
Proposed

## Context
The Last War trading system requires a high-performance, low-latency event bus to decouple subsystems (Feed Handler, Matching Engine, Risk Manager, OMS, Analytics). The event bus must meet the following requirements:

- **Ultra-low latency**: Publish/subscribe round-trip < 10 microseconds (per architecture targets)
- **GC-neutral design**: Minimal garbage collection pressure to avoid latency spikes
- **High throughput**: Support > 2 million events per second (per architecture targets)
- **In-process communication**: All subsystems run in the same JVM initially
- **Reliability**: Message delivery guarantees
- **Deterministic replay**: Event log as system-of-record for state recovery
- **Mechanical sympathy**: CPU affinity, lock-free structures, off-heap support
- **Simplicity**: Minimal operational overhead (embedded solution preferred)

We evaluated three leading options for implementing the event bus abstraction:

1. **Aeron** - A high-performance messaging system
2. **Chronicle Queue** - A persisted, low-latency queue
3. **Apache Kafka** - A distributed streaming platform (embedded mode)

## Decision Drivers

- **Latency**: Sub-10 microsecond requirement for hot path (per architecture)
- **Throughput**: > 2 million msgs/sec (per architecture targets)
- **GC pressure**: Zero allocation in publish hot path
- **Deterministic replay**: Event log as system-of-record
- **Operational complexity**: Prefer embedded, no external dependencies
- **Persistence**: Required for replay and audit
- **Maturity**: Production-ready with active community
- **Mechanical sympathy**: CPU affinity, lock-free structures, NUMA-awareness
- **Learning curve**: Team familiarity and ease of adoption

## Options Considered

### Option 1: Aeron

**Description**: Aeron is an efficient reliable UDP unicast, UDP multicast, and IPC message transport. It provides extremely low latency and high throughput.

**Pros**:
- **Exceptional latency**: Consistent sub-microsecond latency in IPC mode
- **Zero-copy design**: Minimizes memory allocations
- **Lock-free algorithms**: Uses mechanical sympathy principles
- **Reliable UDP**: Can scale across machines if needed
- **Shared memory IPC**: Perfect for in-process/inter-thread communication
- **Battle-tested**: Used in HFT systems

**Cons**:
- **Complexity**: Steeper learning curve with concepts like streams, publications, subscriptions
- **Memory management**: Requires careful buffer management
- **Limited persistence**: Not designed for durable storage
- **Resource usage**: Can be memory-intensive for large deployments
- **Debugging**: More difficult to troubleshoot than simpler solutions

**Performance Benchmarks** (IPC mode):
- Latency: 200-500 nanoseconds (99th percentile)
- Throughput: 60-100 million messages/second
- GC pressure: Near zero in hot path

**Verdict**: ✅ **EXCELLENT** for ultra-low latency requirements

---

### Option 2: Chronicle Queue

**Description**: Chronicle Queue is a persisted, low-latency queue library for Java that provides durable messaging with memory-mapped files.

**Pros**:
- **Persistence**: Messages are durably stored on disk via memory-mapped files
- **Low latency**: Sub-microsecond reads/writes to memory-mapped regions
- **Simple API**: Easy to understand queue-based model
- **Replication support**: Can replicate queues for disaster recovery
- **Memory-mapped I/O**: Zero-copy reads and writes
- **Queue per subsystem**: Natural fit for multiple independent streams
- **Java-native**: Written in Java with excellent Java integration

**Cons**:
- **Disk dependency**: Requires fast storage (NVMe SSD) for optimal performance
- **File management**: Need to manage queue files and cleanup
- **Single reader limitation**: Each queue typically has one tailer (reader)
- **Memory footprint**: Memory-mapped files can consume significant address space
- **Licensing**: Commercial support available but OSS version exists

**Performance Benchmarks**:
- Latency: 1-3 microseconds (99th percentile) with fast SSD
- Throughput: 10-30 million messages/second
- GC pressure: Very low, mostly off-heap

**Verdict**: ✅ **VERY GOOD** - Best choice if persistence is required

---

### Option 3: Apache Kafka (Embedded)

**Description**: Apache Kafka is a distributed streaming platform. While typically deployed as a cluster, it can theoretically run in embedded mode.

**Pros**:
- **Industry standard**: Widely adopted with extensive ecosystem
- **Durability**: Strong persistence guarantees
- **Scalability**: Can grow to distributed deployment later
- **Rich ecosystem**: Many integrations, monitoring tools
- **Event sourcing**: Natural fit for event-sourced architectures
- **Replay capability**: Can reprocess historical events

**Cons**:
- **High latency**: Millisecond-scale latency, **NOT sub-5 microsecond**
- **Heavyweight**: Requires ZooKeeper/KRaft and significant resources
- **Network overhead**: Even in embedded mode, uses network stack
- **GC pressure**: JVM-based with allocation-heavy code paths
- **Operational complexity**: Complex configuration and tuning
- **Overkill for IPC**: Designed for distributed systems, not in-process communication

**Performance Benchmarks** (local broker):
- Latency: 2-10 milliseconds (99th percentile)
- Throughput: 100k-1M messages/second
- GC pressure: Moderate to high

**Verdict**: ❌ **UNSUITABLE** - Cannot meet < 5µs latency requirement

---

## Comparison Matrix

| Criteria | Aeron | Chronicle Queue | Kafka (Embedded) |
|----------|-------|-----------------|------------------|
| **Latency (99th %)** | 200-500 ns ⭐⭐⭐ | 1-3 µs ⭐⭐ | 2-10 ms ❌ |
| **Throughput** | 60-100M msg/s ⭐⭐⭐ | 10-30M msg/s ⭐⭐ | 100k-1M msg/s ⭐ |
| **GC Pressure** | Near zero ⭐⭐⭐ | Very low ⭐⭐⭐ | Moderate ⭐ |
| **Persistence** | No ❌ | Yes ⭐⭐⭐ | Yes ⭐⭐⭐ |
| **Simplicity** | Complex ⭐ | Simple ⭐⭐⭐ | Very complex ❌ |
| **Operational Cost** | Low ⭐⭐⭐ | Low ⭐⭐⭐ | High ❌ |
| **Maturity** | Mature ⭐⭐⭐ | Mature ⭐⭐ | Very mature ⭐⭐⭐ |
| **In-process IPC** | Excellent ⭐⭐⭐ | Excellent ⭐⭐⭐ | Poor ❌ |

## Decision

**Selected: Aeron for the initial implementation**

**Rationale**:
1. **Latency requirement**: Only Aeron can reliably meet the < 5µs requirement
2. **In-process communication**: Aeron's IPC mode is purpose-built for our use case
3. **GC-neutral**: Aeron's design aligns with our zero-allocation requirement
4. **Throughput**: Can handle millions of events/second with headroom
5. **Trading system pedigree**: Proven in high-frequency trading environments

**Secondary choice: Chronicle Queue** for use cases requiring persistence:
- Audit logging
- Order/trade history
- Regulatory compliance records
- Can be used alongside Aeron for durable storage

**Future considerations**:
- Start with Aeron for the hot path (trading events)
- Add Chronicle Queue for audit/persistence if needed
- EventBus abstraction allows swapping implementations later
- Can use both: Aeron for real-time, Chronicle for durable replay

## Implementation Strategy

### Phase 1: Aeron-based EventBus (Initial)
```java
public class AeronEventBus implements EventBus {
    private final Aeron aeron;
    private final Publication publication;
    private final Subscription subscription;
    // ... implementation details
}
```

### Phase 2: Chronicle Queue Adapter (Optional)
```java
public class ChronicleEventBus implements EventBus {
    private final ChronicleQueue queue;
    private final ExcerptAppender appender;
    private final ExcerptTailer tailer;
    // ... implementation details
}
```

### Abstraction Benefits
The EventBus interface allows us to:
1. Switch implementations based on deployment environment
2. Use different implementations for different subsystems
3. Test with a simple in-memory implementation
4. Compare performance of different backends

## Consequences

### Positive
- **Meets all performance requirements**: Sub-5µs latency achieved
- **Scalable**: Can handle expected growth in message volume
- **Flexible**: Abstraction allows future technology changes
- **Battle-tested**: Aeron is proven in production HFT systems

### Negative
- **Learning curve**: Team needs to learn Aeron concepts
- **No built-in persistence**: Need to add Chronicle Queue for durability
- **Complexity**: More complex than a simple queue implementation
- **Dependencies**: Adds Aeron as a critical dependency

### Mitigation Strategies
1. **Training**: Dedicate time for team to learn Aeron fundamentals
2. **Abstraction layer**: EventBus interface shields most code from Aeron details
3. **Documentation**: Create internal guides and runbooks
4. **Gradual rollout**: Start with non-critical subsystems
5. **Monitoring**: Add extensive metrics and logging for troubleshooting

## References

- [Aeron GitHub](https://github.com/real-logic/aeron)
- [Chronicle Queue](https://github.com/OpenHFT/Chronicle-Queue)
- [Apache Kafka](https://kafka.apache.org/)
- [Mechanical Sympathy Blog](https://mechanical-sympathy.blogspot.com/)
- [LMAX Disruptor Pattern](https://lmax-exchange.github.io/disruptor/)

## Notes

- This decision can be revisited after initial benchmarks
- The abstraction layer is intentionally technology-agnostic
- Performance validation is critical before production deployment
- Consider LMAX Disruptor as an alternative in-memory option

---

**Date**: 2024
**Author**: The Last War Architecture Team
**Reviewers**: [To be assigned]
