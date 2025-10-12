# OMS (Order Management System) Core Module

## Overview

The OMS core module implements the domain model and deterministic state machine for order lifecycle management in The Last War trading system. It provides the canonical source of truth for order state and enforces strict state transition rules.

## Components

### Domain Model

#### 1. ClientOrderId
Value object representing a client-assigned order identifier.

```java
ClientOrderId clientId = ClientOrderId.of("ORDER-12345");
```

**Properties:**
- Immutable
- Alphanumeric with hyphens and underscores
- Max 64 characters
- Used for idempotency and client tracking

#### 2. InternalOrderId
Value object representing a system-assigned order identifier.

```java
InternalOrderId internalId = InternalOrderId.of(12345L);
InternalOrderId nextId = internalId.next();
```

**Properties:**
- Immutable
- Positive long value
- Monotonically increasing (in some ID schemes)
- Comparable and ordered

#### 3. OrderState
Enum defining all possible order states.

```java
public enum OrderState {
    NEW,           // Order received
    ACCEPTED,      // Order validated
    WORKING,       // Order active in book
    PARTIAL_FILL,  // Partially executed
    FILLED,        // Completely executed (terminal)
    CANCELLED,     // Cancelled (terminal)
    REJECTED,      // Rejected (terminal)
    EXPIRED        // Expired (terminal)
}
```

**State Categories:**
- **Initial:** NEW
- **Active:** ACCEPTED, WORKING, PARTIAL_FILL
- **Terminal:** FILLED, CANCELLED, REJECTED, EXPIRED

#### 4. OrderTransition
Enum defining all valid state transitions.

```java
// Valid transitions
NEW_TO_ACCEPTED
NEW_TO_REJECTED
ACCEPTED_TO_WORKING
WORKING_TO_PARTIAL_FILL
WORKING_TO_FILLED
PARTIAL_FILL_TO_FILLED
// ... and more
```

#### 5. OrderStateMachine
Deterministic state machine enforcing order lifecycle rules.

```java
OrderStateMachine stateMachine = new OrderStateMachine(orderId);

// Transition to new state
boolean transitioned = stateMachine.transition(OrderState.ACCEPTED);

// Check current state
OrderState current = stateMachine.getCurrentState();
boolean isTerminal = stateMachine.isTerminal();
```

**Features:**
- Deterministic state transitions
- Idempotency support (transitioning to current state is safe)
- Thread-safe
- State history tracking
- Invalid transition rejection

#### 6. Allocation
Domain model representing a fill execution.

```java
Allocation allocation = new Allocation(
    allocationId,
    orderId,
    executionId,
    fillPrice,
    fillQuantity,
    fillTimestamp,
    "NASDAQ",
    counterpartyOrderId
);
```

**Properties:**
- Immutable record of execution
- Links order to execution
- Audit trail for fills

## State Machine Diagram

See [OMS Order State Machine](../../docs/uml/oms-order-state-machine.puml) for complete state transition diagram.

### Valid Transitions

```
NEW → ACCEPTED → WORKING → PARTIAL_FILL → FILLED
                     ↓           ↓
                 CANCELLED   CANCELLED
                     ↓           ↓
                 EXPIRED     EXPIRED
  ↓
REJECTED
```

### Invalid Transitions (Examples)

- NEW → WORKING (must go through ACCEPTED)
- NEW → FILLED (must be accepted and working first)
- ACCEPTED → FILLED (must be working first)
- Any transition from terminal states (FILLED, CANCELLED, REJECTED, EXPIRED)

## Usage Examples

### Basic Order Lifecycle

```java
// Create order with IDs
InternalOrderId internalId = InternalOrderId.of(12345L);
ClientOrderId clientId = ClientOrderId.of("CLIENT-ORDER-001");

// Initialize state machine
OrderStateMachine stateMachine = new OrderStateMachine(internalId);

// Order accepted
stateMachine.transition(OrderState.ACCEPTED);

// Order working
stateMachine.transition(OrderState.WORKING);

// Partial fill
stateMachine.transition(OrderState.PARTIAL_FILL);

// Complete fill
stateMachine.transition(OrderState.FILLED);

// Check final state
assert stateMachine.isTerminal();
assert stateMachine.isFilled();
```

### Idempotent Processing

```java
OrderStateMachine stateMachine = new OrderStateMachine(orderId);

// First transition
boolean transitioned1 = stateMachine.transition(OrderState.ACCEPTED);
assert transitioned1 == true;

// Duplicate transition (idempotent)
boolean transitioned2 = stateMachine.transition(OrderState.ACCEPTED);
assert transitioned2 == false;  // No-op, already in ACCEPTED state
```

### Invalid Transition Handling

```java
OrderStateMachine stateMachine = new OrderStateMachine(orderId);

try {
    // Invalid: cannot go directly from NEW to WORKING
    stateMachine.transition(OrderState.WORKING);
} catch (OrderStateMachine.IllegalStateTransitionException e) {
    // Handle invalid transition
    logger.error("Invalid state transition: {}", e.getMessage());
}
```

### Tracking State History

```java
OrderStateMachine stateMachine = new OrderStateMachine(orderId);

stateMachine.transition(OrderState.ACCEPTED);
stateMachine.transition(OrderState.WORKING);

// Check if order has been in specific states
assert stateMachine.hasBeenInState(OrderState.NEW);
assert stateMachine.hasBeenInState(OrderState.ACCEPTED);
assert !stateMachine.hasBeenInState(OrderState.FILLED);

// Get timestamp when order entered a state
Long acceptedTime = stateMachine.getStateTimestamp(OrderState.ACCEPTED);
```

## Schemas

The OMS module provides schema definitions in multiple formats for interoperability:

### JSON Schema
- Location: `/platform/schemas/json/`
- Files: `Order.schema.json`, `OrderEvent.schema.json`
- Use: REST APIs, validation

### Avro Schema
- Location: `/platform/schemas/avro/`
- Files: `Order.avsc`, `OrderEvent.avsc`
- Use: Kafka topics, schema registry

### Protobuf Schema
- Location: `/platform/schemas/protobuf/`
- Files: `Order.proto`, `OrderEvent.proto`
- Use: gRPC, binary serialization

## Design Principles

### 1. Determinism
Same sequence of events always produces the same final state. No non-deterministic operations in state transitions.

### 2. Immutability
Domain objects (IDs, allocations) are immutable for thread-safety and simplicity.

### 3. Idempotency
Duplicate events are handled gracefully. Transitioning to the current state is a no-op.

### 4. Type Safety
Strong typing prevents invalid operations at compile time (e.g., cannot compare ClientOrderId with InternalOrderId).

### 5. Explicit Transitions
All valid transitions are explicitly enumerated in OrderTransition enum. Invalid transitions are rejected.

## Guarantees

### Idempotency
- Duplicate state transitions to current state are safe
- ClientOrderId provides deduplication key
- See [ADR-002](../../docs/adr/002-oms-idempotency-and-ordering.md) for details

### Ordering
- Events for a single order are processed in sequence
- Out-of-order events are detected and handled
- See [ADR-002](../../docs/adr/002-oms-idempotency-and-ordering.md) for details

### Thread-Safety
- OrderStateMachine is thread-safe with synchronized transitions
- Value objects are immutable (naturally thread-safe)

## Testing

### Unit Tests
Run all unit tests:
```bash
./gradlew :core:oms:test
```

**Test Coverage:**
- ✅ All valid state transitions
- ✅ Invalid transition rejection
- ✅ Idempotency guarantees
- ✅ Terminal state protection
- ✅ ID validation and constraints
- ✅ Allocation validation

### Test Files
- `OrderStateMachineTest.java` - 30+ tests for state machine
- `ClientOrderIdTest.java` - ID validation and constraints
- `InternalOrderIdTest.java` - ID operations and comparison
- `AllocationTest.java` - Allocation domain model

## Performance Characteristics

| Operation | Target | Notes |
|-----------|--------|-------|
| State transition | < 1 µs | O(1) enum lookup |
| Idempotency check | < 1 µs | Hash map lookup |
| State history lookup | < 100 ns | ConcurrentHashMap |
| ID validation | < 100 ns | Primitive comparison |

## Integration

### Dependencies
```kotlin
dependencies {
    api(project(":core:eventbus"))
    api(project(":core:orderbook"))
}
```

### Usage in Other Modules
```java
// In OMS service
import com.thelastwar.oms.*;

OrderStateMachine stateMachine = new OrderStateMachine(orderId);
// ... use state machine
```

## Documentation

- **State Machine Diagram:** [docs/uml/oms-order-state-machine.puml](../../docs/uml/oms-order-state-machine.puml)
- **ADR - Idempotency:** [docs/adr/002-oms-idempotency-and-ordering.md](../../docs/adr/002-oms-idempotency-and-ordering.md)
- **Schemas:** [platform/schemas/](../../platform/schemas/)

## Future Enhancements

1. **Event Sourcing Integration**
   - Store all state transitions as events
   - Support event replay for audit

2. **Distributed Locking**
   - Single-writer per order in distributed deployments
   - ZooKeeper/etcd integration

3. **Advanced Analytics**
   - State transition timing histograms
   - Bottleneck identification

4. **Schema Evolution**
   - Versioned schemas with backward compatibility
   - Migration tools for schema upgrades

## References

- [Event Sourcing Pattern](https://martinfowler.com/eaaDev/EventSourcing.html)
- [State Machine Pattern](https://en.wikipedia.org/wiki/State_pattern)
- [Domain-Driven Design](https://martinfowler.com/tags/domain%20driven%20design.html)

---

**Module:** core:oms  
**Version:** 1.0.0-SNAPSHOT  
**Java:** 25  
**Build:** Gradle 9.1.0
