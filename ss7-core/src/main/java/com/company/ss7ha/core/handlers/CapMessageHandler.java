package com.company.ss7ha.core.handlers;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.mapper.CapJsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.restcomm.protocols.ss7.cap.api.CAPDialog;
import org.restcomm.protocols.ss7.cap.api.CAPParameterFactory;
import org.restcomm.protocols.ss7.cap.api.CAPProvider;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.CAPDialogCircuitSwitchedCall;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.CAPServiceCircuitSwitchedCall;
import org.restcomm.protocols.ss7.cap.api.service.sms.CAPDialogSms;
import org.restcomm.protocols.ss7.cap.api.service.sms.CAPServiceSms;
import org.restcomm.protocols.ss7.cap.api.service.gprs.CAPDialogGprs;
import org.restcomm.protocols.ss7.cap.api.service.gprs.CAPServiceGprs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CapMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(CapMessageHandler.class);
    private final CAPProvider capProvider;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final CapJsonMapper mapper;

    public CapMessageHandler(CAPProvider capProvider, EventPublisher eventPublisher) {
        this.capProvider = capProvider;
        this.eventPublisher = eventPublisher;
        this.objectMapper = new ObjectMapper();
        this.mapper = new CapJsonMapper();
    }

    public void handleCommand(String jsonCommand) {
        try {
            JsonNode root = objectMapper.readTree(jsonCommand);
            String type = root.path("type").asText();
            long dialogId = root.path("dialogId").asLong();
            JsonNode payload = root.path("payload");

            logger.debug("Received CAP command: {} for dialog: {}", type, dialogId);

            CAPDialog dialog = capProvider.getCAPDialog(dialogId);
            if (dialog == null) {
                logger.error("CAP Dialog not found: {}", dialogId);
                return;
            }

            CAPDialogCircuitSwitchedCall csDialog = (dialog instanceof CAPDialogCircuitSwitchedCall) ? (CAPDialogCircuitSwitchedCall) dialog : null;
            CAPDialogSms smsDialog = (dialog instanceof CAPDialogSms) ? (CAPDialogSms) dialog : null;
            CAPDialogGprs gprsDialog = (dialog instanceof CAPDialogGprs) ? (CAPDialogGprs) dialog : null;

            switch (type.toUpperCase()) {
                // Voice
                case "CONNECT": if (csDialog != null) handleConnect(csDialog, payload); break;
                case "CONTINUE": if (csDialog != null) handleContinue(csDialog); break;
                // ... (Other Voice)

                // GPRS
                case "CONNECT_GPRS": if (gprsDialog != null) handleConnectGPRS(gprsDialog, payload); break;
                case "CONTINUE_GPRS": if (gprsDialog != null) handleContinueGPRS(gprsDialog, payload); break;
                case "RELEASE_GPRS": if (gprsDialog != null) handleReleaseGPRS(gprsDialog, payload); break;
                case "APPLY_CHARGING_GPRS": if (gprsDialog != null) handleApplyChargingGPRS(gprsDialog, payload); break;
                
                default: logger.warn("Unknown CAP command type: {}", type);
            }
            
            dialog.send(null);

        } catch (Exception e) {
            logger.error("Error processing CAP command", e);
        }
    }

    // --- Voice Methods ---
    private void handleConnect(CAPDialogCircuitSwitchedCall dialog, JsonNode payload) throws Exception {
        // Stubbed for now as ConnectHandler has the logic
        logger.info("Executing CONNECT");
    }
    private void handleContinue(CAPDialogCircuitSwitchedCall dialog) throws Exception {
        dialog.addContinueRequest();
        logger.info("Executing CONTINUE");
    }
    // ...

    // --- GPRS Methods ---
    private void handleConnectGPRS(CAPDialogGprs dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.AccessPointName apn = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.AccessPointName) mapper.createFromHex(pf, "AccessPointName", payload.get("accessPointName"));
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID pdpid = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID) mapper.createFromHex(pf, "PDPID", payload.get("pdpId"));
        dialog.addConnectGPRSRequest(apn, pdpid);
        logger.info("Executing CONNECT_GPRS");
    }

    private void handleContinueGPRS(CAPDialogGprs dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID pdpid = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID) mapper.createFromHex(pf, "PDPID", payload.get("pdpId"));
        dialog.addContinueGPRSRequest(pdpid);
        logger.info("Executing CONTINUE_GPRS");
    }

    private void handleReleaseGPRS(CAPDialogGprs dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.GPRSCause cause = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.GPRSCause) mapper.createFromHex(pf, "GPRSCause", payload.get("gprsCause"));
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID pdpid = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID) mapper.createFromHex(pf, "PDPID", payload.get("pdpId"));
        dialog.addReleaseGPRSRequest(cause, pdpid);
        logger.info("Executing RELEASE_GPRS");
    }

    private void handleApplyChargingGPRS(CAPDialogGprs dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.ChargingCharacteristics cc = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.ChargingCharacteristics) mapper.createFromHex(pf, "ChargingCharacteristics", payload.get("chargingCharacteristics"));
        
        // Pass 0 if null to satisfy int signature
        int tariffSwitch = payload.has("tariffSwitchInterval") ? payload.get("tariffSwitchInterval").asInt() : 0;
        
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID pdpid = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID) mapper.createFromHex(pf, "PDPID", payload.get("pdpId"));
        dialog.addApplyChargingGPRSRequest(cc, tariffSwitch, pdpid);
        logger.info("Executing APPLY_CHARGING_GPRS");
    }
}