#!/bin/bash
# Check status of all services for The Last War Trading System

KAFKA_DIR="/opt/kafka"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Trading System Services Status${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check ClickHouse
echo -e "${BLUE}ClickHouse:${NC}"
if sudo systemctl is-active --quiet clickhouse-server; then
    echo -e "  Status: ${GREEN}Running ✓${NC}"
    if clickhouse-client --query "SELECT version()" > /dev/null 2>&1; then
        VERSION=$(clickhouse-client --query "SELECT version()")
        echo -e "  Version: $VERSION"
        echo -e "  Connection: ${GREEN}OK ✓${NC}"
    else
        echo -e "  Connection: ${RED}Failed ✗${NC}"
    fi
else
    echo -e "  Status: ${RED}Not running ✗${NC}"
fi
echo ""

# Check Kafka (KRaft mode)
echo -e "${BLUE}Kafka (KRaft mode - no Zookeeper):${NC}"
if pgrep -f "kafka.Kafka" > /dev/null; then
    echo -e "  Status: ${GREEN}Running ✓${NC}"
    PID=$(pgrep -f "kafka.Kafka")
    echo -e "  PID: $PID"
    
    # Check Kafka connectivity
    if $KAFKA_DIR/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1; then
        echo -e "  Connection: ${GREEN}OK ✓${NC}"
        
        # List topics
        echo -e "${BLUE}  Topics:${NC}"
        $KAFKA_DIR/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | sed 's/^/    /'
    else
        echo -e "  Connection: ${RED}Failed ✗${NC}"
    fi
else
    echo -e "  Status: ${RED}Not running ✗${NC}"
fi
echo ""

# Check ClickHouse database
echo -e "${BLUE}ClickHouse Database:${NC}"
if clickhouse-client --database=trading_system --user=trading_user --password=trading_password \
    --query "SELECT 1" > /dev/null 2>&1; then
    echo -e "  Database: ${GREEN}trading_system ✓${NC}"
    
    # Check if orders table exists
    if clickhouse-client --database=trading_system --user=trading_user --password=trading_password \
        --query "EXISTS TABLE oms_order_state" 2>&1 | grep -q "1"; then
        echo -e "  Table oms_order_state: ${GREEN}Exists ✓${NC}"
        
        # Get row count
        COUNT=$(clickhouse-client --database=trading_system --user=trading_user --password=trading_password \
            --query "SELECT count() FROM oms_order_state")
        echo -e "  Records: $COUNT"
    else
        echo -e "  Table oms_order_state: ${YELLOW}Not created yet${NC}"
    fi
else
    echo -e "  Database: ${RED}Not accessible ✗${NC}"
fi
echo ""

# Overall status
echo -e "${BLUE}========================================${NC}"
ALL_OK=true

if ! sudo systemctl is-active --quiet clickhouse-server; then
    ALL_OK=false
fi

if ! pgrep -f "kafka.Kafka" > /dev/null; then
    ALL_OK=false
fi

if [ "$ALL_OK" = true ]; then
    echo -e "${GREEN}All services are running ✓${NC}"
else
    echo -e "${RED}Some services are not running ✗${NC}"
    echo -e "${YELLOW}Run ./scripts/start-services.sh to start services${NC}"
fi
echo ""
