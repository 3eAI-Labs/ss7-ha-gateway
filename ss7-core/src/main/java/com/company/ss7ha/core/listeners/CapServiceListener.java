package com.company.ss7ha.core.listeners;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.mapper.CapJsonMapper;
import com.company.ss7ha.nats.publisher.SS7NatsPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import org.restcomm.protocols.ss7.cap.api.CAPDialog;
import org.restcomm.protocols.ss7.cap.api.CAPMessage;
import org.restcomm.protocols.ss7.cap.api.CAPDialogListener;
import org.restcomm.protocols.ss7.cap.api.dialog.CAPGprsReferenceNumber;
import org.restcomm.protocols.ss7.cap.api.dialog.CAPGeneralAbortReason;
import org.restcomm.protocols.ss7.cap.api.dialog.CAPUserAbortReason;
import org.restcomm.protocols.ss7.cap.api.dialog.CAPNoticeProblemDiagnostic;
import org.restcomm.protocols.ss7.tcap.asn.comp.PAbortCauseType;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.*;
import org.restcomm.protocols.ss7.cap.api.service.circuitSwitchedCall.primitive.*;
import org.restcomm.protocols.ss7.cap.api.service.sms.CAPServiceSmsListener;
import org.restcomm.protocols.ss7.cap.api.service.sms.ConnectSMSRequest; 
import org.restcomm.protocols.ss7.cap.api.service.sms.*; 
import org.restcomm.protocols.ss7.cap.api.service.sms.primitive.*;
import org.restcomm.protocols.ss7.cap.api.service.gprs.CAPServiceGprsListener;
import org.restcomm.protocols.ss7.cap.api.service.gprs.*; 
import org.restcomm.protocols.ss7.cap.api.service.gprs.primitive.*;
import org.restcomm.protocols.ss7.tcap.asn.comp.Problem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CapServiceListener implements CAPServiceCircuitSwitchedCallListener, CAPServiceSmsListener, CAPServiceGprsListener, CAPDialogListener {

    private static final Logger logger = LoggerFactory.getLogger(CapServiceListener.class);
    private final EventPublisher eventPublisher;
    private final CapJsonMapper mapper;

    public CapServiceListener(EventPublisher eventPublisher, SS7NatsPublisher natsPublisher, boolean publishEvents) {
        this.eventPublisher = eventPublisher;
        this.mapper = new CapJsonMapper();
    }

    // CAPServiceListener methods
    @Override public void onCAPMessage(CAPMessage capMessage) {}
    @Override public void onInvokeTimeout(CAPDialog capDialog, Integer invokeId) {}
    @Override public void onRejectComponent(CAPDialog capDialog, Integer invokeId, Problem problem, boolean isLocal) {} 
    @Override public void onErrorComponent(CAPDialog capDialog, Integer invokeId, org.restcomm.protocols.ss7.cap.api.errors.CAPErrorMessage errMsg) {}

    // CAPDialogListener methods
    @Override public void onDialogDelimiter(CAPDialog capDialog) {}
    @Override public void onDialogRequest(CAPDialog capDialog, CAPGprsReferenceNumber ref) {}
    @Override public void onDialogAccept(CAPDialog capDialog, CAPGprsReferenceNumber ref) {}
    @Override public void onDialogUserAbort(CAPDialog capDialog, CAPGeneralAbortReason genReason, CAPUserAbortReason userReason) {}
    @Override public void onDialogProviderAbort(CAPDialog capDialog, PAbortCauseType abortCause) {}
    @Override public void onDialogClose(CAPDialog capDialog) {}
    @Override public void onDialogRelease(CAPDialog capDialog) {}
    @Override public void onDialogTimeout(CAPDialog capDialog) {}
    @Override public void onDialogNotice(CAPDialog capDialog, CAPNoticeProblemDiagnostic notice) {}

    // Voice
    @Override public void onInitialDPRequest(InitialDPRequest request) {}
    @Override public void onApplyChargingReportRequest(ApplyChargingReportRequest request) {}
    @Override public void onEventReportBCSMRequest(EventReportBCSMRequest request) {}
    @Override public void onCallInformationReportRequest(CallInformationReportRequest request) {}
    @Override public void onReleaseCallRequest(ReleaseCallRequest request) {}
    @Override public void onAssistRequestInstructionsRequest(AssistRequestInstructionsRequest request) {}
    @Override public void onConnectRequest(ConnectRequest request) {}
    @Override public void onDisconnectForwardConnectionRequest(DisconnectForwardConnectionRequest request) {}
    @Override public void onEstablishTemporaryConnectionRequest(EstablishTemporaryConnectionRequest request) {}
    @Override public void onResetTimerRequest(ResetTimerRequest request) {}
    @Override public void onActivityTestResponse(ActivityTestResponse response) {}
    @Override public void onApplyChargingRequest(ApplyChargingRequest request) {}
    @Override public void onCallGapRequest(CallGapRequest request) {} 
    @Override public void onCallInformationRequestRequest(CallInformationRequestRequest request) {}
    @Override public void onCancelRequest(CancelRequest request) {}
    @Override public void onCollectInformationRequest(CollectInformationRequest request) {}
    @Override public void onContinueRequest(ContinueRequest request) {}
    @Override public void onContinueWithArgumentRequest(ContinueWithArgumentRequest request) {}
    @Override public void onFurnishChargingInformationRequest(FurnishChargingInformationRequest request) {}
    @Override public void onRequestReportBCSMEventRequest(RequestReportBCSMEventRequest request) {}
    @Override public void onSendChargingInformationRequest(SendChargingInformationRequest request) {}
    @Override public void onSpecializedResourceReportRequest(SpecializedResourceReportRequest request) {}
    @Override public void onActivityTestRequest(ActivityTestRequest request) {}
    @Override public void onDisconnectLegRequest(DisconnectLegRequest request) {}
    @Override public void onDisconnectLegResponse(DisconnectLegResponse response) {}
    @Override public void onDisconnectForwardConnectionWithArgumentRequest(DisconnectForwardConnectionWithArgumentRequest request) {}
    @Override public void onConnectToResourceRequest(ConnectToResourceRequest request) {}
    @Override public void onPlayAnnouncementRequest(PlayAnnouncementRequest request) {}
    @Override public void onPromptAndCollectUserInformationRequest(PromptAndCollectUserInformationRequest request) {}
    @Override public void onPromptAndCollectUserInformationResponse(PromptAndCollectUserInformationResponse response) {}
    @Override public void onInitiateCallAttemptRequest(InitiateCallAttemptRequest request) {}
    @Override public void onInitiateCallAttemptResponse(InitiateCallAttemptResponse response) {}
    @Override public void onMoveLegRequest(MoveLegRequest request) {}
    @Override public void onMoveLegResponse(MoveLegResponse response) {}
    @Override public void onSplitLegRequest(SplitLegRequest request) {}
    @Override public void onSplitLegResponse(SplitLegResponse response) {}

    // SMS
    @Override public void onInitialDPSMSRequest(InitialDPSMSRequest request) {}
    @Override public void onEventReportSMSRequest(EventReportSMSRequest request) {}
    @Override public void onConnectSMSRequest(ConnectSMSRequest request) {} // Restored @Override via method presence
    @Override public void onContinueSMSRequest(ContinueSMSRequest request) {}
    @Override public void onFurnishChargingInformationSMSRequest(FurnishChargingInformationSMSRequest request) {}
    @Override public void onReleaseSMSRequest(ReleaseSMSRequest request) {}
    @Override public void onRequestReportSMSEventRequest(RequestReportSMSEventRequest request) {}
    @Override public void onResetTimerSMSRequest(org.restcomm.protocols.ss7.cap.api.service.sms.ResetTimerSMSRequest request) {}

    // GPRS
    @Override public void onInitialDpGprsRequest(InitialDpGprsRequest request) {}
    @Override public void onApplyChargingReportGPRSRequest(ApplyChargingReportGPRSRequest request) {}
    @Override public void onEntityReleasedGPRSRequest(EntityReleasedGPRSRequest request) {}
    @Override public void onEventReportGPRSRequest(EventReportGPRSRequest request) {}
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
    @Override public void onActivityTestGPRSResponse(ActivityTestGPRSResponse response) {}
    @Override public void onEventReportGPRSResponse(EventReportGPRSResponse response) {} 
    @Override public void onApplyChargingReportGPRSResponse(ApplyChargingReportGPRSResponse response) {} 
    @Override public void onEntityReleasedGPRSResponse(EntityReleasedGPRSResponse response) {}
}