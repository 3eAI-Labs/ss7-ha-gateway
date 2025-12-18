package com.company.ss7ha.core.listeners;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.handlers.CorrelationIdWrapper; // Added
import com.company.ss7ha.core.model.DialogState;
import com.company.ss7ha.core.store.DialogStore;
import com.company.ss7ha.messages.MapSmsMessage;
import com.company.ss7ha.messages.MapSriMessage;
import com.company.ss7ha.messages.SS7Message;
import com.company.ss7ha.nats.publisher.SS7NatsPublisher;
import org.restcomm.protocols.ss7.map.api.MAPDialog;
import org.restcomm.protocols.ss7.map.api.MAPMessage; // Retained
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

import org.restcomm.protocols.ss7.map.api.service.sms.LocationInfoWithLMSI;
import org.restcomm.protocols.ss7.commonapp.api.primitives.ISDNAddressString;
import org.restcomm.protocols.ss7.map.api.service.lsm.AdditionalNumber;

import java.io.Externalizable;

/**
 * MAP SMS Service Listener with NATS persistence and event integration.
 */
public class MapSmsServiceListener implements MAPServiceSmsListener {

    private static final Logger logger = LoggerFactory.getLogger(MapSmsServiceListener.class);

    private final DialogStore dialogStore;
    private final EventPublisher eventPublisher;
    private final SS7NatsPublisher natsPublisher;

    // Configuration
    private final boolean persistDialogs;
    private final boolean publishEvents;

    public MapSmsServiceListener(DialogStore dialogStore,
                                 EventPublisher eventPublisher,
                                 SS7NatsPublisher natsPublisher,
                                 boolean persistDialogs,
                                 boolean publishEvents) {
        this.dialogStore = dialogStore;
        this.eventPublisher = eventPublisher;
        this.natsPublisher = natsPublisher;
        this.persistDialogs = persistDialogs && (dialogStore != null);
        this.publishEvents = publishEvents;

        logger.info("MapSmsServiceListener initialized (persist: {}, events: {})",
                this.persistDialogs, this.publishEvents);
    }

    // MO-ForwardSM
    @Override
    public void onMoForwardShortMessageRequest(MoForwardShortMessageRequest request) {
        MAPDialogSms dialog = request.getMAPDialog();
        Long dialogId = dialog.getLocalDialogId();
        Integer invokeId = request.getInvokeId();

        logger.info("Received MO-ForwardSM request (dialog: {}, invoke: {})",
                dialogId, invokeId);

        try {
            if (persistDialogs) {
                persistDialogState(dialog, "MO_FORWARD_SM", invokeId);
            }

            if (publishEvents && eventPublisher != null) {
                java.util.Map<String, Object> eventData = new java.util.HashMap<>();
                eventData.put("type", "MO_FORWARD_SM");
                eventData.put("dialogId", dialogId);
                eventData.put("invokeId", invokeId);
                eventData.put("request", request);
                eventPublisher.publishEvent("sms.mo.incoming", eventData);
                logger.info("Published MO-ForwardSM event (dialog: {})", dialogId);
            }
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
            if (persistDialogs && dialogStore != null) {
                updateDialogAfterResponse(dialogId, invokeId);
            }
        } catch (Exception e) {
            logger.error("Error processing MO-ForwardSM response (dialog: {}, invoke: {})",
                    dialogId, invokeId, e);
        }
    }

    // MT-ForwardSM
    @Override
    public void onMtForwardShortMessageRequest(MtForwardShortMessageRequest request) {
        MAPDialogSms dialog = request.getMAPDialog();
        Long dialogId = dialog.getLocalDialogId();
        Integer invokeId = request.getInvokeId();

        logger.info("Received MT-ForwardSM request (dialog: {}, invoke: {})",
                dialogId, invokeId);

        try {
            if (persistDialogs && dialogStore != null) {
                persistDialogState(dialog, "MT_FORWARD_SM", invokeId);
            }

            // Loopback logic removed as per instruction.
            // The gateway will act as a standard SS7 node.

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
            if (persistDialogs && dialogStore != null) {
                updateDialogAfterResponse(dialogId, invokeId);
            }
        } catch (Exception e) {
            logger.error("Error processing MT-ForwardSM response (dialog: {}, invoke: {})",
                    dialogId, invokeId, e);
        }
    }

    // SRI-SM
    @Override
    public void onSendRoutingInfoForSMRequest(SendRoutingInfoForSMRequest request) {
        MAPDialogSms dialog = request.getMAPDialog();
        Long dialogId = dialog.getLocalDialogId();
        Integer invokeId = request.getInvokeId();

        logger.info("Received SRI-SM request (dialog: {}, invoke: {})",
                dialogId, invokeId);

        try {
            if (persistDialogs && dialogStore != null) {
                persistDialogState(dialog, "SRI_SM", invokeId);
            }
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
            if (persistDialogs && dialogStore != null) {
                updateDialogAfterResponse(dialogId, invokeId);
            }

            Externalizable userObject = dialog.getUserObject();
            String correlationId = null;
            if (userObject instanceof CorrelationIdWrapper) {
                correlationId = ((CorrelationIdWrapper) userObject).getCorrelationId();
            }

            if (correlationId == null) {
                logger.warn("No CorrelationId found in dialog user object for SRI-SM response (dialog: {})", dialogId);
                return;
            }

            String imsi = response.getIMSI() != null ? response.getIMSI().getData() : null;
            String msisdn = null;
            String mscAddress = null;
            String vlrAddress = null;
            Boolean subscriberAvailable = null;

            LocationInfoWithLMSI locationInfo = response.getLocationInfoWithLMSI();
            if (locationInfo != null) {
                ISDNAddressString networkNodeNumber = locationInfo.getNetworkNodeNumber();
                if (networkNodeNumber != null) {
                    if (!locationInfo.getGprsNodeIndicator()) {
                        mscAddress = networkNodeNumber.getAddress();
                    } else {
                        vlrAddress = networkNodeNumber.getAddress();
                    }
                }

                AdditionalNumber additionalNumber = locationInfo.getAdditionalNumber();
                if (additionalNumber != null) {
                    if (locationInfo.getGprsNodeIndicator() && additionalNumber.getMSCNumber() != null) {
                        mscAddress = additionalNumber.getMSCNumber().getAddress();
                    } else if (!locationInfo.getGprsNodeIndicator() && additionalNumber.getSGSNNumber() != null) {
                        vlrAddress = additionalNumber.getSGSNNumber().getAddress();
                    }
                }
                subscriberAvailable = true;
            }

            com.company.ss7ha.messages.MapSriMessage sriResponse = new com.company.ss7ha.messages.MapSriMessage();
            sriResponse.setCorrelationId(correlationId);
            sriResponse.setMsisdn(msisdn);
            sriResponse.setImsi(imsi);
            sriResponse.setMscAddress(mscAddress);
            sriResponse.setVlrAddress(vlrAddress);
            sriResponse.setSubscriberAvailable(subscriberAvailable);
            sriResponse.setSriType(com.company.ss7ha.messages.MapSriMessage.SriType.SRI_SM_RESPONSE);
            sriResponse.setDirection(com.company.ss7ha.messages.SS7Message.MessageDirection.INBOUND);
            
            if (natsPublisher != null) {
                natsPublisher.publishSriResponse(sriResponse);
                logger.info("Published SRI-SM response for {} [CorrId: {}] to NATS", msisdn, correlationId);
            } else {
                logger.error("NATS Publisher not available, cannot publish SRI-SM response for {}", msisdn);
            }

        } catch (Exception e) {
            logger.error("Error processing SRI-SM response (dialog: {}, invoke: {})",
                    dialogId, invokeId, e);
        }
    }

    // Dialog Events
    public void onDialogDelimiter(MAPDialog dialog) {
        logger.debug("Dialog delimiter (dialog: {})", dialog.getLocalDialogId());
    }

    public void onDialogRequest(MAPDialog dialog) {
        Long dialogId = dialog.getLocalDialogId();
        logger.info("Dialog request received (dialog: {})", dialogId);

        try {
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
            if (persistDialogs) {
                dialogStore.deleteDialog(dialogId);
            }
        } catch (Exception e) {
            logger.error("Error deleting dialog state on timeout (dialog: {})", dialogId, e);
        }
    }

    private void persistDialogState(MAPDialog dialog, String operationType, Integer invokeId) {
        try {
            Long dialogId = dialog.getLocalDialogId();
            DialogState state = dialogStore.loadDialog(dialogId);
            if (state == null) {
                state = createDialogState(dialog);
            }

            if (state != null) {
                state.putCustomData("lastOperation", operationType);
                state.putCustomData("invokeId", String.valueOf(invokeId));
                state.setDialogState("ACTIVE");
                dialogStore.updateDialog(state);
                logger.debug("Persisted dialog state (dialog: {}, operation: {}, invoke: {})", dialogId, operationType, invokeId);
            }
        } catch (Exception e) {
            logger.error("Failed to persist dialog state", e);
        }
    }

    private DialogState createDialogState(MAPDialog dialog) {
        DialogState state = new DialogState();
        state.setDialogId(dialog.getLocalDialogId());
        if (dialog.getRemoteDialogId() != null) {
            state.setRemoteDialogId(String.valueOf(dialog.getRemoteDialogId()));
        }
        state.setDialogState(dialog.getState() != null ? dialog.getState().toString() : "UNKNOWN");
        state.setServiceType("SMS");
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

    private void updateDialogAfterResponse(Long dialogId, Integer invokeId) {
        try {
            DialogState state = dialogStore.loadDialog(dialogId);
            if (state != null) {
                state.putCustomData("lastInvokeCompleted", String.valueOf(invokeId));
                dialogStore.updateDialog(state);
                logger.debug("Updated dialog state after response (dialog: {}, invoke: {})", dialogId, invokeId);
            }
        } catch (Exception e) {
            logger.error("Failed to update dialog state after response", e);
        }
    }

    @Override
    public void onErrorComponent(MAPDialog dialog, Integer invokeId, org.restcomm.protocols.ss7.map.api.errors.MAPErrorMessage mapErrorMessage) {
        logger.warn("Error component received (dialog: {}, invoke: {}, error: {})",
                dialog.getLocalDialogId(), invokeId,
                mapErrorMessage != null ? mapErrorMessage.getErrorCode() : "unknown");
    }

    @Override
    public void onRejectComponent(MAPDialog dialog, Integer invokeId, org.restcomm.protocols.ss7.tcap.asn.comp.Problem problem, boolean isLocalOriginated) {
        logger.warn("Reject component received (dialog: {}, invoke: {}, problem: {}, local: {})",
                dialog.getLocalDialogId(), invokeId, problem, isLocalOriginated);
    }

    @Override
    public void onInvokeTimeout(MAPDialog dialog, Integer invokeId) {
        logger.warn("Invoke timeout (dialog: {}, invoke: {})", dialog.getLocalDialogId(), invokeId);
        try {
            if (persistDialogs && dialogStore != null) {
                updateDialogAfterResponse(dialog.getLocalDialogId(), invokeId);
            }
        } catch (Exception e) {
            logger.error("Error handling invoke timeout", e);
        }
    }

    @Override
    public void onNoteSubscriberPresentRequest(org.restcomm.protocols.ss7.map.api.service.sms.NoteSubscriberPresentRequest request) {
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
    }
}
