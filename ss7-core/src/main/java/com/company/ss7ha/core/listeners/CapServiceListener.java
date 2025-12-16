package com.company.ss7ha.core.listeners;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.mapper.CapJsonMapper;
import com.company.ss7ha.nats.publisher.SS7NatsPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import org.restcomm.protocols.ss7.cap.api.CAPDialog;
import org.restcomm.protocols.ss7.cap.api.isup.CalledPartyNumber;
import org.restcomm.protocols.ss7.cap.api.isup.CallingPartyNumber;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.*;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.primitive.*;
import org.restcomm.protocols.ss7.cap.api.service.sms.CAPServiceSmsListener;
import org.restcomm.protocols.ss7.cap.api.service.sms.primitive.*;
import org.restcomm.protocols.ss7.cap.api.service.gprs.CAPServiceGprsListener;
import org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * CAP v4 Service Listener for Advanced Services (CRBT, Pay-For-Me, Multi-Party), SMS and GPRS Control.
 * Handles CAMEL Phase 4 operations and bridges them to NATS.
 */
public class CapServiceListener implements CAPServiceCircuitSwitchedCallListener, CAPServiceSmsListener, CAPServiceGprsListener {

    private static final Logger logger = LoggerFactory.getLogger(CapServiceListener.class);
    private final EventPublisher eventPublisher;
    private final SS7NatsPublisher natsPublisher;
    private final boolean publishEvents;
    private final CapJsonMapper mapper;

    public CapServiceListener(EventPublisher eventPublisher, SS7NatsPublisher natsPublisher, boolean publishEvents) {
        this.eventPublisher = eventPublisher;
        this.natsPublisher = natsPublisher;
        this.publishEvents = publishEvents;
        this.mapper = new CapJsonMapper();
    }

    // =========================================================================
    // Circuit Switched Call Control (Voice)
    // =========================================================================

    @Override
    public void onInitialDPRequest(InitialDPRequest request) {
        logger.info("Received CAP v4 InitialDP Request");
        try {
            CAPDialog dialog = request.getCAPDialog();
            Long dialogId = dialog.getLocalDialogId();
            
            JsonNode eventData = mapper.mapInitialDP(request);
            ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("dialogId", dialogId);
            ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("type", "CAP_INITIAL_DP");

            if (publishEvents && eventPublisher != null) {
                eventPublisher.publishEvent("cap.initial.dp", eventData);
                logger.info("Published InitialDP event to NATS (Dialog: {})", dialogId);
            }
        } catch (Exception e) {
            logger.error("Error handling InitialDP", e);
        }
    }

    @Override
    public void onApplyChargingReportRequest(ApplyChargingReportRequest request) {
        logger.info("Received ApplyChargingReport");
        if (publishEvents && eventPublisher != null) {
            JsonNode eventData = mapper.mapApplyChargingReport(request);
            ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("dialogId", request.getCAPDialog().getLocalDialogId());
            ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("type", "CAP_APPLY_CHARGING_REPORT");
            eventPublisher.publishEvent("cap.charging.report", eventData);
        }
    }

    @Override
    public void onEventReportBCSMRequest(EventReportBCSMRequest request) {
        logger.info("Received EventReportBCSM");
        if (publishEvents && eventPublisher != null) {
            JsonNode eventData = mapper.mapEventReportBCSM(request);
            ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("dialogId", request.getCAPDialog().getLocalDialogId());
            ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("type", "CAP_EVENT_REPORT_BCSM");
            eventPublisher.publishEvent("cap.event.report", eventData);
        }
    }

    @Override
    public void onCallInformationReportRequest(CallInformationReportRequest request) {
        logger.info("Received CallInformationReport");
        try {
            if (publishEvents && eventPublisher != null) {
                JsonNode eventData = mapper.mapCallInformationReport(request);
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("dialogId", request.getCAPDialog().getLocalDialogId());
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("type", "CAP_CALL_INFORMATION_REPORT");
                eventPublisher.publishEvent("cap.report.callinformation", eventData);
            }
        } catch (Exception e) {
            logger.error("Error handling CallInformationReport", e);
        }
    }

    // Voice Stubs...
    @Override public void onReleaseCallRequest(ReleaseCallRequest request) {}
    @Override public void onAssistRequestInstructionsRequest(AssistRequestInstructionsRequest request) {}
    @Override public void onConnectRequest(ConnectRequest request) {}
    @Override public void onDisconnectForwardConnectionRequest(DisconnectForwardConnectionRequest request) {}
    @Override public void onEstablishTemporaryConnectionRequest(EstablishTemporaryConnectionRequest request) {}
    @Override public void onResetTimerRequest(ResetTimerRequest request) {}
    @Override public void onActivityTestResponse(ActivityTestResponse response) {}
    @Override public void onApplyChargingRequest(ApplyChargingRequest request) {}
    @Override public void onCallGapRequest(CallCallGapRequest request) {}
    @Override public void onCallInformationRequestRequest(CallInformationRequestRequest request) {}
    @Override public void onCancelRequest(CancelRequest request) {}
    @Override public void onCollectInformationRequest(CollectInformationRequest request) {}
    @Override public void onContinueRequest(ContinueRequest request) {}
    @Override public void onContinueWithArgumentRequest(ContinueWithArgumentRequest request) {}
    @Override public void onEventReportBCSMResponse(EventReportBCSMResponse response) {}
    @Override public void onFurnishChargingInformationRequest(FurnishChargingInformationRequest request) {}
    @Override public void onRequestReportBCSMEventRequest(RequestReportBCSMEventRequest request) {}
    @Override public void onSendChargingInformationRequest(SendChargingInformationRequest request) {}
    @Override public void onSpecializedResourceReportRequest(SpecializedResourceReportRequest request) {}
    @Override public void onDialogTimeout(CAPDialog capDialog) { logger.warn("CAP Dialog timeout: {}", capDialog.getLocalDialogId()); }
    @Override public void onDialogDelimiter(CAPDialog capDialog) {}
    @Override public void onDialogRequest(CAPDialog capDialog, org.restcomm.protocols.ss7.cap.api.CAPMessage capMessage) {}
    @Override public void onDialogRelease(CAPDialog capDialog) { logger.debug("CAP Dialog released: {}", capDialog.getLocalDialogId()); }
    @Override public void onDialogAbort(CAPDialog capDialog) { logger.warn("CAP Dialog aborted: {}", capDialog.getLocalDialogId()); }

    // =========================================================================
    // SMS Control
    // =========================================================================

    @Override
    public void onInitialDPSMSRequest(InitialDPSMSRequest request) {
        logger.info("Received InitialDP-SMS");
        try {
            if (publishEvents && eventPublisher != null) {
                JsonNode eventData = mapper.mapInitialDPSMS(request);
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("dialogId", request.getCAPDialog().getLocalDialogId());
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("type", "CAP_INITIAL_DP_SMS");
                eventPublisher.publishEvent("cap.initial.dp.sms", eventData);
            }
        } catch (Exception e) {
            logger.error("Error handling InitialDP-SMS", e);
        }
    }

    @Override
    public void onEventReportSMSRequest(EventReportSMSRequest request) {
        logger.info("Received EventReportSMS");
        try {
            if (publishEvents && eventPublisher != null) {
                JsonNode eventData = mapper.mapEventReportSMS(request);
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("dialogId", request.getCAPDialog().getLocalDialogId());
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("type", "CAP_EVENT_REPORT_SMS");
                eventPublisher.publishEvent("cap.event.report.sms", eventData);
            }
        } catch (Exception e) {
            logger.error("Error handling EventReportSMS", e);
        }
    }

    // SMS Stubs...
    @Override public void onConnectSMSRequest(ConnectSMSRequest request) {}
    @Override public void onContinueSMSRequest(ContinueSMSRequest request) {}
    @Override public void onFurnishChargingInformationSMSRequest(FurnishChargingInformationSMSRequest request) {}
    @Override public void onReleaseSMSRequest(ReleaseSMSRequest request) {}
    @Override public void onRequestReportSMSEventRequest(RequestReportSMSEventRequest request) {}
    @Override
    public void onResetTimerSMSRequest(org.restcomm.protocols.ss7.cap.api.service.sms.primitive.ResetTimerSMSRequest request) {
        logger.info("Received ResetTimerSMS");
        try {
            if (publishEvents && eventPublisher != null) {
                JsonNode eventData = mapper.mapResetTimerSMS(request);
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("dialogId", request.getCAPDialog().getLocalDialogId());
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("type", "CAP_RESET_TIMER_SMS");
                eventPublisher.publishEvent("cap.report.reset.timer.sms", eventData);
                logger.info("Published ResetTimerSMS event to NATS (Dialog: {})", request.getCAPDialog().getLocalDialogId());
            }
        } catch (Exception e) {
            logger.error("Error handling ResetTimerSMS", e);
        }
    }

    // =========================================================================
    // GPRS Control
    // =========================================================================

    @Override
    public void onInitialDPGPRSRequest(InitialDPGPRSRequest request) {
        logger.info("Received InitialDP-GPRS");
        try {
            if (publishEvents && eventPublisher != null) {
                JsonNode eventData = mapper.mapInitialDPGPRS(request);
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("dialogId", request.getCAPDialog().getLocalDialogId());
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("type", "CAP_INITIAL_DP_GPRS");
                eventPublisher.publishEvent("cap.initial.dp.gprs", eventData);
            }
        } catch (Exception e) {
            logger.error("Error handling InitialDP-GPRS", e);
        }
    }

    @Override
    public void onApplyChargingReportGPRSRequest(ApplyChargingReportGPRSRequest request) {
        logger.info("Received ApplyChargingReportGPRS");
        try {
            if (publishEvents && eventPublisher != null) {
                JsonNode eventData = mapper.mapApplyChargingReportGPRS(request);
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("dialogId", request.getCAPDialog().getLocalDialogId());
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("type", "CAP_APPLY_CHARGING_REPORT_GPRS");
                eventPublisher.publishEvent("cap.charging.report.gprs", eventData);
            }
        } catch (Exception e) {
            logger.error("Error handling ApplyChargingReportGPRS", e);
        }
    }

    @Override
    public void onEntityReleasedGPRSRequest(EntityReleasedGPRSRequest request) {
        logger.info("Received EntityReleasedGPRS");
        try {
            if (publishEvents && eventPublisher != null) {
                JsonNode eventData = mapper.mapEntityReleasedGPRS(request);
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("dialogId", request.getCAPDialog().getLocalDialogId());
                ((com.fasterxml.jackson.databind.node.ObjectNode) eventData).put("type", "CAP_ENTITY_RELEASED_GPRS");
                eventPublisher.publishEvent("cap.entity.released.gprs", eventData);
            }
        } catch (Exception e) {
            logger.error("Error handling EntityReleasedGPRS", e);
        }
    }

    @Override
    public void onEventReportGPRSRequest(EventReportGPRSRequest request) {
        // Implementation for EventReportGPRS (similar to others)
        // Assuming mapping method exists or using raw
        logger.info("Received EventReportGPRS");
    }

    // GPRS Stubs...
    @Override public void onActivityTestGPRSRequest(ActivityTestGPRSRequest request) {}
    @Override public void onApplyChargingGPRSRequest(ApplyChargingGPRSRequest request) {}
    @Override public void onCancelGPRSRequest(CancelGPRSRequest request) {}
    @Override public void onConnectGPRSRequest(ConnectGPRSRequest request) {}
    @Override public void onContinueGPRSRequest(ContinueGPRSRequest request) {}
    @Override public void onFurnishChargingInformationGPRSRequest(FurnishChargingInformationGPRSRequest request) {}
    @Override public void onReleaseGPRSRequest(ReleaseGPRSRequest request) {}
    @Override public void onRequestReportGPRSEventRequest(RequestReportGPRSEventRequest request) {}
    @Override public void onResetTimerGPRSRequest(ResetTimerGPRSRequest request) {}
    @Override public void onSendChargingInformationGPRSRequest(SendChargingInformationGPRSRequest request) {}
}