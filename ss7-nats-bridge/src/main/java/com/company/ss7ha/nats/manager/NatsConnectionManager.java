package com.company.ss7ha.nats.manager;

import io.nats.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * Centralized Manager for NATS Connection.
 * Implements the Manager Pattern to handle connection lifecycle, reconnection, and error handling.
 */
public class NatsConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(NatsConnectionManager.class);
    private static NatsConnectionManager instance;
    private Connection natsConnection;
    private final String natsUrl;

    private NatsConnectionManager(String natsUrl) {
        this.natsUrl = natsUrl != null ? natsUrl : "nats://localhost:4222";
    }

    public static synchronized NatsConnectionManager getInstance(String natsUrl) {
        if (instance == null) {
            instance = new NatsConnectionManager(natsUrl);
        }
        return instance;
    }

    public static synchronized NatsConnectionManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("NatsConnectionManager not initialized. Call getInstance(String url) first.");
        }
        return instance;
    }

    public void connect() throws IOException, InterruptedException {
        if (natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED) {
            logger.debug("NATS is already connected.");
            return;
        }

        logger.info("Connecting to NATS: {}", natsUrl);

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
                    logger.error("NATS error: {}", error);
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
            .connectionListener((conn, type) -> {
                logger.info("NATS connection event: {}", type);
                if (type == ConnectionListener.Events.RECONNECTED) {
                    logger.info("NATS reconnected successfully");
                } else if (type == ConnectionListener.Events.DISCONNECTED) {
                    logger.warn("NATS disconnected");
                }
            })
            .build();

        natsConnection = Nats.connect(options);
        logger.info("NATS connected successfully");
    }

    public Connection getConnection() {
        if (natsConnection == null) {
            throw new IllegalStateException("NATS not connected. Call connect() first.");
        }
        return natsConnection;
    }

    public boolean isConnected() {
        return natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED;
    }

    public void close() {
        logger.info("Closing NATS connection...");
        if (natsConnection != null) {
            try {
                natsConnection.flush(Duration.ofSeconds(5));
                natsConnection.close();
                logger.info("NATS connection closed.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted during NATS close", e);
            } catch (Exception e) {
                logger.error("Error closing NATS connection", e);
            }
        }
    }
}
