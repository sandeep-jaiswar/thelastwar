#!/bin/bash
# Benchmark Execution Script for EventBus Performance Testing
# 
# This script runs the complete benchmark suite to validate acceptance criteria:
# - p99 latency < 10 µs
# - Sustained throughput > 2M msgs/s for 60 seconds
# - No major GC events
#
# Usage:
#   ./run-benchmarks.sh [options]
#
# Options:
#   --quick       Run quick benchmarks (shorter duration)
#   --sustained   Run 60-second sustained load tests
#   --full        Run complete benchmark suite (default)
#   --with-jfr    Enable Java Flight Recorder
#   --help        Show this help message

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RESULTS_DIR="$PROJECT_ROOT/benchmark-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default options
RUN_MODE="full"
ENABLE_JFR=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --quick)
            RUN_MODE="quick"
            shift
            ;;
        --sustained)
            RUN_MODE="sustained"
            shift
            ;;
        --full)
            RUN_MODE="full"
            shift
            ;;
        --with-jfr)
            ENABLE_JFR=true
            shift
            ;;
        --help)
            grep "^#" "$0" | grep -v "^#!/" | sed 's/^# //'
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Create results directory
mkdir -p "$RESULTS_DIR/$TIMESTAMP"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}EventBus Benchmark Suite${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "Mode: ${GREEN}$RUN_MODE${NC}"
echo -e "Results: ${GREEN}$RESULTS_DIR/$TIMESTAMP${NC}"
echo -e "JFR Enabled: ${GREEN}$ENABLE_JFR${NC}"
echo ""

# System information
echo -e "${BLUE}System Information:${NC}"
echo -e "CPU: $(grep "model name" /proc/cpuinfo | head -1 | cut -d: -f2 | xargs)"
echo -e "Cores: $(nproc)"
echo -e "Memory: $(free -h | grep Mem | awk '{print $2}')"
echo -e "Java Version: $(java -version 2>&1 | head -1)"
echo ""

# Check CPU governor
echo -e "${BLUE}Checking CPU governor...${NC}"
GOVERNOR=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null || echo "unknown")
if [ "$GOVERNOR" = "performance" ]; then
    echo -e "${GREEN}✓ CPU governor is set to 'performance'${NC}"
else
    echo -e "${YELLOW}⚠ CPU governor is '$GOVERNOR' (recommend 'performance')${NC}"
    echo -e "  Run: ${BLUE}sudo cpupower frequency-set -g performance${NC}"
fi
echo ""

cd "$PROJECT_ROOT"

# Compile benchmarks
echo -e "${BLUE}Compiling benchmarks...${NC}"
./gradlew :core:eventbus:testClasses --no-daemon --quiet
echo -e "${GREEN}✓ Compilation complete${NC}"
echo ""

# JFR arguments
JFR_ARGS=""
if [ "$ENABLE_JFR" = true ]; then
    JFR_FILE="$RESULTS_DIR/$TIMESTAMP/flight-recording.jfr"
    JFR_ARGS="-jvmArgs '-XX:StartFlightRecording=disk=true,dumponexit=true,filename=$JFR_FILE,settings=profile'"
    echo -e "${BLUE}Java Flight Recorder enabled${NC}"
    echo -e "Recording will be saved to: ${GREEN}$JFR_FILE${NC}"
    echo ""
fi

# Function to run a benchmark
run_benchmark() {
    local name=$1
    local args=$2
    local output_file="$RESULTS_DIR/$TIMESTAMP/${name}.txt"
    
    echo -e "${BLUE}Running: ${name}${NC}"
    echo -e "Arguments: ${YELLOW}$args${NC}"
    echo ""
    
    ./gradlew :core:eventbus:jmh --no-daemon -Pargs="$args" $JFR_ARGS \
        2>&1 | tee "$output_file"
    
    if [ ${PIPESTATUS[0]} -eq 0 ]; then
        echo -e "${GREEN}✓ Benchmark completed: $name${NC}"
    else
        echo -e "${RED}✗ Benchmark failed: $name${NC}"
        return 1
    fi
    echo ""
}

# Run benchmarks based on mode
case $RUN_MODE in
    quick)
        echo -e "${BLUE}========================================${NC}"
        echo -e "${BLUE}Quick Benchmark Suite (Short Duration)${NC}"
        echo -e "${BLUE}========================================${NC}"
        echo ""
        
        run_benchmark "quick-publish" \
            "-f 1 -wi 3 -i 5 -r 2 AeronBenchmark.benchmarkPublish"
        
        run_benchmark "quick-throughput" \
            "-f 1 -wi 3 -i 5 -r 2 AeronBenchmark.benchmarkThroughput128B"
        
        run_benchmark "quick-latency" \
            "-f 1 -wi 3 -i 3 -r 2 AeronBenchmark.benchmarkLatencyDistribution"
        ;;
        
    sustained)
        echo -e "${BLUE}========================================${NC}"
        echo -e "${BLUE}Sustained Load Tests (60 seconds)${NC}"
        echo -e "${BLUE}========================================${NC}"
        echo ""
        
        run_benchmark "sustained-aeron-60s" \
            "-f 1 -wi 3 -i 1 -r 60 AeronBenchmark.benchmarkSustained60Seconds"
        
        run_benchmark "sustained-synthetic-60s" \
            "-f 1 -wi 3 -i 1 -r 60 SyntheticLoadBenchmark.benchmarkSustainedThroughput"
        
        run_benchmark "sustained-mixed-workload" \
            "-f 1 -wi 3 -i 1 -r 60 SyntheticLoadBenchmark.benchmarkMixedWorkload"
        ;;
        
    full)
        echo -e "${BLUE}========================================${NC}"
        echo -e "${BLUE}Full Benchmark Suite${NC}"
        echo -e "${BLUE}========================================${NC}"
        echo ""
        
        # Latency benchmarks
        echo -e "${BLUE}--- Latency Benchmarks ---${NC}"
        run_benchmark "latency-publish" \
            "-f 1 -wi 5 -i 10 AeronBenchmark.benchmarkPublish"
        
        run_benchmark "latency-publish-128b" \
            "-f 1 -wi 5 -i 10 AeronBenchmark.benchmarkPublish128B"
        
        run_benchmark "latency-roundtrip" \
            "-f 1 -wi 5 -i 10 AeronBenchmark.benchmarkPublishAndReceive"
        
        # Throughput benchmarks
        echo -e "${BLUE}--- Throughput Benchmarks ---${NC}"
        run_benchmark "throughput-standard" \
            "-f 1 -wi 5 -i 10 AeronBenchmark.benchmarkThroughput"
        
        run_benchmark "throughput-128b" \
            "-f 1 -wi 5 -i 10 AeronBenchmark.benchmarkThroughput128B"
        
        # Latency distribution (for percentiles)
        echo -e "${BLUE}--- Latency Distribution (Percentiles) ---${NC}"
        run_benchmark "latency-distribution" \
            "-f 1 -wi 5 -i 1 -r 30 AeronBenchmark.benchmarkLatencyDistribution"
        
        # Sustained load tests
        echo -e "${BLUE}--- Sustained Load Tests (60s) ---${NC}"
        run_benchmark "sustained-60s" \
            "-f 1 -wi 3 -i 1 -r 60 AeronBenchmark.benchmarkSustained60Seconds"
        
        # Synthetic load tests
        echo -e "${BLUE}--- Synthetic Load Tests ---${NC}"
        run_benchmark "synthetic-sustained" \
            "-f 1 -wi 3 -i 1 -r 60 SyntheticLoadBenchmark.benchmarkSustainedThroughput"
        
        run_benchmark "synthetic-mixed" \
            "-f 1 -wi 3 -i 1 -r 60 SyntheticLoadBenchmark.benchmarkMixedWorkload"
        
        run_benchmark "synthetic-highfreq" \
            "-f 1 -wi 3 -i 1 -r 30 SyntheticLoadBenchmark.benchmarkHighFrequencyTicks"
        ;;
esac

# Analyze GC logs
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}GC Analysis${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

if [ -f /tmp/aeron_gc.log ]; then
    echo -e "${BLUE}Analyzing GC log...${NC}"
    
    # Count GC events
    MINOR_GC=$(grep -c "Pause Young" /tmp/aeron_gc.log 2>/dev/null || echo 0)
    MAJOR_GC=$(grep -c "Pause Full" /tmp/aeron_gc.log 2>/dev/null || echo 0)
    
    # Find max pause time
    MAX_PAUSE=$(grep "Pause" /tmp/aeron_gc.log | grep -oP '\d+\.\d+ms' | sort -n | tail -1 || echo "N/A")
    
    echo -e "Minor GC events: ${YELLOW}$MINOR_GC${NC}"
    echo -e "Major GC events: ${YELLOW}$MAJOR_GC${NC}"
    echo -e "Max GC pause: ${YELLOW}$MAX_PAUSE${NC}"
    
    if [ "$MAJOR_GC" -eq 0 ]; then
        echo -e "${GREEN}✓ No major GC events (PASSED)${NC}"
    else
        echo -e "${RED}✗ Major GC events detected (FAILED)${NC}"
    fi
    
    # Copy GC log to results
    cp /tmp/aeron_gc.log "$RESULTS_DIR/$TIMESTAMP/gc.log"
    echo -e "GC log saved to: ${GREEN}$RESULTS_DIR/$TIMESTAMP/gc.log${NC}"
else
    echo -e "${YELLOW}⚠ No GC log found at /tmp/aeron_gc.log${NC}"
fi
echo ""

# Summary
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Benchmark Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "Results directory: ${GREEN}$RESULTS_DIR/$TIMESTAMP${NC}"
echo ""
echo -e "${BLUE}Validation Checklist:${NC}"
echo ""
echo -e "1. ${BLUE}p99 Latency < 10 µs${NC}"
echo -e "   Check: latency-distribution.txt for p99.99 value"
echo -e "   Expected: < 10,000 ns"
echo ""
echo -e "2. ${BLUE}Throughput > 2M msgs/s (60s)${NC}"
echo -e "   Check: sustained-60s.txt or sustained-synthetic-60s.txt"
echo -e "   Expected: Score > 2,000,000 ops/s"
echo ""
echo -e "3. ${BLUE}No Major GC Events${NC}"
echo -e "   Check: gc.log or analysis above"
echo -e "   Expected: 0 Full GC events"
echo ""

if [ "$ENABLE_JFR" = true ]; then
    echo -e "${BLUE}Flight Recorder Analysis:${NC}"
    echo -e "Open recording with Java Mission Control:"
    echo -e "  ${GREEN}jmc $JFR_FILE${NC}"
    echo ""
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Benchmark suite completed!${NC}"
echo -e "${GREEN}========================================${NC}"
