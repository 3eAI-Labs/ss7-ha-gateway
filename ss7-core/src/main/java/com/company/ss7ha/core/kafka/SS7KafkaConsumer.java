package com.company.ss7ha.core.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumes MAP messages from Kafka and triggers SS7 stack.
 * For loopback testing: creates MO SMS responses and publishes back to Kafka
 */
public class SS7KafkaConsumer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SS7KafkaConsumer.class);
    private static final String TOPIC_MAP_MT_SMS_REQ = "map.mt.sms.request";
    private static final String TOPIC_MAP_MO_SMS = "map.mo.sms";

    private final KafkaConsumer<String, byte[]> consumer;
    private final KafkaProducer<String, byte[]> producer;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final boolean loopbackEnabled;

    // Reference to SS7 stack components (could be passed in constructor)
    // private final MAPStack mapStack;

    public SS7KafkaConsumer(Properties config) {
        this.objectMapper = new ObjectMapper();
        this.loopbackEnabled = Boolean.parseBoolean(
            config.getProperty("ss7.loopback.enabled", "true"));

        // Consumer properties
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                 config.getProperty("kafka.bootstrap.servers", "localhost:9092"));
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "ss7-gateway-group-v2");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        this.consumer = new KafkaConsumer<>(consumerProps);
        this.consumer.subscribe(Collections.singletonList(TOPIC_MAP_MT_SMS_REQ));

        // Producer properties for loopback MO messages
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                 config.getProperty("kafka.bootstrap.servers", "localhost:9092"));
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");

        this.producer = new KafkaProducer<>(producerProps);

        logger.info("SS7KafkaConsumer initialized for topic: {} (loopback: {})",
            TOPIC_MAP_MT_SMS_REQ, loopbackEnabled);
    }

    @Override
    public void run() {
        logger.info("SS7KafkaConsumer started");
        try {
            while (running.get()) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, byte[]> record : records) {
                    processRecord(record);
                }
            }
        } catch (Exception e) {
            logger.error("Error in Kafka consumer loop", e);
        } finally {
            consumer.close();
            logger.info("SS7KafkaConsumer stopped");
        }
    }

    private void processRecord(ConsumerRecord<String, byte[]> record) {
        try {
            // Deserialize byte array to JSON string
            String jsonString = new String(record.value(), java.nio.charset.StandardCharsets.UTF_8);
            logger.info("Received Kafka message: key={}, valueSize={} bytes", record.key(), record.value().length);

            // Parse JSON
            JsonNode json = objectMapper.readTree(jsonString);
            String messageId = json.has("messageId") ? json.get("messageId").asText() : record.key();
            String recipient = json.has("recipient") ? json.get("recipient").asText() : null;
            String sender = json.has("sender") ? json.get("sender").asText() : null;
            String content = json.has("content") ? json.get("content").asText() : "";

            String messageType = record.headers().lastHeader("messageType") != null ?
                new String(record.headers().lastHeader("messageType").value()) : "UNKNOWN";

            logger.info("Processing MT SMS (messageType={}, messageId={}, to={}, from={})",
                messageType, messageId, recipient, sender);

            // TODO: Invoke MAP Stack here to send via SS7
            // mapStack.getProvider().getMAPServiceSms()...

            // Loopback: Create MO SMS response (simulating mobile device reply)
            if (loopbackEnabled && recipient != null && sender != null) {
                sendLoopbackMoSms(messageId, recipient, sender, content);
            }

            logger.info("Successfully processed MT SMS request (Simulated)");

        } catch (Exception e) {
            logger.error("Failed to process message", e);
        }
    }

    /**
     * Send loopback MO SMS (simulating mobile device response)
     */
    private void sendLoopbackMoSms(String originalMessageId, String originalRecipient,
                                   String originalSender, String originalContent) {
        try {
            // Create MO SMS message (swap sender/recipient)
            ObjectNode moMessage = objectMapper.createObjectNode();
            moMessage.put("messageId", originalMessageId + "_MO");
            moMessage.put("correlationId", originalMessageId);
            moMessage.put("sender", originalRecipient);  // Mobile device (was recipient)
            moMessage.put("recipient", originalSender);  // ESME (was sender)
            moMessage.put("content", "Echo: " + originalContent);
            moMessage.put("messageType", "MAP_SMS");  // Jackson type discriminator for SS7Message
            moMessage.put("smsType", "MO_FORWARD_SM");
            moMessage.put("direction", "INBOUND");
            moMessage.put("timestamp", java.time.Instant.now().toString());

            // Convert to bytes
            byte[] moMessageBytes = objectMapper.writeValueAsBytes(moMessage);

            // Create Kafka record
            ProducerRecord<String, byte[]> moRecord = new ProducerRecord<>(
                TOPIC_MAP_MO_SMS,
                originalMessageId + "_MO",
                moMessageBytes
            );

            // Add headers
            moRecord.headers().add("messageType", "MO_FORWARD_SM".getBytes());

            // Send to Kafka
            producer.send(moRecord, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send loopback MO SMS", exception);
                } else {
                    logger.info("Loopback MO SMS sent to topic {} (from {} to {})",
                        metadata.topic(), originalRecipient, originalSender);
                }
            });

        } catch (Exception e) {
            logger.error("Failed to create loopback MO SMS", e);
        }
    }

    public void stop() {
        running.set(false);
    }
}
