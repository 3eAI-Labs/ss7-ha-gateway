package com.company.ss7ha.core.redis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Represents a pending invoke operation awaiting response.
 *
 * License: AGPL-3.0
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PendingInvoke implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("invokeId")
    private Long invokeId;

    @JsonProperty("operationCode")
    private String operationCode;

    @JsonProperty("state")
    private String state;  // SENT, PENDING_RESPONSE, TIMEOUT, etc.

    @JsonProperty("sentAt")
    private Long sentAt;

    @JsonProperty("timeout")
    private Integer timeout;  // milliseconds

    @JsonProperty("linkedId")
    private Long linkedId;

    @JsonProperty("invokeClass")
    private Integer invokeClass;  // 1=Success+Failure, 2=Failure only, etc.

    public PendingInvoke() {
    }

    public PendingInvoke(Long invokeId, String operationCode, long sentAt) {
        this.invokeId = invokeId;
        this.operationCode = operationCode;
        this.sentAt = sentAt;
        this.state = "SENT";
    }

    // Getters and Setters

    public Long getInvokeId() {
        return invokeId;
    }

    public void setInvokeId(Long invokeId) {
        this.invokeId = invokeId;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public void setOperationCode(String operationCode) {
        this.operationCode = operationCode;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Long getSentAt() {
        return sentAt;
    }

    public void setSentAt(Long sentAt) {
        this.sentAt = sentAt;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Long getLinkedId() {
        return linkedId;
    }

    public void setLinkedId(Long linkedId) {
        this.linkedId = linkedId;
    }

    public Integer getInvokeClass() {
        return invokeClass;
    }

    public void setInvokeClass(Integer invokeClass) {
        this.invokeClass = invokeClass;
    }

    /**
     * Check if invoke has timed out.
     *
     * @return true if timeout period has elapsed
     */
    public boolean isTimedOut() {
        if (timeout == null || sentAt == null) {
            return false;
        }
        return (System.currentTimeMillis() - sentAt) > timeout;
    }

    @Override
    public String toString() {
        return "PendingInvoke{" +
                "invokeId=" + invokeId +
                ", operationCode='" + operationCode + '\'' +
                ", state='" + state + '\'' +
                ", sentAt=" + sentAt +
                '}';
    }
}
