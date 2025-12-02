package com.company.ss7ha.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Dialog State Model - License-safe representation of SS7 Dialog
 *
 * This class contains ONLY primitive data extracted from JSS7 Dialog.
 * NO JSS7 objects are stored here - maintains AGPL firewall!
 *
 * Can be serialized to JSON and stored in NATS JetStream KV.
 */
public class DialogState implements Serializable {

    private static final long serialVersionUID = 1L;

    // Dialog identification
    private Long dialogId;
    private String remoteDialogId;

    // Dialog metadata
    private String dialogState;  // IDLE, ACTIVE, etc.
    private Instant createdAt;
    private Instant lastUpdateAt;

    // Address information (primitives only)
    private String localAddress;
    private String remoteAddress;
    private Integer localSsn;
    private Integer remoteSsn;

    // Application context
    private String applicationContextName;
    private Long applicationContextVersion;

    // Service type
    private String serviceType;  // SMS, USSD, etc.

    // Custom data
    private Map<String, String> customData = new HashMap<>();

    // Constructors
    public DialogState() {
        this.createdAt = Instant.now();
        this.lastUpdateAt = Instant.now();
    }

    // Getters and Setters
    public Long getDialogId() {
        return dialogId;
    }

    public void setDialogId(Long dialogId) {
        this.dialogId = dialogId;
    }

    public String getRemoteDialogId() {
        return remoteDialogId;
    }

    public void setRemoteDialogId(String remoteDialogId) {
        this.remoteDialogId = remoteDialogId;
    }

    public String getDialogState() {
        return dialogState;
    }

    public void setDialogState(String dialogState) {
        this.dialogState = dialogState;
        this.lastUpdateAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastUpdateAt() {
        return lastUpdateAt;
    }

    public void setLastUpdateAt(Instant lastUpdateAt) {
        this.lastUpdateAt = lastUpdateAt;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public Integer getLocalSsn() {
        return localSsn;
    }

    public void setLocalSsn(Integer localSsn) {
        this.localSsn = localSsn;
    }

    public Integer getRemoteSsn() {
        return remoteSsn;
    }

    public void setRemoteSsn(Integer remoteSsn) {
        this.remoteSsn = remoteSsn;
    }

    public String getApplicationContextName() {
        return applicationContextName;
    }

    public void setApplicationContextName(String applicationContextName) {
        this.applicationContextName = applicationContextName;
    }

    public Long getApplicationContextVersion() {
        return applicationContextVersion;
    }

    public void setApplicationContextVersion(Long applicationContextVersion) {
        this.applicationContextVersion = applicationContextVersion;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public Map<String, String> getCustomData() {
        return customData;
    }

    public void setCustomData(Map<String, String> customData) {
        this.customData = customData;
    }

    public void putCustomData(String key, String value) {
        this.customData.put(key, value);
        this.lastUpdateAt = Instant.now();
    }

    @Override
    public String toString() {
        return "DialogState{" +
                "dialogId=" + dialogId +
                ", remoteDialogId='" + remoteDialogId + '\'' +
                ", dialogState='" + dialogState + '\'' +
                ", serviceType='" + serviceType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
