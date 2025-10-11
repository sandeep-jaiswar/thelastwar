# Gateway Health & Observability Dashboard

## Overview

This document describes the unified observability and metrics layer for all gateways in The Last War trading system. The system provides comprehensive monitoring of gateway health, performance, and reliability using Prometheus for metrics collection and Grafana for visualization.

## Architecture

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   REST Gateway   │────▶│   Prometheus     │────▶│     Grafana      │
│   (port 8080)    │     │   (port 9090)    │     │   (port 3000)    │
│   /metrics       │     │                  │     │   Dashboards     │
└──────────────────┘     │   Scrape every   │     │   + Alerts       │
                         │   5 seconds      │     └──────────────────┘
┌──────────────────┐     │                  │
│   FIX Gateway    │────▶│   Alert rules    │
│   (port 8081)    │     │   evaluation     │
│   /metrics       │     │                  │
└──────────────────┘     └──────────────────┘
                                  │
┌──────────────────┐              │
│  WebSocket GW    │──────────────┘
│  (port 8082)     │
│  /metrics        │
└──────────────────┘
```

## Metrics Collected

### Gateway Metrics (FIX Protocol)

All metrics are tagged with `gateway` label identifying the specific gateway instance.

**Counters:**
- `gateway_messages_inbound_total` - Total inbound messages received
- `gateway_messages_outbound_total` - Total outbound messages sent
- `gateway_errors_decode_total` - Message decode errors
- `gateway_errors_encode_total` - Message encode errors
- `gateway_sessions_connects_total` - Session connect events
- `gateway_sessions_disconnects_total` - Session disconnect events

**Timers (with p50, p95, p99 percentiles):**
- `gateway_decode_latency_seconds` - Message decode latency distribution
- `gateway_encode_latency_seconds` - Message encode latency distribution
- `gateway_message_roundtrip_seconds` - Message round-trip latency

**Gauges:**
- `gateway_sessions_active` - Number of active sessions
- `gateway_queue_inbound` - Inbound queue depth
- `gateway_queue_outbound` - Outbound queue depth

### WebSocket Gateway Metrics

All metrics are tagged with `gateway` label identifying the specific gateway instance.

**Counters:**
- `wsgateway_messages_inbound_total` - Messages received from clients
- `wsgateway_messages_outbound_total` - Messages sent to clients
- `wsgateway_subscriptions_total` - Subscription requests
- `wsgateway_unsubscriptions_total` - Unsubscription requests
- `wsgateway_connections_opened_total` - WebSocket connections opened
- `wsgateway_connections_closed_total` - WebSocket connections closed
- `wsgateway_backpressure_events_total` - Backpressure events (message drops)
- `wsgateway_serialization_errors_total` - Serialization errors

**Timers (with p50, p95, p99 percentiles):**
- `wsgateway_broadcast_latency_seconds` - Broadcast latency from event to client
- `wsgateway_serialization_latency_seconds` - Message serialization latency

**Gauges:**
- `wsgateway_clients_concurrent` - Number of concurrent WebSocket clients
- `wsgateway_subscriptions_active` - Number of active subscriptions

### REST Gateway Metrics

REST Gateway uses Spring Boot Actuator metrics which include:
- HTTP request metrics (throughput, latency, errors)
- JVM metrics (memory, threads, GC)
- EventBus metrics (inherited from event bus integration)

## Setup Instructions

### 1. Install Prometheus

```bash
# Download and extract Prometheus
wget https://github.com/prometheus/prometheus/releases/download/v2.48.0/prometheus-2.48.0.linux-amd64.tar.gz
tar xvfz prometheus-2.48.0.linux-amd64.tar.gz
cd prometheus-2.48.0.linux-amd64

# Copy the gateway-specific configuration
cp /path/to/thelastwar/docs/prometheus.yml ./prometheus.yml
cp /path/to/thelastwar/docs/gateway_alerts.yml ./gateway_alerts.yml

# Start Prometheus
./prometheus --config.file=prometheus.yml
```

Prometheus will be available at `http://localhost:9090`

### 2. Install Grafana

```bash
# Install Grafana (Ubuntu/Debian)
sudo apt-get install -y software-properties-common
sudo add-apt-repository "deb https://packages.grafana.com/oss/deb stable main"
wget -q -O - https://packages.grafana.com/gpg.key | sudo apt-key add -
sudo apt-get update
sudo apt-get install grafana

# Start Grafana
sudo systemctl start grafana-server
sudo systemctl enable grafana-server
```

Grafana will be available at `http://localhost:3000` (default credentials: admin/admin)

### 3. Configure Grafana Data Source

1. Log in to Grafana
2. Navigate to **Configuration** → **Data Sources**
3. Click **Add data source**
4. Select **Prometheus**
5. Configure:
   - Name: `Prometheus`
   - URL: `http://localhost:9090`
   - Access: `Server (default)`
6. Click **Save & Test**

### 4. Import Gateway Dashboard

1. In Grafana, navigate to **Dashboards** → **Import**
2. Click **Upload JSON file**
3. Select `/path/to/thelastwar/docs/grafana-gateway-dashboard.json`
4. Select the Prometheus data source
5. Click **Import**

### 5. Start Gateway Services

Each gateway must be configured to use a `PrometheusMeterRegistry` and expose metrics:

#### REST Gateway

The REST Gateway automatically exposes metrics at `http://localhost:8080/metrics` when running:

```bash
./gradlew :core:restgateway:bootRun
```

#### FIX Gateway

Example code to expose metrics:

```java
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import com.thelastwar.gateway.GatewayMetrics;
import com.thelastwar.gateway.GatewayMetricsExporter;

// Create Prometheus registry
PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

// Create gateway metrics
GatewayMetrics metrics = new GatewayMetrics(registry, "fix-gateway");

// Create metrics exporter
GatewayMetricsExporter exporter = new GatewayMetricsExporter(metrics, 8081);

// Expose via HTTP server (implementation-specific)
// Example: Start simple HTTP server on port 8081 serving exporter.scrape()
```

#### WebSocket Gateway

Example code to expose metrics:

```java
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import com.thelastwar.wsgateway.WebSocketGatewayMetrics;
import com.thelastwar.wsgateway.WebSocketMetricsExporter;

// Create Prometheus registry
PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

// Create WebSocket gateway metrics
WebSocketGatewayMetrics metrics = new WebSocketGatewayMetrics(registry, "ws-gateway");

// Create metrics exporter
WebSocketMetricsExporter exporter = new WebSocketMetricsExporter(metrics, 8082);

// Expose via HTTP server (implementation-specific)
```

## Dashboard Panels

The Gateway Health & Observability Dashboard includes:

1. **Gateway Message Throughput** - Inbound/outbound message rate for all gateways
2. **Active Sessions/Connections** - Current active sessions and WebSocket connections
3. **Gateway Latency Distribution** - p50, p95, p99 latency for decode/encode operations
4. **WebSocket Gateway Latency** - p50, p95, p99 broadcast latency
5. **Error Rate** - Decode, encode, serialization errors, and backpressure events
6. **Queue Depth** - Inbound and outbound queue utilization
7. **P99 Latency vs SLA Target** - Comparison of p99 latency against 1ms SLA threshold
8. **Session Lifecycle Events** - Connection/disconnection rates

## Alerting Rules

### Latency Alerts

- **HighGatewayDecodeLatency** - Fires when p99 decode latency > 1ms for 5 minutes
- **HighGatewayEncodeLatency** - Fires when p99 encode latency > 1ms for 5 minutes
- **HighWebSocketBroadcastLatency** - Fires when p99 broadcast latency > 1ms for 5 minutes

### Error Rate Alerts

- **HighDecodeErrorRate** - Fires when decode errors > 10/sec for 2 minutes
- **HighEncodeErrorRate** - Fires when encode errors > 10/sec for 2 minutes
- **HighSerializationErrorRate** - Fires when serialization errors > 10/sec for 2 minutes

### Throughput Alerts

- **LowGatewayThroughput** - Fires when throughput < 10 messages/sec for 5 minutes
- **LowWebSocketThroughput** - Fires when WebSocket throughput < 10 messages/sec for 5 minutes

### Session Alerts

- **NoActiveSessions** - Fires when no active sessions for 10 minutes
- **HighSessionChurn** - Fires when disconnect rate > 100/sec for 5 minutes

### Scrape Health Alerts

- **PrometheusScrapeFailed** - Fires when Prometheus fails to scrape gateway for 2 minutes
- **LowScrapeSuccessRate** - Fires when scrape success rate < 99% for 5 minutes

## Acceptance Criteria Validation

### ✅ Metrics exported successfully from all gateways

Verify by accessing:
- REST Gateway: `http://localhost:8080/metrics`
- FIX Gateway: `http://localhost:8081/metrics`
- WebSocket Gateway: `http://localhost:8082/metrics`

Expected output: Prometheus-formatted metrics text

### ✅ Grafana dashboard visualizes real-time latency & throughput

1. Open Grafana dashboard
2. Verify all panels show data
3. Verify latency panels show p50, p95, p99 percentiles
4. Verify throughput panels show message rates

### ✅ Alert triggers when p99 latency > defined SLA

1. Simulate high latency (e.g., add artificial delays in gateway code)
2. Wait 5 minutes
3. Verify alert fires in Prometheus Alerts page
4. Verify alert appears in Grafana dashboard

### ✅ Prometheus scrape success rate 100%

Check in Prometheus:
```promql
# Query scrape success rate
rate(up{job=~".*gateway"}[5m])
```

Expected: Value of 1.0 (100% success rate)

## Performance Targets

- **Throughput**: ≥ 10,000 messages/sec per gateway
- **Latency p99**: ≤ 1ms for all gateway operations
- **Scrape interval**: 5 seconds
- **Alert evaluation**: Every 30 seconds
- **Metrics retention**: 15 days (configurable in Prometheus)

## Troubleshooting

### Metrics not appearing in Prometheus

1. Verify gateway is running and exposing metrics endpoint
2. Check Prometheus targets page: `http://localhost:9090/targets`
3. Verify target status is "UP"
4. Check Prometheus logs for scrape errors

### Dashboard panels showing "No Data"

1. Verify Prometheus data source is configured correctly
2. Verify time range in dashboard matches data availability
3. Check if metrics are being scraped (Prometheus targets page)
4. Verify metric names in dashboard match actual metric names

### Alerts not firing

1. Check alert rules are loaded: `http://localhost:9090/alerts`
2. Verify alert conditions are met (query metrics manually)
3. Check alert evaluation interval in Prometheus config
4. Verify Alertmanager is configured (if using external alerting)

## Integration with Existing Systems

The gateway metrics integrate with the existing EventBus metrics. Each gateway that uses the EventBus will also export EventBus metrics with appropriate tagging:

- `eventbus_events_published_total{bus="rest-gateway"}`
- `eventbus_publish_latency_seconds{bus="rest-gateway"}`
- etc.

This provides end-to-end visibility from gateway ingress/egress through internal event bus communication.

## References

- Prometheus documentation: https://prometheus.io/docs/
- Grafana documentation: https://grafana.com/docs/
- Micrometer documentation: https://micrometer.io/docs/
- EventBus metrics guide: `docs/METRICS_BACKPRESSURE_GUIDE.md`
- EventBus README: `docs/EVENT_BUS_README.md`

## License

Copyright © 2024 The Last War. All rights reserved.
