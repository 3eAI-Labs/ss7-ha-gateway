package com.company.ss7ha.core.redis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable representation of SS7 Dialog state for Redis storage.
 *
 * This class contains ONLY primitive types and plain Java objects.
 * NO JSS7/TCAP objects are included to ensure clean serialization.
 *
 * License: AGPL-3.0
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DialogState implements Serializable {

    private static final long serialVersionUID = 1L;

    // Core identifiers
    @JsonProperty("dialogId")
    private Long dialogId;

    @JsonProperty("remoteDialogId")
    private Long remoteDialogId;

    // Addresses (stored as primitive data)
    @JsonProperty("localGT")
    private String localGT;

    @JsonProperty("remoteGT")
    private String remoteGT;

    @JsonProperty("localSSN")
    private Integer localSSN;

    @JsonProperty("remoteSSN")
    private Integer remoteSSN;

    @JsonProperty("localPC")
    private Integer localPC;

    @JsonProperty("remotePC")
    private Integer remotePC;

    // State
    @JsonProperty("state")
    private String state;  // IDLE, ACTIVE, INITIAL_SENT, INITIAL_RECEIVED, etc.

    @JsonProperty("networkId")
    private Integer networkId;

    // Timing
    @JsonProperty("createdAt")
    private Long createdAt;

    @JsonProperty("lastActivity")
    private Long lastActivity;

    @JsonProperty("dialogTimeout")
    private Long dialogTimeout;

    // Application Context
    @JsonProperty("applicationContextName")
    private String applicationContextName;

    @JsonProperty("applicationContextVersion")
    private Integer applicationContextVersion;

    // Sequence control
    @JsonProperty("localSeqControl")
    private Integer localSeqControl;

    @JsonProperty("remoteSeqControl")
    private Integer remoteSeqControl;

    // Pending operations
    @JsonProperty("pendingInvokes")
    private List<PendingInvoke> pendingInvokes;

    // Custom metadata
    @JsonProperty("customData")
    private Map<String, String> customData;

    // Protocol-specific flags
    @JsonProperty("returnMessageOnError")
    private Boolean returnMessageOnError;

    @JsonProperty("preArrangedEnd")
    private Boolean preArrangedEnd;

    public DialogState() {
        this.pendingInvokes = new ArrayList<>();
        this.customData = new HashMap<>();
        this.createdAt = System.currentTimeMillis();
        this.lastActivity = System.currentTimeMillis();
    }

    // Getters and Setters

    public Long getDialogId() {
        return dialogId;
    }

    public void setDialogId(Long dialogId) {
        this.dialogId = dialogId;
    }

    public Long getRemoteDialogId() {
        return remoteDialogId;
    }

    public void setRemoteDialogId(Long remoteDialogId) {
        this.remoteDialogId = remoteDialogId;
    }

    public String getLocalGT() {
        return localGT;
    }

    public void setLocalGT(String localGT) {
        this.localGT = localGT;
    }

    public String getRemoteGT() {
        return remoteGT;
    }

    public void setRemoteGT(String remoteGT) {
        this.remoteGT = remoteGT;
    }

    public Integer getLocalSSN() {
        return localSSN;
    }

    public void setLocalSSN(Integer localSSN) {
        this.localSSN = localSSN;
    }

    public Integer getRemoteSSN() {
        return remoteSSN;
    }

    public void setRemoteSSN(Integer remoteSSN) {
        this.remoteSSN = remoteSSN;
    }

    public Integer getLocalPC() {
        return localPC;
    }

    public void setLocalPC(Integer localPC) {
        this.localPC = localPC;
    }

    public Integer getRemotePC() {
        return remotePC;
    }

    public void setRemotePC(Integer remotePC) {
        this.remotePC = remotePC;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Integer getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Integer networkId) {
        this.networkId = networkId;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(Long lastActivity) {
        this.lastActivity = lastActivity;
    }

    public Long getDialogTimeout() {
        return dialogTimeout;
    }

    public void setDialogTimeout(Long dialogTimeout) {
        this.dialogTimeout = dialogTimeout;
    }

    public String getApplicationContextName() {
        return applicationContextName;
    }

    public void setApplicationContextName(String applicationContextName) {
        this.applicationContextName = applicationContextName;
    }

    public Integer getApplicationContextVersion() {
        return applicationContextVersion;
    }

    public void setApplicationContextVersion(Integer applicationContextVersion) {
        this.applicationContextVersion = applicationContextVersion;
    }

    public Integer getLocalSeqControl() {
        return localSeqControl;
    }

    public void setLocalSeqControl(Integer localSeqControl) {
        this.localSeqControl = localSeqControl;
    }

    public Integer getRemoteSeqControl() {
        return remoteSeqControl;
    }

    public void setRemoteSeqControl(Integer remoteSeqControl) {
        this.remoteSeqControl = remoteSeqControl;
    }

    public List<PendingInvoke> getPendingInvokes() {
        return pendingInvokes;
    }

    public void setPendingInvokes(List<PendingInvoke> pendingInvokes) {
        this.pendingInvokes = pendingInvokes;
    }

    public Map<String, String> getCustomData() {
        return customData;
    }

    public void setCustomData(Map<String, String> customData) {
        this.customData = customData;
    }

    public Boolean getReturnMessageOnError() {
        return returnMessageOnError;
    }

    public void setReturnMessageOnError(Boolean returnMessageOnError) {
        this.returnMessageOnError = returnMessageOnError;
    }

    public Boolean getPreArrangedEnd() {
        return preArrangedEnd;
    }

    public void setPreArrangedEnd(Boolean preArrangedEnd) {
        this.preArrangedEnd = preArrangedEnd;
    }

    /**
     * Update last activity timestamp.
     */
    public void touch() {
        this.lastActivity = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "DialogState{" +
                "dialogId=" + dialogId +
                ", remoteDialogId=" + remoteDialogId +
                ", localGT='" + localGT + '\'' +
                ", remoteGT='" + remoteGT + '\'' +
                ", state='" + state + '\'' +
                ", networkId=" + networkId +
                ", pendingInvokes=" + pendingInvokes.size() +
                '}';
    }
}
