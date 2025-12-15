package com.company.ss7ha.core.handlers;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.messages.MapSriMessage;
import com.company.ss7ha.messages.SS7Message;
import com.company.ss7ha.nats.subscriber.SS7NatsSubscriber;
import org.restcomm.protocols.ss7.map.api.MAPProvider;
import org.restcomm.protocols.ss7.map.api.MAPStack;
import org.restcomm.protocols.ss7.map.api.MAPDialog;
import org.restcomm.protocols.ss7.map.api.MAPException;
import org.restcomm.protocols.ss7.map.api.MAPParameterFactory;
import org.restcomm.protocols.ss7.map.api.service.sms.MAPDialogSms;
import org.restcomm.protocols.ss7.map.api.service.sms.SendRoutingInfoForSMRequest;
import org.restcomm.protocols.ss7.commonapp.api.primitives.ISDNAddressString;
import org.restcomm.protocols.ss7.commonapp.api.primitives.AddressNature;
import org.restcomm.protocols.ss7.commonapp.api.primitives.AddressString;
import org.restcomm.protocols.ss7.commonapp.api.primitives.NumberingPlan;
import org.restcomm.protocols.ss7.commonapp.api.primitives.MAPExtensionContainer;
import org.restcomm.protocols.ss7.commonapp.api.primitives.IMSI;
import org.restcomm.protocols.ss7.map.api.service.sms.SM_RP_MTI;
import org.restcomm.protocols.ss7.map.api.service.sms.SM_RP_SMEA;
import org.restcomm.protocols.ss7.map.api.service.sms.SMDeliveryNotIntended;
import org.restcomm.protocols.ss7.map.api.service.mobility.subscriberManagement.TeleserviceCode;
import org.restcomm.protocols.ss7.map.api.service.sms.CorrelationID;
import com.mobius.software.common.dal.timers.TaskCallback;
import org.restcomm.protocols.ss7.map.api.MAPApplicationContext;
import org.restcomm.protocols.ss7.map.api.MAPApplicationContextName;
import org.restcomm.protocols.ss7.map.api.MAPApplicationContextVersion;
import org.restcomm.protocols.ss7.sccp.parameter.SccpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Handles SRI-SM (SendRoutingInfoForSM) requests from NATS, creates MAP requests, and sends them over SS7.
 */
public class SriMessageHandler implements SS7NatsSubscriber.MessageHandler<MapSriMessage> {

    private static final Logger logger = LoggerFactory.getLogger(SriMessageHandler.class);

    private final MAPStack mapStack;
    private final EventPublisher eventPublisher;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    public SriMessageHandler(MAPStack mapStack, EventPublisher eventPublisher) {
        this.mapStack = mapStack;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void handle(MapSriMessage sriMessage) {
        logger.info("Handling SRI-SM request for MSISDN: {} [CorrId: {}]", sriMessage.getMsisdn(), sriMessage.getCorrelationId());

        try {
            String msisdn = sriMessage.getMsisdn();
            if (msisdn == null || msisdn.isEmpty()) {
                throw new IllegalArgumentException("MSISDN cannot be null or empty for SRI-SM request");
            }

            MAPProvider mapProvider = mapStack.getProvider();
            MAPParameterFactory mapParamFactory = mapProvider.getMAPParameterFactory();

            // Create MAPDialog
            MAPApplicationContext appCtx = MAPApplicationContext.getInstance(
                MAPApplicationContextName.shortMsgGatewayContext,
                MAPApplicationContextVersion.version2);

            // Dummy SCCP addresses
            SccpAddress origAddress = null;
            SccpAddress destAddress = null;
            AddressString origReference = null;
            AddressString destReference = null;
            int networkId = 1;

            MAPDialogSms mapDialog = mapProvider.getMAPServiceSms().createNewDialog(
                appCtx, origAddress, origReference, destAddress, destReference, networkId);
            
            ISDNAddressString destinationMsisdn = mapParamFactory.createISDNAddressString(
                AddressNature.international_number, NumberingPlan.ISDN, msisdn);

            // Construct other parameters for addSendRoutingInfoForSMRequest
            boolean smRpPri = false; 
            AddressString serviceCentreAddress = null; 
            MAPExtensionContainer extensionContainer = null;
            boolean gprsSupportIndicator = false;
            SM_RP_MTI smRpMti = null;
            SM_RP_SMEA smRpSmea = null;
            SMDeliveryNotIntended smDeliveryNotIntended = null;
            boolean ipSmGwGuidanceIndicator = false;
            IMSI imsi = null;
            boolean t4TriggerIndicator = false;
            boolean singleAttemptDelivery = false;
            TeleserviceCode teleservice = null;
            CorrelationID correlationID = null;

            // Add the SRI-SM request to the dialog
            mapDialog.addSendRoutingInfoForSMRequest(destinationMsisdn, smRpPri, serviceCentreAddress, 
                                                     extensionContainer, gprsSupportIndicator, smRpMti, 
                                                     smRpSmea, smDeliveryNotIntended, ipSmGwGuidanceIndicator, 
                                                     imsi, t4TriggerIndicator, singleAttemptDelivery, 
                                                     teleservice, correlationID);
            
            // Set correlation ID for later response handling using wrapper
            mapDialog.setUserObject(new CorrelationIdWrapper(sriMessage.getCorrelationId()));

            // Send the dialog
            mapDialog.send(new TaskCallback<Exception>() {
                @Override
                public void onSuccess() {
                    logger.info("MAP dialog for MSISDN {} sent successfully.", msisdn);
                }

                @Override
                public void onError(Exception e) {
                    logger.error("Error sending MAP dialog for MSISDN {}: {}", msisdn, e.getMessage(), e);
                    publishFailureResponse(sriMessage.getCorrelationId(), sriMessage.getMessageId(), e.getMessage());
                }
            });

            logger.info("Sent SRI-SM request for MSISDN: {} over SS7. DialogId: {} [CorrId: {}]",
                msisdn, mapDialog.getLocalDialogId(), sriMessage.getCorrelationId());

        } catch (MAPException | IllegalArgumentException e) {
            logger.error("Error sending SRI-SM request for MSISDN: {}", sriMessage.getMsisdn(), e);
            publishFailureResponse(sriMessage.getCorrelationId(), sriMessage.getMessageId(), e.getMessage());
        }
    }

    private void publishFailureResponse(String correlationId, String originalMessageId, String errorMessage) {
        MapSriMessage response = new MapSriMessage();
        response.setCorrelationId(correlationId);
        response.setMessageId(originalMessageId);
        response.setSriType(MapSriMessage.SriType.SRI_SM_RESPONSE);
        response.setErrorCode(-1); // Generic error code
        response.setErrorMessage(errorMessage);
        response.setDirection(SS7Message.MessageDirection.INBOUND); // Response to SMSC

        eventPublisher.publishEvent("map.sri.response", response);
        logger.error("Published SRI failure response for message {}: {}", originalMessageId, errorMessage);
    }
}
