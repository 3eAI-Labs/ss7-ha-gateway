package com.company.ss7ha.messages;

/**
 * MAP SMS message for MO/MT ForwardSM operations
 */
public class MapSmsMessage extends SS7Message {
    
    public enum SmsType {
        MO_FORWARD_SM,
        MT_FORWARD_SM,
        MO_FORWARD_SM_RESP,
        MT_FORWARD_SM_RESP,
        DELIVERY_REPORT
    }
    
    public enum SmsEncoding {
        GSM7,
        UCS2,
        BINARY
    }
    
    private SmsType smsType;
    private String sender;
    private String recipient;
    private String content;
    private SmsEncoding encoding;
    private NetworkInfo networkInfo;
    private SmsMetadata metadata;
    
    // SMS specific metadata
    public static class SmsMetadata {
        private Integer messageReference;
        private Integer validityPeriod; // in minutes
        private Integer priority;
        private Boolean requestDeliveryReport;
        private Boolean concatenated;
        private Integer totalParts;
        private Integer partNumber;
        private String udh; // User Data Header for concatenated messages
        private Integer dcs; // Data Coding Scheme
        private String smscAddress;
        
        // Getters and setters
        public Integer getMessageReference() { return messageReference; }
        public void setMessageReference(Integer messageReference) { this.messageReference = messageReference; }
        public Integer getValidityPeriod() { return validityPeriod; }
        public void setValidityPeriod(Integer validityPeriod) { this.validityPeriod = validityPeriod; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        public Boolean getRequestDeliveryReport() { return requestDeliveryReport; }
        public void setRequestDeliveryReport(Boolean requestDeliveryReport) { this.requestDeliveryReport = requestDeliveryReport; }
        public Boolean getConcatenated() { return concatenated; }
        public void setConcatenated(Boolean concatenated) { this.concatenated = concatenated; }
        public Integer getTotalParts() { return totalParts; }
        public void setTotalParts(Integer totalParts) { this.totalParts = totalParts; }
        public Integer getPartNumber() { return partNumber; }
        public void setPartNumber(Integer partNumber) { this.partNumber = partNumber; }
        public String getUdh() { return udh; }
        public void setUdh(String udh) { this.udh = udh; }
        public Integer getDcs() { return dcs; }
        public void setDcs(Integer dcs) { this.dcs = dcs; }
        public String getSmscAddress() { return smscAddress; }
        public void setSmscAddress(String smscAddress) { this.smscAddress = smscAddress; }
    }
    
    // Response/Error fields
    private Integer errorCode;
    private String errorMessage;
    private DeliveryStatus deliveryStatus;
    
    public enum DeliveryStatus {
        PENDING,
        DELIVERED,
        FAILED,
        EXPIRED,
        REJECTED,
        UNDELIVERABLE
    }
    
    public MapSmsMessage() {
        super();
        this.metadata = new SmsMetadata();
        this.networkInfo = new NetworkInfo();
    }
    
    // Builder pattern for easy construction
    public static MapSmsMessageBuilder builder() {
        return new MapSmsMessageBuilder();
    }
    
    public static class MapSmsMessageBuilder {
        private MapSmsMessage message = new MapSmsMessage();
        
        public MapSmsMessageBuilder smsType(SmsType type) {
            message.setSmsType(type);
            return this;
        }
        
        public MapSmsMessageBuilder sender(String sender) {
            message.setSender(sender);
            return this;
        }
        
        public MapSmsMessageBuilder recipient(String recipient) {
            message.setRecipient(recipient);
            return this;
        }
        
        public MapSmsMessageBuilder content(String content) {
            message.setContent(content);
            return this;
        }
        
        public MapSmsMessageBuilder encoding(SmsEncoding encoding) {
            message.setEncoding(encoding);
            return this;
        }
        
        public MapSmsMessageBuilder imsi(String imsi) {
            message.getNetworkInfo().setImsi(imsi);
            return this;
        }
        
        public MapSmsMessageBuilder mscAddress(String mscAddress) {
            message.getNetworkInfo().setMscAddress(mscAddress);
            return this;
        }
        
        public MapSmsMessage build() {
            return message;
        }
    }
    
    // Getters and setters
    public SmsType getSmsType() { return smsType; }
    public void setSmsType(SmsType smsType) { this.smsType = smsType; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public SmsEncoding getEncoding() { return encoding; }
    public void setEncoding(SmsEncoding encoding) { this.encoding = encoding; }
    public NetworkInfo getNetworkInfo() { return networkInfo; }
    public void setNetworkInfo(NetworkInfo networkInfo) { this.networkInfo = networkInfo; }
    public SmsMetadata getMetadata() { return metadata; }
    public void setMetadata(SmsMetadata metadata) { this.metadata = metadata; }
    public Integer getErrorCode() { return errorCode; }
    public void setErrorCode(Integer errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public DeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; }
}