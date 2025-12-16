package com.company.ss7ha.core.handlers.impl;

import com.company.ss7ha.core.api.MessageHandler;
import com.company.ss7ha.core.mapper.CapJsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.restcomm.protocols.ss7.cap.api.CAPDialog;
import org.restcomm.protocols.ss7.cap.api.CAPParameterFactory;
import org.restcomm.protocols.ss7.cap.api.CAPProvider;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.CAPDialogCircuitSwitchedCall;
import org.restcomm.protocols.ss7.commonapp.api.isup.CauseIsup;
import org.restcomm.protocols.ss7.isup.ISUPParameterFactory;
import org.restcomm.protocols.ss7.isup.message.parameter.CauseIndicators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReleaseCallHandler implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseCallHandler.class);
    private final CapJsonMapper mapper = new CapJsonMapper();

    @Override
    public void handle(JsonNode payload, CAPDialog dialog, CAPProvider provider) throws Exception {
        ISUPParameterFactory ipf = provider.getISUPParameterFactory();
        CAPParameterFactory pf = provider.getCAPParameterFactory();
        
        if (!(dialog instanceof CAPDialogCircuitSwitchedCall)) {
            logger.error("Dialog is not a CircuitSwitchedCall dialog");
            return;
        }
        CAPDialogCircuitSwitchedCall csDialog = (CAPDialogCircuitSwitchedCall) dialog;
        
        int causeCode = payload.path("cause").asInt(31); 
        
        CauseIndicators isupCause = ipf.createCauseIndicators();
        isupCause.setCodingStandard(CauseIndicators._CODING_STANDARD_ITUT);
        isupCause.setLocation(CauseIndicators._LOCATION_USER);
        isupCause.setCauseValue(causeCode);
        
        CauseIsup cause = pf.createCause(isupCause);
        
        csDialog.addReleaseCallRequest(cause);
        logger.info("Executing RELEASE_CALL with cause {}", causeCode);
    }
}