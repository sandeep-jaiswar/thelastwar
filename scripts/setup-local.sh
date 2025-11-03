#!/bin/bash
# Complete Local Setup Script for The Last War Trading System
# This script sets up all dependencies for running on a local Linux machine
# without Docker or Kubernetes

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}The Last War - Local Setup${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if running on Linux
if [[ "$OSTYPE" != "linux-gnu"* ]]; then
    echo -e "${RED}Error: This script is designed for Linux systems${NC}"
    echo -e "${YELLOW}Current OS: $OSTYPE${NC}"
    exit 1
fi

echo -e "${GREEN}Running on Linux - proceeding with setup${NC}"
echo ""

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to check Java version
check_java_version() {
    if command_exists java; then
        JAVA_VERSION=$(java -version 2>&1 | grep -oP 'version "?\K[0-9]+' | head -1)
        if [ "$JAVA_VERSION" -ge 21 ]; then
            echo -e "${GREEN}✓ Java $JAVA_VERSION detected${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠ Java $JAVA_VERSION detected, but Java 21+ is required${NC}"
            return 1
        fi
    else
        echo -e "${RED}✗ Java not found${NC}"
        return 1
    fi
}

# Step 1: Install Java 21
echo -e "${BLUE}Step 1: Checking Java installation...${NC}"
if check_java_version; then
    echo -e "${GREEN}Java is already installed and meets requirements${NC}"
else
    echo -e "${YELLOW}Installing OpenJDK 21...${NC}"
    if command_exists apt-get; then
        sudo apt-get update
        sudo apt-get install -y openjdk-21-jdk
        
        # Set JAVA_HOME
        JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
        echo "export JAVA_HOME=$JAVA_HOME" >> ~/.bashrc
        echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> ~/.bashrc
        export JAVA_HOME
        export PATH=$JAVA_HOME/bin:$PATH
        
        echo -e "${GREEN}✓ Java 21 installed successfully${NC}"
    else
        echo -e "${RED}✗ apt-get not found. Please install Java 21 manually${NC}"
        exit 1
    fi
fi
echo ""

# Step 2: Install ClickHouse
echo -e "${BLUE}Step 2: Checking ClickHouse installation...${NC}"
if command_exists clickhouse-client; then
    echo -e "${GREEN}✓ ClickHouse already installed${NC}"
else
    echo -e "${YELLOW}Installing ClickHouse...${NC}"
    
    # Add ClickHouse repository
    sudo apt-get install -y apt-transport-https ca-certificates dirmngr
    sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 8919F6BD2B48D754
    
    echo "deb https://packages.clickhouse.com/deb stable main" | sudo tee \
        /etc/apt/sources.list.d/clickhouse.list
    
    sudo apt-get update
    sudo apt-get install -y clickhouse-server clickhouse-client
    
    # Start ClickHouse
    sudo systemctl start clickhouse-server
    sudo systemctl enable clickhouse-server
    
    echo -e "${GREEN}✓ ClickHouse installed successfully${NC}"
fi

# Configure ClickHouse
echo -e "${YELLOW}Configuring ClickHouse database...${NC}"
sleep 2  # Give ClickHouse time to start

clickhouse-client --query "CREATE DATABASE IF NOT EXISTS trading_system" || true
clickhouse-client --query "CREATE USER IF NOT EXISTS trading_user IDENTIFIED BY 'trading_password'" || true
clickhouse-client --query "GRANT ALL ON trading_system.* TO trading_user" || true

# Test ClickHouse connection
if clickhouse-client --database=trading_system --user=trading_user --password=trading_password \
    --query "SELECT 'ClickHouse is ready!'" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ ClickHouse database configured and accessible${NC}"
else
    echo -e "${RED}✗ Failed to configure ClickHouse database${NC}"
    exit 1
fi
echo ""

# Step 3: Install Kafka
echo -e "${BLUE}Step 3: Checking Kafka installation...${NC}"
KAFKA_DIR="/opt/kafka"

if [ -d "$KAFKA_DIR" ]; then
    echo -e "${GREEN}✓ Kafka already installed at $KAFKA_DIR${NC}"
else
    echo -e "${YELLOW}Installing Kafka...${NC}"
    
    cd /tmp
    wget https://downloads.apache.org/kafka/3.8.0/kafka_2.13-3.8.0.tgz
    sudo tar -xzf kafka_2.13-3.8.0.tgz -C /opt
    sudo mv /opt/kafka_2.13-3.8.0 $KAFKA_DIR
    rm kafka_2.13-3.8.0.tgz
    
    # Create Kafka directories
    sudo mkdir -p /var/lib/kafka/data
    sudo mkdir -p /var/lib/kafka/logs
    sudo chown -R $USER:$USER /var/lib/kafka
    
    # Configure Kafka
    cat > $KAFKA_DIR/config/server.properties << EOF
broker.id=0
listeners=PLAINTEXT://localhost:9092
log.dirs=/var/lib/kafka/data
num.partitions=8
default.replication.factor=1
min.insync.replicas=1
log.retention.hours=168
log.segment.bytes=1073741824
zookeeper.connect=localhost:2181
EOF
    
    echo -e "${GREEN}✓ Kafka installed successfully${NC}"
fi
echo ""

# Step 4: System Performance Tuning
echo -e "${BLUE}Step 4: Applying system performance tuning...${NC}"

# Check if running with sudo
if [ "$EUID" -eq 0 ]; then
    echo -e "${YELLOW}Running as root - applying system tuning${NC}"
    
    # Set CPU governor to performance
    echo performance | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor > /dev/null 2>&1 || echo "Could not set CPU governor"
    
    # Increase file descriptors
    if ! grep -q "soft nofile 65536" /etc/security/limits.conf; then
        echo "* soft nofile 65536" >> /etc/security/limits.conf
        echo "* hard nofile 65536" >> /etc/security/limits.conf
    fi
    
    echo -e "${GREEN}✓ System tuning applied${NC}"
else
    echo -e "${YELLOW}⚠ Not running as root - skipping system tuning${NC}"
    echo -e "${YELLOW}  Run with sudo for optimal performance tuning${NC}"
fi
echo ""

# Step 5: Create application directories
echo -e "${BLUE}Step 5: Creating application directories...${NC}"
mkdir -p /var/lib/trading-system/snapshots || sudo mkdir -p /var/lib/trading-system/snapshots
mkdir -p /var/lib/trading-system/logs || sudo mkdir -p /var/lib/trading-system/logs
sudo chown -R $USER:$USER /var/lib/trading-system 2>/dev/null || true
echo -e "${GREEN}✓ Application directories created${NC}"
echo ""

# Step 6: Build the project
echo -e "${BLUE}Step 6: Building the trading system...${NC}"
cd "$PROJECT_ROOT"

echo -e "${YELLOW}Running Gradle build (this may take a few minutes)...${NC}"
./gradlew clean build -x test

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Build completed successfully${NC}"
else
    echo -e "${RED}✗ Build failed${NC}"
    exit 1
fi
echo ""

# Summary
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Setup Complete!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${GREEN}All components have been installed and configured:${NC}"
echo -e "  ✓ Java 21"
echo -e "  ✓ ClickHouse (trading_system database)"
echo -e "  ✓ Kafka"
echo -e "  ✓ Application directories"
echo -e "  ✓ Project built successfully"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo -e "  1. Start services: ${GREEN}./scripts/start-services.sh${NC}"
echo -e "  2. Check status: ${GREEN}./scripts/check-services.sh${NC}"
echo -e "  3. View logs: ${GREEN}tail -f /var/lib/trading-system/logs/*.log${NC}"
echo -e "  4. Stop services: ${GREEN}./scripts/stop-services.sh${NC}"
echo ""
echo -e "${YELLOW}For detailed documentation, see LOCAL_SETUP.md${NC}"
echo ""
