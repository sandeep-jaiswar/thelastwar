#!/bin/bash

# LocalStack Bootstrap Script
# This script initializes LocalStack with mock AWS resources for local development

set -e

echo "=========================================="
echo "LocalStack Bootstrap Starting..."
echo "=========================================="

# Wait for LocalStack to be ready
echo "Waiting for LocalStack to be ready..."
max_attempts=30
attempt=0
until curl -s http://localhost:4566/_localstack/health | grep -q '"s3": "running"' || [ $attempt -eq $max_attempts ]; do
    echo "Attempt $((attempt+1))/$max_attempts - LocalStack not ready yet..."
    sleep 2
    attempt=$((attempt+1))
done

if [ $attempt -eq $max_attempts ]; then
    echo "ERROR: LocalStack failed to start within the expected time"
    exit 1
fi

echo "LocalStack is ready!"

# Set AWS CLI to use LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
LOCALSTACK_ENDPOINT=http://localhost:4566

# Function to check if AWS CLI is available
check_aws_cli() {
    if ! command -v aws &> /dev/null; then
        echo "AWS CLI not found, using awslocal or curl fallback"
        return 1
    fi
    return 0
}

echo ""
echo "=========================================="
echo "Creating S3 Buckets..."
echo "=========================================="

# Create S3 buckets for trade data
BUCKETS=(
    "trading-orders"
    "trading-executions"
    "trading-positions"
    "trading-snapshots"
    "trading-analytics"
    "trading-reports"
)

for bucket in "${BUCKETS[@]}"; do
    echo "Creating bucket: $bucket"
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://$bucket 2>/dev/null || echo "Bucket $bucket already exists"
    echo "✓ Bucket $bucket ready"
done

echo ""
echo "=========================================="
echo "Creating SQS Queues..."
echo "=========================================="

# Create SQS queues for event processing
QUEUES=(
    "order-events"
    "execution-events"
    "risk-alerts"
    "position-updates"
    "market-data-events"
    "trade-reconciliation"
)

for queue in "${QUEUES[@]}"; do
    echo "Creating queue: $queue"
    aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue --queue-name $queue 2>/dev/null || echo "Queue $queue already exists"
    echo "✓ Queue $queue ready"
done

echo ""
echo "=========================================="
echo "Creating SNS Topics..."
echo "=========================================="

# Create SNS topics for notifications
TOPICS=(
    "order-notifications"
    "execution-notifications"
    "risk-alerts-topic"
    "system-alerts"
    "trade-confirmations"
)

for topic in "${TOPICS[@]}"; do
    echo "Creating topic: $topic"
    topic_arn=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sns create-topic --name $topic --output text 2>/dev/null || echo "")
    if [ -n "$topic_arn" ]; then
        echo "✓ Topic $topic ready (ARN: $topic_arn)"
    else
        echo "✓ Topic $topic already exists"
    fi
done

echo ""
echo "=========================================="
echo "Creating Secrets in Secrets Manager..."
echo "=========================================="

# Create secrets for FIX and API credentials
echo "Creating secret: fix/credentials"
aws --endpoint-url=$LOCALSTACK_ENDPOINT secretsmanager create-secret \
    --name fix/credentials \
    --description "Mock FIX protocol credentials" \
    --secret-string '{
        "senderCompId": "BROKER001",
        "targetCompId": "EXCHANGE001",
        "username": "fix_user",
        "password": "fix_password",
        "host": "localhost",
        "port": "9876"
    }' 2>/dev/null || echo "Secret fix/credentials already exists"

echo "Creating secret: api/credentials"
aws --endpoint-url=$LOCALSTACK_ENDPOINT secretsmanager create-secret \
    --name api/credentials \
    --description "Mock API credentials" \
    --secret-string '{
        "apiKey": "mock-api-key-12345",
        "apiSecret": "mock-api-secret-67890",
        "traderId": "TRADER001",
        "accountId": "ACC12345"
    }' 2>/dev/null || echo "Secret api/credentials already exists"

echo "Creating secret: database/credentials"
aws --endpoint-url=$LOCALSTACK_ENDPOINT secretsmanager create-secret \
    --name database/credentials \
    --description "Database credentials" \
    --secret-string '{
        "postgres": {
            "host": "postgres",
            "port": 5432,
            "database": "trading",
            "username": "trading_user",
            "password": "trading_pass"
        },
        "clickhouse": {
            "host": "clickhouse",
            "port": 8123,
            "database": "ticks",
            "username": "clickhouse_user",
            "password": "clickhouse_pass"
        },
        "redis": {
            "host": "redis",
            "port": 6379
        }
    }' 2>/dev/null || echo "Secret database/credentials already exists"

echo "✓ All secrets created"

echo ""
echo "=========================================="
echo "Creating DynamoDB Tables..."
echo "=========================================="

# Create DynamoDB table for session state
echo "Creating table: trading-sessions"
aws --endpoint-url=$LOCALSTACK_ENDPOINT dynamodb create-table \
    --table-name trading-sessions \
    --attribute-definitions \
        AttributeName=sessionId,AttributeType=S \
        AttributeName=timestamp,AttributeType=N \
    --key-schema \
        AttributeName=sessionId,KeyType=HASH \
        AttributeName=timestamp,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST 2>/dev/null || echo "Table trading-sessions already exists"

echo "Creating table: order-state"
aws --endpoint-url=$LOCALSTACK_ENDPOINT dynamodb create-table \
    --table-name order-state \
    --attribute-definitions \
        AttributeName=orderId,AttributeType=S \
    --key-schema \
        AttributeName=orderId,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST 2>/dev/null || echo "Table order-state already exists"

echo "✓ All DynamoDB tables created"

echo ""
echo "=========================================="
echo "Validating Resources..."
echo "=========================================="

# Validate S3 buckets
echo -n "Validating S3 buckets... "
bucket_count=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls | wc -l)
echo "✓ Found $bucket_count buckets"

# Validate SQS queues
echo -n "Validating SQS queues... "
queue_count=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs list-queues --output text 2>/dev/null | wc -l)
echo "✓ Found $queue_count queues"

# Validate SNS topics
echo -n "Validating SNS topics... "
topic_count=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sns list-topics --output text 2>/dev/null | wc -l)
echo "✓ Found $topic_count topics"

# Validate secrets
echo -n "Validating Secrets Manager... "
secret_count=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT secretsmanager list-secrets --output text 2>/dev/null | wc -l)
echo "✓ Found $secret_count secrets"

# Validate DynamoDB tables
echo -n "Validating DynamoDB tables... "
table_count=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT dynamodb list-tables --output text 2>/dev/null | wc -l)
echo "✓ Found $table_count tables"

echo ""
echo "=========================================="
echo "LocalStack Bootstrap Complete!"
echo "=========================================="
echo ""
echo "Available Resources:"
echo "  - S3 Buckets: ${BUCKETS[*]}"
echo "  - SQS Queues: ${QUEUES[*]}"
echo "  - SNS Topics: ${TOPICS[*]}"
echo "  - Secrets: fix/credentials, api/credentials, database/credentials"
echo "  - DynamoDB Tables: trading-sessions, order-state"
echo ""
echo "LocalStack Endpoint: http://localhost:4566"
echo ""
