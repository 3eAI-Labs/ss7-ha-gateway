package com.company.ss7ha.core.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumes MAP messages from Kafka and triggers SS7 stack.
 */
public class SS7KafkaConsumer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SS7KafkaConsumer.class);
    private static final String TOPIC_MAP_MT_SMS_REQ = "map.mt.sms.request";

    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    // Reference to SS7 stack components (could be passed in constructor)
    // private final MAPStack mapStack; 

    public SS7KafkaConsumer(Properties config) {
        this.objectMapper = new ObjectMapper();
        
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                 config.getProperty("kafka.bootstrap.servers", "localhost:9092"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ss7-gateway-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(TOPIC_MAP_MT_SMS_REQ));
        
        logger.info("SS7KafkaConsumer initialized for topic: {}", TOPIC_MAP_MT_SMS_REQ);
    }

    @Override
    public void run() {
        logger.info("SS7KafkaConsumer started");
        try {
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
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

    private void processRecord(ConsumerRecord<String, String> record) {
        try {
            logger.info("Received Kafka message: key={}, value={}", record.key(), record.value());
            
            JsonNode json = objectMapper.readTree(record.value());
            String messageType = record.headers().lastHeader("messageType") != null ? 
                new String(record.headers().lastHeader("messageType").value()) : "UNKNOWN";
                
            // Relaxed check for demonstration
            if ("MT_FORWARD_SM".equals(messageType) || "UNKNOWN".equals(messageType)) {
                // Extract fields
                JsonNode recipientNode = json.get("recipient");
                if (recipientNode == null) recipientNode = json.get("msisdn");
                String destAddr = recipientNode != null ? recipientNode.asText() : "unknown";
                
                JsonNode contentNode = json.get("messageContent");
                if (contentNode == null) contentNode = json.get("message");
                String text = contentNode != null ? contentNode.asText() : "";
                
                logger.info("Processing MT SMS to {}: {}", destAddr, text);
                
                // TODO: Invoke MAP Stack here
                // mapStack.getProvider().getMAPServiceSms()...
                
                logger.info("Successfully processed MT SMS request (Simulated)");
            }
            
        } catch (Exception e) {
            logger.error("Failed to process message", e);
        }
    }

    public void stop() {
        running.set(false);
    }
}
