# Kafka to NATS Migration Guide

## Overview

This guide provides step-by-step instructions for migrating both `cloud-native-smsc` and `ss7-ha-gateway` from Kafka to NATS.io.

## Why NATS for SMSC?

| Aspect | Kafka | NATS |
|--------|-------|------|
| **Latency** | 5-15ms typical | Sub-millisecond |
| **Ops Complexity** | High (ZooKeeper, partitions, consumer groups) | Low (single binary, no dependencies) |
| **Code Complexity** | High (offsets, rebalancing, serialization) | Low (simple pub/sub) |
| **Message Patterns** | Persistent log | Pub/Sub + optional persistence (JetStream) |
| **Best For** | Analytics, data pipelines, event sourcing | Real-time messaging, RPC, command/control |

For SMS delivery (transient, low-latency required), NATS is the better fit.

---

## Part 1: cloud-native-smsc Migration

### 1.1 Dependencies (pom.xml)

**Parent POM (`pom.xml`)**

```xml
<!-- BEFORE -->
<kafka.version>3.5.1</kafka.version>

<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>${kafka.version}</version>
</dependency>

<!-- AFTER -->
<nats.version>2.17.2</nats.version>

<dependency>
    <groupId>io.nats</groupId>
    <artifactId>jnats</artifactId>
    <version>${nats.version}</version>
</dependency>
```

**Module POM (`smsc-core/pom.xml`)**

```xml
<!-- BEFORE -->
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
</dependency>

<!-- AFTER -->
<dependency>
    <groupId>io.nats</groupId>
    <artifactId>jnats</artifactId>
</dependency>
```

### 1.2 Configuration Class

**File**: `smsc-core/src/main/java/com/yourcompany/smsc/config/NatsConfiguration.java` (Already created)

**Property Mapping**:
```properties
# Kafka properties (REMOVE)
kafka.bootstrap.servers=localhost:9092
kafka.group.id=smsc-gateway-group
kafka.enable.auto.commit=false
kafka.auto.offset.reset=earliest

# NATS properties (ADD)
nats.server.url=nats://localhost:4222
nats.max.reconnects=-1
nats.reconnect.wait.ms=1000
nats.connection.timeout.ms=2000
```

### 1.3 Subject Naming Convention

NATS uses hierarchical subjects instead of Kafka topics:

```
Kafka Topics              →  NATS Subjects
─────────────────────────────────────────────
map.mt.sms.request        →  map.mt.sms.request
map.mo.sms                →  map.mo.sms
map.mt.sms.response       →  map.mt.sms.response
map.sri.response          →  map.sri.response
map.delivery.report       →  map.delivery.report
```

**Advanced routing** (NATS supports wildcards):
```
map.mt.sms.*              - Match any single token
map.mt.>                  - Match multiple tokens
map.mt.sms.{msisdn}       - Routing by MSISDN (use last digit)
```

### 1.4 Producer Migration

**Kafka Producer Pattern**:
```java
// smsc-core/src/main/java/com/yourcompany/smsc/kafka/producer/MapMessageProducer.java
import org.apache.kafka.clients.producer.*;

public class MapMessageProducer {
    private KafkaProducer<String, SS7Message> producer;

    public void start() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, SS7MessageSerializer.class);
        producer = new KafkaProducer<>(props);
    }

    public void sendMtSmsRequest(MapSmsMessage message) {
        ProducerRecord<String, SS7Message> record = new ProducerRecord<>(
            "map.mt.sms.request",
            message.getMessageId(),
            message
        );
        producer.send(record, callback);
    }
}
```

**NATS Producer Pattern** (Create as `MapMessagePublisher.java`):
```java
// smsc-core/src/main/java/com/yourcompany/smsc/nats/publisher/MapMessagePublisher.java
package com.yourcompany.smsc.nats.publisher;

import io.nats.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.smsc.kafka.messages.*;
import org.apache.log4j.Logger;

public class MapMessagePublisher {
    private static final Logger logger = Logger.getLogger(MapMessagePublisher.class);

    private final NatsConfiguration config;
    private final ObjectMapper objectMapper;
    private Connection natsConnection;

    // NATS subjects
    private static final String SUBJECT_MT_SMS_REQ = "map.mt.sms.request";
    private static final String SUBJECT_MO_SMS = "map.mo.sms";

    public MapMessagePublisher(NatsConfiguration config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    public void start() throws IOException, InterruptedException {
        logger.info("Connecting to NATS: " + config.getServerUrl());

        Options options = new Options.Builder()
            .server(config.getServerUrl())
            .maxReconnects(config.getMaxReconnects())
            .reconnectWait(Duration.ofMillis(config.getReconnectWaitMs()))
            .connectionTimeout(Duration.ofMillis(config.getConnectionTimeoutMs()))
            .pingInterval(Duration.ofMillis(config.getPingIntervalMs()))
            .errorListener(new ErrorListener() {
                @Override
                public void errorOccurred(Connection conn, String error) {
                    logger.error("NATS error: " + error);
                }

                @Override
                public void exceptionOccurred(Connection conn, Exception exp) {
                    logger.error("NATS exception", exp);
                }
            })
            .build();

        natsConnection = Nats.connect(options);
        logger.info("Connected to NATS successfully");
    }

    public void sendMtSmsRequest(MapSmsMessage message) {
        try {
            byte[] data = objectMapper.writeValueAsBytes(message);

            // Simple publish (fire-and-forget)
            natsConnection.publish(SUBJECT_MT_SMS_REQ, data);

            logger.info("Published MT SMS request: " + message.getMessageId());

        } catch (Exception e) {
            logger.error("Failed to publish MT SMS request", e);
            throw new RuntimeException("NATS publish failed", e);
        }
    }

    public void stop() {
        if (natsConnection != null) {
            try {
                natsConnection.close();
                logger.info("NATS connection closed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted during NATS close", e);
            }
        }
    }
}
```

**Key Differences**:
- No serializers/deserializers config - handle in application code
- No partitioning/keys - NATS routing is subject-based
- Simpler connection setup
- No callbacks needed (fire-and-forget)

### 1.5 Consumer Migration

**Kafka Consumer Pattern**:
```java
// smsc-core/src/main/java/com/yourcompany/smsc/kafka/consumer/MapMessageConsumer.java
public class MapMessageConsumer {
    private KafkaConsumer<String, SS7Message> consumer;

    public void start() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "smsc-gateway-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SS7MessageDeserializer.class);

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("map.mo.sms", "map.mt.sms.response"));

        while (running) {
            ConsumerRecords<String, SS7Message> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, SS7Message> record : records) {
                processMessage(record);
            }
            consumer.commitAsync();
        }
    }
}
```

**NATS Consumer Pattern** (Create as `MapMessageSubscriber.java`):
```java
// smsc-core/src/main/java/com/yourcompany/smsc/nats/subscriber/MapMessageSubscriber.java
package com.yourcompany.smsc.nats.subscriber;

import io.nats.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.smsc.kafka.messages.*;
import org.apache.log4j.Logger;
import java.util.concurrent.*;

public class MapMessageSubscriber {
    private static final Logger logger = Logger.getLogger(MapMessageSubscriber.class);

    private final NatsConfiguration config;
    private final ObjectMapper objectMapper;
    private final SmsRouter smsRouter;
    private final DeliveryManager deliveryManager;

    private Connection natsConnection;
    private Dispatcher dispatcher;
    private ExecutorService executorService;

    // NATS subjects
    private static final String SUBJECT_MO_SMS = "map.mo.sms";
    private static final String SUBJECT_MT_SMS_RESP = "map.mt.sms.response";
    private static final String SUBJECT_SRI_RESP = "map.sri.response";
    private static final String SUBJECT_DELIVERY_REPORT = "map.delivery.report";

    public MapMessageSubscriber(NatsConfiguration config, SmsRouter smsRouter,
                                DeliveryManager deliveryManager) {
        this.config = config;
        this.smsRouter = smsRouter;
        this.deliveryManager = deliveryManager;
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    public void start() throws IOException, InterruptedException {
        logger.info("Starting NATS subscriber");

        Options options = new Options.Builder()
            .server(config.getServerUrl())
            .maxReconnects(config.getMaxReconnects())
            .reconnectWait(Duration.ofMillis(config.getReconnectWaitMs()))
            .build();

        natsConnection = Nats.connect(options);

        // Create thread pool for message processing
        int threadCount = 10;
        executorService = Executors.newFixedThreadPool(threadCount);

        // Create dispatcher for async message handling
        dispatcher = natsConnection.createDispatcher();

        // Subscribe to subjects
        dispatcher.subscribe(SUBJECT_MO_SMS, (msg) -> {
            executorService.submit(() -> processMoSms(msg));
        });

        dispatcher.subscribe(SUBJECT_MT_SMS_RESP, (msg) -> {
            executorService.submit(() -> processMtSmsResponse(msg));
        });

        dispatcher.subscribe(SUBJECT_SRI_RESP, (msg) -> {
            executorService.submit(() -> processSriResponse(msg));
        });

        dispatcher.subscribe(SUBJECT_DELIVERY_REPORT, (msg) -> {
            executorService.submit(() -> processDeliveryReport(msg));
        });

        logger.info("NATS subscriber started successfully");
    }

    private void processMoSms(Message msg) {
        try {
            MapSmsMessage mapMessage = objectMapper.readValue(msg.getData(), MapSmsMessage.class);

            logger.info("Processing MO SMS from " + mapMessage.getSender() +
                       " to " + mapMessage.getRecipient());

            // Convert and route to SMPP
            SmsMessage sms = messageConverter.fromMapMessage(mapMessage);
            sms.setSourceAddress(mapMessage.getSender());
            sms.setDestinationAddress(mapMessage.getRecipient());

            String sourceAddr = sms.getSourceAddress();
            String destAddr = sms.getDestinationAddress();
            String content = sms.getShortMessage();

            // Route MO SMS via SMPP deliver_sm
            com.yourcompany.smsc.smpp.server.SmppServer smppServer =
                com.yourcompany.smsc.smpp.server.SmppServer.getInstance();

            if (smppServer != null) {
                boolean sent = smppServer.routeDeliverSm(sourceAddr, destAddr, content);
                if (sent) {
                    logger.info("Successfully routed and delivered MO SMS");
                } else {
                    logger.warn("Failed to route/deliver MO SMS");
                }
            }

        } catch (Exception e) {
            logger.error("Failed to process MO SMS", e);
        }
    }

    private void processMtSmsResponse(Message msg) {
        try {
            MapSmsMessage mapMessage = objectMapper.readValue(msg.getData(), MapSmsMessage.class);
            logger.info("Processing MT SMS response for: " + mapMessage.getCorrelationId());
            deliveryManager.handleDeliveryResponse(mapMessage);
        } catch (Exception e) {
            logger.error("Failed to process MT SMS response", e);
        }
    }

    private void processSriResponse(Message msg) {
        try {
            MapSriMessage sriMessage = objectMapper.readValue(msg.getData(), MapSriMessage.class);
            logger.info("Processing SRI response for: " + sriMessage.getMsisdn());
            // Handle SRI response
        } catch (Exception e) {
            logger.error("Failed to process SRI response", e);
        }
    }

    private void processDeliveryReport(Message msg) {
        try {
            MapSmsMessage reportMessage = objectMapper.readValue(msg.getData(), MapSmsMessage.class);
            logger.info("Processing delivery report for: " + reportMessage.getCorrelationId());
            deliveryManager.handleDeliveryResponse(reportMessage);
        } catch (Exception e) {
            logger.error("Failed to process delivery report", e);
        }
    }

    public void stop() {
        logger.info("Stopping NATS subscriber");

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (natsConnection != null) {
            try {
                natsConnection.close();
                logger.info("NATS connection closed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted during NATS close", e);
            }
        }
    }
}
```

**Key Differences**:
- No polling loop - NATS is push-based (async callbacks)
- No offset management/commits
- No consumer groups - NATS uses queue groups for load balancing
- Simpler error handling

### 1.6 Serialization Changes

**Remove Kafka Serializers**:
```bash
# DELETE these files:
smsc-core/src/main/java/com/yourcompany/smsc/kafka/serialization/SS7MessageSerializer.java
smsc-core/src/main/java/com/yourcompany/smsc/kafka/serialization/SS7MessageDeserializer.java
```

**NATS serialization is application-level**:
```java
// In producer
byte[] data = objectMapper.writeValueAsBytes(message);
natsConnection.publish(subject, data);

// In consumer
MapSmsMessage message = objectMapper.readValue(msg.getData(), MapSmsMessage.class);
```

### 1.7 Application Startup Changes

**File**: `smsc-core/src/main/java/com/yourcompany/smsc/SmscApplication.java`

```java
// BEFORE
private MapMessageConsumer mapConsumer;
private MapMessageProducer mapProducer;

public void start() {
    KafkaConfiguration kafkaConfig = new KafkaConfiguration(config);
    mapConsumer = new MapMessageConsumer(kafkaConfig, smsRouter, deliveryManager);
    mapProducer = new MapMessageProducer(kafkaConfig);

    mapConsumer.start();
    mapProducer.start();
}

// AFTER
private MapMessageSubscriber mapSubscriber;
private MapMessagePublisher mapPublisher;

public void start() {
    NatsConfiguration natsConfig = new NatsConfiguration(config);
    mapSubscriber = new MapMessageSubscriber(natsConfig, smsRouter, deliveryManager);
    mapPublisher = new MapMessagePublisher(natsConfig);

    mapSubscriber.start();
    mapPublisher.start();
}
```

---

## Part 2: ss7-ha-gateway Migration

### 2.1 Dependencies

**Module POM** (`ss7-kafka-bridge/pom.xml` → rename to `ss7-nats-bridge/pom.xml`):

```xml
<!-- BEFORE -->
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>3.5.1</version>
</dependency>

<!-- AFTER -->
<dependency>
    <groupId>io.nats</groupId>
    <artifactId>jnats</artifactId>
    <version>2.17.2</version>
</dependency>
```

### 2.2 SS7 Producer Migration

**Kafka Pattern**:
```java
// ss7-kafka-bridge/src/main/java/com/company/ss7ha/kafka/producer/SS7KafkaProducer.java
public class SS7KafkaProducer {
    private KafkaProducer<String, byte[]> producer;

    public void publishMoSms(MapSmsMessage message) {
        byte[] data = objectMapper.writeValueAsBytes(message);
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
            "map.mo.sms",
            message.getMessageId(),
            data
        );
        producer.send(record);
    }
}
```

**NATS Pattern** (Create as `SS7NatsPublisher.java`):
```java
// ss7-nats-bridge/src/main/java/com/company/ss7ha/nats/publisher/SS7NatsPublisher.java
package com.company.ss7ha.nats.publisher;

import io.nats.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SS7NatsPublisher {
    private static final Logger logger = LoggerFactory.getLogger(SS7NatsPublisher.class);

    private Connection natsConnection;
    private final ObjectMapper objectMapper;

    // NATS subjects
    private static final String SUBJECT_MO_SMS = "map.mo.sms";
    private static final String SUBJECT_MT_SMS_RESP = "map.mt.sms.response";

    public SS7NatsPublisher(String natsUrl) throws IOException, InterruptedException {
        this.objectMapper = new ObjectMapper();

        Options options = new Options.Builder()
            .server(natsUrl)
            .maxReconnects(-1)
            .reconnectWait(Duration.ofSeconds(1))
            .build();

        natsConnection = Nats.connect(options);
        logger.info("SS7NatsPublisher connected to NATS: {}", natsUrl);
    }

    public void publishMoSms(MapSmsMessage message) {
        try {
            byte[] data = objectMapper.writeValueAsBytes(message);
            natsConnection.publish(SUBJECT_MO_SMS, data);
            logger.info("Published MO SMS: {}", message.getMessageId());
        } catch (Exception e) {
            logger.error("Failed to publish MO SMS", e);
        }
    }

    public void publishMtSmsResponse(MapSmsMessage response) {
        try {
            byte[] data = objectMapper.writeValueAsBytes(response);
            natsConnection.publish(SUBJECT_MT_SMS_RESP, data);
            logger.info("Published MT SMS response: {}", response.getCorrelationId());
        } catch (Exception e) {
            logger.error("Failed to publish MT SMS response", e);
        }
    }

    public void close() {
        if (natsConnection != null) {
            try {
                natsConnection.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

### 2.3 SS7 Consumer Migration

**Kafka Pattern**:
```java
// ss7-core/src/main/java/com/company/ss7ha/core/kafka/SS7KafkaConsumer.java
public class SS7KafkaConsumer implements Runnable {
    private KafkaConsumer<String, byte[]> consumer;

    public void run() {
        while (running.get()) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, byte[]> record : records) {
                processRecord(record);
            }
        }
    }
}
```

**NATS Pattern** (Create as `SS7NatsSubscriber.java`):
```java
// ss7-core/src/main/java/com/company/ss7ha/core/nats/SS7NatsSubscriber.java
package com.company.ss7ha.core.nats;

import io.nats.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SS7NatsSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(SS7NatsSubscriber.class);

    private static final String SUBJECT_MT_SMS_REQ = "map.mt.sms.request";

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;
    private final boolean loopbackEnabled;
    private final SS7NatsPublisher publisher;

    public SS7NatsSubscriber(String natsUrl, SS7NatsPublisher publisher, boolean loopbackEnabled)
            throws IOException, InterruptedException {
        this.objectMapper = new ObjectMapper();
        this.loopbackEnabled = loopbackEnabled;
        this.publisher = publisher;

        Options options = new Options.Builder()
            .server(natsUrl)
            .maxReconnects(-1)
            .build();

        natsConnection = Nats.connect(options);
        logger.info("SS7NatsSubscriber connected to NATS: {}", natsUrl);
    }

    public void start() {
        Dispatcher dispatcher = natsConnection.createDispatcher();

        dispatcher.subscribe(SUBJECT_MT_SMS_REQ, (msg) -> {
            try {
                processMessage(msg.getData());
            } catch (Exception e) {
                logger.error("Error processing MT SMS request", e);
            }
        });

        logger.info("SS7NatsSubscriber started, listening on: {}", SUBJECT_MT_SMS_REQ);
    }

    private void processMessage(byte[] data) {
        try {
            JsonNode json = objectMapper.readTree(data);
            String messageId = json.has("messageId") ? json.get("messageId").asText() : "unknown";
            String recipient = json.has("recipient") ? json.get("recipient").asText() : null;
            String sender = json.has("sender") ? json.get("sender").asText() : null;
            String content = json.has("content") ? json.get("content").asText() : "";

            logger.info("Processing MT SMS (messageId={}, to={}, from={})",
                messageId, recipient, sender);

            // TODO: Invoke MAP Stack here to send via SS7

            // Loopback: Create MO SMS response
            if (loopbackEnabled && recipient != null && sender != null) {
                sendLoopbackMoSms(messageId, recipient, sender, content);
            }

        } catch (Exception e) {
            logger.error("Failed to process message", e);
        }
    }

    private void sendLoopbackMoSms(String originalMessageId, String originalRecipient,
                                   String originalSender, String originalContent) {
        try {
            MapSmsMessage moMessage = new MapSmsMessage();
            moMessage.setMessageId(originalMessageId + "_MO");
            moMessage.setCorrelationId(originalMessageId);
            moMessage.setSender(originalRecipient);  // Swap
            moMessage.setRecipient(originalSender);  // Swap
            moMessage.setContent("Echo: " + originalContent);
            moMessage.setSmsType(SmsType.MO_FORWARD_SM);
            moMessage.setDirection(Direction.INBOUND);
            moMessage.setTimestamp(Instant.now());

            publisher.publishMoSms(moMessage);
            logger.info("Loopback MO SMS sent (from {} to {})", originalRecipient, originalSender);

        } catch (Exception e) {
            logger.error("Failed to create loopback MO SMS", e);
        }
    }

    public void close() {
        if (natsConnection != null) {
            try {
                natsConnection.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

---

## Part 3: Deployment Changes

### 3.1 Kubernetes - Deploy NATS

**File**: `k8s/nats-deployment.yaml` (Create new file)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: nats
  namespace: smsc
spec:
  selector:
    app: nats
  ports:
    - name: client
      port: 4222
      targetPort: 4222
    - name: monitoring
      port: 8222
      targetPort: 8222
  type: ClusterIP
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: nats
  namespace: smsc
spec:
  serviceName: nats
  replicas: 1  # Start with 1, scale to 3 for HA
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
        ports:
        - containerPort: 4222
          name: client
        - containerPort: 8222
          name: monitoring
        args:
          - "-m"
          - "8222"
          - "--max_payload"
          - "8388608"   # 8MB max message size
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /healthz
            port: 8222
          initialDelaySeconds: 10
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /healthz
            port: 8222
          initialDelaySeconds: 5
          periodSeconds: 5
```

### 3.2 Application Configuration

**File**: `smsc-config.properties`

```properties
# BEFORE - Remove these
#kafka.bootstrap.servers=kafka.smsc.svc.cluster.local:9092
#kafka.group.id=smsc-gateway-group
#kafka.enable.auto.commit=false

# AFTER - Add these
nats.server.url=nats://nats.smsc.svc.cluster.local:4222
nats.max.reconnects=-1
nats.reconnect.wait.ms=1000
nats.connection.timeout.ms=2000
```

**File**: `ss7-config.properties`

```properties
# BEFORE
#kafka.bootstrap.servers=kafka.smsc.svc.cluster.local:9092

# AFTER
nats.server.url=nats://nats.smsc.svc.cluster.local:4222
ss7.loopback.enabled=true
```

### 3.3 Update Deployment Manifests

**File**: `k8s/deployment.yaml` (cloud-native-smsc)

```yaml
# Update environment variables
env:
  - name: NATS_SERVER_URL
    value: "nats://nats.smsc.svc.cluster.local:4222"
  # Remove KAFKA_BOOTSTRAP_SERVERS
```

**File**: `k8s/ss7-deployment.yaml` (ss7-ha-gateway)

```yaml
env:
  - name: NATS_SERVER_URL
    value: "nats://nats.smsc.svc.cluster.local:4222"
  - name: SS7_LOOPBACK_ENABLED
    value: "true"
```

---

## Part 4: Testing Migration

### 4.1 Unit Tests

Create test using NATS test server:

```java
import io.nats.client.Nats;
import io.nats.client.Connection;

public class NatsPublisherTest {

    @Test
    public void testPublishMoSms() throws Exception {
        // Start in-memory NATS server
        try (NatsTestServer ts = new NatsTestServer()) {
            Connection nc = Nats.connect(ts.getURI());

            MapMessagePublisher publisher = new MapMessagePublisher(config);
            publisher.start();

            MapSmsMessage msg = new MapSmsMessage();
            msg.setMessageId("test-123");
            msg.setSender("14155551111");
            msg.setRecipient("12345");

            publisher.sendMtSmsRequest(msg);

            // Verify message received
            // ...
        }
    }
}
```

### 4.2 Integration Test Plan

1. **Deploy NATS**:
```bash
kubectl apply -f k8s/nats-deployment.yaml
kubectl wait --for=condition=ready pod -l app=nats -n smsc --timeout=60s
```

2. **Build and deploy with NATS**:
```bash
cd cloud-native-smsc
mvn clean package
docker build -t registry.3eai-labs.com:32000/cloud-native-smsc:nats-1.0.0 .
docker push registry.3eai-labs.com:32000/cloud-native-smsc:nats-1.0.0
kubectl set image deployment/cloud-native-smsc smsc=registry.3eai-labs.com:32000/cloud-native-smsc:nats-1.0.0 -n smsc
```

3. **Test end-to-end flow**:
```bash
kubectl run esme-nats-test \
  --image=registry.3eai-labs.com:32000/esme-test-client:1.0.2 \
  --rm -i --restart=Never \
  --env="SMSC_HOST=cloud-native-smsc.smsc.svc.cluster.local" \
  --env="SMSC_PORT=2775" \
  -n esme \
  -- sh -c 'echo "test" | java -jar /app/esme-test-client.jar'
```

4. **Monitor NATS**:
```bash
kubectl port-forward svc/nats 8222:8222 -n smsc
curl http://localhost:8222/varz  # View stats
```

---

## Part 5: Migration Checklist

### cloud-native-smsc

- [ ] Update parent POM dependencies
- [ ] Update smsc-core POM dependencies
- [ ] Create NatsConfiguration.java
- [ ] Create MapMessagePublisher.java (replaces MapMessageProducer)
- [ ] Create MapMessageSubscriber.java (replaces MapMessageConsumer)
- [ ] Delete Kafka serializer/deserializer classes
- [ ] Update SmscApplication.java to use NATS classes
- [ ] Update smsc-config.properties
- [ ] Build and test locally
- [ ] Build Docker image with new tag
- [ ] Deploy to Kubernetes
- [ ] Verify logs and functionality

### ss7-ha-gateway

- [ ] Update pom.xml dependencies
- [ ] Rename ss7-kafka-bridge to ss7-nats-bridge
- [ ] Create SS7NatsPublisher.java (replaces SS7KafkaProducer)
- [ ] Create SS7NatsSubscriber.java (replaces SS7KafkaConsumer)
- [ ] Update SS7Gateway main class
- [ ] Update ss7-config.properties
- [ ] Build and test locally
- [ ] Build Docker image with new tag
- [ ] Deploy to Kubernetes
- [ ] Verify SS7 → NATS → SMSC flow

### Infrastructure

- [ ] Deploy NATS to Kubernetes
- [ ] Update both application deployment YAMLs
- [ ] Test end-to-end A2P flow
- [ ] Test end-to-end P2A flow
- [ ] Monitor NATS metrics
- [ ] Remove Kafka deployment (after validation)

---

## Part 6: Rollback Plan

If issues occur, rollback is simple:

```bash
# Rollback cloud-native-smsc
kubectl set image deployment/cloud-native-smsc \
  smsc=registry.3eai-labs.com:32000/cloud-native-smsc:1.0.38 -n smsc

# Rollback ss7-ha-gateway
kubectl set image deployment/ss7-ha-gw-1 \
  ss7-gateway=registry.3eai-labs.com:32000/ss7-ha-gateway:latest -n ss7

# Or use git:
git checkout master
# Rebuild and redeploy
```

---

## Part 7: Performance Benefits (Expected)

| Metric | Kafka (Current) | NATS (Expected) |
|--------|----------------|-----------------|
| Average Latency | 10-20ms | <1ms |
| P99 Latency | 50-100ms | 2-5ms |
| Throughput (SMS/sec) | 10K-20K | 100K+ |
| Memory Footprint | 512MB+ | 64MB |
| CPU Usage | High | Low |
| Ops Complexity | High | Low |

---

## Questions?

For migration issues:
1. Check NATS logs: `kubectl logs -n smsc -l app=nats`
2. Check app logs: `kubectl logs -n smsc -l app=cloud-native-smsc`
3. NATS monitoring: `http://localhost:8222/varz` (via port-forward)

Good luck with the migration!
