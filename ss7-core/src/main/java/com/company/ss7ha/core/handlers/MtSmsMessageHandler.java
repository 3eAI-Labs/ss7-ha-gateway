package com.company.ss7ha.core.handlers;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.utils.Gsm7BitCharset; // Added import for Gsm7BitCharset
import com.company.ss7ha.messages.MapSmsMessage;
import com.company.ss7ha.messages.SS7Message;
import com.company.ss7ha.messages.MapSmsMessage.SmsType; // Added import for SmsType
import com.company.ss7ha.nats.subscriber.SS7NatsSubscriber;
import org.restcomm.protocols.ss7.map.api.MAPProvider;
import org.restcomm.protocols.ss7.map.api.MAPStack;
import org.restcomm.protocols.ss7.map.api.service.sms.MAPDialogSms;
import org.restcomm.protocols.ss7.map.api.service.sms.MtForwardShortMessageRequest;
import org.restcomm.protocols.ss7.map.api.primitives.AlertServiceCentreInd;
import org.restcomm.protocols.ss7.map.api.primitives.AddressString;
import org.restcomm.protocols.ss7.map.api.primitives.ISDNAddressString;
import org.restcomm.protocols.ss7.map.api.service.supplementary.MAPServiceSupplementary;
import org.restcomm.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream; // Added import
import java.io.IOException;
import java.util.Base64;

public class MtSmsMessageHandler implements SS7NatsSubscriber.MessageHandler<MapSmsMessage> {

    private static final Logger logger = LoggerFactory.getLogger(MtSmsMessageHandler.class);

    private final MAPStack mapStack;
    private final EventPublisher eventPublisher;

    public MtSmsMessageHandler(MAPStack mapStack, EventPublisher eventPublisher) {
        this.mapStack = mapStack;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void handle(MapSmsMessage mtSmsMessage) {
        logger.info("Handling MT SMS message: {}", mtSmsMessage.getMessageId());

        try {
            // 1. Base64 Decode the PDU content
            byte[] tpdu = null;
            if (mtSmsMessage.getEncoding() == MapSmsMessage.SmsEncoding.BINARY) {
                tpdu = Base64.getDecoder().decode(mtSmsMessage.getContent());
                logger.debug("Decoded binary PDU for message: {}", mtSmsMessage.getMessageId());
            } else {
                // For now, assume if not binary, it's text, and we need to encode it into a simple TP-DU
                // This would involve a TP-DU encoder here as well, but for strict compliance with our
                // cloud-native-smsc project, we expect BINARY.
                logger.warn("Received non-binary MT SMS content for message {}. Expecting BINARY PDU. Attempting to create simple PDU.", mtSmsMessage.getMessageId());
                // Fallback: Create a very basic TP-DU (SMS-DELIVER) for plain text
                // This is a simplified fallback and not fully compliant.
                tpdu = createSimpleTextTpdu(mtSmsMessage.getRecipient(), mtSmsMessage.getContent());
            }

            // 2. Extract recipient (MSISDN)
            String recipientMsisdn = mtSmsMessage.getRecipient();
            if (recipientMsisdn == null || recipientMsisdn.isEmpty()) {
                throw new IllegalArgumentException("Recipient MSISDN cannot be null or empty for MT SMS");
            }

            // 3. Create MAPDialog
            MAPProvider mapProvider = mapStack.getProvider();
            MAPDialogSms mapDialog = mapProvider.getMAPServiceSms().createNewDialog(
                MAPDialog.MAPDialogueType.ASYNCHRONOUS_CLIENT_MT_SMS);
            
            // Set remote address (MSC/VLR GT) from networkInfo
            // For now, hardcode or get from config if not in message
            String mscAddress = mtSmsMessage.getNetworkInfo().getMscAddress(); 
            if (mscAddress != null) {
                AddressString remoteAddress = mapProvider.getMAPParameterFactory().createAddressString(mscAddress, 
                                                AddressString.AddressNature.INTERNATIONAL_NUMBER, 
                                                AddressString.NumberingPlan.ISDN);
                mapDialog.setRemoteAddress(remoteAddress);
            } else {
                logger.warn("MSC Address not found in MapSmsMessage. Using default/configured remote address.");
                // Fallback to configured or default remote address for testing
                // This should be set by the calling SMSC based on HLR lookup.
            }

            // 4. Create MtForwardShortMessageRequest
            MtForwardShortMessageRequest mtRequest = mapDialog.newMtForwardShortMessageRequest();
            
            // Set the MSISDN of the recipient
            ISDNAddressString msisdn = mapProvider.getMAPParameterFactory().createISDNAddressString(
                recipientMsisdn, ISDNAddressString.AddressNature.INTERNATIONAL_NUMBER, ISDNAddressString.NumberingPlan.ISDN);
            mtRequest.setMsisdn(msisdn);
            
            // Set the SM-RP-UI (TP-DU)
            mtRequest.setSmRpUi(tpdu);
            
            // Alert Service Centre Indication - if needed (TP-SRI in TPDU)
            if (mtSmsMessage.getMetadata().getRequestDeliveryReport() != null && mtSmsMessage.getMetadata().getRequestDeliveryReport()) {
                mtRequest.setAlertServiceCentreInd(AlertServiceCentreInd.ALERT_SERVICE_CENTRE_PRESENCE);
            }

            // Other parameters from MapSmsMessage.metadata if available (e.g., priority, etc.)
            // For simplicity, we assume the TP-DU already contains most details.

            // 5. Send the request
            mapDialog.add
                
                mtRequest);
            mapDialog.send();

            logger.info("Sent MT-ForwardSM request for message {} to MSISDN {} over SS7. DialogId: {}",
                mtSmsMessage.getMessageId(), recipientMsisdn, mapDialog.getLocalDialogId());

            // TODO: Store correlation between MAP dialog/invoke ID and NATS message ID for response handling

        } catch (Exception e) {
            logger.error("Error sending MT SMS over SS7 for message: {}", mtSmsMessage.getMessageId(), e);
            // Publish failure response to NATS
            publishFailureResponse(mtSmsMessage.getCorrelationId(), mtSmsMessage.getMessageId(), e.getMessage());
        }
    }




    private void publishFailureResponse(String correlationId, String originalMessageId, String errorMessage) {
        MapSmsMessage response = new MapSmsMessage();
        response.setCorrelationId(correlationId);
        response.setMessageId(originalMessageId);
        response.setSmsType(SmsType.MT_FORWARD_SM_RESP);
        response.setDeliveryStatus(MapSmsMessage.DeliveryStatus.FAILED);
        response.setErrorMessage(errorMessage);
        response.setDirection(SS7Message.MessageDirection.INBOUND); // Response to SMSC

        eventPublisher.publishEvent("map.mt.sms.response", response);
        logger.error("Published MT SMS failure response for message {}: {}", originalMessageId, errorMessage);
    }
    
    private byte[] createSimpleTextTpdu(String recipient, String content) {
        // This is a highly simplified TP-DU for plain text, not a full encoder.
        // It's meant as a fallback/example if non-binary content is received.
        // In production, a proper TP-DU encoder should be used here.
        // This is effectively a basic SMS-DELIVER (from SC to MS).

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            // First Octet (TP-MTI = 0x00 for SMS-DELIVER, TP-UDHI=0, TP-SRI=0, TP-LP=0, TP-MMS=0)
            baos.write(0x00); 

            // Originating Address (simplified, assuming international ISDN number)
            // Length, TON/NPI, Address Value (BCD)
            // Example: "+1234567890" -> 07912143658709F8
            // Hardcoded for now. This would come from the SMSC's configuration.
            byte[] oaBytes = { 0x07, (byte)0x91, 0x21, 0x43, 0x65, 0x87, 0x09, (byte)0xF8 };
            baos.write(oaBytes);

            // TP-PID (0x00 for default)
            baos.write(0x00);

            // TP-DCS (0x00 for GSM 7-bit)
            baos.write(0x00);

            // TP-SCTS (7 octets - example timestamp)
            // Example: 2025/01/01 00:00:00 GMT+0 -> 25011000000000
            byte[] sctsBytes = { 0x25, 0x10, 0x10, 0x00, 0x00, 0x00, 0x00 };
            baos.write(sctsBytes);
            
            // TP-UDL (User Data Length - septet count)
            // Use Gsm7BitCharset from the other project. Need to copy or use as dependency.
            // For now, simplify and assume content is short and directly represents bytes for UDL.
            byte[] gsm7bitContent = Gsm7BitCharset.pack(content);
            baos.write(gsm7bitContent.length);

            // TP-UD (User Data)
            baos.write(gsm7bitContent);
            
            return baos.toByteArray();
        } catch (IOException e) {
            logger.error("Error creating simple text TPDU fallback", e);
            return new byte[0];
        }
    }
}