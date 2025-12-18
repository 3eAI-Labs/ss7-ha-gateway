package com.company.ss7ha.core.listeners;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.mapper.MapJsonMapper;
import com.company.ss7ha.nats.publisher.SS7NatsPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import org.restcomm.protocols.ss7.map.api.MAPDialog;
import org.restcomm.protocols.ss7.map.api.MAPDialogListener;
import org.restcomm.protocols.ss7.map.api.dialog.*;
import org.restcomm.protocols.ss7.commonapp.api.primitives.AddressString;
import org.restcomm.protocols.ss7.commonapp.api.primitives.MAPExtensionContainer;
import org.restcomm.protocols.ss7.tcap.asn.ApplicationContextName;
import org.restcomm.protocols.ss7.map.api.MAPMessage; 

import org.restcomm.protocols.ss7.map.api.service.mobility.MAPServiceMobilityListener;
import org.restcomm.protocols.ss7.map.api.service.mobility.authentication.*;
import org.restcomm.protocols.ss7.map.api.service.mobility.locationManagement.*;
import org.restcomm.protocols.ss7.map.api.service.mobility.subscriberManagement.*;
import org.restcomm.protocols.ss7.map.api.service.mobility.faultRecovery.*;
import org.restcomm.protocols.ss7.map.api.service.mobility.oam.*;
import org.restcomm.protocols.ss7.map.api.service.mobility.imei.*; 
import org.restcomm.protocols.ss7.map.api.service.mobility.subscriberInformation.*; 
import org.restcomm.protocols.ss7.map.api.service.sms.*;
import org.restcomm.protocols.ss7.map.api.service.supplementary.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericMapServiceListener implements MAPDialogListener, MAPServiceSmsListener, MAPServiceMobilityListener, MAPServiceSupplementaryListener {

    private static final Logger logger = LoggerFactory.getLogger(GenericMapServiceListener.class);
    private final EventPublisher eventPublisher;
    private final MapJsonMapper mapper;

    public GenericMapServiceListener(EventPublisher eventPublisher, SS7NatsPublisher natsPublisher, boolean publishEvents) {
        this.eventPublisher = eventPublisher;
        this.mapper = new MapJsonMapper();
    }

    @Override public void onMAPMessage(MAPMessage mapMessage) {
        publishMapEvent("MAP_MESSAGE", mapMessage);
    }
    @Override public void onErrorComponent(MAPDialog mapDialog, Integer invokeId, org.restcomm.protocols.ss7.map.api.errors.MAPErrorMessage mapErrorMessage) {
        publishMapEvent("ERROR_COMPONENT", mapErrorMessage);
    }
    @Override public void onRejectComponent(MAPDialog mapDialog, Integer invokeId, org.restcomm.protocols.ss7.tcap.asn.comp.Problem problem, boolean isLocal) {
        publishMapEvent("REJECT_COMPONENT", isLocal);
    }
    @Override public void onInvokeTimeout(MAPDialog mapDialog, Integer invokeId) {
        publishMapEvent("INVOKE_TIMEOUT", invokeId);
    }

    // SMS
    @Override public void onMoForwardShortMessageRequest(MoForwardShortMessageRequest request) {
        publishMapEvent("MO_FORWARD_SHORT_MESSAGE_REQUEST", request);
    }
    @Override public void onMoForwardShortMessageResponse(MoForwardShortMessageResponse response) {
        publishMapEvent("MO_FORWARD_SHORT_MESSAGE_RESPONSE", response);
    }
    @Override public void onMtForwardShortMessageRequest(MtForwardShortMessageRequest request) {
        publishMapEvent("MT_FORWARD_SHORT_MESSAGE_REQUEST", request);
    }
    @Override public void onMtForwardShortMessageResponse(MtForwardShortMessageResponse response) {
        publishMapEvent("MT_FORWARD_SHORT_MESSAGE_RESPONSE", response);
    }
    @Override public void onAlertServiceCentreRequest(AlertServiceCentreRequest request) {
        publishMapEvent("ALERT_SERVICE_CENTRE_REQUEST", request);
    }
    @Override public void onAlertServiceCentreResponse(AlertServiceCentreResponse response) {
        publishMapEvent("ALERT_SERVICE_CENTRE_RESPONSE", response);
    }
    @Override public void onInformServiceCentreRequest(InformServiceCentreRequest request) {
        publishMapEvent("INFORM_SERVICE_CENTRE_REQUEST", request);
    }
    @Override public void onReportSMDeliveryStatusRequest(ReportSMDeliveryStatusRequest request) {
        publishMapEvent("REPORT_SM_DELIVERY_STATUS_REQUEST", request);
    }
    @Override public void onReportSMDeliveryStatusResponse(ReportSMDeliveryStatusResponse response) {
        publishMapEvent("REPORT_SM_DELIVERY_STATUS_RESPONSE", response);
    }
    @Override public void onSendRoutingInfoForSMRequest(SendRoutingInfoForSMRequest request) {
        publishMapEvent("SEND_ROUTING_INFO_FOR_SM_REQUEST", request);
    }
    @Override public void onSendRoutingInfoForSMResponse(SendRoutingInfoForSMResponse response) {
        publishMapEvent("SEND_ROUTING_INFO_FOR_SM_RESPONSE", response);
    }
    @Override public void onNoteSubscriberPresentRequest(NoteSubscriberPresentRequest request) {
        publishMapEvent("NOTE_SUBSCRIBER_PRESENT_REQUEST", request);
    }
    @Override public void onReadyForSMResponse(ReadyForSMResponse response) {
        publishMapEvent("READY_FOR_SM_RESPONSE", response);
    }
    @Override public void onReadyForSMRequest(ReadyForSMRequest request) {
        publishMapEvent("READY_FOR_SM_REQUEST", request);
    }
    @Override public void onForwardShortMessageRequest(ForwardShortMessageRequest request) {
        publishMapEvent("FORWARD_SHORT_MESSAGE_REQUEST", request);
    } 
    @Override public void onForwardShortMessageResponse(ForwardShortMessageResponse response) {
        publishMapEvent("FORWARD_SHORT_MESSAGE_RESPONSE", response);
    } 

    // Mobility
    @Override 
    public void onUpdateLocationRequest(UpdateLocationRequest request) {
        publishMapEvent("UPDATE_LOCATION", request, request.getMAPDialog());
    }
    
    @Override public void onUpdateLocationResponse(UpdateLocationResponse response) {
        publishMapEvent("UPDATE_LOCATION_RESPONSE", response);
    }
    @Override public void onUpdateGprsLocationRequest(UpdateGprsLocationRequest request) {
        publishMapEvent("UPDATE_GPRS_LOCATION_REQUEST", request);
    }
    @Override public void onUpdateGprsLocationResponse(UpdateGprsLocationResponse response) {
        publishMapEvent("UPDATE_GPRS_LOCATION_RESPONSE", response);
    }
    
    @Override 
    public void onSendAuthenticationInfoRequest(SendAuthenticationInfoRequest request) {
        publishMapEvent("SEND_AUTH_INFO", request, request.getMAPDialog());
    }
    
    @Override public void onSendAuthenticationInfoResponse(SendAuthenticationInfoResponse response) {
        publishMapEvent("SEND_AUTHENTICATION_INFO_RESPONSE", response);
    }
    
    @Override 
    public void onCancelLocationRequest(CancelLocationRequest request) {
        publishMapEvent("CANCEL_LOCATION_REQUEST", request);
    }
    @Override 
    public void onCancelLocationResponse(CancelLocationResponse response) {
        publishMapEvent("CANCEL_LOCATION_RESPONSE", response);
    }

    @Override 
    public void onPurgeMSRequest(PurgeMSRequest request) {
        publishMapEvent("PURGE_MS_REQUEST", request);
    }
    @Override 
    public void onPurgeMSResponse(PurgeMSResponse response) {
        publishMapEvent("PURGE_MS_RESPONSE", response);
    }

    @Override 
    public void onSendIdentificationRequest(SendIdentificationRequest request) {
        publishMapEvent("SEND_IDENTIFICATION_REQUEST", request);
    }
    @Override 
    public void onSendIdentificationResponse(SendIdentificationResponse response) {
        publishMapEvent("SEND_IDENTIFICATION_RESPONSE", response);
    }

    @Override 
    public void onAuthenticationFailureReportRequest(AuthenticationFailureReportRequest request) {
        publishMapEvent("AUTHENTICATION_FAILURE_REPORT_REQUEST", request);
    }
    @Override 
    public void onAuthenticationFailureReportResponse(AuthenticationFailureReportResponse response) {
        publishMapEvent("AUTHENTICATION_FAILURE_REPORT_RESPONSE", response);
    }

    @Override 
    public void onRestoreDataRequest(RestoreDataRequest request) {
        publishMapEvent("RESTORE_DATA_REQUEST", request);
    }
    @Override 
    public void onRestoreDataResponse(RestoreDataResponse response) {
        publishMapEvent("RESTORE_DATA_RESPONSE", response);
    }

    @Override 
    public void onActivateTraceModeRequest_Mobility(ActivateTraceModeRequest_Mobility request) {
        publishMapEvent("ACTIVATE_TRACE_MODE_REQUEST", request);
    }
    @Override 
    public void onActivateTraceModeResponse_Mobility(ActivateTraceModeResponse_Mobility response) {
        publishMapEvent("ACTIVATE_TRACE_MODE_RESPONSE", response);
    }

    @Override 
    public void onCheckImeiRequest(CheckImeiRequest request) {
        publishMapEvent("CHECK_IMEI_REQUEST", request);
    }
    @Override 
    public void onCheckImeiResponse(CheckImeiResponse response) {
        publishMapEvent("CHECK_IMEI_RESPONSE", response);
    }

    @Override 
    public void onDeleteSubscriberDataRequest(DeleteSubscriberDataRequest request) {
        publishMapEvent("DELETE_SUBSCRIBER_DATA_REQUEST", request);
    }
    @Override 
    public void onDeleteSubscriberDataResponse(DeleteSubscriberDataResponse response) {
        publishMapEvent("DELETE_SUBSCRIBER_DATA_RESPONSE", response);
    }

    @Override 
    public void onInsertSubscriberDataRequest(InsertSubscriberDataRequest request) {
        publishMapEvent("INSERT_SUBSCRIBER_DATA_REQUEST", request);
    }
    @Override 
    public void onInsertSubscriberDataResponse(InsertSubscriberDataResponse response) {
        publishMapEvent("INSERT_SUBSCRIBER_DATA_RESPONSE", response);
    }

    @Override 
    public void onProvideSubscriberInfoRequest(ProvideSubscriberInfoRequest request) {
        publishMapEvent("PROVIDE_SUBSCRIBER_INFO_REQUEST", request);
    }
    @Override 
    public void onProvideSubscriberInfoResponse(ProvideSubscriberInfoResponse response) {
        publishMapEvent("PROVIDE_SUBSCRIBER_INFO_RESPONSE", response);
    }

    @Override 
    public void onAnyTimeSubscriptionInterrogationRequest(AnyTimeSubscriptionInterrogationRequest request) {
        publishMapEvent("ANY_TIME_SUBSCRIPTION_INTERROGATION_REQUEST", request);
    }
    @Override 
    public void onAnyTimeSubscriptionInterrogationResponse(AnyTimeSubscriptionInterrogationResponse response) {
        publishMapEvent("ANY_TIME_SUBSCRIPTION_INTERROGATION_RESPONSE", response);
    }

    @Override 
    public void onAnyTimeInterrogationRequest(AnyTimeInterrogationRequest request) {
        publishMapEvent("ANY_TIME_INTERROGATION_REQUEST", request);
    }
    @Override 
    public void onAnyTimeInterrogationResponse(AnyTimeInterrogationResponse response) {
        publishMapEvent("ANY_TIME_INTERROGATION_RESPONSE", response);
    }

    @Override 
    public void onForwardCheckSSIndicationRequest(ForwardCheckSSIndicationRequest request) {
        publishMapEvent("FORWARD_CHECK_SS_INDICATION_REQUEST", request);
    }
    @Override 
    public void onResetRequest(ResetRequest request) {
        publishMapEvent("RESET_REQUEST", request);
    }

    // USSD
    @Override 
    public void onProcessUnstructuredSSRequest(ProcessUnstructuredSSRequest request) {
        publishMapEvent("PROCESS_USS", request, request.getMAPDialog());
    }

    private void publishMapEvent(String type, Object payload) {
        MAPDialog dialog = null;
        try {
            if (payload instanceof org.restcomm.protocols.ss7.map.api.MAPMessage) {
                dialog = ((org.restcomm.protocols.ss7.map.api.MAPMessage) payload).getMAPDialog();
            }
        } catch (Exception e) {
            // Ignore if dialog extraction fails
        }
        publishMapEvent(type, payload, dialog);
    }

    private void publishMapEvent(String type, Object payload, MAPDialog dialog) {
        if (eventPublisher == null) return;
        
        try {
            java.util.Map<String, Object> eventData = new java.util.HashMap<>();
            eventData.put("type", type);
            eventData.put("timestamp", System.currentTimeMillis());
            
            if (dialog != null) {
                eventData.put("dialogId", dialog.getLocalDialogId());
                eventData.put("remoteDialogId", dialog.getRemoteDialogId());
            }
            
            // Serialize payload to simplified JSON or map if needed
            // For now, putting the raw object and letting Jackson handler in Adapter deal with it
            eventData.put("payload", payload);

            String topic = "map.events." + type.toLowerCase().replace("_", ".");
            eventPublisher.publishEvent(topic, eventData);
            
            logger.info("Published MAP event: {} (Dialog: {})", type, dialog != null ? dialog.getLocalDialogId() : "null");
        } catch (Exception e) {
            logger.error("Failed to publish MAP event: " + type, e);
        }
    }
    @Override public void onProcessUnstructuredSSResponse(ProcessUnstructuredSSResponse response) {
        publishMapEvent("PROCESS_UNSTRUCTURED_SS_RESPONSE", response);
    }
    @Override public void onUnstructuredSSRequest(UnstructuredSSRequest request) {
        publishMapEvent("UNSTRUCTURED_SS_REQUEST", request);
    }
    @Override public void onUnstructuredSSResponse(UnstructuredSSResponse response) {
        publishMapEvent("UNSTRUCTURED_SS_RESPONSE", response);
    }
    @Override public void onUnstructuredSSNotifyRequest(UnstructuredSSNotifyRequest request) {
        publishMapEvent("UNSTRUCTURED_SS_NOTIFY_REQUEST", request);
    }
    @Override public void onUnstructuredSSNotifyResponse(UnstructuredSSNotifyResponse response) {
        publishMapEvent("UNSTRUCTURED_SS_NOTIFY_RESPONSE", response);
    }
    @Override public void onRegisterSSRequest(RegisterSSRequest request) {
        publishMapEvent("REGISTER_SS_REQUEST", request);
    }
    @Override public void onRegisterSSResponse(RegisterSSResponse response) {
        publishMapEvent("REGISTER_SS_RESPONSE", response);
    }
    @Override public void onEraseSSRequest(EraseSSRequest request) {
        publishMapEvent("ERASE_SS_REQUEST", request);
    }
    @Override public void onEraseSSResponse(EraseSSResponse response) {
        publishMapEvent("ERASE_SS_RESPONSE", response);
    }
    @Override public void onActivateSSRequest(ActivateSSRequest request) {
        publishMapEvent("ACTIVATE_SS_REQUEST", request);
    }
    @Override public void onActivateSSResponse(ActivateSSResponse response) {
        publishMapEvent("ACTIVATE_SS_RESPONSE", response);
    }
    @Override public void onDeactivateSSRequest(DeactivateSSRequest request) {
        publishMapEvent("DEACTIVATE_SS_REQUEST", request);
    }
    @Override public void onDeactivateSSResponse(DeactivateSSResponse response) {
        publishMapEvent("DEACTIVATE_SS_RESPONSE", response);
    }
    @Override public void onInterrogateSSRequest(InterrogateSSRequest request) {
        publishMapEvent("INTERROGATE_SS_REQUEST", request);
    }
    @Override public void onInterrogateSSResponse(InterrogateSSResponse response) {
        publishMapEvent("INTERROGATE_SS_RESPONSE", response);
    }
    @Override public void onGetPasswordRequest(GetPasswordRequest request) {
        publishMapEvent("GET_PASSWORD_REQUEST", request);
    }
    @Override public void onGetPasswordResponse(GetPasswordResponse response) {
        publishMapEvent("GET_PASSWORD_RESPONSE", response);
    }
    @Override public void onRegisterPasswordRequest(RegisterPasswordRequest request) {
        publishMapEvent("REGISTER_PASSWORD_REQUEST", request);
    } // Added
    @Override public void onRegisterPasswordResponse(RegisterPasswordResponse response) {
        publishMapEvent("REGISTER_PASSWORD_RESPONSE", response);
    }

    // Dialog
    @Override public void onDialogDelimiter(MAPDialog mapDialog) {
        publishMapEvent("DIALOG_DELIMITER", mapDialog);
    }
    
    @Override
    public void onDialogRequest(MAPDialog mapDialog, AddressString destAddress, AddressString origAddress, MAPExtensionContainer extensionContainer) {
    }
    
    @Override
    public void onDialogRequestEricsson(MAPDialog mapDialog, AddressString destAddress, AddressString origAddress, AddressString destReference, AddressString origReference) {
    }

    @Override public void onDialogAccept(MAPDialog mapDialog, MAPExtensionContainer extensionContainer) {
        publishMapEvent("DIALOG_ACCEPT", extensionContainer);
    }
    @Override public void onDialogRelease(MAPDialog mapDialog) {
        publishMapEvent("DIALOG_RELEASE", mapDialog);
    }
    @Override public void onDialogTimeout(MAPDialog mapDialog) {
        publishMapEvent("DIALOG_TIMEOUT", mapDialog);
    }
    @Override public void onDialogNotice(MAPDialog mapDialog, MAPNoticeProblemDiagnostic notice) {
        publishMapEvent("DIALOG_NOTICE", notice);
    }
    @Override public void onDialogProviderAbort(MAPDialog mapDialog, MAPAbortProviderReason abortReason, MAPAbortSource abortSource, MAPExtensionContainer extensionContainer) {
        publishMapEvent("DIALOG_PROVIDER_ABORT", extensionContainer);
    }
    @Override public void onDialogUserAbort(MAPDialog mapDialog, MAPUserAbortChoice userReason, MAPExtensionContainer extensionContainer) {
        publishMapEvent("DIALOG_USER_ABORT", extensionContainer);
    }
    @Override public void onDialogReject(MAPDialog mapDialog, MAPRefuseReason refuseReason, ApplicationContextName alternativeApplicationContext, MAPExtensionContainer extensionContainer) {
        publishMapEvent("DIALOG_REJECT", extensionContainer);
    }
    @Override public void onDialogClose(MAPDialog mapDialog) {
        publishMapEvent("DIALOG_CLOSE", mapDialog);
    }
}
