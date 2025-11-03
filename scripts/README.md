# Scripts Reference

This directory contains scripts for managing The Last War trading system on local Linux environments.

## Available Scripts

### setup-local.sh
Complete automated setup script that installs and configures all dependencies.

```bash
./scripts/setup-local.sh
```

**What it does:**
- Checks and installs Java 21 (OpenJDK)
- Installs and configures ClickHouse
- Installs and configures Kafka
- Applies system performance tuning (if run with sudo)
- Creates application directories
- Builds the project

**Requirements:**
- Ubuntu 22.04 or later
- Internet connection for package downloads
- sudo access (optional, for optimal system tuning)

### start-services.sh
Starts all required services for the trading system.

```bash
./scripts/start-services.sh
```

**What it does:**
- Starts ClickHouse server
- Starts Zookeeper
- Starts Kafka
- Creates required Kafka topics (oms-events, execution-events, trade-events)
- Initializes ClickHouse database schema

**Logs location:** `/var/lib/trading-system/logs/`

### stop-services.sh
Stops Kafka and Zookeeper services.

```bash
./scripts/stop-services.sh
```

**What it does:**
- Stops Kafka broker
- Stops Zookeeper
- Leaves ClickHouse running (by design)

**Note:** To stop ClickHouse manually:
```bash
sudo systemctl stop clickhouse-server
```

### check-services.sh
Checks the status of all services.

```bash
./scripts/check-services.sh
```

**What it displays:**
- ClickHouse server status and version
- Zookeeper status and PID
- Kafka status and connectivity
- List of Kafka topics
- ClickHouse database and table status
- Record counts

### run-benchmarks.sh
Runs performance benchmarks for the trading system.

```bash
# Full benchmark suite
./scripts/run-benchmarks.sh --full

# Quick benchmarks
./scripts/run-benchmarks.sh --quick

# Sustained load tests (60 seconds)
./scripts/run-benchmarks.sh --sustained

# With Java Flight Recorder
./scripts/run-benchmarks.sh --full --with-jfr
```

See the script header for more options and details.

## Typical Workflow

### Initial Setup
```bash
# 1. Run complete setup (first time only)
./scripts/setup-local.sh

# 2. Start services
./scripts/start-services.sh

# 3. Verify everything is running
./scripts/check-services.sh
```

### Daily Development
```bash
# Start services
./scripts/start-services.sh

# Check status
./scripts/check-services.sh

# Work on your code...

# Run benchmarks (optional)
./scripts/run-benchmarks.sh --quick

# Stop services when done
./scripts/stop-services.sh
```

### Testing Changes
```bash
# Build with tests
./gradlew clean build

# Build without tests (faster)
./gradlew clean build -x test

# Run specific module tests
./gradlew :core:oms:test

# Run benchmarks
./scripts/run-benchmarks.sh --quick
```

## Troubleshooting

### Services won't start
```bash
# Check if ports are already in use
sudo netstat -tlnp | grep -E '8123|9092|2181'

# Check logs
tail -f /var/lib/trading-system/logs/*.log
sudo journalctl -u clickhouse-server -n 100
```

### Build failures
```bash
# Verify Java version
java -version

# Clean and rebuild
./gradlew clean build --refresh-dependencies
```

### ClickHouse connection issues
```bash
# Test connection
clickhouse-client --query "SELECT 1"

# Check service status
sudo systemctl status clickhouse-server

# View logs
sudo tail -f /var/log/clickhouse-server/clickhouse-server.log
```

### Kafka issues
```bash
# Check if Kafka is running
pgrep -f "kafka.Kafka"

# Test connectivity
/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092

# View logs
tail -f /var/lib/kafka/logs/kafka.log
```

## Environment Variables

The scripts respect the following environment variables:

- `JAVA_HOME` - Java installation directory
- `KAFKA_DIR` - Kafka installation directory (default: `/opt/kafka`)
- `LOG_DIR` - Log directory (default: `/var/lib/trading-system/logs`)

## Script Exit Codes

All scripts follow standard exit code conventions:
- `0` - Success
- `1` - General error
- `2` - Misuse of command

## Log Files

Logs are stored in `/var/lib/trading-system/logs/`:
- `zookeeper.log` - Zookeeper service logs
- `kafka.log` - Kafka broker logs
- Application logs (when running application components)

System logs:
- ClickHouse: `/var/log/clickhouse-server/`
- Kafka: `/var/lib/kafka/logs/`

## Performance Considerations

For optimal performance:
1. Run `setup-local.sh` with sudo to apply system tuning
2. Set CPU governor to performance mode
3. Disable swap or minimize swappiness
4. Use SSD for data storage
5. Allocate sufficient memory (16GB+ recommended)

## Additional Resources

- [LOCAL_SETUP.md](../LOCAL_SETUP.md) - Comprehensive setup guide
- [README.md](../README.md) - Project overview
- [PERFORMANCE_TUNING.md](../docs/PERFORMANCE_TUNING.md) - Performance tuning guide
