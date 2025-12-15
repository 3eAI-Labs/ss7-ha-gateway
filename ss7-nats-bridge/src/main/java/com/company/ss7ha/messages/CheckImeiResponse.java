package com.company.ss7ha.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CheckImeiResponse {
    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("status")
    private int status; // 0=UNKNOWN, 1=WHITE, 2=BLACK, 3=GREY

    @JsonProperty("reason_code")
    private String reasonCode;

    @JsonProperty("error_code")
    private int errorCode;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public int getErrorCode() { return errorCode; }
    public void setErrorCode(int errorCode) { this.errorCode = errorCode; }
}
