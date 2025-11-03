# Local Setup Guide - The Last War Trading System

This guide provides complete instructions for setting up and running The Last War trading system on a local Linux environment **without Docker or Kubernetes**.

## System Requirements

### Hardware Requirements
- **CPU**: 8+ cores (16+ recommended for production)
- **RAM**: 16GB minimum (32GB+ recommended)
- **Disk**: 50GB free space (SSD recommended)
- **Network**: 1Gbps network interface

### Software Requirements
- **OS**: Linux (Ubuntu 22.04 LTS or later recommended)
- **Java**: OpenJDK 21 or later
- **ClickHouse**: 24.3 or later
- **Kafka**: 3.8.0 or later
- **Gradle**: 8.8+ (included via wrapper)

## Quick Start

```bash
# Clone the repository
git clone https://github.com/sandeep-jaiswar/thelastwar.git
cd thelastwar

# Run the complete setup
./scripts/setup-local.sh

# Start all services
./scripts/start-services.sh

# Stop all services
./scripts/stop-services.sh
```

## Detailed Setup Instructions

### 1. Install Java 21

```bash
# Install OpenJDK 21
sudo apt update
sudo apt install -y openjdk-21-jdk

# Verify installation
java -version
# Should show: openjdk version "21.x.x"

# Set JAVA_HOME (add to ~/.bashrc or ~/.zshrc)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

### 2. Install ClickHouse

ClickHouse is a high-performance columnar OLAP database optimized for analytics and fast data ingestion.

```bash
# Add ClickHouse repository
sudo apt-get install -y apt-transport-https ca-certificates dirmngr
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 8919F6BD2B48D754

echo "deb https://packages.clickhouse.com/deb stable main" | sudo tee \
    /etc/apt/sources.list.d/clickhouse.list

# Install ClickHouse server and client
sudo apt-get update
sudo apt-get install -y clickhouse-server clickhouse-client

# Start ClickHouse service
sudo systemctl start clickhouse-server
sudo systemctl enable clickhouse-server

# Verify ClickHouse is running
clickhouse-client --query "SELECT version()"
```

#### Configure ClickHouse for Trading System

```bash
# Create database and user
clickhouse-client << EOF
CREATE DATABASE IF NOT EXISTS trading_system;
CREATE USER IF NOT EXISTS trading_user IDENTIFIED BY 'trading_password';
GRANT ALL ON trading_system.* TO trading_user;
EOF

# Test connection
clickhouse-client --database=trading_system --user=trading_user --password=trading_password \
    --query "SELECT 'ClickHouse is ready!'"
```

#### ClickHouse Performance Tuning

Edit `/etc/clickhouse-server/config.xml` for optimal performance:

```xml
<clickhouse>
    <!-- Increase memory limits for better performance -->
    <max_memory_usage>20000000000</max_memory_usage>
    <max_memory_usage_for_user>40000000000</max_memory_usage_for_user>
    
    <!-- Optimize for write-heavy workload -->
    <merge_tree>
        <parts_to_delay_insert>150</parts_to_delay_insert>
        <parts_to_throw_insert>300</parts_to_throw_insert>
        <max_bytes_to_merge_at_max_space_in_pool>161061273600</max_bytes_to_merge_at_max_space_in_pool>
    </merge_tree>
    
    <!-- Increase connection limits -->
    <max_connections>4096</max_connections>
    <max_concurrent_queries>100</max_concurrent_queries>
</clickhouse>
```

Restart ClickHouse after configuration changes:
```bash
sudo systemctl restart clickhouse-server
```

### 3. Install Kafka

Kafka provides the event streaming backbone for the trading system.

```bash
# Download Kafka
cd /opt
sudo wget https://downloads.apache.org/kafka/3.8.0/kafka_2.13-3.8.0.tgz
sudo tar -xzf kafka_2.13-3.8.0.tgz
sudo mv kafka_2.13-3.8.0 kafka
sudo rm kafka_2.13-3.8.0.tgz

# Create Kafka data directories
sudo mkdir -p /var/lib/kafka/kraft-combined-logs
sudo mkdir -p /var/lib/kafka/logs
sudo chown -R $USER:$USER /var/lib/kafka

# Create KRaft config directory
sudo mkdir -p /opt/kafka/config/kraft

# Configure Kafka in KRaft mode (no Zookeeper needed)
# Generate a cluster UUID
CLUSTER_UUID=$(/opt/kafka/bin/kafka-storage.sh random-uuid)

sudo tee /opt/kafka/config/kraft/server.properties > /dev/null << EOF
# KRaft mode configuration (replaces Zookeeper)
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@localhost:9093
listeners=PLAINTEXT://localhost:9092,CONTROLLER://localhost:9093
inter.broker.listener.name=PLAINTEXT
advertised.listeners=PLAINTEXT://localhost:9092
controller.listener.names=CONTROLLER
log.dirs=/var/lib/kafka/kraft-combined-logs
num.partitions=8
default.replication.factor=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
log.retention.hours=168
log.segment.bytes=1073741824
EOF

# Format the storage directory with the cluster UUID
/opt/kafka/bin/kafka-storage.sh format -t $CLUSTER_UUID -c /opt/kafka/config/kraft/server.properties
```

#### Start Kafka in KRaft Mode (No Zookeeper Required)

Kafka 3.8.0 supports KRaft mode, which eliminates the need for Zookeeper by using Kafka's built-in consensus protocol.

```bash
# Start Kafka in KRaft mode
nohup /opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/kraft/server.properties \
    > /var/lib/kafka/logs/kafka.log 2>&1 &

# Wait for Kafka to start
sleep 10

# Verify Kafka is running
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

#### Create Required Kafka Topics

```bash
# Create OMS events topic
/opt/kafka/bin/kafka-topics.sh --create \
    --bootstrap-server localhost:9092 \
    --replication-factor 1 \
    --partitions 8 \
    --topic oms-events

# Create execution events topic
/opt/kafka/bin/kafka-topics.sh --create \
    --bootstrap-server localhost:9092 \
    --replication-factor 1 \
    --partitions 8 \
    --topic execution-events

# Create trade events topic
/opt/kafka/bin/kafka-topics.sh --create \
    --bootstrap-server localhost:9092 \
    --replication-factor 1 \
    --partitions 8 \
    --topic trade-events

# List topics to verify
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

### 4. System Performance Tuning

For ultra-low latency trading, optimize your Linux system:

```bash
# Set CPU governor to performance mode
echo performance | sudo tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor

# Disable CPU frequency scaling
sudo systemctl disable ondemand

# Increase file descriptors
echo "* soft nofile 65536" | sudo tee -a /etc/security/limits.conf
echo "* hard nofile 65536" | sudo tee -a /etc/security/limits.conf

# Optimize network settings
sudo sysctl -w net.core.rmem_max=134217728
sudo sysctl -w net.core.wmem_max=134217728
sudo sysctl -w net.ipv4.tcp_rmem='4096 87380 67108864'
sudo sysctl -w net.ipv4.tcp_wmem='4096 65536 67108864'

# Disable swap for consistent latency
sudo swapoff -a

# Set transparent huge pages to madvise
echo madvise | sudo tee /sys/kernel/mm/transparent_hugepage/enabled
```

### 5. Build the Trading System

```bash
# Navigate to project directory
cd /path/to/thelastwar

# Build all modules
./gradlew clean build

# Run tests
./gradlew test

# Build without tests (faster)
./gradlew clean build -x test
```

### 6. Configure Application Properties

Create configuration files for each component:

#### OMS Configuration

Create `core/oms/src/main/resources/application.properties`:

```properties
# ClickHouse Configuration
clickhouse.url=jdbc:clickhouse://localhost:8123/trading_system
clickhouse.username=trading_user
clickhouse.password=trading_password

# Kafka Configuration
kafka.bootstrap.servers=localhost:9092
kafka.oms.topic=oms-events
kafka.consumer.group.id=oms-consumer-group

# Snapshot Configuration
snapshot.directory=/var/lib/trading-system/snapshots
snapshot.interval.minutes=5
snapshot.retention.count=10

# Logging
logging.level.root=INFO
logging.level.com.thelastwar=DEBUG
```

#### REST Gateway Configuration

The configuration is already in `core/restgateway/src/main/resources/application.properties`.
Update if needed:

```properties
server.port=8080
spring.application.name=rest-gateway

# Logging
logging.level.root=INFO
logging.level.com.thelastwar=DEBUG
```

### 7. Initialize Database Schema

The ClickHouse schema is automatically created when the application starts, but you can also create it manually:

```bash
clickhouse-client --database=trading_system --user=trading_user --password=trading_password << EOF
CREATE TABLE IF NOT EXISTS oms_order_state (
    internal_order_id Int64,
    client_order_id String,
    symbol String,
    side Int16,
    order_type Int16,
    quantity Int64,
    price Int64,
    account Int64,
    current_state String,
    filled_quantity Int64,
    remaining_quantity Int64,
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now(),
    version Int64
) ENGINE = ReplacingMergeTree(version)
ORDER BY internal_order_id;
EOF
```

### 8. Running the System

#### Start Core Services

```bash
# Use the provided script
./scripts/start-services.sh
```

Or start components individually:

```bash
# Start Event Bus (if standalone)
./gradlew :core:eventbus:run

# Start OMS Service
./gradlew :core:oms:run

# Start REST Gateway
./gradlew :core:restgateway:bootRun

# Start WebSocket Gateway
./gradlew :core:wsgateway:run

# Start Matching Engine
./gradlew :core:matching:run
```

#### Verify Services

```bash
# Check REST Gateway health
curl http://localhost:8080/actuator/health

# Check ClickHouse connection
clickhouse-client --database=trading_system --query "SELECT count() FROM oms_order_state"

# Check Kafka topics
/opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic oms-events \
    --from-beginning \
    --max-messages 10
```

### 9. Monitoring and Observability

#### View Metrics

Metrics are exposed via Prometheus format:

```bash
# View OMS metrics
curl http://localhost:8080/actuator/prometheus

# View system metrics
curl http://localhost:9090/metrics
```

#### View Logs

```bash
# OMS logs
tail -f /var/lib/trading-system/logs/oms.log

# Kafka logs
tail -f /var/lib/kafka/logs/kafka.log

# ClickHouse logs
sudo tail -f /var/log/clickhouse-server/clickhouse-server.log
```

#### Monitor ClickHouse Performance

```bash
# Check running queries
clickhouse-client --query "SELECT * FROM system.processes"

# Check table statistics
clickhouse-client --query "SELECT * FROM system.tables WHERE database = 'trading_system'"

# Check merge statistics
clickhouse-client --query "SELECT * FROM system.merges"
```

### 10. Running Benchmarks

```bash
# Run complete benchmark suite
./scripts/run-benchmarks.sh --full

# Run quick benchmarks
./scripts/run-benchmarks.sh --quick

# Run specific benchmark
./gradlew :core:eventbus:jmh -Pargs="AeronBenchmark.benchmarkPublish"
```

## Troubleshooting

### ClickHouse Issues

**Problem**: ClickHouse fails to start
```bash
# Check logs
sudo journalctl -u clickhouse-server -n 100

# Check configuration
clickhouse-client --query "SELECT * FROM system.settings"
```

**Problem**: Connection refused
```bash
# Ensure ClickHouse is listening on correct port
sudo netstat -tlnp | grep 8123

# Test connection
clickhouse-client --host localhost --port 9000 --query "SELECT 1"
```

### Kafka Issues

**Problem**: Kafka won't start
```bash
# Check Kafka logs
tail -f /var/lib/kafka/logs/kafka.log

# Verify KRaft storage is formatted
ls -la /var/lib/kafka/kraft-combined-logs/

# Restart Kafka
./scripts/stop-services.sh
./scripts/start-services.sh
```

**Problem**: Topic creation fails
```bash
# Verify Kafka is accessible
/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092

# List existing topics
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

### Java/Build Issues

**Problem**: Gradle build fails with Java version error
```bash
# Verify Java version
java -version

# Set correct JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew clean build
```

**Problem**: OutOfMemoryError during build
```bash
# Increase Gradle memory
export GRADLE_OPTS="-Xmx4g"
./gradlew clean build
```

## Production Deployment Checklist

- [ ] Java 21 installed and configured
- [ ] ClickHouse installed, configured, and optimized
- [ ] Kafka cluster setup with proper replication
- [ ] System performance tuning applied
- [ ] File descriptor limits increased
- [ ] CPU governor set to performance mode
- [ ] Swap disabled or minimized
- [ ] Network tuning applied
- [ ] Monitoring and alerting configured
- [ ] Log rotation configured
- [ ] Backup strategy implemented
- [ ] Database schema initialized
- [ ] Kafka topics created with correct partitions
- [ ] Application properties configured
- [ ] Security settings applied (firewalls, authentication)
- [ ] Performance benchmarks validated

## Next Steps

1. Review the [Performance Tuning Guide](PERFORMANCE_TUNING.md)
2. Read the [Benchmark Guide](docs/BENCHMARK_GUIDE.md)
3. Explore [Event Bus Integration](docs/EVENTBUS_INTEGRATION.md)
4. Review [Architecture Decision Records](docs/adr/)

## Support

For issues or questions:
- Check existing GitHub Issues
- Review documentation in the `docs/` directory
- Contact the development team

---

**Note**: This is a high-performance trading system designed for ultra-low latency. Proper hardware and system tuning are essential for achieving target performance metrics (< 10µs latency, > 2M msg/s throughput).
