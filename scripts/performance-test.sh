#!/bin/bash

#
# SS7 HA Gateway - Performance Test Script
#
# This script performs load testing on the SS7 HA Gateway:
# - Redis latency measurement
# - Dialog creation/deletion performance
# - Failover time measurement
#

set -e

REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-7000}"
NUM_DIALOGS="${NUM_DIALOGS:-1000}"
DURATION="${DURATION:-60}"

echo "======================================"
echo "SS7 HA Gateway Performance Test"
echo "======================================"
echo ""
echo "Configuration:"
echo "  Redis: $REDIS_HOST:$REDIS_PORT"
echo "  Number of dialogs: $NUM_DIALOGS"
echo "  Duration: $DURATION seconds"
echo ""

#
# Test 1: Redis Latency Benchmark
#
echo "[Test 1] Redis Latency Benchmark"
echo "-----------------------------------"

redis-cli -h $REDIS_HOST -p $REDIS_PORT --latency-history -i 1 & 
REDIS_LATENCY_PID=$!

sleep 10

kill $REDIS_LATENCY_PID 2>/dev/null || true

echo ""

#
# Test 2: Redis Throughput Benchmark
#
echo "[Test 2] Redis Throughput Benchmark"
echo "------------------------------------"

redis-benchmark -h $REDIS_HOST -p $REDIS_PORT \
    -t set,get -n 100000 -q -c 50 -d 1024

echo ""

#
# Test 3: Dialog Store Performance
#
echo "[Test 3] Dialog Store Performance"
echo "-----------------------------------"

# Create test dialogs
echo "Creating $NUM_DIALOGS dialogs..."
START_TIME=$(date +%s%N)

for i in $(seq 1 $NUM_DIALOGS); do
    DIALOG_ID=$((10000 + i))
    redis-cli -h $REDIS_HOST -p $REDIS_PORT \
        SET "dialog:$DIALOG_ID" '{"dialogId":'$DIALOG_ID',"state":"ACTIVE"}' \
        EX 3600 > /dev/null
done

END_TIME=$(date +%s%N)
ELAPSED_MS=$(( ($END_TIME - $START_TIME) / 1000000 ))
THROUGHPUT=$(( $NUM_DIALOGS * 1000 / $ELAPSED_MS ))

echo "  Created: $NUM_DIALOGS dialogs"
echo "  Time: $ELAPSED_MS ms"
echo "  Throughput: $THROUGHPUT dialogs/sec"
echo "  Average latency: $(( $ELAPSED_MS / $NUM_DIALOGS )) ms per dialog"

echo ""

# Read test dialogs
echo "Reading $NUM_DIALOGS dialogs..."
START_TIME=$(date +%s%N)

for i in $(seq 1 $NUM_DIALOGS); do
    DIALOG_ID=$((10000 + i))
    redis-cli -h $REDIS_HOST -p $REDIS_PORT \
        GET "dialog:$DIALOG_ID" > /dev/null
done

END_TIME=$(date +%s%N)
ELAPSED_MS=$(( ($END_TIME - $START_TIME) / 1000000 ))
THROUGHPUT=$(( $NUM_DIALOGS * 1000 / $ELAPSED_MS ))

echo "  Read: $NUM_DIALOGS dialogs"
echo "  Time: $ELAPSED_MS ms"
echo "  Throughput: $THROUGHPUT dialogs/sec"
echo "  Average latency: $(( $ELAPSED_MS / $NUM_DIALOGS )) ms per dialog"

echo ""

# Cleanup test dialogs
echo "Deleting $NUM_DIALOGS dialogs..."
START_TIME=$(date +%s%N)

for i in $(seq 1 $NUM_DIALOGS); do
    DIALOG_ID=$((10000 + i))
    redis-cli -h $REDIS_HOST -p $REDIS_PORT \
        DEL "dialog:$DIALOG_ID" > /dev/null
done

END_TIME=$(date +%s%N)
ELAPSED_MS=$(( ($END_TIME - $START_TIME) / 1000000 ))
THROUGHPUT=$(( $NUM_DIALOGS * 1000 / $ELAPSED_MS ))

echo "  Deleted: $NUM_DIALOGS dialogs"
echo "  Time: $ELAPSED_MS ms"
echo "  Throughput: $THROUGHPUT dialogs/sec"

echo ""

#
# Test 5: Concurrent Operations Test
#
echo "[Test 4] Concurrent Operations Test"
echo "------------------------------------"

CONCURRENCY=50

echo "Running $CONCURRENCY concurrent clients..."

for i in $(seq 1 $CONCURRENCY); do
    (
        for j in $(seq 1 $(( $NUM_DIALOGS / $CONCURRENCY ))); do
            DIALOG_ID=$(( (i * 10000) + j ))
            redis-cli -h $REDIS_HOST -p $REDIS_PORT \
                SET "dialog:$DIALOG_ID" '{"dialogId":'$DIALOG_ID'}' \
                EX 3600 > /dev/null 2>&1
        done
    ) &
done

# Wait for all background jobs
wait

echo "  Completed concurrent operations"

echo ""

#
# Test 6: Memory Usage
#
echo "[Test 5] Memory Usage"
echo "---------------------"

MEMORY_INFO=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT INFO memory | grep used_memory_human)
echo "  $MEMORY_INFO"

MEMORY_PEAK=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT INFO memory | grep used_memory_peak_human)
echo "  $MEMORY_PEAK"

echo ""

#
# Summary
#
echo "======================================