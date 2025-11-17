package com.company.ss7ha.core.converters;

import com.company.ss7ha.core.redis.model.DialogState;
import com.company.ss7ha.core.redis.model.PendingInvoke;
import com.mobius.software.telco.protocols.ss7.asn.ASNClass;
import com.mobius.software.telco.protocols.ss7.asn.ASNParser;
import com.mobius.software.telco.protocols.ss7.tcap.api.TCAPDialog;
import com.mobius.software.telco.protocols.ss7.tcap.api.tc.dialog.Dialog;
import com.mobius.software.telco.protocols.ss7.tcap.api.tc.dialog.events.TCBeginIndication;
import com.mobius.software.telco.protocols.ss7.tcap.api.tc.dialog.events.TCContinueIndication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts JSS7 Dialog objects to license-safe DialogState.
 *
 * CRITICAL: This converter extracts ONLY primitive data from Dialog.
 * NO JSS7 objects are stored - this maintains the AGPL firewall!
 *
 * LICENSE: AGPL-3.0 (part of ss7-ha-gateway)
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public class DialogStateConverter {

    private static final Logger logger = LoggerFactory.getLogger(DialogStateConverter.class);

    /**
     * Convert JSS7 Dialog to primitive DialogState.
     *
     * This method extracts all essential dialog information as primitive types:
     * - Dialog IDs (local and remote)
     * - Addresses (GT, SSN, PC as strings/integers)
     * - State, timing, application context
     *
     * NO JSS7 objects are included in the result!
     *
     * @param dialog JSS7 Dialog object
     * @return DialogState with primitive types only
     */
    public static DialogState fromDialog(Dialog dialog) {
        if (dialog == null) {
            logger.warn("Cannot convert null dialog to DialogState");
            return null;
        }

        try {
            DialogState state = new DialogState();

            // Basic dialog identifiers
            state.setDialogId(dialog.getLocalDialogId());
            state.setRemoteDialogId(dialog.getRemoteDialogId());

            // Dialog state
            state.setState(dialog.getState() != null ? dialog.getState().toString() : "UNKNOWN");
            state.setCreatedAt(System.currentTimeMillis());
            state.setLastActivity(System.currentTimeMillis());

            // Network ID (if available)
            if (dialog.getNetworkId() != null) {
                state.setNetworkId(dialog.getNetworkId());
            }

            // Local addressing
            if (dialog.getLocalAddress() != null) {
                if (dialog.getLocalAddress().getGlobalTitle() != null) {
                    state.setLocalGT(dialog.getLocalAddress().getGlobalTitle().getDigits());
                }
                if (dialog.getLocalAddress().getSubsystemNumber() != null) {
                    state.setLocalSSN(dialog.getLocalAddress().getSubsystemNumber());
                }
                if (dialog.getLocalAddress().getPointCode() != null) {
                    state.setLocalPC(dialog.getLocalAddress().getPointCode());
                }
            }

            // Remote addressing
            if (dialog.getRemoteAddress() != null) {
                if (dialog.getRemoteAddress().getGlobalTitle() != null) {
                    state.setRemoteGT(dialog.getRemoteAddress().getGlobalTitle().getDigits());
                }
                if (dialog.getRemoteAddress().getSubsystemNumber() != null) {
                    state.setRemoteSSN(dialog.getRemoteAddress().getSubsystemNumber());
                }
                if (dialog.getRemoteAddress().getPointCode() != null) {
                    state.setRemotePC(dialog.getRemoteAddress().getPointCode());
                }
            }

            // Application context (if available)
            if (dialog.getApplicationContext() != null) {
                state.setApplicationContextName(dialog.getApplicationContext().toString());
            }

            // Dialog timeout
            if (dialog.getIdleTaskTimeout() != null) {
                state.setDialogTimeout(dialog.getIdleTaskTimeout());
            }

            // Initialize empty collections
            state.setPendingInvokes(new ArrayList<>());
            state.setCustomData(new HashMap<>());

            logger.debug("Converted dialog {} to DialogState (GT: {} -> {})",
                    state.getDialogId(),
                    state.getLocalGT(),
                    state.getRemoteGT());

            return state;

        } catch (Exception e) {
            logger.error("Failed to convert dialog {} to DialogState",
                    dialog.getLocalDialogId(), e);
            return null;
        }
    }

    /**
     * Update existing DialogState from Dialog.
     *
     * This updates state, timing, and other dynamic fields while preserving
     * pending invokes and custom data.
     *
     * @param state Existing DialogState
     * @param dialog Current JSS7 Dialog
     * @return Updated DialogState
     */
    public static DialogState updateFromDialog(DialogState state, Dialog dialog) {
        if (state == null || dialog == null) {
            logger.warn("Cannot update DialogState - state or dialog is null");
            return state;
        }

        try {
            // Update state
            if (dialog.getState() != null) {
                state.setState(dialog.getState().toString());
            }

            // Update activity timestamp
            state.setLastActivity(System.currentTimeMillis());

            // Update remote dialog ID if now available
            if (dialog.getRemoteDialogId() != null && state.getRemoteDialogId() == null) {
                state.setRemoteDialogId(dialog.getRemoteDialogId());
            }

            // Update remote addressing if now available
            if (dialog.getRemoteAddress() != null) {
                if (dialog.getRemoteAddress().getGlobalTitle() != null && state.getRemoteGT() == null) {
                    state.setRemoteGT(dialog.getRemoteAddress().getGlobalTitle().getDigits());
                }
                if (dialog.getRemoteAddress().getSubsystemNumber() != null && state.getRemoteSSN() == null) {
                    state.setRemoteSSN(dialog.getRemoteAddress().getSubsystemNumber());
                }
                if (dialog.getRemoteAddress().getPointCode() != null && state.getRemotePC() == null) {
                    state.setRemotePC(dialog.getRemoteAddress().getPointCode());
                }
            }

            logger.debug("Updated DialogState for dialog {}", state.getDialogId());

            return state;

        } catch (Exception e) {
            logger.error("Failed to update DialogState from dialog {}",
                    dialog.getLocalDialogId(), e);
            return state;
        }
    }

    /**
     * Add pending invoke to DialogState.
     *
     * @param state DialogState
     * @param invokeId Invoke ID
     * @param operationCode Operation code as string
     * @param timeout Timeout in milliseconds
     */
    public static void addPendingInvoke(DialogState state, Long invokeId,
                                       String operationCode, Integer timeout) {
        if (state == null) {
            return;
        }

        PendingInvoke invoke = new PendingInvoke();
        invoke.setInvokeId(invokeId);
        invoke.setOperationCode(operationCode);
        invoke.setState("SENT");
        invoke.setSentAt(System.currentTimeMillis());
        invoke.setTimeout(timeout);

        if (state.getPendingInvokes() == null) {
            state.setPendingInvokes(new ArrayList<>());
        }

        state.getPendingInvokes().add(invoke);

        logger.debug("Added pending invoke {} (op: {}) to dialog {}",
                invokeId, operationCode, state.getDialogId());
    }

    /**
     * Remove pending invoke from DialogState.
     *
     * @param state DialogState
     * @param invokeId Invoke ID to remove
     */
    public static void removePendingInvoke(DialogState state, Long invokeId) {
        if (state == null || state.getPendingInvokes() == null) {
            return;
        }

        state.getPendingInvokes().removeIf(invoke ->
                invoke.getInvokeId() != null && invoke.getInvokeId().equals(invokeId));

        logger.debug("Removed pending invoke {} from dialog {}",
                invokeId, state.getDialogId());
    }

    /**
     * Check if DialogState has timed out invokes.
     *
     * @param state DialogState to check
     * @return List of timed out invoke IDs
     */
    public static List<Long> getTimedOutInvokes(DialogState state) {
        List<Long> timedOut = new ArrayList<>();

        if (state == null || state.getPendingInvokes() == null) {
            return timedOut;
        }

        for (PendingInvoke invoke : state.getPendingInvokes()) {
            if (invoke.isTimedOut()) {
                timedOut.add(invoke.getInvokeId());
            }
        }

        return timedOut;
    }

    /**
     * Add custom metadata to DialogState.
     *
     * Useful for application-specific data that needs to survive failover.
     *
     * @param state DialogState
     * @param key Metadata key
     * @param value Metadata value (must be String)
     */
    public static void setCustomData(DialogState state, String key, String value) {
        if (state == null) {
            return;
        }

        if (state.getCustomData() == null) {
            state.setCustomData(new HashMap<>());
        }

        state.getCustomData().put(key, value);
    }

    /**
     * Get custom metadata from DialogState.
     *
     * @param state DialogState
     * @param key Metadata key
     * @return Metadata value, or null if not found
     */
    public static String getCustomData(DialogState state, String key) {
        if (state == null || state.getCustomData() == null) {
            return null;
        }

        return state.getCustomData().get(key);
    }
}
