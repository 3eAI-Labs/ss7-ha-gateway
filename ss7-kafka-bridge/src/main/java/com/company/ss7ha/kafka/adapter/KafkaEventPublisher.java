/*
 * Copyright (C) 2024 3eAI Labs
 *
 * This file is part of SS7 HA Gateway.
 */
package com.company.ss7ha.kafka.adapter;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.kafka.converters.EventConverter;
import com.company.ss7ha.kafka.messages.SS7Message;
import com.company.ss7ha.kafka.producer.SS7KafkaProducer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Adapter that implements EventPublisher interface using Kafka as the transport.
 *
 * This class bridges ss7-core's generic EventPublisher interface with the
 * Kafka-specific SS7KafkaProducer implementation.
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final SS7KafkaProducer kafkaProducer;
    private final EventConverter eventConverter;

    public KafkaEventPublisher(SS7KafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
        this.eventConverter = new EventConverter();
    }

    @Override
    public CompletableFuture<PublishMetadata> publishEvent(String topic, Object event) {
        try {
            // Convert generic event to SS7Message
            SS7Message message = eventConverter.convert(event);

            // Send via Kafka
            return kafkaProducer.sendMessage(topic, message)
                .thenApply(metadata -> new KafkaPublishMetadata(metadata));

        } catch (Exception e) {
            logger.error("Failed to publish event to topic {}: {}", topic, e.getMessage());
            CompletableFuture<PublishMetadata> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    @Override
    public CompletableFuture<PublishMetadata> publishEventWithPartitioning(
            String topic, Object event, String partitionKey) {

        try {
            SS7Message message = eventConverter.convert(event);

            return kafkaProducer.sendMessageWithPartitioning(topic, message, partitionKey)
                .thenApply(metadata -> new KafkaPublishMetadata(metadata));

        } catch (Exception e) {
            logger.error("Failed to publish event with partitioning: {}", e.getMessage());
            CompletableFuture<PublishMetadata> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    @Override
    public void publishBatch(String topic, List<Object> events) {
        try {
            // Convert all events to SS7Messages
            List<SS7Message> messages = events.stream()
                .map(eventConverter::convert)
                .collect(Collectors.toList());

            kafkaProducer.sendBatch(topic, messages);

        } catch (Exception e) {
            logger.error("Failed to publish batch: {}", e.getMessage());
            throw new RuntimeException("Batch publish failed", e);
        }
    }

    @Override
    public void close() {
        if (kafkaProducer != null) {
            kafkaProducer.close();
        }
    }

    /**
     * Kafka-specific implementation of PublishMetadata
     */
    private static class KafkaPublishMetadata implements PublishMetadata {
        private final RecordMetadata kafkaMetadata;

        public KafkaPublishMetadata(RecordMetadata kafkaMetadata) {
            this.kafkaMetadata = kafkaMetadata;
        }

        @Override
        public String getTopic() {
            return kafkaMetadata.topic();
        }

        @Override
        public int getPartition() {
            return kafkaMetadata.partition();
        }

        @Override
        public long getOffset() {
            return kafkaMetadata.offset();
        }

        @Override
        public long getTimestamp() {
            return kafkaMetadata.timestamp();
        }
    }
}
