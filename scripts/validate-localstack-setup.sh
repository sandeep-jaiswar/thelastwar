#!/bin/bash

# LocalStack Setup Validation Script
# This script validates that all LocalStack resources are properly configured

set -e

echo "=========================================="
echo "LocalStack Setup Validation"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Set AWS CLI to use LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
LOCALSTACK_ENDPOINT=http://localhost:4566

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to print status
print_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓${NC} $2"
    else
        echo -e "${RED}✗${NC} $2"
        return 1
    fi
}

# Function to print warning
print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Function to print info
print_info() {
    echo -e "ℹ $1"
}

echo ""
echo "Step 1: Checking Prerequisites"
echo "----------------------------------------"

if command_exists aws; then
    print_status 0 "AWS CLI is installed"
else
    print_status 1 "AWS CLI is not installed"
    print_info "Install with: pip install awscli"
    exit 1
fi

if command_exists docker; then
    print_status 0 "Docker is installed"
else
    print_status 1 "Docker is not installed"
    exit 1
fi

if command_exists docker-compose || docker compose version >/dev/null 2>&1; then
    print_status 0 "Docker Compose is available"
else
    print_status 1 "Docker Compose is not available"
    exit 1
fi

echo ""
echo "Step 2: Checking LocalStack Service"
echo "----------------------------------------"

if curl -s http://localhost:4566/_localstack/health >/dev/null 2>&1; then
    print_status 0 "LocalStack is running"
else
    print_status 1 "LocalStack is not running"
    print_info "Start with: docker compose up -d localstack"
    exit 1
fi

# Check service health
health=$(curl -s http://localhost:4566/_localstack/health)
echo "$health" | jq . 2>/dev/null || echo "$health"

echo ""
echo "Step 3: Validating S3 Buckets"
echo "----------------------------------------"

expected_buckets=(
    "trading-orders"
    "trading-executions"
    "trading-positions"
    "trading-snapshots"
    "trading-analytics"
    "trading-reports"
)

buckets=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls 2>/dev/null || echo "")

for bucket in "${expected_buckets[@]}"; do
    if echo "$buckets" | grep -q "$bucket"; then
        print_status 0 "Bucket $bucket exists"
    else
        print_status 1 "Bucket $bucket does not exist"
    fi
done

echo ""
echo "Step 4: Validating SQS Queues"
echo "----------------------------------------"

expected_queues=(
    "order-events"
    "execution-events"
    "risk-alerts"
    "position-updates"
    "market-data-events"
)

queues=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs list-queues --output text 2>/dev/null || echo "")

for queue in "${expected_queues[@]}"; do
    if echo "$queues" | grep -q "$queue"; then
        print_status 0 "Queue $queue exists"
    else
        print_status 1 "Queue $queue does not exist"
    fi
done

echo ""
echo "Step 5: Validating SNS Topics"
echo "----------------------------------------"

expected_topics=(
    "order-notifications"
    "execution-notifications"
    "risk-alerts-topic"
    "system-alerts"
)

topics=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sns list-topics --output text 2>/dev/null || echo "")

for topic in "${expected_topics[@]}"; do
    if echo "$topics" | grep -q "$topic"; then
        print_status 0 "Topic $topic exists"
    else
        print_status 1 "Topic $topic does not exist"
    fi
done

echo ""
echo "Step 6: Validating Secrets Manager"
echo "----------------------------------------"

expected_secrets=(
    "fix/credentials"
    "api/credentials"
    "database/credentials"
)

secrets=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT secretsmanager list-secrets --output text 2>/dev/null || echo "")

for secret in "${expected_secrets[@]}"; do
    if echo "$secrets" | grep -q "$secret"; then
        print_status 0 "Secret $secret exists"
    else
        print_status 1 "Secret $secret does not exist"
    fi
done

echo ""
echo "Step 7: Validating DynamoDB Tables"
echo "----------------------------------------"

expected_tables=(
    "trading-sessions"
    "order-state"
)

tables=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT dynamodb list-tables --output text 2>/dev/null || echo "")

for table in "${expected_tables[@]}"; do
    if echo "$tables" | grep -q "$table"; then
        print_status 0 "Table $table exists"
    else
        print_status 1 "Table $table does not exist"
    fi
done

echo ""
echo "Step 8: Checking Other Services"
echo "----------------------------------------"

# PostgreSQL
if docker compose exec -T postgres pg_isready -U trading_user -d trading >/dev/null 2>&1; then
    print_status 0 "PostgreSQL is ready"
else
    print_warning "PostgreSQL is not ready or not running"
fi

# Redis
if docker compose exec -T redis redis-cli ping >/dev/null 2>&1; then
    print_status 0 "Redis is ready"
else
    print_warning "Redis is not ready or not running"
fi

# ClickHouse
if curl -s http://localhost:8123/ping >/dev/null 2>&1; then
    print_status 0 "ClickHouse is ready"
else
    print_warning "ClickHouse is not ready or not running"
fi

# Redpanda
if docker compose exec -T redpanda rpk cluster health >/dev/null 2>&1; then
    print_status 0 "Redpanda (Kafka) is ready"
else
    print_warning "Redpanda is not ready or not running"
fi

echo ""
echo "=========================================="
echo "Validation Complete!"
echo "=========================================="
echo ""
echo "All LocalStack resources are configured correctly."
echo "You can now use the local development environment."
echo ""
echo "Next steps:"
echo "  - Build the project: ./gradlew build"
echo "  - Run examples: ./gradlew :examples:localstack-integration:run"
echo "  - See README-local.md for more information"
echo ""
