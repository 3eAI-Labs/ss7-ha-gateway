/*
 * Copyright (C) 2024 3eAI Labs
 *
 * This file is part of SS7 HA Gateway.
 */
package com.company.ss7ha.kafka.converters;

import com.company.ss7ha.kafka.messages.SS7Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Converts generic event objects to SS7Message format for Kafka publishing.
 *
 * This converter handles the transformation from ss7-core's generic event format
 * to the Kafka-specific SS7Message format.
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public class EventConverter {

    private static final Logger logger = LoggerFactory.getLogger(EventConverter.class);

    /**
     * Convert a generic event object to SS7Message.
     *
     * @param event The event object (typically a Map or specific event class)
     * @return SS7Message ready for Kafka publishing
     */
    public SS7Message convert(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        // Handle Map-based events (from ss7-core)
        if (event instanceof Map) {
            return convertFromMap((Map<String, Object>) event);
        }

        // Handle already-converted SS7Message
        if (event instanceof SS7Message) {
            return (SS7Message) event;
        }

        // Unknown event type
        logger.warn("Unknown event type: {}, creating generic message",
                   event.getClass().getName());
        return createGenericMessage(event);
    }

    /**
     * Convert Map-based event to SS7Message
     */
    private SS7Message convertFromMap(Map<String, Object> eventData) {
        String type = (String) eventData.get("type");

        if ("MO_FORWARD_SM".equals(type)) {
            return MoForwardSmConverter.convertFromMap(eventData);
        }

        // Add more converters as needed
        // if ("MT_FORWARD_SM".equals(type)) { ... }
        // if ("SRI_SM".equals(type)) { ... }

        logger.warn("No specific converter for type: {}, using generic", type);
        return createGenericMessageFromMap(eventData);
    }

    /**
     * Create a generic SS7Message from Map data
     */
    private SS7Message createGenericMessageFromMap(Map<String, Object> eventData) {
        // Use anonymous class since SS7Message is abstract
        SS7Message message = new SS7Message((String) eventData.get("type")) {
            // Anonymous concrete implementation
        };
        message.setMessageId(UUID.randomUUID().toString());
        message.setTimestamp(Instant.now());

        // Extract common fields
        if (eventData.containsKey("dialogId")) {
            message.setDialogId(String.valueOf(eventData.get("dialogId")));
        }

        // Note: setRawData() doesn't exist in SS7Message - removed

        return message;
    }

    /**
     * Create generic message from unknown object type
     */
    private SS7Message createGenericMessage(Object event) {
        // Use anonymous class since SS7Message is abstract
        SS7Message message = new SS7Message(event.getClass().getSimpleName()) {
            // Anonymous concrete implementation
        };
        message.setMessageId(UUID.randomUUID().toString());
        message.setTimestamp(Instant.now());

        // Note: setRawData() doesn't exist in SS7Message - removed

        return message;
    }
}
