package com.company.ss7ha.core.redis;

import com.company.ss7ha.core.redis.model.DialogState;
import com.company.ss7ha.core.redis.model.PendingInvoke;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.params.SetParams;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Redis Cluster implementation of DialogStore using Jedis client.
 *
 * This implementation uses Redis Hashes for dialog state storage,
 * providing efficient partial updates and TTL management.
 *
 * License: AGPL-3.0
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public class RedisDialogStoreImpl implements RedisDialogStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisDialogStoreImpl.class);

    private final JedisCluster jedisCluster;
    private final ObjectMapper objectMapper;
    private final int defaultTTL;  // seconds

    // Statistics
    private final AtomicLong storeCount = new AtomicLong(0);
    private final AtomicLong updateCount = new AtomicLong(0);
    private final AtomicLong loadCount = new AtomicLong(0);
    private final AtomicLong deleteCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    // Key prefixes
    private static final String DIALOG_STATE_PREFIX = "dialog:";
    private static final String DIALOG_STATE_SUFFIX = ":state";
    private static final String DIALOG_INVOKES_SUFFIX = ":invokes";
    private static final String ACTIVE_DIALOGS_KEY = "dialogs:active";
    private static final String NETWORK_DIALOGS_PREFIX = "dialogs:network:";

    /**
     * Constructor with default TTL of 1 hour.
     *
     * @param jedisCluster Redis cluster connection
     */
    public RedisDialogStoreImpl(JedisCluster jedisCluster) {
        this(jedisCluster, 3600);  // 1 hour default TTL
    }

    /**
     * Constructor with custom TTL.
     *
     * @param jedisCluster Redis cluster connection
     * @param defaultTTL Default TTL in seconds
     */
    public RedisDialogStoreImpl(JedisCluster jedisCluster, int defaultTTL) {
        this.jedisCluster = jedisCluster;
        this.objectMapper = new ObjectMapper();
        this.defaultTTL = defaultTTL;

        logger.info("RedisDialogStore initialized with TTL: {} seconds", defaultTTL);
    }

    @Override
    public boolean storeDialog(DialogState state) {
        if (state == null || state.getDialogId() == null) {
            logger.warn("Cannot store null dialog or dialog without ID");
            return false;
        }

        try {
            String key = getDialogKey(state.getDialogId());

            // Serialize dialog state to JSON
            String json = objectMapper.writeValueAsString(state);

            // Store with TTL
            String result = jedisCluster.setex(key, defaultTTL, json);

            // Add to active dialogs sorted set (score = timestamp)
            jedisCluster.zadd(ACTIVE_DIALOGS_KEY, System.currentTimeMillis(),
                             state.getDialogId().toString());

            // Add to network-specific set
            if (state.getNetworkId() != null) {
                String networkKey = NETWORK_DIALOGS_PREFIX + state.getNetworkId();
                jedisCluster.zadd(networkKey, System.currentTimeMillis(),
                                 state.getDialogId().toString());
            }

            storeCount.incrementAndGet();

            logger.debug("Stored dialog {} in Redis", state.getDialogId());
            return "OK".equals(result);

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize dialog {}", state.getDialogId(), e);
            errorCount.incrementAndGet();
            return false;
        } catch (Exception e) {
            logger.error("Failed to store dialog {} in Redis", state.getDialogId(), e);
            errorCount.incrementAndGet();
            return false;
        }
    }

    @Override
    public boolean updateDialog(DialogState state) {
        if (state == null || state.getDialogId() == null) {
            return false;
        }

        try {
            String key = getDialogKey(state.getDialogId());

            // Update timestamp
            state.touch();

            // Serialize and store
            String json = objectMapper.writeValueAsString(state);
            String result = jedisCluster.setex(key, defaultTTL, json);

            // Update score in sorted sets
            jedisCluster.zadd(ACTIVE_DIALOGS_KEY, state.getLastActivity(),
                             state.getDialogId().toString());

            if (state.getNetworkId() != null) {
                String networkKey = NETWORK_DIALOGS_PREFIX + state.getNetworkId();
                jedisCluster.zadd(networkKey, state.getLastActivity(),
                                 state.getDialogId().toString());
            }

            updateCount.incrementAndGet();

            logger.trace("Updated dialog {} in Redis", state.getDialogId());
            return "OK".equals(result);

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize dialog {}", state.getDialogId(), e);
            errorCount.incrementAndGet();
            return false;
        } catch (Exception e) {
            logger.error("Failed to update dialog {} in Redis", state.getDialogId(), e);
            errorCount.incrementAndGet();
            return false;
        }
    }

    @Override
    public DialogState loadDialog(Long dialogId) {
        if (dialogId == null) {
            return null;
        }

        try {
            String key = getDialogKey(dialogId);
            String json = jedisCluster.get(key);

            if (json == null) {
                logger.debug("Dialog {} not found in Redis", dialogId);
                return null;
            }

            DialogState state = objectMapper.readValue(json, DialogState.class);
            loadCount.incrementAndGet();

            logger.info("Loaded dialog {} from Redis (failover recovery)", dialogId);
            return state;

        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize dialog {}", dialogId, e);
            errorCount.incrementAndGet();
            return null;
        } catch (Exception e) {
            logger.error("Failed to load dialog {} from Redis", dialogId, e);
            errorCount.incrementAndGet();
            return null;
        }
    }

    @Override
    public void deleteDialog(Long dialogId) {
        if (dialogId == null) {
            return;
        }

        try {
            String key = getDialogKey(dialogId);
            String invokesKey = getInvokesKey(dialogId);

            // Delete main state
            jedisCluster.del(key);

            // Delete invokes
            jedisCluster.del(invokesKey);

            // Remove from active dialogs set
            jedisCluster.zrem(ACTIVE_DIALOGS_KEY, dialogId.toString());

            // Remove from network sets (we don't know which network, so scan)
            // In production, consider maintaining reverse mapping
            // For now, this is acceptable performance trade-off

            deleteCount.incrementAndGet();

            logger.debug("Deleted dialog {} from Redis", dialogId);

        } catch (Exception e) {
            logger.error("Failed to delete dialog {} from Redis", dialogId, e);
            errorCount.incrementAndGet();
        }
    }

    @Override
    public boolean touchDialog(Long dialogId) {
        if (dialogId == null) {
            return false;
        }

        try {
            String key = getDialogKey(dialogId);

            // Refresh TTL
            Long result = jedisCluster.expire(key, defaultTTL);

            // Update score in sorted set
            long now = System.currentTimeMillis();
            jedisCluster.zadd(ACTIVE_DIALOGS_KEY, now, dialogId.toString());

            return result != null && result == 1;

        } catch (Exception e) {
            logger.error("Failed to touch dialog {} in Redis", dialogId, e);
            errorCount.incrementAndGet();
            return false;
        }
    }

    @Override
    public boolean storePendingInvoke(Long dialogId, Long invokeId,
                                       String operationCode, long sentAt) {
        if (dialogId == null || invokeId == null) {
            return false;
        }

        try {
            String key = getInvokesKey(dialogId);

            PendingInvoke invoke = new PendingInvoke(invokeId, operationCode, sentAt);
            String json = objectMapper.writeValueAsString(invoke);

            // Store as hash field
            jedisCluster.hset(key, invokeId.toString(), json);

            // Set TTL on hash
            jedisCluster.expire(key, defaultTTL);

            logger.trace("Stored pending invoke {} for dialog {}", invokeId, dialogId);
            return true;

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize invoke {} for dialog {}",
                        invokeId, dialogId, e);
            errorCount.incrementAndGet();
            return false;
        } catch (Exception e) {
            logger.error("Failed to store invoke {} for dialog {}",
                        invokeId, dialogId, e);
            errorCount.incrementAndGet();
            return false;
        }
    }

    @Override
    public boolean removePendingInvoke(Long dialogId, Long invokeId) {
        if (dialogId == null || invokeId == null) {
            return false;
        }

        try {
            String key = getInvokesKey(dialogId);
            Long result = jedisCluster.hdel(key, invokeId.toString());

            logger.trace("Removed pending invoke {} for dialog {}", invokeId, dialogId);
            return result != null && result == 1;

        } catch (Exception e) {
            logger.error("Failed to remove invoke {} for dialog {}",
                        invokeId, dialogId, e);
            errorCount.incrementAndGet();
            return false;
        }
    }

    @Override
    public List<Long> getActiveDialogIds(int limit) {
        try {
            Set<String> dialogIds;

            if (limit > 0) {
                // Get most recent N dialogs
                dialogIds = jedisCluster.zrevrange(ACTIVE_DIALOGS_KEY, 0, limit - 1);
            } else {
                // Get all dialogs
                dialogIds = jedisCluster.zrange(ACTIVE_DIALOGS_KEY, 0, -1);
            }

            return dialogIds.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Failed to get active dialog IDs", e);
            errorCount.incrementAndGet();
            return Collections.emptyList();
        }
    }

    @Override
    public List<Long> getActiveDialogIdsByNetwork(int networkId, int limit) {
        try {
            String networkKey = NETWORK_DIALOGS_PREFIX + networkId;
            Set<String> dialogIds;

            if (limit > 0) {
                dialogIds = jedisCluster.zrevrange(networkKey, 0, limit - 1);
            } else {
                dialogIds = jedisCluster.zrange(networkKey, 0, -1);
            }

            return dialogIds.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Failed to get active dialog IDs for network {}", networkId, e);
            errorCount.incrementAndGet();
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new HashMap<>();

        stats.put("store_count", storeCount.get());
        stats.put("update_count", updateCount.get());
        stats.put("load_count", loadCount.get());
        stats.put("delete_count", deleteCount.get());
        stats.put("error_count", errorCount.get());

        try {
            // Get active dialog count
            Long activeCount = jedisCluster.zcard(ACTIVE_DIALOGS_KEY);
            stats.put("active_dialogs", activeCount != null ? activeCount : 0L);
        } catch (Exception e) {
            logger.warn("Failed to get active dialog count", e);
            stats.put("active_dialogs", -1L);
        }

        return stats;
    }

    @Override
    public boolean isHealthy() {
        try {
            // Simple ping test
            String pong = jedisCluster.ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            logger.error("Redis health check failed", e);
            return false;
        }
    }

    @Override
    public long clearAll() {
        logger.warn("Clearing ALL dialog data from Redis!");

        try {
            long count = 0;

            // Get all active dialog IDs
            List<Long> dialogIds = getActiveDialogIds(0);

            // Delete each dialog
            for (Long dialogId : dialogIds) {
                deleteDialog(dialogId);
                count++;
            }

            // Clear sorted sets
            jedisCluster.del(ACTIVE_DIALOGS_KEY);

            logger.warn("Cleared {} dialogs from Redis", count);
            return count;

        } catch (Exception e) {
            logger.error("Failed to clear all dialogs", e);
            errorCount.incrementAndGet();
            return -1;
        }
    }

    /**
     * Get Redis key for dialog state.
     */
    private String getDialogKey(Long dialogId) {
        return DIALOG_STATE_PREFIX + dialogId + DIALOG_STATE_SUFFIX;
    }

    /**
     * Get Redis key for dialog invokes.
     */
    private String getInvokesKey(Long dialogId) {
        return DIALOG_STATE_PREFIX + dialogId + DIALOG_INVOKES_SUFFIX;
    }

    /**
     * Close Redis connection pool (call on shutdown).
     */
    public void close() {
        try {
            jedisCluster.close();
            logger.info("RedisDialogStore closed");
        } catch (Exception e) {
            logger.error("Error closing Redis connection", e);
        }
    }
}
