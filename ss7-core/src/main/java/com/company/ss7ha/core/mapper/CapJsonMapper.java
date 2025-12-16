package com.company.ss7ha.core.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.restcomm.protocols.ss7.cap.api.CAPParameterFactory;
import org.restcomm.protocols.ss7.cap.api.service.sms.InitialDPSMSRequest;
import org.restcomm.protocols.ss7.cap.api.service.sms.EventReportSMSRequest;
import org.restcomm.protocols.ss7.cap.api.service.sms.ResetTimerSMSRequest;
import org.restcomm.protocols.ss7.cap.api.service.gprs.InitialDPGPRSRequest;
import org.restcomm.protocols.ss7.cap.api.service.gprs.ApplyChargingReportGPRSRequest;
import org.restcomm.protocols.ss7.cap.api.service.gprs.EntityReleasedGPRSRequest;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.InitialDPRequest;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.EventReportBCSMRequest;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.ApplyChargingReportRequest;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.CallInformationReportRequest;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.SpecializedResourceReportRequest;
import org.restcomm.protocols.ss7.cap.api.primitives.EventTypeBCSM;
import org.restcomm.protocols.ss7.cap.api.primitives.MonitorMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive Mapper for CAP v4 Messages <-> JSON.
 * Implements "Hex Passthrough" strategy: Map everything to Hex Strings to ensure no data loss.
 */
public class CapJsonMapper {

    private static final Logger logger = LoggerFactory.getLogger(CapJsonMapper.class);
    private final ObjectMapper mapper;

    public CapJsonMapper() {
        this.mapper = new ObjectMapper();
    }

    // ==================================================================================
    // INGRESS MAPPING (CAP -> JSON)
    // ==================================================================================

    public JsonNode mapInitialDP(InitialDPRequest request) {
        ObjectNode json = mapper.createObjectNode();

        // Primitives
        json.put("serviceKey", request.getServiceKey());
        if (request.getEventTypeBCSM() != null) json.put("eventTypeBCSM", request.getEventTypeBCSM().toString());
        if (request.getCGEncountered() != null) json.put("cGEncountered", request.getCGEncountered().toString());

        // Complex Types -> Hex Passthrough
        putHex(json, "calledPartyNumber", request.getCalledPartyNumber());
        putHex(json, "callingPartyNumber", request.getCallingPartyNumber());
        putHex(json, "callingPartysCategory", request.getCallingPartysCategory());
        putHex(json, "iPSSPCapabilities", request.getIPSSPCapabilities());
        putHex(json, "locationNumber", request.getLocationNumber());
        putHex(json, "originalCalledPartyID", request.getOriginalCalledPartyID());
        putHex(json, "extensions", request.getExtensions());
        putHex(json, "highLayerCompatibility", request.getHighLayerCompatibility());
        putHex(json, "additionalCallingPartyNumber", request.getAdditionalCallingPartyNumber());
        putHex(json, "bearerCapability", request.getBearerCapability());
        putHex(json, "redirectingPartyID", request.getRedirectingPartyID());
        putHex(json, "redirectionInformation", request.getRedirectionInformation());
        putHex(json, "cause", request.getCause());
        putHex(json, "serviceInteractionIndicatorsTwo", request.getServiceInteractionIndicatorsTwo());
        putHex(json, "carrier", request.getCarrier());
        putHex(json, "cugIndex", request.getCugIndex());
        putHex(json, "cugInterlock", request.getCugInterlock());
        putHex(json, "imsi", request.getIMSI());
        putHex(json, "subscriberState", request.getSubscriberState());
        putHex(json, "locationInformation", request.getLocationInformation());
        putHex(json, "extBasicServiceCode", request.getExtBasicServiceCode());
        putHex(json, "callReferenceNumber", request.getCallReferenceNumber());
        putHex(json, "mscAddress", request.getMscAddress());
        putHex(json, "calledPartyBCDNumber", request.getCalledPartyBCDNumber());
        putHex(json, "timeAndTimezone", request.getTimeAndTimezone());
        putHex(json, "initialDPArgExtension", request.getInitialDPArgExtension());
        
        return json;
    }
    
    public JsonNode mapInitialDPSMS(InitialDPSMSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        json.put("serviceKey", request.getServiceKey());
        
        putHex(json, "callingPartyNumber", request.getCallingPartyNumber());
        putHex(json, "destinationSubscriberNumber", request.getDestinationSubscriberNumber());
        putHex(json, "eventTypeSMS", request.getEventTypeSMS());
        putHex(json, "imsi", request.getIMSI());
        putHex(json, "locationInformationMSC", request.getLocationInformationMSC());
        putHex(json, "locationInformationGPRS", request.getLocationInformationGPRS());
        putHex(json, "smscAddress", request.getSMSCAddress());
        putHex(json, "timeAndTimezone", request.getTimeAndTimezone());
        putHex(json, "tpShortMessageSpecificInfo", request.getTPShortMessageSpecificInfo());
        putHex(json, "tpProtocolIdentifier", request.getTPProtocolIdentifier());
        putHex(json, "tpDataCodingScheme", request.getTPDataCodingScheme());
        putHex(json, "tpValidityPeriod", request.getTPValidityPeriod());
        putHex(json, "extensions", request.getExtensions());
        putHex(json, "smsReferenceNumber", request.getSmsReferenceNumber());
        putHex(json, "mscAddress", request.getMscAddress());
        putHex(json, "sgsnNumber", request.getSgsnNumber());
        putHex(json, "msClassmark2", request.getMsClassmark2());
        putHex(json, "gprsMsClass", request.getGPRSMSClass());
        putHex(json, "imei", request.getIMEI());
        putHex(json, "calledPartyNumber", request.getCalledPartyNumber());
        
        return json;
    }
    
    public JsonNode mapEventReportBCSM(EventReportBCSMRequest request) {
        ObjectNode json = mapper.createObjectNode();
        if (request.getEventTypeBCSM() != null) json.put("eventTypeBCSM", request.getEventTypeBCSM().toString());
        
        putHex(json, "eventSpecificInformationBCSM", request.getEventSpecificInformationBCSM());
        putHex(json, "legID", request.getLegID());
        putHex(json, "miscCallInfo", request.getMiscCallInfo());
        putHex(json, "extensions", request.getExtensions());
        
        return json;
    }
    
    public JsonNode mapEventReportSMS(EventReportSMSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        
        putHex(json, "eventTypeSMS", request.getEventTypeSMS());
        putHex(json, "eventSpecificInformationSMS", request.getEventSpecificInformationSMS());
        putHex(json, "miscCallInfo", request.getMiscCallInfo());
        putHex(json, "extensions", request.getExtensions());
        
        return json;
    }
    
    public JsonNode mapApplyChargingReport(ApplyChargingReportRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "callResult", request.getCallResult());
        return json;
    }
    
    public JsonNode mapCallInformationReport(CallInformationReportRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "requestedInformationList", request.getRequestedInformationList());
        putHex(json, "legID", request.getLegID());
        putHex(json, "extensions", request.getExtensions());
        return json;
    }
    
    public JsonNode mapSpecializedResourceReport(SpecializedResourceReportRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "allAnnouncementsComplete", request.getAllAnnouncementsComplete());
        putHex(json, "firstAnnouncementStarted", request.getFirstAnnouncementStarted());
        return json;
    }
    
    public JsonNode mapResetTimerSMS(ResetTimerSMSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "timerID", request.getTimerID());
        putHex(json, "timerValue", request.getTimerValue());
        putHex(json, "extensions", request.getExtensions());
        return json;
    }
    
    // GPRS Mapping Methods
    
    public JsonNode mapInitialDPGPRS(InitialDPGPRSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        json.put("serviceKey", request.getServiceKey());
        
        putHex(json, "gprsEventType", request.getGPRSEventType());
        putHex(json, "msisdn", request.getMSISDN());
        putHex(json, "imsi", request.getIMSI());
        putHex(json, "timeAndTimezone", request.getTimeAndTimezone());
        putHex(json, "gprsMSClass", request.getGPRSMSClass());
        putHex(json, "endUserAddress", request.getEndUserAddress());
        putHex(json, "qualityOfService", request.getQualityOfService());
        putHex(json, "accessPointName", request.getAccessPointName());
        putHex(json, "routeingAreaIdentity", request.getRouteingAreaIdentity());
        putHex(json, "chargingID", request.getChargingID());
        putHex(json, "sgsnCapabilities", request.getSGSNCapabilities());
        putHex(json, "locationInformationGPRS", request.getLocationInformationGPRS());
        putHex(json, "pdpInitiationType", request.getPDPInitiationType());
        putHex(json, "extensions", request.getExtensions());
        putHex(json, "ggsnAddress", request.getGGSNAddress());
        putHex(json, "secondaryPDPContext", request.getSecondaryPDPContext());
        putHex(json, "imei", request.getIMEI());
        
        return json;
    }
    
    public JsonNode mapApplyChargingReportGPRS(ApplyChargingReportGPRSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        
        putHex(json, "chargingResult", request.getChargingResult());
        putHex(json, "qualityOfService", request.getQualityOfService());
        putHex(json, "active", request.getActive());
        putHex(json, "pdpID", request.getPDPID());
        putHex(json, "extensions", request.getExtensions());
        putHex(json, "chargingRollOver", request.getChargingRollOver());
        
        return json;
    }
    
    public JsonNode mapEntityReleasedGPRS(EntityReleasedGPRSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        
        putHex(json, "gprsCause", request.getGPRSCause());
        putHex(json, "pDPID", request.getPDPID());
        putHex(json, "extensions", request.getExtensions());
        
        return json;
    }

    private void putHex(ObjectNode json, String fieldName, Object value) {
        if (value == null) return;
        String hex = toHex(value);
        if (hex != null) {
            json.put(fieldName, hex);
        } else {
            if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum) {
                json.put(fieldName, value.toString());
            } else {
                logger.debug("Could not convert field {} to Hex or primitive: {}", fieldName, value.getClass().getSimpleName());
            }
        }
    }

    private String toHex(Object value) {
        try {
            Method getDataMethod = value.getClass().getMethod("getData");
            byte[] data = (byte[]) getDataMethod.invoke(value);
            return bytesToHex(data);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================================================================================
    // EGRESS MAPPING (JSON -> CAP)
    // ==================================================================================
    
    public EventTypeBCSM[] mapBcsmEvents(JsonNode jsonEvents) {
        if (jsonEvents == null || !jsonEvents.isArray()) {
            return new EventTypeBCSM[0];
        }
        List<EventTypeBCSM> events = new ArrayList<>();
        for (JsonNode node : jsonEvents) {
            try {
                String eventName = node.asText();
                switch (eventName.toUpperCase()) {
                    case "O_ANSWER": events.add(EventTypeBCSM.oAnswer); break;
                    case "O_DISCONNECT": events.add(EventTypeBCSM.oDisconnect); break;
                    case "O_BUSY": events.add(EventTypeBCSM.oCalledPartyBusy); break;
                    case "O_NO_ANSWER": events.add(EventTypeBCSM.oNoAnswer); break;
                    case "ROUTE_SELECT_FAILURE": events.add(EventTypeBCSM.routeSelectFailure); break;
                    case "T_ANSWER": events.add(EventTypeBCSM.tAnswer); break;
                    case "T_DISCONNECT": events.add(EventTypeBCSM.tDisconnect); break;
                    case "T_BUSY": events.add(EventTypeBCSM.tBusy); break;
                    case "T_NO_ANSWER": events.add(EventTypeBCSM.tNoAnswer); break;
                    default: logger.warn("Unknown BCSM Event: {}", eventName);
                }
            } catch (Exception e) {
                logger.warn("Failed to map BCSM event", e);
            }
        }
        return events.toArray(new EventTypeBCSM[0]);
    }

    public MonitorMode mapMonitorMode(String mode) {
        if (mode == null) return MonitorMode.notifyAndContinue;
        switch (mode.toUpperCase()) {
            case "INTERRUPTED": return MonitorMode.interrupted;
            case "NOTIFY_AND_CONTINUE": return MonitorMode.notifyAndContinue;
            case "TRANSPARENT": return MonitorMode.transparent;
            default: return MonitorMode.notifyAndContinue;
        }
    }

    public byte[] mapCause(int causeCode) {
        return new byte[] { (byte)0x80, (byte)(causeCode | 0x80) };
    }

    public Object createFromHex(CAPParameterFactory pf, String type, JsonNode node) {
        if (node == null || node.isNull()) return null;
        String s = node.asText();
        byte[] data = hexToBytes(s);
        if (data == null) return null;

        try {
            switch (type) {
                // Voice Types
                case "OriginalCalledPartyID": return pf.createOriginalCalledPartyID(data);
                case "RedirectingPartyID": return pf.createRedirectingPartyID(data);
                case "RedirectionInformation": return pf.createRedirectionInformation(data);
                case "ServiceInteractionIndicatorsTwo": return pf.createServiceInteractionIndicatorsTwo(data);
                case "ChargeNumber": return pf.createChargeNumber(data);
                case "CallingPartysCategory": return pf.createCallingPartysCategory(data);
                case "AlertingPattern": return pf.createAlertingPattern(data);
                case "Carrier": return pf.createCarrier(data);
                case "CUGInterlock": return pf.createCUGInterlock(data);
                case "SuppressionOfAnnouncement": return pf.createSuppressionOfAnnouncement(data);
                case "OCSIApplicable": return pf.createOCSIApplicable(data);
                case "NAOliInfo": return pf.createNAOliInfo(data);
                case "AChBillingChargingCharacteristics": return pf.createAChBillingChargingCharacteristics(data);
                case "AChChargingAddress": return pf.createAChChargingAddress(data);
                case "SendingSideID": return pf.createSendingSideID(data);
                case "FCIBillingChargingCharacteristics": return pf.createFCIBillingChargingCharacteristics(data);
                case "SCIBillingChargingCharacteristics": return pf.createSCIBillingChargingCharacteristics(data);
                case "RequestedInformationTypeList": return pf.createRequestedInformationTypeList(data);
                
                // SMS Types
                case "SMSAddressString": return pf.createSMSAddressString(data);
                case "CalledPartyBCDNumber": return pf.createCalledPartyBCDNumber(data);
                case "ISDNAddressString": return pf.createISDNAddressString(data);
                case "RPCause": return pf.createRPCause(data);
                case "SMSEvent": return pf.createSMSEvent(data);
                case "FCISMSBillingChargingCharacteristics": return pf.createFCISMSBillingChargingCharacteristics(data);
                case "TimerID": return pf.createTimerID(data);
                case "TimerValue": return pf.createTimerValue(data);
                
                // GPRS Types
                case "AccessPointName": return pf.createAccessPointName(data);
                case "PDPID": return pf.createPDPID(data);
                case "GPRSCause": return pf.createGPRSCause(data);
                case "ChargingCharacteristics": return pf.createChargingCharacteristics(data);

                default: return null;
            }
        } catch (Exception e) {
            logger.warn("Failed to create CAP object for {}", type, e);
            return null;
        }
    }

    // ==================================================================================
    // UTILS
    // ==================================================================================

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return null;
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    public static byte[] hexToBytes(String s) {
        if (s == null) return null;
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
