#!/bin/bash

#
# SS7 HA Gateway Entrypoint Script
#

set -e

echo "======================================"
echo "SS7 HA Gateway - Starting Node $NODE_ID"
echo "======================================"

# Wait for Redis cluster to be ready
echo "Waiting for Redis cluster..."
for node in $(echo $REDIS_CLUSTER_NODES | tr ',' ' '); do
    host=$(echo $node | cut -d':' -f1)
    port=$(echo $node | cut -d':' -f2)
    echo "Checking Redis node $host:$port..."
    while ! nc -z $host $port; do
        sleep 1
    done
    echo "Redis node $host:$port is ready"
done

# Wait for Kafka to be ready
echo "Waiting for Kafka cluster..."
for broker in $(echo $KAFKA_BOOTSTRAP_SERVERS | tr ',' ' '); do
    host=$(echo $broker | cut -d':' -f1)
    port=$(echo $broker | cut -d':' -f2)
    echo "Checking Kafka broker $host:$port..."
    while ! nc -z $host $port; do
        sleep 1
    done
    echo "Kafka broker $host:$port is ready"
done

# Update configuration with environment variables
if [ -n "$REDIS_CLUSTER_NODES" ]; then
    sed -i "s|redis.cluster.nodes=.*|redis.cluster.nodes=$REDIS_CLUSTER_NODES|" config/ss7-ha-gateway.properties
fi

if [ -n "$KAFKA_BOOTSTRAP_SERVERS" ]; then
    sed -i "s|kafka.bootstrap.servers=.*|kafka.bootstrap.servers=$KAFKA_BOOTSTRAP_SERVERS|" config/ss7-ha-gateway.properties
fi

if [ -n "$KAFKA_CLIENT_ID" ]; then
    sed -i "s|kafka.client.id=.*|kafka.client.id=$KAFKA_CLIENT_ID|" config/ss7-ha-gateway.properties
fi

echo "Configuration updated:"
cat config/ss7-ha-gateway.properties

# Start application
echo "Starting SS7 HA Gateway..."
exec java $JAVA_OPTS \
    -Dconfig.file=config/ss7-ha-gateway.properties \
    -Dnode.id=$NODE_ID \
    -jar ss7-ha-gateway.jar
