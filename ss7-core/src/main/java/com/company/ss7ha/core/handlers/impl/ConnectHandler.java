package com.company.ss7ha.core.handlers.impl;

import com.company.ss7ha.core.api.MessageHandler;
import com.company.ss7ha.core.mapper.CapJsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.restcomm.protocols.ss7.cap.api.CAPDialog;
import org.restcomm.protocols.ss7.cap.api.CAPParameterFactory;
import org.restcomm.protocols.ss7.cap.api.CAPProvider;
import org.restcomm.protocols.ss7.cap.api.isup.CalledPartyNumber;
import org.restcomm.protocols.ss7.cap.api.isup.CallingPartyNumber;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.CAPServiceCircuitSwitchedCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class ConnectHandler implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(ConnectHandler.class);
    private final CapJsonMapper mapper = new CapJsonMapper();

    @Override
    public void handle(JsonNode payload, CAPDialog dialog, CAPProvider provider) throws Exception {
        CAPParameterFactory pf = provider.getCAPParameterFactory();
        CAPServiceCircuitSwitchedCall service = provider.getCAPServiceCircuitSwitchedCall();

        // Mandatory: Destination Routing Address
        String destNum = payload.path("destinationRoutingAddress").asText();
        CalledPartyNumber dest = pf.createCalledPartyNumber(destNum, 
            org.restcomm.protocols.ss7.cap.api.isup.enums.NatureOfAddress.INTERNATIONAL,
            org.restcomm.protocols.ss7.cap.api.isup.enums.NumberingPlan.ISDN_TELEPHONY);

        // Optional Parameters
        CallingPartyNumber calling = null;
        if (payload.has("callingPartyNumber")) {
             String callingParty = payload.path("callingPartyNumber").asText();
             calling = pf.createCallingPartyNumber(callingParty,
                org.restcomm.protocols.ss7.cap.api.isup.enums.NatureOfAddress.INTERNATIONAL,
                org.restcomm.protocols.ss7.cap.api.isup.enums.NumberingPlan.ISDN_TELEPHONY,
                org.restcomm.protocols.ss7.cap.api.isup.enums.AddressRepresentationRestrictedIndicator.PRESENTATION_ALLOWED,
                org.restcomm.protocols.ss7.cap.api.isup.enums.ScreeningIndicator.USER_PROVIDED_NOT_VERIFIED);
        }

        // Helper to parse hex parameters using CapJsonMapper
        org.restcomm.protocols.ss7.cap.api.isup.OriginalCalledPartyID originalCalled = 
            (org.restcomm.protocols.ss7.cap.api.isup.OriginalCalledPartyID) mapper.createFromHex(pf, "OriginalCalledPartyID", payload.get("originalCalledPartyID"));
            
        org.restcomm.protocols.ss7.cap.api.isup.RedirectingPartyID redirectingParty = 
            (org.restcomm.protocols.ss7.cap.api.isup.RedirectingPartyID) mapper.createFromHex(pf, "RedirectingPartyID", payload.get("redirectingPartyID"));

        org.restcomm.protocols.ss7.cap.api.isup.RedirectionInformation redirectionInfo = 
            (org.restcomm.protocols.ss7.cap.api.isup.RedirectionInformation) mapper.createFromHex(pf, "RedirectionInformation", payload.get("redirectionInformation"));

        org.restcomm.protocols.ss7.cap.api.primitives.ServiceInteractionIndicatorsTwo sii2 = 
            (org.restcomm.protocols.ss7.cap.api.primitives.ServiceInteractionIndicatorsTwo) mapper.createFromHex(pf, "ServiceInteractionIndicatorsTwo", payload.get("serviceInteractionIndicatorsTwo"));

        org.restcomm.protocols.ss7.cap.api.isup.ChargeNumber chargeNumber = 
            (org.restcomm.protocols.ss7.cap.api.isup.ChargeNumber) mapper.createFromHex(pf, "ChargeNumber", payload.get("chargeNumber"));
        
        org.restcomm.protocols.ss7.cap.api.isup.CallingPartysCategory cpc = 
            (org.restcomm.protocols.ss7.cap.api.isup.CallingPartysCategory) mapper.createFromHex(pf, "CallingPartysCategory", payload.get("callingPartysCategory"));

        org.restcomm.protocols.ss7.cap.api.primitives.AlertingPattern alertingPattern = 
            (org.restcomm.protocols.ss7.cap.api.primitives.AlertingPattern) mapper.createFromHex(pf, "AlertingPattern", payload.get("alertingPattern"));

        org.restcomm.protocols.ss7.cap.api.primitives.Carrier carrier = 
            (org.restcomm.protocols.ss7.cap.api.primitives.Carrier) mapper.createFromHex(pf, "Carrier", payload.get("carrier"));

        org.restcomm.protocols.ss7.cap.api.primitives.CUGInterlock cugInterlock = 
            (org.restcomm.protocols.ss7.cap.api.primitives.CUGInterlock) mapper.createFromHex(pf, "CUGInterlock", payload.get("cugInterlock"));

        org.restcomm.protocols.ss7.cap.api.primitives.SuppressionOfAnnouncement suppressionOfAnnouncement = 
            (org.restcomm.protocols.ss7.cap.api.primitives.SuppressionOfAnnouncement) mapper.createFromHex(pf, "SuppressionOfAnnouncement", payload.get("suppressionOfAnnouncement"));

        org.restcomm.protocols.ss7.cap.api.primitives.OCSIApplicable oCsiApplicable = 
            (org.restcomm.protocols.ss7.cap.api.primitives.OCSIApplicable) mapper.createFromHex(pf, "OCSIApplicable", payload.get("oCsiApplicable"));

        org.restcomm.protocols.ss7.cap.api.primitives.NAOliInfo naOliInfo = 
            (org.restcomm.protocols.ss7.cap.api.primitives.NAOliInfo) mapper.createFromHex(pf, "NAOliInfo", payload.get("naOliInfo"));

        byte[] extensions = payload.has("extensions") ? CapJsonMapper.hexToBytes(payload.get("extensions").asText()) : null;
        
        ArrayList<org.restcomm.protocols.ss7.cap.api.isup.GenericNumber> genericNumbers = null;
        if (payload.has("genericNumbers") && payload.get("genericNumbers").isArray()) {
            genericNumbers = new ArrayList<>();
            for (JsonNode node : payload.get("genericNumbers")) {
                genericNumbers.add(pf.createGenericNumber(CapJsonMapper.hexToBytes(node.asText())));
            }
        }

        // Implicit parameters
        Object cugOutgoingAccess = payload.has("cugOutgoingAccess") ? new Object() : null; 
        Object borInterrogationRequested = payload.has("borInterrogationRequested") ? new Object() : null;
        Object suppressNCsi = payload.has("suppressNCsi") ? new Object() : null;

        service.addConnectRequest(dialog, dest, 
            alertingPattern, 
            originalCalled, 
            extensions, 
            carrier, 
            cpc, 
            redirectingParty, 
            redirectionInfo, 
            genericNumbers, 
            sii2, 
            chargeNumber, 
            null, 
            cugInterlock, 
            cugOutgoingAccess, 
            suppressionOfAnnouncement, 
            oCsiApplicable, 
            naOliInfo, 
            borInterrogationRequested, 
            suppressNCsi,
            calling 
        );
        logger.info("Executing CONNECT to {}", destNum);
    }
}
