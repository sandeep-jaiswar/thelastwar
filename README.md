# The Last War - Trading System

## 📘 Overview
This repository hosts a high-performance trading system designed for **equities, debt, bonds, futures, and options** trading.  
Our objective is to build one of the **lowest-latency and most deterministic** trading systems in the investment banking domain — capable of handling **millions of events per second** with **sub–10 µs end-to-end latency**.

The architecture is **event-driven, memory-resident, and horizontally sharded**, enabling deterministic replay, state recovery, and cross-asset scalability.

---

## 🧩 Architecture Summary

### Core Domains
| Layer | Description |
|-------|--------------|
| **Low-Latency Layer (Colo/Bare-metal)** | Feed Handlers, FIX Gateways, Matching Engines, and Smart Order Router. |
| **Core Event Fabric** | Ultra-fast messaging backbone (Aeron + Chronicle Queue + Kafka). |
| **Real-Time Services (Kubernetes)** | OMS, Risk, Ledger, Positions, and Surveillance microservices. |
| **Post-Trade & Reconciliation** | Clearing, Settlement, Accounting, and Reg Reporting adapters. |
| **Data Platform** | Tick Store, OLAP/Analytics, Historical Replay. |
| **Infra & Observability** | Auth, Secrets, Monitoring, and CI/CD pipelines. |

### Performance Targets
| Metric | Target |
|---------|--------|
| Matching Engine | < 5 µs p99 per match |
| Event Bus | < 10 µs round-trip |
| Pre-Trade Risk | < 10 µs inline check |
| Order Lifecycle | < 50 µs ingress → execution |
| Throughput | > 2 million msgs/sec |
| Replay Determinism | Bit-for-bit identical state |

---

## ⚙️ Technology Stack

### Language & Runtime
- **Java 25** (primary language)
- **Kotlin** (utility & DSL modules)
- **JMH / Chronicle Benchmark** (micro-benchmarks)

### Messaging & Data
- **Aeron** / **Chronicle Queue** (low-latency transport)
- **Kafka** / **Redpanda** (persistent event log)
- **Chronicle Map** (off-heap cache)
- **PostgreSQL / ClickHouse** (persistence)
- **Avro / Protobuf** (schema evolution)

### Build & Dependency Management
- **Gradle 8.8+ (Kotlin DSL)**  
  ```bash
  ./gradlew build
  ./gradlew test
  ./gradlew jmh

  Monorepo managed using Gradle Composite Builds

Common build logic defined in /buildSrc/

Containerization & Deployment

Docker + Kubernetes for runtime services.

Helm Charts for deployment automation.

GitHub Actions for CI/CD.

SonarQube + PMD + SpotBugs for static analysis.

🧠 Development Principles
Low-Latency Design Rules

No heap allocations in hot path (use object pools / off-heap).

Single-writer principle: each order book runs on one dedicated thread.

Immutable data flow: all events append-only; replay must be deterministic.

CPU affinity pinning: pin critical threads to dedicated cores.

Mechanical sympathy: leverage NUMA-awareness and lock-free structures.

GC avoidance: prefer structs/records, off-heap buffers, and pre-sized arrays.

Deterministic replay: event log acts as system-of-record.

Coding Standards

Java 25 with strict compiler flags (-Xlint:all -Werror).

Static analysis via PMD, SpotBugs, and ErrorProne.

Unit + latency benchmark for each module.

All PRs reviewed against .github/CODE_REVIEW.md.

🧪 Testing & Benchmarking
Unit / Integration Tests
./gradlew test


Uses JUnit 5 and Mockito.

Latency assertions enforced via LatencyAssert utility.

Performance Tests
./gradlew jmh


JMH benchmarks for each subsystem (EventBus, LOB, Risk, etc.).

Generates build/reports/benchmarks/ with latency histograms.

Determinism Tests
./gradlew replayTest


Validates that replayed events produce identical results.

🧰 Repository Structure
multi-asset-trading-platform/
├── core/
│   ├── eventbus/               # Aeron + Chronicle abstraction
│   ├── matching-engine/        # LOB + Matching logic
│   ├── risk/                   # Inline pre-trade risk engine
│   ├── ledger/                 # Trade capture + positions
│   └── cache/                  # Off-heap predictive cache
├── infra/
│   ├── kafka/                  # Kafka setup + schemas
│   ├── prometheus/             # Monitoring
│   └── ci-cd/                  # Build + deployment scripts
├── ui/
│   ├── trader-dashboard/       # WebSocket + REST dashboard
│   └── analytics/              # Historical analytics UI
├── docs/
│   └── architecture/           # UML diagrams, ADRs, benchmarks
└── .github/
    ├── PULL_REQUEST_TEMPLATE.md
    ├── CODE_REVIEW.md
    ├── COPILOT_INSTRUCTIONS.md
    └── workflows/

🧭 Local Setup
Prerequisites

JDK 25+

Docker & Docker Compose

Gradle Wrapper (./gradlew)

Optional: Linux (low-latency tuned kernel recommended)

Run Locally
docker-compose up
./gradlew run


Access:

Trader UI → http://localhost:8080

Grafana → http://localhost:3000

Prometheus → http://localhost:9090

🤖 Copilot Guidance

Copilot is configured through .github/COPILOT_INSTRUCTIONS.md
Key goals for Copilot:

Generate lock-free, allocation-free Java code.

Prefer mechanical sympathy patterns (LMAX Disruptor, Aeron, Chronicle).

Optimize for microsecond-level latency.

Use records, varhandles, and non-blocking algorithms.

🧑‍💻 Contributing

Fork the repo and clone locally.

Run ./gradlew clean build.

Push feature branch → open PR.

All PRs must include:

Unit tests.

JMH benchmarks.

Performance regression comparison.


## Documentation

- [Event Bus README](docs/EVENT_BUS_README.md)
- [Performance Tuning Guide](docs/PERFORMANCE_TUNING.md) - Comprehensive tuning for ultra-low latency
- [Benchmark Guide](docs/BENCHMARK_GUIDE.md) - Running and interpreting performance benchmarks
- [Architecture Decision Records](docs/adr/)
- [UML Diagrams](docs/uml/)

## License

Copyright © 2024 The Last War. All rights reserved.
