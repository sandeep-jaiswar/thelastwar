#!/bin/bash
# Stop all services for The Last War Trading System

KAFKA_DIR="/opt/kafka"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Stopping Trading System Services${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Track which services were stopped
STOPPED_SERVICES=()

# Step 1: Stop Kafka
echo -e "${BLUE}Step 1: Stopping Kafka...${NC}"
if pgrep -f "kafka.Kafka" > /dev/null; then
    $KAFKA_DIR/bin/kafka-server-stop.sh
    sleep 5
    
    # Force kill if still running
    if pgrep -f "kafka.Kafka" > /dev/null; then
        pkill -9 -f "kafka.Kafka"
    fi
    
    echo -e "${GREEN}✓ Kafka stopped${NC}"
    STOPPED_SERVICES+=("Kafka")
else
    echo -e "${YELLOW}⚠ Kafka is not running${NC}"
fi
echo ""

# Step 2: Stop Zookeeper
echo -e "${BLUE}Step 2: Stopping Zookeeper...${NC}"
if pgrep -f "zookeeper" > /dev/null; then
    $KAFKA_DIR/bin/zookeeper-server-stop.sh
    sleep 3
    
    # Force kill if still running
    if pgrep -f "zookeeper" > /dev/null; then
        pkill -9 -f "zookeeper"
    fi
    
    echo -e "${GREEN}✓ Zookeeper stopped${NC}"
    STOPPED_SERVICES+=("Zookeeper")
else
    echo -e "${YELLOW}⚠ Zookeeper is not running${NC}"
fi
echo ""

# Step 3: Stop ClickHouse (optional - commented out by default)
echo -e "${BLUE}Step 3: ClickHouse status...${NC}"
if sudo systemctl is-active --quiet clickhouse-server; then
    echo -e "${YELLOW}⚠ ClickHouse is still running${NC}"
    echo -e "${YELLOW}  To stop: sudo systemctl stop clickhouse-server${NC}"
else
    echo -e "${GREEN}✓ ClickHouse is not running${NC}"
fi
echo ""

# Summary
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Services Stopped${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

if [ ${#STOPPED_SERVICES[@]} -gt 0 ]; then
    echo -e "${GREEN}Stopped services:${NC}"
    for service in "${STOPPED_SERVICES[@]}"; do
        echo -e "  ✓ $service"
    done
else
    echo -e "${YELLOW}No services were stopped (none were running)${NC}"
fi

echo ""
echo -e "${YELLOW}Note: ClickHouse is left running by default${NC}"
echo -e "To stop ClickHouse: ${GREEN}sudo systemctl stop clickhouse-server${NC}"
echo ""
