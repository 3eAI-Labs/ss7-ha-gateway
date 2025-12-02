# SS7 HA Gateway - Complete Operations Guide

**Version:** 2.0.0
**Document Status:** Production Ready
**Last Updated:** December 2025
**License:** AGPL-3.0

---

## Table of Contents

- [Quick Start (5 Minutes)](#quick-start-5-minutes)
1. [Executive Summary](#1-executive-summary)
2. [Product Overview](#2-product-overview)
3. [Architecture](#3-architecture)
4. [Prerequisites and System Requirements](#4-prerequisites-and-system-requirements)
5. [Installation Guide](#5-installation-guide)
6. [Configuration Guide](#6-configuration-guide)
7. [Kubernetes Deployment](#7-kubernetes-deployment)
8. [Operations Guide](#8-operations-guide)
9. [Monitoring and Observability](#9-monitoring-and-observability)
10. [Logging](#10-logging)
11. [Alarms and Alerting](#11-alarms-and-alerting)
12. [Troubleshooting](#12-troubleshooting)
13. [Security](#13-security)
14. [Reference](#14-reference)
15. [Appendix](#15-appendix)

---

## Quick Start (5 Minutes)

> **TL;DR**: Get SS7 HA Gateway running in 5 minutes with Docker Compose.

### Prerequisites Checklist

```bash
# Verify you have these installed:
docker --version      # Docker 20.10+
docker-compose --version  # Docker Compose 2.0+
java -version         # Java 11+ (for building only)
```

### Step 1: Clone and Build (2 min)

```bash
# Clone the repository
git clone https://github.com/3eAI-Labs/ss7-ha-gateway.git
cd ss7-ha-gateway

# Build the application
mvn clean package -DskipTests

# Build Docker image
docker build -t ss7-ha-gateway:latest -f docker/Dockerfile .
```

### Step 2: Configure (1 min)

Edit `config/ss7-config.properties` - only 3 required settings:

```properties
# Your SS7 network settings (get these from your network team)
sccp.local.pc=1              # Your Point Code
sccp.local.ssn=8             # Your Subsystem Number (8=MSC)
sccp.local.gt=123456789      # Your Global Title
```

### Step 3: Start (1 min)

```bash
# Start NATS and Gateway
docker-compose up -d

# Verify everything is running
docker-compose ps

# Expected output:
# NAME              STATUS
# nats-0            Up (healthy)
# ss7-gateway-0     Up (healthy)
```

### Step 4: Verify (1 min)

```bash
# Check gateway health
curl http://localhost:9090/metrics | head -20

# Check NATS connectivity
curl http://localhost:8222/varz | jq .connections

# View logs
docker-compose logs -f ss7-gateway-0
```

### Quick Start Success Criteria

| Check | Command | Expected |
|-------|---------|----------|
| Gateway running | `docker ps \| grep ss7` | Container "Up" |
| Metrics available | `curl localhost:9090/metrics` | Prometheus metrics |
| NATS connected | `curl localhost:8222/connz` | 1+ connections |
| No errors in logs | `docker logs ss7-gateway-0 2>&1 \| grep -i error` | Empty output |

### Next Steps

- **Production deployment?** → See [Section 7: Kubernetes Deployment](#7-kubernetes-deployment)
- **Configure SS7 peers?** → See [Section 6: Configuration Guide](#6-configuration-guide)
- **Set up monitoring?** → See [Section 9: Monitoring and Observability](#9-monitoring-and-observability)
- **Having issues?** → See [Section 12: Troubleshooting](#12-troubleshooting)

---

## 1. Executive Summary

### 1.1 Purpose

The SS7 HA Gateway is a carrier-grade, high-availability protocol handling layer that bridges legacy SS7/MAP/CAP telecom networks with modern cloud-native applications. It provides:

- **Protocol Translation**: Converts SS7 signaling to JSON events over NATS
- **High Availability**: Active-Active and Active-Standby deployment modes
- **Horizontal Scalability**: Scale-out architecture with NATS queue groups
- **Cloud-Native Design**: Kubernetes-ready with StatefulSet deployment

### 1.2 Target Audience

| Role | Relevant Sections |
|------|------------------|
| Network Architect | Sections 2, 3, 7 |
| System Administrator | Sections 4, 5, 6, 7, 8 |
| DevOps Engineer | Sections 5, 6, 7, 9, 10 |
| NOC/Operations | Sections 8, 9, 10, 11, 12 |
| Developer | Sections 3, 6, 14 |

### 1.3 Document Conventions

| Convention | Meaning |
|------------|---------|
| `monospace` | Commands, configuration keys, file paths |
| **bold** | Important terms, UI elements |
| *italics* | Variable values to be replaced |
| ⚠️ | Warning - potential service impact |
| 📝 | Note - additional information |
| ✅ | Verified/recommended configuration |

### 1.4 Service Level Agreements (SLA)

The SS7 HA Gateway is designed to meet carrier-grade SLA requirements:

#### Availability Targets

| Deployment Mode | Target Availability | Downtime/Year |
|-----------------|--------------------:|---------------|
| Standalone | 99.9% | 8.76 hours |
| HA (Active-Active) | 99.99% | 52.6 minutes |
| N+1 | 99.99% | 52.6 minutes |

#### Recovery Objectives

| Metric | Target | Description |
|--------|--------|-------------|
| **RTO** (Recovery Time Objective) | < 5 seconds | Time to restore service after failure |
| **RPO** (Recovery Point Objective) | 0 (Zero) | No data loss (stateless architecture) |
| **MTTR** (Mean Time To Recovery) | < 30 seconds | Average recovery time including detection |
| **MTBF** (Mean Time Between Failures) | > 720 hours | Expected uptime between failures |

#### Performance SLAs

| Metric | Target | Measurement |
|--------|--------|-------------|
| Dialog Throughput | > 50,000/sec | Per instance |
| Dialog Latency (P99) | < 100 ms | End-to-end processing |
| NATS Publish Latency | < 2 ms | Event publishing |
| Error Rate | < 0.1% | Failed dialogs / total dialogs |
| CPU Utilization | < 70% | Steady state |
| Memory Utilization | < 80% | Steady state |

#### SLA Exclusions

The following scenarios are excluded from SLA calculations:
- Planned maintenance windows (with 48-hour notice)
- External network failures (SCTP peer unavailable)
- NATS cluster failures (separate SLA)
- Force majeure events
- Customer-initiated configuration errors

#### SLA Monitoring

SLAs are monitored via:
- Prometheus metrics (see [Section 9](#9-monitoring-and-observability))
- Grafana dashboards with SLA tracking panels
- Alerting rules for SLA breach warnings (see [Section 11](#11-alarms-and-alerting))

---

## 2. Product Overview

### 2.1 What is SS7 HA Gateway?

The SS7 HA Gateway is an open-source, event-driven protocol handling layer that enables modern applications to communicate with legacy SS7 telecom infrastructure. It processes:

- **MAP (Mobile Application Part)**: SMS, HLR queries, subscriber information
- **CAP (CAMEL Application Part)**: Intelligent Network services, prepaid, VAS

### 2.2 Key Features

| Feature | Description |
|---------|-------------|
| **High Availability** | Automatic failover with sub-5s recovery time |
| **Horizontal Scaling** | Scale to 10+ instances with NATS queue groups |
| **Event-Driven** | JSON events published to NATS for downstream processing |
| **Protocol Support** | M3UA, SCCP, TCAP, MAP v1-v3, CAP v1-v4 |
| **Dialog Persistence** | Optional NATS JetStream KV for dialog state |
| **Monitoring** | Prometheus metrics, structured logging |
| **Performance** | 50,000+ dialogs/second per instance |

### 2.3 Supported Operations

#### MAP Operations
| Operation | Direction | Subject |
|-----------|-----------|---------|
| MO-ForwardSM | Network → Gateway | `map.mo.sms.response` |
| MT-ForwardSM | Gateway → Network | `map.mt.sms.request` |
| SendRoutingInfoForSM | Bidirectional | `map.sri.request/response` |
| AlertServiceCentre | Network → Gateway | `map.alert.response` |
| ReportSMDeliveryStatus | Gateway → Network | `map.delivery.report` |

#### CAP Operations
| Operation | Direction | Subject |
|-----------|-----------|---------|
| InitialDP | Network → Gateway | `cap.initial.dp` |
| Continue | Gateway → Network | `cap.continue` |
| ReleaseCall | Gateway → Network | `cap.release` |
| ApplyCharging | Gateway → Network | `cap.charging` |

> **📝 Implementation Status Note**
>
> | Service | Status | Notes |
> |---------|--------|-------|
> | MAP SMS (MO/MT-ForwardSM) | ✅ Fully Implemented | Production ready |
> | MAP SRI-SM | ✅ Fully Implemented | HLR queries supported |
> | MAP Delivery Reports | ✅ Implemented | Status callbacks |
> | CAP InitialDP | ⚠️ Planned | Roadmap Q1 2026 |
> | CAP Continue/Release | ⚠️ Planned | Roadmap Q1 2026 |
> | CAP ApplyCharging | ⚠️ Planned | Roadmap Q2 2026 |
> | USSD | ⚠️ Planned | Roadmap Q2 2026 |
>
> The CAP service listener framework is in place but handlers are not yet implemented. Contact the development team for early access or custom implementation requirements.

### 2.4 Technology Stack

| Component | Technology | Version | License |
|-----------|------------|---------|---------|
| SS7 Stack | Corsac JSS7 | 10.0.58 | AGPL-3.0 |
| Messaging | NATS + JetStream | 2.17.2+ | Apache-2.0 |
| Serialization | Jackson | 2.15.2+ | Apache-2.0 |
| Runtime | Java | 11+ (LTS) | GPL-2.0 |
| Logging | SLF4J + Log4j2 | 2.x | Apache-2.0 |

### 2.5 Compatibility Matrix

#### SS7 HA Gateway Version Compatibility

| Gateway Version | Java | NATS Server | Corsac JSS7 | Kubernetes | Status |
|-----------------|------|-------------|-------------|------------|--------|
| 2.0.x (Current) | 11, 17, 21 | 2.9+ | 10.0.58 | 1.24+ | ✅ Supported |
| 1.1.x | 11, 17 | N/A (Kafka) | 10.0.58 | 1.22+ | ⚠️ Deprecated |
| 1.0.x | 8, 11 | N/A (Kafka) | 10.0.52 | 1.20+ | ❌ EOL |

#### NATS Version Compatibility

| NATS Server | JetStream | KV Store | Gateway Support |
|-------------|-----------|----------|-----------------|
| 2.10.x | ✅ Full | ✅ Full | ✅ Recommended |
| 2.9.x | ✅ Full | ✅ Full | ✅ Supported |
| 2.8.x | ✅ Full | ⚠️ Limited | ⚠️ Min Version |
| < 2.8 | ❌ No | ❌ No | ❌ Not Supported |

#### Java Version Compatibility

| Java Version | Build | Runtime | Performance | Recommendation |
|--------------|-------|---------|-------------|----------------|
| Java 21 (LTS) | ✅ | ✅ | Best | ✅ Production |
| Java 17 (LTS) | ✅ | ✅ | Excellent | ✅ Production |
| Java 11 (LTS) | ✅ | ✅ | Good | ✅ Production |
| Java 8 | ❌ | ❌ | N/A | ❌ Not Supported |

#### Kubernetes Version Compatibility

| K8s Version | SCTP Support | HPA v2 | PDB v1 | Gateway Support |
|-------------|--------------|--------|--------|-----------------|
| 1.28+ | ✅ GA | ✅ | ✅ | ✅ Recommended |
| 1.24-1.27 | ✅ GA | ✅ | ✅ | ✅ Supported |
| 1.20-1.23 | ✅ GA | ⚠️ v2beta2 | ⚠️ v1beta1 | ⚠️ Limited |
| < 1.20 | ⚠️ Beta | ❌ | ❌ | ❌ Not Supported |

#### CNI Plugin Compatibility (for SCTP)

| CNI Plugin | SCTP Support | Tested Version | Notes |
|------------|--------------|----------------|-------|
| Calico | ✅ Full | 3.26+ | Recommended |
| Cilium | ✅ Full | 1.14+ | eBPF mode supported |
| Flannel | ⚠️ Kernel | 0.22+ | Requires kernel SCTP |
| AWS VPC CNI | ❌ No | N/A | Use TCP tunneling |
| Azure CNI | ⚠️ Limited | N/A | Test before use |
| GKE CNI | ❌ No | N/A | Use TCP tunneling |

#### Legend
- ✅ Fully supported and tested
- ⚠️ Limited support or deprecated
- ❌ Not supported

---

## 3. Architecture

### 3.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         SS7 NETWORK                                      │
│   ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐            │
│   │   HLR   │    │   MSC   │    │   VLR   │    │   SCP   │            │
│   └────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘            │
│        │              │              │              │                   │
│        └──────────────┴──────────────┴──────────────┘                   │
│                              │ SIGTRAN (M3UA/SCTP)                       │
└──────────────────────────────┼──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     SS7 HA GATEWAY CLUSTER                               │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐               │
│  │  Gateway-0    │  │  Gateway-1    │  │  Gateway-2    │               │
│  │  ┌─────────┐  │  │  ┌─────────┐  │  │  ┌─────────┐  │               │
│  │  │ M3UA    │  │  │  │ M3UA    │  │  │  │ M3UA    │  │               │
│  │  │ SCCP    │  │  │  │ SCCP    │  │  │  │ SCCP    │  │               │
│  │  │ TCAP    │  │  │  │ TCAP    │  │  │  │ TCAP    │  │               │
│  │  │ MAP/CAP │  │  │  │ MAP/CAP │  │  │  │ MAP/CAP │  │               │
│  │  └─────────┘  │  │  └─────────┘  │  │  └─────────┘  │               │
│  │       │       │  │       │       │  │       │       │               │
│  │  NATS Client  │  │  NATS Client  │  │  NATS Client  │               │
│  └───────┬───────┘  └───────┬───────┘  └───────┬───────┘               │
│          │                  │                  │                        │
│          └──────────────────┴──────────────────┘                        │
│                             │                                           │
└─────────────────────────────┼───────────────────────────────────────────┘
                              │ NATS (Pub/Sub + KV)
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        NATS CLUSTER                                      │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐                          │
│  │  NATS-0  │◄──►│  NATS-1  │◄──►│  NATS-2  │                          │
│  │ JetStream│    │ JetStream│    │ JetStream│                          │
│  └──────────┘    └──────────┘    └──────────┘                          │
│       │               │               │                                 │
│       └───────────────┴───────────────┘                                 │
└───────────────────────────┼─────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    DOWNSTREAM APPLICATIONS                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────────┐  │
│  │    SMSC     │  │  IN/SCP     │  │  Billing    │  │ Analytics     │  │
│  │  (MT-SMS)   │  │  (Prepaid)  │  │  System     │  │ Platform      │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Protocol Stack

```
┌─────────────────────────────────────┐
│         APPLICATION LAYER           │
│    (MAP Services, CAP Services)     │
├─────────────────────────────────────┤
│             TCAP LAYER              │
│   (Dialog Management, Components)   │
├─────────────────────────────────────┤
│             SCCP LAYER              │
│    (Global Title Routing, SSN)      │
├─────────────────────────────────────┤
│             M3UA LAYER              │
│      (SS7 over IP Adaptation)       │
├─────────────────────────────────────┤
│             SCTP LAYER              │
│    (Stream Control Transport)       │
├─────────────────────────────────────┤
│              IP LAYER               │
└─────────────────────────────────────┘
```

### 3.3 Component Responsibilities

| Component | Responsibility |
|-----------|---------------|
| **SS7HAGatewayBootstrap** | Main entry point, initializes all layers in order |
| **SS7Configuration** | Loads and validates configuration properties |
| **MapSmsServiceListener** | Handles MAP SMS operations, dialog lifecycle |
| **NatsDialogStore** | Persists dialog state to NATS JetStream KV |
| **SS7NatsPublisher** | Publishes SS7 events to NATS subjects |
| **SS7NatsSubscriber** | Subscribes to request topics with queue groups |
| **GTManager** | Manages Global Title routing rules |

### 3.4 Data Flow - MO-SMS (Mobile Originated)

```
1. Mobile → MSC: SMS Submit
2. MSC → Gateway: MO-ForwardSM (M3UA/SCTP)
3. Gateway:
   a. SCCP routes based on GT/SSN
   b. TCAP creates dialog
   c. MAP decodes operation
4. MapSmsServiceListener.onMoForwardShortMessageRequest()
5. Gateway → NATS: Publish to "map.mo.sms.response"
6. Downstream App: Consumes message, processes
7. Gateway → MSC: MAP acknowledgment
```

### 3.5 Data Flow - MT-SMS (Mobile Terminated)

```
1. Downstream App → NATS: Publish to "map.mt.sms.request"
2. Gateway (via queue group): Receives request
3. Gateway:
   a. Query HLR for routing (SRI-SM)
   b. Receive MSC/VLR address
   c. Send MT-ForwardSM to MSC
4. MSC → Mobile: Deliver SMS
5. MSC → Gateway: Delivery Report
6. Gateway → NATS: Publish to "map.mt.sms.response"
```

---

## 4. Prerequisites and System Requirements

### 4.1 Hardware Requirements

#### Minimum (Development/Testing)
| Resource | Requirement |
|----------|-------------|
| CPU | 2 cores |
| Memory | 4 GB RAM |
| Disk | 20 GB SSD |
| Network | 1 Gbps NIC |

#### Recommended (Production - Per Instance)
| Resource | Requirement |
|----------|-------------|
| CPU | 4+ cores |
| Memory | 8 GB RAM |
| Disk | 50 GB SSD |
| Network | 10 Gbps NIC |

#### Cluster Sizing Guidelines
| Traffic Volume | Gateway Instances | NATS Nodes | Total Memory |
|----------------|-------------------|------------|--------------|
| < 1,000 TPS | 2 | 3 | 16 GB |
| 1,000-5,000 TPS | 3 | 3 | 24 GB |
| 5,000-20,000 TPS | 5 | 5 | 40 GB |
| > 20,000 TPS | 8+ | 5+ | 64+ GB |

### 4.2 Software Requirements

| Software | Minimum Version | Recommended | Notes |
|----------|-----------------|-------------|-------|
| Java JDK | 11 | 17 (LTS) | Eclipse Temurin recommended |
| Maven | 3.6.0 | 3.9+ | For building from source |
| Docker | 20.10 | 24.0+ | For containerized deployment |
| Kubernetes | 1.24 | 1.28+ | For orchestrated deployment |
| NATS Server | 2.9.0 | 2.10+ | JetStream enabled |
| Helm | 3.10 | 3.13+ | Optional, for Helm deployments |

### 4.3 Network Requirements

#### Ports
| Port | Protocol | Direction | Purpose |
|------|----------|-----------|---------|
| 2905 | SCTP | Inbound | M3UA signaling |
| 4222 | TCP | Outbound | NATS client connections |
| 6222 | TCP | Internal | NATS cluster routing |
| 8222 | TCP | Internal | NATS monitoring |
| 9090 | TCP | Inbound | Prometheus metrics |
| 8080 | TCP | Inbound | Health checks |

#### Firewall Rules
```bash
# Allow M3UA from SS7 network
iptables -A INPUT -p sctp --dport 2905 -s <STP_IP> -j ACCEPT

# Allow NATS client connections
iptables -A OUTPUT -p tcp --dport 4222 -d <NATS_CLUSTER> -j ACCEPT

# Allow metrics scraping
iptables -A INPUT -p tcp --dport 9090 -s <PROMETHEUS_IP> -j ACCEPT

# Allow health checks
iptables -A INPUT -p tcp --dport 8080 -s <K8S_NODES> -j ACCEPT
```

### 4.4 Operating System Requirements

| OS | Version | Notes |
|----|---------|-------|
| RHEL/CentOS | 8+ | Preferred for telecom |
| Ubuntu | 20.04+ | Good for cloud deployments |
| Alpine Linux | 3.18+ | Container base image |

#### Required Packages
```bash
# RHEL/CentOS
yum install -y lksctp-tools java-17-openjdk-headless

# Ubuntu/Debian
apt-get install -y lksctp-tools openjdk-17-jre-headless

# Alpine (Docker)
apk add --no-cache lksctp-tools openjdk17-jre-headless
```

### 4.5 SCTP Kernel Configuration

```bash
# Enable SCTP module
modprobe sctp
echo "sctp" >> /etc/modules-load.d/sctp.conf

# Verify SCTP is loaded
lsmod | grep sctp

# Recommended sysctl settings for high-performance SCTP
cat >> /etc/sysctl.d/99-sctp.conf << EOF
# SCTP buffer sizes
net.sctp.rmem_default = 262144
net.sctp.wmem_default = 262144
net.sctp.rmem_max = 16777216
net.sctp.wmem_max = 16777216

# SCTP association settings
net.sctp.max_associations = 10000
net.sctp.association_max_retrans = 10

# SCTP heartbeat
net.sctp.hb_interval = 5000
net.sctp.path_max_retrans = 5
EOF

sysctl -p /etc/sysctl.d/99-sctp.conf
```

---

## 5. Installation Guide

### 5.1 Installation Methods

| Method | Use Case | Complexity |
|--------|----------|------------|
| Docker Compose | Development, Testing | Low |
| Kubernetes | Production, HA | Medium |
| Bare Metal | Legacy, Special Requirements | High |

### 5.2 Building from Source

```bash
# Clone the repository
git clone https://github.com/3eAI-Labs/ss7-ha-gateway.git
cd ss7-ha-gateway

# Build with Maven
mvn clean package -DskipTests

# Build Docker image
docker build -t ss7-ha-gateway:latest -f docker/Dockerfile .
```

#### Build Verification
```bash
# Verify JAR creation
ls -la ss7-core/target/ss7-core-*.jar

# Verify Docker image
docker images | grep ss7-ha-gateway
```

### 5.3 Docker Compose Installation (Development)

#### Step 1: Prepare Environment
```bash
cd ss7-ha-gateway

# Create required directories
mkdir -p data/nats data/logs

# Copy and customize configuration
cp config/ss7-config.properties.template config/ss7-config.properties
cp config/application.properties.template config/application.properties
```

#### Step 2: Configure NATS
Create `docker/nats.conf`:
```
# NATS Server Configuration
port: 4222
http_port: 8222

jetstream {
    store_dir: /data
    max_memory_store: 1GB
    max_file_store: 10GB
}

cluster {
    name: nats-cluster
    port: 6222
    routes: [
        nats-route://nats-1:6222
        nats-route://nats-2:6222
    ]
}
```

#### Step 3: Create Docker Compose File
Create `docker-compose.yml`:
```yaml
version: '3.8'

services:
  # NATS Cluster
  nats-0:
    image: nats:2.10-alpine
    container_name: nats-0
    command: ["-c", "/etc/nats/nats.conf", "-n", "nats-0"]
    ports:
      - "4222:4222"
      - "8222:8222"
    volumes:
      - ./docker/nats.conf:/etc/nats/nats.conf:ro
      - nats-data-0:/data
    networks:
      - ss7-network
    restart: unless-stopped

  nats-1:
    image: nats:2.10-alpine
    container_name: nats-1
    command: ["-c", "/etc/nats/nats.conf", "-n", "nats-1"]
    volumes:
      - ./docker/nats.conf:/etc/nats/nats.conf:ro
      - nats-data-1:/data
    networks:
      - ss7-network
    restart: unless-stopped

  nats-2:
    image: nats:2.10-alpine
    container_name: nats-2
    command: ["-c", "/etc/nats/nats.conf", "-n", "nats-2"]
    volumes:
      - ./docker/nats.conf:/etc/nats/nats.conf:ro
      - nats-data-2:/data
    networks:
      - ss7-network
    restart: unless-stopped

  # SS7 HA Gateway
  ss7-gateway-0:
    image: ss7-ha-gateway:latest
    container_name: ss7-gateway-0
    environment:
      - NODE_ID=ss7-gateway-0
      - NATS_URL=nats://nats-0:4222,nats://nats-1:4222,nats://nats-2:4222
      - JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC
      - M3UA_LOCAL_HOST=0.0.0.0
      - M3UA_LOCAL_PORT=2905
      - SCCP_LOCAL_PC=1
      - SCCP_LOCAL_SSN=8
    ports:
      - "2905:2905/sctp"
      - "9090:9090"
    volumes:
      - ./config:/app/config:ro
      - ./data/logs:/app/logs
    networks:
      - ss7-network
    depends_on:
      - nats-0
      - nats-1
      - nats-2
    restart: unless-stopped
    cap_add:
      - NET_ADMIN

  # Prometheus
  prometheus:
    image: prom/prometheus:v2.47.0
    container_name: prometheus
    ports:
      - "9000:9090"
    volumes:
      - ./docker/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    networks:
      - ss7-network
    restart: unless-stopped

  # Grafana
  grafana:
    image: grafana/grafana:10.2.0
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-data:/var/lib/grafana
    networks:
      - ss7-network
    restart: unless-stopped

networks:
  ss7-network:
    driver: bridge

volumes:
  nats-data-0:
  nats-data-1:
  nats-data-2:
  grafana-data:
```

#### Step 4: Start Services
```bash
# Start all services
docker-compose up -d

# Verify all containers are running
docker-compose ps

# Check gateway logs
docker-compose logs -f ss7-gateway-0
```

#### Step 5: Verify Installation
```bash
# Check NATS connectivity
curl http://localhost:8222/varz

# Check gateway metrics
curl http://localhost:9090/metrics

# Check health endpoint
curl http://localhost:8080/health
```

### 5.4 Bare Metal Installation

#### Step 1: Install Prerequisites
```bash
# Install Java 17
yum install -y java-17-openjdk-headless

# Install SCTP tools
yum install -y lksctp-tools

# Verify Java
java -version

# Verify SCTP
checksctp
```

#### Step 2: Create Application User
```bash
# Create non-root user
useradd -r -s /bin/false ss7gateway
mkdir -p /opt/ss7-gateway/{bin,config,logs,lib}
chown -R ss7gateway:ss7gateway /opt/ss7-gateway
```

#### Step 3: Deploy Application
```bash
# Copy application files
cp ss7-core/target/ss7-core-*.jar /opt/ss7-gateway/lib/
cp -r config/* /opt/ss7-gateway/config/

# Set permissions
chmod 750 /opt/ss7-gateway/lib/*.jar
chmod 640 /opt/ss7-gateway/config/*
```

#### Step 4: Create Systemd Service
Create `/etc/systemd/system/ss7-gateway.service`:
```ini
[Unit]
Description=SS7 HA Gateway
After=network.target nats.service
Wants=network.target

[Service]
Type=simple
User=ss7gateway
Group=ss7gateway
WorkingDirectory=/opt/ss7-gateway

Environment=JAVA_HOME=/usr/lib/jvm/java-17-openjdk
Environment=JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
Environment=NATS_URL=nats://localhost:4222

ExecStart=/usr/lib/jvm/java-17-openjdk/bin/java \
    ${JAVA_OPTS} \
    -Dconfig.dir=/opt/ss7-gateway/config \
    -Dlog.dir=/opt/ss7-gateway/logs \
    -jar /opt/ss7-gateway/lib/ss7-core-1.0.0.jar

ExecStop=/bin/kill -SIGTERM $MAINPID

Restart=on-failure
RestartSec=10
TimeoutStopSec=30

# Security hardening
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
ReadWritePaths=/opt/ss7-gateway/logs

# Allow SCTP
AmbientCapabilities=CAP_NET_ADMIN CAP_NET_RAW

[Install]
WantedBy=multi-user.target
```

#### Step 5: Enable and Start Service
```bash
# Reload systemd
systemctl daemon-reload

# Enable service
systemctl enable ss7-gateway

# Start service
systemctl start ss7-gateway

# Check status
systemctl status ss7-gateway

# View logs
journalctl -u ss7-gateway -f
```

---

## 6. Configuration Guide

### 6.1 Configuration Files Overview

| File | Purpose | Location |
|------|---------|----------|
| `ss7-config.properties` | SS7 stack configuration | `/app/config/` |
| `application.properties` | Application settings | `/app/config/` |
| `log4j2.xml` | Logging configuration | `/app/config/` |

### 6.2 SS7 Stack Configuration

#### M3UA Layer Configuration
```properties
# M3UA Stack Name
m3ua.name=M3UA-STACK

# SCTP Server Configuration (Local endpoint)
m3ua.local.host=0.0.0.0
m3ua.local.port=2905

# Remote Peer Configuration
m3ua.sctp.remote.host=10.0.0.100
m3ua.sctp.remote.port=2905

# M3UA Routing Context
m3ua.routing.context=100

# ASP Configuration
m3ua.asp.name=ASP1
m3ua.asp.functionality=IPSP

# AS Configuration
m3ua.as.name=AS1
m3ua.as.mode=OVERRIDE
m3ua.as.traffic.mode=LOADSHARE

# Connection Settings
m3ua.heartbeat.enabled=true
m3ua.heartbeat.interval=5000
```

| Parameter | Description | Default | Range |
|-----------|-------------|---------|-------|
| `m3ua.local.host` | Local bind address | `0.0.0.0` | Valid IP |
| `m3ua.local.port` | SCTP listening port | `2905` | 1024-65535 |
| `m3ua.routing.context` | M3UA routing context | `100` | 0-4294967295 |
| `m3ua.asp.functionality` | ASP type | `IPSP` | ASP, IPSP, SGW |

#### SCCP Layer Configuration
```properties
# SCCP Stack Name
sccp.name=SCCP-STACK

# Local Point Code
sccp.local.pc=1-1-1
# Alternative formats: sccp.local.pc=1 (integer)

# Remote Point Code
sccp.remote.pc=2-2-2

# Subsystem Numbers
sccp.local.ssn=8
sccp.remote.ssn=6
sccp.cap.ssn=146

# Global Titles
sccp.local.gt=123456789
sccp.gt.generic1=123456789
sccp.gt.generic2=123456790

# Internal GT Routing (for load distribution)
sccp.gt.internal[0].name=GT-International
sccp.gt.internal[0].address=123456791
sccp.gt.internal[0].pattern=^\\+.*
sccp.gt.internal[0].description=International SMS

sccp.gt.internal[1].name=GT-National
sccp.gt.internal[1].address=123456792
sccp.gt.internal[1].pattern=^0.*
sccp.gt.internal[1].description=National SMS

sccp.gt.internal[2].name=GT-Premium
sccp.gt.internal[2].address=123456793
sccp.gt.internal[2].pattern=^[1-9][0-9]{3,6}$
sccp.gt.internal[2].description=Premium/Short codes

sccp.gt.internal[3].name=GT-Bulk
sccp.gt.internal[3].address=123456794
sccp.gt.internal[3].pattern=BULK
sccp.gt.internal[3].description=Bulk SMS

sccp.gt.internal[4].name=GT-Emergency
sccp.gt.internal[4].address=123456795
sccp.gt.internal[4].pattern=^(911|112|999)$
sccp.gt.internal[4].description=Emergency services

# Network Indicator
sccp.network.indicator=NATIONAL
# Options: INTERNATIONAL, NATIONAL, RESERVED, SPARE
```

| Parameter | Description | Values |
|-----------|-------------|--------|
| `sccp.local.pc` | Local point code | Format: `x-x-x` or integer |
| `sccp.local.ssn` | Local subsystem number | 6=HLR, 8=MSC, 146=CAP |
| `sccp.network.indicator` | Network type | INTERNATIONAL, NATIONAL |

#### TCAP Layer Configuration
```properties
# TCAP Stack Name
tcap.name=TCAP-STACK

# Timeout Settings (milliseconds)
tcap.dialog.idle.timeout=60000
tcap.invoke.timeout=30000

# Dialog Limits
tcap.max.dialogs=5000
tcap.dialog.range.start=1
tcap.dialog.range.end=2147483647

# Preview Mode (for monitoring only)
tcap.preview.mode=false
```

| Parameter | Description | Default | Notes |
|-----------|-------------|---------|-------|
| `tcap.dialog.idle.timeout` | Dialog timeout | `60000` ms | Close idle dialogs |
| `tcap.invoke.timeout` | Invoke timeout | `30000` ms | Operation timeout |
| `tcap.max.dialogs` | Max concurrent dialogs | `5000` | Tune based on capacity |

#### MAP Layer Configuration
```properties
# MAP Stack Name
map.name=MAP-STACK

# MAP Version
map.version=MAP_V3
# Options: MAP_V1, MAP_V2, MAP_V3

# Service Enablement
map.sms.enabled=true
map.ussd.enabled=false
map.location.enabled=false

# Congestion Control
map.congestion.control.enabled=true
map.congestion.threshold=80
```

#### CAP Layer Configuration
```properties
# CAP Enablement
cap.enabled=true

# CAP Version
cap.version=CAP_V4
# Options: CAP_V1, CAP_V2, CAP_V3, CAP_V4

# CAP SSN (always 146 for CAMEL)
cap.ssn=146
```

### 6.3 NATS Configuration

```properties
# NATS Server URL(s)
nats.server.url=nats://nats-0:4222,nats://nats-1:4222,nats://nats-2:4222

# Queue Group (for load balancing)
nats.queue.group=ss7-gateway-group-v2

# Connection Settings
nats.connection.timeout.ms=5000
nats.reconnect.wait.ms=1000
nats.max.reconnects=-1
nats.ping.interval.ms=120000

# JetStream Settings (for dialog persistence)
nats.jetstream.enabled=true
nats.jetstream.bucket.name=ss7-dialog-state
nats.jetstream.replicas=3

# Dialog Persistence
dialog.persist.enabled=true
dialog.ttl.seconds=600

# Event Publishing
events.publish.enabled=true
```

| Parameter | Description | Default |
|-----------|-------------|---------|
| `nats.server.url` | NATS server URLs | `nats://localhost:4222` |
| `nats.queue.group` | Queue group name | `ss7-gateway-group-v2` |
| `nats.max.reconnects` | Max reconnect attempts | `-1` (unlimited) |
| `dialog.ttl.seconds` | Dialog state TTL | `600` |

#### JetStream Key-Value Bucket Setup

The SS7 HA Gateway uses NATS JetStream Key-Value store for dialog state persistence. The bucket must be created before starting the gateway (or it will be auto-created with defaults).

**Manual Bucket Creation (Recommended for Production)**
```bash
# Connect to NATS CLI
# Install: https://github.com/nats-io/natscli

# Create the dialog state bucket with production settings
nats kv add ss7-dialog-state \
  --replicas=3 \
  --ttl=10m \
  --history=1 \
  --storage=file \
  --max-value-size=1MB \
  --description="SS7 Gateway Dialog State Store"

# Verify bucket creation
nats kv ls

# View bucket info
nats kv info ss7-dialog-state

# Expected output:
# Configuration:
#   Bucket: ss7-dialog-state
#   Replicas: 3
#   TTL: 10m0s
#   Storage: File
#   Max Value Size: 1.0 MB
```

**Kubernetes Job for Bucket Creation**
```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: create-nats-kv-bucket
  namespace: ss7-ha-gateway
spec:
  template:
    spec:
      containers:
      - name: nats-cli
        image: natsio/nats-box:latest
        command:
        - /bin/sh
        - -c
        - |
          nats kv add ss7-dialog-state \
            --server=nats://nats.ss7-ha-gateway.svc.cluster.local:4222 \
            --replicas=3 \
            --ttl=10m \
            --storage=file \
            || echo "Bucket already exists"
      restartPolicy: OnFailure
```

**Bucket Management Commands**
```bash
# List all keys in bucket
nats kv ls ss7-dialog-state

# Get a specific dialog state
nats kv get ss7-dialog-state dialog:12345

# Watch for changes (debugging)
nats kv watch ss7-dialog-state

# Purge all keys (CAUTION: data loss)
nats kv purge ss7-dialog-state

# Delete bucket (CAUTION: destroys data)
nats kv rm ss7-dialog-state
```

### 6.4 Application Configuration

```properties
# Application Identity
application.name=SS7-HA-Gateway
application.version=2.0.0
application.node.id=${NODE_ID:node-1}
application.datacenter=${DATACENTER:dc1}

# HA Mode
ha.mode=ACTIVE_ACTIVE
# Options: ACTIVE_STANDBY, ACTIVE_ACTIVE

# Thread Pool Settings
thread.pool.core.size=50
thread.pool.max.size=200
thread.pool.queue.capacity=1000
thread.pool.keep.alive.seconds=60

# Performance Settings
performance.max.concurrent.dialogs=5000
performance.message.buffer.size=10000

# Health Check Settings
health.check.enabled=true
health.check.port=8080
health.check.path=/health
health.check.interval.ms=30000

# Circuit Breaker
circuit.breaker.enabled=true
circuit.breaker.failure.threshold=5
circuit.breaker.timeout.ms=30000
circuit.breaker.half.open.requests=3

# Graceful Shutdown
shutdown.timeout.ms=30000
```

### 6.5 Environment Variable Overrides

All configuration properties can be overridden via environment variables:

| Property | Environment Variable |
|----------|---------------------|
| `m3ua.local.host` | `M3UA_LOCAL_HOST` |
| `m3ua.local.port` | `M3UA_LOCAL_PORT` |
| `sccp.local.pc` | `SCCP_LOCAL_PC` |
| `sccp.local.ssn` | `SCCP_LOCAL_SSN` |
| `nats.server.url` | `NATS_URL` |
| `nats.queue.group` | `NATS_QUEUE_GROUP` |

**Naming Convention:** Replace `.` with `_` and convert to uppercase.

### 6.6 Logging Configuration

Create `log4j2.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" monitorInterval="30">
    <Properties>
        <Property name="LOG_DIR">${sys:log.dir:-/app/logs}</Property>
        <Property name="LOG_PATTERN">%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</Property>
    </Properties>

    <Appenders>
        <!-- Console Appender -->
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="${LOG_PATTERN}"/>
        </Console>

        <!-- Rolling File Appender -->
        <RollingFile name="RollingFile"
                     fileName="${LOG_DIR}/ss7-gateway.log"
                     filePattern="${LOG_DIR}/ss7-gateway-%d{yyyy-MM-dd}-%i.log.gz">
            <PatternLayout pattern="${LOG_PATTERN}"/>
            <Policies>
                <SizeBasedTriggeringPolicy size="100MB"/>
                <TimeBasedTriggeringPolicy interval="1"/>
            </Policies>
            <DefaultRolloverStrategy max="30"/>
        </RollingFile>

        <!-- Error File Appender -->
        <RollingFile name="ErrorFile"
                     fileName="${LOG_DIR}/ss7-gateway-error.log"
                     filePattern="${LOG_DIR}/ss7-gateway-error-%d{yyyy-MM-dd}.log.gz">
            <PatternLayout pattern="${LOG_PATTERN}"/>
            <ThresholdFilter level="ERROR"/>
            <Policies>
                <TimeBasedTriggeringPolicy interval="1"/>
            </Policies>
            <DefaultRolloverStrategy max="90"/>
        </RollingFile>

        <!-- Audit File Appender -->
        <RollingFile name="AuditFile"
                     fileName="${LOG_DIR}/ss7-audit.log"
                     filePattern="${LOG_DIR}/ss7-audit-%d{yyyy-MM-dd}.log.gz">
            <PatternLayout pattern="%d{ISO8601} %msg%n"/>
            <Policies>
                <TimeBasedTriggeringPolicy interval="1"/>
            </Policies>
            <DefaultRolloverStrategy max="365"/>
        </RollingFile>
    </Appenders>

    <Loggers>
        <!-- SS7 Core Logging -->
        <Logger name="com.company.ss7ha" level="INFO" additivity="false">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="RollingFile"/>
            <AppenderRef ref="ErrorFile"/>
        </Logger>

        <!-- MAP Service Logging -->
        <Logger name="com.company.ss7ha.core.listeners" level="DEBUG" additivity="false">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="RollingFile"/>
        </Logger>

        <!-- NATS Logging -->
        <Logger name="io.nats" level="INFO" additivity="false">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="RollingFile"/>
        </Logger>

        <!-- JSS7 Stack Logging -->
        <Logger name="org.restcomm" level="WARN" additivity="false">
            <AppenderRef ref="RollingFile"/>
        </Logger>

        <!-- Audit Logging -->
        <Logger name="audit" level="INFO" additivity="false">
            <AppenderRef ref="AuditFile"/>
        </Logger>

        <Root level="INFO">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="RollingFile"/>
        </Root>
    </Loggers>
</Configuration>
```

---

## 7. Kubernetes Deployment

### 7.1 Deployment Architectures

#### Standalone Deployment
- Single gateway instance
- For development and testing
- No HA capabilities

#### High Availability (HA) Deployment
- 2+ gateway instances
- Active-Active with NATS queue groups
- Automatic failover

#### N+1 Deployment
- N active instances + 1 standby
- Standby takes over on failure
- Cost-optimized HA

### 7.1.1 SCTP Protocol Support in Kubernetes

> **⚠️ Important: SCTP Support Requirements**
>
> M3UA (SS7 over IP) requires SCTP protocol. Kubernetes SCTP support has specific requirements:

| Requirement | Details |
|-------------|---------|
| Kubernetes Version | 1.20+ (SCTP GA since 1.20) |
| CNI Plugin | Must support SCTP (Calico, Cilium, Flannel with kernel support) |
| Kernel | Linux kernel 4.4+ with SCTP module loaded |
| Feature Gate | `SCTPSupport=true` (default since 1.20) |

#### Verify SCTP Support
```bash
# Check if SCTP is enabled in cluster
kubectl get nodes -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}'

# Test SCTP pod creation
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: sctp-test
spec:
  containers:
  - name: sctp-test
    image: alpine
    command: ["sleep", "infinity"]
    ports:
    - containerPort: 2905
      protocol: SCTP
EOF

# Verify pod runs successfully
kubectl get pod sctp-test
kubectl delete pod sctp-test
```

#### SCTP Limitations and Workarounds

| Limitation | Workaround |
|------------|------------|
| Cloud provider LB doesn't support SCTP | Use `externalTrafficPolicy: Local` with MetalLB or bare-metal |
| NodePort doesn't support SCTP | Use HostNetwork or dedicated ingress |
| Some CNIs lack SCTP support | Use Calico or Cilium |
| Network policies for SCTP | Ensure policies explicitly allow SCTP/132 |

#### Alternative: TCP Tunneling (Not Recommended)
If SCTP is not available, some deployments use SCTP-over-TCP tunneling, but this adds latency and is not recommended for production SS7 workloads.

```yaml
# Example: Using TCP instead of SCTP (development only)
# Note: This requires SCTP-TCP gateway/proxy
ports:
  - containerPort: 2905
    protocol: TCP  # Only for dev/test without native SCTP
```

### 7.2 Namespace and Prerequisites

```yaml
# File: 00-namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ss7-ha-gateway
  labels:
    app.kubernetes.io/name: ss7-ha-gateway
    app.kubernetes.io/component: telecom
---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: ss7-gateway-quota
  namespace: ss7-ha-gateway
spec:
  hard:
    requests.cpu: "20"
    requests.memory: "40Gi"
    limits.cpu: "40"
    limits.memory: "80Gi"
    persistentvolumeclaims: "10"
    pods: "20"
```

### 7.3 NATS Deployment

```yaml
# File: 01-nats.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: nats-config
  namespace: ss7-ha-gateway
data:
  nats.conf: |
    port: 4222
    http_port: 8222

    server_name: $POD_NAME

    jetstream {
      store_dir: /data
      max_memory_store: 1GB
      max_file_store: 10GB
    }

    cluster {
      name: nats-cluster
      port: 6222
      routes: [
        nats-route://nats-0.nats.ss7-ha-gateway.svc.cluster.local:6222
        nats-route://nats-1.nats.ss7-ha-gateway.svc.cluster.local:6222
        nats-route://nats-2.nats.ss7-ha-gateway.svc.cluster.local:6222
      ]
    }
---
apiVersion: v1
kind: Service
metadata:
  name: nats
  namespace: ss7-ha-gateway
  labels:
    app: nats
spec:
  clusterIP: None
  ports:
    - name: client
      port: 4222
    - name: cluster
      port: 6222
    - name: monitor
      port: 8222
  selector:
    app: nats
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: nats
  namespace: ss7-ha-gateway
spec:
  serviceName: nats
  replicas: 3
  selector:
    matchLabels:
      app: nats
  template:
    metadata:
      labels:
        app: nats
    spec:
      containers:
        - name: nats
          image: nats:2.10-alpine
          command:
            - nats-server
            - -c
            - /etc/nats/nats.conf
          env:
            - name: POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
          ports:
            - containerPort: 4222
              name: client
            - containerPort: 6222
              name: cluster
            - containerPort: 8222
              name: monitor
          volumeMounts:
            - name: config
              mountPath: /etc/nats
            - name: data
              mountPath: /data
          resources:
            requests:
              memory: "1Gi"
              cpu: "500m"
            limits:
              memory: "2Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /healthz
              port: 8222
            initialDelaySeconds: 10
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /healthz
              port: 8222
            initialDelaySeconds: 5
            periodSeconds: 10
      volumes:
        - name: config
          configMap:
            name: nats-config
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        storageClassName: fast-ssd
        resources:
          requests:
            storage: 10Gi
```

### 7.4 Secrets Configuration

```yaml
# File: 02-secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: ss7-gateway-secrets
  namespace: ss7-ha-gateway
type: Opaque
stringData:
  # NATS Authentication (if enabled)
  nats-user: "ss7gateway"
  nats-password: "CHANGE_ME_SECURE_PASSWORD"

  # TLS Certificates (base64 encoded)
  # tls.crt: |
  #   -----BEGIN CERTIFICATE-----
  #   ...
  #   -----END CERTIFICATE-----
  # tls.key: |
  #   -----BEGIN PRIVATE KEY-----
  #   ...
  #   -----END PRIVATE KEY-----
---
apiVersion: v1
kind: Secret
metadata:
  name: ss7-tls-certs
  namespace: ss7-ha-gateway
type: kubernetes.io/tls
data:
  # Base64 encoded certificate and key
  tls.crt: ""
  tls.key: ""
```

### 7.5 ConfigMap

```yaml
# File: 03-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: ss7-gateway-config
  namespace: ss7-ha-gateway
data:
  ss7-config.properties: |
    # M3UA Configuration
    m3ua.name=M3UA-STACK
    m3ua.sctp.server.host=0.0.0.0
    m3ua.sctp.server.port=2905
    m3ua.routing.context=100
    m3ua.asp.name=ASP1
    m3ua.as.name=AS1

    # SCCP Configuration
    sccp.name=SCCP-STACK
    sccp.local.pc=1
    sccp.local.ssn=8
    sccp.local.gt=123456789
    sccp.network.indicator=NATIONAL

    # TCAP Configuration
    tcap.name=TCAP-STACK
    tcap.dialog.idle.timeout=60000
    tcap.invoke.timeout=30000
    tcap.max.dialogs=5000

    # MAP Configuration
    map.name=MAP-STACK
    map.version=MAP_V3

    # Monitoring
    monitoring.prometheus.enabled=true
    monitoring.prometheus.port=9090

  application.properties: |
    # NATS Configuration
    nats.server.url=nats://nats.ss7-ha-gateway.svc.cluster.local:4222
    nats.queue.group=ss7-gateway-group-v2
    nats.jetstream.enabled=true
    nats.jetstream.bucket.name=ss7-dialog-state

    # Dialog Persistence
    dialog.persist.enabled=true
    dialog.ttl.seconds=600

    # Event Publishing
    events.publish.enabled=true

    # Health Check
    health.check.enabled=true
    health.check.port=8080

    # HA Mode
    ha.mode=ACTIVE_ACTIVE

  log4j2.xml: |
    <?xml version="1.0" encoding="UTF-8"?>
    <Configuration status="WARN">
        <Appenders>
            <Console name="Console" target="SYSTEM_OUT">
                <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"/>
            </Console>
        </Appenders>
        <Loggers>
            <Logger name="com.company.ss7ha" level="INFO"/>
            <Logger name="io.nats" level="INFO"/>
            <Root level="INFO">
                <AppenderRef ref="Console"/>
            </Root>
        </Loggers>
    </Configuration>
```

### 7.6 Standalone Deployment

```yaml
# File: 04-standalone-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ss7-gateway-standalone
  namespace: ss7-ha-gateway
  labels:
    app: ss7-ha-gateway
    deployment-mode: standalone
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ss7-ha-gateway
  template:
    metadata:
      labels:
        app: ss7-ha-gateway
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
        prometheus.io/path: "/metrics"
    spec:
      serviceAccountName: ss7-gateway
      containers:
        - name: ss7-gateway
          image: ss7-ha-gateway:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 2905
              protocol: SCTP
              name: m3ua
            - containerPort: 9090
              protocol: TCP
              name: metrics
            - containerPort: 8080
              protocol: TCP
              name: health
          env:
            - name: NODE_ID
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: NATS_URL
              value: "nats://nats.ss7-ha-gateway.svc.cluster.local:4222"
            - name: JAVA_OPTS
              value: "-Xms512m -Xmx1g -XX:+UseG1GC"
          volumeMounts:
            - name: config
              mountPath: /app/config
              readOnly: true
          resources:
            requests:
              memory: "1Gi"
              cpu: "500m"
            limits:
              memory: "2Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /metrics  # Using metrics endpoint until dedicated health endpoint is implemented
              port: 9090
            initialDelaySeconds: 60
            periodSeconds: 30
            timeoutSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /metrics  # Using metrics endpoint until dedicated health endpoint is implemented
              port: 9090
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          securityContext:
            capabilities:
              add:
                - NET_ADMIN
            readOnlyRootFilesystem: true
            runAsNonRoot: true
            runAsUser: 1000
      volumes:
        - name: config
          configMap:
            name: ss7-gateway-config
---
apiVersion: v1
kind: Service
metadata:
  name: ss7-gateway-standalone
  namespace: ss7-ha-gateway
spec:
  type: ClusterIP
  ports:
    - name: m3ua
      port: 2905
      targetPort: 2905
      protocol: SCTP
    - name: metrics
      port: 9090
      targetPort: 9090
      protocol: TCP
  selector:
    app: ss7-ha-gateway
```

### 7.7 High Availability (HA) Deployment

```yaml
# File: 05-ha-deployment.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: ss7-ha-gateway
  namespace: ss7-ha-gateway
  labels:
    app: ss7-ha-gateway
    deployment-mode: ha
spec:
  serviceName: ss7-ha-gateway
  replicas: 3
  podManagementPolicy: Parallel
  selector:
    matchLabels:
      app: ss7-ha-gateway
      component: ss7-gateway
  template:
    metadata:
      labels:
        app: ss7-ha-gateway
        component: ss7-gateway
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
        prometheus.io/path: "/metrics"
    spec:
      serviceAccountName: ss7-gateway
      terminationGracePeriodSeconds: 60
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchExpressions:
                  - key: app
                    operator: In
                    values:
                      - ss7-ha-gateway
              topologyKey: kubernetes.io/hostname
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchExpressions:
                    - key: app
                      operator: In
                      values:
                        - ss7-ha-gateway
                topologyKey: topology.kubernetes.io/zone
      containers:
        - name: ss7-gateway
          image: ss7-ha-gateway:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 2905
              protocol: SCTP
              name: m3ua
            - containerPort: 9090
              protocol: TCP
              name: metrics
            - containerPort: 8080
              protocol: TCP
              name: health
          env:
            - name: NODE_ID
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: POD_IP
              valueFrom:
                fieldRef:
                  fieldPath: status.podIP
            - name: NATS_URL
              value: "nats://nats.ss7-ha-gateway.svc.cluster.local:4222"
            - name: NATS_QUEUE_GROUP
              value: "ss7-gateway-group-v2"
            - name: HA_MODE
              value: "ACTIVE_ACTIVE"
            - name: JAVA_OPTS
              value: "-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
          volumeMounts:
            - name: config
              mountPath: /app/config
              readOnly: true
            - name: tmp
              mountPath: /tmp
          resources:
            requests:
              memory: "2Gi"
              cpu: "1000m"
            limits:
              memory: "4Gi"
              cpu: "2000m"
          livenessProbe:
            httpGet:
              path: /metrics
              port: 9090
            initialDelaySeconds: 60
            periodSeconds: 30
            timeoutSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /metrics
              port: 9090
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
            successThreshold: 1
          startupProbe:
            httpGet:
              path: /metrics
              port: 9090
            initialDelaySeconds: 10
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 30
          securityContext:
            capabilities:
              add:
                - NET_ADMIN
            readOnlyRootFilesystem: true
            runAsNonRoot: true
            runAsUser: 1000
      volumes:
        - name: config
          configMap:
            name: ss7-gateway-config
        - name: tmp
          emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: ss7-ha-gateway
  namespace: ss7-ha-gateway
  labels:
    app: ss7-ha-gateway
spec:
  clusterIP: None
  ports:
    - name: m3ua
      port: 2905
      targetPort: 2905
      protocol: SCTP
    - name: metrics
      port: 9090
      targetPort: 9090
      protocol: TCP
    - name: health
      port: 8080
      targetPort: 8080
      protocol: TCP
  selector:
    app: ss7-ha-gateway
    component: ss7-gateway
---
apiVersion: v1
kind: Service
metadata:
  name: ss7-gateway-external
  namespace: ss7-ha-gateway
  labels:
    app: ss7-ha-gateway
  annotations:
    metallb.universe.tf/address-pool: telecom-pool
spec:
  type: LoadBalancer
  externalTrafficPolicy: Local
  ports:
    - name: m3ua
      port: 2905
      targetPort: 2905
      protocol: SCTP
  selector:
    app: ss7-ha-gateway
    component: ss7-gateway
```

### 7.8 N+1 Deployment

```yaml
# File: 06-n-plus-1-deployment.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: ss7-gateway-active
  namespace: ss7-ha-gateway
  labels:
    app: ss7-ha-gateway
    role: active
    deployment-mode: n-plus-1
spec:
  serviceName: ss7-gateway-active
  replicas: 2  # N active instances
  podManagementPolicy: Parallel
  selector:
    matchLabels:
      app: ss7-ha-gateway
      role: active
  template:
    metadata:
      labels:
        app: ss7-ha-gateway
        role: active
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
    spec:
      serviceAccountName: ss7-gateway
      terminationGracePeriodSeconds: 60
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchLabels:
                  app: ss7-ha-gateway
              topologyKey: kubernetes.io/hostname
      containers:
        - name: ss7-gateway
          image: ss7-ha-gateway:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 2905
              protocol: SCTP
              name: m3ua
            - containerPort: 9090
              protocol: TCP
              name: metrics
            - containerPort: 8080
              protocol: TCP
              name: health
          env:
            - name: NODE_ID
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: NATS_URL
              value: "nats://nats.ss7-ha-gateway.svc.cluster.local:4222"
            - name: NATS_QUEUE_GROUP
              value: "ss7-gateway-active"
            - name: HA_MODE
              value: "ACTIVE_ACTIVE"
            - name: GATEWAY_ROLE
              value: "ACTIVE"
            - name: JAVA_OPTS
              value: "-Xms1g -Xmx2g -XX:+UseG1GC"
          volumeMounts:
            - name: config
              mountPath: /app/config
              readOnly: true
          resources:
            requests:
              memory: "2Gi"
              cpu: "1000m"
            limits:
              memory: "4Gi"
              cpu: "2000m"
          livenessProbe:
            httpGet:
              path: /metrics
              port: 9090
            initialDelaySeconds: 60
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /metrics
              port: 9090
            initialDelaySeconds: 30
            periodSeconds: 10
          securityContext:
            capabilities:
              add:
                - NET_ADMIN
      volumes:
        - name: config
          configMap:
            name: ss7-gateway-config
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: ss7-gateway-standby
  namespace: ss7-ha-gateway
  labels:
    app: ss7-ha-gateway
    role: standby
    deployment-mode: n-plus-1
spec:
  serviceName: ss7-gateway-standby
  replicas: 1  # +1 standby instance
  selector:
    matchLabels:
      app: ss7-ha-gateway
      role: standby
  template:
    metadata:
      labels:
        app: ss7-ha-gateway
        role: standby
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
    spec:
      serviceAccountName: ss7-gateway
      terminationGracePeriodSeconds: 60
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchLabels:
                  app: ss7-ha-gateway
              topologyKey: kubernetes.io/hostname
      containers:
        - name: ss7-gateway
          image: ss7-ha-gateway:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 2905
              protocol: SCTP
              name: m3ua
            - containerPort: 9090
              protocol: TCP
              name: metrics
            - containerPort: 8080
              protocol: TCP
              name: health
          env:
            - name: NODE_ID
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: NATS_URL
              value: "nats://nats.ss7-ha-gateway.svc.cluster.local:4222"
            - name: NATS_QUEUE_GROUP
              value: "ss7-gateway-standby"
            - name: HA_MODE
              value: "ACTIVE_STANDBY"
            - name: GATEWAY_ROLE
              value: "STANDBY"
            - name: JAVA_OPTS
              value: "-Xms512m -Xmx1g -XX:+UseG1GC"
          volumeMounts:
            - name: config
              mountPath: /app/config
              readOnly: true
          resources:
            requests:
              memory: "1Gi"
              cpu: "500m"
            limits:
              memory: "2Gi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /metrics
              port: 9090
            initialDelaySeconds: 60
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /metrics
              port: 9090
            initialDelaySeconds: 30
            periodSeconds: 10
          securityContext:
            capabilities:
              add:
                - NET_ADMIN
      volumes:
        - name: config
          configMap:
            name: ss7-gateway-config
---
# Service for active instances
apiVersion: v1
kind: Service
metadata:
  name: ss7-gateway-active
  namespace: ss7-ha-gateway
spec:
  clusterIP: None
  ports:
    - name: m3ua
      port: 2905
      protocol: SCTP
    - name: metrics
      port: 9090
  selector:
    app: ss7-ha-gateway
    role: active
---
# Service for standby instances
apiVersion: v1
kind: Service
metadata:
  name: ss7-gateway-standby
  namespace: ss7-ha-gateway
spec:
  clusterIP: None
  ports:
    - name: m3ua
      port: 2905
      protocol: SCTP
    - name: metrics
      port: 9090
  selector:
    app: ss7-ha-gateway
    role: standby
```

### 7.9 Autoscaling and Disruption Budget

```yaml
# File: 07-autoscaling.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ss7-ha-gateway-hpa
  namespace: ss7-ha-gateway
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: StatefulSet
    name: ss7-ha-gateway
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Percent
          value: 100
          periodSeconds: 60
        - type: Pods
          value: 2
          periodSeconds: 60
      selectPolicy: Max
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 50
          periodSeconds: 60
      selectPolicy: Min
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: ss7-ha-gateway-pdb
  namespace: ss7-ha-gateway
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: ss7-ha-gateway
      component: ss7-gateway
```

### 7.10 Monitoring Resources

```yaml
# File: 08-monitoring.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: ss7-ha-gateway
  namespace: ss7-ha-gateway
  labels:
    app: ss7-ha-gateway
spec:
  selector:
    matchLabels:
      app: ss7-ha-gateway
  endpoints:
    - port: metrics
      interval: 30s
      path: /metrics
      scrapeTimeout: 10s
---
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: ss7-ha-gateway-alerts
  namespace: ss7-ha-gateway
  labels:
    app: ss7-ha-gateway
spec:
  groups:
    - name: ss7-gateway-alerts
      rules:
        - alert: SS7GatewayDown
          expr: up{job="ss7-ha-gateway"} == 0
          for: 1m
          labels:
            severity: critical
          annotations:
            summary: "SS7 Gateway instance is down"
            description: "SS7 Gateway {{ $labels.instance }} has been down for more than 1 minute."

        - alert: SS7GatewayHighErrorRate
          expr: rate(ss7_dialog_errors_total[5m]) > 10
          for: 2m
          labels:
            severity: warning
          annotations:
            summary: "High error rate on SS7 Gateway"
            description: "SS7 Gateway {{ $labels.instance }} error rate is {{ $value }} errors/sec."

        - alert: SS7GatewayHighDialogCount
          expr: ss7_active_dialogs > 4000
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "High dialog count on SS7 Gateway"
            description: "SS7 Gateway {{ $labels.instance }} has {{ $value }} active dialogs."

        - alert: SS7GatewayNATSDisconnected
          expr: ss7_nats_connected == 0
          for: 30s
          labels:
            severity: critical
          annotations:
            summary: "SS7 Gateway disconnected from NATS"
            description: "SS7 Gateway {{ $labels.instance }} lost connection to NATS."

        - alert: SS7GatewaySCTPDown
          expr: ss7_sctp_association_active == 0
          for: 1m
          labels:
            severity: critical
          annotations:
            summary: "SS7 Gateway SCTP association down"
            description: "SS7 Gateway {{ $labels.instance }} SCTP association is down."
```

### 7.11 RBAC Configuration

```yaml
# File: 09-rbac.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: ss7-gateway
  namespace: ss7-ha-gateway
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: ss7-gateway-role
  namespace: ss7-ha-gateway
rules:
  - apiGroups: [""]
    resources: ["configmaps"]
    verbs: ["get", "list", "watch"]
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["get"]
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["coordination.k8s.io"]
    resources: ["leases"]
    verbs: ["get", "list", "watch", "create", "update", "delete"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: ss7-gateway-rolebinding
  namespace: ss7-ha-gateway
subjects:
  - kind: ServiceAccount
    name: ss7-gateway
    namespace: ss7-ha-gateway
roleRef:
  kind: Role
  name: ss7-gateway-role
  apiGroup: rbac.authorization.k8s.io
```

### 7.12 Network Policies

```yaml
# File: 10-network-policies.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: ss7-gateway-network-policy
  namespace: ss7-ha-gateway
spec:
  podSelector:
    matchLabels:
      app: ss7-ha-gateway
  policyTypes:
    - Ingress
    - Egress
  ingress:
    # Allow M3UA from STP
    - from:
        - ipBlock:
            cidr: 10.0.0.0/8  # Adjust to your SS7 network
      ports:
        - protocol: SCTP
          port: 2905
    # Allow metrics scraping from Prometheus
    - from:
        - namespaceSelector:
            matchLabels:
              name: monitoring
      ports:
        - protocol: TCP
          port: 9090
    # Allow health checks from Kubernetes
    - from:
        - ipBlock:
            cidr: 10.0.0.0/8
      ports:
        - protocol: TCP
          port: 8080
  egress:
    # Allow NATS connections
    - to:
        - podSelector:
            matchLabels:
              app: nats
      ports:
        - protocol: TCP
          port: 4222
    # Allow DNS
    - to:
        - namespaceSelector: {}
          podSelector:
            matchLabels:
              k8s-app: kube-dns
      ports:
        - protocol: UDP
          port: 53
```

### 7.13 Deployment Commands

```bash
# Apply all manifests in order
kubectl apply -f 00-namespace.yaml
kubectl apply -f 01-nats.yaml
kubectl apply -f 02-secrets.yaml
kubectl apply -f 03-configmap.yaml
kubectl apply -f 09-rbac.yaml
kubectl apply -f 10-network-policies.yaml

# For Standalone deployment
kubectl apply -f 04-standalone-deployment.yaml

# For HA deployment
kubectl apply -f 05-ha-deployment.yaml
kubectl apply -f 07-autoscaling.yaml
kubectl apply -f 08-monitoring.yaml

# For N+1 deployment
kubectl apply -f 06-n-plus-1-deployment.yaml
kubectl apply -f 08-monitoring.yaml

# Verify deployment
kubectl get pods -n ss7-ha-gateway
kubectl get svc -n ss7-ha-gateway
kubectl get statefulset -n ss7-ha-gateway

# Check logs
kubectl logs -n ss7-ha-gateway ss7-ha-gateway-0 -f

# Check metrics
kubectl port-forward -n ss7-ha-gateway svc/ss7-ha-gateway 9090:9090
curl http://localhost:9090/metrics
```

---

## 8. Operations Guide

### 8.1 Starting and Stopping

#### Kubernetes Operations
```bash
# Scale up
kubectl scale statefulset ss7-ha-gateway -n ss7-ha-gateway --replicas=5

# Scale down
kubectl scale statefulset ss7-ha-gateway -n ss7-ha-gateway --replicas=3

# Rolling restart
kubectl rollout restart statefulset ss7-ha-gateway -n ss7-ha-gateway

# Check rollout status
kubectl rollout status statefulset ss7-ha-gateway -n ss7-ha-gateway

# Rollback
kubectl rollout undo statefulset ss7-ha-gateway -n ss7-ha-gateway
```

#### Docker Compose Operations
```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# Restart specific service
docker-compose restart ss7-gateway-0

# Scale services
docker-compose up -d --scale ss7-gateway=3
```

#### Systemd Operations (Bare Metal)
```bash
# Start service
systemctl start ss7-gateway

# Stop service
systemctl stop ss7-gateway

# Restart service
systemctl restart ss7-gateway

# Check status
systemctl status ss7-gateway

# View logs
journalctl -u ss7-gateway -f
```

### 8.2 Configuration Updates

#### Hot Reload (ConfigMap Update)
```bash
# Update ConfigMap
kubectl create configmap ss7-gateway-config \
  --from-file=ss7-config.properties \
  --from-file=application.properties \
  --dry-run=client -o yaml | kubectl apply -f -

# Trigger reload (pods will detect ConfigMap changes)
kubectl rollout restart statefulset ss7-ha-gateway -n ss7-ha-gateway
```

#### Environment Variable Update
```bash
# Update environment variable
kubectl set env statefulset/ss7-ha-gateway \
  -n ss7-ha-gateway \
  TCAP_MAX_DIALOGS=10000
```

### 8.3 Backup and Recovery

#### Configuration Backup
```bash
# Backup ConfigMaps
kubectl get configmap ss7-gateway-config -n ss7-ha-gateway -o yaml > backup/configmap-$(date +%Y%m%d).yaml

# Backup Secrets
kubectl get secret ss7-gateway-secrets -n ss7-ha-gateway -o yaml > backup/secrets-$(date +%Y%m%d).yaml

# Backup all resources
kubectl get all -n ss7-ha-gateway -o yaml > backup/all-resources-$(date +%Y%m%d).yaml
```

#### NATS JetStream Backup
```bash
# List streams
nats stream ls

# Backup stream
nats stream backup ss7-dialog-state backup/nats/

# Restore stream
nats stream restore ss7-dialog-state backup/nats/
```

### 8.4 Maintenance Mode

```bash
# Drain a specific pod (graceful)
kubectl drain ss7-ha-gateway-0 --ignore-daemonsets --delete-emptydir-data

# Cordon node (prevent new pods)
kubectl cordon <node-name>

# Uncordon node (allow new pods)
kubectl uncordon <node-name>
```

### 8.5 Health Verification

```bash
# Check all pods status
kubectl get pods -n ss7-ha-gateway -o wide

# Check pod events
kubectl describe pod ss7-ha-gateway-0 -n ss7-ha-gateway

# Check readiness
kubectl get pods -n ss7-ha-gateway -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}'

# Check NATS connectivity
kubectl exec -n ss7-ha-gateway ss7-ha-gateway-0 -- nats server ping

# Check SCTP associations
kubectl exec -n ss7-ha-gateway ss7-ha-gateway-0 -- cat /proc/net/sctp/assocs
```

### 8.6 Failover Procedures

#### Manual Failover (N+1 Mode)
```bash
# Promote standby to active
kubectl label pod ss7-gateway-standby-0 -n ss7-ha-gateway role=active --overwrite

# Demote active to standby
kubectl label pod ss7-gateway-active-0 -n ss7-ha-gateway role=standby --overwrite

# Update service selectors if needed
kubectl patch service ss7-gateway-external -n ss7-ha-gateway \
  -p '{"spec":{"selector":{"role":"active"}}}'
```

#### Automatic Failover
Automatic failover is handled by:
1. Kubernetes liveness/readiness probes
2. NATS queue group rebalancing
3. StatefulSet pod replacement

---

## 9. Monitoring and Observability

### 9.1 Key Performance Indicators (KPIs)

| KPI | Description | Target | Critical Threshold |
|-----|-------------|--------|-------------------|
| Dialog Throughput | Dialogs processed per second | > 50,000 | < 10,000 |
| Dialog Latency (P99) | 99th percentile dialog processing time | < 100ms | > 500ms |
| Error Rate | Percentage of failed dialogs | < 0.1% | > 1% |
| SCTP Retransmissions | SCTP packet retransmissions per minute | < 100 | > 1000 |
| NATS Publish Latency | Time to publish to NATS | < 2ms | > 10ms |
| Memory Utilization | JVM heap usage | < 70% | > 90% |
| CPU Utilization | Process CPU usage | < 70% | > 90% |
| Active Dialogs | Current concurrent dialogs | < 4000 | > 4500 |

### 9.2 Prometheus Metrics

#### Application Metrics
```
# Dialog metrics
ss7_dialogs_created_total{instance, service}
ss7_dialogs_closed_total{instance, service, reason}
ss7_dialogs_active{instance, service}
ss7_dialog_duration_seconds_bucket{instance, service, le}

# Operation metrics
ss7_operations_total{instance, operation, status}
ss7_operation_duration_seconds_bucket{instance, operation, le}

# Error metrics
ss7_errors_total{instance, error_type}
ss7_dialog_timeouts_total{instance}
ss7_dialog_aborts_total{instance, reason}

# NATS metrics
ss7_nats_messages_published_total{instance, subject}
ss7_nats_messages_received_total{instance, subject}
ss7_nats_publish_latency_seconds_bucket{instance, le}
ss7_nats_connected{instance}
ss7_nats_reconnects_total{instance}

# SCTP metrics
ss7_sctp_associations_active{instance}
ss7_sctp_packets_sent_total{instance}
ss7_sctp_packets_received_total{instance}
ss7_sctp_retransmissions_total{instance}

# JVM metrics
jvm_memory_used_bytes{instance, area}
jvm_memory_max_bytes{instance, area}
jvm_gc_pause_seconds_count{instance, gc}
jvm_gc_pause_seconds_sum{instance, gc}
jvm_threads_current{instance}
```

### 9.3 Grafana Dashboards

#### Dashboard Layout: SS7 Gateway Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        SS7 HA GATEWAY OVERVIEW                                   │
│  Time Range: [Last 1 hour ▼]  Refresh: [30s ▼]  Instance: [All ▼]              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐              │
│  │   ACTIVE DIALOGS │  │  THROUGHPUT/SEC  │  │   ERROR RATE     │              │
│  │                  │  │                  │  │                  │              │
│  │      2,847       │  │     48,231       │  │      0.02%       │              │
│  │    ───────────   │  │   ───────────    │  │   ───────────    │              │
│  │   [████████░░]   │  │   [██████████]   │  │   [██░░░░░░░░]   │              │
│  │    57% of max    │  │   dialogs/sec    │  │    < threshold   │              │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘              │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │                      DIALOG THROUGHPUT (5m rate)                          │  │
│  │  50k ┤                                                                    │  │
│  │      │    ╭──────╮                      ╭──────────╮                      │  │
│  │  40k ┤───╯      ╰──────────────────────╯          ╰───────────────       │  │
│  │      │                                                                    │  │
│  │  30k ┤                                                                    │  │
│  │      │                                                                    │  │
│  │  20k ┤                                                                    │  │
│  │      ├────────────────────────────────────────────────────────────────   │  │
│  │       10:00    10:15    10:30    10:45    11:00    11:15    11:30        │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
│  ┌─────────────────────────────────┐  ┌─────────────────────────────────────┐  │
│  │     LATENCY DISTRIBUTION        │  │        NATS CONNECTIVITY           │  │
│  │                                 │  │                                     │  │
│  │  P50:   12ms  [████░░░░░░]     │  │  Connected: ✅ 3/3 instances       │  │
│  │  P95:   45ms  [██████░░░░]     │  │  Reconnects: 0 (last 1h)           │  │
│  │  P99:   89ms  [████████░░]     │  │  Pub Latency: 1.2ms avg            │  │
│  │  Max:  234ms  [██████████]     │  │  Messages/sec: 48,231              │  │
│  └─────────────────────────────────┘  └─────────────────────────────────────┘  │
│                                                                                  │
│  ┌─────────────────────────────────┐  ┌─────────────────────────────────────┐  │
│  │       JVM MEMORY USAGE          │  │        CPU UTILIZATION             │  │
│  │                                 │  │                                     │  │
│  │  Heap:  68% [████████████░░░░] │  │  gateway-0: 45% [█████████░░░░░░░] │  │
│  │  Non-H: 23% [████░░░░░░░░░░░░] │  │  gateway-1: 52% [██████████░░░░░░] │  │
│  │  GC/min: 3  [██░░░░░░░░░░░░░░] │  │  gateway-2: 48% [█████████░░░░░░░] │  │
│  └─────────────────────────────────┘  └─────────────────────────────────────┘  │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### Dashboard: SS7 Gateway Overview (JSON Export)
```json
{
  "dashboard": {
    "title": "SS7 HA Gateway Overview",
    "panels": [
      {
        "title": "Dialog Throughput",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(ss7_dialogs_created_total[5m])",
            "legendFormat": "{{instance}}"
          }
        ]
      },
      {
        "title": "Active Dialogs",
        "type": "gauge",
        "targets": [
          {
            "expr": "sum(ss7_dialogs_active)"
          }
        ],
        "thresholds": [
          { "value": 3000, "color": "green" },
          { "value": 4000, "color": "yellow" },
          { "value": 4500, "color": "red" }
        ]
      },
      {
        "title": "Error Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(ss7_errors_total[5m])",
            "legendFormat": "{{error_type}}"
          }
        ]
      },
      {
        "title": "Dialog Latency (P99)",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.99, rate(ss7_dialog_duration_seconds_bucket[5m]))",
            "legendFormat": "P99"
          }
        ]
      },
      {
        "title": "NATS Publish Latency",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.99, rate(ss7_nats_publish_latency_seconds_bucket[5m]))",
            "legendFormat": "P99"
          }
        ]
      },
      {
        "title": "Memory Usage",
        "type": "graph",
        "targets": [
          {
            "expr": "jvm_memory_used_bytes{area='heap'} / jvm_memory_max_bytes{area='heap'} * 100",
            "legendFormat": "{{instance}} Heap %"
          }
        ]
      }
    ]
  }
}
```

### 9.4 Health Check Endpoints

> **⚠️ Implementation Note**: The current implementation uses `/metrics` on port 9090 for Kubernetes probes. A dedicated `/health` endpoint on port 8080 is planned for future releases. The table below shows both current and planned endpoints.

| Endpoint | Port | Status | Purpose | Response |
|----------|------|--------|---------|----------|
| `GET /metrics` | 9090 | **Current** | Prometheus metrics + liveness | Prometheus format |
| `GET /health` | 8080 | *Planned* | Overall health status | `{"status": "UP"}` |
| `GET /health/live` | 8080 | *Planned* | Liveness check | `200 OK` or `503` |
| `GET /health/ready` | 8080 | *Planned* | Readiness check | `200 OK` or `503` |

#### Current Kubernetes Probe Configuration
```yaml
# Current implementation uses /metrics endpoint
livenessProbe:
  httpGet:
    path: /metrics
    port: 9090
  initialDelaySeconds: 60
  periodSeconds: 30

readinessProbe:
  httpGet:
    path: /metrics
    port: 9090
  initialDelaySeconds: 30
  periodSeconds: 10
```

#### Planned Health Check Response Format (Future)
```json
{
  "status": "UP",
  "components": {
    "nats": {
      "status": "UP",
      "details": {
        "connected": true,
        "server": "nats://nats-0:4222"
      }
    },
    "sctp": {
      "status": "UP",
      "details": {
        "associations": 1,
        "state": "ESTABLISHED"
      }
    },
    "dialogs": {
      "status": "UP",
      "details": {
        "active": 1234,
        "max": 5000
      }
    }
  }
}
```

---

## 10. Logging

### 10.1 Log Levels

| Level | Use Case | Example |
|-------|----------|---------|
| ERROR | System failures, unrecoverable errors | SCTP connection failed |
| WARN | Recoverable issues, degraded performance | High dialog count |
| INFO | Normal operations, state changes | Dialog created/closed |
| DEBUG | Detailed operation flow | Message processing steps |
| TRACE | Very detailed, development only | Full message content |

### 10.2 Log Format

#### Standard Log Format
```
2025-12-02 10:30:45.123 [main-worker-1] INFO  c.c.s.c.l.MapSmsServiceListener - Dialog created: dialogId=12345, remoteGT=1234567890
```

#### JSON Log Format (Recommended for Production)
```json
{
  "timestamp": "2025-12-02T10:30:45.123Z",
  "level": "INFO",
  "thread": "main-worker-1",
  "logger": "com.company.ss7ha.core.listeners.MapSmsServiceListener",
  "message": "Dialog created",
  "context": {
    "dialogId": 12345,
    "remoteGT": "1234567890",
    "service": "SMS",
    "nodeId": "ss7-gateway-0"
  }
}
```

### 10.3 Log Categories

| Category | Logger Name | Default Level |
|----------|-------------|---------------|
| Core | `com.company.ss7ha.core` | INFO |
| Listeners | `com.company.ss7ha.core.listeners` | INFO |
| NATS | `com.company.ss7ha.nats` | INFO |
| Store | `com.company.ss7ha.core.store` | INFO |
| JSS7 | `org.restcomm` | WARN |
| NATS Client | `io.nats` | INFO |
| Audit | `audit` | INFO |

### 10.4 Log Aggregation

#### Fluentd Configuration
```yaml
<source>
  @type tail
  path /app/logs/ss7-gateway.log
  pos_file /var/log/fluentd/ss7-gateway.log.pos
  tag ss7.gateway
  <parse>
    @type json
  </parse>
</source>

<filter ss7.**>
  @type record_transformer
  <record>
    cluster ${CLUSTER_NAME}
    namespace ${POD_NAMESPACE}
    pod ${POD_NAME}
  </record>
</filter>

<match ss7.**>
  @type elasticsearch
  host elasticsearch.logging.svc.cluster.local
  port 9200
  index_name ss7-gateway
  type_name _doc
</match>
```

### 10.5 Log Rotation

```xml
<!-- Log4j2 Rolling Configuration -->
<RollingFile name="RollingFile"
             fileName="${LOG_DIR}/ss7-gateway.log"
             filePattern="${LOG_DIR}/ss7-gateway-%d{yyyy-MM-dd}-%i.log.gz">
    <PatternLayout pattern="${LOG_PATTERN}"/>
    <Policies>
        <SizeBasedTriggeringPolicy size="100MB"/>
        <TimeBasedTriggeringPolicy interval="1"/>
    </Policies>
    <DefaultRolloverStrategy max="30">
        <Delete basePath="${LOG_DIR}" maxDepth="1">
            <IfFileName glob="ss7-gateway-*.log.gz"/>
            <IfLastModified age="30d"/>
        </Delete>
    </DefaultRolloverStrategy>
</RollingFile>
```

---

## 11. Alarms and Alerting

### 11.1 Alarm Definitions

#### Critical Alarms

| Alarm ID | Name | Condition | Action |
|----------|------|-----------|--------|
| SS7-CRIT-001 | Gateway Down | Pod not responding for > 1 min | Immediate investigation |
| SS7-CRIT-002 | SCTP Association Lost | No active SCTP association | Check network connectivity |
| SS7-CRIT-003 | NATS Disconnected | NATS connection lost > 30s | Check NATS cluster health |
| SS7-CRIT-004 | Dialog Overflow | Active dialogs > 95% max | Scale up or investigate |
| SS7-CRIT-005 | All Instances Down | No healthy instances | Emergency response |

#### Warning Alarms

| Alarm ID | Name | Condition | Action |
|----------|------|-----------|--------|
| SS7-WARN-001 | High Error Rate | Error rate > 1% for 5 min | Investigate errors |
| SS7-WARN-002 | High Latency | P99 latency > 500ms | Performance analysis |
| SS7-WARN-003 | Memory Pressure | Heap > 80% for 10 min | Consider scaling |
| SS7-WARN-004 | High Dialog Count | Dialogs > 80% max | Monitor trend |
| SS7-WARN-005 | SCTP Retransmissions | Retrans > 1000/min | Network quality check |

#### Informational Alarms

| Alarm ID | Name | Condition | Action |
|----------|------|-----------|--------|
| SS7-INFO-001 | Instance Started | New instance joined | Log for audit |
| SS7-INFO-002 | Instance Stopped | Instance gracefully stopped | Log for audit |
| SS7-INFO-003 | Config Reloaded | Configuration updated | Verify changes |
| SS7-INFO-004 | Failover Occurred | Automatic failover triggered | Review cause |

### 11.2 AlertManager Configuration

```yaml
# alertmanager.yml
global:
  smtp_smarthost: 'smtp.company.com:587'
  smtp_from: 'alerts@company.com'
  smtp_auth_username: 'alerts@company.com'
  smtp_auth_password: 'secret'

route:
  group_by: ['alertname', 'severity']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: 'critical-receiver'
      repeat_interval: 15m
    - match:
        severity: warning
      receiver: 'warning-receiver'
      repeat_interval: 1h

receivers:
  - name: 'default'
    email_configs:
      - to: 'noc@company.com'

  - name: 'critical-receiver'
    email_configs:
      - to: 'noc@company.com'
        send_resolved: true
    pagerduty_configs:
      - service_key: '<pagerduty-service-key>'
    slack_configs:
      - api_url: '<slack-webhook-url>'
        channel: '#ss7-alerts'

  - name: 'warning-receiver'
    email_configs:
      - to: 'noc@company.com'
        send_resolved: true
    slack_configs:
      - api_url: '<slack-webhook-url>'
        channel: '#ss7-alerts'

inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'instance']
```

### 11.3 Prometheus Alert Rules

```yaml
groups:
  - name: ss7-gateway-critical
    rules:
      - alert: SS7GatewayDown
        expr: up{job="ss7-ha-gateway"} == 0
        for: 1m
        labels:
          severity: critical
          alarm_id: SS7-CRIT-001
        annotations:
          summary: "SS7 Gateway instance {{ $labels.instance }} is down"
          description: "The SS7 Gateway instance has been down for more than 1 minute."
          runbook_url: "https://wiki.company.com/ss7-gateway/runbooks/instance-down"

      - alert: SS7SCTPAssociationDown
        expr: ss7_sctp_associations_active == 0
        for: 1m
        labels:
          severity: critical
          alarm_id: SS7-CRIT-002
        annotations:
          summary: "SS7 Gateway SCTP association is down"
          description: "No active SCTP associations on {{ $labels.instance }}"
          runbook_url: "https://wiki.company.com/ss7-gateway/runbooks/sctp-down"

      - alert: SS7NATSDisconnected
        expr: ss7_nats_connected == 0
        for: 30s
        labels:
          severity: critical
          alarm_id: SS7-CRIT-003
        annotations:
          summary: "SS7 Gateway disconnected from NATS"
          description: "Instance {{ $labels.instance }} lost NATS connection"
          runbook_url: "https://wiki.company.com/ss7-gateway/runbooks/nats-disconnect"

      - alert: SS7DialogOverflow
        expr: ss7_dialogs_active / ss7_dialogs_max > 0.95
        for: 2m
        labels:
          severity: critical
          alarm_id: SS7-CRIT-004
        annotations:
          summary: "SS7 Gateway dialog capacity near limit"
          description: "Instance {{ $labels.instance }} at {{ $value | humanizePercentage }} dialog capacity"

  - name: ss7-gateway-warning
    rules:
      - alert: SS7HighErrorRate
        expr: rate(ss7_errors_total[5m]) / rate(ss7_dialogs_created_total[5m]) > 0.01
        for: 5m
        labels:
          severity: warning
          alarm_id: SS7-WARN-001
        annotations:
          summary: "High error rate on SS7 Gateway"
          description: "Error rate is {{ $value | humanizePercentage }} on {{ $labels.instance }}"

      - alert: SS7HighLatency
        expr: histogram_quantile(0.99, rate(ss7_dialog_duration_seconds_bucket[5m])) > 0.5
        for: 5m
        labels:
          severity: warning
          alarm_id: SS7-WARN-002
        annotations:
          summary: "High dialog latency on SS7 Gateway"
          description: "P99 latency is {{ $value | humanizeDuration }} on {{ $labels.instance }}"

      - alert: SS7HighMemoryUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.8
        for: 10m
        labels:
          severity: warning
          alarm_id: SS7-WARN-003
        annotations:
          summary: "High memory usage on SS7 Gateway"
          description: "Heap usage is {{ $value | humanizePercentage }} on {{ $labels.instance }}"
```

### 11.4 Escalation Matrix

| Severity | Initial Response | Escalation (15 min) | Escalation (1 hr) |
|----------|-----------------|---------------------|-------------------|
| Critical | NOC + On-call | Team Lead | Engineering Manager |
| Warning | NOC | On-call (if persists 30 min) | Team Lead |
| Info | NOC (log only) | - | - |

---

## 12. Troubleshooting

### 12.1 Common Issues and Solutions

#### Issue: Gateway Not Starting

**Symptoms:**
- Pod in CrashLoopBackOff
- Application fails to initialize

**Diagnosis:**
```bash
# Check pod events
kubectl describe pod ss7-ha-gateway-0 -n ss7-ha-gateway

# Check container logs
kubectl logs ss7-ha-gateway-0 -n ss7-ha-gateway --previous

# Check resource limits
kubectl top pod ss7-ha-gateway-0 -n ss7-ha-gateway
```

**Common Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| SCTP not enabled | Ensure `lksctp-tools` installed, check kernel module |
| NATS unreachable | Verify NATS URL, check network policies |
| Invalid configuration | Validate `ss7-config.properties` syntax |
| Insufficient memory | Increase resource limits |
| Port already in use | Check for conflicting services on port 2905 |

#### Issue: SCTP Association Not Establishing

**Symptoms:**
- `ss7_sctp_associations_active = 0`
- Logs show connection timeouts

**Diagnosis:**
```bash
# Check SCTP associations
kubectl exec -n ss7-ha-gateway ss7-ha-gateway-0 -- cat /proc/net/sctp/assocs

# Check SCTP endpoints
kubectl exec -n ss7-ha-gateway ss7-ha-gateway-0 -- cat /proc/net/sctp/eps

# Test connectivity to remote peer
kubectl exec -n ss7-ha-gateway ss7-ha-gateway-0 -- nc -z <remote-host> 2905
```

**Common Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| Firewall blocking SCTP | Add firewall rules for SCTP/132 |
| Wrong remote peer address | Verify `m3ua.sctp.remote.host` |
| Routing context mismatch | Align `m3ua.routing.context` with peer |
| ASP/AS configuration | Verify ASP name and AS mode match peer |

#### Issue: Dialogs Timing Out

**Symptoms:**
- High `ss7_dialog_timeouts_total`
- Operations not completing

**Diagnosis:**
```bash
# Check dialog statistics
curl http://localhost:9090/metrics | grep ss7_dialog

# Check active dialogs
kubectl exec -n ss7-ha-gateway ss7-ha-gateway-0 -- \
  curl -s http://localhost:8080/debug/dialogs

# Check TCAP layer
kubectl logs ss7-ha-gateway-0 -n ss7-ha-gateway | grep -i timeout
```

**Common Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| Peer not responding | Check peer health and connectivity |
| Timeout too short | Increase `tcap.dialog.idle.timeout` |
| Network congestion | Check network latency, packet loss |
| Resource exhaustion | Scale up or optimize processing |

#### Issue: NATS Publishing Failures

**Symptoms:**
- Messages not reaching downstream
- High publish error rate

**Diagnosis:**
```bash
# Check NATS connection status
kubectl exec -n ss7-ha-gateway ss7-ha-gateway-0 -- \
  curl -s http://localhost:8080/health | jq .components.nats

# Check NATS server status
kubectl exec -n ss7-ha-gateway nats-0 -- nats server report

# Check subject statistics
kubectl exec -n ss7-ha-gateway nats-0 -- nats sub --queue ss7-test map.mo.sms.response
```

**Common Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| NATS cluster down | Check NATS pods, restore cluster |
| Network partition | Check inter-pod connectivity |
| JetStream not enabled | Enable JetStream in NATS config |
| Authentication failure | Verify credentials in secret |

#### Issue: High Memory Usage

**Symptoms:**
- OOM kills
- GC pauses increasing

**Diagnosis:**
```bash
# Check JVM memory
kubectl exec -n ss7-ha-gateway ss7-ha-gateway-0 -- \
  jcmd 1 GC.heap_info

# Check GC statistics
kubectl exec -n ss7-ha-gateway ss7-ha-gateway-0 -- \
  jstat -gcutil 1 1000 10

# Generate heap dump
kubectl exec -n ss7-ha-gateway ss7-ha-gateway-0 -- \
  jcmd 1 GC.heap_dump /tmp/heapdump.hprof
```

**Common Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| Dialog leak | Check dialog cleanup, verify TTL |
| Large message backlog | Investigate slow consumers |
| Insufficient heap | Increase `-Xmx` value |
| Memory leak | Analyze heap dump, file bug report |

### 12.2 Diagnostic Commands

#### SCTP Diagnostics
```bash
# List all SCTP associations
cat /proc/net/sctp/assocs

# List SCTP endpoints
cat /proc/net/sctp/eps

# SCTP statistics
cat /proc/net/sctp/snmp

# Detailed SCTP info
ss -S
```

#### NATS Diagnostics
```bash
# Server info
nats server info

# Connection status
nats server report connections

# JetStream status
nats stream ls
nats stream info ss7-dialog-state

# Consumer status
nats consumer ls ss7-dialog-state

# Monitor subjects
nats sub "map.>"
```

#### JVM Diagnostics
```bash
# Thread dump
jcmd <pid> Thread.print

# Heap histogram
jcmd <pid> GC.class_histogram

# VM flags
jcmd <pid> VM.flags

# JFR recording
jcmd <pid> JFR.start duration=60s filename=/tmp/recording.jfr
```

### 12.3 Log Analysis Queries

#### Elasticsearch/Kibana Queries
```
# Find all errors in last hour
level:ERROR AND @timestamp:[now-1h TO now]

# Find dialog timeout errors
message:"dialog timeout" AND dialogId:*

# Find SCTP connection issues
logger:*sctp* AND (level:ERROR OR level:WARN)

# Find specific dialog
dialogId:12345

# Find high latency operations
operation_duration_ms:>500
```

### 12.4 Recovery Procedures

#### Procedure: Complete Cluster Recovery
```bash
# 1. Stop all gateways
kubectl scale statefulset ss7-ha-gateway -n ss7-ha-gateway --replicas=0

# 2. Wait for termination
kubectl wait --for=delete pod -l app=ss7-ha-gateway -n ss7-ha-gateway --timeout=60s

# 3. Verify NATS health
kubectl exec -n ss7-ha-gateway nats-0 -- nats server check

# 4. Clear stale state if needed
kubectl exec -n ss7-ha-gateway nats-0 -- nats kv purge ss7-dialog-state

# 5. Restart gateways
kubectl scale statefulset ss7-ha-gateway -n ss7-ha-gateway --replicas=3

# 6. Verify health
kubectl wait --for=condition=Ready pod -l app=ss7-ha-gateway -n ss7-ha-gateway --timeout=120s

# 7. Verify connectivity
kubectl exec -n ss7-ha-gateway ss7-ha-gateway-0 -- curl -s http://localhost:8080/health
```

#### Procedure: Single Instance Recovery
```bash
# 1. Delete unhealthy pod (StatefulSet will recreate)
kubectl delete pod ss7-ha-gateway-0 -n ss7-ha-gateway

# 2. Wait for recreation
kubectl wait --for=condition=Ready pod ss7-ha-gateway-0 -n ss7-ha-gateway --timeout=120s

# 3. Verify health
kubectl logs ss7-ha-gateway-0 -n ss7-ha-gateway --tail=50
```

---

## 13. Security

### 13.1 Authentication and Authorization

#### NATS Authentication
```yaml
# nats.conf with authentication
authorization {
  users = [
    {
      user: ss7gateway
      password: $NATS_PASSWORD
      permissions: {
        publish: ["map.>", "cap.>"]
        subscribe: ["map.>", "cap.>", "_INBOX.>"]
      }
    }
  ]
}
```

#### TLS Configuration
```properties
# Enable TLS for NATS
nats.tls.enabled=true
nats.tls.cert.path=/app/certs/tls.crt
nats.tls.key.path=/app/certs/tls.key
nats.tls.ca.path=/app/certs/ca.crt
```

### 13.2 Network Security

#### Kubernetes Network Policy
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: ss7-gateway-strict
  namespace: ss7-ha-gateway
spec:
  podSelector:
    matchLabels:
      app: ss7-ha-gateway
  policyTypes:
    - Ingress
    - Egress
  ingress:
    # Only allow M3UA from specific STP IP
    - from:
        - ipBlock:
            cidr: 10.0.1.0/24  # STP network
      ports:
        - protocol: SCTP
          port: 2905
    # Only allow metrics from Prometheus
    - from:
        - namespaceSelector:
            matchLabels:
              name: monitoring
        - podSelector:
            matchLabels:
              app: prometheus
      ports:
        - protocol: TCP
          port: 9090
  egress:
    # Only allow NATS within namespace
    - to:
        - podSelector:
            matchLabels:
              app: nats
      ports:
        - protocol: TCP
          port: 4222
    # Allow DNS
    - to:
        - namespaceSelector: {}
      ports:
        - protocol: UDP
          port: 53
```

### 13.3 Container Security

#### Security Context
```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  runAsGroup: 1000
  fsGroup: 1000
  readOnlyRootFilesystem: true
  allowPrivilegeEscalation: false
  capabilities:
    drop:
      - ALL
    add:
      - NET_ADMIN  # Required for SCTP
```

### 13.4 Secrets Management

#### Sealed Secrets (Bitnami)
```bash
# Encrypt secret
kubeseal --format yaml < secret.yaml > sealed-secret.yaml

# Apply sealed secret
kubectl apply -f sealed-secret.yaml
```

#### External Secrets Operator
```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: ss7-gateway-secrets
  namespace: ss7-ha-gateway
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-backend
    kind: SecretStore
  target:
    name: ss7-gateway-secrets
  data:
    - secretKey: nats-password
      remoteRef:
        key: ss7/nats
        property: password
```

### 13.5 Audit Logging

```java
// Audit log format
{
  "timestamp": "2025-12-02T10:30:45.123Z",
  "eventType": "DIALOG_CREATED",
  "nodeId": "ss7-gateway-0",
  "dialogId": 12345,
  "remoteAddress": "1234567890",
  "localAddress": "0987654321",
  "service": "MAP_SMS",
  "user": "system",
  "clientIP": "10.0.1.100"
}
```

---

## 14. Reference

### 14.1 Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `NODE_ID` | Unique node identifier | `node-1` | Yes |
| `NATS_URL` | NATS server URL(s) | `nats://localhost:4222` | Yes |
| `NATS_QUEUE_GROUP` | NATS queue group | `ss7-gateway-group-v2` | No |
| `M3UA_LOCAL_HOST` | M3UA bind address | `0.0.0.0` | No |
| `M3UA_LOCAL_PORT` | M3UA bind port | `2905` | No |
| `SCCP_LOCAL_PC` | Local point code | `1` | Yes |
| `SCCP_LOCAL_SSN` | Local subsystem number | `8` | Yes |
| `JAVA_OPTS` | JVM options | `-Xms1g -Xmx2g` | No |
| `LOG_LEVEL` | Root log level | `INFO` | No |
| `HA_MODE` | HA deployment mode | `ACTIVE_ACTIVE` | No |
| `DATACENTER` | Datacenter identifier | `dc1` | No |

### 14.2 Secrets Reference

| Secret Key | Description | Format |
|------------|-------------|--------|
| `nats-user` | NATS username | Plain text |
| `nats-password` | NATS password | Plain text |
| `tls.crt` | TLS certificate | PEM encoded |
| `tls.key` | TLS private key | PEM encoded |
| `ca.crt` | CA certificate | PEM encoded |

### 14.3 Volume Mounts

| Mount Path | Description | Type | Mode |
|------------|-------------|------|------|
| `/app/config` | Configuration files | ConfigMap | Read-only |
| `/app/logs` | Application logs | EmptyDir/PVC | Read-write |
| `/app/certs` | TLS certificates | Secret | Read-only |
| `/tmp` | Temporary files | EmptyDir | Read-write |

### 14.4 Ports Reference

| Port | Protocol | Service | Description |
|------|----------|---------|-------------|
| 2905 | SCTP | M3UA | SS7 signaling |
| 4222 | TCP | NATS Client | NATS connections |
| 6222 | TCP | NATS Cluster | NATS routing |
| 8080 | TCP | Health | Health checks |
| 8222 | TCP | NATS Monitor | NATS monitoring |
| 9090 | TCP | Metrics | Prometheus metrics |

### 14.5 NATS Subjects Reference

| Subject | Direction | Description |
|---------|-----------|-------------|
| `map.mo.sms.response` | Publish | MO-SMS from network |
| `map.mt.sms.request` | Subscribe | MT-SMS to network |
| `map.mt.sms.response` | Publish | MT-SMS delivery status |
| `map.sri.request` | Subscribe | SRI-SM requests |
| `map.sri.response` | Publish | SRI-SM responses |
| `map.alert.response` | Publish | Alert service centre |
| `map.delivery.report` | Publish | Delivery reports |
| `cap.initial.dp` | Publish | CAP Initial DP |
| `cap.continue` | Subscribe | CAP Continue |
| `cap.release` | Subscribe | CAP Release |

### 14.6 SS7 Point Code Formats

| Format | Example | Description |
|--------|---------|-------------|
| Integer | `123` | Single integer |
| 3-8-3 | `1-2-3` | ITU format |
| 8-8-8 | `1-2-3` | ANSI format |
| 3-8-3 decimal | `1.2.3` | Dot notation |

### 14.7 Subsystem Numbers (SSN)

| SSN | Service |
|-----|---------|
| 0 | Management |
| 1 | Reserved |
| 6 | HLR |
| 7 | VLR |
| 8 | MSC |
| 9 | EIR |
| 10 | AUC |
| 146 | CAMEL (CAP) |
| 147 | USSD Gateway |
| 149 | LCS |

---

## 15. Appendix

### 15.1 Glossary

| Term | Definition |
|------|------------|
| ASP | Application Server Process |
| AS | Application Server |
| CAP | CAMEL Application Part |
| GT | Global Title |
| HLR | Home Location Register |
| M3UA | MTP3 User Adaptation Layer |
| MAP | Mobile Application Part |
| MO-SMS | Mobile Originated SMS |
| MSC | Mobile Switching Centre |
| MT-SMS | Mobile Terminated SMS |
| NATS | Neural Autonomic Transport System |
| PC | Point Code |
| SCCP | Signalling Connection Control Part |
| SCTP | Stream Control Transmission Protocol |
| SRI-SM | Send Routing Info for Short Message |
| SSN | Subsystem Number |
| STP | Signal Transfer Point |
| TCAP | Transaction Capabilities Application Part |
| VLR | Visitor Location Register |

### 15.2 Error Codes

| Code | Meaning | Action |
|------|---------|--------|
| `TCAP-001` | Dialog timeout | Check peer, increase timeout |
| `TCAP-002` | Dialog abort | Check peer, review operation |
| `MAP-001` | System failure | Check HLR/VLR connectivity |
| `MAP-002` | Data missing | Validate request parameters |
| `MAP-003` | Unexpected data | Version mismatch |
| `SCTP-001` | Association lost | Check network, reinitialize |
| `SCTP-002` | Init timeout | Check peer address, firewall |
| `NATS-001` | Connection lost | Check NATS cluster health |
| `NATS-002` | Publish timeout | Check network, NATS capacity |

### 15.3 Performance Tuning Guide

#### JVM Tuning
```bash
# Production JVM settings
JAVA_OPTS="-Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ParallelRefProcEnabled \
  -XX:+UseStringDeduplication \
  -XX:+AlwaysPreTouch \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/app/logs/heapdump.hprof \
  -Djava.net.preferIPv4Stack=true"
```

#### Thread Pool Tuning
```properties
# For high throughput
thread.pool.core.size=100
thread.pool.max.size=400
thread.pool.queue.capacity=2000

# For low latency
thread.pool.core.size=50
thread.pool.max.size=100
thread.pool.queue.capacity=500
```

### 15.4 Migration Checklist

#### From Kafka to NATS
- [ ] Update dependencies in `pom.xml`
- [ ] Replace Kafka producer with NATS publisher
- [ ] Replace Kafka consumer with NATS subscriber
- [ ] Update subject naming (topics → subjects)
- [ ] Configure queue groups for load balancing
- [ ] Enable JetStream for persistence (optional)
- [ ] Update monitoring dashboards
- [ ] Update alert rules
- [ ] Test failover scenarios
- [ ] Update documentation

### 15.5 Compliance Notes

#### AGPL-3.0 Compliance
- The Corsac JSS7 library is AGPL-3.0 licensed
- Dialog state serialization uses primitives only (no JSS7 objects)
- NATS messages contain JSON data, not JSS7 objects
- Downstream consumers are license-independent

#### Data Privacy
- Personal data (MSISDN, IMSI) may be processed
- Ensure appropriate data handling policies
- Implement audit logging for compliance
- Consider data masking for logs

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2024-01-15 | Engineering | Initial release |
| 1.1.0 | 2024-06-01 | Engineering | Added N+1 deployment |
| 2.0.0 | 2025-12-02 | Engineering | NATS migration, comprehensive update |

---

**Document Prepared By:**
- Product Owner: Requirements and scope
- Technical Writer: Documentation
- Developer: Technical details and validation
- Editor: Review and formatting

**Approved By:** [Approval signature placeholder]

**Next Review Date:** [6 months from publication]
