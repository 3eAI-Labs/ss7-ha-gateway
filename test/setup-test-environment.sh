#!/bin/bash

# SS7 HA Gateway Test Environment Setup
# ======================================
# This script sets up JSS7 simulators to test the SS7 HA Gateway

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
JSS7_DIR="/home/lsg/Workspaces/telco/jss7"
SIMULATOR_DIR="$JSS7_DIR/tools/simulator"
TEST_CONFIG_DIR="$SCRIPT_DIR/config"

echo "================================================"
echo "SS7 HA Gateway Test Environment Setup"
echo "================================================"
echo ""

# Check if JSS7 simulator exists
if [ ! -d "$SIMULATOR_DIR" ]; then
    echo "❌ JSS7 Simulator not found at: $SIMULATOR_DIR"
    echo "Please ensure JSS7 is properly installed"
    exit 1
fi

# Create test configuration directories
echo "📁 Creating test configuration directories..."
mkdir -p "$TEST_CONFIG_DIR/hlr-simulator"
mkdir -p "$TEST_CONFIG_DIR/msc-simulator"
mkdir -p "$TEST_CONFIG_DIR/smsc-simulator"
mkdir -p "$TEST_CONFIG_DIR/test-results"
mkdir -p "$SCRIPT_DIR/logs"

# Function to create simulator instance
create_simulator_instance() {
    local instance_name=$1
    local instance_dir=$2
    local rmi_port=$3
    local http_port=$4
    
    echo "🔧 Creating simulator instance: $instance_name"
    
    # Copy simulator bootstrap
    cp -r "$SIMULATOR_DIR/bootstrap" "$instance_dir/"
    
    # Update configuration
    cat > "$instance_dir/bootstrap/src/main/config/run.sh" <<EOF
#!/bin/bash

# Simulator startup script for $instance_name
SIMULATOR_HOME=\$(dirname "\$0")
JAVA_OPTS="-Xms256m -Xmx512m"
MAIN_CLASS="org.restcomm.protocols.ss7.tools.simulator.bootstrap.Main"

# Set instance-specific properties
JAVA_OPTS="\$JAVA_OPTS -Djava.rmi.server.hostname=localhost"
JAVA_OPTS="\$JAVA_OPTS -Dcom.sun.management.jmxremote.port=$rmi_port"
JAVA_OPTS="\$JAVA_OPTS -Dcom.sun.management.jmxremote.local.only=false"
JAVA_OPTS="\$JAVA_OPTS -Dcom.sun.management.jmxremote.authenticate=false"
JAVA_OPTS="\$JAVA_OPTS -Dcom.sun.management.jmxremote.ssl=false"
JAVA_OPTS="\$JAVA_OPTS -Dsimulator.name=$instance_name"
JAVA_OPTS="\$JAVA_OPTS -Dsimulator.http.port=$http_port"

# Add JSS7 libraries to classpath
CLASSPATH="\$SIMULATOR_HOME/../lib/*:\$SIMULATOR_HOME/../lib/native/*"

if [ "\$1" = "gui" ]; then
    MAIN_CLASS="org.restcomm.protocols.ss7.tools.simulator.gui.MainGui"
    echo "Starting $instance_name Simulator GUI..."
else
    echo "Starting $instance_name Simulator Core..."
fi

java \$JAVA_OPTS -cp "\$CLASSPATH" \$MAIN_CLASS
EOF
    
    chmod +x "$instance_dir/bootstrap/src/main/config/run.sh"
}

# Create HLR Simulator
create_simulator_instance "HLR" "$TEST_CONFIG_DIR/hlr-simulator" 9001 8001

# Create MSC Simulator
create_simulator_instance "MSC" "$TEST_CONFIG_DIR/msc-simulator" 9002 8002

# Create SMSC Simulator (for testing)
create_simulator_instance "SMSC-Test" "$TEST_CONFIG_DIR/smsc-simulator" 9003 8003

echo ""
echo "📝 Creating test configuration files..."

# Create HLR simulator configuration
cat > "$TEST_CONFIG_DIR/hlr-simulator/hlr-config.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<simulator>
    <name>HLR-Simulator</name>
    <instance>HLR-01</instance>
    
    <!-- M3UA Configuration -->
    <m3ua>
        <localPC>100</localPC>
        <remotePC>1</remotePC>
        <localSSN>6</localSSN>
        <remoteSSN>8</remoteSSN>
        <routingContext>100</routingContext>
        <networkIndicator>2</networkIndicator>
        <localHost>127.0.0.1</localHost>
        <localPort>8011</localPort>
        <remoteHost>127.0.0.1</remoteHost>
        <remotePort>2905</remotePort>
    </m3ua>
    
    <!-- SCCP Configuration -->
    <sccp>
        <routeOnGT>true</routeOnGT>
        <remoteSpc>1</remoteSpc>
        <localSpc>100</localSpc>
        <ni>2</ni>
        <ssn>6</ssn>
        <globalTitleType>4</globalTitleType>
        <addressNature>4</addressNature>
        <numberingPlan>1</numberingPlan>
        <address>919890123456</address>
    </sccp>
    
    <!-- MAP Configuration -->
    <map>
        <applicationContext>networkLocUp_v3</applicationContext>
        <dialogTimeout>60000</dialogTimeout>
    </map>
    
    <!-- Test Scenarios -->
    <scenarios>
        <scenario name="SRI_Response">
            <description>Respond to SendRoutingInfo requests</description>
            <enabled>true</enabled>
            <autoResponse>true</autoResponse>
            <responseDelay>100</responseDelay>
        </scenario>
        <scenario name="ATI_Response">
            <description>Respond to AnyTimeInterrogation requests</description>
            <enabled>true</enabled>
            <autoResponse>true</autoResponse>
            <responseDelay>50</responseDelay>
        </scenario>
    </scenarios>
</simulator>
EOF

# Create MSC simulator configuration
cat > "$TEST_CONFIG_DIR/msc-simulator/msc-config.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<simulator>
    <name>MSC-Simulator</name>
    <instance>MSC-01</instance>
    
    <!-- M3UA Configuration -->
    <m3ua>
        <localPC>200</localPC>
        <remotePC>1</remotePC>
        <localSSN>7</localSSN>
        <remoteSSN>8</remoteSSN>
        <routingContext>200</routingContext>
        <networkIndicator>2</networkIndicator>
        <localHost>127.0.0.1</localHost>
        <localPort>8012</localPort>
        <remoteHost>127.0.0.1</remoteHost>
        <remotePort>2906</remotePort>
    </m3ua>
    
    <!-- SCCP Configuration -->
    <sccp>
        <routeOnGT>true</routeOnGT>
        <remoteSpc>1</remoteSpc>
        <localSpc>200</localSpc>
        <ni>2</ni>
        <ssn>7</ssn>
        <globalTitleType>4</globalTitleType>
        <addressNature>4</addressNature>
        <numberingPlan>1</numberingPlan>
        <address>919890234567</address>
    </sccp>
    
    <!-- MAP Configuration -->
    <map>
        <applicationContext>shortMsgMT_Relay_v3</applicationContext>
        <dialogTimeout>60000</dialogTimeout>
    </map>
    
    <!-- Test Scenarios -->
    <scenarios>
        <scenario name="MT_ForwardSM">
            <description>Handle MT-ForwardSM requests</description>
            <enabled>true</enabled>
            <autoResponse>true</autoResponse>
            <successRate>95</successRate>
            <responseDelay>200</responseDelay>
        </scenario>
        <scenario name="MO_ForwardSM">
            <description>Generate MO-ForwardSM requests</description>
            <enabled>true</enabled>
            <rate>10</rate> <!-- 10 msgs/sec -->
            <duration>60</duration> <!-- seconds -->
        </scenario>
    </scenarios>
</simulator>
EOF

# Create test scenarios script
cat > "$SCRIPT_DIR/test-scenarios.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<test-scenarios>
    <!-- HA Failover Test Scenarios -->
    
    <scenario id="1" name="Basic Connectivity Test">
        <description>Test basic SS7 connectivity between HA Gateway and simulators</description>
        <steps>
            <step>Start HLR simulator on PC=100</step>
            <step>Start MSC simulator on PC=200</step>
            <step>Start HA Gateway with active node</step>
            <step>Send test SCCP message from HLR to HA Gateway</step>
            <step>Verify message received and processed</step>
        </steps>
        <expected>All messages successfully routed</expected>
    </scenario>
    
    <scenario id="2" name="Active-Standby Failover">
        <description>Test failover from active to standby node</description>
        <steps>
            <step>Start both HA Gateway nodes (active + standby)</step>
            <step>Send continuous traffic through active node</step>
            <step>Kill active node process</step>
            <step>Verify standby node takes over</step>
            <step>Check message continuity and loss rate</step>
        </steps>
        <expected>Failover completes within 5 seconds with minimal message loss</expected>
    </scenario>
    
    <scenario id="3" name="Load Balancing Test">
        <description>Test load distribution across multiple nodes</description>
        <steps>
            <step>Start 3 HA Gateway nodes in load-balance mode</step>
            <step>Send 1000 messages from MSC simulator</step>
            <step>Monitor message distribution across nodes</step>
            <step>Verify even distribution based on GT hash</step>
        </steps>
        <expected>Messages distributed evenly (±10%) across all nodes</expected>
    </scenario>
    
    <scenario id="4" name="Circuit Breaker Test">
        <description>Test circuit breaker behavior under failure conditions</description>
        <steps>
            <step>Configure circuit breaker with threshold=5 failures</step>
            <step>Simulate destination unavailability</step>
            <step>Send 10 messages and observe circuit breaker opening</step>
            <step>Verify circuit enters OPEN state after 5 failures</step>
            <step>Wait for timeout and verify HALF_OPEN state</step>
            <step>Restore destination and verify circuit closes</step>
        </steps>
        <expected>Circuit breaker transitions correctly through states</expected>
    </scenario>
    
    <scenario id="5" name="High Load Stress Test">
        <description>Test system behavior under high message load</description>
        <steps>
            <step>Start all simulators and HA Gateway nodes</step>
            <step>Generate 10,000 msgs/sec from multiple simulators</step>
            <step>Run for 5 minutes continuously</step>
            <step>Monitor CPU, memory, and message latency</step>
            <step>Check for message loss or errors</step>
        </steps>
        <expected>System handles load with <1% message loss and <500ms latency</expected>
    </scenario>
    
    <scenario id="6" name="Network Partition Test">
        <description>Test behavior during network partition (split-brain)</description>
        <steps>
            <step>Start 2 HA nodes with shared state</step>
            <step>Simulate network partition between nodes</step>
            <step>Verify split-brain prevention mechanism activates</step>
            <step>Check that only one node remains active</step>
            <step>Restore network and verify reconciliation</step>
        </steps>
        <expected>Split-brain prevented, single active node maintained</expected>
    </scenario>
    
    <scenario id="7" name="GT Routing Test">
        <description>Test Global Title translation and routing</description>
        <steps>
            <step>Configure multiple GT routing rules</step>
            <step>Send messages to different GT ranges</step>
            <step>Verify correct routing based on GT patterns</step>
            <step>Test wildcard and prefix matching</step>
        </steps>
        <expected>All GT patterns correctly routed to destinations</expected>
    </scenario>
    
    <scenario id="8" name="Kafka Integration Test">
        <description>Test SS7 to Kafka message flow</description>
        <steps>
            <step>Start Kafka cluster</step>
            <step>Configure HA Gateway with Kafka producer</step>
            <step>Send MAP messages through SS7</step>
            <step>Verify messages published to correct Kafka topics</step>
            <step>Check message ordering and partitioning</step>
        </steps>
        <expected>All messages correctly published to Kafka with proper partitioning</expected>
    </scenario>
</test-scenarios>
EOF

# Create automated test runner
cat > "$SCRIPT_DIR/run-tests.sh" <<'EOF'
#!/bin/bash

# SS7 HA Gateway Automated Test Runner
# =====================================

set -e

TEST_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
RESULTS_DIR="$TEST_DIR/config/test-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="$RESULTS_DIR/test_report_$TIMESTAMP.txt"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "================================================"
echo "SS7 HA Gateway Test Suite"
echo "================================================"
echo ""

# Function to run a test scenario
run_test() {
    local test_name=$1
    local test_command=$2
    
    echo -n "Running: $test_name... "
    
    if eval "$test_command" > "$RESULTS_DIR/${test_name}.log" 2>&1; then
        echo -e "${GREEN}✅ PASSED${NC}"
        echo "✅ $test_name: PASSED" >> "$REPORT_FILE"
        return 0
    else
        echo -e "${RED}❌ FAILED${NC}"
        echo "❌ $test_name: FAILED" >> "$REPORT_FILE"
        echo "   See: $RESULTS_DIR/${test_name}.log" >> "$REPORT_FILE"
        return 1
    fi
}

# Initialize report
echo "Test Report - $TIMESTAMP" > "$REPORT_FILE"
echo "================================" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

# Start simulators
echo "🚀 Starting SS7 Simulators..."
"$TEST_DIR/start-simulators.sh"
sleep 5

# Run test scenarios
echo ""
echo "🧪 Running Test Scenarios..."
echo ""

TOTAL_TESTS=0
PASSED_TESTS=0

# Test 1: Basic Connectivity
((TOTAL_TESTS++))
if run_test "basic_connectivity" "$TEST_DIR/tests/test_basic_connectivity.sh"; then
    ((PASSED_TESTS++))
fi

# Test 2: Failover
((TOTAL_TESTS++))
if run_test "failover" "$TEST_DIR/tests/test_failover.sh"; then
    ((PASSED_TESTS++))
fi

# Test 3: Load Balancing
((TOTAL_TESTS++))
if run_test "load_balancing" "$TEST_DIR/tests/test_load_balancing.sh"; then
    ((PASSED_TESTS++))
fi

# Test 4: Circuit Breaker
((TOTAL_TESTS++))
if run_test "circuit_breaker" "$TEST_DIR/tests/test_circuit_breaker.sh"; then
    ((PASSED_TESTS++))
fi

# Test 5: Performance
((TOTAL_TESTS++))
if run_test "performance" "$TEST_DIR/tests/test_performance.sh"; then
    ((PASSED_TESTS++))
fi

# Stop simulators
echo ""
echo "🛑 Stopping SS7 Simulators..."
"$TEST_DIR/stop-simulators.sh"

# Generate summary
echo ""
echo "================================"
echo "Test Summary"
echo "================================"
echo "Total Tests: $TOTAL_TESTS"
echo "Passed: $PASSED_TESTS"
echo "Failed: $((TOTAL_TESTS - PASSED_TESTS))"
echo ""

if [ $PASSED_TESTS -eq $TOTAL_TESTS ]; then
    echo -e "${GREEN}✅ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Check $REPORT_FILE for details.${NC}"
    exit 1
fi
EOF

chmod +x "$SCRIPT_DIR/run-tests.sh"

# Create simulator management scripts
cat > "$SCRIPT_DIR/start-simulators.sh" <<'EOF'
#!/bin/bash

TEST_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CONFIG_DIR="$TEST_DIR/config"
LOG_DIR="$TEST_DIR/logs"

echo "Starting SS7 Simulators..."

# Start HLR Simulator
echo "  Starting HLR Simulator..."
cd "$CONFIG_DIR/hlr-simulator/bootstrap/src/main/config"
nohup ./run.sh core > "$LOG_DIR/hlr-simulator.log" 2>&1 &
echo $! > "$LOG_DIR/hlr-simulator.pid"

# Start MSC Simulator
echo "  Starting MSC Simulator..."
cd "$CONFIG_DIR/msc-simulator/bootstrap/src/main/config"
nohup ./run.sh core > "$LOG_DIR/msc-simulator.log" 2>&1 &
echo $! > "$LOG_DIR/msc-simulator.pid"

echo "Simulators started. PIDs saved in $LOG_DIR/"
EOF

cat > "$SCRIPT_DIR/stop-simulators.sh" <<'EOF'
#!/bin/bash

TEST_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
LOG_DIR="$TEST_DIR/logs"

echo "Stopping SS7 Simulators..."

# Stop HLR Simulator
if [ -f "$LOG_DIR/hlr-simulator.pid" ]; then
    PID=$(cat "$LOG_DIR/hlr-simulator.pid")
    if ps -p $PID > /dev/null; then
        echo "  Stopping HLR Simulator (PID: $PID)..."
        kill $PID
    fi
    rm "$LOG_DIR/hlr-simulator.pid"
fi

# Stop MSC Simulator
if [ -f "$LOG_DIR/msc-simulator.pid" ]; then
    PID=$(cat "$LOG_DIR/msc-simulator.pid")
    if ps -p $PID > /dev/null; then
        echo "  Stopping MSC Simulator (PID: $PID)..."
        kill $PID
    fi
    rm "$LOG_DIR/msc-simulator.pid"
fi

echo "Simulators stopped."
EOF

chmod +x "$SCRIPT_DIR/start-simulators.sh"
chmod +x "$SCRIPT_DIR/stop-simulators.sh"

echo ""
echo "✅ Test environment setup complete!"
echo ""
echo "📋 Available Commands:"
echo "  • Start simulators: ./start-simulators.sh"
echo "  • Stop simulators:  ./stop-simulators.sh"
echo "  • Run all tests:    ./run-tests.sh"
echo ""
echo "📁 Configuration files created in: $TEST_CONFIG_DIR"
echo "📊 Test results will be saved in: $TEST_CONFIG_DIR/test-results"
echo ""
echo "Next steps:"
echo "1. Build JSS7 simulator if not already built"
echo "2. Adjust simulator configurations as needed"
echo "3. Run ./run-tests.sh to execute test suite"
EOF