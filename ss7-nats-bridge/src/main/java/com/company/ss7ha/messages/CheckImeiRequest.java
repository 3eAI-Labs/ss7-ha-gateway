package com.company.ss7ha.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CheckImeiRequest {
    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("imei")
    private String imei;

    @JsonProperty("imei_sv")
    private String imeiSv;

    @JsonProperty("imsi")
    private String imsi;

    @JsonProperty("plmn_id")
    private String plmnId;

    @JsonProperty("timestamp_ms")
    private long timestampMs;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }

    public String getImeiSv() { return imeiSv; }
    public void setImeiSv(String imeiSv) { this.imeiSv = imeiSv; }

    public String getImsi() { return imsi; }
    public void setImsi(String imsi) { this.imsi = imsi; }

    public String getPlmnId() { return plmnId; }
    public void setPlmnId(String plmnId) { this.plmnId = plmnId; }

    public long getTimestampMs() { return timestampMs; }
    public void setTimestampMs(long timestampMs) { this.timestampMs = timestampMs; }
}
