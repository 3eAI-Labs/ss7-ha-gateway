package com.company.ss7ha.core.handlers;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.messages.MapSriMessage;
import com.company.ss7ha.messages.SS7Message;
import com.company.ss7ha.nats.subscriber.SS7NatsSubscriber;
import org.restcomm.protocols.ss7.map.api.MAPProvider;
import org.restcomm.protocols.ss7.map.api.MAPStack;
import org.restcomm.protocols.ss7.map.api.service.sms.MAPDialogSms;
import org.restcomm.protocols.ss7.map.api.service.sms.SendRoutingInfoForSMRequest;
import org.restcomm.protocols.ss7.map.api.primitives.ISDNAddressString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles SRI-SM (SendRoutingInfoForSM) requests from NATS, creates MAP requests, and sends them over SS7.
 */
public class SriMessageHandler implements SS7NatsSubscriber.MessageHandler<MapSriMessage> {

    private static final Logger logger = LoggerFactory.getLogger(SriMessageHandler.class);

    private final MAPStack mapStack;
    private final EventPublisher eventPublisher;

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
            MAPDialogSms mapDialog = mapProvider.getMAPServiceSms().createNewDialog(
                MAPDialog.MAPDialogueType.ASYNCHRONOUS_CLIENT_SRI_SM);
            
            SendRoutingInfoForSMRequest sriRequest = mapDialog.newSendRoutingInfoForSMRequest();
            ISDNAddressString destinationMsisdn = mapProvider.getMAPParameterFactory().createISDNAddressString(
                msisdn, ISDNAddressString.AddressNature.INTERNATIONAL_NUMBER, ISDNAddressString.NumberingPlan.ISDN);
            sriRequest.setMsisdn(destinationMsisdn);

            // Set correlation ID for later response handling
            mapDialog.setUserObject(sriMessage.getCorrelationId()); // Store NATS correlation ID in MAP Dialog User Object

            mapDialog.add(sriRequest);
            mapDialog.send();

            logger.info("Sent SRI-SM request for MSISDN: {} over SS7. DialogId: {} [CorrId: {}]",
                msisdn, mapDialog.getLocalDialogId(), sriMessage.getCorrelationId());

        } catch (Exception e) {
            logger.error("Error sending SRI-SM request for MSISDN: {}", sriMessage.getMsisdn(), e);
            // Publish failure response to NATS
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
