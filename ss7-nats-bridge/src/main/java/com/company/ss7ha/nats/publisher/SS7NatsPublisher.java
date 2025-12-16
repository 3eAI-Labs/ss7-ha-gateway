package com.company.ss7ha.nats.publisher;

import io.nats.client.Connection;
import io.nats.client.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.company.ss7ha.messages.*;
import com.company.ss7ha.nats.manager.NatsConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * NATS publisher for SS7 Gateway
 * Publishes MO SMS and delivery reports to SMSC Gateway
 *
 * Replaces Kafka producer functionality
 */
public class SS7NatsPublisher {
    private static final Logger logger = LoggerFactory.getLogger(SS7NatsPublisher.class);

    private final String natsUrl;
    private final ObjectMapper objectMapper;
    // Connection managed by NatsConnectionManager

    // NATS subjects for publishing to SMSC
    private static final String SUBJECT_MO_SMS_RESP = "map.mo.sms.response";
    private static final String SUBJECT_MT_SMS_RESP = "map.mt.sms.response";
    private static final String SUBJECT_SRI_RESP = "map.sri.response";
    private static final String SUBJECT_CHECK_IMEI_REQ = "eir.v1.check.request";
    private static final String SUBJECT_OPS_EVENTS_PREFIX = "ops.events.";

    public SS7NatsPublisher(String natsUrl) {
        this.natsUrl = natsUrl != null ? natsUrl : "nats://localhost:4222";
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Publish an Operational Event (Alarm, Audit, Log) for AI Agent Ingestion.
     * Compliant with AI_OPERATIONS_SPEC.md
     *
     * @param code The standardized event code (e.g., EVT-CRIT-001)
     * @param severity INFO, WARNING, or CRITICAL
     * @param message Human-readable description
     * @param details Contextual details (e.g., assocId, peerIp)
     */
    public void publishOpsEvent(String code, String severity, String message, java.util.Map<String, Object> details) {
        Connection natsConnection = NatsConnectionManager.getInstance().getConnection();
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            logger.warn("Cannot publish Ops Event {}, NATS not connected", code);
            return; // Fail safe, don't throw exception for logging
        }

        try {
            OpsEvent event = new OpsEvent();
            event.eventId = java.util.UUID.randomUUID().toString();
            event.timestamp = java.time.Instant.now().toString();
            event.type = severity.equals("CRITICAL") || severity.equals("WARNING") ? "ALARM" : "AUDIT";
            event.code = code;
            event.severity = severity;
            event.source = new OpsEvent.Source(
                System.getenv("NODE_ID") != null ? System.getenv("NODE_ID") : "unknown-node",
                "ss7-gateway"
            );
            event.details = new java.util.LinkedHashMap<>();
            event.details.put("message", message);
            if (details != null) {
                event.details.putAll(details);
            }

            byte[] data = objectMapper.writeValueAsBytes(event);
            String subject = SUBJECT_OPS_EVENTS_PREFIX + code;
            natsConnection.publish(subject, data);

            logger.debug("Published Ops Event: {} ({})", code, subject);

        } catch (Exception e) {
            logger.error("Failed to publish Ops Event: " + code, e);
        }
    }

    // Inner DTO for AI Operations Spec
    private static class OpsEvent {
        public String eventId;
        public String timestamp;
        public String type;
        public String code;
        public String severity;
        public Source source;
        public java.util.Map<String, Object> details;

        public static class Source {
            public String nodeId;
            public String component;

            public Source(String nodeId, String component) {
                this.nodeId = nodeId;
                this.component = component;
            }
        }
    }

    /**
     * Initialize NATS connection via Manager
     */
    public void start() throws IOException, InterruptedException {
        logger.info("Starting SS7 NATS publisher, utilizing manager for: " + natsUrl);
        NatsConnectionManager.getInstance(natsUrl).connect();
        logger.info("SS7 NATS publisher ready");
    }

    /**
     * Send CheckIMEI Request to EIR Core Logic (Request-Reply)
     * @param request The CheckImeiRequest POJO
     * @return The CheckImeiResponse from the EIR Core
     */
    public CheckImeiResponse sendCheckImeiRequest(CheckImeiRequest request) throws Exception {
        Connection natsConnection = NatsConnectionManager.getInstance().getConnection();
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            throw new IllegalStateException("NATS connection not available");
        }

        byte[] reqBytes = objectMapper.writeValueAsBytes(request);
        
        // Synchronous Request with 200ms timeout (per Data Contract)
        Message reply = natsConnection.request(SUBJECT_CHECK_IMEI_REQ, reqBytes, Duration.ofMillis(200));
        
        if (reply == null) {
            throw new java.util.concurrent.TimeoutException("EIR Core did not reply in 200ms");
        }

        return objectMapper.readValue(reply.getData(), CheckImeiResponse.class);
    }

    /**
     * Publish MO SMS response (mobile-originated SMS) to SMSC
     * This is an SMS received from the mobile network that needs to be delivered to ESME
     */
    public void publishMoSmsResponse(MapSmsMessage smsMessage) {
        Connection natsConnection = NatsConnectionManager.getInstance().getConnection();
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
        Connection natsConnection = NatsConnectionManager.getInstance().getConnection();
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
        Connection natsConnection = NatsConnectionManager.getInstance().getConnection();
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
        return NatsConnectionManager.getInstance().isConnected();
    }

    /**
     * Get connection statistics
     */
    public String getStats() {
        if (!NatsConnectionManager.getInstance().isConnected()) {
            return "Not connected";
        }

        io.nats.client.Statistics stats = NatsConnectionManager.getInstance().getConnection().getStatistics();
        return String.format("SS7 NATS Stats - In: %d msgs/%d bytes, Out: %d msgs/%d bytes",
            stats.getInMsgs(), stats.getInBytes(),
            stats.getOutMsgs(), stats.getOutBytes());
    }

    /**
     * Stop the publisher and close connection
     */
    public void stop() {
        logger.info("Stopping SS7 NATS publisher");
        NatsConnectionManager.getInstance().close();
    }
}
