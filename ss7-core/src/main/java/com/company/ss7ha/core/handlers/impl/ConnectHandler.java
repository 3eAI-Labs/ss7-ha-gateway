package com.company.ss7ha.core.handlers.impl;

import com.company.ss7ha.core.api.MessageHandler;
import com.company.ss7ha.core.mapper.CapJsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.restcomm.protocols.ss7.cap.api.CAPDialog;
import org.restcomm.protocols.ss7.cap.api.CAPParameterFactory;
import org.restcomm.protocols.ss7.cap.api.CAPProvider;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.CAPDialogCircuitSwitchedCall;
import org.restcomm.protocols.ss7.isup.ISUPParameterFactory;
import org.restcomm.protocols.ss7.isup.message.parameter.CalledPartyNumber;
import org.restcomm.protocols.ss7.isup.message.parameter.NAINumber;
import org.restcomm.protocols.ss7.commonapp.api.isup.CalledPartyNumberIsup;
import org.restcomm.protocols.ss7.commonapp.api.circuitSwitchedCall.DestinationRoutingAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

public class ConnectHandler implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(ConnectHandler.class);
    private final CapJsonMapper mapper = new CapJsonMapper();

    @Override
    public void handle(JsonNode payload, CAPDialog dialog, CAPProvider provider) throws Exception {
        CAPParameterFactory pf = provider.getCAPParameterFactory();
        ISUPParameterFactory ipf = provider.getISUPParameterFactory();
        
        // Cast to correct dialog type
        if (!(dialog instanceof CAPDialogCircuitSwitchedCall)) {
            logger.error("Dialog is not a CircuitSwitchedCall dialog");
            return;
        }
        CAPDialogCircuitSwitchedCall csDialog = (CAPDialogCircuitSwitchedCall) dialog;

        String destNum = payload.path("destinationRoutingAddress").asText();
        
        CalledPartyNumber cpn = ipf.createCalledPartyNumber();
        cpn.setAddress(destNum);
        cpn.setNatureOfAddresIndicator(NAINumber._NAI_INTERNATIONAL_NUMBER);
        cpn.setNumberingPlanIndicator(CalledPartyNumber._NPI_ISDN);

        CalledPartyNumberIsup cpnIsup = pf.createCalledPartyNumber(cpn);
        DestinationRoutingAddress dra = pf.createDestinationRoutingAddress(Collections.singletonList(cpnIsup));

        // Use correct method on Dialog, NOT Service
        csDialog.addConnectRequest(
            dra,    // DestinationRoutingAddress
            null,   // AlertingPattern
            null,   // OriginalCalledNumberIsup
            null,   // CAPINAPExtensions
            null,   // Carrier
            null,   // CallingPartysCategoryIsup
            null,   // RedirectingPartyIDIsup
            null,   // RedirectionInformationIsup
            null,   // GenericNumberIsup List
            null,   // ServiceInteractionIndicatorsTwo
            null,   // LocationNumberIsup
            null,   // LegID
            null,   // CUGInterlock
            false,  // cugOutgoingAccess
            false,  // borInterrogationRequested
            false,  // suppressNCsi
            null,   // NAOliInfo
            false,  // chargeNumber
            false   // continue
        );
        
        logger.info("Executing CONNECT to {}", destNum);
    }
}
