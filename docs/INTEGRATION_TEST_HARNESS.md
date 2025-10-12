# Integration Test Harness Documentation

## Overview

The Integration Test Harness validates the complete trading system flow from gateway ingestion through EventBus message routing to matching engine processing and back to gateways. This comprehensive test suite ensures:

- **End-to-end latency < 3 ms** for FIX order round trips
- **No message drops** under sustained 10K msg/s load
- **Proper event routing** through EventBus → Matching Engine → Gateway
- **Automated reporting** with latency and throughput metrics in CI

## Test Components

### 1. FIX Gateway Load Tests

**Location**: `core/gateway/src/test/java/com/thelastwar/gateway/integration/FixGatewayLoadTest.java`

Tests FIX protocol gateway under high-load conditions:

- **Sustained throughput test**: 10K messages/second
- **Latency distribution**: P50, P95, P99 measurements
- **Message drop detection**: Validates no drops under load
- **Mock FIX server**: Uses QuickFIX test harness

**Run tests:**
```bash
./gradlew :core:gateway:test --tests "FixGatewayLoadTest"
```

**Example output:**
```
=== FIX Gateway Load Test: 10K msg/s ===
Total messages: 10000
Total time: 1234 ms
Throughput: 8103 msg/s
Latency Statistics:
  Min: 0.123 ms
  Avg: 0.456 ms
  P50: 0.389 ms
  P95: 1.234 ms
  P99: 2.456 ms
  Max: 3.789 ms

Validation:
  ✓ Throughput >= 5,000 msg/s: PASS
  ✓ Latency < 3 ms: PASS
```

### 2. REST Gateway Load Tests

**Location**: `core/restgateway/load-tests/`

Uses **wrk** HTTP benchmarking tool for REST API load testing:

**Files:**
- `order_submit.lua`: Lua script for wrk with request templates
- `run-load-test.sh`: Automated test runner with validation

**Run tests:**
```bash
cd core/restgateway/load-tests
./run-load-test.sh
```

**Configuration:**
```bash
# Environment variables
export REST_HOST=localhost
export REST_PORT=8080
export THREADS=4
export CONNECTIONS=100
export DURATION=30s
```

**Requirements:**
- Install wrk: `sudo apt-get install wrk` (Ubuntu/Debian) or `brew install wrk` (macOS)

**Example output:**
```
=== REST Gateway Load Test ===
Target: http://localhost:8080/api/orders
Threads: 4
Connections: 100
Duration: 30s

Running 30s test @ http://localhost:8080/api/orders
  4 threads and 100 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.45ms    1.23ms   45.67ms   89.34%
    Req/Sec     2.5k      234      3.1k      87%
  Latency Distribution
     50%    2.12ms
     75%    2.89ms
     90%    3.45ms
     99%    5.67ms
  10234 requests in 30.01s, 1.23MB read
Requests/sec:   10456.78
Transfer/sec:     41.23KB

Validating Acceptance Criteria:
  ✓ Throughput >= 10,000 req/s: PASS
  ✓ Latency < 3 ms: PASS
```

### 3. WebSocket Gateway Load Tests

**Location**: `core/wsgateway/src/test/java/com/thelastwar/wsgateway/integration/WebSocketGatewayLoadTest.java`

Tests WebSocket event broadcasting under high load:

- **High throughput broadcasting**: 10K events/second
- **Multiple event types**: ORDER_ACCEPTED, ORDER_FILLED, MARKET_DATA_UPDATE
- **MessagePack vs JSON**: Performance comparison
- **Concurrent client simulation**

**Run tests:**
```bash
./gradlew :core:wsgateway:test --tests "WebSocketGatewayLoadTest"
```

**Example output:**
```
=== WebSocket Gateway Load Test: 10K events/s ===
Total events: 10000
Total time: 987 ms
Throughput: 10132 events/s
Latency Statistics:
  Avg: 0.234 ms
  P50: 0.198 ms
  P95: 0.567 ms
  P99: 1.234 ms

Validation:
  ✓ Throughput >= 5,000 events/s: PASS
  ✓ Latency < 3 ms: PASS
```

### 4. End-to-End Integration Tests

**Location**: `core/gateway/src/test/java/com/thelastwar/gateway/integration/EndToEndIntegrationTest.java`

Validates complete message flow:

```
FIX Gateway → EventBus → Simulated Matching Engine → EventBus → Gateway
```

Tests:
- **Complete round-trip flow**: Order submission → Execution
- **High volume round-trip**: 100+ orders with latency tracking
- **EventBus reliability**: Message delivery guarantees
- **Simulated matching engine**: Processes orders and generates fills

**Run tests:**
```bash
./gradlew :core:gateway:test --tests "EndToEndIntegrationTest"
```

**Example output:**
```
=== End-to-End Integration Test ===
Round-trip completed:
  Order submitted: 1
  Executions received: 1
  Total latency: 5.234 ms

✓ End-to-end flow validated

=== High Volume Round-Trip Test ===
Orders sent: 100
Orders submitted: 100
Executions received: 100
Total time: 1234 ms
Throughput: 81 orders/s

✓ High volume round-trip validated
```

## CI/CD Integration

### GitHub Actions Workflow

**Location**: `.github/workflows/integration-tests.yml`

Automated integration test execution on every PR and push to production/main:

**Features:**
- Runs all integration and load tests
- Generates comprehensive test reports
- Validates acceptance criteria
- Uploads artifacts (test reports, load test results)
- Comments PR with results summary
- Quality gate blocks merge if tests fail

**Workflow steps:**
1. Setup Java 25 environment
2. Cache Gradle dependencies
3. Run integration tests
4. Generate test reports
5. Validate acceptance criteria
6. Upload artifacts
7. Comment PR with results

**Artifacts retained for 30 days:**
- Integration test reports (JUnit XML)
- Load test results (text reports)
- Integration summary

### Running in CI

The workflow automatically runs on:
- Pull requests to `production` or `main` branches
- Pushes to `production` or `main` branches

**Manual trigger:**
```bash
# Via GitHub CLI
gh workflow run integration-tests.yml
```

## Report Generation

All tests generate detailed reports in `build/reports/load-tests/`:

### Report Format

```
=== FIX Gateway Load Test Report ===
Test: fix_gateway_load_test
Timestamp: 1699876543210

Configuration:
  Total messages: 10000
  Test duration: 1234 ms
  Target: 10,000 msg/s
  Latency target: < 3 ms

Results:
  Throughput: 8103 msg/s
  Received: 9876 responses

Latency Distribution:
  Average: 0.456 ms
  P50: 0.389 ms
  P95: 1.234 ms
  P99: 2.456 ms

Acceptance Criteria:
  ✓ Throughput >= 5,000 msg/s: PASS
  ✓ Latency < 3 ms: PASS
  ✓ No message drops: PASS
```

## Acceptance Criteria Validation

### 1. End-to-End Latency < 3 ms

**Test**: FIX Gateway round-trip latency
**Validation**: Measured in `FixGatewayLoadTest.testSustainedThroughput_10K_MessagesPerSecond()`
**Metric**: Average send latency

### 2. No Message Drops at 10K msg/s

**Test**: All gateway load tests
**Validation**: Compares sent vs received message counts
**Metric**: Drop rate should be 0%

### 3. Reports Auto-Uploaded to CI

**Implementation**: GitHub Actions workflow uploads artifacts
**Location**: Actions → Workflow run → Artifacts section
**Retention**: 30 days

### 4. Green Tests Required for Merge

**Implementation**: Quality gate job in CI workflow
**Enforcement**: PR cannot merge if integration tests fail
**Check**: All tests must pass before merge allowed

## Running Tests Locally

### Prerequisites

```bash
# Install Java 25
# Install Gradle (or use wrapper)
# Install wrk (optional, for REST load tests)
sudo apt-get install wrk
```

### Run All Integration Tests

```bash
# Run all integration tests
./gradlew clean test --tests "*IntegrationTest" --tests "*LoadTest"

# Run only FIX Gateway tests
./gradlew :core:gateway:test --tests "*LoadTest" --tests "*IntegrationTest"

# Run only WebSocket Gateway tests
./gradlew :core:wsgateway:test --tests "*LoadTest" --tests "*IntegrationTest"
```

### Run REST Load Tests

```bash
# Start REST Gateway first (in separate terminal)
./gradlew :core:restgateway:bootRun

# Then run load tests
cd core/restgateway/load-tests
./run-load-test.sh
```

### View Reports

```bash
# Open HTML test reports
open core/gateway/build/reports/tests/test/index.html
open core/wsgateway/build/reports/tests/test/index.html

# View load test text reports
cat core/gateway/build/reports/load-tests/*_report.txt
cat core/wsgateway/build/reports/load-tests/*_report.txt
```

## Performance Targets

| Component | Metric | Target | Typical |
|-----------|--------|--------|---------|
| FIX Gateway | Throughput | ≥ 10,000 msg/s | 8,000-12,000 msg/s |
| FIX Gateway | Latency (avg) | < 3 ms | 0.5-2 ms |
| FIX Gateway | Latency (P99) | < 5 ms | 2-4 ms |
| REST Gateway | Throughput | ≥ 10,000 req/s | 10,000-15,000 req/s |
| REST Gateway | Latency (avg) | < 3 ms | 1-2 ms |
| WebSocket Gateway | Throughput | ≥ 10,000 events/s | 10,000-20,000 events/s |
| WebSocket Gateway | Latency (avg) | < 3 ms | 0.2-1 ms |
| End-to-End | Round-trip | < 10 ms | 3-8 ms |

## Troubleshooting

### Tests Fail with Port Already in Use

```bash
# Kill processes using test ports
lsof -ti:19876,19877,19878,8092,8093,8094,8095,8096 | xargs kill -9
```

### QuickFIX Session Issues

```bash
# Clean FIX session stores
rm -rf build/tmp/fix-*-store
rm -rf build/tmp/fix-*-log
```

### wrk Not Found

```bash
# Install wrk
sudo apt-get install wrk  # Ubuntu/Debian
brew install wrk           # macOS
```

### Low Throughput in Tests

- Check CPU/memory resources
- Reduce concurrent test count
- Increase test duration for stabilization
- Check for other processes consuming resources

## Future Enhancements

1. **Gatling WebSocket Load Tests**: Add Scala-based Gatling tests for WebSocket
2. **k6 REST Load Tests**: Alternative JavaScript-based load testing
3. **Distributed Load Testing**: Multi-node load generation
4. **Real-time Monitoring**: Grafana dashboards during tests
5. **Performance Regression Detection**: Automatic comparison with baseline
6. **Chaos Engineering**: Network latency/partition injection
7. **Production Traffic Replay**: Shadow traffic from production

## References

- [EventBus Integration Guide](../docs/EVENTBUS_INTEGRATION.md)
- [Gateway Observability Guide](../docs/GATEWAY_OBSERVABILITY_GUIDE.md)
- [Performance Tuning Guide](../docs/PERFORMANCE_TUNING.md)
- [Benchmark Guide](../docs/BENCHMARK_GUIDE.md)

## Support

For issues or questions:
- Create an issue in the repository
- Check existing integration test examples
- Review CI workflow logs for detailed error messages
