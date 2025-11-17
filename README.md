# SS7 HA Gateway

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.oracle.com/java/)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

High Availability SS7 Protocol Gateway with distributed state management and event-driven architecture.

**Developed by [3eAI Labs](https://github.com/3eAI-labs)**

---

## Overview

SS7 HA Gateway is an open-source, carrier-grade protocol handling layer for SS7/MAP/CAP networks. It provides:

- **High Availability**: Redis-based distributed state management
- **Horizontal Scalability**: Multiple gateway instances with automatic failover
- **Event-Driven Architecture**: Clean JSON events published to Kafka
- **Zero Message Loss**: Sub-15-second failover with full dialog recovery
- **Production Ready**: Battle-tested components with comprehensive monitoring

### Architecture

```
┌──────────────────────────────────────────────────────┐
│            SS7 Network (HLR, MSC, VLR)               │
└────────────────────┬─────────────────────────────────┘
                     │
         ┌───────────▼──────────┐
         │  SS7 HA Gateway      │
         │  (This Project)      │
         │                      │
         │  • M3UA, SCCP, TCAP  │
         │  • MAP, CAP          │
         │  • Dialog Management │
         └──────┬───────────────┘
                │
    ┌───────────┼───────────┐
    │           │           │
┌───▼───┐   ┌──▼───┐   ┌──▼────┐
│ Redis │   │ Kafka│   │Gateway│
│Cluster│   │      │   │ Nodes │
└───────┘   └──┬───┘   └───────┘
               │
               │ JSON Events
               │
        ┌──────▼────────┐
        │   Your Apps   │
        │  • SMSC       │
        │  • IN/CAMEL   │
        │  • USSD       │
        └───────────────┘
```

---

## Features

### Protocol Support
- **M3UA** (RFC 4666) - SCTP-based SS7 transport
- **SCCP** - Signaling Connection Control Part
- **TCAP** - Transaction Capabilities Application Part
- **MAP** - Mobile Application Part (SMS, USSD, subscriber data)
- **CAP** - CAMEL Application Part (IN services)

### High Availability
- **Redis Cluster Integration**: Shared dialog state across instances
- **Automatic Failover**: Kafka consumer group rebalancing
- **Dialog Recovery**: Full state restoration on instance failure
- **Zero Downtime Updates**: Rolling deployments supported

### Event-Driven Integration
- **Kafka Producer**: Publishes MAP/CAP events as JSON
- **Clean API**: No SS7 stack dependencies for consumers
- **Standard Format**: JSON schema with versioning support
- **Language Agnostic**: Any language can consume events

### Monitoring & Operations
- **Prometheus Metrics**: Dialog counts, throughput, latency
- **Health Checks**: Kubernetes-ready liveness/readiness probes
- **Structured Logging**: JSON logs with correlation IDs
- **Grafana Dashboards**: Pre-built visualization templates

---

## Quick Start

### Prerequisites

- Java 8 or higher
- Maven 3.6+
- Redis Cluster (6 nodes recommended)
- Kafka Cluster (3 brokers minimum)
- Docker (optional, for containerized deployment)

### Build from Source

```bash
git clone https://github.com/3eAI-labs/ss7-ha-gateway.git
cd ss7-ha-gateway
mvn clean install
```

### Configuration

Create `config/ss7-ha-gateway.properties`:

```properties
# Redis Configuration
redis.cluster.nodes=redis1:6379,redis2:6379,redis3:6379
redis.dialog.ttl=3600
redis.persist.dialogs=true

# Kafka Configuration
kafka.bootstrap.servers=kafka1:9092,kafka2:9092,kafka3:9092
kafka.topic.prefix=ss7.
kafka.publish.enabled=true

# M3UA Configuration
m3ua.sctp.server.host=0.0.0.0
m3ua.sctp.server.port=2905

# SCCP Configuration
sccp.local.pc=1
sccp.local.ssn=8
sccp.local.gt=123456789

# TCAP Configuration
tcap.dialog.idle.timeout=60000
tcap.max.dialogs=5000
```

### Run

```bash
java -jar ss7-core/target/ss7-core-*.jar
```

### Docker Deployment

```bash
docker-compose up -d
```

See [docs/PHASE_4_TESTING_DEPLOYMENT.md](docs/PHASE_4_TESTING_DEPLOYMENT.md) for detailed deployment guides.

---

## Documentation

- **[Architecture Guide](docs/SYSTEM_ARCHITECTURE.md)** - System design and components
- **[Redis HA Setup](docs/REDIS_HA_ARCHITECTURE.md)** - High availability configuration
- **[Phase 3: SS7 Integration](docs/PHASE_3_SS7_INTEGRATION.md)** - Implementation details
- **[Phase 4: Testing & Deployment](docs/PHASE_4_TESTING_DEPLOYMENT.md)** - Testing strategy

---

## Performance

**Tested Throughput:**
- 50,000+ dialogs/second per instance
- Sub-2ms Redis latency
- Sub-15s failover recovery time
- <5% overhead vs. standalone

**Scalability:**
- Horizontal: Add more gateway instances
- Vertical: Scale Redis and Kafka clusters
- Tested with 150,000+ concurrent dialogs

---

## Example: Consuming MO-SMS Events

Your application receives JSON events from Kafka:

```json
{
  "messageType": "MO_FORWARD_SM",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1731650400000,
  "sender": {
    "msisdn": "+1234567890",
    "imsi": "310260000000000"
  },
  "recipient": {
    "address": "+0987654321"
  },
  "message": {
    "content": "SGVsbG8gV29ybGQ=",
    "encoding": "GSM7"
  }
}
```

Process with any language:

```java
// Java example
@KafkaListener(topics = "ss7.sms.mo.incoming")
public void handleMoSms(String json) {
    MoSmsMessage msg = objectMapper.readValue(json, MoSmsMessage.class);
    // Your business logic here
}
```

```python
# Python example
consumer = KafkaConsumer('ss7.sms.mo.incoming')
for message in consumer:
    msg = json.loads(message.value)
    # Your business logic here
```

---

## Technology Stack

- **[Corsac JSS7](https://github.com/mobius-software-ltd/corsac-jss7)** - SS7 protocol implementation (AGPL-3.0)
- **[Apache Kafka](https://kafka.apache.org/)** - Event streaming platform
- **[Redis](https://redis.io/)** - Distributed state storage
- **[Jackson](https://github.com/FasterXML/jackson)** - JSON processing
- **[Prometheus](https://prometheus.io/)** - Metrics and monitoring

---

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

**Ways to contribute:**
- 🐛 Report bugs via [GitHub Issues](https://github.com/3eAI-labs/ss7-ha-gateway/issues)
- 💡 Suggest features or improvements
- 📝 Improve documentation
- 🔧 Submit pull requests
- ⭐ Star the repository

---

## Community & Support

- **GitHub Issues**: [Report bugs or request features](https://github.com/3eAI-labs/ss7-ha-gateway/issues)
- **Discussions**: [Ask questions and share ideas](https://github.com/3eAI-labs/ss7-ha-gateway/discussions)

---

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

See [LICENSE](LICENSE) for the full license text.

**What this means:**
- ✅ Free to use, modify, and distribute
- ✅ Commercial use allowed
- ⚠️ Must disclose source code when used as a network service
- ⚠️ Derivative works must also be AGPL-3.0

---

## Acknowledgments

This project builds upon:
- **Corsac JSS7** by [Mobius Software](https://github.com/mobius-software-ltd/corsac-jss7)
- Open source contributions from the telecom community

---

## Roadmap

**Current Version: 1.0.0**

**Upcoming Features:**
- [ ] Diameter protocol support
- [ ] gRPC API alongside Kafka
- [ ] Multi-datacenter replication
- [ ] Enhanced monitoring dashboards
- [ ] Performance optimization for 100K+ TPS

---

## About 3eAI Labs

**3eAI Labs** develops open-source and commercial solutions for telecommunications and enterprise systems.

- **GitHub**: https://github.com/3eAI-labs

---

**Made with ❤️ by the 3eAI Labs Team**
