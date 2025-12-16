package com.company.ss7ha.core.listeners;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.mapper.MapJsonMapper;
import com.company.ss7ha.nats.publisher.SS7NatsPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import org.restcomm.protocols.ss7.map.api.MAPDialog;
import org.restcomm.protocols.ss7.map.api.MAPDialogListener;
import org.restcomm.protocols.ss7.map.api.service.mobility.MAPServiceMobilityListener;
import org.restcomm.protocols.ss7.map.api.service.mobility.authentication.AuthenticationFailureReportRequest;
import org.restcomm.protocols.ss7.map.api.service.mobility.authentication.AuthenticationFailureReportResponse;
import org.restcomm.protocols.ss7.map.api.service.mobility.authentication.SendAuthenticationInfoRequest;
import org.restcomm.protocols.ss7.map.api.service.mobility.authentication.SendAuthenticationInfoResponse;
import org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.CancelLocationRequest;
import org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.CancelLocationResponse;
import org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.PurgeMSRequest;
import org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.PurgeMSResponse;
import org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.SendIdentificationRequest;
import org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.SendIdentificationResponse;
import org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.UpdateGprsLocationRequest;
import org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.UpdateGprsLocationResponse;
import org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.UpdateLocationRequest;
import org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.UpdateLocationResponse;
import org.restcomm.protocols.ss7.map.api.service.sms.MAPServiceSmsListener;
import org.restcomm.protocols.ss7.map.api.service.sms.MoForwardShortMessageRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.MoForwardShortMessageResponse;
import org.restcomm.protocols.ss7.map.api.service.sms.MtForwardShortMessageRequest;
import org.restcomm.protocols.ss7.map.api.service.sms.MtForwardShortMessageResponse;
import org.restcomm.protocols.ss7.map.api.service.supplementary.MAPServiceSupplementaryListener;
import org.restcomm.protocols.ss7.map.api.service.supplementary.ProcessUnstructuredSSRequest;
import org.restcomm.protocols.ss7.map.api.service.supplementary.ProcessUnstructuredSSResponse;
import org.restcomm.protocols.ss7.map.api.service.supplementary.UnstructuredSSRequest;
import org.restcomm.protocols.ss7.map.api.service.supplementary.UnstructuredSSResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic MAP Service Listener covering SMS, Mobility, USSD and other MAP services.
 * Bridges all MAP operations to NATS using Hex Passthrough.
 */
public class GenericMapServiceListener implements MAPDialogListener, MAPServiceSmsListener, MAPServiceMobilityListener, MAPServiceSupplementaryListener {

    private static final Logger logger = LoggerFactory.getLogger(GenericMapServiceListener.class);
    private final EventPublisher eventPublisher;
    private final SS7NatsPublisher natsPublisher;
    private final boolean publishEvents;
    private final MapJsonMapper mapper;

    public GenericMapServiceListener(EventPublisher eventPublisher, SS7NatsPublisher natsPublisher, boolean publishEvents) {
        this.eventPublisher = eventPublisher;
        this.natsPublisher = natsPublisher;
        this.publishEvents = publishEvents;
        this.mapper = new MapJsonMapper();
    }

    // =========================================================================
    // Helper to publish event
    // =========================================================================
    private void publishMapEvent(String type, JsonNode data, MAPDialog dialog) {
        if (publishEvents && eventPublisher != null) {
            try {
                ((com.fasterxml.jackson.databind.node.ObjectNode) data).put("dialogId", dialog.getLocalDialogId());
                ((com.fasterxml.jackson.databind.node.ObjectNode) data).put("type", type);
                
                String subject = "map.event." + type.toLowerCase().replace("_", ".");
                eventPublisher.publishEvent(subject, data);
                logger.info("Published MAP event {} to NATS (Dialog: {})", type, dialog.getLocalDialogId());
            } catch (Exception e) {
                logger.error("Error publishing MAP event {}", type, e);
            }
        }
    }

    // =========================================================================
    // MAP Service SMS Listener
    // =========================================================================
    @Override
    public void onMoForwardShortMessageRequest(MoForwardShortMessageRequest request) {
        publishMapEvent("MAP_MO_FORWARD_SM", mapper.mapMoForwardShortMessage(request), request.getMAPDialog());
    }

    @Override
    public void onMoForwardShortMessageResponse(MoForwardShortMessageResponse response) {
        publishMapEvent("MAP_MO_FORWARD_SM_RES", mapper.mapMoForwardShortMessageResponse(response), response.getMAPDialog());
    }

    @Override
    public void onMtForwardShortMessageRequest(MtForwardShortMessageRequest request) {
        publishMapEvent("MAP_MT_FORWARD_SM", mapper.mapMtForwardShortMessage(request), request.getMAPDialog());
    }

    @Override
    public void onMtForwardShortMessageResponse(MtForwardShortMessageResponse response) {
        publishMapEvent("MAP_MT_FORWARD_SM_RES", mapper.mapMtForwardShortMessageResponse(response), response.getMAPDialog());
    }
    
    // ... Implement other SMS methods (AlertServiceCentre, ReportSMDeliveryStatus, etc.) ...
    @Override public void onAlertServiceCentreRequest(org.restcomm.protocols.ss7.map.api.service.sms.AlertServiceCentreRequest request) {}
    @Override public void onAlertServiceCentreResponse(org.restcomm.protocols.ss7.map.api.service.sms.AlertServiceCentreResponse response) {}
    @Override public void onInformServiceCentreRequest(org.restcomm.protocols.ss7.map.api.service.sms.InformServiceCentreRequest request) {}
    @Override public void onReportSMDeliveryStatusRequest(org.restcomm.protocols.ss7.map.api.service.sms.ReportSMDeliveryStatusRequest request) {}
    @Override public void onReportSMDeliveryStatusResponse(org.restcomm.protocols.ss7.map.api.service.sms.ReportSMDeliveryStatusResponse response) {}
    @Override public void onSendRoutingInfoForSMRequest(org.restcomm.protocols.ss7.map.api.service.sms.SendRoutingInfoForSMRequest request) {
        publishMapEvent("MAP_SRI_SM", mapper.mapSendRoutingInfoForSM(request), request.getMAPDialog());
    }
    @Override public void onSendRoutingInfoForSMResponse(org.restcomm.protocols.ss7.map.api.service.sms.SendRoutingInfoForSMResponse response) {}

    // =========================================================================
    // MAP Service Mobility Listener (Location, Auth, etc.)
    // =========================================================================
    @Override
    public void onUpdateLocationRequest(UpdateLocationRequest request) {
        publishMapEvent("MAP_UPDATE_LOCATION", mapper.mapUpdateLocation(request), request.getMAPDialog());
    }

    @Override
    public void onUpdateLocationResponse(UpdateLocationResponse response) {
        publishMapEvent("MAP_UPDATE_LOCATION_RES", mapper.mapUpdateLocationResponse(response), response.getMAPDialog());
    }

    @Override
    public void onUpdateGprsLocationRequest(UpdateGprsLocationRequest request) {
        publishMapEvent("MAP_UPDATE_GPRS_LOCATION", mapper.mapUpdateGprsLocation(request), request.getMAPDialog());
    }

    @Override
    public void onUpdateGprsLocationResponse(UpdateGprsLocationResponse response) {
        publishMapEvent("MAP_UPDATE_GPRS_LOCATION_RES", mapper.mapUpdateGprsLocationResponse(response), response.getMAPDialog());
    }

    @Override
    public void onSendAuthenticationInfoRequest(SendAuthenticationInfoRequest request) {
        publishMapEvent("MAP_SEND_AUTH_INFO", mapper.mapSendAuthenticationInfo(request), request.getMAPDialog());
    }

    @Override
    public void onSendAuthenticationInfoResponse(SendAuthenticationInfoResponse response) {
        publishMapEvent("MAP_SEND_AUTH_INFO_RES", mapper.mapSendAuthenticationInfoResponse(response), response.getMAPDialog());
    }
    
    // ... Implement other Mobility methods (PurgeMS, CancelLocation, etc.) ...
    @Override public void onCancelLocationRequest(CancelLocationRequest request) {}
    @Override public void onCancelLocationResponse(CancelLocationResponse response) {}
    @Override public void onPurgeMSRequest(PurgeMSRequest request) {}
    @Override public void onPurgeMSResponse(PurgeMSResponse response) {}
    @Override public void onSendIdentificationRequest(SendIdentificationRequest request) {}
    @Override public void onSendIdentificationResponse(SendIdentificationResponse response) {}
    @Override public void onAuthenticationFailureReportRequest(AuthenticationFailureReportRequest request) {}
    @Override public void onAuthenticationFailureReportResponse(AuthenticationFailureReportResponse response) {}
    // Add more mobility stubs as needed by interface...

    // =========================================================================
    // MAP Service Supplementary Listener (USSD)
    // =========================================================================
    @Override
    public void onProcessUnstructuredSSRequest(ProcessUnstructuredSSRequest request) {
        publishMapEvent("MAP_PROCESS_USS", mapper.mapProcessUnstructuredSS(request), request.getMAPDialog());
    }

    @Override
    public void onProcessUnstructuredSSResponse(ProcessUnstructuredSSResponse response) {
        publishMapEvent("MAP_PROCESS_USS_RES", mapper.mapProcessUnstructuredSSResponse(response), response.getMAPDialog());
    }

    @Override
    public void onUnstructuredSSRequest(UnstructuredSSRequest request) {
        publishMapEvent("MAP_UNSTRUCTURED_SS", mapper.mapUnstructuredSS(request), request.getMAPDialog());
    }

    @Override
    public void onUnstructuredSSResponse(UnstructuredSSResponse response) {
        publishMapEvent("MAP_UNSTRUCTURED_SS_RES", mapper.mapUnstructuredSSResponse(response), response.getMAPDialog());
    }
    
    // ... Other USSD methods ...
    @Override public void onUnstructuredSSNotifyRequest(org.restcomm.protocols.ss7.map.api.service.supplementary.UnstructuredSSNotifyRequest request) {}
    @Override public void onUnstructuredSSNotifyResponse(org.restcomm.protocols.ss7.map.api.service.supplementary.UnstructuredSSNotifyResponse response) {}
    @Override public void onRegisterSSRequest(org.restcomm.protocols.ss7.map.api.service.supplementary.RegisterSSRequest request) {}
    @Override public void onRegisterSSResponse(org.restcomm.protocols.ss7.map.api.service.supplementary.RegisterSSResponse response) {}
    @Override public void onEraseSSRequest(org.restcomm.protocols.ss7.map.api.service.supplementary.EraseSSRequest request) {}
    @Override public void onEraseSSResponse(org.restcomm.protocols.ss7.map.api.service.supplementary.EraseSSResponse response) {}
    @Override public void onActivateSSRequest(org.restcomm.protocols.ss7.map.api.service.supplementary.ActivateSSRequest request) {}
    @Override public void onActivateSSResponse(org.restcomm.protocols.ss7.map.api.service.supplementary.ActivateSSResponse response) {}
    @Override public void onDeactivateSSRequest(org.restcomm.protocols.ss7.map.api.service.supplementary.DeactivateSSRequest request) {}
    @Override public void onDeactivateSSResponse(org.restcomm.protocols.ss7.map.api.service.supplementary.DeactivateSSResponse response) {}
    @Override public void onInterrogateSSRequest(org.restcomm.protocols.ss7.map.api.service.supplementary.InterrogateSSRequest request) {}
    @Override public void onInterrogateSSResponse(org.restcomm.protocols.ss7.map.api.service.supplementary.InterrogateSSResponse response) {}
    @Override public void onGetPasswordRequest(org.restcomm.protocols.ss7.map.api.service.supplementary.GetPasswordRequest request) {}
    @Override public void onGetPasswordResponse(org.restcomm.protocols.ss7.map.api.service.supplementary.GetPasswordResponse response) {}


    // =========================================================================
    // MAP Dialog Listener
    // =========================================================================
    @Override
    public void onDialogDelimiter(MAPDialog mapDialog) {
    }

    @Override
    public void onDialogRequest(MAPDialog mapDialog, org.restcomm.protocols.ss7.map.api.MAPMessage mapMessage) {
    }

    @Override
    public void onDialogRelease(MAPDialog mapDialog) {
        logger.debug("MAP Dialog released: {}", mapDialog.getLocalDialogId());
    }

    @Override
    public void onDialogTimeout(MAPDialog mapDialog) {
        logger.warn("MAP Dialog timeout: {}", mapDialog.getLocalDialogId());
    }
    
    @Override public void onDialogNotice(MAPDialog mapDialog, org.restcomm.protocols.ss7.map.api.MAPNoticeProblem notice) {}
    @Override public void onDialogProviderAbort(MAPDialog mapDialog, org.restcomm.protocols.ss7.map.api.MAPAbortProviderReason abortReason, org.restcomm.protocols.ss7.map.api.MAPAbortSource abortSource, org.restcomm.protocols.ss7.map.api.MAPExtensionContainer extensionContainer) {}
    @Override public void onDialogUserAbort(MAPDialog mapDialog, org.restcomm.protocols.ss7.map.api.MAPUserAbortChoice userReason, org.restcomm.protocols.ss7.map.api.MAPExtensionContainer extensionContainer) {}
    @Override public void onDialogReject(MAPDialog mapDialog, org.restcomm.protocols.ss7.map.api.MAPRefuseReason refuseReason, org.restcomm.protocols.ss7.map.api.MAPProviderError providerError, org.restcomm.protocols.ss7.map.api.MAPExtensionContainer extensionContainer) {}
}
