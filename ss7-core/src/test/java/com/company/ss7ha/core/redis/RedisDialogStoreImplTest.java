package com.company.ss7ha.core.redis;

import com.company.ss7ha.core.redis.model.DialogState;
import com.company.ss7ha.core.redis.model.PendingInvoke;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisDialogStoreImpl.
 *
 * Tests Redis dialog storage operations with mocked JedisCluster.
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public class RedisDialogStoreImplTest {

    private JedisCluster mockJedis;
    private RedisDialogStoreImpl dialogStore;

    private static final int TEST_TTL = 3600; // 1 hour

    @Before
    public void setUp() {
        mockJedis = mock(JedisCluster.class);
        dialogStore = new RedisDialogStoreImpl(mockJedis, TEST_TTL);
    }

    @After
    public void tearDown() {
        // Cleanup
    }

    /**
     * Test storing a dialog.
     */
    @Test
    public void testStoreDialog() {
        // Create test dialog state
        DialogState state = createTestDialogState(12345L);

        // Mock Redis response
        when(mockJedis.setex(anyString(), anyInt(), anyString())).thenReturn("OK");
        when(mockJedis.zadd(anyString(), anyDouble(), anyString())).thenReturn(1L);

        // Store dialog
        boolean result = dialogStore.storeDialog(state);

        // Verify
        assertTrue(result);

        // Verify setex was called
        verify(mockJedis, atLeastOnce()).setex(anyString(), eq(TEST_TTL), anyString());

        // Verify zadd was called for active dialogs tracking
        verify(mockJedis, atLeastOnce()).zadd(anyString(), anyDouble(), anyString());
    }

    /**
     * Test storing null dialog.
     */
    @Test
    public void testStoreDialog_Null() {
        boolean result = dialogStore.storeDialog(null);
        assertFalse(result);

        // Verify no Redis calls
        verify(mockJedis, never()).setex(anyString(), anyInt(), anyString());
    }

    /**
     * Test updating a dialog.
     */
    @Test
    public void testUpdateDialog() {
        // Create test dialog state
        DialogState state = createTestDialogState(12345L);
        state.setState("ACTIVE");

        // Mock Redis response
        when(mockJedis.setex(anyString(), anyInt(), anyString())).thenReturn("OK");

        // Update dialog
        boolean result = dialogStore.updateDialog(state);

        // Verify
        assertTrue(result);
        verify(mockJedis, atLeastOnce()).setex(anyString(), eq(TEST_TTL), anyString());
    }

    /**
     * Test loading a dialog.
     */
    @Test
    public void testLoadDialog() {
        // Create test dialog state
        DialogState originalState = createTestDialogState(12345L);

        // Mock Redis to return JSON
        String jsonState = serializeToJson(originalState);
        when(mockJedis.get(anyString())).thenReturn(jsonState);

        // Load dialog
        DialogState loadedState = dialogStore.loadDialog(12345L);

        // Verify
        assertNotNull(loadedState);
        assertEquals(originalState.getDialogId(), loadedState.getDialogId());
        assertEquals(originalState.getLocalGT(), loadedState.getLocalGT());
        assertEquals(originalState.getRemoteGT(), loadedState.getRemoteGT());

        // Verify get was called
        verify(mockJedis).get(anyString());
    }

    /**
     * Test loading non-existent dialog.
     */
    @Test
    public void testLoadDialog_NotFound() {
        // Mock Redis to return null
        when(mockJedis.get(anyString())).thenReturn(null);

        // Load dialog
        DialogState state = dialogStore.loadDialog(99999L);

        // Verify
        assertNull(state);
    }

    /**
     * Test deleting a dialog.
     */
    @Test
    public void testDeleteDialog() {
        // Mock Redis responses
        when(mockJedis.del(anyString())).thenReturn(1L);
        when(mockJedis.zrem(anyString(), anyString())).thenReturn(1L);

        // Delete dialog
        boolean result = dialogStore.deleteDialog(12345L);

        // Verify
        assertTrue(result);

        // Verify del was called
        verify(mockJedis).del(anyString());

        // Verify zrem was called to remove from active dialogs
        verify(mockJedis, atLeastOnce()).zrem(anyString(), anyString());
    }

    /**
     * Test touching (refreshing TTL) a dialog.
     */
    @Test
    public void testTouchDialog() {
        // Mock Redis response
        when(mockJedis.expire(anyString(), anyInt())).thenReturn(1L);

        // Touch dialog
        boolean result = dialogStore.touchDialog(12345L);

        // Verify
        assertTrue(result);

        // Verify expire was called
        verify(mockJedis).expire(anyString(), eq(TEST_TTL));
    }

    /**
     * Test touching non-existent dialog.
     */
    @Test
    public void testTouchDialog_NotFound() {
        // Mock Redis to return 0 (key not found)
        when(mockJedis.expire(anyString(), anyInt())).thenReturn(0L);

        // Touch dialog
        boolean result = dialogStore.touchDialog(99999L);

        // Verify
        assertFalse(result);
    }

    /**
     * Test storing pending invoke.
     */
    @Test
    public void testStorePendingInvoke() {
        // Mock Redis responses
        when(mockJedis.hset(anyString(), anyString(), anyString())).thenReturn(1L);
        when(mockJedis.expire(anyString(), anyInt())).thenReturn(1L);

        // Store pending invoke
        boolean result = dialogStore.storePendingInvoke(
            12345L, 1L, "MO_FORWARD_SM", System.currentTimeMillis());

        // Verify
        assertTrue(result);

        // Verify hset was called
        verify(mockJedis).hset(anyString(), anyString(), anyString());

        // Verify expire was called
        verify(mockJedis).expire(anyString(), anyInt());
    }

    /**
     * Test removing pending invoke.
     */
    @Test
    public void testRemovePendingInvoke() {
        // Mock Redis response
        when(mockJedis.hdel(anyString(), anyString())).thenReturn(1L);

        // Remove pending invoke
        boolean result = dialogStore.removePendingInvoke(12345L, 1L);

        // Verify
        assertTrue(result);

        // Verify hdel was called
        verify(mockJedis).hdel(anyString(), anyString());
    }

    /**
     * Test getting active dialog IDs.
     */
    @Test
    public void testGetActiveDialogIds() {
        // Mock Redis to return some dialog IDs
        java.util.Set<String> mockDialogIds = new java.util.HashSet<>();
        mockDialogIds.add("12345");
        mockDialogIds.add("67890");
        mockDialogIds.add("11111");

        when(mockJedis.zrange(anyString(), anyLong(), anyLong())).thenReturn(mockDialogIds);

        // Get active dialog IDs
        List<Long> activeIds = dialogStore.getActiveDialogIds(10);

        // Verify
        assertNotNull(activeIds);
        assertEquals(3, activeIds.size());
        assertTrue(activeIds.contains(12345L));
        assertTrue(activeIds.contains(67890L));
        assertTrue(activeIds.contains(11111L));

        // Verify zrange was called
        verify(mockJedis).zrange(anyString(), eq(0L), eq(9L)); // limit 10
    }

    /**
     * Test getting statistics.
     */
    @Test
    public void testGetStatistics() {
        // Mock Redis responses
        when(mockJedis.zcard(anyString())).thenReturn(42L);

        // Get statistics
        Map<String, Long> stats = dialogStore.getStatistics();

        // Verify
        assertNotNull(stats);
        assertTrue(stats.containsKey("dialogs.active"));
        assertEquals(Long.valueOf(42L), stats.get("dialogs.active"));
    }

    /**
     * Test health check.
     */
    @Test
    public void testIsHealthy() {
        // Mock Redis PING response
        when(mockJedis.ping()).thenReturn("PONG");

        // Check health
        boolean healthy = dialogStore.isHealthy();

        // Verify
        assertTrue(healthy);

        // Verify ping was called
        verify(mockJedis).ping();
    }

    /**
     * Test health check when Redis is down.
     */
    @Test
    public void testIsHealthy_RedisDown() {
        // Mock Redis to throw exception
        when(mockJedis.ping()).thenThrow(new RuntimeException("Connection failed"));

        // Check health
        boolean healthy = dialogStore.isHealthy();

        // Verify
        assertFalse(healthy);
    }

    /**
     * Test dialog with pending invokes.
     */
    @Test
    public void testStoreAndLoadDialogWithInvokes() {
        // Create dialog with pending invokes
        DialogState state = createTestDialogState(12345L);

        PendingInvoke invoke1 = new PendingInvoke();
        invoke1.setInvokeId(1L);
        invoke1.setOperationCode("MO_FORWARD_SM");
        invoke1.setState("SENT");
        invoke1.setSentAt(System.currentTimeMillis());

        PendingInvoke invoke2 = new PendingInvoke();
        invoke2.setInvokeId(2L);
        invoke2.setOperationCode("SRI_SM");
        invoke2.setState("SENT");
        invoke2.setSentAt(System.currentTimeMillis());

        state.setPendingInvokes(new ArrayList<>());
        state.getPendingInvokes().add(invoke1);
        state.getPendingInvokes().add(invoke2);

        // Mock store
        when(mockJedis.setex(anyString(), anyInt(), anyString())).thenReturn("OK");
        when(mockJedis.zadd(anyString(), anyDouble(), anyString())).thenReturn(1L);

        // Store
        boolean stored = dialogStore.storeDialog(state);
        assertTrue(stored);

        // Mock load
        String jsonState = serializeToJson(state);
        when(mockJedis.get(anyString())).thenReturn(jsonState);

        // Load
        DialogState loaded = dialogStore.loadDialog(12345L);

        // Verify
        assertNotNull(loaded);
        assertNotNull(loaded.getPendingInvokes());
        assertEquals(2, loaded.getPendingInvokes().size());
    }

    /**
     * Helper method to create test dialog state.
     */
    private DialogState createTestDialogState(Long dialogId) {
        DialogState state = new DialogState();
        state.setDialogId(dialogId);
        state.setRemoteDialogId(dialogId + 100);
        state.setLocalGT("123456789");
        state.setRemoteGT("987654321");
        state.setLocalSSN(8);
        state.setRemoteSSN(6);
        state.setState("INITIAL");
        state.setNetworkId(1);
        state.setCreatedAt(System.currentTimeMillis());
        state.setLastActivity(System.currentTimeMillis());
        state.setDialogTimeout(60000L);
        state.setPendingInvokes(new ArrayList<>());
        state.setCustomData(new HashMap<>());

        return state;
    }

    /**
     * Helper method to serialize to JSON (simplified).
     */
    private String serializeToJson(DialogState state) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize", e);
        }
    }
}
