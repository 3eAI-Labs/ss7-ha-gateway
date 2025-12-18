package com.company.ss7ha.core.events;

import com.company.ss7ha.nats.publisher.SS7NatsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Adapts SS7NatsPublisher to the EventPublisher interface.
 */
public class NatsEventPublisherAdapter implements EventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(NatsEventPublisherAdapter.class);
    private final SS7NatsPublisher natsPublisher;
    private final ObjectMapper mapper;

    public NatsEventPublisherAdapter(SS7NatsPublisher natsPublisher) {
        this.natsPublisher = natsPublisher;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public CompletableFuture<PublishMetadata> publishEvent(String topic, Object event) {
        return publishEventWithPartitioning(topic, event, null);
    }

    @Override
    public CompletableFuture<PublishMetadata> publishEventWithPartitioning(String topic, Object event, String partitionKey) {
        CompletableFuture<PublishMetadata> future = new CompletableFuture<>();
        try {
            // Convert event to generic map/string if needed, or pass directly if supported
            // Assuming SS7NatsPublisher has a generic publish method or we adapt specific topics
            // For now, we'll try to use a generic publish if available, or just log if not
            
            // NOTE: SS7NatsPublisher is currently specific to strict message types.
            // We are adapting it to send raw JSON to the specified topic.
            
            String jsonPayload = mapper.writeValueAsString(event);
            natsPublisher.publishRaw(topic, jsonPayload);
            
            future.complete(new PublishMetadata() {
                @Override public String getTopic() { return topic; }
                @Override public int getPartition() { return -1; }
                @Override public long getOffset() { return -1; }
                @Override public long getTimestamp() { return System.currentTimeMillis(); }
            });
            
        } catch (Exception e) {
            logger.error("Failed to publish event to NATS topic: {}", topic, e);
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void publishBatch(String topic, List<Object> events) {
        for (Object event : events) {
            publishEvent(topic, event);
        }
    }

    @Override
    public void close() {
        // NatsPublisher lifecycle is managed elsewhere (Bootstrap)
    }
}
