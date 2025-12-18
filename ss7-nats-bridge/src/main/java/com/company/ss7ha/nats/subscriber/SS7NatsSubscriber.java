package com.company.ss7ha.nats.subscriber;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Statistics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.company.ss7ha.messages.*;
import com.company.ss7ha.nats.manager.NatsConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(SS7NatsSubscriber.class);

    private final String natsUrl;
    private final String queueGroup;
    private final ObjectMapper objectMapper;
    // Connection managed by NatsConnectionManager
    private Dispatcher dispatcher;
    private ExecutorService messageProcessorPool;

    // NATS subjects for subscribing to requests from SMSC
    private static final String SUBJECT_MT_SMS_REQ = "ss7.mt.sms.request";
    private static final String SUBJECT_SRI_REQ = "ss7.sri.request";
    private static final String SUBJECT_CAP_CMD_REQ = "cap.command.>";

    // Message handlers (to be injected)
    private MessageHandler<MapSmsMessage> mtSmsHandler;
    private MessageHandler<MapSriMessage> sriHandler;
    private MessageHandler<String> capCommandHandler;

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
        System.out.println("DEBUG: SS7NatsSubscriber.start() called");
        logger.info("Starting SS7 NATS subscriber, utilizing manager for: " + natsUrl);

        // Ensure connection is established via Manager
        NatsConnectionManager.getInstance(natsUrl).connect();
        Connection natsConnection = NatsConnectionManager.getInstance().getConnection();

        logger.info("SS7 NATS subscriber connected/ready");

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
        System.out.println("DEBUG: Subscribing to subjects: " + SUBJECT_MT_SMS_REQ + ", " + SUBJECT_SRI_REQ);
        // Subscribe to MT SMS requests (mobile-terminated SMS to be sent to network)
        dispatcher.subscribe(SUBJECT_MT_SMS_REQ, queueGroup, this::handleMtSmsRequest);
        logger.info("Subscribed to " + SUBJECT_MT_SMS_REQ + " with queue group: " + queueGroup);

        // Subscribe to SRI requests (query HLR for routing info)
        dispatcher.subscribe(SUBJECT_SRI_REQ, queueGroup, this::handleSriRequest);
        logger.info("Subscribed to " + SUBJECT_SRI_REQ + " with queue group: " + queueGroup);
        
        // Subscribe to CAP commands (Connect, Continue, etc.)
        dispatcher.subscribe(SUBJECT_CAP_CMD_REQ, queueGroup, this::handleCapCommand);
        logger.info("Subscribed to " + SUBJECT_CAP_CMD_REQ + " with queue group: " + queueGroup);
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
     * Handle CAP command messages (Raw JSON)
     */
    private void handleCapCommand(Message msg) {
        messageProcessorPool.submit(() -> {
            try {
                String jsonCommand = new String(msg.getData(), java.nio.charset.StandardCharsets.UTF_8);
                
                logger.debug("Received CAP command on subject: " + msg.getSubject());

                if (capCommandHandler != null) {
                    capCommandHandler.handle(jsonCommand);
                } else {
                    logger.warn("No CAP command handler registered");
                }

            } catch (Exception e) {
                logger.error("Failed to process CAP command", e);
            }
        });
    }

    /**
     * Check if subscriber is connected
     */
    public boolean isConnected() {
        return NatsConnectionManager.getInstance().isConnected();
    }

    /**
     * Get connection statistics
     */
    public String getStats() {
        if (!NatsConnectionManager.getInstance().isConnected()) {
            return "Not connected";
        }

        Statistics stats = NatsConnectionManager.getInstance().getConnection().getStatistics();
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

        // Unsubscribe dispatcher
        if (dispatcher != null && NatsConnectionManager.getInstance().isConnected()) {
            dispatcher.unsubscribe(SUBJECT_MT_SMS_REQ);
            dispatcher.unsubscribe(SUBJECT_SRI_REQ);
            dispatcher.unsubscribe(SUBJECT_CAP_CMD_REQ);
            // We do NOT close the connection here if it is shared, but assuming this stops the app:
            NatsConnectionManager.getInstance().close();
        }
    }

    // Setter methods for message handlers (dependency injection)
    public void setMtSmsHandler(MessageHandler<MapSmsMessage> handler) {
        this.mtSmsHandler = handler;
    }

    public void setSriHandler(MessageHandler<MapSriMessage> handler) {
        this.sriHandler = handler;
    }
    
    public void setCapCommandHandler(MessageHandler<String> handler) {
        this.capCommandHandler = handler;
    }

    /**
     * Generic message handler interface
     */
    public interface MessageHandler<T> {
        void handle(T message);
    }
}
