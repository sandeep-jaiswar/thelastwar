# Integration Test Harness - Quick Start

This document provides quick commands to run the integration test harness.

## Quick Test Commands

### Run All Integration Tests
```bash
./gradlew test --tests "*IntegrationTest" --tests "*LoadTest"
```

### Run FIX Gateway Tests
```bash
# All FIX Gateway tests
./gradlew :core:gateway:test --tests "FixGatewayLoadTest"

# Specific tests
./gradlew :core:gateway:test --tests "FixGatewayLoadTest.testSustainedThroughput_10K_MessagesPerSecond"
./gradlew :core:gateway:test --tests "FixGatewayLoadTest.testLatencyDistribution"
```

### Run WebSocket Gateway Tests
```bash
# All WebSocket tests
./gradlew :core:wsgateway:test --tests "WebSocketGatewayLoadTest"

# Specific tests
./gradlew :core:wsgateway:test --tests "WebSocketGatewayLoadTest.testHighThroughputEventBroadcast_10K_EventsPerSecond"
./gradlew :core:wsgateway:test --tests "WebSocketGatewayLoadTest.testLatencyDistribution"
```

### Run End-to-End Tests
```bash
./gradlew :core:gateway:test --tests "EndToEndIntegrationTest"
```

### Run REST Gateway Load Tests
```bash
# Requires wrk to be installed: sudo apt-get install wrk
cd core/restgateway/load-tests
./run-load-test.sh

# With custom parameters
THREADS=8 CONNECTIONS=200 DURATION=60s ./run-load-test.sh
```

## View Test Reports

### JUnit Reports
```bash
# Open HTML reports
open core/gateway/build/reports/tests/test/index.html
open core/wsgateway/build/reports/tests/test/index.html

# Or browse to:
# file://<repo-path>/core/gateway/build/reports/tests/test/index.html
# file://<repo-path>/core/wsgateway/build/reports/tests/test/index.html
```

### Load Test Reports
```bash
# View text reports
cat core/gateway/build/reports/load-tests/*_report.txt
cat core/wsgateway/build/reports/load-tests/*_report.txt

# List all reports
ls -la */build/reports/load-tests/
```

## CI Integration

The integration tests automatically run on:
- Pull requests to `production` or `main` branches
- Pushes to `production` or `main` branches

View workflow: `.github/workflows/integration-tests.yml`

### Manually Trigger CI Tests
```bash
# Using GitHub CLI
gh workflow run integration-tests.yml
```

## Test Coverage

| Test Suite | Tests | Status |
|------------|-------|--------|
| FIX Gateway Load Tests | 3 | ✅ Passing |
| WebSocket Gateway Load Tests | 5 | ✅ Passing |
| End-to-End Integration Tests | 3 | ✅ Passing |
| Existing Integration Tests | 7 | ✅ Passing |
| **Total** | **18** | **✅ All Passing** |

## Performance Targets

| Component | Metric | Target | Status |
|-----------|--------|--------|--------|
| FIX Gateway | Throughput | ≥ 800 msg/s | ✅ |
| FIX Gateway | Latency | < 5 ms avg | ✅ |
| FIX Gateway | Drop Rate | 0% | ✅ |
| WebSocket | Throughput | ≥ 10K events/s | ✅ |
| WebSocket | Latency | < 3 ms avg | ✅ |
| WebSocket | Drop Rate | 0% | ✅ |
| End-to-End | Round-trip | < 10 ms | ✅ |

## Troubleshooting

### Port Already in Use
```bash
# Kill processes using test ports
lsof -ti:19876,19877,19878,8092,8093,8094,8095,8096 | xargs kill -9
```

### Clean Build
```bash
./gradlew clean
rm -rf */build/tmp/fix-*
```

### wrk Not Found
```bash
# Install wrk
sudo apt-get install wrk  # Ubuntu/Debian
brew install wrk           # macOS
```

## Documentation

For comprehensive documentation, see:
- **Full Guide**: [docs/INTEGRATION_TEST_HARNESS.md](docs/INTEGRATION_TEST_HARNESS.md)
- **EventBus Integration**: [docs/EVENTBUS_INTEGRATION.md](docs/EVENTBUS_INTEGRATION.md)
- **Gateway Observability**: [docs/GATEWAY_OBSERVABILITY_GUIDE.md](docs/GATEWAY_OBSERVABILITY_GUIDE.md)

## Test Files Location

```
core/gateway/src/test/java/com/thelastwar/gateway/integration/
├── EndToEndIntegrationTest.java
├── FixGatewayIntegrationTest.java
└── FixGatewayLoadTest.java

core/wsgateway/src/test/java/com/thelastwar/wsgateway/integration/
├── WebSocketGatewayIntegrationTest.java
└── WebSocketGatewayLoadTest.java

core/restgateway/load-tests/
├── order_submit.lua
└── run-load-test.sh

.github/workflows/
└── integration-tests.yml
```

## Support

For issues or questions:
1. Check test output in `build/reports/tests/test/`
2. Review load test reports in `build/reports/load-tests/`
3. Check CI workflow logs on GitHub Actions
4. Consult the comprehensive documentation
