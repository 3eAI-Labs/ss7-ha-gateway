package com.company.ss7ha.kafka.converters;

import com.company.ss7ha.kafka.messages.MoSmsMessage;
import com.mobius.software.telco.protocols.ss7.map.api.primitives.AddressNature;
import com.mobius.software.telco.protocols.ss7.map.api.primitives.AddressString;
import com.mobius.software.telco.protocols.ss7.map.api.primitives.IMSI;
import com.mobius.software.telco.protocols.ss7.map.api.primitives.ISDNAddressString;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.MoForwardShortMessageRequest;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.SmsSignalInfo;
import com.mobius.software.telco.protocols.ss7.map.api.service.sms.LocationInfoWithLMSI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            message.setTimestamp(System.currentTimeMillis());
            message.setDialogId(dialogId);
            message.setInvokeId(invokeId);

            // Sender information (MSISDN, IMSI)
            if (request.getMsIsdn() != null) {
                MoSmsMessage.AddressInfo sender = new MoSmsMessage.AddressInfo();
                sender.setMsisdn(extractAddress(request.getMsIsdn()));

                // IMSI (if available)
                if (request.getImsi() != null) {
                    sender.setImsi(request.getImsi().getData());
                }

                message.setSender(sender);
            }

            // Service center address (recipient)
            if (request.getSmRpDa() != null && request.getSmRpDa().getServiceCentreAddressDA() != null) {
                MoSmsMessage.AddressInfo recipient = new MoSmsMessage.AddressInfo();
                recipient.setAddress(extractAddress(request.getSmRpDa().getServiceCentreAddressDA()));
                message.setRecipient(recipient);
            }

            // SMS content
            if (request.getSmRpUi() != null) {
                MoSmsMessage.MessageContent content = new MoSmsMessage.MessageContent();

                // Base64 encode the SMS TPDU
                byte[] tpdu = request.getSmRpUi().getValue();
                if (tpdu != null) {
                    content.setContent(Base64.getEncoder().encodeToString(tpdu));
                }

                // Encoding type (will be decoded by consumer)
                content.setEncoding("SMS-TPDU");

                message.setMessage(content);
            }

            // Network information
            MoSmsMessage.NetworkInfo networkInfo = new MoSmsMessage.NetworkInfo();

            // MSC address
            if (request.getStoredMSISDN() != null) {
                networkInfo.setMscAddress(extractAddress(request.getStoredMSISDN()));
            }

            // Location info (if available)
            if (request.getLocationInfoWithLMSI() != null) {
                LocationInfoWithLMSI locInfo = request.getLocationInfoWithLMSI();
                MoSmsMessage.LocationInfo location = new MoSmsMessage.LocationInfo();

                if (locInfo.getNetworkNodeNumber() != null) {
                    networkInfo.setVlrNumber(extractAddress(locInfo.getNetworkNodeNumber()));
                }

                // Cell global identity (if available)
                if (locInfo.getGprsNodeIndicator() != null) {
                    // Extract MCC, MNC, LAC, Cell ID from CGI
                    // Note: Actual extraction depends on GPRS node indicator structure
                    // Simplified here - production code should parse properly
                }

                networkInfo.setLocationInfo(location);
            }

            message.setNetworkInfo(networkInfo);

            // Service information
            MoSmsMessage.ServiceInfo serviceInfo = new MoSmsMessage.ServiceInfo();

            // Priority (if available from message class)
            if (request.getSmRpUi() != null) {
                // Extract message class from TPDU (simplified)
                // Production code should properly decode TPDU
                serviceInfo.setPriority("NORMAL");
            }

            // More messages to send flag
            if (request.getMoreMessagesToSend() != null) {
                serviceInfo.setMoreMessagesToSend(request.getMoreMessagesToSend());
            }

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
}
