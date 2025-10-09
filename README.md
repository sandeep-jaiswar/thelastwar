# The Last War - Trading System

A high-performance, ultra-low latency trading system built with modern Java.

## Project Structure

- **Event Bus** - Core abstraction layer for decoupling subsystems ([Documentation](docs/EVENT_BUS_README.md))
  - Feed Handler
  - Matching Engine
  - Risk Manager
  - OMS (Order Management System)
  - Analytics

## Components

### Event Bus Abstraction Layer

The Event Bus provides a GC-neutral publish/subscribe mechanism with sub-5 microsecond latency.

**Key Features:**
- Ultra-low latency (< 5µs publish/subscribe)
- GC-neutral design (no autoboxing, minimal allocations)
- Type-safe interfaces
- Flexible implementation strategy (Aeron, Chronicle Queue, or custom)

See [Event Bus Documentation](docs/EVENT_BUS_README.md) for detailed usage and architecture.

## Building

```bash
mvn clean compile
```

## Testing

```bash
mvn test
```

## Documentation

- [Event Bus README](docs/EVENT_BUS_README.md)
- [Architecture Decision Records](docs/adr/)
- [UML Diagrams](docs/uml/)

## License

Copyright © 2024 The Last War. All rights reserved.