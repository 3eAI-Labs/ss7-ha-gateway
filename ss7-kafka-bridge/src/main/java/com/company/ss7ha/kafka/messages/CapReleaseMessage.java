package com.company.ss7ha.kafka.messages;

/**
 * CAP Release message for IN services
 */
public class CapReleaseMessage extends SS7Message {
    
    private String originalDialogId;
    private ReleaseCause cause;
    private String announcementId;
    
    public enum ReleaseCause {
        NORMAL_CLEARING,
        USER_BUSY,
        NO_ANSWER,
        INSUFFICIENT_BALANCE,
        SERVICE_NOT_ALLOWED,
        INVALID_NUMBER,
        NETWORK_FAILURE
    }
    
    public CapReleaseMessage() {
        super();
    }
    
    // Getters and setters
    public String getOriginalDialogId() { return originalDialogId; }
    public void setOriginalDialogId(String originalDialogId) { this.originalDialogId = originalDialogId; }
    public ReleaseCause getCause() { return cause; }
    public void setCause(ReleaseCause cause) { this.cause = cause; }
    public String getAnnouncementId() { return announcementId; }
    public void setAnnouncementId(String announcementId) { this.announcementId = announcementId; }
}