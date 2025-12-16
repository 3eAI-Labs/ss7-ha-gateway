package com.company.ss7ha.core.handlers;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.mapper.CapJsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.restcomm.protocols.ss7.cap.api.CAPDialog;
import org.restcomm.protocols.ss7.cap.api.CAPParameterFactory;
import org.restcomm.protocols.ss7.cap.api.CAPProvider;
import org.restcomm.protocols.ss7.cap.api.isup.CalledPartyNumber;
import org.restcomm.protocols.ss7.cap.api.isup.CallingPartyNumber;
import org.restcomm.protocols.ss7.cap.api.isup.Cause;
import org.restcomm.protocols.ss7.cap.api.primitives.BCSMEvent;
import org.restcomm.protocols.ss7.cap.api.primitives.EventTypeBCSM;
import org.restcomm.protocols.ss7.cap.api.primitives.MonitorMode;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.CAPServiceCircuitSwitchedCall;
import org.restcomm.protocols.ss7.cap.api.service.sms.CAPServiceSms;
import org.restcomm.protocols.ss7.cap.api.service.gprs.CAPServiceGprs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles outgoing CAP (CAMEL) commands from NATS.
 * Bridges JSON commands (Business Logic) to JSS7 CAP Provider.
 * Supports Voice, SMS and GPRS Control commands with Hex Passthrough.
 */
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

            CAPServiceCircuitSwitchedCall service = capProvider.getCAPServiceCircuitSwitchedCall();
            CAPServiceSms smsService = capProvider.getCAPServiceSms();
            CAPServiceGprs gprsService = capProvider.getCAPServiceGprs();

            switch (type.toUpperCase()) {
                // Voice Control
                case "CONNECT":
                    handleConnect(service, dialog, payload);
                    break;
                case "CONTINUE":
                    handleContinue(service, dialog);
                    break;
                case "CONTINUE_WITH_ARGUMENT":
                    handleContinueWithArgument(service, dialog, payload);
                    break;
                case "RELEASE_CALL":
                    handleReleaseCall(service, dialog, payload);
                    break;
                case "REQUEST_REPORT_BCSM":
                    handleRequestReportBCSM(service, dialog, payload);
                    break;
                case "DISCONNECT_FORWARD_CONNECTION":
                    handleDisconnectForwardConnection(service, dialog);
                    break;
                case "APPLY_CHARGING":
                    handleApplyCharging(service, dialog, payload);
                    break;
                case "FURNISH_CHARGING_INFORMATION":
                    handleFurnishChargingInformation(service, dialog, payload);
                    break;
                case "SEND_CHARGING_INFORMATION":
                    handleSendChargingInformation(service, dialog, payload);
                    break;
                case "CALL_INFORMATION_REQUEST":
                    handleCallInformationRequest(service, dialog, payload);
                    break;

                // SMS Control
                case "CONNECT_SMS":
                    handleConnectSMS(smsService, dialog, payload);
                    break;
                case "CONTINUE_SMS":
                    handleContinueSMS(smsService, dialog);
                    break;
                case "RELEASE_SMS":
                    handleReleaseSMS(smsService, dialog, payload);
                    break;
                case "REQUEST_REPORT_SMS_EVENT":
                    handleRequestReportSMSEvent(smsService, dialog, payload);
                    break;
                case "FURNISH_CHARGING_INFORMATION_SMS":
                    handleFurnishChargingInformationSMS(smsService, dialog, payload);
                    break;
                case "RESET_TIMER_SMS":
                    handleResetTimerSMS(smsService, dialog, payload);
                    break;

                // GPRS Control
                case "CONNECT_GPRS":
                    handleConnectGPRS(gprsService, dialog, payload);
                    break;
                case "CONTINUE_GPRS":
                    handleContinueGPRS(gprsService, dialog, payload);
                    break;
                case "RELEASE_GPRS":
                    handleReleaseGPRS(gprsService, dialog, payload);
                    break;
                case "APPLY_CHARGING_GPRS":
                    handleApplyChargingGPRS(gprsService, dialog, payload);
                    break;

                default:
                    logger.warn("Unknown CAP command type: {}", type);
            }
            
            dialog.send();

        } catch (Exception e) {
            logger.error("Error processing CAP command", e);
        }
    }

    // --- Voice Methods ---

    private void handleConnect(CAPServiceCircuitSwitchedCall service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        String destNum = payload.path("destinationRoutingAddress").asText();
        CalledPartyNumber dest = pf.createCalledPartyNumber(destNum, 
            org.restcomm.protocols.ss7.cap.api.isup.enums.NatureOfAddress.INTERNATIONAL,
            org.restcomm.protocols.ss7.cap.api.isup.enums.NumberingPlan.ISDN_TELEPHONY);

        CallingPartyNumber calling = null;
        if (payload.has("callingPartyNumber")) {
             String callingParty = payload.path("callingPartyNumber").asText();
             calling = pf.createCallingPartyNumber(callingParty,
                org.restcomm.protocols.ss7.cap.api.isup.enums.NatureOfAddress.INTERNATIONAL,
                org.restcomm.protocols.ss7.cap.api.isup.enums.NumberingPlan.ISDN_TELEPHONY,
                org.restcomm.protocols.ss7.cap.api.isup.enums.AddressRepresentationRestrictedIndicator.PRESENTATION_ALLOWED,
                org.restcomm.protocols.ss7.cap.api.isup.enums.ScreeningIndicator.USER_PROVIDED_NOT_VERIFIED);
        }

        org.restcomm.protocols.ss7.cap.api.isup.OriginalCalledPartyID originalCalled = 
            (org.restcomm.protocols.ss7.cap.api.isup.OriginalCalledPartyID) createFromHex(pf, "OriginalCalledPartyID", payload);
            
        org.restcomm.protocols.ss7.cap.api.isup.RedirectingPartyID redirectingParty = 
            (org.restcomm.protocols.ss7.cap.api.isup.RedirectingPartyID) createFromHex(pf, "RedirectingPartyID", payload);

        org.restcomm.protocols.ss7.cap.api.isup.RedirectionInformation redirectionInfo = 
            (org.restcomm.protocols.ss7.cap.api.isup.RedirectionInformation) createFromHex(pf, "RedirectionInformation", payload);

        org.restcomm.protocols.ss7.cap.api.primitives.ServiceInteractionIndicatorsTwo sii2 = 
            (org.restcomm.protocols.ss7.cap.api.primitives.ServiceInteractionIndicatorsTwo) createFromHex(pf, "ServiceInteractionIndicatorsTwo", payload);

        org.restcomm.protocols.ss7.cap.api.isup.ChargeNumber chargeNumber = 
            (org.restcomm.protocols.ss7.cap.api.isup.ChargeNumber) createFromHex(pf, "ChargeNumber", payload);
        
        org.restcomm.protocols.ss7.cap.api.isup.CallingPartysCategory cpc = 
            (org.restcomm.protocols.ss7.cap.api.isup.CallingPartysCategory) createFromHex(pf, "CallingPartysCategory", payload);

        org.restcomm.protocols.ss7.cap.api.primitives.AlertingPattern alertingPattern = 
            (org.restcomm.protocols.ss7.cap.api.primitives.AlertingPattern) createFromHex(pf, "AlertingPattern", payload);

        org.restcomm.protocols.ss7.cap.api.primitives.Carrier carrier = 
            (org.restcomm.protocols.ss7.cap.api.primitives.Carrier) createFromHex(pf, "Carrier", payload);

        org.restcomm.protocols.ss7.cap.api.primitives.CUGInterlock cugInterlock = 
            (org.restcomm.protocols.ss7.cap.api.primitives.CUGInterlock) createFromHex(pf, "CUGInterlock", payload);

        org.restcomm.protocols.ss7.cap.api.primitives.SuppressionOfAnnouncement suppressionOfAnnouncement = 
            (org.restcomm.protocols.ss7.cap.api.primitives.SuppressionOfAnnouncement) createFromHex(pf, "SuppressionOfAnnouncement", payload);

        org.restcomm.protocols.ss7.cap.api.primitives.OCSIApplicable oCsiApplicable = 
            (org.restcomm.protocols.ss7.cap.api.primitives.OCSIApplicable) createFromHex(pf, "OCSIApplicable", payload);

        org.restcomm.protocols.ss7.cap.api.primitives.NAOliInfo naOliInfo = 
            (org.restcomm.protocols.ss7.cap.api.primitives.NAOliInfo) createFromHex(pf, "NAOliInfo", payload);

        byte[] extensions = payload.has("extensions") ? hexToBytes(payload.get("extensions").asText()) : null;
        
        ArrayList<org.restcomm.protocols.ss7.cap.api.isup.GenericNumber> genericNumbers = null;
        if (payload.has("genericNumbers") && payload.get("genericNumbers").isArray()) {
            genericNumbers = new ArrayList<>();
            for (JsonNode node : payload.get("genericNumbers")) {
                genericNumbers.add(pf.createGenericNumber(hexToBytes(node.asText())));
            }
        }

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

    private void handleContinue(CAPServiceCircuitSwitchedCall service, CAPDialog dialog) throws Exception {
        service.addContinueRequest(dialog);
        logger.info("Executing CONTINUE");
    }
    
    private void handleContinueWithArgument(CAPServiceCircuitSwitchedCall service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.primitives.AlertingPattern alertingPattern = 
            (org.restcomm.protocols.ss7.cap.api.primitives.AlertingPattern) createFromHex(pf, "AlertingPattern", payload);

        org.restcomm.protocols.ss7.cap.api.primitives.ServiceInteractionIndicatorsTwo sii2 = 
            (org.restcomm.protocols.ss7.cap.api.primitives.ServiceInteractionIndicatorsTwo) createFromHex(pf, "ServiceInteractionIndicatorsTwo", payload);

        org.restcomm.protocols.ss7.cap.api.isup.ChargeNumber chargeNumber = 
            (org.restcomm.protocols.ss7.cap.api.isup.ChargeNumber) createFromHex(pf, "ChargeNumber", payload);

        org.restcomm.protocols.ss7.cap.api.isup.CallingPartysCategory cpc = 
            (org.restcomm.protocols.ss7.cap.api.isup.CallingPartysCategory) createFromHex(pf, "CallingPartysCategory", payload);
            
        org.restcomm.protocols.ss7.cap.api.primitives.Carrier carrier = 
            (org.restcomm.protocols.ss7.cap.api.primitives.Carrier) createFromHex(pf, "Carrier", payload);
            
        org.restcomm.protocols.ss7.cap.api.primitives.SuppressionOfAnnouncement suppressionOfAnnouncement = 
            (org.restcomm.protocols.ss7.cap.api.primitives.SuppressionOfAnnouncement) createFromHex(pf, "SuppressionOfAnnouncement", payload);
            
        org.restcomm.protocols.ss7.cap.api.primitives.NAOliInfo naOliInfo = 
            (org.restcomm.protocols.ss7.cap.api.primitives.NAOliInfo) createFromHex(pf, "NAOliInfo", payload);

        byte[] extensions = payload.has("extensions") ? hexToBytes(payload.get("extensions").asText()) : null;

        ArrayList<org.restcomm.protocols.ss7.cap.api.isup.GenericNumber> genericNumbers = null;
        if (payload.has("genericNumbers") && payload.get("genericNumbers").isArray()) {
            genericNumbers = new ArrayList<>();
            for (JsonNode node : payload.get("genericNumbers")) {
                genericNumbers.add(pf.createGenericNumber(hexToBytes(node.asText())));
            }
        }
        
        Object cugInterlock = payload.has("cugInterlock") ? new Object() : null;
        Object cugOutgoingAccess = payload.has("cugOutgoingAccess") ? new Object() : null;
        Object borInterrogationRequested = payload.has("borInterrogationRequested") ? new Object() : null;
        Object suppressOCsi = payload.has("suppressOCsi") ? new Object() : null;
        Object suppressNCsi = payload.has("suppressNCsi") ? new Object() : null;
        Object suppressOutgoingCallBarring = payload.has("suppressOutgoingCallBarring") ? new Object() : null;

        service.addContinueWithArgumentRequest(dialog, 
            alertingPattern, 
            extensions, 
            sii2, 
            cpc, 
            genericNumbers, 
            (org.restcomm.protocols.ss7.cap.api.primitives.CUGInterlock)cugInterlock, 
            cugOutgoingAccess,
            chargeNumber, 
            carrier,
            suppressionOfAnnouncement,
            naOliInfo,
            borInterrogationRequested,
            suppressOCsi,
            null, 
            suppressNCsi,
            suppressOutgoingCallBarring,
            null  
        );
        logger.info("Executing CONTINUE_WITH_ARGUMENT");
    }

    private void handleApplyCharging(CAPServiceCircuitSwitchedCall service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.primitives.AChBillingChargingCharacteristics ach = 
            (org.restcomm.protocols.ss7.cap.api.primitives.AChBillingChargingCharacteristics) createFromHex(pf, "AChBillingChargingCharacteristics", payload);
            
        org.restcomm.protocols.ss7.cap.api.primitives.AChChargingAddress address = 
            (org.restcomm.protocols.ss7.cap.api.primitives.AChChargingAddress) createFromHex(pf, "AChChargingAddress", payload);
            
        org.restcomm.protocols.ss7.cap.api.primitives.SendingSideID partyToCharge = 
            (org.restcomm.protocols.ss7.cap.api.primitives.SendingSideID) createFromHex(pf, "SendingSideID", payload);
            
        byte[] extensions = payload.has("extensions") ? hexToBytes(payload.get("extensions").asText()) : null;

        service.addApplyChargingRequest(dialog, ach, partyToCharge, extensions, address);
        logger.info("Executing APPLY_CHARGING");
    }

    private void handleFurnishChargingInformation(CAPServiceCircuitSwitchedCall service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.primitives.FCIBillingChargingCharacteristics fci = 
            (org.restcomm.protocols.ss7.cap.api.primitives.FCIBillingChargingCharacteristics) createFromHex(pf, "FCIBillingChargingCharacteristics", payload);

        service.addFurnishChargingInformationRequest(dialog, fci);
        logger.info("Executing FURNISH_CHARGING_INFORMATION");
    }

    private void handleSendChargingInformation(CAPServiceCircuitSwitchedCall service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.primitives.SCIBillingChargingCharacteristics sci = 
            (org.restcomm.protocols.ss7.cap.api.primitives.SCIBillingChargingCharacteristics) createFromHex(pf, "SCIBillingChargingCharacteristics", payload);
            
        org.restcomm.protocols.ss7.cap.api.primitives.SendingSideID partyToCharge = 
            (org.restcomm.protocols.ss7.cap.api.primitives.SendingSideID) createFromHex(pf, "SendingSideID", payload);
            
        byte[] extensions = payload.has("extensions") ? hexToBytes(payload.get("extensions").asText()) : null;

        service.addSendChargingInformationRequest(dialog, sci, partyToCharge, extensions);
        logger.info("Executing SEND_CHARGING_INFORMATION");
    }

    private void handleCallInformationRequest(CAPServiceCircuitSwitchedCall service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.primitives.RequestedInformationTypeList requestList = 
            (org.restcomm.protocols.ss7.cap.api.primitives.RequestedInformationTypeList) createFromHex(pf, "RequestedInformationTypeList", payload);
            
        org.restcomm.protocols.ss7.cap.api.primitives.SendingSideID legID = 
            (org.restcomm.protocols.ss7.cap.api.primitives.SendingSideID) createFromHex(pf, "SendingSideID", payload);
            
        byte[] extensions = payload.has("extensions") ? hexToBytes(payload.get("extensions").asText()) : null;

        service.addCallInformationRequestRequest(dialog, requestList, extensions, legID);
        logger.info("Executing CALL_INFORMATION_REQUEST");
    }

    private void handleReleaseCall(CAPServiceCircuitSwitchedCall service, CAPDialog dialog, JsonNode payload) throws Exception {
        int causeCode = payload.path("cause").asInt(31); 
        CAPParameterFactory paramFactory = capProvider.getCAPParameterFactory();
        Cause cause = paramFactory.createCause(mapper.mapCause(causeCode));
        
        service.addReleaseCallRequest(dialog, cause);
        logger.info("Executing RELEASE_CALL with cause {}", causeCode);
    }
    
    private void handleRequestReportBCSM(CAPServiceCircuitSwitchedCall service, CAPDialog dialog, JsonNode payload) throws Exception {
        JsonNode eventsNode = payload.path("events");
        String monitorModeStr = payload.path("monitorMode").asText();
        
        EventTypeBCSM[] eventTypes = mapper.mapBcsmEvents(eventsNode);
        MonitorMode mode = mapper.mapMonitorMode(monitorModeStr);
        
        CAPParameterFactory paramFactory = capProvider.getCAPParameterFactory();
        BCSMEvent[] bcsmEvents = new BCSMEvent[eventTypes.length];
        
        for (int i = 0; i < eventTypes.length; i++) {
            bcsmEvents[i] = paramFactory.createBCSMEvent(eventTypes[i], mode, null, null, null);
        }
        
        service.addRequestReportBCSMEventRequest(dialog, bcsmEvents, null);
        logger.info("Executing REQUEST_REPORT_BCSM for {} events", eventTypes.length);
    }
    
    private void handleDisconnectForwardConnection(CAPServiceCircuitSwitchedCall service, CAPDialog dialog) throws Exception {
        service.addDisconnectForwardConnectionRequest(dialog);
        logger.info("Executing DISCONNECT_FORWARD_CONNECTION");
    }

    // --- SMS Methods ---

    private void handleConnectSMS(CAPServiceSms service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.service.sms.primitive.SMSAddressString callingParty = 
            (org.restcomm.protocols.ss7.cap.api.service.sms.primitive.SMSAddressString) createFromHex(pf, "SMSAddressString", payload.get("callingPartysNumber"));
            
        org.restcomm.protocols.ss7.cap.api.service.sms.primitive.CalledPartyBCDNumber destSubscriber = 
            (org.restcomm.protocols.ss7.cap.api.service.sms.primitive.CalledPartyBCDNumber) createFromHex(pf, "CalledPartyBCDNumber", payload.get("destinationSubscriberNumber"));
            
        org.restcomm.protocols.ss7.cap.api.isup.ISDNAddressString smscAddress = 
            (org.restcomm.protocols.ss7.cap.api.isup.ISDNAddressString) createFromHex(pf, "ISDNAddressString", payload.get("smscAddress"));
            
        byte[] extensions = payload.has("extensions") ? hexToBytes(payload.get("extensions").asText()) : null;

        service.addConnectSMSRequest(dialog, callingParty, destSubscriber, smscAddress, extensions);
        logger.info("Executing CONNECT_SMS");
    }

    private void handleContinueSMS(CAPServiceSms service, CAPDialog dialog) throws Exception {
        service.addContinueSMSRequest(dialog);
        logger.info("Executing CONTINUE_SMS");
    }

    private void handleReleaseSMS(CAPServiceSms service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.service.sms.primitive.RPCause rpCause = 
            (org.restcomm.protocols.ss7.cap.api.service.sms.primitive.RPCause) createFromHex(pf, "RPCause", payload.get("rpCause"));

        service.addReleaseSMSRequest(dialog, rpCause);
        logger.info("Executing RELEASE_SMS");
    }

    private void handleRequestReportSMSEvent(CAPServiceSms service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        ArrayList<org.restcomm.protocols.ss7.cap.api.service.sms.primitive.SMSEvent> smsEvents = new ArrayList<>();
        
        if (payload.has("smsEvents") && payload.get("smsEvents").isArray()) {
            for (JsonNode node : payload.get("smsEvents")) {
                smsEvents.add((org.restcomm.protocols.ss7.cap.api.service.sms.primitive.SMSEvent) createFromHex(pf, "SMSEvent", node));
            }
        }
        
        byte[] extensions = payload.has("extensions") ? hexToBytes(payload.get("extensions").asText()) : null;

        service.addRequestReportSMSEventRequest(dialog, smsEvents.toArray(new org.restcomm.protocols.ss7.cap.api.service.sms.primitive.SMSEvent[0]), extensions);
        logger.info("Executing REQUEST_REPORT_SMS_EVENT");
    }

    private void handleFurnishChargingInformationSMS(CAPServiceSms service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.service.sms.primitive.FCISMSBillingChargingCharacteristics fci = 
            (org.restcomm.protocols.ss7.cap.api.service.sms.primitive.FCISMSBillingChargingCharacteristics) createFromHex(pf, "FCISMSBillingChargingCharacteristics", payload);

        service.addFurnishChargingInformationSMSRequest(dialog, fci);
        logger.info("Executing FURNISH_CHARGING_INFORMATION_SMS");
    }

    private void handleResetTimerSMS(CAPServiceSms service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.primitives.TimerID timerID = 
            (org.restcomm.protocols.ss7.cap.api.primitives.TimerID) createFromHex(pf, "TimerID", payload);
            
        org.restcomm.protocols.ss7.cap.api.primitives.TimerValue timerValue = 
            (org.restcomm.protocols.ss7.cap.api.primitives.TimerValue) createFromHex(pf, "TimerValue", payload);
            
        byte[] extensions = payload.has("extensions") ? hexToBytes(payload.get("extensions").asText()) : null;

        service.addResetTimerSMSRequest(dialog, timerID, timerValue, extensions);
        logger.info("Executing RESET_TIMER_SMS");
    }

    // --- GPRS Methods ---

    private void handleConnectGPRS(CAPServiceGprs service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.AccessPointName apn = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.AccessPointName) createFromHex(pf, "AccessPointName", payload.get("accessPointName"));
            
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID pdpid = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID) createFromHex(pf, "PDPID", payload.get("pdpId"));

        service.addConnectGPRSRequest(dialog, apn, pdpid);
        logger.info("Executing CONNECT_GPRS");
    }

    private void handleContinueGPRS(CAPServiceGprs service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID pdpid = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID) createFromHex(pf, "PDPID", payload.get("pdpId"));

        service.addContinueGPRSRequest(dialog, pdpid);
        logger.info("Executing CONTINUE_GPRS");
    }

    private void handleReleaseGPRS(CAPServiceGprs service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.GPRSCause cause = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.GPRSCause) createFromHex(pf, "GPRSCause", payload.get("gprsCause"));
            
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID pdpid = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID) createFromHex(pf, "PDPID", payload.get("pdpId"));

        service.addReleaseGPRSRequest(dialog, cause, pdpid);
        logger.info("Executing RELEASE_GPRS");
    }

    private void handleApplyChargingGPRS(CAPServiceGprs service, CAPDialog dialog, JsonNode payload) throws Exception {
        CAPParameterFactory pf = capProvider.getCAPParameterFactory();
        
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.ChargingCharacteristics cc = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.ChargingCharacteristics) createFromHex(pf, "ChargingCharacteristics", payload.get("chargingCharacteristics"));
            
        Integer tariffSwitch = payload.has("tariffSwitchInterval") ? payload.get("tariffSwitchInterval").asInt() : null;
        
        org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID pdpid = 
            (org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.PDPID) createFromHex(pf, "PDPID", payload.get("pdpId"));

        service.addApplyChargingGPRSRequest(dialog, cc, tariffSwitch, pdpid);
        logger.info("Executing APPLY_CHARGING_GPRS");
    }

    private static byte[] hexToBytes(String s) {
        if (s == null) return null;
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
    
    private Object createFromHex(CAPParameterFactory pf, String type, JsonNode payload) {
        // Handle case where payload is just the value, not wrapped in an object with param name
        JsonNode node = payload;
        if (payload != null && payload.has(Character.toLowerCase(type.charAt(0)) + type.substring(1))) {
             node = payload.get(Character.toLowerCase(type.charAt(0)) + type.substring(1));
        }
        
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
}