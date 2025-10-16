# LocalStack Integration Examples

This directory contains example code demonstrating how to integrate AWS SDK with LocalStack for local development.

## Overview

The examples show:
- How to configure AWS SDK clients for LocalStack
- S3 operations (upload, download, list, delete)
- SQS operations (send, receive, delete messages)
- Best practices for switching between LocalStack and real AWS

## Prerequisites

1. **LocalStack running**: Ensure the Docker Compose stack is up
   ```bash
   docker compose up -d
   ```

2. **Bootstrap script executed**: Run the LocalStack bootstrap script
   ```bash
   ./scripts/localstack-bootstrap.sh
   ```

3. **JDK 25**: Java Development Kit 25 or higher

## Building

```bash
cd examples/localstack-integration
../../gradlew build
```

## Running the Examples

### Run all demos

```bash
../../gradlew run
```

### Run specific example classes

```bash
# S3 Example
../../gradlew run --args="com.thelastwar.examples.S3Example"

# SQS Example
../../gradlew run --args="com.thelastwar.examples.SqsExample"
```

### Using environment variables

```bash
# Use LocalStack (default)
USE_LOCALSTACK=true ../../gradlew run

# Use real AWS (requires credentials)
USE_LOCALSTACK=false AWS_PROFILE=myprofile ../../gradlew run

# Custom LocalStack endpoint
LOCALSTACK_ENDPOINT=http://192.168.1.100:4566 ../../gradlew run
```

## Code Structure

### LocalStackConfig.java
Main configuration class that creates AWS SDK clients:
- `createS3Client()` - S3 client
- `createSqsClient()` - SQS client
- `createSnsClient()` - SNS client
- `createSecretsManagerClient()` - Secrets Manager client
- `createDynamoDbClient()` - DynamoDB client

Key features:
- Automatic endpoint override for LocalStack
- Environment variable configuration
- Easy switching between LocalStack and AWS

### S3Example.java
Demonstrates S3 operations:
- `uploadOrderSnapshot()` - Upload order snapshots to S3
- `downloadObject()` - Download objects from S3
- `listObjects()` - List objects with prefix
- `deleteObject()` - Delete objects
- `bucketExists()` - Check bucket existence

### SqsExample.java
Demonstrates SQS operations:
- `sendOrderEvent()` - Send order events to queue
- `receiveMessages()` - Receive and process messages
- `deleteMessage()` - Delete processed messages
- `purgeQueue()` - Clear queue (testing)
- `getApproximateMessageCount()` - Get message count

### LocalStackIntegrationDemo.java
Main entry point that runs all examples.

## Example Output

```
==================================================
LocalStack Integration Demo
==================================================

LocalStack Endpoint: http://localhost:4566
Region: US_EAST_1

========== S3 Example ==========
✓ Uploaded order snapshot to s3://trading-orders/snapshots/ORD-12345/1697472000000.json
✓ Downloaded object from s3://trading-orders/snapshots/ORD-12345/1697472000000.json
Downloaded content: {
    "orderId": "ORD-12345",
    "symbol": "AAPL",
    "side": "BUY",
    ...
}

Objects in s3://trading-orders/snapshots/:
  - snapshots/ORD-12345/1697472000000.json (size: 235 bytes, modified: 2024-10-16T18:00:00Z)

✓ Deleted object s3://trading-orders/snapshots/ORD-12345/1697472000000.json
✓ S3 demo completed successfully

========== SQS Example ==========
✓ Sent message to queue: order-events (MessageId: abc-123)
✓ Sent message to queue: order-events (MessageId: def-456)
Approximate messages in queue: 2
✓ Received 2 message(s) from queue: order-events

Received message:
  Body: {"orderId":"ORD-12345","event":"ORDER_CREATED","details":{"symbol":"AAPL","quantity":100,"price":150.25}...
  Attributes: [EventType, OrderId]

✓ Deleted message from queue: order-events
✓ SQS demo completed successfully

==================================================
✓ All demos completed successfully!
==================================================
```

## Integration in Your Application

### Using the Configuration

```java
import com.thelastwar.examples.LocalStackConfig;
import software.amazon.awssdk.services.s3.S3Client;

public class MyService {
    private final S3Client s3Client;
    
    public MyService() {
        this.s3Client = LocalStackConfig.createS3Client();
    }
    
    public void saveOrderSnapshot(String orderId, String data) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket("trading-orders")
                .key("orders/" + orderId + ".json")
                .build(),
            RequestBody.fromString(data)
        );
    }
}
```

### Environment-Based Configuration

In your application properties:
```properties
# application.properties
aws.localstack.enabled=${USE_LOCALSTACK:true}
aws.localstack.endpoint=${LOCALSTACK_ENDPOINT:http://localhost:4566}
aws.region=us-east-1
```

In your code:
```java
public class AwsClientFactory {
    @Value("${aws.localstack.enabled}")
    private boolean useLocalStack;
    
    @Value("${aws.localstack.endpoint}")
    private String localStackEndpoint;
    
    public S3Client createS3Client() {
        var builder = S3Client.builder();
        
        if (useLocalStack) {
            builder.endpointOverride(URI.create(localStackEndpoint))
                   .forcePathStyle(true);
        }
        
        return builder.build();
    }
}
```

## Testing

These examples can be used as the basis for integration tests:

```java
@Test
public void testS3Integration() {
    S3Example s3 = new S3Example("trading-orders");
    
    String snapshot = "{\"orderId\":\"TEST-123\"}";
    String key = s3.uploadOrderSnapshot("TEST-123", snapshot);
    
    String downloaded = s3.downloadObject(key);
    assertThat(downloaded).contains("TEST-123");
    
    s3.deleteObject(key);
}
```

## Troubleshooting

### LocalStack not reachable

```bash
# Check LocalStack is running
docker compose ps localstack

# Check LocalStack health
curl http://localhost:4566/_localstack/health
```

### Resources not found

```bash
# Run bootstrap script to create resources
./scripts/localstack-bootstrap.sh

# Or verify resources exist
aws --endpoint-url=http://localhost:4566 s3 ls
aws --endpoint-url=http://localhost:4566 sqs list-queues
```

### Connection refused

1. Ensure LocalStack container is running
2. Check no firewall blocking port 4566
3. Verify Docker network is working

### SDK errors

If you get SDK errors, ensure:
- Using AWS SDK v2 (not v1)
- Force path-style enabled for S3
- Endpoint override is set correctly

## Additional Examples

For more examples, see:
- `README-local.md` - Full local development guide
- `terraform/` - Infrastructure as Code examples
- OMS integration tests - Real-world usage in the codebase

## Next Steps

1. Copy `LocalStackConfig.java` to your project
2. Adapt the examples for your use case
3. Add integration tests using LocalStack
4. Configure CI/CD to use LocalStack for testing
