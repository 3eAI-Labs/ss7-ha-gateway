package com.company.ss7ha.nats.publisher;

import io.nats.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.company.ss7ha.messages.*;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.time.Duration;

/**
 * NATS publisher for SS7 Gateway
 * Publishes MO SMS and delivery reports to SMSC Gateway
 *
 * Replaces Kafka producer functionality
 */
public class SS7NatsPublisher {
    private static final Logger logger = Logger.getLogger(SS7NatsPublisher.class);

    private final String natsUrl;
    private final ObjectMapper objectMapper;
    private Connection natsConnection;

    // NATS subjects for publishing to SMSC
    private static final String SUBJECT_MO_SMS_RESP = "map.mo.sms.response";
    private static final String SUBJECT_MT_SMS_RESP = "map.mt.sms.response";
    private static final String SUBJECT_SRI_RESP = "map.sri.response";

    public SS7NatsPublisher(String natsUrl) {
        this.natsUrl = natsUrl != null ? natsUrl : "nats://localhost:4222";
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Initialize NATS connection
     */
    public void start() throws IOException, InterruptedException {
        logger.info("Starting SS7 NATS publisher, connecting to: " + natsUrl);

        Options options = new Options.Builder()
            .server(natsUrl)
            .maxReconnects(-1)  // Unlimited reconnects
            .reconnectWait(Duration.ofMillis(1000))
            .connectionTimeout(Duration.ofMillis(5000))
            .pingInterval(Duration.ofMillis(120000))
            .maxPingsOut(2)
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
            .connectionListener(new ConnectionListener() {
                @Override
                public void connectionEvent(Connection conn, Events type) {
                    logger.info("NATS connection event: " + type);
                    if (type == Events.RECONNECTED) {
                        logger.info("NATS reconnected successfully");
                    } else if (type == Events.DISCONNECTED) {
                        logger.warn("NATS disconnected");
                    }
                }
            })
            .build();

        natsConnection = Nats.connect(options);
        logger.info("SS7 NATS publisher connected successfully");
    }

    /**
     * Publish MO SMS response (mobile-originated SMS) to SMSC
     * This is an SMS received from the mobile network that needs to be delivered to ESME
     */
    public void publishMoSmsResponse(MapSmsMessage smsMessage) {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            logger.error("Cannot publish MO SMS, NATS not connected");
            throw new IllegalStateException("NATS connection not available");
        }

        try {
            byte[] data = objectMapper.writeValueAsBytes(smsMessage);
            natsConnection.publish(SUBJECT_MO_SMS_RESP, data);

            logger.info("Published MO SMS response: " + smsMessage.getMessageId() +
                       " from " + smsMessage.getSender() +
                       " to " + smsMessage.getRecipient());

        } catch (Exception e) {
            logger.error("Failed to publish MO SMS response: " + smsMessage.getMessageId(), e);
            throw new RuntimeException("NATS publish failed", e);
        }
    }

    /**
     * Publish MT SMS delivery report to SMSC
     * This is a delivery status update for an MT SMS sent to the mobile network
     */
    public void publishMtSmsResponse(MapSmsMessage smsMessage) {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            logger.error("Cannot publish MT SMS response, NATS not connected");
            throw new IllegalStateException("NATS connection not available");
        }

        try {
            byte[] data = objectMapper.writeValueAsBytes(smsMessage);
            natsConnection.publish(SUBJECT_MT_SMS_RESP, data);

            logger.info("Published MT SMS response: " + smsMessage.getMessageId() +
                       " status: " + smsMessage.getDeliveryStatus());

        } catch (Exception e) {
            logger.error("Failed to publish MT SMS response: " + smsMessage.getMessageId(), e);
            throw new RuntimeException("NATS publish failed", e);
        }
    }

    /**
     * Publish SRI response (routing info from HLR) to SMSC
     */
    public void publishSriResponse(MapSriMessage sriMessage) {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            logger.error("Cannot publish SRI response, NATS not connected");
            throw new IllegalStateException("NATS connection not available");
        }

        try {
            byte[] data = objectMapper.writeValueAsBytes(sriMessage);
            natsConnection.publish(SUBJECT_SRI_RESP, data);

            logger.info("Published SRI response for MSISDN: " + sriMessage.getMsisdn() +
                       " MSC: " + sriMessage.getMscAddress());

        } catch (Exception e) {
            logger.error("Failed to publish SRI response for MSISDN: " + sriMessage.getMsisdn(), e);
            throw new RuntimeException("NATS publish failed", e);
        }
    }

    /**
     * Check if publisher is connected
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
     * Stop the publisher and close connection
     */
    public void stop() {
        logger.info("Stopping SS7 NATS publisher");

        if (natsConnection != null) {
            try {
                // Flush any pending messages
                natsConnection.flush(Duration.ofSeconds(5));

                // Close connection
                natsConnection.close();
                logger.info("SS7 NATS publisher stopped");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted during NATS close", e);
            } catch (Exception e) {
                logger.error("Error closing NATS connection", e);
            }
        }
    }
}
