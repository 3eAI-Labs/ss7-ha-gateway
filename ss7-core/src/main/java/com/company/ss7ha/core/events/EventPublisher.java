/*
 * Copyright (C) 2024 3eAI Labs
 *
 * This file is part of SS7 HA Gateway.
 */
package com.company.ss7ha.core.events;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for publishing SS7 events to external systems.
 * This abstraction allows ss7-core to be independent of the messaging implementation.
 *
 * Implementations might use Kafka, RabbitMQ, gRPC, or other messaging systems.
 */
public interface EventPublisher {

    /**
     * Publish an event to a specific topic/channel
     *
     * @param topic The topic/channel name
     * @param event The event object to publish
     * @return CompletableFuture with metadata about the published event
     */
    CompletableFuture<PublishMetadata> publishEvent(String topic, Object event);

    /**
     * Publish an event with custom partitioning/routing key
     *
     * @param topic The topic/channel name
     * @param event The event object
     * @param partitionKey Key for determining partition/routing
     * @return CompletableFuture with metadata
     */
    CompletableFuture<PublishMetadata> publishEventWithPartitioning(
        String topic, Object event, String partitionKey);

    /**
     * Publish multiple events in batch
     *
     * @param topic The topic/channel name
     * @param events List of events to publish
     */
    void publishBatch(String topic, List<Object> events);

    /**
     * Close/shutdown the publisher
     */
    void close();

    /**
     * Metadata returned after publishing an event
     */
    interface PublishMetadata {
        /**
         * @return Topic/channel where event was published
         */
        String getTopic();

        /**
         * @return Partition number (if applicable, -1 otherwise)
         */
        int getPartition();

        /**
         * @return Offset/sequence number (if applicable, -1 otherwise)
         */
        long getOffset();

        /**
         * @return Timestamp when event was published
         */
        long getTimestamp();
    }
}
