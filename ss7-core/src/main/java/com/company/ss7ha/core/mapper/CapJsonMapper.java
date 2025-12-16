package com.company.ss7ha.core.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.Unpooled;
import org.restcomm.protocols.ss7.cap.api.CAPParameterFactory;
import org.restcomm.protocols.ss7.cap.api.service.sms.InitialDPSMSRequest;
import org.restcomm.protocols.ss7.cap.api.service.sms.EventReportSMSRequest;
import org.restcomm.protocols.ss7.cap.api.service.sms.ResetTimerSMSRequest;
import org.restcomm.protocols.ss7.cap.api.service.gprs.InitialDpGprsRequest;
import org.restcomm.protocols.ss7.cap.api.service.gprs.ApplyChargingReportGPRSRequest;
import org.restcomm.protocols.ss7.cap.api.service.gprs.EntityReleasedGPRSRequest;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.InitialDPRequest;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.EventReportBCSMRequest;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.ApplyChargingReportRequest;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.CallInformationReportRequest;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.SpecializedResourceReportRequest;
import org.restcomm.protocols.ss7.commonapp.api.primitives.EventTypeBCSM;
import org.restcomm.protocols.ss7.commonapp.api.primitives.MonitorMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CapJsonMapper {

    private static final Logger logger = LoggerFactory.getLogger(CapJsonMapper.class);
    private final ObjectMapper mapper;

    public CapJsonMapper() {
        this.mapper = new ObjectMapper();
    }

    public JsonNode mapInitialDP(InitialDPRequest request) {
        ObjectNode json = mapper.createObjectNode();
        json.put("serviceKey", request.getServiceKey());
        if (request.getEventTypeBCSM() != null) json.put("eventTypeBCSM", request.getEventTypeBCSM().toString());
        putHex(json, "calledPartyNumber", request.getCalledPartyNumber());
        putHex(json, "callingPartyNumber", request.getCallingPartyNumber());
        putHex(json, "callingPartysCategory", request.getCallingPartysCategory());
        return json;
    }
    
    public JsonNode mapInitialDPSMS(InitialDPSMSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        json.put("serviceKey", request.getServiceKey());
        putHex(json, "callingPartyNumber", request.getCallingPartyNumber());
        putHex(json, "destinationSubscriberNumber", request.getDestinationSubscriberNumber());
        putHex(json, "eventTypeSMS", request.getEventTypeSMS());
        putHex(json, "imsi", request.getImsi());
        putHex(json, "msClassmark2", request.getMSClassmark2()); 
        putHex(json, "imei", request.getImei()); 
        return json;
    }
    
    public JsonNode mapEventReportBCSM(EventReportBCSMRequest request) {
        ObjectNode json = mapper.createObjectNode();
        if (request.getEventTypeBCSM() != null) json.put("eventTypeBCSM", request.getEventTypeBCSM().toString());
        putHex(json, "eventSpecificInformationBCSM", request.getEventSpecificInformationBCSM());
        return json;
    }
    
    public JsonNode mapEventReportSMS(EventReportSMSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "eventTypeSMS", request.getEventTypeSMS());
        return json;
    }
    
    public JsonNode mapApplyChargingReport(ApplyChargingReportRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "callResult", request.getTimeDurationChargingResult());
        return json;
    }
    
    public JsonNode mapCallInformationReport(CallInformationReportRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "requestedInformationList", request.getRequestedInformationList());
        return json;
    }
    
    public JsonNode mapSpecializedResourceReport(SpecializedResourceReportRequest request) {
        ObjectNode json = mapper.createObjectNode();
        return json;
    }
    
    public JsonNode mapResetTimerSMS(ResetTimerSMSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "timerID", request.getTimerID());
        putHex(json, "timerValue", request.getTimerValue());
        return json;
    }
    
    public JsonNode mapInitialDPGPRS(InitialDpGprsRequest request) {
        ObjectNode json = mapper.createObjectNode();
        json.put("serviceKey", request.getServiceKey());
        putHex(json, "gprsEventType", request.getGPRSEventType());
        putHex(json, "msisdn", request.getMsisdn());
        putHex(json, "imsi", request.getImsi());
        putHex(json, "ggsnAddress", request.getGSNAddress());
        putHex(json, "imei", request.getImei());
        return json;
    }
    
    public JsonNode mapApplyChargingReportGPRS(ApplyChargingReportGPRSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "chargingResult", request.getChargingResult());
        return json;
    }
    
    public JsonNode mapEntityReleasedGPRS(EntityReleasedGPRSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "gprsCause", request.getGPRSCause());
        return json;
    }

    private void putHex(ObjectNode json, String fieldName, Object value) {
        if (value == null) return;
        String hex = toHex(value);
        if (hex != null) {
            json.put(fieldName, hex);
        } else {
            json.put(fieldName, value.toString());
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

    public Object createFromHex(CAPParameterFactory pf, String type, JsonNode node) {
        if (node == null || node.isNull()) return null;
        String s = node.asText();
        byte[] data = hexToBytes(s);
        if (data == null) return null;

        try {
            switch (type) {
                // Primitive Types
                case "ChargingCharacteristics": return pf.createChargingCharacteristics(hexToInt(data)); 
                
                // GPRS Types
                case "AccessPointName": return pf.createAccessPointName(Unpooled.wrappedBuffer(data));
                case "PDPID": return pf.createPDPID(hexToInt(data));
                case "GPRSCause": return pf.createGPRSCause(hexToInt(data));

                default: 
                    logger.warn("createFromHex: Type {} requires complex decoding not implemented.", type);
                    return null;
            }
        } catch (Exception e) {
            logger.warn("Failed to create CAP object for {}", type, e);
            return null;
        }
    }

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
    
    public static int hexToInt(byte[] b) {
        if (b == null || b.length == 0) return 0;
        int value = 0;
        for (byte by : b) {
            value = (value << 8) | (by & 0xFF);
        }
        return value;
    }
}
