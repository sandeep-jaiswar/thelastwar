# ADR-002: OMS Idempotency, Deduplication, and Ordering Guarantees

## Status
Proposed

## Context
The Order Management System (OMS) must provide strong guarantees around order processing to ensure correctness, prevent duplicate orders, and maintain consistent state in the presence of failures, retries, and network issues. These guarantees are critical for:

- **Financial correctness**: Preventing duplicate order submissions that could result in unintended positions
- **State consistency**: Ensuring the order state machine evolves deterministically
- **Audit compliance**: Maintaining accurate records of all order state changes
- **System reliability**: Recovering gracefully from failures without data loss or corruption
- **Client trust**: Providing predictable behavior that clients can rely on

The OMS must handle:
1. Duplicate order submissions (network retries, client retries)
2. Out-of-order event delivery (in distributed systems)
3. Concurrent state transitions (multiple threads/processes)
4. System failures and restarts

## Decision Drivers

- **Correctness**: Prevent duplicate orders and ensure exactly-once semantics
- **Performance**: Minimize overhead of idempotency checks (target < 1µs)
- **Scalability**: Support high throughput (> 1M orders/sec)
- **Determinism**: Same sequence of events produces same final state
- **Auditability**: Complete history of all order state changes
- **Recovery**: Fast recovery from failures with no data loss

## Decision

### 1. Idempotency Strategy

#### Client Order ID Based Idempotency
- Every order must have a unique `ClientOrderId` provided by the client
- The OMS maintains an index: `ClientOrderId -> InternalOrderId`
- Duplicate order submissions with the same `ClientOrderId` are detected and rejected/acknowledged without creating a new order
- The `ClientOrderId` is immutable for the lifetime of the order

**Implementation:**
```java
// Idempotency check on order submission
public OrderSubmissionResult submitOrder(OrderRequest request) {
    ClientOrderId clientOrderId = request.getClientOrderId();
    
    // Check if order already exists (idempotency)
    InternalOrderId existingOrderId = clientOrderIdIndex.get(clientOrderId);
    if (existingOrderId != null) {
        // Idempotent response: return existing order
        return OrderSubmissionResult.alreadyExists(existingOrderId);
    }
    
    // Create new order
    InternalOrderId newOrderId = generateInternalOrderId();
    clientOrderIdIndex.put(clientOrderId, newOrderId);
    
    // Process order...
    return OrderSubmissionResult.success(newOrderId);
}
```

**Benefits:**
- Simple and efficient (O(1) lookup)
- Client-controlled deduplication
- Works across system restarts (if index is persisted)

**Limitations:**
- Requires clients to generate unique IDs
- Index must be persistent or reconstructed on restart

#### State Transition Idempotency
- State transitions to the current state are allowed (no-op)
- The `OrderStateMachine.transition()` method returns `false` for idempotent transitions
- This allows safe replay of duplicate events

**Example:**
```java
// Idempotent state transition
OrderStateMachine stateMachine = getStateMachine(orderId);
boolean transitioned = stateMachine.transition(OrderState.ACCEPTED);
// Returns false if already in ACCEPTED state
```

**Benefits:**
- Handles duplicate execution events gracefully
- Simplifies retry logic in clients
- Essential for deterministic replay

### 2. Deduplication Strategy

#### Event-Level Deduplication
Each event has a unique `eventId` assigned at ingestion. The OMS tracks processed events to detect duplicates.

**Implementation:**
- Maintain a bloom filter or hash set of recent event IDs
- Size: Last 1M events (~8MB with bloom filter, 0.1% false positive rate)
- TTL: Events older than 1 hour are evicted
- Persistent: Checkpointed to disk periodically

```java
public void processEvent(OrderEvent event) {
    if (processedEvents.contains(event.getEventId())) {
        // Duplicate event, skip processing
        logger.debug("Duplicate event detected: {}", event.getEventId());
        return;
    }
    
    // Process event
    processOrderEvent(event);
    
    // Mark as processed
    processedEvents.add(event.getEventId());
}
```

**Benefits:**
- Prevents duplicate processing of events
- Low memory footprint with bloom filter
- Fast lookup (O(1) average)

**Trade-offs:**
- Bloom filter false positives require fallback to exact check
- Requires memory proportional to deduplication window

#### Sequence Number Based Deduplication
For ordered event streams, use monotonically increasing sequence numbers per order.

**Implementation:**
```java
public void processOrderUpdate(InternalOrderId orderId, long sequenceNumber, OrderUpdate update) {
    OrderStateMachine stateMachine = getStateMachine(orderId);
    long lastSeqNum = stateMachine.getLastProcessedSequence();
    
    if (sequenceNumber <= lastSeqNum) {
        // Duplicate or out-of-order, skip
        logger.debug("Skipping duplicate/old sequence {} for order {}", sequenceNumber, orderId);
        return;
    }
    
    // Process update
    applyUpdate(stateMachine, update);
    stateMachine.setLastProcessedSequence(sequenceNumber);
}
```

**Benefits:**
- Exact deduplication (no false positives)
- Detects out-of-order delivery
- Minimal memory overhead (one counter per order)

### 3. Ordering Guarantees

#### Per-Order Ordering
- All events for a single order are processed in sequence order
- Uses sequence numbers assigned at order ingestion
- Out-of-order events are buffered and reordered

**Guarantee:** For a given `InternalOrderId`, events with sequence numbers `s1 < s2 < s3` will be applied to the state machine in that order, even if they arrive out-of-order.

**Implementation:**
```java
class OrderEventProcessor {
    // Buffer for out-of-order events
    private final Map<InternalOrderId, PriorityQueue<OrderEvent>> pendingEvents;
    private final Map<InternalOrderId, Long> expectedSequence;
    
    public void processEvent(OrderEvent event) {
        InternalOrderId orderId = event.getInternalOrderId();
        long expectedSeq = expectedSequence.getOrDefault(orderId, 1L);
        
        if (event.getSequenceNumber() == expectedSeq) {
            // In order, process immediately
            applyEvent(event);
            expectedSequence.put(orderId, expectedSeq + 1);
            
            // Process any buffered events that are now in order
            processBufferedEvents(orderId);
        } else if (event.getSequenceNumber() > expectedSeq) {
            // Future event, buffer it
            pendingEvents.computeIfAbsent(orderId, k -> new PriorityQueue<>())
                         .add(event);
        } else {
            // Old event, ignore (already processed)
            logger.debug("Ignoring old event seq={} for order {}", 
                event.getSequenceNumber(), orderId);
        }
    }
}
```

**Benefits:**
- Guarantees deterministic state evolution
- Handles network reordering automatically
- Essential for distributed systems

#### Global Ordering (Optional)
For audit and replay purposes, maintain a global sequence of all events across all orders.

**Implementation:**
- Use a distributed sequence generator (e.g., Snowflake ID)
- Events are tagged with both local (per-order) and global sequence numbers
- Global log stores events in global sequence order for replay

**Use Cases:**
- Full system state reconstruction
- Compliance reporting
- Performance benchmarking and simulation

### 4. Consistency Guarantees

#### Single-Writer Per Order
- Each order is owned by a single OMS instance at any time
- Prevents concurrent state modifications from multiple processes
- Uses distributed locking (e.g., ZooKeeper, etcd) in distributed deployments

**Benefits:**
- Eliminates race conditions
- Simplifies state machine logic
- Ensures linearizable operations

#### State Machine Determinism
- State transitions are purely deterministic functions of current state and event
- No non-deterministic operations (e.g., `System.currentTimeMillis()` for decisions)
- Timestamps are attached to events, not generated during processing

**Example:**
```java
// CORRECT: Deterministic transition
public void applyFill(OrderStateMachine sm, FillEvent event) {
    if (event.getFillQuantity() == sm.getRemainingQuantity()) {
        sm.transition(OrderState.FILLED);
    } else {
        sm.transition(OrderState.PARTIAL_FILL);
    }
}

// INCORRECT: Non-deterministic (uses current time)
public void checkExpiry(OrderStateMachine sm) {
    if (System.currentTimeMillis() > sm.getExpiryTime()) {  // ❌ Non-deterministic
        sm.transition(OrderState.EXPIRED);
    }
}
```

### 5. Failure Recovery

#### Event Sourcing
- All state changes are derived from events
- Events are stored in an append-only log
- State can be reconstructed by replaying events

**Recovery Process:**
1. Load latest checkpoint (snapshot of state at sequence N)
2. Replay events from sequence N+1 to latest
3. Rebuild in-memory indexes (ClientOrderId → InternalOrderId)
4. Resume processing

**Benefits:**
- Complete audit trail
- Deterministic recovery
- Time-travel queries (state at any point in time)

#### Checkpoint Strategy
- Periodic snapshots of order state (every 100K orders or 5 minutes)
- Snapshots stored in compressed format
- Old snapshots garbage collected (retain last 7 days)

**Implementation:**
```java
public void createCheckpoint() {
    long currentSequence = globalSequenceNumber.get();
    
    // Serialize all order states
    Map<InternalOrderId, OrderState> snapshot = orderStateMachines.entrySet()
        .stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> e.getValue().getCurrentState()
        ));
    
    // Persist snapshot
    checkpointStore.save(currentSequence, snapshot);
    
    logger.info("Checkpoint created at sequence {}", currentSequence);
}
```

## Consequences

### Positive
- **Strong consistency**: Exactly-once order processing semantics
- **High reliability**: Graceful recovery from failures
- **Auditability**: Complete history of all order state changes
- **Performance**: Idempotency checks have minimal overhead (< 1µs)
- **Scalability**: Design supports horizontal scaling with partitioning

### Negative
- **Complexity**: Additional logic for deduplication and ordering
- **Memory**: Requires memory for bloom filters and pending event buffers
- **Latency**: Buffering out-of-order events adds slight latency (< 10ms typically)
- **Storage**: Event log and checkpoints require disk space

### Mitigation Strategies
1. **Bloom filter tuning**: Adjust size and false positive rate based on traffic
2. **Partitioning**: Partition orders by symbol or account for parallelism
3. **Garbage collection**: Aggressively clean up old events and checkpoints
4. **Monitoring**: Alert on high buffer depths (indicates ordering issues)

## Performance Targets

| Operation | Target | Notes |
|-----------|--------|-------|
| Idempotency check | < 1 µs | Hash map lookup |
| Deduplication check | < 1 µs | Bloom filter check |
| Sequence ordering | < 5 µs | Priority queue operations |
| Checkpoint creation | < 100 ms | Every 100K orders |
| Recovery time | < 30 sec | For 10M orders |

## Validation

### Unit Tests
- ✅ Idempotent state transitions return false
- ✅ Duplicate client order IDs are rejected
- ✅ Out-of-order events are buffered and reordered
- ✅ Sequence gaps trigger waiting for missing events
- ✅ State machine evolves deterministically

### Integration Tests
- Simulate network retries and verify no duplicate orders
- Inject out-of-order events and verify correct final state
- Crash and restart system, verify full recovery
- Replay historical events and verify bit-for-bit identical state

### Load Tests
- Submit 1M orders with 10% duplicate rate, verify correct handling
- Inject 5% out-of-order events, measure latency impact
- Measure checkpoint and recovery times at scale

## Implementation Plan

### Phase 1: Core Idempotency (Week 1)
- Implement ClientOrderId index
- Add idempotent state transitions
- Unit tests for idempotency

### Phase 2: Deduplication (Week 2)
- Implement bloom filter for event deduplication
- Add sequence number tracking per order
- Integration tests

### Phase 3: Ordering Guarantees (Week 3)
- Implement per-order event ordering
- Add buffering for out-of-order events
- Load tests with reordering

### Phase 4: Recovery (Week 4)
- Implement checkpointing
- Build recovery process
- Disaster recovery tests

## References

- [Event Sourcing Pattern](https://martinfowler.com/eaaDev/EventSourcing.html)
- [Idempotency Patterns](https://blog.jonathanoliver.com/idempotency-patterns/)
- [Exactly-Once Semantics](https://www.confluent.io/blog/exactly-once-semantics-are-possible-heres-how-apache-kafka-does-it/)
- [Distributed Sequence Generation](https://en.wikipedia.org/wiki/Snowflake_ID)

## Notes

- This ADR supersedes any previous informal idempotency guarantees
- Idempotency is a requirement for production deployment
- Team should review and sign off before implementation

---

**Date**: 2024-10-12  
**Author**: The Last War Architecture Team  
**Reviewers**: [To be assigned]
