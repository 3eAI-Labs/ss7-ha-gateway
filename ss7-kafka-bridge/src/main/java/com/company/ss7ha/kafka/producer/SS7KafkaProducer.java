package com.company.ss7ha.kafka.producer;

import com.company.ss7ha.kafka.messages.SS7Message;
import com.company.ss7ha.kafka.serialization.SS7MessageSerializer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Kafka producer for sending SS7 messages to Kafka topics
 */
public class SS7KafkaProducer {
    private static final Logger logger = LoggerFactory.getLogger(SS7KafkaProducer.class);
    
    private final KafkaProducer<String, SS7Message> producer;
    private final String topicPrefix;
    private final ConcurrentHashMap<String, Long> messageMetrics = new ConcurrentHashMap<>();
    
    // Topic names
    public static final String TOPIC_MAP_MO_SMS = "map.mo.sms";
    public static final String TOPIC_MAP_MT_SMS = "map.mt.sms";
    public static final String TOPIC_MAP_SRI = "map.sri";
    public static final String TOPIC_MAP_DELIVERY_REPORT = "map.delivery.report";
    public static final String TOPIC_CAP_INITIAL_DP = "cap.initial.dp";
    public static final String TOPIC_CAP_CONTINUE = "cap.continue";
    public static final String TOPIC_CAP_RELEASE = "cap.release";
    public static final String TOPIC_ADMIN_HEALTH = "admin.health";
    
    public SS7KafkaProducer(Properties kafkaConfig, String topicPrefix) {
        this.topicPrefix = topicPrefix != null ? topicPrefix : "";
        
        // Configure Kafka producer
        Properties props = new Properties();
        props.putAll(kafkaConfig);
        
        // Set default properties if not provided
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, 
                         StringSerializer.class.getName());
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
                         SS7MessageSerializer.class.getName());
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.RETRIES_CONFIG, 3);
        props.putIfAbsent(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.putIfAbsent(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.putIfAbsent(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        props.putIfAbsent(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");
        props.putIfAbsent(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.putIfAbsent(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        this.producer = new KafkaProducer<>(props);
        
        logger.info("SS7 Kafka Producer initialized with topic prefix: {}", topicPrefix);
    }
    
    /**
     * Send a message to the appropriate Kafka topic
     */
    public CompletableFuture<RecordMetadata> sendMessage(String topic, SS7Message message) {
        CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
        
        String fullTopic = topicPrefix + topic;
        String key = message.getCorrelationId() != null ? 
                    message.getCorrelationId() : message.getMessageId();
        
        ProducerRecord<String, SS7Message> record = new ProducerRecord<>(fullTopic, key, message);
        
        // Add headers for routing and monitoring
        record.headers()
            .add("messageType", message.getClass().getSimpleName().getBytes())
            .add("timestamp", String.valueOf(System.currentTimeMillis()).getBytes())
            .add("sourceGT", message.getSourceGT() != null ? 
                            message.getSourceGT().getBytes() : new byte[0]);
        
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                logger.error("Failed to send message to topic {}: {}", 
                           fullTopic, exception.getMessage());
                future.completeExceptionally(exception);
                updateMetrics(fullTopic + ".failed");
            } else {
                logger.debug("Message sent to topic {} partition {} offset {}", 
                           metadata.topic(), metadata.partition(), metadata.offset());
                future.complete(metadata);
                updateMetrics(fullTopic + ".success");
            }
        });
        
        return future;
    }
    
    /**
     * Send message with custom partitioning based on destination
     */
    public CompletableFuture<RecordMetadata> sendMessageWithPartitioning(
            String topic, SS7Message message, String partitionKey) {
        
        CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
        String fullTopic = topicPrefix + topic;
        
        ProducerRecord<String, SS7Message> record = new ProducerRecord<>(
            fullTopic, 
            null,  // Let Kafka determine partition based on key
            partitionKey,
            message
        );
        
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                future.completeExceptionally(exception);
            } else {
                future.complete(metadata);
            }
        });
        
        return future;
    }
    
    /**
     * Batch send multiple messages
     */
    public void sendBatch(String topic, java.util.List<SS7Message> messages) {
        String fullTopic = topicPrefix + topic;
        
        for (SS7Message message : messages) {
            String key = message.getCorrelationId() != null ? 
                        message.getCorrelationId() : message.getMessageId();
            ProducerRecord<String, SS7Message> record = new ProducerRecord<>(fullTopic, key, message);
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send batch message: {}", exception.getMessage());
                    updateMetrics(fullTopic + ".batch.failed");
                } else {
                    updateMetrics(fullTopic + ".batch.success");
                }
            });
        }
        
        // Flush to ensure all messages are sent
        producer.flush();
    }
    
    /**
     * Send a health check message
     */
    public void sendHealthCheck(String nodeId, String status) {
        HealthCheckMessage health = new HealthCheckMessage();
        health.setNodeId(nodeId);
        health.setStatus(status);
        health.setTimestamp(java.time.Instant.now());

        // Health check is a special type of SS7Message
        sendMessage(TOPIC_ADMIN_HEALTH, health);
    }
    
    /**
     * Update metrics
     */
    private void updateMetrics(String metric) {
        messageMetrics.compute(metric, (k, v) -> v == null ? 1L : v + 1L);
    }
    
    /**
     * Get metrics
     */
    public ConcurrentHashMap<String, Long> getMetrics() {
        return new ConcurrentHashMap<>(messageMetrics);
    }
    
    /**
     * Flush any pending messages
     */
    public void flush() {
        producer.flush();
    }
    
    /**
     * Close the producer
     */
    public void close() {
        try {
            logger.info("Closing SS7 Kafka Producer");
            producer.close(java.time.Duration.ofSeconds(10));
        } catch (Exception e) {
            logger.error("Error closing Kafka producer", e);
        }
    }

    /**
     * Health check message as a special SS7Message type
     */
    private static class HealthCheckMessage extends SS7Message {
        private String nodeId;
        private String status;

        // Getters and setters
        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        // Note: timestamp is inherited from SS7Message (Instant type)
    }
}