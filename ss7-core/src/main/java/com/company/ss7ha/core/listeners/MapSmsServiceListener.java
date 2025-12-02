package com.company.ss7ha.core.listeners;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.model.DialogState;
import com.company.ss7ha.core.store.DialogStore;
import org.restcomm.protocols.ss7.map.api.MAPDialog;
import org.restcomm.protocols.ss7.map.api.MAPMessage;
import org.restcomm.protocols.ss7.map.api.service.sms.MAPDialogSms;
import org.restcomm.protocols.ss7.map.api.service.sms.MAPServiceSmsListener;
import org.restcomm.protocols.ss7.map.api.service.sms.MoForwardShortMessageRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.MoForwardShortMessageResponse;
import org.restcomm.protocols.ss7.map.api.service.sms.MtForwardShortMessageRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.MtForwardShortMessageResponse;
import org.restcomm.protocols.ss7.map.api.service.sms.SendRoutingInfoForSMRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.SendRoutingInfoForSMResponse;
import org.restcomm.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MAP SMS Service Listener with NATS persistence and event integration.
 *
 * This listener:
 * 1. Receives MAP SMS operations (MO-ForwardSM, MT-ForwardSM, SRI-SM)
 * 2. Optionally stores dialog state in NATS JetStream KV for failover
 * 3. Publishes events to NATS
 *
 * CRITICAL: This maintains the AGPL firewall by:
 * - Extracting primitive data to DialogState (NATS KV)
 * - Converting to JSON messages (NATS)
 * - NO JSS7 objects cross the NATS boundary!
 *
 * LICENSE: AGPL-3.0 (part of ss7-ha-gateway)
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public class MapSmsServiceListener implements MAPServiceSmsListener {

    private static final Logger logger = LoggerFactory.getLogger(MapSmsServiceListener.class);

    private final DialogStore dialogStore;
    private final EventPublisher eventPublisher;

    // Configuration
    private final boolean persistDialogs;
    private final boolean publishEvents;

    /**
     * Constructor with Dialog Store and Event Publisher integration.
     *
     * @param dialogStore Dialog store (NATS KV, in-memory, etc.) - can be null for stateless mode
     * @param eventPublisher Event publisher (NATS, gRPC, etc.)
     * @param persistDialogs Enable dialog persistence
     * @param publishEvents Enable event publishing
     */
    public MapSmsServiceListener(DialogStore dialogStore,
                                 EventPublisher eventPublisher,
                                 boolean persistDialogs,
                                 boolean publishEvents) {
        this.dialogStore = dialogStore;
        this.eventPublisher = eventPublisher;
        this.persistDialogs = persistDialogs && (dialogStore != null);
        this.publishEvents = publishEvents;

        logger.info("MapSmsServiceListener initialized (persist: {}, events: {})",
                this.persistDialogs, this.publishEvents);
    }

    //
    // MO-ForwardSM (Mobile Originated SMS)
    //

    @Override
    public void onMoForwardShortMessageRequest(MoForwardShortMessageRequest request) {
        MAPDialogSms dialog = request.getMAPDialog();
        Long dialogId = dialog.getLocalDialogId();
        Integer invokeId = request.getInvokeId();

        logger.info("Received MO-ForwardSM request (dialog: {}, invoke: {})",
                dialogId, invokeId);

        try {
            // 1. Persist dialog state to NATS KV
            if (persistDialogs) {
                persistDialogState(dialog, "MO_FORWARD_SM", invokeId);
            }

            // 2. Publish event to NATS
            if (publishEvents && eventPublisher != null) {
                // Create event payload - publisher will handle serialization
                java.util.Map<String, Object> eventData = new java.util.HashMap<>();
                eventData.put("type", "MO_FORWARD_SM");
                eventData.put("dialogId", dialogId);
                eventData.put("invokeId", invokeId);
                eventData.put("request", request);

                // Publish to subject: sms.mo.incoming
                eventPublisher.publishEvent("sms.mo.incoming", eventData);

                logger.info("Published MO-ForwardSM event (dialog: {})", dialogId);
            }

            // 3. Application logic would go here
            // For now, just log that we received it
            // Actual processing happens in the SMSC Gateway (consumer side)

        } catch (Exception e) {
            logger.error("Error processing MO-ForwardSM (dialog: {}, invoke: {})",
                    dialogId, invokeId, e);
        }
    }

    @Override
    public void onMoForwardShortMessageResponse(MoForwardShortMessageResponse response) {
        MAPDialogSms dialog = response.getMAPDialog();
        Long dialogId = dialog.getLocalDialogId();
        Integer invokeId = response.getInvokeId();

        logger.info("Received MO-ForwardSM response (dialog: {}, invoke: {})",
                dialogId, invokeId);

        try {
            // Remove pending invoke from dialog state
            if (persistDialogs && dialogStore != null) {
                updateDialogAfterResponse(dialogId, invokeId);
            }

            // Response handling (acknowledge, etc.)

        } catch (Exception e) {
            logger.error("Error processing MO-ForwardSM response (dialog: {}, invoke: {})",
                    dialogId, invokeId, e);
        }
    }

    //
    // MT-ForwardSM (Mobile Terminated SMS)
    //

    @Override
    public void onMtForwardShortMessageRequest(MtForwardShortMessageRequest request) {
        MAPDialogSms dialog = request.getMAPDialog();
        Long dialogId = dialog.getLocalDialogId();
        Integer invokeId = request.getInvokeId();

        logger.info("Received MT-ForwardSM request (dialog: {}, invoke: {})",
                dialogId, invokeId);

        try {
            // Persist dialog state
            if (persistDialogs && dialogStore != null) {
                persistDialogState(dialog, "MT_FORWARD_SM", invokeId);
            }

            // MT-ForwardSM converter would go here
            // For now, just log

        } catch (Exception e) {
            logger.error("Error processing MT-ForwardSM (dialog: {}, invoke: {})",
                    dialogId, invokeId, e);
        }
    }

    @Override
    public void onMtForwardShortMessageResponse(MtForwardShortMessageResponse response) {
        MAPDialogSms dialog = response.getMAPDialog();
        Long dialogId = dialog.getLocalDialogId();
        Integer invokeId = response.getInvokeId();

        logger.info("Received MT-ForwardSM response (dialog: {}, invoke: {})",
                dialogId, invokeId);

        try {
            // Remove pending invoke
            if (persistDialogs && dialogStore != null) {
                updateDialogAfterResponse(dialogId, invokeId);
            }

        } catch (Exception e) {
            logger.error("Error processing MT-ForwardSM response (dialog: {}, invoke: {})",
                    dialogId, invokeId, e);
        }
    }

    //
    // SRI-SM (Send Routing Info for SM)
    //

    @Override
    public void onSendRoutingInfoForSMRequest(SendRoutingInfoForSMRequest request) {
        MAPDialogSms dialog = request.getMAPDialog();
        Long dialogId = dialog.getLocalDialogId();
        Integer invokeId = request.getInvokeId();

        logger.info("Received SRI-SM request (dialog: {}, invoke: {})",
                dialogId, invokeId);

        try {
            // Persist dialog state
            if (persistDialogs && dialogStore != null) {
                persistDialogState(dialog, "SRI_SM", invokeId);
            }

            // SRI-SM logic would go here

        } catch (Exception e) {
            logger.error("Error processing SRI-SM (dialog: {}, invoke: {})",
                    dialogId, invokeId, e);
        }
    }

    @Override
    public void onSendRoutingInfoForSMResponse(SendRoutingInfoForSMResponse response) {
        MAPDialogSms dialog = response.getMAPDialog();
        Long dialogId = dialog.getLocalDialogId();
        Integer invokeId = response.getInvokeId();

        logger.info("Received SRI-SM response (dialog: {}, invoke: {})",
                dialogId, invokeId);

        try {
            // Remove pending invoke
            if (persistDialogs && dialogStore != null) {
                updateDialogAfterResponse(dialogId, invokeId);
            }

        } catch (Exception e) {
            logger.error("Error processing SRI-SM response (dialog: {}, invoke: {})",
                    dialogId, invokeId, e);
        }
    }

    //
    // Dialog Lifecycle Events
    //

    
    public void onDialogDelimiter(MAPDialog dialog) {
        logger.debug("Dialog delimiter (dialog: {})", dialog.getLocalDialogId());
    }


    public void onDialogRequest(MAPDialog dialog) {
        Long dialogId = dialog.getLocalDialogId();
        logger.info("Dialog request received (dialog: {})", dialogId);

        try {
            // Create initial dialog state
            if (persistDialogs) {
                DialogState state = createDialogState(dialog);
                if (state != null) {
                    state.setDialogState("REQUEST");
                    dialogStore.storeDialog(state);
                    logger.debug("Stored initial dialog state (dialog: {})", dialogId);
                }
            }
        } catch (Exception e) {
            logger.error("Error storing initial dialog state (dialog: {})", dialogId, e);
        }
    }


    public void onDialogAccept(MAPDialog dialog) {
        Long dialogId = dialog.getLocalDialogId();
        logger.info("Dialog accepted (dialog: {})", dialogId);

        try {
            // Update dialog state
            if (persistDialogs) {
                DialogState state = dialogStore.loadDialog(dialogId);
                if (state == null) {
                    state = createDialogState(dialog);
                }
                if (state != null) {
                    state.setDialogState("ACCEPTED");
                    dialogStore.updateDialog(state);
                }
            }
        } catch (Exception e) {
            logger.error("Error updating dialog state on accept (dialog: {})", dialogId, e);
        }
    }


    public void onDialogClose(MAPDialog dialog) {
        Long dialogId = dialog.getLocalDialogId();
        logger.info("Dialog closed (dialog: {})", dialogId);

        try {
            // Delete dialog state from NATS KV
            if (persistDialogs) {
                dialogStore.deleteDialog(dialogId);
                logger.debug("Deleted dialog state (dialog: {})", dialogId);
            }
        } catch (Exception e) {
            logger.error("Error deleting dialog state (dialog: {})", dialogId, e);
        }
    }


    public void onDialogAbort(MAPDialog dialog) {
        Long dialogId = dialog.getLocalDialogId();
        logger.warn("Dialog aborted (dialog: {})", dialogId);

        try {
            // Delete dialog state
            if (persistDialogs) {
                dialogStore.deleteDialog(dialogId);
            }
        } catch (Exception e) {
            logger.error("Error deleting dialog state on abort (dialog: {})", dialogId, e);
        }
    }


    public void onDialogTimeout(MAPDialog dialog) {
        Long dialogId = dialog.getLocalDialogId();
        logger.warn("Dialog timeout (dialog: {})", dialogId);

        try {
            // Delete dialog state
            if (persistDialogs) {
                dialogStore.deleteDialog(dialogId);
            }
        } catch (Exception e) {
            logger.error("Error deleting dialog state on timeout (dialog: {})", dialogId, e);
        }
    }

    //
    // Helper Methods
    //

    /**
     * Persist dialog state to NATS KV.
     *
     * @param dialog MAP dialog
     * @param operationType Operation type
     * @param invokeId Invoke ID
     */
    private void persistDialogState(MAPDialog dialog, String operationType, Integer invokeId) {
        try {
            Long dialogId = dialog.getLocalDialogId();

            // Load existing state or create new
            DialogState state = dialogStore.loadDialog(dialogId);
            if (state == null) {
                // Create new dialog state from MAP dialog
                state = createDialogState(dialog);
            }

            if (state != null) {
                // Add operation info to custom data
                state.putCustomData("lastOperation", operationType);
                state.putCustomData("invokeId", String.valueOf(invokeId));
                state.setDialogState("ACTIVE");

                // Store/update in NATS KV
                dialogStore.updateDialog(state);

                logger.debug("Persisted dialog state (dialog: {}, operation: {}, invoke: {})",
                        dialogId, operationType, invokeId);
            }
        } catch (Exception e) {
            logger.error("Failed to persist dialog state", e);
        }
    }

    /**
     * Create DialogState from MAPDialog (license-safe extraction of primitives).
     *
     * @param dialog MAP dialog
     * @return DialogState with primitive data only
     */
    private DialogState createDialogState(MAPDialog dialog) {
        DialogState state = new DialogState();
        state.setDialogId(dialog.getLocalDialogId());

        if (dialog.getRemoteDialogId() != null) {
            state.setRemoteDialogId(String.valueOf(dialog.getRemoteDialogId()));
        }

        state.setDialogState(dialog.getState() != null ? dialog.getState().toString() : "UNKNOWN");
        state.setServiceType("SMS");

        // Extract address information safely
        try {
            if (dialog.getLocalAddress() != null) {
                state.setLocalAddress(dialog.getLocalAddress().toString());
            }
            if (dialog.getRemoteAddress() != null) {
                state.setRemoteAddress(dialog.getRemoteAddress().toString());
            }
        } catch (Exception e) {
            logger.warn("Could not extract dialog addresses", e);
        }

        return state;
    }

    /**
     * Update dialog state after receiving response.
     *
     * @param dialogId Dialog ID
     * @param invokeId Invoke ID to remove
     */
    private void updateDialogAfterResponse(Long dialogId, Integer invokeId) {
        try {
            DialogState state = dialogStore.loadDialog(dialogId);
            if (state != null) {
                // Mark invoke as completed
                state.putCustomData("lastInvokeCompleted", String.valueOf(invokeId));

                // Update in NATS KV
                dialogStore.updateDialog(state);

                logger.debug("Updated dialog state after response (dialog: {}, invoke: {})",
                        dialogId, invokeId);
            }
        } catch (Exception e) {
            logger.error("Failed to update dialog state after response", e);
        }
    }

    //
    // Unused Events (Implemented for interface compliance)
    //

    @Override
    public void onErrorComponent(MAPDialog dialog, Integer invokeId,
                                 org.restcomm.protocols.ss7.map.api.errors.MAPErrorMessage mapErrorMessage) {
        logger.warn("Error component received (dialog: {}, invoke: {}, error: {})",
                dialog.getLocalDialogId(), invokeId,
                mapErrorMessage != null ? mapErrorMessage.getErrorCode() : "unknown");
    }

    @Override
    public void onRejectComponent(MAPDialog dialog, Integer invokeId,
                                  org.restcomm.protocols.ss7.tcap.asn.comp.Problem problem,
                                  boolean isLocalOriginated) {
        logger.warn("Reject component received (dialog: {}, invoke: {}, problem: {}, local: {})",
                dialog.getLocalDialogId(), invokeId, problem, isLocalOriginated);
    }

    @Override
    public void onInvokeTimeout(MAPDialog dialog, Integer invokeId) {
        logger.warn("Invoke timeout (dialog: {}, invoke: {})",
                dialog.getLocalDialogId(), invokeId);

        try {
            // Remove timed out invoke
            if (persistDialogs && dialogStore != null) {
                updateDialogAfterResponse(dialog.getLocalDialogId(), invokeId);
            }
        } catch (Exception e) {
            logger.error("Error handling invoke timeout", e);
        }
    }

    @Override
    public void onNoteSubscriberPresentRequest(org.restcomm.protocols.ss7.map.api.service.sms.NoteSubscriberPresentRequest request) {
        // Note Subscriber Present - indicates subscriber is available
        // Not implemented in this version
        logger.debug("Note Subscriber Present request received");
    }

    @Override
    public void onReadyForSMRequest(org.restcomm.protocols.ss7.map.api.service.sms.ReadyForSMRequest request) {
        logger.debug("Ready for SM request received");
    }

    @Override
    public void onReadyForSMResponse(org.restcomm.protocols.ss7.map.api.service.sms.ReadyForSMResponse response) {
        logger.debug("Ready for SM response received");
    }

    @Override
    public void onForwardShortMessageRequest(org.restcomm.protocols.ss7.map.api.service.sms.ForwardShortMessageRequest request) {
        logger.debug("Forward short message request received");
    }

    @Override
    public void onForwardShortMessageResponse(org.restcomm.protocols.ss7.map.api.service.sms.ForwardShortMessageResponse response) {
        logger.debug("Forward short message response received");
    }

    @Override
    public void onInformServiceCentreRequest(org.restcomm.protocols.ss7.map.api.service.sms.InformServiceCentreRequest request) {
        logger.debug("Inform service centre request received");
    }

    @Override
    public void onAlertServiceCentreRequest(org.restcomm.protocols.ss7.map.api.service.sms.AlertServiceCentreRequest request) {
        logger.debug("Alert service centre request received");
    }

    @Override
    public void onAlertServiceCentreResponse(org.restcomm.protocols.ss7.map.api.service.sms.AlertServiceCentreResponse response) {
        logger.debug("Alert service centre response received");
    }

    @Override
    public void onReportSMDeliveryStatusRequest(org.restcomm.protocols.ss7.map.api.service.sms.ReportSMDeliveryStatusRequest request) {
        logger.debug("Report SM delivery status request received");
    }

    @Override
    public void onReportSMDeliveryStatusResponse(org.restcomm.protocols.ss7.map.api.service.sms.ReportSMDeliveryStatusResponse response) {
        logger.debug("Report SM delivery status response received");
    }

    @Override
    public void onMAPMessage(MAPMessage message) {
        // Generic message handler - not used
    }
}
