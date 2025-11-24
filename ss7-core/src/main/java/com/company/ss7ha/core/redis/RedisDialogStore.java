package com.company.ss7ha.core.redis;

import com.company.ss7ha.core.redis.model.DialogState;

import java.util.List;
import java.util.Map;

/**
 * Redis-backed dialog state storage for HA failover.
 *
 * This interface provides persistent storage for SS7 dialog state,
 * enabling zero-message-loss failover when nodes crash.
 *
 * License: AGPL-3.0 (This code uses Corsac JSS7 stack)
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public interface RedisDialogStore {

    /**
     * Store new dialog in Redis.
     * Called immediately after dialog creation.
     *
     * @param state Dialog state to store
     * @return true if stored successfully, false otherwise
     */
    boolean storeDialog(DialogState state);

    /**
     * Update existing dialog state in Redis.
     * Called after each dialog operation (send, receive).
     *
     * @param state Updated dialog state
     * @return true if updated successfully, false otherwise
     */
    boolean updateDialog(DialogState state);

    /**
     * Load dialog from Redis (for failover recovery).
     * Called when message arrives for unknown dialog ID.
     *
     * @param dialogId Dialog ID to load
     * @return Reconstructed dialog state, or null if not found
     */
    DialogState loadDialog(Long dialogId);

    /**
     * Delete dialog from Redis (on close/timeout).
     * Called when dialog completes or times out.
     *
     * @param dialogId Dialog ID to delete
     */
    void deleteDialog(Long dialogId);

    /**
     * Update dialog activity timestamp (lightweight operation).
     * Refreshes TTL and updates last activity time.
     *
     * @param dialogId Dialog ID
     * @return true if updated, false if dialog not found
     */
    boolean touchDialog(Long dialogId);

    /**
     * Store pending invoke for dialog.
     * Tracks sent invokes awaiting response.
     *
     * @param dialogId Dialog ID
     * @param invokeId Invoke ID
     * @param operationCode Operation code
     * @param sentAt Timestamp when invoke was sent
     * @return true if stored successfully
     */
    boolean storePendingInvoke(Long dialogId, Long invokeId,
                                String operationCode, long sentAt);

    /**
     * Remove pending invoke (on response received).
     *
     * @param dialogId Dialog ID
     * @param invokeId Invoke ID to remove
     * @return true if removed successfully
     */
    boolean removePendingInvoke(Long dialogId, Long invokeId);

    /**
     * Get all active dialog IDs (for monitoring/recovery).
     *
     * @param limit Maximum number of IDs to return (0 = no limit)
     * @return List of active dialog IDs
     */
    List<Long> getActiveDialogIds(int limit);

    /**
     * Get all active dialog IDs for a specific network.
     *
     * @param networkId Network ID
     * @param limit Maximum number of IDs to return
     * @return List of active dialog IDs for the network
     */
    List<Long> getActiveDialogIdsByNetwork(int networkId, int limit);

    /**
     * Get dialog statistics for monitoring.
     *
     * @return Map of metric name to value
     */
    Map<String, Long> getStatistics();

    /**
     * Health check - verify Redis connectivity.
     *
     * @return true if Redis is healthy and responsive
     */
    boolean isHealthy();

    /**
     * Clear all dialog data (for testing/maintenance).
     * WARNING: This deletes ALL dialog state!
     *
     * @return Number of dialogs deleted
     */
    long clearAll();

    /**
     * Close the store connection.
     */
    void close();
}
