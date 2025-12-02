# SS7 HA Gateway - Project Brief (v2.0)

## Project Description
SS7 HA Gateway is an open-source, carrier-grade protocol handling layer designed for SS7/MAP/CAP networks. It serves as a bridge between legacy telecom infrastructure (HLR, MSC, VLR) and modern application ecosystems.

The gateway provides high availability through distributed state management using **NATS JetStream Key-Value Store** and implements a low-latency event-driven architecture by publishing clean JSON events to **NATS Core**. This allows modern applications to interact with SS7 networks without needing complex SS7 stack knowledge or heavy middleware like Kafka and Redis.

## Who is this library for?
This library is designed for a wide range of stakeholders in the telecommunications industry:
- **Telecom Operators & Carriers:** For modernizing legacy infrastructure and exposing services via modern APIs.
- **Mobile Virtual Network Operators (MVNOs):** To implement core network services efficiently.
- **Value-Added Service (VAS) Providers:** Companies building SMS Centers (SMSC), USSD gateways, or Location Based Services (LBS).
- **FinTech & Authentication Providers:** For delivering OTPs and 2FA services via reliable SMS channels.
- **Software Developers:** Who need to integrate with SS7 networks using familiar tools like NATS and JSON, avoiding the steep learning curve of SS7 protocols.

## Key Advantages
The SS7 HA Gateway offers several significant benefits over legacy architectures:

### 1. High Availability & Reliability
Utilizes **NATS JetStream Key-Value Store** for distributed dialog state management. This ensures continuous operation even if a gateway node fails, as any other instance can retrieve the dialog state and continue the session. It guarantees seamless failover and data consistency.

### 2. Simplified Architecture & Operations
Replaces the complex combination of Apache Kafka (messaging) and Redis (state) with a single, lightweight **NATS Server**. This drastically reduces operational complexity, deployment footprint, and maintenance overhead while improving performance.

### 3. Scalability & Load Balancing
Built for horizontal scalability using **NATS Queue Groups**. Incoming SS7 traffic is automatically load-balanced across all available gateway instances. You can simply add more gateway nodes to handle increased load, supporting 50,000+ dialogs per second per instance.

### 4. Ultra-Low Latency Event-Driven Design
Decouples the complex SS7 layer from business logic using NATS Pub/Sub. Applications consume standard JSON events with sub-millisecond latency, enabling real-time integration in any programming language (Go, Java, Python, Node.js, etc.).

### 5. Cost-Effective & Open Source
Reduces reliance on expensive proprietary hardware and complex software licenses. It runs efficiently on standard commodity hardware or containerized environments (Docker/Kubernetes), with NATS providing a highly efficient resource profile.
