# Local Development Environment Setup

This guide explains how to set up and run the entire trading platform locally using Docker Compose with LocalStack, Redpanda (Kafka), PostgreSQL, ClickHouse, and Redis.

## 🎯 Overview

This local development environment provides a **fully functional, offline-ready** simulation of the complete trading platform infrastructure. It eliminates the need for AWS access or any external cloud dependencies.

### What's Included

- **LocalStack** - Simulates AWS services (S3, SQS, SNS, Secrets Manager, CloudWatch, DynamoDB)
- **Redpanda** - Kafka-compatible event streaming platform
- **PostgreSQL** - Trade ledger and persistent storage
- **ClickHouse** - Time-series tick data and analytics database
- **Redis** - In-memory cache and state management
- **Redpanda Console** - Web UI for Kafka management

## 📋 Prerequisites

Before you begin, ensure you have the following installed:

- **Docker Desktop** (or Docker Engine + Docker Compose)
  - Docker version 20.10 or higher
  - Docker Compose version 2.0 or higher
- **AWS CLI** (for LocalStack interaction)
  ```bash
  # Install AWS CLI
  pip install awscli
  # or
  brew install awscli  # macOS
  ```
- **JDK 25** (for running the Java services)
- **Gradle** (wrapper included in the project)

### System Requirements

- **CPU**: 4 cores minimum (8+ recommended)
- **RAM**: 8 GB minimum (16+ GB recommended)
- **Disk**: 10 GB free space

## 🚀 Quick Start

### 1. Start All Services

Start the entire stack with a single command:

```bash
docker compose up -d
```

This will start:
- LocalStack on `localhost:4566`
- Redpanda (Kafka) on `localhost:19092`
- PostgreSQL on `localhost:5432`
- ClickHouse on `localhost:8123`
- Redis on `localhost:6379`
- Redpanda Console on `localhost:8090`

### 2. Verify Services are Running

Check that all services are healthy:

```bash
docker compose ps
```

All services should show status as "Up" or "healthy".

### 3. Initialize LocalStack Resources

The LocalStack bootstrap script runs automatically, but you can also run it manually:

```bash
docker compose exec localstack /etc/localstack/init/ready.d/init-aws.sh
```

Or run it directly (requires AWS CLI):

```bash
./scripts/localstack-bootstrap.sh
```

### 4. Access Service UIs

- **Redpanda Console** (Kafka UI): http://localhost:8090
- **LocalStack Health**: http://localhost:4566/_localstack/health
- **ClickHouse UI**: http://localhost:8123/play
- **Trader UI** (if deployed): http://localhost:8080

## 🔧 Service Configuration

### LocalStack (AWS Services)

LocalStack provides local implementations of AWS services:

**Endpoint**: `http://localhost:4566`

**Available Services**:
- S3 - Object storage
- SQS - Message queues
- SNS - Pub/Sub messaging
- Secrets Manager - Secret storage
- CloudWatch - Metrics and logs
- DynamoDB - NoSQL database

**AWS CLI Configuration**:

```bash
# Configure AWS CLI to use LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

# Use --endpoint-url for all AWS CLI commands
aws --endpoint-url=http://localhost:4566 s3 ls
aws --endpoint-url=http://localhost:4566 sqs list-queues
aws --endpoint-url=http://localhost:4566 sns list-topics
```

**Pre-configured Resources**:

S3 Buckets:
- `trading-orders` - Order snapshots and archives
- `trading-executions` - Execution reports
- `trading-positions` - Position snapshots
- `trading-snapshots` - OMS state snapshots
- `trading-analytics` - Analytics data
- `trading-reports` - Generated reports

SQS Queues:
- `order-events` - Order lifecycle events
- `execution-events` - Trade executions
- `risk-alerts` - Risk check alerts
- `position-updates` - Position changes
- `market-data-events` - Market data updates
- `trade-reconciliation` - Reconciliation tasks

SNS Topics:
- `order-notifications` - Order status changes
- `execution-notifications` - Trade confirmations
- `risk-alerts-topic` - Risk alerts
- `system-alerts` - System notifications
- `trade-confirmations` - Trade confirmations

Secrets:
- `fix/credentials` - FIX protocol credentials
- `api/credentials` - API access credentials
- `database/credentials` - Database connection details

### Redpanda (Kafka)

**Kafka Bootstrap Server**: `localhost:19092`
**Schema Registry**: `localhost:18081`
**Admin API**: `localhost:9644`
**Web Console**: `localhost:8090`

### PostgreSQL

**Connection Details**:
- Host: `localhost`
- Port: `5432`
- Database: `trading`
- Username: `trading_user`
- Password: `trading_pass`

**Connection String**:
```
jdbc:postgresql://localhost:5432/trading?user=trading_user&password=trading_pass
```

**Tables**:
- `trading.orders` - Order information
- `trading.executions` - Trade executions
- `trading.positions` - Current positions
- `trading.order_snapshots` - Event sourcing snapshots
- `trading.risk_limits` - Risk limit configurations

### ClickHouse

**Connection Details**:
- HTTP Interface: `localhost:8123`
- Native Client: `localhost:9000`
- Database: `ticks`
- Username: `clickhouse_user`
- Password: `clickhouse_pass`

**HTTP Connection String**:
```
http://clickhouse_user:clickhouse_pass@localhost:8123/ticks
```

**Tables**:
- `ticks.market_ticks` - Market data ticks
- `ticks.order_events` - Order lifecycle events
- `ticks.execution_events` - Trade executions
- `ticks.risk_events` - Risk check events
- `ticks.performance_metrics` - System metrics

### Redis

**Connection Details**:
- Host: `localhost`
- Port: `6379`
- No authentication (local dev only)

**Connection String**:
```
redis://localhost:6379
```

## 📝 Testing the Environment

### Test 1: S3 Upload and Download

```bash
# Set LocalStack endpoint
export AWS_ENDPOINT=http://localhost:4566
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

# Create a test file
echo "Test order data" > /tmp/test-order.txt

# Upload to S3
aws --endpoint-url=$AWS_ENDPOINT s3 cp /tmp/test-order.txt s3://trading-orders/test-order.txt

# List objects
aws --endpoint-url=$AWS_ENDPOINT s3 ls s3://trading-orders/

# Download from S3
aws --endpoint-url=$AWS_ENDPOINT s3 cp s3://trading-orders/test-order.txt /tmp/downloaded-order.txt

# Verify
cat /tmp/downloaded-order.txt
```

### Test 2: SQS Message Publishing and Consuming

```bash
# Get queue URL
QUEUE_URL=$(aws --endpoint-url=$AWS_ENDPOINT sqs get-queue-url --queue-name order-events --query 'QueueUrl' --output text)

# Send a message
aws --endpoint-url=$AWS_ENDPOINT sqs send-message \
    --queue-url $QUEUE_URL \
    --message-body '{"orderId":"ORD123","symbol":"AAPL","side":"BUY","quantity":100,"price":150.25}'

# Receive message
aws --endpoint-url=$AWS_ENDPOINT sqs receive-message \
    --queue-url $QUEUE_URL \
    --max-number-of-messages 1

# Purge queue (for testing)
aws --endpoint-url=$AWS_ENDPOINT sqs purge-queue --queue-url $QUEUE_URL
```

### Test 3: SNS Topic Publishing

```bash
# Get topic ARN
TOPIC_ARN=$(aws --endpoint-url=$AWS_ENDPOINT sns list-topics --query 'Topics[?contains(TopicArn, `order-notifications`)].TopicArn' --output text)

# Publish message
aws --endpoint-url=$AWS_ENDPOINT sns publish \
    --topic-arn $TOPIC_ARN \
    --message '{"type":"ORDER_FILLED","orderId":"ORD123","status":"FILLED"}'
```

### Test 4: Secrets Manager

```bash
# Retrieve a secret
aws --endpoint-url=$AWS_ENDPOINT secretsmanager get-secret-value \
    --secret-id api/credentials \
    --query 'SecretString' \
    --output text | jq .
```

### Test 5: PostgreSQL Query

```bash
# Connect to PostgreSQL
docker compose exec postgres psql -U trading_user -d trading

# Query tables
\dt trading.*

# Query orders
SELECT * FROM trading.orders LIMIT 10;

# Exit
\q
```

### Test 6: ClickHouse Query

```bash
# Query via HTTP
curl -u clickhouse_user:clickhouse_pass 'http://localhost:8123/?query=SELECT%20*%20FROM%20ticks.market_ticks%20LIMIT%2010'

# Or use clickhouse-client
docker compose exec clickhouse clickhouse-client -u clickhouse_user --password clickhouse_pass

# Query
SELECT * FROM ticks.market_ticks LIMIT 10;

# Exit
exit;
```

### Test 7: Redis Operations

```bash
# Connect to Redis
docker compose exec redis redis-cli

# Set a key
SET order:ORD123 '{"status":"ACTIVE","quantity":100}'

# Get a key
GET order:ORD123

# Exit
exit
```

### Test 8: Kafka (Redpanda) Publishing

```bash
# Using Redpanda's rpk CLI
docker compose exec redpanda rpk topic create order-events --brokers localhost:9092

# Produce a message
echo '{"orderId":"ORD123","event":"ORDER_CREATED"}' | \
    docker compose exec -T redpanda rpk topic produce order-events

# Consume messages
docker compose exec redpanda rpk topic consume order-events --num 1
```

## 🔌 Integration Examples

### Java AWS SDK Configuration for LocalStack

Create a configuration class for AWS SDK clients:

```java
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import java.net.URI;

public class LocalStackConfig {
    private static final String LOCALSTACK_ENDPOINT = "http://localhost:4566";
    private static final Region REGION = Region.US_EAST_1;
    
    private static final StaticCredentialsProvider CREDENTIALS = 
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create("test", "test")
        );
    
    public static S3Client createS3Client() {
        return S3Client.builder()
            .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
            .region(REGION)
            .credentialsProvider(CREDENTIALS)
            .forcePathStyle(true) // Required for LocalStack
            .build();
    }
    
    public static SqsClient createSqsClient() {
        return SqsClient.builder()
            .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
            .region(REGION)
            .credentialsProvider(CREDENTIALS)
            .build();
    }
}
```

### S3 Example Usage

```java
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3Example {
    public static void main(String[] args) {
        S3Client s3 = LocalStackConfig.createS3Client();
        
        // Upload order snapshot to S3
        String bucketName = "trading-orders";
        String key = "snapshots/order-123.json";
        String content = "{\"orderId\":\"123\",\"status\":\"FILLED\"}";
        
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build(),
            RequestBody.fromString(content)
        );
        
        System.out.println("Uploaded to s3://" + bucketName + "/" + key);
    }
}
```

### SQS Example Usage

```java
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

public class SqsExample {
    public static void main(String[] args) {
        SqsClient sqs = LocalStackConfig.createSqsClient();
        
        // Get queue URL
        String queueUrl = sqs.getQueueUrl(
            GetQueueUrlRequest.builder()
                .queueName("order-events")
                .build()
        ).queueUrl();
        
        // Send message
        sqs.sendMessage(
            SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody("{\"orderId\":\"123\",\"event\":\"ORDER_CREATED\"}")
                .build()
        );
        
        System.out.println("Message sent to queue: " + queueUrl);
        
        // Receive message
        ReceiveMessageResponse response = sqs.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(5)
                .build()
        );
        
        response.messages().forEach(msg -> {
            System.out.println("Received: " + msg.body());
            
            // Delete message after processing
            sqs.deleteMessage(
                DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(msg.receiptHandle())
                    .build()
            );
        });
    }
}
```

## 🛠️ Troubleshooting

### Services Not Starting

```bash
# Check logs for all services
docker compose logs

# Check specific service
docker compose logs localstack
docker compose logs redpanda
docker compose logs postgres

# Restart a specific service
docker compose restart localstack
```

### Port Conflicts

If you have port conflicts, you can modify the port mappings in `docker-compose.yml`:

```yaml
# Example: Change PostgreSQL port
postgres:
  ports:
    - "5433:5432"  # Use 5433 instead of 5432
```

### LocalStack Not Responding

```bash
# Check LocalStack health
curl http://localhost:4566/_localstack/health

# Re-run bootstrap script
docker compose exec localstack /etc/localstack/init/ready.d/init-aws.sh
```

### Clean Restart

To completely clean and restart the environment:

```bash
# Stop and remove all containers and volumes
docker compose down -v

# Start fresh
docker compose up -d

# Check status
docker compose ps
```

## 🔒 Security Notes

⚠️ **This setup is for LOCAL DEVELOPMENT ONLY**

- All default passwords are hardcoded and insecure
- No TLS/SSL encryption
- No authentication on most services
- Not suitable for production or public networks

## 📚 Additional Resources

### Service Documentation

- [LocalStack Documentation](https://docs.localstack.cloud/)
- [Redpanda Documentation](https://docs.redpanda.com/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [ClickHouse Documentation](https://clickhouse.com/docs/)
- [Redis Documentation](https://redis.io/documentation)

### Useful Commands

```bash
# View all container logs
docker compose logs -f

# Stop all services
docker compose stop

# Start all services
docker compose start

# Remove all containers and volumes (clean slate)
docker compose down -v

# Scale a service (e.g., multiple Redpanda nodes)
docker compose up -d --scale redpanda=3

# Execute command in container
docker compose exec <service-name> <command>

# View resource usage
docker stats
```

## 🎓 Next Steps

1. **Build the services**: Run `./gradlew build` to compile the Java services
2. **Run integration tests**: Execute tests that connect to the local environment
3. **Develop features**: Use this environment for rapid development and testing
4. **Performance testing**: Run benchmarks against the local infrastructure
5. **Event replay**: Practice event sourcing and replay scenarios

## 📞 Support

For issues or questions about the local development environment:

1. Check the troubleshooting section above
2. Review service logs: `docker compose logs <service-name>`
3. Consult the main [README.md](./README.md) for project documentation
4. Review individual service documentation

---

**Happy Coding! 🚀**
