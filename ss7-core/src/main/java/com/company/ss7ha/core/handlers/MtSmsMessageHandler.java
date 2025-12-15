package com.company.ss7ha.core.handlers;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.utils.Gsm7BitCharset;
import com.company.ss7ha.messages.MapSmsMessage;
import com.company.ss7ha.messages.SS7Message;
import com.company.ss7ha.messages.MapSmsMessage.SmsType;
import com.company.ss7ha.nats.subscriber.SS7NatsSubscriber;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.restcomm.protocols.ss7.map.api.MAPProvider;
import org.restcomm.protocols.ss7.map.api.MAPStack;
import org.restcomm.protocols.ss7.map.api.MAPDialog;
import org.restcomm.protocols.ss7.map.api.MAPException;
import org.restcomm.protocols.ss7.map.api.MAPParameterFactory;
import org.restcomm.protocols.ss7.map.api.service.sms.MAPDialogSms;
import org.restcomm.protocols.ss7.map.api.service.sms.SM_RP_DA;
import org.restcomm.protocols.ss7.map.api.service.sms.SM_RP_OA;
import org.restcomm.protocols.ss7.map.api.service.sms.SmsSignalInfo;
import org.restcomm.protocols.ss7.map.api.smstpdu.SmsTpdu;
import org.restcomm.protocols.ss7.map.smstpdu.SmsTpduImpl;
import org.restcomm.protocols.ss7.tcap.api.tc.dialog.Dialog;
import com.mobius.software.common.dal.timers.TaskCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.restcomm.protocols.ss7.commonapp.api.primitives.AddressNature;
import org.restcomm.protocols.ss7.commonapp.api.primitives.AddressString;
import org.restcomm.protocols.ss7.commonapp.api.primitives.ISDNAddressString;
import org.restcomm.protocols.ss7.commonapp.api.primitives.MAPExtensionContainer;
import org.restcomm.protocols.ss7.commonapp.api.primitives.NumberingPlan;
import org.restcomm.protocols.ss7.map.api.MAPApplicationContext;
import org.restcomm.protocols.ss7.map.api.MAPApplicationContextName;
import org.restcomm.protocols.ss7.map.api.MAPApplicationContextVersion;
import org.restcomm.protocols.ss7.sccp.parameter.SccpAddress;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class MtSmsMessageHandler implements SS7NatsSubscriber.MessageHandler<MapSmsMessage> {

    private static final Logger logger = LoggerFactory.getLogger(MtSmsMessageHandler.class);

    private final MAPStack mapStack;
    private final EventPublisher eventPublisher;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

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
                logger.warn("Received non-binary MT SMS content for message {}. Expecting BINARY PDU. Attempting to create simple PDU.", mtSmsMessage.getMessageId());
                tpdu = createSimpleTextTpdu(mtSmsMessage.getRecipient(), mtSmsMessage.getContent());
            }

            // 2. Extract recipient (MSISDN) and sender
            String recipientMsisdn = mtSmsMessage.getRecipient();
            String senderMsisdn = mtSmsMessage.getSender();

            if (recipientMsisdn == null || recipientMsisdn.isEmpty()) {
                throw new IllegalArgumentException("Recipient MSISDN cannot be null or empty for MT SMS");
            }

            MAPProvider mapProvider = mapStack.getProvider();
            MAPParameterFactory mapParamFactory = mapProvider.getMAPParameterFactory();

            // 3. Create MAPDialog
            // Use appropriate application context
            MAPApplicationContext appCtx = MAPApplicationContext.getInstance(
                MAPApplicationContextName.shortMsgGatewayContext,
                MAPApplicationContextVersion.version3);

            // Dummy SCCP addresses for now.
            SccpAddress origAddress = null; 
            SccpAddress destAddress = null; 
            AddressString origReference = null; 
            AddressString destReference = null; 
            int networkId = 1;

            MAPDialogSms mapDialog = mapProvider.getMAPServiceSms().createNewDialog(
                appCtx, origAddress, origReference, destAddress, destReference, networkId);
            
            // 4. Construct SM_RP_DA (Destination Address)
            ISDNAddressString daMsisdn = mapParamFactory.createISDNAddressString(
                AddressNature.international_number, NumberingPlan.ISDN, recipientMsisdn);
            SM_RP_DA smRpDa = mapParamFactory.createSM_RP_DA(daMsisdn);

            // 5. Construct SM_RP_OA (Originating Address)
            ISDNAddressString oaMsisdn = null;
            SM_RP_OA smRpOa = null;
            if (senderMsisdn != null && !senderMsisdn.isEmpty()) {
                oaMsisdn = mapParamFactory.createISDNAddressString(
                    AddressNature.international_number, NumberingPlan.ISDN, senderMsisdn);
                smRpOa = mapParamFactory.createSM_RP_OA_Msisdn(oaMsisdn);
            } else {
                smRpOa = mapParamFactory.createSM_RP_OA();
            }

            // 6. Construct SmsSignalInfo from TPDU
            ByteBuf tpduByteBuf = Unpooled.wrappedBuffer(tpdu);
            SmsTpdu smsTpdu = SmsTpduImpl.createInstance(tpduByteBuf, false, StandardCharsets.UTF_8);
            SmsSignalInfo smsSignalInfo = mapParamFactory.createSmsSignalInfo(smsTpdu, StandardCharsets.UTF_8);

            // 7. Send the MT-ForwardSM request
            mapDialog.addMtForwardShortMessageRequest(smRpDa, smRpOa, smsSignalInfo, false, null);
            
            // Send the dialog
            mapDialog.send(new TaskCallback<Exception>() {
                @Override
                public void onSuccess() {
                    logger.info("MAP dialog for message {} sent successfully.", mtSmsMessage.getMessageId());
                }

                @Override
                public void onError(Exception e) {
                    logger.error("Error sending MAP dialog for message {}: {}", mtSmsMessage.getMessageId(), e.getMessage(), e);
                    publishFailureResponse(mtSmsMessage.getCorrelationId(), mtSmsMessage.getMessageId(), e.getMessage());
                }
            });

            logger.info("Sent MT-ForwardSM request for message {} to MSISDN {} over SS7. DialogId: {}",
                mtSmsMessage.getMessageId(), recipientMsisdn, mapDialog.getLocalDialogId());

        } catch (MAPException | IllegalArgumentException e) {
            logger.error("Error sending MT SMS over SS7 for message: {}", mtSmsMessage.getMessageId(), e);
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
        response.setDirection(SS7Message.MessageDirection.INBOUND);

        eventPublisher.publishEvent("map.mt.sms.response", response);
        logger.error("Published MT SMS failure response for message {}: {}", originalMessageId, errorMessage);
    }
    
    private byte[] createSimpleTextTpdu(String recipient, String content) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            baos.write(0x00); 
            byte[] oaBytes = { (byte)0x07, (byte)0x91, (byte)0x21, (byte)0x43, (byte)0x65, (byte)0x87, (byte)0x09, (byte)0xF8 };
            baos.write(oaBytes);
            baos.write(0x00);
            baos.write(0x00);
            byte[] sctsBytes = { (byte)0x25, (byte)0x10, (byte)0x10, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00 };
            baos.write(sctsBytes);
            byte[] gsm7bitContent = Gsm7BitCharset.pack(content);
            baos.write(gsm7bitContent.length);
            baos.write(gsm7bitContent);
            return baos.toByteArray();
        } catch (IOException e) {
            logger.error("Error creating simple text TPDU fallback", e);
            return new byte[0];
        }
    }
}
