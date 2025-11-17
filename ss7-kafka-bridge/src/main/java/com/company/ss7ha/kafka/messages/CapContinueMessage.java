package com.company.ss7ha.kafka.messages;

/**
 * CAP Continue message for IN services
 */
public class CapContinueMessage extends SS7Message {
    
    private String originalDialogId;
    private ContinueAction action;
    private String modifiedNumber;
    private Integer chargingUnits;
    
    public enum ContinueAction {
        CONTINUE_CALL,
        CONTINUE_WITH_MODIFICATION,
        CONNECT_TO_RESOURCE,
        APPLY_CHARGING
    }
    
    public CapContinueMessage() {
        super();
    }
    
    // Getters and setters
    public String getOriginalDialogId() { return originalDialogId; }
    public void setOriginalDialogId(String originalDialogId) { this.originalDialogId = originalDialogId; }
    public ContinueAction getAction() { return action; }
    public void setAction(ContinueAction action) { this.action = action; }
    public String getModifiedNumber() { return modifiedNumber; }
    public void setModifiedNumber(String modifiedNumber) { this.modifiedNumber = modifiedNumber; }
    public Integer getChargingUnits() { return chargingUnits; }
    public void setChargingUnits(Integer chargingUnits) { this.chargingUnits = chargingUnits; }
}