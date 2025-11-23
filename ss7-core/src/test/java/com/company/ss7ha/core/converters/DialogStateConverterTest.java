package com.company.ss7ha.core.converters;

import com.company.ss7ha.core.redis.model.DialogState;
import com.company.ss7ha.core.redis.model.PendingInvoke;
import org.restcomm.protocols.ss7.sccp.parameter.SccpAddress;
import org.restcomm.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.restcomm.protocols.ss7.tcap.api.tc.dialog.TRPseudoState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DialogStateConverter.
 *
 * Tests the conversion of JSS7 Dialog objects to primitive DialogState.
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public class DialogStateConverterTest {

    @Mock
    private Dialog mockDialog;

    @Mock
    private SccpAddress mockLocalAddress;

    @Mock
    private SccpAddress mockRemoteAddress;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Test basic dialog conversion.
     */
    @Test
    public void testFromDialog_BasicConversion() {
        // Setup mock dialog
        when(mockDialog.getLocalDialogId()).thenReturn(12345L);
        when(mockDialog.getRemoteDialogId()).thenReturn(67890L);
        when(mockDialog.getState()).thenReturn(TRPseudoState.Active);
        when(mockDialog.getNetworkId()).thenReturn(1);

        // Convert
        com.company.ss7ha.core.redis.model.DialogState state =
            DialogStateConverter.fromDialog(mockDialog);

        // Verify
        assertNotNull(state);
        assertEquals(Long.valueOf(12345L), state.getDialogId());
        assertEquals(Long.valueOf(67890L), state.getRemoteDialogId());
        assertEquals("Active", state.getState());
        assertEquals(Integer.valueOf(1), state.getNetworkId());
        assertNotNull(state.getCreatedAt());
        assertNotNull(state.getLastActivity());
        assertNotNull(state.getPendingInvokes());
        assertTrue(state.getPendingInvokes().isEmpty());
    }

    /**
     * Test conversion with null dialog.
     */
    @Test
    public void testFromDialog_NullDialog() {
        com.company.ss7ha.core.redis.model.DialogState state =
            DialogStateConverter.fromDialog((Dialog)null);

        assertNull(state);
    }

    /**
     * Test conversion with addressing information.
     */
    @Test
    public void testFromDialog_WithAddressing() {
        // Setup mock dialog with addresses
        when(mockDialog.getLocalDialogId()).thenReturn(12345L);
        when(mockDialog.getState()).thenReturn(TRPseudoState.Active);
        when(mockDialog.getLocalAddress()).thenReturn(mockLocalAddress);
        when(mockDialog.getRemoteAddress()).thenReturn(mockRemoteAddress);

        // Create mock global titles first
        org.restcomm.protocols.ss7.sccp.parameter.GlobalTitle mockLocalGT = createMockGT("123456789");
        org.restcomm.protocols.ss7.sccp.parameter.GlobalTitle mockRemoteGT = createMockGT("987654321");

        // Mock local address
        when(mockLocalAddress.getGlobalTitle()).thenReturn(mockLocalGT);
        when(mockLocalAddress.getSubsystemNumber()).thenReturn(8);
        when(mockLocalAddress.getSignalingPointCode()).thenReturn(1);

        // Mock remote address
        when(mockRemoteAddress.getGlobalTitle()).thenReturn(mockRemoteGT);
        when(mockRemoteAddress.getSubsystemNumber()).thenReturn(6);
        when(mockRemoteAddress.getSignalingPointCode()).thenReturn(2);

        // Convert
        com.company.ss7ha.core.redis.model.DialogState state =
            DialogStateConverter.fromDialog(mockDialog);

        // Verify addressing
        assertNotNull(state);
        assertEquals("123456789", state.getLocalGT());
        assertEquals(Integer.valueOf(8), state.getLocalSSN());
        assertEquals(Integer.valueOf(1), state.getLocalPC());
        assertEquals("987654321", state.getRemoteGT());
        assertEquals(Integer.valueOf(6), state.getRemoteSSN());
        assertEquals(Integer.valueOf(2), state.getRemotePC());
    }

    /**
     * Test updating existing dialog state.
     */
    @Test
    public void testUpdateFromDialog() {
        // Create initial state
        com.company.ss7ha.core.redis.model.DialogState state =
            new com.company.ss7ha.core.redis.model.DialogState();
        state.setDialogId(12345L);
        state.setState("INITIAL");
        state.setCreatedAt(System.currentTimeMillis() - 10000);
        state.setLastActivity(System.currentTimeMillis() - 10000);

        // Setup mock dialog with updated state
        when(mockDialog.getLocalDialogId()).thenReturn(12345L);
        when(mockDialog.getRemoteDialogId()).thenReturn(67890L);
        when(mockDialog.getState()).thenReturn(TRPseudoState.Active);

        long beforeUpdate = System.currentTimeMillis();

        // Update
        com.company.ss7ha.core.redis.model.DialogState updated =
            DialogStateConverter.updateFromDialog(state, (Dialog)mockDialog);

        // Verify
        assertSame(state, updated);
        assertEquals("Active", updated.getState());
        assertEquals(Long.valueOf(67890L), updated.getRemoteDialogId());
        assertTrue(updated.getLastActivity() >= beforeUpdate);
        assertEquals(state.getCreatedAt(), updated.getCreatedAt()); // Should not change
    }

    /**
     * Test update with null inputs.
     */
    @Test
    public void testUpdateFromDialog_NullInputs() {
        com.company.ss7ha.core.redis.model.DialogState state =
            new com.company.ss7ha.core.redis.model.DialogState();

        // Update with null dialog
        com.company.ss7ha.core.redis.model.DialogState result1 =
            DialogStateConverter.updateFromDialog(state, (Dialog)null);
        assertSame(state, result1);

        // Update null state
        com.company.ss7ha.core.redis.model.DialogState result2 =
            DialogStateConverter.updateFromDialog(null, (Dialog)mockDialog);
        assertNull(result2);
    }

    /**
     * Test adding pending invoke.
     */
    @Test
    public void testAddPendingInvoke() {
        com.company.ss7ha.core.redis.model.DialogState state =
            new com.company.ss7ha.core.redis.model.DialogState();
        state.setDialogId(12345L);

        // Add invoke
        DialogStateConverter.addPendingInvoke(state, 1L, "MO_FORWARD_SM", 30000);

        // Verify
        assertNotNull(state.getPendingInvokes());
        assertEquals(1, state.getPendingInvokes().size());

        PendingInvoke invoke = state.getPendingInvokes().get(0);
        assertEquals(Long.valueOf(1L), invoke.getInvokeId());
        assertEquals("MO_FORWARD_SM", invoke.getOperationCode());
        assertEquals("SENT", invoke.getState());
        assertEquals(Integer.valueOf(30000), invoke.getTimeout());
        assertNotNull(invoke.getSentAt());
    }

    /**
     * Test adding multiple pending invokes.
     */
    @Test
    public void testAddPendingInvoke_Multiple() {
        com.company.ss7ha.core.redis.model.DialogState state =
            new com.company.ss7ha.core.redis.model.DialogState();
        state.setDialogId(12345L);

        // Add multiple invokes
        DialogStateConverter.addPendingInvoke(state, 1L, "MO_FORWARD_SM", 30000);
        DialogStateConverter.addPendingInvoke(state, 2L, "SRI_SM", 30000);
        DialogStateConverter.addPendingInvoke(state, 3L, "MT_FORWARD_SM", 30000);

        // Verify
        assertEquals(3, state.getPendingInvokes().size());
    }

    /**
     * Test removing pending invoke.
     */
    @Test
    public void testRemovePendingInvoke() {
        com.company.ss7ha.core.redis.model.DialogState state =
            new com.company.ss7ha.core.redis.model.DialogState();
        state.setDialogId(12345L);

        // Add invokes
        DialogStateConverter.addPendingInvoke(state, 1L, "MO_FORWARD_SM", 30000);
        DialogStateConverter.addPendingInvoke(state, 2L, "SRI_SM", 30000);
        DialogStateConverter.addPendingInvoke(state, 3L, "MT_FORWARD_SM", 30000);

        assertEquals(3, state.getPendingInvokes().size());

        // Remove invoke 2
        DialogStateConverter.removePendingInvoke(state, 2L);

        // Verify
        assertEquals(2, state.getPendingInvokes().size());
        assertFalse(state.getPendingInvokes().stream()
                .anyMatch(inv -> inv.getInvokeId().equals(2L)));
    }

    /**
     * Test getting timed out invokes.
     */
    @Test
    public void testGetTimedOutInvokes() throws InterruptedException {
        com.company.ss7ha.core.redis.model.DialogState state =
            new com.company.ss7ha.core.redis.model.DialogState();
        state.setDialogId(12345L);

        // Add invoke with very short timeout
        DialogStateConverter.addPendingInvoke(state, 1L, "MO_FORWARD_SM", 100);

        // Add invoke with long timeout
        DialogStateConverter.addPendingInvoke(state, 2L, "SRI_SM", 30000);

        // Wait for first invoke to timeout
        Thread.sleep(150);

        // Get timed out invokes
        List<Long> timedOut = DialogStateConverter.getTimedOutInvokes(state);

        // Verify
        assertNotNull(timedOut);
        assertEquals(1, timedOut.size());
        assertTrue(timedOut.contains(1L));
        assertFalse(timedOut.contains(2L));
    }

    /**
     * Test getting timed out invokes with null state.
     */
    @Test
    public void testGetTimedOutInvokes_NullState() {
        List<Long> timedOut = DialogStateConverter.getTimedOutInvokes(null);

        assertNotNull(timedOut);
        assertTrue(timedOut.isEmpty());
    }

    /**
     * Test custom data storage.
     */
    @Test
    public void testSetCustomData() {
        com.company.ss7ha.core.redis.model.DialogState state =
            new com.company.ss7ha.core.redis.model.DialogState();
        state.setDialogId(12345L);

        // Set custom data
        DialogStateConverter.setCustomData(state, "key1", "value1");
        DialogStateConverter.setCustomData(state, "key2", "value2");

        // Verify
        assertNotNull(state.getCustomData());
        assertEquals(2, state.getCustomData().size());
        assertEquals("value1", state.getCustomData().get("key1"));
        assertEquals("value2", state.getCustomData().get("key2"));
    }

    /**
     * Test getting custom data.
     */
    @Test
    public void testGetCustomData() {
        com.company.ss7ha.core.redis.model.DialogState state =
            new com.company.ss7ha.core.redis.model.DialogState();
        state.setDialogId(12345L);

        DialogStateConverter.setCustomData(state, "testKey", "testValue");

        // Get custom data
        String value = DialogStateConverter.getCustomData(state, "testKey");
        assertEquals("testValue", value);

        // Get non-existent key
        String nullValue = DialogStateConverter.getCustomData(state, "nonExistent");
        assertNull(nullValue);
    }

    /**
     * Test getting custom data with null state.
     */
    @Test
    public void testGetCustomData_NullState() {
        String value = DialogStateConverter.getCustomData(null, "anyKey");
        assertNull(value);
    }

    /**
     * Helper method to create mock GlobalTitle.
     */
    private org.restcomm.protocols.ss7.sccp.parameter.GlobalTitle createMockGT(String digits) {
        org.restcomm.protocols.ss7.sccp.parameter.GlobalTitle mockGT =
            mock(org.restcomm.protocols.ss7.sccp.parameter.GlobalTitle.class);
        when(mockGT.getDigits()).thenReturn(digits);
        return mockGT;
    }
}
