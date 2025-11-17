package com.company.ss7ha.kafka.messages;

/**
 * CAP InitialDP message for IN services
 */
public class CapInitialDPMessage extends SS7Message {
    
    private String serviceKey;
    private String callingPartyNumber;
    private String calledPartyNumber;
    private String redirectingNumber;
    private String locationNumber;
    private String imsi;
    private String mscAddress;
    private CallType callType;
    private BearerCapability bearerCapability;
    
    public enum CallType {
        VOICE,
        VIDEO,
        DATA,
        FAX,
        SMS
    }
    
    public enum BearerCapability {
        SPEECH,
        UNRESTRICTED_DIGITAL,
        RESTRICTED_DIGITAL,
        AUDIO_3_1_KHZ
    }
    
    // IN specific parameters
    private INServiceData inServiceData;
    
    public static class INServiceData {
        private Integer serviceClass;
        private String vpnId;
        private String accountCode;
        private Double balance;
        private String subscriberCategory;
        private Boolean roamingAllowed;
        
        // Getters and setters
        public Integer getServiceClass() { return serviceClass; }
        public void setServiceClass(Integer serviceClass) { this.serviceClass = serviceClass; }
        public String getVpnId() { return vpnId; }
        public void setVpnId(String vpnId) { this.vpnId = vpnId; }
        public String getAccountCode() { return accountCode; }
        public void setAccountCode(String accountCode) { this.accountCode = accountCode; }
        public Double getBalance() { return balance; }
        public void setBalance(Double balance) { this.balance = balance; }
        public String getSubscriberCategory() { return subscriberCategory; }
        public void setSubscriberCategory(String subscriberCategory) { this.subscriberCategory = subscriberCategory; }
        public Boolean getRoamingAllowed() { return roamingAllowed; }
        public void setRoamingAllowed(Boolean roamingAllowed) { this.roamingAllowed = roamingAllowed; }
    }
    
    public CapInitialDPMessage() {
        super();
        this.inServiceData = new INServiceData();
    }
    
    // Getters and setters
    public String getServiceKey() { return serviceKey; }
    public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }
    public String getCallingPartyNumber() { return callingPartyNumber; }
    public void setCallingPartyNumber(String callingPartyNumber) { this.callingPartyNumber = callingPartyNumber; }
    public String getCalledPartyNumber() { return calledPartyNumber; }
    public void setCalledPartyNumber(String calledPartyNumber) { this.calledPartyNumber = calledPartyNumber; }
    public String getRedirectingNumber() { return redirectingNumber; }
    public void setRedirectingNumber(String redirectingNumber) { this.redirectingNumber = redirectingNumber; }
    public String getLocationNumber() { return locationNumber; }
    public void setLocationNumber(String locationNumber) { this.locationNumber = locationNumber; }
    public String getImsi() { return imsi; }
    public void setImsi(String imsi) { this.imsi = imsi; }
    public String getMscAddress() { return mscAddress; }
    public void setMscAddress(String mscAddress) { this.mscAddress = mscAddress; }
    public CallType getCallType() { return callType; }
    public void setCallType(CallType callType) { this.callType = callType; }
    public BearerCapability getBearerCapability() { return bearerCapability; }
    public void setBearerCapability(BearerCapability bearerCapability) { this.bearerCapability = bearerCapability; }
    public INServiceData getInServiceData() { return inServiceData; }
    public void setInServiceData(INServiceData inServiceData) { this.inServiceData = inServiceData; }
}