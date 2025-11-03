#!/bin/bash
# Start all services for The Last War Trading System

set -e

KAFKA_DIR="/opt/kafka"
LOG_DIR="/var/lib/trading-system/logs"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Starting Trading System Services${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Create log directory if it doesn't exist
mkdir -p "$LOG_DIR"

# Function to check if a process is running
is_running() {
    pgrep -f "$1" > /dev/null 2>&1
}

# Step 1: Start ClickHouse
echo -e "${BLUE}Step 1: Starting ClickHouse...${NC}"
if sudo systemctl is-active --quiet clickhouse-server; then
    echo -e "${GREEN}✓ ClickHouse is already running${NC}"
else
    sudo systemctl start clickhouse-server
    sleep 3
    if sudo systemctl is-active --quiet clickhouse-server; then
        echo -e "${GREEN}✓ ClickHouse started successfully${NC}"
    else
        echo -e "${RED}✗ Failed to start ClickHouse${NC}"
        exit 1
    fi
fi
echo ""

# Step 2: Start Zookeeper
echo -e "${BLUE}Step 2: Starting Zookeeper...${NC}"
if is_running "zookeeper"; then
    echo -e "${GREEN}✓ Zookeeper is already running${NC}"
else
    nohup $KAFKA_DIR/bin/zookeeper-server-start.sh \
        $KAFKA_DIR/config/zookeeper.properties \
        > $LOG_DIR/zookeeper.log 2>&1 &
    
    echo "Waiting for Zookeeper to start..."
    sleep 5
    
    if is_running "zookeeper"; then
        echo -e "${GREEN}✓ Zookeeper started successfully${NC}"
    else
        echo -e "${RED}✗ Failed to start Zookeeper${NC}"
        exit 1
    fi
fi
echo ""

# Step 3: Start Kafka
echo -e "${BLUE}Step 3: Starting Kafka...${NC}"
if is_running "kafka.Kafka"; then
    echo -e "${GREEN}✓ Kafka is already running${NC}"
else
    nohup $KAFKA_DIR/bin/kafka-server-start.sh \
        $KAFKA_DIR/config/server.properties \
        > $LOG_DIR/kafka.log 2>&1 &
    
    echo "Waiting for Kafka to start..."
    sleep 10
    
    if is_running "kafka.Kafka"; then
        echo -e "${GREEN}✓ Kafka started successfully${NC}"
    else
        echo -e "${RED}✗ Failed to start Kafka${NC}"
        exit 1
    fi
fi
echo ""

# Step 4: Create Kafka topics
echo -e "${BLUE}Step 4: Creating Kafka topics...${NC}"

create_topic() {
    local topic=$1
    if $KAFKA_DIR/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep -q "^$topic$"; then
        echo -e "${GREEN}✓ Topic '$topic' already exists${NC}"
    else
        $KAFKA_DIR/bin/kafka-topics.sh --create \
            --bootstrap-server localhost:9092 \
            --replication-factor 1 \
            --partitions 8 \
            --topic $topic
        echo -e "${GREEN}✓ Topic '$topic' created${NC}"
    fi
}

create_topic "oms-events"
create_topic "execution-events"
create_topic "trade-events"
echo ""

# Step 5: Initialize ClickHouse schema
echo -e "${BLUE}Step 5: Initializing ClickHouse schema...${NC}"
clickhouse-client --database=trading_system --user=trading_user --password=trading_password << 'EOF' || true
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

echo -e "${GREEN}✓ ClickHouse schema initialized${NC}"
echo ""

# Summary
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}All Services Started Successfully!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${GREEN}Running services:${NC}"
echo -e "  ✓ ClickHouse (port 8123)"
echo -e "  ✓ Zookeeper (port 2181)"
echo -e "  ✓ Kafka (port 9092)"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo -e "  1. Check service status: ${GREEN}./scripts/check-services.sh${NC}"
echo -e "  2. View logs: ${GREEN}tail -f $LOG_DIR/*.log${NC}"
echo -e "  3. Run the application components"
echo -e "  4. Stop services: ${GREEN}./scripts/stop-services.sh${NC}"
echo ""
echo -e "${BLUE}Kafka topics created:${NC}"
$KAFKA_DIR/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
echo ""
