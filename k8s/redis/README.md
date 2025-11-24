# Redis High Availability Deployment

**Redis cluster for SS7 HA Gateway session state and caching**

## Overview

This directory contains Kubernetes manifests for deploying a high-availability Redis cluster with master-replica replication and Sentinel for automatic failover.

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│               Redis HA Cluster                          │
│                                                          │
│  ┌──────────────┐                                       │
│  │ Redis Master │  (RW)                                 │
│  │  :6379       │                                       │
│  └──────────────┘                                       │
│         │                                                │
│         ├──────────────┬────────────────┐              │
│         │              │                 │              │
│  ┌──────────────┐  ┌──────────────┐                   │
│  │ Redis Replica│  │ Redis Replica│  (RO)              │
│  │  :6379       │  │  :6379       │                    │
│  └──────────────┘  └──────────────┘                   │
│                                                          │
│  ┌──────────────────────────────────┐                  │
│  │    Redis Sentinel (3 nodes)      │                  │
│  │  Auto failover & monitoring      │                  │
│  │         :26379                    │                  │
│  └──────────────────────────────────┘                  │
└─────────────────────────────────────────────────────────┘
         │                     │
         │                     │
  SS7 HA Gateway      Other Services
  (session state)     (caching)
```

---

## Features

- ✅ **High Availability**: Master-replica with automatic failover
- ✅ **Redis Sentinel**: Monitors and manages failover (3 sentinels)
- ✅ **Persistence**: RDB + AOF for data durability
- ✅ **Performance Tuned**: Optimized for telco session state
- ✅ **Scalability**: 1 master + 2 replicas (can scale)

---

## Components

### Redis Master
- **Replicas**: 1
- **Storage**: 20Gi persistent volume
- **Persistence**: RDB snapshots + AOF
- **Read/Write**: Accepts both read and write operations

### Redis Replicas
- **Replicas**: 2
- **Storage**: Ephemeral (synced from master)
- **Read-Only**: Serves read requests to offload master

### Redis Sentinel
- **Replicas**: 3
- **Port**: 26379
- **Quorum**: 2 (requires 2 sentinels to agree on failover)
- **Function**: Monitors master health and triggers failover

---

## Deployment

### Prerequisites

```bash
# Ensure microk8s storage is enabled
microk8s enable hostpath-storage

# Or check storage class exists
kubectl get storageclass
```

### Deploy Redis Cluster

```bash
cd ~/Workspaces/telco/ss7-ha-gateway/k8s/redis

# Deploy Redis HA cluster
kubectl apply -f redis-ha.yaml

# Wait for pods to be ready
kubectl wait --for=condition=ready pod -l app=redis -n redis --timeout=180s

# Check deployment
kubectl get all -n redis
```

### Verify Deployment

```bash
# Check pods
kubectl get pods -n redis

# Expected output:
# NAME                              READY   STATUS    RESTARTS   AGE
# redis-master-xxxxxxxxx-xxxxx      1/1     Running   0          2m
# redis-replica-xxxxxxxxx-xxxxx     1/1     Running   0          2m
# redis-replica-xxxxxxxxx-yyyyy     1/1     Running   0          2m
# redis-sentinel-xxxxxxxxx-xxxxx    1/1     Running   0          2m
# redis-sentinel-xxxxxxxxx-yyyyy    1/1     Running   0          2m
# redis-sentinel-xxxxxxxxx-zzzzz    1/1     Running   0          2m

# Check services
kubectl get svc -n redis

# Check replication status
kubectl exec -it redis-master-<pod-id> -n redis -- redis-cli INFO replication
```

---

## Testing

### Basic Operations

```bash
# Connect to master
kubectl exec -it redis-master-<pod-id> -n redis -- redis-cli

# Set a key
SET test:key "Hello Redis"

# Get a key
GET test:key

# Check TTL
TTL test:key

# Set with expiration
SETEX session:12345 3600 "user-session-data"
```

### Test Replication

```bash
# On master: Set a key
kubectl exec -it redis-master-<pod-id> -n redis -- redis-cli SET repltest "data"

# On replica: Get the key
kubectl exec -it redis-replica-<pod-id> -n redis -- redis-cli GET repltest

# Should return: "data"
```

### Test Sentinel

```bash
# Connect to sentinel
kubectl exec -it redis-sentinel-<pod-id> -n redis -- redis-cli -p 26379

# Check master
SENTINEL master mymaster

# Check replicas
SENTINEL replicas mymaster

# Check sentinel info
SENTINEL sentinels mymaster
```

---

## Configuration

### Redis Configuration

Key settings in `redis.conf`:

```
# Memory
maxmemory 2gb
maxmemory-policy allkeys-lru

# Persistence
save 900 1
save 300 10
save 60 10000
appendonly yes
appendfsync everysec

# Replication
replica-read-only yes

# Performance
tcp-backlog 511
tcp-keepalive 300
maxclients 10000
```

### Sentinel Configuration

```
# Monitoring
sentinel monitor mymaster redis-master.redis.svc.cluster.local 6379 2

# Timeouts
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000

# Parallel syncs
sentinel parallel-syncs mymaster 1
```

---

## SS7 HA Gateway Integration

### Session State Storage

```java
// SS7 Gateway stores session state in Redis
// Key pattern: session:{dialog-id}
// Value: JSON session data
// TTL: dialog timeout (default 60 seconds)

redis.set("session:12345", sessionData, 60);
redis.get("session:12345");
redis.del("session:12345");
```

### Connection Configuration

```properties
# ss7-config.properties
redis.host=redis-master.redis.svc.cluster.local
redis.port=6379
redis.connection.timeout=2000
redis.max.total.connections=50
redis.max.idle.connections=10
redis.min.idle.connections=5
```

### Data Stored

- **SS7 Dialog Sessions**: Active MAP/TCAP dialogs
- **Transaction State**: Pending SRI/MT-Forward-SM transactions
- **Message Cache**: Recently processed messages (deduplication)
- **Rate Limiting**: Per-MSISDN throttling counters

---

## Monitoring

### Health Checks

```bash
# Check master status
kubectl exec -it redis-master-<pod-id> -n redis -- redis-cli PING

# Check memory usage
kubectl exec -it redis-master-<pod-id> -n redis -- redis-cli INFO memory

# Check connected clients
kubectl exec -it redis-master-<pod-id> -n redis -- redis-cli CLIENT LIST

# Check slow queries
kubectl exec -it redis-master-<pod-id> -n redis -- redis-cli SLOWLOG GET 10
```

### Performance Metrics

```bash
# Get stats
kubectl exec -it redis-master-<pod-id> -n redis -- redis-cli INFO stats

# Key metrics:
# - total_commands_processed
# - instantaneous_ops_per_sec
# - keyspace_hits / keyspace_misses (hit rate)
# - used_memory_human
# - connected_clients
```

### Replication Lag

```bash
# On master
kubectl exec -it redis-master-<pod-id> -n redis -- \
  redis-cli INFO replication | grep offset

# On replica
kubectl exec -it redis-replica-<pod-id> -n redis -- \
  redis-cli INFO replication | grep offset

# Lag = master_repl_offset - replica_offset
```

---

## High Availability Testing

### Simulate Master Failure

```bash
# Delete master pod
kubectl delete pod redis-master-<pod-id> -n redis

# Watch sentinel logs
kubectl logs -n redis redis-sentinel-<pod-id> -f

# Expected:
# - Sentinel detects master down
# - Quorum reached (2/3 sentinels)
# - Failover initiated
# - Replica promoted to master
# - New master announced

# Verify new master
kubectl exec -it redis-sentinel-<pod-id> -n redis -- \
  redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```

### Verify Failover

```bash
# Check which pod is now master
kubectl exec -it redis-replica-<pod-id> -n redis -- \
  redis-cli INFO replication | grep role

# If role=master, failover succeeded

# Test write to new master
kubectl exec -it <new-master-pod> -n redis -- \
  redis-cli SET failover:test "success"
```

---

## Scaling

### Add More Replicas

```bash
# Edit deployment
kubectl edit deployment redis-replica -n redis

# Change replicas: 2 -> 3
spec:
  replicas: 3

# New replica will auto-sync from master
```

### Increase Storage

```bash
# Edit PVC (if storage class supports expansion)
kubectl edit pvc redis-master-pvc -n redis

# Increase storage: 20Gi -> 50Gi
```

---

## Backup and Recovery

### Manual Backup

```bash
# Trigger RDB snapshot
kubectl exec -it redis-master-<pod-id> -n redis -- redis-cli BGSAVE

# Copy snapshot file
kubectl cp redis/<pod-name>:/data/dump.rdb ./dump.rdb

# Backup AOF
kubectl cp redis/<pod-name>:/data/appendonly.aof ./appendonly.aof
```

### Restore from Backup

```bash
# Copy backup to pod
kubectl cp ./dump.rdb redis/<pod-name>:/data/dump.rdb

# Restart pod to load backup
kubectl delete pod redis-master-<pod-id> -n redis
```

---

## Troubleshooting

### Master not accepting writes

```bash
# Check master status
kubectl exec -it redis-master-<pod-id> -n redis -- \
  redis-cli INFO replication

# Look for:
# role:master
# connected_slaves:2

# If role=slave, failover occurred
# Check sentinel logs
kubectl logs -n redis redis-sentinel-<pod-id>
```

### Replica not syncing

```bash
# On replica, check sync status
kubectl exec -it redis-replica-<pod-id> -n redis -- \
  redis-cli INFO replication | grep master_link_status

# Should be: master_link_status:up

# If down, check:
# - Network connectivity to master
# - Master is accepting connections
# - Firewall rules
```

### High memory usage

```bash
# Check memory stats
kubectl exec -it redis-master-<pod-id> -n redis -- \
  redis-cli INFO memory

# Check largest keys
kubectl exec -it redis-master-<pod-id> -n redis -- \
  redis-cli --bigkeys

# Increase maxmemory or enable eviction
kubectl edit configmap redis-config -n redis
```

### Sentinel not detecting failover

```bash
# Check sentinel config
kubectl exec -it redis-sentinel-<pod-id> -n redis -- \
  cat /etc/redis/sentinel.conf

# Check quorum (needs 2/3)
kubectl get pods -n redis | grep sentinel

# Ensure at least 2 sentinels running
```

---

## Security (Production)

### Enable Authentication

```yaml
# Create Secret
apiVersion: v1
kind: Secret
metadata:
  name: redis-password
  namespace: redis
stringData:
  password: "your-strong-password"

# Update ConfigMap
data:
  redis.conf: |
    requirepass ${REDIS_PASSWORD}
    masterauth ${REDIS_PASSWORD}

# Mount secret in pods
env:
- name: REDIS_PASSWORD
  valueFrom:
    secretKeyRef:
      name: redis-password
      key: password
```

### TLS Encryption

```yaml
# Add TLS certificates
- name: redis-tls
  secret:
    secretName: redis-tls-cert

# Update config
tls-port 6380
tls-cert-file /etc/redis/tls/tls.crt
tls-key-file /etc/redis/tls/tls.key
tls-ca-cert-file /etc/redis/tls/ca.crt
```

---

## Cleanup

```bash
# Delete Redis HA cluster
kubectl delete -f redis-ha.yaml

# Delete namespace (WARNING: deletes all data!)
kubectl delete namespace redis
```

---

## References

- [Redis Documentation](https://redis.io/documentation)
- [Redis Sentinel](https://redis.io/docs/management/sentinel/)
- [Redis Persistence](https://redis.io/docs/management/persistence/)
- [Redis Replication](https://redis.io/docs/management/replication/)
