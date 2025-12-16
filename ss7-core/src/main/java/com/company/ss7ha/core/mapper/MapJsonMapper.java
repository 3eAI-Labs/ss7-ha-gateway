package com.company.ss7ha.core.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Generic Mapper for MAP Messages <-> JSON.
 * Implements "Hex Passthrough" strategy for all MAP operations.
 */
public class MapJsonMapper {

    private static final Logger logger = LoggerFactory.getLogger(MapJsonMapper.class);
    private final ObjectMapper mapper;

    public MapJsonMapper() {
        this.mapper = new ObjectMapper();
    }

    // ==================================================================================
    // SMS MAPPING
    // ==================================================================================

    public JsonNode mapMoForwardShortMessage(org.restcomm.protocols.ss7.map.api.service.sms.MoForwardShortMessageRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "smRPDA", request.getSM_RP_DA());
        putHex(json, "smRPOA", request.getSM_RP_OA());
        putHex(json, "smRPUI", request.getSM_RP_UI()); // TPDU is here
        putHex(json, "extensionContainer", request.getExtensionContainer());
        putHex(json, "imsi", request.getIMSI());
        return json;
    }

    public JsonNode mapMoForwardShortMessageResponse(org.restcomm.protocols.ss7.map.api.service.sms.MoForwardShortMessageResponse response) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "smRPUI", response.getSM_RP_UI());
        putHex(json, "extensionContainer", response.getExtensionContainer());
        return json;
    }

    public JsonNode mapMtForwardShortMessage(org.restcomm.protocols.ss7.map.api.service.sms.MtForwardShortMessageRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "smRPDA", request.getSM_RP_DA()); // IMSI/LMSI
        putHex(json, "smRPOA", request.getSM_RP_OA()); // SMSC Addr
        putHex(json, "smRPUI", request.getSM_RP_UI()); // TPDU
        putHex(json, "moreMessagesToSend", request.getMoreMessagesToSend());
        return json;
    }

    public JsonNode mapMtForwardShortMessageResponse(org.restcomm.protocols.ss7.map.api.service.sms.MtForwardShortMessageResponse response) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "smRPUI", response.getSM_RP_UI());
        return json;
    }
    
    public JsonNode mapSendRoutingInfoForSM(org.restcomm.protocols.ss7.map.api.service.sms.SendRoutingInfoForSMRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "msisdn", request.getMsisdn());
        putHex(json, "smRPPRI", request.getSM_RP_PRI());
        putHex(json, "serviceCentreAddress", request.getServiceCentreAddress());
        return json;
    }

    // ==================================================================================
    // MOBILITY MAPPING (Location, Auth)
    // ==================================================================================

    public JsonNode mapUpdateLocation(org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.UpdateLocationRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "imsi", request.getImsi());
        putHex(json, "mscNumber", request.getMscNumber());
        putHex(json, "vlrNumber", request.getVlrNumber());
        putHex(json, "lmsi", request.getLmsi());
        putHex(json, "extensionContainer", request.getExtensionContainer());
        return json;
    }

    public JsonNode mapUpdateLocationResponse(org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.UpdateLocationResponse response) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "hlrNumber", response.getHlrNumber());
        return json;
    }

    public JsonNode mapUpdateGprsLocation(org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.UpdateGprsLocationRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "imsi", request.getImsi());
        putHex(json, "sgsnNumber", request.getSgsnNumber());
        putHex(json, "sgsnAddress", request.getSgsnAddress());
        return json;
    }

    public JsonNode mapUpdateGprsLocationResponse(org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.UpdateGprsLocationResponse response) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "hlrNumber", response.getHlrNumber());
        return json;
    }

    public JsonNode mapSendAuthenticationInfo(org.restcomm.protocols.ss7.map.api.service.mobility.authentication.SendAuthenticationInfoRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "imsi", request.getImsi());
        putHex(json, "numberOfRequestedVectors", request.getNumberOfRequestedVectors());
        putHex(json, "segmentationProhibited", request.getSegmentationProhibited());
        return json;
    }

    public JsonNode mapSendAuthenticationInfoResponse(org.restcomm.protocols.ss7.map.api.service.mobility.authentication.SendAuthenticationInfoResponse response) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "authenticationSetList", response.getAuthenticationSetList());
        return json;
    }

    // ==================================================================================
    // USSD MAPPING
    // ==================================================================================

    public JsonNode mapProcessUnstructuredSSRequest(org.restcomm.protocols.ss7.map.api.service.supplementary.ProcessUnstructuredSSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "ussdDataCodingScheme", request.getUSSDDataCodingScheme());
        putHex(json, "ussdString", request.getUSSDString());
        putHex(json, "msisdn", request.getMSISDN());
        return json;
    }

    public JsonNode mapProcessUnstructuredSSResponse(org.restcomm.protocols.ss7.map.api.service.supplementary.ProcessUnstructuredSSResponse response) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "ussdDataCodingScheme", response.getUSSDDataCodingScheme());
        putHex(json, "ussdString", response.getUSSDString());
        return json;
    }

    public JsonNode mapUnstructuredSSRequest(org.restcomm.protocols.ss7.map.api.service.supplementary.UnstructuredSSRequest request) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "ussdDataCodingScheme", request.getUSSDDataCodingScheme());
        putHex(json, "ussdString", request.getUSSDString());
        return json;
    }

    public JsonNode mapUnstructuredSSResponse(org.restcomm.protocols.ss7.map.api.service.supplementary.UnstructuredSSResponse response) {
        ObjectNode json = mapper.createObjectNode();
        putHex(json, "ussdDataCodingScheme", response.getUSSDDataCodingScheme());
        putHex(json, "ussdString", response.getUSSDString());
        return json;
    }

    // ==================================================================================
    // UTILS (Same as CapJsonMapper)
    // ==================================================================================

    private void putHex(ObjectNode json, String fieldName, Object value) {
        if (value == null) return;
        String hex = toHex(value);
        if (hex != null) {
            json.put(fieldName, hex);
        } else {
            // Primitive fallback
            json.put(fieldName, value.toString());
        }
    }

    private String toHex(Object value) {
        try {
            Method getDataMethod = value.getClass().getMethod("getData");
            byte[] data = (byte[]) getDataMethod.invoke(value);
            return bytesToHex(data);
        } catch (Exception e) {
            try {
                // Some JSS7 types like IMSI have getData() but return String or other types? 
                // Or maybe they don't have getData but have specific accessors.
                // For this generic mapper, we rely on getData returning byte[] or simple types toString.
                return null; 
            } catch (Exception ex) {
                return null;
            }
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
}
