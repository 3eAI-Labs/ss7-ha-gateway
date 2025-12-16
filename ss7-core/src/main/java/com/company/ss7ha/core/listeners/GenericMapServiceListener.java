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

    @Override public void onMAPMessage(MAPMessage mapMessage) {}
    @Override public void onErrorComponent(MAPDialog mapDialog, Integer invokeId, org.restcomm.protocols.ss7.map.api.errors.MAPErrorMessage mapErrorMessage) {}
    @Override public void onRejectComponent(MAPDialog mapDialog, Integer invokeId, org.restcomm.protocols.ss7.tcap.asn.comp.Problem problem, boolean isLocal) {}
    @Override public void onInvokeTimeout(MAPDialog mapDialog, Integer invokeId) {}

    // SMS
    @Override public void onMoForwardShortMessageRequest(MoForwardShortMessageRequest request) {}
    @Override public void onMoForwardShortMessageResponse(MoForwardShortMessageResponse response) {}
    @Override public void onMtForwardShortMessageRequest(MtForwardShortMessageRequest request) {}
    @Override public void onMtForwardShortMessageResponse(MtForwardShortMessageResponse response) {}
    @Override public void onAlertServiceCentreRequest(AlertServiceCentreRequest request) {}
    @Override public void onAlertServiceCentreResponse(AlertServiceCentreResponse response) {}
    @Override public void onInformServiceCentreRequest(InformServiceCentreRequest request) {}
    @Override public void onReportSMDeliveryStatusRequest(ReportSMDeliveryStatusRequest request) {}
    @Override public void onReportSMDeliveryStatusResponse(ReportSMDeliveryStatusResponse response) {}
    @Override public void onSendRoutingInfoForSMRequest(SendRoutingInfoForSMRequest request) {}
    @Override public void onSendRoutingInfoForSMResponse(SendRoutingInfoForSMResponse response) {}
    @Override public void onNoteSubscriberPresentRequest(NoteSubscriberPresentRequest request) {}
    @Override public void onReadyForSMResponse(ReadyForSMResponse response) {}
    @Override public void onReadyForSMRequest(ReadyForSMRequest request) {}
    @Override public void onForwardShortMessageRequest(ForwardShortMessageRequest request) {} 
    @Override public void onForwardShortMessageResponse(ForwardShortMessageResponse response) {} 

    // Mobility
    @Override public void onUpdateLocationRequest(UpdateLocationRequest request) {}
    @Override public void onUpdateLocationResponse(UpdateLocationResponse response) {}
    @Override public void onUpdateGprsLocationRequest(UpdateGprsLocationRequest request) {}
    @Override public void onUpdateGprsLocationResponse(UpdateGprsLocationResponse response) {}
    @Override public void onSendAuthenticationInfoRequest(SendAuthenticationInfoRequest request) {}
    @Override public void onSendAuthenticationInfoResponse(SendAuthenticationInfoResponse response) {}
    @Override public void onCancelLocationRequest(CancelLocationRequest request) {}
    @Override public void onCancelLocationResponse(CancelLocationResponse response) {}
    @Override public void onPurgeMSRequest(PurgeMSRequest request) {}
    @Override public void onPurgeMSResponse(PurgeMSResponse response) {}
    @Override public void onSendIdentificationRequest(SendIdentificationRequest request) {}
    @Override public void onSendIdentificationResponse(SendIdentificationResponse response) {}
    @Override public void onAuthenticationFailureReportRequest(AuthenticationFailureReportRequest request) {}
    @Override public void onAuthenticationFailureReportResponse(AuthenticationFailureReportResponse response) {}
    @Override public void onRestoreDataRequest(RestoreDataRequest request) {}
    @Override public void onRestoreDataResponse(RestoreDataResponse response) {}
    @Override public void onActivateTraceModeRequest_Mobility(ActivateTraceModeRequest_Mobility request) {} 
    @Override public void onActivateTraceModeResponse_Mobility(ActivateTraceModeResponse_Mobility response) {} 
    @Override public void onCheckImeiRequest(CheckImeiRequest request) {} 
    @Override public void onCheckImeiResponse(CheckImeiResponse response) {}
    @Override public void onDeleteSubscriberDataRequest(DeleteSubscriberDataRequest request) {}
    @Override public void onDeleteSubscriberDataResponse(DeleteSubscriberDataResponse response) {}
    @Override public void onInsertSubscriberDataRequest(InsertSubscriberDataRequest request) {}
    @Override public void onInsertSubscriberDataResponse(InsertSubscriberDataResponse response) {}
    @Override public void onProvideSubscriberInfoRequest(ProvideSubscriberInfoRequest request) {} 
    @Override public void onProvideSubscriberInfoResponse(ProvideSubscriberInfoResponse response) {} 
    @Override public void onAnyTimeSubscriptionInterrogationRequest(AnyTimeSubscriptionInterrogationRequest request) {} 
    @Override public void onAnyTimeSubscriptionInterrogationResponse(AnyTimeSubscriptionInterrogationResponse response) {} 
    @Override public void onAnyTimeInterrogationRequest(AnyTimeInterrogationRequest request) {} 
    @Override public void onAnyTimeInterrogationResponse(AnyTimeInterrogationResponse response) {} 
    @Override public void onForwardCheckSSIndicationRequest(ForwardCheckSSIndicationRequest request) {}
    @Override public void onResetRequest(ResetRequest request) {}

    // USSD
    @Override public void onProcessUnstructuredSSRequest(ProcessUnstructuredSSRequest request) {}
    @Override public void onProcessUnstructuredSSResponse(ProcessUnstructuredSSResponse response) {}
    @Override public void onUnstructuredSSRequest(UnstructuredSSRequest request) {}
    @Override public void onUnstructuredSSResponse(UnstructuredSSResponse response) {}
    @Override public void onUnstructuredSSNotifyRequest(UnstructuredSSNotifyRequest request) {}
    @Override public void onUnstructuredSSNotifyResponse(UnstructuredSSNotifyResponse response) {}
    @Override public void onRegisterSSRequest(RegisterSSRequest request) {}
    @Override public void onRegisterSSResponse(RegisterSSResponse response) {}
    @Override public void onEraseSSRequest(EraseSSRequest request) {}
    @Override public void onEraseSSResponse(EraseSSResponse response) {}
    @Override public void onActivateSSRequest(ActivateSSRequest request) {}
    @Override public void onActivateSSResponse(ActivateSSResponse response) {}
    @Override public void onDeactivateSSRequest(DeactivateSSRequest request) {}
    @Override public void onDeactivateSSResponse(DeactivateSSResponse response) {}
    @Override public void onInterrogateSSRequest(InterrogateSSRequest request) {}
    @Override public void onInterrogateSSResponse(InterrogateSSResponse response) {}
    @Override public void onGetPasswordRequest(GetPasswordRequest request) {}
    @Override public void onGetPasswordResponse(GetPasswordResponse response) {}
    @Override public void onRegisterPasswordRequest(RegisterPasswordRequest request) {} // Added
    @Override public void onRegisterPasswordResponse(RegisterPasswordResponse response) {}

    // Dialog
    @Override public void onDialogDelimiter(MAPDialog mapDialog) {}
    
    @Override
    public void onDialogRequest(MAPDialog mapDialog, AddressString destAddress, AddressString origAddress, MAPExtensionContainer extensionContainer) {
    }
    
    @Override
    public void onDialogRequestEricsson(MAPDialog mapDialog, AddressString destAddress, AddressString origAddress, AddressString destReference, AddressString origReference) {
    }

    @Override public void onDialogAccept(MAPDialog mapDialog, MAPExtensionContainer extensionContainer) {}
    @Override public void onDialogRelease(MAPDialog mapDialog) {}
    @Override public void onDialogTimeout(MAPDialog mapDialog) {}
    @Override public void onDialogNotice(MAPDialog mapDialog, MAPNoticeProblemDiagnostic notice) {}
    @Override public void onDialogProviderAbort(MAPDialog mapDialog, MAPAbortProviderReason abortReason, MAPAbortSource abortSource, MAPExtensionContainer extensionContainer) {}
    @Override public void onDialogUserAbort(MAPDialog mapDialog, MAPUserAbortChoice userReason, MAPExtensionContainer extensionContainer) {}
    @Override public void onDialogReject(MAPDialog mapDialog, MAPRefuseReason refuseReason, ApplicationContextName alternativeApplicationContext, MAPExtensionContainer extensionContainer) {}
    @Override public void onDialogClose(MAPDialog mapDialog) {}
}
