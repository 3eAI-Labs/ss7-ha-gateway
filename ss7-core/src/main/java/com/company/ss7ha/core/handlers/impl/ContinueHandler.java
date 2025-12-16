package com.company.ss7ha.core.handlers.impl;

import com.company.ss7ha.core.api.MessageHandler;
import com.company.ss7ha.core.mapper.CapJsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.restcomm.protocols.ss7.cap.api.CAPDialog;
import org.restcomm.protocols.ss7.cap.api.CAPProvider;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.CAPDialogCircuitSwitchedCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContinueHandler implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(ContinueHandler.class);

    @Override
    public void handle(JsonNode payload, CAPDialog dialog, CAPProvider provider) throws Exception {
        if (dialog instanceof CAPDialogCircuitSwitchedCall) {
            ((CAPDialogCircuitSwitchedCall) dialog).addContinueRequest();
            logger.info("Executing CONTINUE");
        } else {
            logger.error("Dialog is not a CircuitSwitchedCall dialog");
        }
    }
}