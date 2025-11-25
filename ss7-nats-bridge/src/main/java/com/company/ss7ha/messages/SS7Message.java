package com.company.ss7ha.messages;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all SS7 messages sent through NATS
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "messageType"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = MapSmsMessage.class, name = "MAP_SMS")
})
public abstract class SS7Message {

    private String messageId;
    private Instant timestamp;
    private String messageType;
    private String sourceGT;
    private String destinationGT;
    private String dialogId;
    private String correlationId;
    private MessageDirection direction;

    public SS7Message() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public SS7Message(String messageType) {
        this();
        this.messageType = messageType;
    }
    
    public enum MessageDirection {
        INBOUND,   // From network to application
        OUTBOUND   // From application to network
    }
    
    // Common SS7 parameters
    public static class NetworkInfo {
        private String mscAddress;
        private String vlrAddress;
        private String hlrAddress;
        private String imsi;
        private String msisdn;
        private Integer networkId;
        private Integer cellId;
        
        // Getters and setters
        public String getMscAddress() { return mscAddress; }
        public void setMscAddress(String mscAddress) { this.mscAddress = mscAddress; }
        public String getVlrAddress() { return vlrAddress; }
        public void setVlrAddress(String vlrAddress) { this.vlrAddress = vlrAddress; }
        public String getHlrAddress() { return hlrAddress; }
        public void setHlrAddress(String hlrAddress) { this.hlrAddress = hlrAddress; }
        public String getImsi() { return imsi; }
        public void setImsi(String imsi) { this.imsi = imsi; }
        public String getMsisdn() { return msisdn; }
        public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
        public Integer getNetworkId() { return networkId; }
        public void setNetworkId(Integer networkId) { this.networkId = networkId; }
        public Integer getCellId() { return cellId; }
        public void setCellId(Integer cellId) { this.cellId = cellId; }
    }
    
    // Getters and setters
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getSourceGT() { return sourceGT; }
    public void setSourceGT(String sourceGT) { this.sourceGT = sourceGT; }
    public String getDestinationGT() { return destinationGT; }
    public void setDestinationGT(String destinationGT) { this.destinationGT = destinationGT; }
    public String getDialogId() { return dialogId; }
    public void setDialogId(String dialogId) { this.dialogId = dialogId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public MessageDirection getDirection() { return direction; }
    public void setDirection(MessageDirection direction) { this.direction = direction; }
}