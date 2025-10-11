#!/bin/bash

# REST Gateway Load Test Runner
# Uses wrk to test REST API endpoints under load

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
HOST="${REST_HOST:-localhost}"
PORT="${REST_PORT:-8080}"
BASE_URL="http://${HOST}:${PORT}"
REPORT_DIR="build/reports/load-tests"
LUA_SCRIPT="core/restgateway/load-tests/order_submit.lua"

# Test parameters
THREADS="${THREADS:-4}"
CONNECTIONS="${CONNECTIONS:-100}"
DURATION="${DURATION:-30s}"

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}REST Gateway Load Test${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""

# Check if wrk is installed
if ! command -v wrk &> /dev/null; then
    echo -e "${YELLOW}Warning: wrk is not installed${NC}"
    echo "To install on Ubuntu/Debian: sudo apt-get install wrk"
    echo "To install on macOS: brew install wrk"
    echo ""
    echo -e "${YELLOW}Skipping load test...${NC}"
    exit 0
fi

# Create report directory
mkdir -p "${REPORT_DIR}"

# Run the load test
echo -e "${GREEN}Starting load test...${NC}"
echo "  Target: ${BASE_URL}/api/orders"
echo "  Threads: ${THREADS}"
echo "  Connections: ${CONNECTIONS}"
echo "  Duration: ${DURATION}"
echo ""

REPORT_FILE="${REPORT_DIR}/rest_gateway_load_test_$(date +%Y%m%d_%H%M%S).txt"

# Run wrk
if [ -f "${LUA_SCRIPT}" ]; then
    echo -e "${BLUE}Running with Lua script...${NC}"
    wrk -t${THREADS} -c${CONNECTIONS} -d${DURATION} --latency \
        -s "${LUA_SCRIPT}" \
        "${BASE_URL}/api/orders" | tee "${REPORT_FILE}"
else
    echo -e "${YELLOW}Lua script not found, running basic test...${NC}"
    wrk -t${THREADS} -c${CONNECTIONS} -d${DURATION} --latency \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer test-token" \
        "${BASE_URL}/api/orders" | tee "${REPORT_FILE}"
fi

echo ""
echo -e "${GREEN}Load test complete!${NC}"
echo -e "Report saved to: ${REPORT_FILE}"

# Parse results and validate acceptance criteria
if [ -f "${REPORT_FILE}" ]; then
    echo ""
    echo -e "${BLUE}Validating Acceptance Criteria:${NC}"
    
    # Extract throughput (requests/sec)
    THROUGHPUT=$(grep "Requests/sec:" "${REPORT_FILE}" | awk '{print $2}')
    
    if [ -n "${THROUGHPUT}" ]; then
        echo "  Throughput: ${THROUGHPUT} req/s"
        
        # Check if throughput >= 10,000 req/s
        THROUGHPUT_INT=$(echo "${THROUGHPUT}" | cut -d'.' -f1)
        if [ "${THROUGHPUT_INT}" -ge 10000 ]; then
            echo -e "  ${GREEN}✓ Throughput >= 10,000 req/s: PASS${NC}"
        else
            echo -e "  ${YELLOW}⚠ Throughput < 10,000 req/s: ${THROUGHPUT_INT} (target: 10000)${NC}"
        fi
    fi
    
    # Extract latency
    AVG_LATENCY=$(grep "Latency" "${REPORT_FILE}" | head -1 | awk '{print $2}')
    if [ -n "${AVG_LATENCY}" ]; then
        echo "  Avg Latency: ${AVG_LATENCY}"
        
        # Check if latency < 3ms (simple check, may need adjustment)
        if [[ "${AVG_LATENCY}" == *"ms"* ]]; then
            LATENCY_VAL=$(echo "${AVG_LATENCY}" | sed 's/ms//')
            if (( $(echo "${LATENCY_VAL} < 3.0" | bc -l) )); then
                echo -e "  ${GREEN}✓ Latency < 3 ms: PASS${NC}"
            else
                echo -e "  ${YELLOW}⚠ Latency >= 3 ms: ${AVG_LATENCY}${NC}"
            fi
        fi
    fi
fi

echo ""
echo -e "${BLUE}=====================================${NC}"
