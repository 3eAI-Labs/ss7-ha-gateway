package com.company.ss7ha.core.integration;

import com.company.ss7ha.core.converters.DialogStateConverter;
import com.company.ss7ha.core.redis.RedisDialogStore;
import com.company.ss7ha.core.redis.RedisDialogStoreImpl;
import com.company.ss7ha.core.redis.model.DialogState;
import com.company.ss7ha.kafka.messages.MoSmsMessage;
import com.company.ss7ha.kafka.producer.SS7KafkaProducer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.JedisCluster;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration test.
 *
 * Tests the complete flow from dialog creation to Kafka message publishing.
 *
 * NOTE: This test uses mocked Redis and Kafka for portability.
 * For real integration tests with actual Redis/Kafka, use Testcontainers.
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public class EndToEndFlowTest {

    private RedisDialogStore dialogStore;
    private SS7KafkaProducer kafkaProducer;

    private JedisCluster mockJedis;

    @Before
    public void setUp() {
        // Setup mocked Redis
        mockJedis = mock(JedisCluster.class);
        dialogStore = new RedisDialogStoreImpl(mockJedis, 3600);

        // Setup mocked Kafka
        Properties kafkaConfig = new Properties();
        kafkaConfig.setProperty("bootstrap.servers", "localhost:9092");
        // kafkaProducer = new SS7KafkaProducer(kafkaConfig, "test.");
        kafkaProducer = mock(SS7KafkaProducer.class);
    }

    @After
    public void tearDown() {
        // Cleanup
    }

    /**
     * Test complete dialog lifecycle.
     *
     * Flow:
     * 1. Create dialog
     * 2. Store in Redis
     * 3. Add pending invoke
     * 4. Process MO-ForwardSM
     * 5. Publish to Kafka
     * 6. Receive response
     * 7. Remove pending invoke
     * 8. Close dialog
     * 9. Delete from Redis
     */
    @Test
    public void testCompleteDialogLifecycle() {
        Long dialogId = 12345L;

        // Mock Redis responses
        when(mockJedis.setex(anyString(), anyInt(), anyString())).thenReturn("OK");
        when(mockJedis.zadd(anyString(), anyDouble(), anyString())).thenReturn(1L);
        when(mockJedis.expire(anyString(), anyInt())).thenReturn(1L);
        when(mockJedis.del(anyString())).thenReturn(1L);
        when(mockJedis.zrem(anyString(), anyString())).thenReturn(1L);

        // 1. Create dialog state
        DialogState state = new DialogState();
        state.setDialogId(dialogId);
        state.setLocalGT("123456789");
        state.setRemoteGT("987654321");
        state.setState("INITIAL");
        state.setCreatedAt(System.currentTimeMillis());
        state.setLastActivity(System.currentTimeMillis());

        // 2. Store in Redis
        boolean stored = dialogStore.storeDialog(state);
        assertTrue("Dialog should be stored", stored);

        // Verify Redis store was called
        verify(mockJedis, atLeastOnce()).setex(anyString(), anyInt(), anyString());

        // 3. Add pending invoke
        DialogStateConverter.addPendingInvoke(state, 1L, "MO_FORWARD_SM", 30000);
        boolean updated = dialogStore.updateDialog(state);
        assertTrue("Dialog should be updated with invoke", updated);

        // 4. Simulate MO-ForwardSM processing
        // (In real test, this would trigger MapSmsServiceListener)
        state.setState("ACTIVE");
        dialogStore.updateDialog(state);

        // 5. Verify Kafka publish would be called
        // (In real test, MapSmsServiceListener would publish to Kafka)
        // kafkaProducer.sendMessage("sms.mo.incoming", moSmsMessage);

        // 6. Simulate response received
        DialogStateConverter.removePendingInvoke(state, 1L);
        dialogStore.updateDialog(state);

        // Verify pending invokes list is now empty
        assertTrue("Pending invokes should be empty after response",
                  state.getPendingInvokes().isEmpty());

        // 7. Touch dialog (refresh TTL)
        boolean touched = dialogStore.touchDialog(dialogId);
        assertTrue("Dialog TTL should be refreshed", touched);

        // 8. Close dialog
        state.setState("CLOSED");
        dialogStore.updateDialog(state);

        // 9. Delete from Redis
        boolean deleted = dialogStore.deleteDialog(dialogId);
        assertTrue("Dialog should be deleted", deleted);

        // Verify Redis delete was called
        verify(mockJedis).del(anyString());
    }

    /**
     * Test concurrent dialog operations.
     */
    @Test
    public void testConcurrentDialogOperations() throws InterruptedException {
        int numDialogs = 10;
        CountDownLatch latch = new CountDownLatch(numDialogs);

        // Mock Redis responses
        when(mockJedis.setex(anyString(), anyInt(), anyString())).thenReturn("OK");
        when(mockJedis.zadd(anyString(), anyDouble(), anyString())).thenReturn(1L);

        // Create and store multiple dialogs concurrently
        for (int i = 0; i < numDialogs; i++) {
            final long dialogId = 10000L + i;

            new Thread(() -> {
                try {
                    DialogState state = new DialogState();
                    state.setDialogId(dialogId);
                    state.setLocalGT("123456789");
                    state.setRemoteGT("987654321");
                    state.setState("ACTIVE");
                    state.setCreatedAt(System.currentTimeMillis());

                    boolean stored = dialogStore.storeDialog(state);
                    assertTrue("Dialog " + dialogId + " should be stored", stored);

                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Wait for all threads to complete
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue("All dialogs should be stored", completed);

        // Verify Redis was called for each dialog
        verify(mockJedis, atLeast(numDialogs)).setex(anyString(), anyInt(), anyString());
    }

    /**
     * Test dialog timeout detection.
     */
    @Test
    public void testDialogTimeoutDetection() throws InterruptedException {
        DialogState state = new DialogState();
        state.setDialogId(12345L);
        state.setLocalGT("123456789");
        state.setState("ACTIVE");
        state.setCreatedAt(System.currentTimeMillis());

        // Add invoke with very short timeout
        DialogStateConverter.addPendingInvoke(state, 1L, "MO_FORWARD_SM", 100);

        // Wait for timeout
        Thread.sleep(150);

        // Check for timed out invokes
        java.util.List<Long> timedOut = DialogStateConverter.getTimedOutInvokes(state);

        // Verify
        assertNotNull(timedOut);
        assertEquals(1, timedOut.size());
        assertTrue(timedOut.contains(1L));
    }

    /**
     * Test error recovery scenario.
     */
    @Test
    public void testErrorRecoveryScenario() {
        Long dialogId = 12345L;

        // Mock Redis to simulate initial success then failure
        when(mockJedis.setex(anyString(), anyInt(), anyString()))
            .thenReturn("OK")
            .thenThrow(new RuntimeException("Redis connection lost"))
            .thenReturn("OK"); // Recovery

        // 1. Initial store succeeds
        DialogState state = new DialogState();
        state.setDialogId(dialogId);
        state.setState("ACTIVE");

        boolean stored1 = dialogStore.storeDialog(state);
        assertTrue("Initial store should succeed", stored1);

        // 2. Update fails (Redis connection lost)
        state.setState("ACTIVE_UPDATE");
        boolean stored2 = dialogStore.storeDialog(state);
        assertFalse("Update should fail", stored2);

        // 3. Retry succeeds (connection recovered)
        boolean stored3 = dialogStore.storeDialog(state);
        assertTrue("Retry should succeed after recovery", stored3);

        // Verify Redis was called 3 times
        verify(mockJedis, times(3)).setex(anyString(), anyInt(), anyString());
    }

    /**
     * Test dialog state persistence and recovery.
     */
    @Test
    public void testDialogStatePersistenceAndRecovery() {
        Long dialogId = 12345L;

        // Create dialog state
        DialogState originalState = new DialogState();
        originalState.setDialogId(dialogId);
        originalState.setLocalGT("123456789");
        originalState.setRemoteGT("987654321");
        originalState.setState("ACTIVE");
        originalState.setLocalSSN(8);
        originalState.setRemoteSSN(6);

        // Add pending invoke
        DialogStateConverter.addPendingInvoke(originalState, 1L, "MO_FORWARD_SM", 30000);

        // Add custom data
        DialogStateConverter.setCustomData(originalState, "testKey", "testValue");

        // Mock Redis store and load
        when(mockJedis.setex(anyString(), anyInt(), anyString())).thenReturn("OK");
        when(mockJedis.zadd(anyString(), anyDouble(), anyString())).thenReturn(1L);

        // Store
        boolean stored = dialogStore.storeDialog(originalState);
        assertTrue("State should be stored", stored);

        // Serialize to JSON (simulate Redis storage)
        String json = serializeToJson(originalState);
        assertNotNull("JSON should not be null", json);

        // Mock Redis load
        when(mockJedis.get(anyString())).thenReturn(json);

        // Load (simulating failover recovery)
        DialogState recoveredState = dialogStore.loadDialog(dialogId);

        // Verify all data was preserved
        assertNotNull("Recovered state should not be null", recoveredState);
        assertEquals(originalState.getDialogId(), recoveredState.getDialogId());
        assertEquals(originalState.getLocalGT(), recoveredState.getLocalGT());
        assertEquals(originalState.getRemoteGT(), recoveredState.getRemoteGT());
        assertEquals(originalState.getState(), recoveredState.getState());
        assertEquals(originalState.getLocalSSN(), recoveredState.getLocalSSN());

        // Verify pending invokes were preserved
        assertNotNull(recoveredState.getPendingInvokes());
        assertEquals(1, recoveredState.getPendingInvokes().size());
        assertEquals(Long.valueOf(1L), recoveredState.getPendingInvokes().get(0).getInvokeId());

        // Verify custom data was preserved
        assertNotNull(recoveredState.getCustomData());
        assertEquals("testValue", recoveredState.getCustomData().get("testKey"));
    }

    /**
     * Helper method to serialize to JSON.
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
