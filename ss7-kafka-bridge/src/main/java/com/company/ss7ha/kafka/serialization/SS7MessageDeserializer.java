package com.company.ss7ha.kafka.serialization;

import com.company.ss7ha.kafka.messages.SS7Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka deserializer for SS7Message objects
 */
public class SS7MessageDeserializer implements Deserializer<SS7Message> {
    private static final Logger logger = LoggerFactory.getLogger(SS7MessageDeserializer.class);
    
    private final ObjectMapper objectMapper;
    
    public SS7MessageDeserializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public SS7Message deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        
        try {
            return objectMapper.readValue(data, SS7Message.class);
        } catch (Exception e) {
            logger.error("Failed to deserialize SS7Message from topic {}: {}", 
                        topic, e.getMessage());
            throw new RuntimeException("Deserialization error", e);
        }
    }
}