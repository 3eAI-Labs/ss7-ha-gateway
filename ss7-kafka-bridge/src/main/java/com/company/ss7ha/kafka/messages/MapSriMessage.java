package com.company.ss7ha.kafka.messages;

/**
 * MAP Send Routing Info for SMS message
 */
public class MapSriMessage extends SS7Message {
    
    public enum SriType {
        SRI_SM_REQUEST,
        SRI_SM_RESPONSE
    }
    
    private SriType sriType;
    private String msisdn;
    private String imsi;
    private String mscAddress;
    private String vlrAddress;
    private Boolean subscriberAvailable;
    private NetworkInfo networkInfo;
    
    // Response fields
    private Integer errorCode;
    private String errorMessage;
    private LocationInfo locationInfo;
    
    public static class LocationInfo {
        private String servingNode;
        private String cellGlobalId;
        private String locationAreaId;
        private String ageOfLocation; // in minutes
        
        // Getters and setters
        public String getServingNode() { return servingNode; }
        public void setServingNode(String servingNode) { this.servingNode = servingNode; }
        public String getCellGlobalId() { return cellGlobalId; }
        public void setCellGlobalId(String cellGlobalId) { this.cellGlobalId = cellGlobalId; }
        public String getLocationAreaId() { return locationAreaId; }
        public void setLocationAreaId(String locationAreaId) { this.locationAreaId = locationAreaId; }
        public String getAgeOfLocation() { return ageOfLocation; }
        public void setAgeOfLocation(String ageOfLocation) { this.ageOfLocation = ageOfLocation; }
    }
    
    public MapSriMessage() {
        super();
        this.networkInfo = new NetworkInfo();
        this.locationInfo = new LocationInfo();
    }
    
    // Getters and setters
    public SriType getSriType() { return sriType; }
    public void setSriType(SriType sriType) { this.sriType = sriType; }
    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
    public String getImsi() { return imsi; }
    public void setImsi(String imsi) { this.imsi = imsi; }
    public String getMscAddress() { return mscAddress; }
    public void setMscAddress(String mscAddress) { this.mscAddress = mscAddress; }
    public String getVlrAddress() { return vlrAddress; }
    public void setVlrAddress(String vlrAddress) { this.vlrAddress = vlrAddress; }
    public Boolean getSubscriberAvailable() { return subscriberAvailable; }
    public void setSubscriberAvailable(Boolean subscriberAvailable) { this.subscriberAvailable = subscriberAvailable; }
    public NetworkInfo getNetworkInfo() { return networkInfo; }
    public void setNetworkInfo(NetworkInfo networkInfo) { this.networkInfo = networkInfo; }
    public Integer getErrorCode() { return errorCode; }
    public void setErrorCode(Integer errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocationInfo getLocationInfo() { return locationInfo; }
    public void setLocationInfo(LocationInfo locationInfo) { this.locationInfo = locationInfo; }
}