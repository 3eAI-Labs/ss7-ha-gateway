package com.company.ss7ha.core.store;

import com.company.ss7ha.core.model.DialogState;
import com.company.ss7ha.nats.manager.NatsConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.nats.client.Connection;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.KeyValueManagement;
import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * NATS JetStream Key-Value Store for Dialog State
 *
 * Replaces Redis with NATS JetStream KV for dialog persistence.
 * Provides TTL, distributed storage, and high availability.
 */
public class NatsDialogStore implements DialogStore {

    private static final Logger logger = LoggerFactory.getLogger(NatsDialogStore.class);
    private static final String KV_BUCKET_NAME = "ss7-dialog-state";

    private final Connection natsConnection;
    private final KeyValue kvStore;
    private final ObjectMapper objectMapper;
    private final int ttlSeconds;

    /**
     * Create NATS Dialog Store
     *
     * @param natsUrl NATS server URL (e.g., "nats://localhost:4222")
     * @param ttlSeconds Dialog state TTL in seconds
     */
    public NatsDialogStore(String natsUrl, int ttlSeconds) throws Exception {
        this.ttlSeconds = ttlSeconds;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        logger.info("Initializing NATS Dialog Store via Manager: {}", natsUrl);
        NatsConnectionManager.getInstance(natsUrl).connect();
        this.natsConnection = NatsConnectionManager.getInstance().getConnection();

        // Create or get KV bucket
        KeyValueManagement kvm = natsConnection.keyValueManagement();

        KeyValue tempStore = null;
        try {
            // Try to get existing bucket
            tempStore = natsConnection.keyValue(KV_BUCKET_NAME);
            logger.info("Using existing NATS KV bucket: {}", KV_BUCKET_NAME);
        } catch (Exception e) {
            // Create new bucket with TTL
            logger.info("Creating new NATS KV bucket: {} with TTL: {}s", KV_BUCKET_NAME, ttlSeconds);
            KeyValueConfiguration kvc = KeyValueConfiguration.builder()
                    .name(KV_BUCKET_NAME)
                    .ttl(Duration.ofSeconds(ttlSeconds))
                    .build();
            kvm.create(kvc);
            tempStore = natsConnection.keyValue(KV_BUCKET_NAME);
            logger.info("NATS KV bucket created successfully");
        }
        this.kvStore = tempStore;
    }

    @Override
    public void storeDialog(DialogState state) throws Exception {
        if (state == null || state.getDialogId() == null) {
            throw new IllegalArgumentException("DialogState and dialogId cannot be null");
        }

        String key = buildKey(state.getDialogId());
        String json = objectMapper.writeValueAsString(state);

        kvStore.put(key, json.getBytes());
        logger.debug("Stored dialog state: dialogId={}, size={}bytes", state.getDialogId(), json.length());
    }

    @Override
    public DialogState loadDialog(Long dialogId) throws Exception {
        if (dialogId == null) {
            throw new IllegalArgumentException("dialogId cannot be null");
        }

        String key = buildKey(dialogId);
        KeyValueEntry entry = kvStore.get(key);

        if (entry == null || entry.getValue() == null) {
            logger.debug("Dialog state not found: dialogId={}", dialogId);
            return null;
        }

        DialogState state = objectMapper.readValue(entry.getValue(), DialogState.class);
        logger.debug("Loaded dialog state: dialogId={}", dialogId);
        return state;
    }

    @Override
    public void updateDialog(DialogState state) throws Exception {
        // For NATS KV, update is same as store (put with new value)
        storeDialog(state);
    }

    @Override
    public void deleteDialog(Long dialogId) throws Exception {
        if (dialogId == null) {
            throw new IllegalArgumentException("dialogId cannot be null");
        }

        String key = buildKey(dialogId);
        kvStore.delete(key);
        logger.debug("Deleted dialog state: dialogId={}", dialogId);
    }

    @Override
    public boolean exists(Long dialogId) throws Exception {
        if (dialogId == null) {
            return false;
        }

        String key = buildKey(dialogId);
        KeyValueEntry entry = kvStore.get(key);
        return entry != null && entry.getValue() != null;
    }

    @Override
    public void close() throws Exception {
        logger.info("Closing NATS DialogStore usage (connection is shared/managed)");
        // Do NOT close natsConnection as it is managed by NatsConnectionManager
    }

    /**
     * Build KV key from dialog ID
     */
    private String buildKey(Long dialogId) {
        return "dialog:" + dialogId;
    }
}
