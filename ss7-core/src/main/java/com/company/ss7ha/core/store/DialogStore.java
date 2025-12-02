package com.company.ss7ha.core.store;

import com.company.ss7ha.core.model.DialogState;

/**
 * Dialog Store Interface
 *
 * Abstraction for dialog state persistence.
 * Implementations: NATS JetStream KV, Redis, In-Memory, etc.
 */
public interface DialogStore {

    /**
     * Store dialog state with TTL
     */
    void storeDialog(DialogState state) throws Exception;

    /**
     * Load dialog state by ID
     */
    DialogState loadDialog(Long dialogId) throws Exception;

    /**
     * Update existing dialog state
     */
    void updateDialog(DialogState state) throws Exception;

    /**
     * Delete dialog state
     */
    void deleteDialog(Long dialogId) throws Exception;

    /**
     * Check if dialog exists
     */
    boolean exists(Long dialogId) throws Exception;

    /**
     * Close the store connection
     */
    void close() throws Exception;
}
