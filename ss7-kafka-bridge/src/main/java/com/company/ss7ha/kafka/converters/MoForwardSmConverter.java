package com.company.ss7ha.kafka.converters;

import com.company.ss7ha.kafka.messages.MoSmsMessage;
import org.restcomm.protocols.ss7.commonapp.api.primitives.AddressNature;
import org.restcomm.protocols.ss7.commonapp.api.primitives.AddressString;
import org.restcomm.protocols.ss7.commonapp.api.primitives.IMSI;
import org.restcomm.protocols.ss7.commonapp.api.primitives.ISDNAddressString;
import org.restcomm.protocols.ss7.map.api.service.sms.MoForwardShortMessageRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.SM_RP_OA;
import org.restcomm.protocols.ss7.map.api.service.sms.SM_RP_DA;
import org.restcomm.protocols.ss7.map.api.service.sms.SmsSignalInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mobius.software.telco.protocols.ss7.asn.primitives.ASNOctetString;
import io.netty.buffer.ByteBuf;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Converts JSS7 MO-ForwardSM request to license-safe JSON message.
 *
 * CRITICAL: This converter creates ONLY primitive-type objects.
 * Output (MoSmsMessage) contains NO JSS7 types - AGPL firewall intact!
 *
 * LICENSE: AGPL-3.0 (part of ss7-ha-gateway)
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public class MoForwardSmConverter {

    private static final Logger logger = LoggerFactory.getLogger(MoForwardSmConverter.class);

    /**
     * Convert MO-ForwardSM request to JSON-serializable MoSmsMessage.
     *
     * Extracts all relevant data from the MAP operation and converts to
     * primitive types (String, Integer, Boolean, etc.).
     *
     * @param request MO-ForwardSM request from JSS7
     * @param dialogId Local dialog ID
     * @param invokeId Invoke ID
     * @return MoSmsMessage with primitive types only
     */
    public static MoSmsMessage convert(MoForwardShortMessageRequest request,
                                      Long dialogId,
                                      Integer invokeId) {
        if (request == null) {
            logger.warn("Cannot convert null MO-ForwardSM request");
            return null;
        }

        try {
            MoSmsMessage message = new MoSmsMessage();

            // Message metadata
            message.setMessageId(UUID.randomUUID().toString());
            message.setTimestamp(Instant.now());
            message.setDialogId(dialogId != null ? dialogId.toString() : null);
            message.setInvokeId(invokeId);

            // Sender information (MSISDN from SM_RP_OA, IMSI)
            SM_RP_OA rpOA = request.getSM_RP_OA();
            if (rpOA != null && rpOA.getMsisdn() != null) {
                MoSmsMessage.AddressInfo sender = new MoSmsMessage.AddressInfo();
                sender.setMsisdn(extractAddress(rpOA.getMsisdn()));

                // IMSI (if available - Corsac has getIMSI)
                if (request.getIMSI() != null) {
                    sender.setImsi(request.getIMSI().getData());
                }

                message.setSender(sender);
            }

            // Service center address (recipient from SM_RP_DA)
            SM_RP_DA rpDA = request.getSM_RP_DA();
            if (rpDA != null && rpDA.getServiceCentreAddressDA() != null) {
                MoSmsMessage.AddressInfo recipient = new MoSmsMessage.AddressInfo();
                recipient.setMsisdn(extractAddress(rpDA.getServiceCentreAddressDA()));
                message.setRecipient(recipient);
            }

            // SMS content (from SM_RP_UI)
            if (request.getSM_RP_UI() != null) {
                MoSmsMessage.MessageContent content = new MoSmsMessage.MessageContent();

                // Base64 encode the SMS TPDU (Corsac uses ASNOctetString internally)
                SmsSignalInfo smsSignalInfo = request.getSM_RP_UI();
                if (smsSignalInfo instanceof ASNOctetString) {
                    ByteBuf tpduBuf = ((ASNOctetString) smsSignalInfo).getValue();
                    if (tpduBuf != null && tpduBuf.readableBytes() > 0) {
                        byte[] tpdu = new byte[tpduBuf.readableBytes()];
                        tpduBuf.getBytes(tpduBuf.readerIndex(), tpdu);
                        content.setContent(Base64.getEncoder().encodeToString(tpdu));
                    }
                }

                // Encoding type (will be decoded by consumer)
                content.setEncoding("SMS-TPDU");

                message.setMessage(content);
            }

            // Network information (simplified - Corsac doesn't have getStoredMSISDN/getLocationInfoWithLMSI)
            MoSmsMessage.NetworkInfo networkInfo = new MoSmsMessage.NetworkInfo();
            message.setNetworkInfo(networkInfo);

            // Service information (simplified - Corsac doesn't have getMoreMessagesToSend)
            MoSmsMessage.ServiceInfo serviceInfo = new MoSmsMessage.ServiceInfo();
            serviceInfo.setPriority("NORMAL");
            message.setServiceInfo(serviceInfo);

            logger.info("Converted MO-ForwardSM to JSON message: {} (dialog: {}, invoke: {})",
                    message.getMessageId(), dialogId, invokeId);

            return message;

        } catch (Exception e) {
            logger.error("Failed to convert MO-ForwardSM request (dialog: {}, invoke: {})",
                    dialogId, invokeId, e);
            return null;
        }
    }

    /**
     * Extract address string from ISDNAddressString.
     *
     * Converts to international format (+CCNDC...)
     *
     * @param address ISDN address
     * @return Address as string
     */
    private static String extractAddress(ISDNAddressString address) {
        if (address == null || address.getAddress() == null) {
            return null;
        }

        String addr = address.getAddress();

        // Add + prefix for international numbers
        if (address.getAddressNature() == AddressNature.international_number) {
            if (!addr.startsWith("+")) {
                addr = "+" + addr;
            }
        }

        return addr;
    }

    /**
     * Extract address string from AddressString.
     *
     * @param address Address string
     * @return Address as string
     */
    private static String extractAddress(AddressString address) {
        if (address == null || address.getAddress() == null) {
            return null;
        }

        String addr = address.getAddress();

        // Add + prefix for international numbers
        if (address.getAddressNature() == AddressNature.international_number) {
            if (!addr.startsWith("+")) {
                addr = "+" + addr;
            }
        }

        return addr;
    }

    /**
     * Decode GSM 7-bit encoding to UTF-8 string.
     *
     * Note: This is a simplified implementation.
     * Production code should use proper GSM 7-bit decoder.
     *
     * @param data Encoded data
     * @return Decoded string
     */
    private static String decodeGsm7(byte[] data) {
        if (data == null) {
            return null;
        }

        try {
            // Simplified - production should use proper GSM 7-bit alphabet
            // For now, return as UTF-8
            return new String(data, "UTF-8");
        } catch (Exception e) {
            logger.warn("Failed to decode GSM7 data", e);
            return null;
        }
    }

    /**
     * Decode UCS-2 encoding to UTF-8 string.
     *
     * @param data Encoded data
     * @return Decoded string
     */
    private static String decodeUcs2(byte[] data) {
        if (data == null) {
            return null;
        }

        try {
            return new String(data, "UTF-16BE");
        } catch (Exception e) {
            logger.warn("Failed to decode UCS2 data", e);
            return null;
        }
    }

    /**
     * Convert Map-based event to MoSmsMessage.
     * This is used when events come from ss7-core's generic EventPublisher.
     *
     * @param eventData Map containing event data
     * @return MoSmsMessage
     */
    public static MoSmsMessage convertFromMap(java.util.Map<String, Object> eventData) {
        // TODO: Implement proper conversion from Map to MoSmsMessage
        // For now, delegate to the full converter if we have the request object
        Object request = eventData.get("request");
        if (request instanceof MoForwardShortMessageRequest) {
            Long dialogId = eventData.containsKey("dialogId") ?
                          ((Number) eventData.get("dialogId")).longValue() : null;
            Integer invokeId = eventData.containsKey("invokeId") ?
                             ((Number) eventData.get("invokeId")).intValue() : null;

            return convert((MoForwardShortMessageRequest) request, dialogId, invokeId);
        }

        // Fallback: create a minimal message from available data
        logger.warn("Creating minimal MoSmsMessage from Map data (full request not available)");
        MoSmsMessage message = new MoSmsMessage();
        message.setMessageId(java.util.UUID.randomUUID().toString());
        message.setTimestamp(Instant.now());

        if (eventData.containsKey("dialogId")) {
            message.setDialogId(String.valueOf(((Number) eventData.get("dialogId")).longValue()));
        }
        if (eventData.containsKey("invokeId")) {
            message.setInvokeId(((Number) eventData.get("invokeId")).intValue());
        }

        return message;
    }
}
