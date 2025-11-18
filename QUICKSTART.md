# LocalStack Environment - Quick Start Guide

## 🚀 Get Started in 3 Commands

```bash
# 1. Start all services
make quickstart

# 2. Validate setup
make validate

# 3. Test integration
make test-integration
```

## 📊 What You Get

### Services Running Locally

| Service | Purpose | Port |
|---------|---------|------|
| **LocalStack** | AWS services (S3, SQS, SNS, etc.) | 4566 |
| **Redpanda** | Kafka-compatible event streaming | 19092 |
| **Redpanda Console** | Kafka Web UI | 8090 |
| **PostgreSQL** | Trade ledger database | 5432 |
| **ClickHouse** | Time-series tick database | 8123 |
| **Redis** | In-memory cache | 6379 |

### AWS Resources Created

- **6 S3 Buckets**: trading-orders, trading-executions, trading-positions, trading-snapshots, trading-analytics, trading-reports
- **5 SQS Queues**: order-events, execution-events, risk-alerts, position-updates, market-data-events
- **4 SNS Topics**: order-notifications, execution-notifications, risk-alerts-topic, system-alerts
- **3 Secrets**: fix/credentials, api/credentials, database/credentials
- **2 DynamoDB Tables**: trading-sessions, order-state

## 🔧 Common Commands

```bash
# Start services
make up

# Stop services
make down

# Restart services
make restart

# View logs
make logs

# Clean slate (removes all data!)
make clean

# Run bootstrap script
make bootstrap

# Validate setup
make validate

# Build project
make build

# Run integration examples
make test-integration
```

## 🧪 Testing Examples

### Test S3
```bash
export AWS_ENDPOINT=http://localhost:4566
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

# List buckets
aws --endpoint-url=$AWS_ENDPOINT s3 ls

# Upload file
echo "test" > /tmp/test.txt
aws --endpoint-url=$AWS_ENDPOINT s3 cp /tmp/test.txt s3://trading-orders/
```

### Test SQS
```bash
# Get queue URL
QUEUE_URL=$(aws --endpoint-url=$AWS_ENDPOINT sqs get-queue-url --queue-name order-events --query 'QueueUrl' --output text)

# Send message
aws --endpoint-url=$AWS_ENDPOINT sqs send-message \
    --queue-url $QUEUE_URL \
    --message-body '{"orderId":"123","event":"ORDER_CREATED"}'

# Receive message
aws --endpoint-url=$AWS_ENDPOINT sqs receive-message --queue-url $QUEUE_URL
```

### Test PostgreSQL
```bash
docker compose exec postgres psql -U trading_user -d trading -c "SELECT * FROM trading.orders;"
```

### Test ClickHouse
```bash
curl 'http://localhost:8123/?query=SELECT%20*%20FROM%20ticks.market_ticks%20LIMIT%2010'
```

### Test Redis
```bash
docker compose exec redis redis-cli SET test "hello" && \
docker compose exec redis redis-cli GET test
```

## 📚 Documentation

- **[README-local.md](README-local.md)** - Complete local development guide (500+ lines)
- **[LOCALSTACK_IMPLEMENTATION_SUMMARY.md](LOCALSTACK_IMPLEMENTATION_SUMMARY.md)** - Full implementation details
- **[examples/localstack-integration/README.md](examples/localstack-integration/README.md)** - Integration code examples
- **[terraform/README.md](terraform/README.md)** - Infrastructure as Code guide

## 🔍 Verify Everything Works

```bash
# Run validation script
./scripts/validate-localstack-setup.sh

# Expected output:
# ✓ AWS CLI is installed
# ✓ Docker is installed
# ✓ LocalStack is running
# ✓ All buckets exist
# ✓ All queues exist
# ✓ All topics exist
# ✓ All secrets exist
# ✓ All tables exist
# ✓ PostgreSQL is ready
# ✓ Redis is ready
# ✓ ClickHouse is ready
# ✓ Redpanda is ready
```

## 🎯 Next Steps

1. **Build the project**: `./gradlew build`
2. **Run your services**: Configure them to use LocalStack endpoints
3. **Develop features**: All AWS services available locally
4. **Test end-to-end**: Complete trading workflows offline
5. **Debug easily**: No cloud latency, full local control

## 🛟 Troubleshooting

### Services won't start
```bash
# Check Docker is running
docker ps

# View logs
make logs

# Clean restart
make clean && make quickstart
```

### Port conflicts
```bash
# Check what's using ports
lsof -i :4566  # LocalStack
lsof -i :8090  # Redpanda Console
lsof -i :5432  # PostgreSQL
```

### Resources not found
```bash
# Re-run bootstrap
make bootstrap

# Or run manually
./scripts/localstack-bootstrap.sh
```

## 💡 Pro Tips

1. **Use `make quickstart`** - Fastest way to get everything running
2. **Check logs first** - `make logs` shows all service logs
3. **Validate often** - `make validate` confirms everything is working
4. **Clean when stuck** - `make clean && make quickstart` for fresh start
5. **Test with examples** - Run `make test-integration` to see it in action

## 📞 Need Help?

1. Check **[README-local.md](README-local.md)** for detailed documentation
2. Run `./scripts/validate-localstack-setup.sh` to diagnose issues
3. Review service logs: `docker compose logs <service-name>`
4. See **[LOCALSTACK_IMPLEMENTATION_SUMMARY.md](LOCALSTACK_IMPLEMENTATION_SUMMARY.md)** for implementation details

---

**Ready to develop? Run `make quickstart` now! 🚀**
