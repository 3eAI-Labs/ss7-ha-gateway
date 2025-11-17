package com.company.ss7ha.kafka.serialization;

import com.company.ss7ha.kafka.messages.SS7Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka serializer for SS7Message objects
 */
public class SS7MessageSerializer implements Serializer<SS7Message> {
    private static final Logger logger = LoggerFactory.getLogger(SS7MessageSerializer.class);
    
    private final ObjectMapper objectMapper;
    
    public SS7MessageSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    @Override
    public byte[] serialize(String topic, SS7Message data) {
        if (data == null) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            logger.error("Failed to serialize SS7Message for topic {}: {}", 
                        topic, e.getMessage(), e);
            throw new RuntimeException("Serialization error", e);
        }
    }
}