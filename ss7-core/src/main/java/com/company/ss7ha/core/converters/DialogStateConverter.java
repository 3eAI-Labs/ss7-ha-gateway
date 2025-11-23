package com.company.ss7ha.core.converters;

import com.company.ss7ha.core.redis.model.DialogState;
import com.company.ss7ha.core.redis.model.PendingInvoke;
import org.restcomm.protocols.ss7.map.api.MAPDialog;
import org.restcomm.protocols.ss7.sccp.parameter.SccpAddress;
import org.restcomm.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Converts Corsac JSS7 Dialog objects to license-safe DialogState.
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
     * Convert Corsac JSS7 Dialog to primitive DialogState.
     *
     * This method extracts all essential dialog information as primitive types:
     * - Dialog IDs (local and remote)
     * - Addresses (GT, SSN, PC as strings/integers)
     * - State, timing, application context
     *
     * NO JSS7 objects are included in the result!
     *
     * @param dialog Corsac JSS7 Dialog object
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
            if (dialog.getState() != null) {
                state.setState(dialog.getState().toString());
            } else {
                state.setState("UNKNOWN");
            }
            state.setCreatedAt(System.currentTimeMillis());
            state.setLastActivity(System.currentTimeMillis());

            // Network ID
            state.setNetworkId(dialog.getNetworkId());

            // Local addressing
            SccpAddress localAddress = dialog.getLocalAddress();
            if (localAddress != null) {
                if (localAddress.getGlobalTitle() != null) {
                    state.setLocalGT(localAddress.getGlobalTitle().getDigits());
                }
                state.setLocalSSN(localAddress.getSubsystemNumber());
                state.setLocalPC(localAddress.getSignalingPointCode());
            }

            // Remote addressing
            SccpAddress remoteAddress = dialog.getRemoteAddress();
            if (remoteAddress != null) {
                if (remoteAddress.getGlobalTitle() != null) {
                    state.setRemoteGT(remoteAddress.getGlobalTitle().getDigits());
                }
                state.setRemoteSSN(remoteAddress.getSubsystemNumber());
                state.setRemotePC(remoteAddress.getSignalingPointCode());
            }

            // Application context name
            if (dialog.getApplicationContextName() != null) {
                state.setApplicationContextName(dialog.getApplicationContextName().toString());
            }

            // Dialog timeout
            state.setDialogTimeout(dialog.getIdleTaskTimeout());

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
     * Convert Corsac JSS7 MAPDialog to primitive DialogState.
     *
     * This is an overload for MAP-specific dialogs.
     *
     * @param mapDialog Corsac JSS7 MAPDialog object
     * @return DialogState with primitive types only
     */
    public static DialogState fromDialog(MAPDialog mapDialog) {
        if (mapDialog == null) {
            logger.warn("Cannot convert null MAP dialog to DialogState");
            return null;
        }

        try {
            DialogState state = new DialogState();

            // Basic dialog identifiers
            state.setDialogId(mapDialog.getLocalDialogId());
            state.setRemoteDialogId(mapDialog.getRemoteDialogId());

            // Dialog state
            if (mapDialog.getState() != null) {
                state.setState(mapDialog.getState().toString());
            } else {
                state.setState("UNKNOWN");
            }
            state.setCreatedAt(System.currentTimeMillis());
            state.setLastActivity(System.currentTimeMillis());

            // Local addressing
            SccpAddress localAddress = mapDialog.getLocalAddress();
            if (localAddress != null) {
                if (localAddress.getGlobalTitle() != null) {
                    state.setLocalGT(localAddress.getGlobalTitle().getDigits());
                }
                state.setLocalSSN(localAddress.getSubsystemNumber());
                state.setLocalPC(localAddress.getSignalingPointCode());
            }

            // Remote addressing
            SccpAddress remoteAddress = mapDialog.getRemoteAddress();
            if (remoteAddress != null) {
                if (remoteAddress.getGlobalTitle() != null) {
                    state.setRemoteGT(remoteAddress.getGlobalTitle().getDigits());
                }
                state.setRemoteSSN(remoteAddress.getSubsystemNumber());
                state.setRemotePC(remoteAddress.getSignalingPointCode());
            }

            // Application context name
            if (mapDialog.getApplicationContext() != null) {
                state.setApplicationContextName(mapDialog.getApplicationContext().toString());
            }

            // Initialize empty collections
            state.setPendingInvokes(new ArrayList<>());
            state.setCustomData(new HashMap<>());

            logger.debug("Converted MAP dialog {} to DialogState (GT: {} -> {})",
                    state.getDialogId(),
                    state.getLocalGT(),
                    state.getRemoteGT());

            return state;

        } catch (Exception e) {
            logger.error("Failed to convert MAP dialog {} to DialogState",
                    mapDialog.getLocalDialogId(), e);
            return null;
        }
    }

    /**
     * Update existing DialogState from MAPDialog.
     *
     * @param state Existing DialogState
     * @param mapDialog Current Corsac JSS7 MAPDialog
     * @return Updated DialogState
     */
    public static DialogState updateFromDialog(DialogState state, MAPDialog mapDialog) {
        if (state == null || mapDialog == null) {
            logger.warn("Cannot update DialogState - state or dialog is null");
            return state;
        }

        try {
            // Update state
            if (mapDialog.getState() != null) {
                state.setState(mapDialog.getState().toString());
            }

            // Update activity timestamp
            state.setLastActivity(System.currentTimeMillis());

            // Update remote dialog ID if now available
            Long remoteDialogId = mapDialog.getRemoteDialogId();
            if (remoteDialogId != null && state.getRemoteDialogId() == null) {
                state.setRemoteDialogId(remoteDialogId);
            }

            // Update remote addressing if now available
            SccpAddress remoteAddress = mapDialog.getRemoteAddress();
            if (remoteAddress != null) {
                if (remoteAddress.getGlobalTitle() != null && state.getRemoteGT() == null) {
                    state.setRemoteGT(remoteAddress.getGlobalTitle().getDigits());
                }
                if (state.getRemoteSSN() == null) {
                    state.setRemoteSSN(remoteAddress.getSubsystemNumber());
                }
                if (state.getRemotePC() == null) {
                    state.setRemotePC(remoteAddress.getSignalingPointCode());
                }
            }

            logger.debug("Updated DialogState for MAP dialog {}", state.getDialogId());

            return state;

        } catch (Exception e) {
            logger.error("Failed to update DialogState from MAP dialog {}",
                    mapDialog.getLocalDialogId(), e);
            return state;
        }
    }

    /**
     * Update existing DialogState from Dialog.
     *
     * This updates state, timing, and other dynamic fields while preserving
     * pending invokes and custom data.
     *
     * @param state Existing DialogState
     * @param dialog Current Corsac JSS7 Dialog
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
            Long remoteDialogId = dialog.getRemoteDialogId();
            if (remoteDialogId != null && state.getRemoteDialogId() == null) {
                state.setRemoteDialogId(remoteDialogId);
            }

            // Update remote addressing if now available
            SccpAddress remoteAddress = dialog.getRemoteAddress();
            if (remoteAddress != null) {
                if (remoteAddress.getGlobalTitle() != null && state.getRemoteGT() == null) {
                    state.setRemoteGT(remoteAddress.getGlobalTitle().getDigits());
                }
                if (state.getRemoteSSN() == null) {
                    state.setRemoteSSN(remoteAddress.getSubsystemNumber());
                }
                if (state.getRemotePC() == null) {
                    state.setRemotePC(remoteAddress.getSignalingPointCode());
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
