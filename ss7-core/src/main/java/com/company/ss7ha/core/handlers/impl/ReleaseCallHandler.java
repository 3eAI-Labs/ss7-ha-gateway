package com.company.ss7ha.core.handlers.impl;

import com.company.ss7ha.core.api.MessageHandler;
import com.company.ss7ha.core.mapper.CapJsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.restcomm.protocols.ss7.cap.api.CAPDialog;
import org.restcomm.protocols.ss7.cap.api.CAPParameterFactory;
import org.restcomm.protocols.ss7.cap.api.CAPProvider;
import org.restcomm.protocols.ss7.cap.api.isup.Cause;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.CAPServiceCircuitSwitchedCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReleaseCallHandler implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseCallHandler.class);
    private final CapJsonMapper mapper = new CapJsonMapper();

    @Override
    public void handle(JsonNode payload, CAPDialog dialog, CAPProvider provider) throws Exception {
        CAPServiceCircuitSwitchedCall service = provider.getCAPServiceCircuitSwitchedCall();
        int causeCode = payload.path("cause").asInt(31); 
        CAPParameterFactory paramFactory = provider.getCAPParameterFactory();
        Cause cause = paramFactory.createCause(mapper.mapCause(causeCode));
        
        service.addReleaseCallRequest(dialog, cause);
        logger.info("Executing RELEASE_CALL with cause {}", causeCode);
    }
}
