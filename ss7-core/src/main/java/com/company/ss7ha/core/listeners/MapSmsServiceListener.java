package com.company.ss7ha.core.listeners;

import com.company.ss7ha.core.converters.DialogStateConverter;
import com.company.ss7ha.core.redis.RedisDialogStore;
import com.company.ss7ha.core.redis.model.DialogState;
import com.company.ss7ha.kafka.converters.MoForwardSmConverter;
import com.company.ss7ha.kafka.messages.MoSmsMessage;
import com.company.ss7ha.kafka.producer.SS7KafkaProducer;
import com.mobius.software.telco.protocols.ss7.map.api.MAPDialog;
import com.mobius.software.telco.protocols.ss7.map.api.MAPMessage;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.MAPDialogSms;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.MAPServiceSmsListener;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.MoForwardShortMessageRequest;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.MoForwardShortMessageResponse;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.MtForwardShortMessageRequest;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.MtForwardShortMessageResponse;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.SendRoutingInfoForSMRequest;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.SendRoutingInfoForSMResponse;
import com.mobius.software.telco.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MAP SMS Service Listener with Redis persistence and Kafka integration.
 *
 * This listener:
 * 1. Receives MAP SMS operations (MO-ForwardSM, MT-ForwardSM, SRI-SM)
 * 2. Stores dialog state in Redis for failover
 * 3. Converts to JSON and publishes to Kafka
 *
 * CRITICAL: This maintains the AGPL firewall by:
 * - Extracting primitive data to DialogState (Redis)
 * - Converting to JSON messages (Kafka)
 * - NO JSS7 objects cross the Kafka boundary!
 *
 * LICENSE: AGPL-3.0 (part of ss7-ha-gateway)
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public class MapSmsServiceListener implements MAPServiceSmsListener {

    private static final Logger logger = LoggerFactory.getLogger(MapSmsServiceListener.class);

    private final RedisDialogStore dialogStore;
    private final SS7KafkaProducer kafkaProducer;

    // Configuration
    private final boolean persistDialogs;
    private final boolean publishToKafka;
    private final int dialogTTL;

    /**
     * Constructor with Redis and Kafka integration.
     *
     * @param dialogStore Redis dialog store
     * @param kafkaProducer Kafka producer
     * @param persistDialogs Enable dialog persistence
     * @param publishToKafka Enable Kafka publishing
     * @param dialogTTL Dialog TTL in seconds
     */
    public MapSmsServiceListener(RedisDialogStore dialogStore,
                                 SS7KafkaProducer kafkaProducer,
                                 boolean persistDialogs,
                                 boolean publishToKafka,
                                 int dialogTTL) {
        this.dialogStore = dialogStore;
        this.kafkaProducer = kafkaProducer;
        this.persistDialogs = persistDialogs;
        this.publishToKafka = publishToKafka;
        this.dialogTTL = dialogTTL;

        logger.info("MapSmsServiceListener initialized (persist: {}, kafka: {}, TTL: {}s)",
                persistDialogs, publishToKafka, dialogTTL);
    }

    //
    // MO-ForwardSM (Mobile Originated SMS)
    //

    @Override
    public void onMoForwardShortMessageRequest(MoForwardShortMessageRequest request) {
        MAPDialogSms dialog = request.getMAPDialog();
        Long dialogId = dialog.getLocalDialogId();
        Integer invokeId = request.getInvokeId().intValue();

        logger.info("Received MO-ForwardSM request (dialog: {}, invoke: {})",
                dialogId, invokeId);

        try {
            // 1. Persist dialog state to Redis
            if (persistDialogs && dialogStore != null) {
                persistDialogState(dialog, "MO_FORWARD_SM", invokeId);
            }

            // 2. Convert to JSON and publish to Kafka
            if (publishToKafka && kafkaProducer != null) {
                MoSmsMessage jsonMessage = MoForwardSmConverter.convert(
                        request, dialogId, invokeId);

                if (jsonMessage != null) {
                    // Publish to Kafka topic: sms.mo.incoming
                    kafkaProducer.sendMessage("sms.mo.incoming", jsonMessage);

                    logger.info("Published MO-ForwardSM to Kafka (message: {}, dialog: {})",
                            jsonMessage.getMessageId(), dialogId);
                } else {
                    logger.error("Failed to convert MO-ForwardSM to JSON (dialog: {})", dialogId);
                }
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
        Integer invokeId = response.getInvokeId().intValue();

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
        Integer invokeId = request.getInvokeId().intValue();

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
        Integer invokeId = response.getInvokeId().intValue();

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
        Integer invokeId = request.getInvokeId().intValue();

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
        Integer invokeId = response.getInvokeId().intValue();

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

    @Override
    public void onDialogDelimiter(MAPDialog dialog) {
        logger.debug("Dialog delimiter (dialog: {})", dialog.getLocalDialogId());
    }

    @Override
    public void onDialogRequest(MAPDialog dialog) {
        Long dialogId = dialog.getLocalDialogId();
        logger.info("Dialog request received (dialog: {})", dialogId);

        try {
            // Create initial dialog state
            if (persistDialogs && dialogStore != null) {
                DialogState state = DialogStateConverter.fromDialog(dialog);
                if (state != null) {
                    dialogStore.storeDialog(state);
                    logger.debug("Stored initial dialog state (dialog: {})", dialogId);
                }
            }
        } catch (Exception e) {
            logger.error("Error storing initial dialog state (dialog: {})", dialogId, e);
        }
    }

    @Override
    public void onDialogAccept(MAPDialog dialog) {
        Long dialogId = dialog.getLocalDialogId();
        logger.info("Dialog accepted (dialog: {})", dialogId);

        try {
            // Update dialog state
            if (persistDialogs && dialogStore != null) {
                DialogState state = dialogStore.loadDialog(dialogId);
                if (state != null) {
                    DialogStateConverter.updateFromDialog(state, dialog);
                    dialogStore.updateDialog(state);
                }
            }
        } catch (Exception e) {
            logger.error("Error updating dialog state on accept (dialog: {})", dialogId, e);
        }
    }

    @Override
    public void onDialogClose(MAPDialog dialog) {
        Long dialogId = dialog.getLocalDialogId();
        logger.info("Dialog closed (dialog: {})", dialogId);

        try {
            // Delete dialog state from Redis
            if (persistDialogs && dialogStore != null) {
                dialogStore.deleteDialog(dialogId);
                logger.debug("Deleted dialog state (dialog: {})", dialogId);
            }
        } catch (Exception e) {
            logger.error("Error deleting dialog state (dialog: {})", dialogId, e);
        }
    }

    @Override
    public void onDialogAbort(MAPDialog dialog) {
        Long dialogId = dialog.getLocalDialogId();
        logger.warn("Dialog aborted (dialog: {})", dialogId);

        try {
            // Delete dialog state
            if (persistDialogs && dialogStore != null) {
                dialogStore.deleteDialog(dialogId);
            }
        } catch (Exception e) {
            logger.error("Error deleting dialog state on abort (dialog: {})", dialogId, e);
        }
    }

    @Override
    public void onDialogTimeout(MAPDialog dialog) {
        Long dialogId = dialog.getLocalDialogId();
        logger.warn("Dialog timeout (dialog: {})", dialogId);

        try {
            // Delete dialog state
            if (persistDialogs && dialogStore != null) {
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
     * Persist dialog state to Redis.
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
                state = DialogStateConverter.fromDialog(dialog);
            } else {
                DialogStateConverter.updateFromDialog(state, dialog);
            }

            if (state != null) {
                // Add pending invoke
                DialogStateConverter.addPendingInvoke(
                        state,
                        invokeId.longValue(),
                        operationType,
                        30000  // 30 second timeout
                );

                // Store/update in Redis
                dialogStore.updateDialog(state);

                logger.debug("Persisted dialog state (dialog: {}, operation: {}, invoke: {})",
                        dialogId, operationType, invokeId);
            }
        } catch (Exception e) {
            logger.error("Failed to persist dialog state", e);
        }
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
                // Remove pending invoke
                DialogStateConverter.removePendingInvoke(state, invokeId.longValue());

                // Update in Redis
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
    public void onErrorComponent(MAPDialog dialog, Long invokeId) {
        logger.warn("Error component received (dialog: {}, invoke: {})",
                dialog.getLocalDialogId(), invokeId);
    }

    @Override
    public void onRejectComponent(MAPDialog dialog, Long invokeId) {
        logger.warn("Reject component received (dialog: {}, invoke: {})",
                dialog.getLocalDialogId(), invokeId);
    }

    @Override
    public void onInvokeTimeout(MAPDialog dialog, Long invokeId) {
        logger.warn("Invoke timeout (dialog: {}, invoke: {})",
                dialog.getLocalDialogId(), invokeId);

        try {
            // Remove timed out invoke
            if (persistDialogs && dialogStore != null) {
                updateDialogAfterResponse(dialog.getLocalDialogId(), invokeId.intValue());
            }
        } catch (Exception e) {
            logger.error("Error handling invoke timeout", e);
        }
    }

    @Override
    public void onMAPMessage(MAPMessage message) {
        // Generic message handler - not used
    }
}
