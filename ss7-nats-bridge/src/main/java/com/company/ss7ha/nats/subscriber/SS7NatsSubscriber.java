package com.company.ss7ha.nats.subscriber;

import io.nats.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.company.ss7ha.messages.*;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * NATS subscriber for SS7 Gateway
 * Subscribes to MT SMS requests and SRI requests from SMSC Gateway
 *
 * Uses queue groups for load balancing across multiple SS7 Gateway instances
 * Replaces Kafka consumer functionality
 */
public class SS7NatsSubscriber {
    private static final Logger logger = Logger.getLogger(SS7NatsSubscriber.class);

    private final String natsUrl;
    private final String queueGroup;
    private final ObjectMapper objectMapper;
    private Connection natsConnection;
    private Dispatcher dispatcher;
    private ExecutorService messageProcessorPool;

    // NATS subjects for subscribing to requests from SMSC
    private static final String SUBJECT_MT_SMS_REQ = "map.mt.sms.request";
    private static final String SUBJECT_SRI_REQ = "map.sri.request";

    // Message handlers (to be injected)
    private MessageHandler<MapSmsMessage> mtSmsHandler;
    private MessageHandler<MapSriMessage> sriHandler;

    public SS7NatsSubscriber(String natsUrl, String queueGroup) {
        this.natsUrl = natsUrl != null ? natsUrl : "nats://localhost:4222";
        this.queueGroup = queueGroup != null ? queueGroup : "ss7-gateway-group";
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Thread pool for async message processing
        this.messageProcessorPool = Executors.newFixedThreadPool(50);
    }

    /**
     * Initialize NATS connection and subscribe to subjects
     */
    public void start() throws IOException, InterruptedException {
        logger.info("Starting SS7 NATS subscriber, connecting to: " + natsUrl);

        Options options = new Options.Builder()
            .server(natsUrl)
            .maxReconnects(-1)  // Unlimited reconnects
            .reconnectWait(Duration.ofMillis(1000))
            .connectionTimeout(Duration.ofMillis(5000))
            .pingInterval(Duration.ofMillis(120000))
            .maxPingsOut(2)
            .connectionListener(new ConnectionListener() {
                @Override
                public void connectionEvent(Connection conn, Events type) {
                    logger.info("NATS connection event: " + type);
                    if (type == Events.RECONNECTED) {
                        logger.info("NATS reconnected successfully, resubscribing...");
                    }
                }
            })
            .errorListener(new ErrorListener() {
                @Override
                public void errorOccurred(Connection conn, String error) {
                    logger.error("NATS error: " + error);
                }

                @Override
                public void exceptionOccurred(Connection conn, Exception exp) {
                    logger.error("NATS exception", exp);
                }

                @Override
                public void slowConsumerDetected(Connection conn, Consumer consumer) {
                    logger.warn("NATS slow consumer detected");
                }
            })
            .build();

        natsConnection = Nats.connect(options);
        logger.info("SS7 NATS subscriber connected successfully");

        // Create dispatcher for async message handling
        dispatcher = natsConnection.createDispatcher((msg) -> {
            logger.warn("Received message on unhandled subject: " + msg.getSubject());
        });

        // Subscribe to subjects with queue group
        subscribeToSubjects();
    }

    /**
     * Subscribe to all MAP request subjects using queue groups for load balancing
     */
    private void subscribeToSubjects() {
        // Subscribe to MT SMS requests (mobile-terminated SMS to be sent to network)
        dispatcher.subscribe(SUBJECT_MT_SMS_REQ, queueGroup, this::handleMtSmsRequest);
        logger.info("Subscribed to " + SUBJECT_MT_SMS_REQ + " with queue group: " + queueGroup);

        // Subscribe to SRI requests (query HLR for routing info)
        dispatcher.subscribe(SUBJECT_SRI_REQ, queueGroup, this::handleSriRequest);
        logger.info("Subscribed to " + SUBJECT_SRI_REQ + " with queue group: " + queueGroup);
    }

    /**
     * Handle MT SMS request messages
     * Process SMS to be sent to the mobile network via SS7
     */
    private void handleMtSmsRequest(Message msg) {
        messageProcessorPool.submit(() -> {
            try {
                MapSmsMessage smsMessage = objectMapper.readValue(msg.getData(), MapSmsMessage.class);

                logger.info("Received MT SMS request: " + smsMessage.getMessageId() +
                           " to " + smsMessage.getRecipient());

                if (mtSmsHandler != null) {
                    mtSmsHandler.handle(smsMessage);
                } else {
                    logger.warn("No MT SMS handler registered");
                }

            } catch (Exception e) {
                logger.error("Failed to process MT SMS request", e);
            }
        });
    }

    /**
     * Handle SRI request messages
     * Query HLR for routing information
     */
    private void handleSriRequest(Message msg) {
        messageProcessorPool.submit(() -> {
            try {
                MapSriMessage sriMessage = objectMapper.readValue(msg.getData(), MapSriMessage.class);

                logger.info("Received SRI request for MSISDN: " + sriMessage.getMsisdn());

                if (sriHandler != null) {
                    sriHandler.handle(sriMessage);
                } else {
                    logger.warn("No SRI handler registered");
                }

            } catch (Exception e) {
                logger.error("Failed to process SRI request", e);
            }
        });
    }

    /**
     * Check if subscriber is connected
     */
    public boolean isConnected() {
        return natsConnection != null &&
               natsConnection.getStatus() == Connection.Status.CONNECTED;
    }

    /**
     * Get connection statistics
     */
    public String getStats() {
        if (natsConnection == null) {
            return "Not connected";
        }

        Statistics stats = natsConnection.getStatistics();
        return String.format("SS7 NATS Stats - In: %d msgs/%d bytes, Out: %d msgs/%d bytes",
            stats.getInMsgs(), stats.getInBytes(),
            stats.getOutMsgs(), stats.getOutBytes());
    }

    /**
     * Stop the subscriber and close connection
     */
    public void stop() {
        logger.info("Stopping SS7 NATS subscriber");

        // Shutdown message processor pool
        if (messageProcessorPool != null) {
            messageProcessorPool.shutdown();
            try {
                if (!messageProcessorPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    messageProcessorPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                messageProcessorPool.shutdownNow();
            }
        }

        // Close NATS connection
        if (natsConnection != null) {
            try {
                // Flush any pending messages
                natsConnection.flush(Duration.ofSeconds(5));

                // Unsubscribe all
                if (dispatcher != null) {
                    dispatcher.unsubscribe(SUBJECT_MT_SMS_REQ);
                    dispatcher.unsubscribe(SUBJECT_SRI_REQ);
                }

                // Close connection
                natsConnection.close();
                logger.info("SS7 NATS subscriber stopped");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted during NATS close", e);
            } catch (Exception e) {
                logger.error("Error closing NATS connection", e);
            }
        }
    }

    // Setter methods for message handlers (dependency injection)
    public void setMtSmsHandler(MessageHandler<MapSmsMessage> handler) {
        this.mtSmsHandler = handler;
    }

    public void setSriHandler(MessageHandler<MapSriMessage> handler) {
        this.sriHandler = handler;
    }

    /**
     * Generic message handler interface
     */
    public interface MessageHandler<T> {
        void handle(T message);
    }
}
