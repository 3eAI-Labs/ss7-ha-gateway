package com.company.ss7ha.kafka.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mobile Originated SMS message (pure JSON - NO JSS7 types).
 *
 * This class is license-safe for use in consumer applications.
 *
 * License: AGPL-3.0 (in SS7-HA-Gateway)
 *         Apache-2.0 compatible (can be used in proprietary code via JSON)
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
 */
public class MoSmsMessage extends SS7Message {

    @JsonProperty("invokeId")
    private Integer invokeId;

    @JsonProperty("sender")
    private AddressInfo sender;

    @JsonProperty("recipient")
    private AddressInfo recipient;

    @JsonProperty("message")
    private MessageContent message;

    @JsonProperty("networkInfo")
    private NetworkInfo networkInfo;

    @JsonProperty("serviceInfo")
    private ServiceInfo serviceInfo;

    public MoSmsMessage() {
        super("MO_FORWARD_SM");
    }

    // Nested classes with only primitive types

    public static class AddressInfo {
        @JsonProperty("msisdn")
        private String msisdn;

        @JsonProperty("imsi")
        private String imsi;

        @JsonProperty("nai")
        private String nai;  // Nature of Address Indicator

        // Getters/setters
        public String getMsisdn() { return msisdn; }
        public void setMsisdn(String msisdn) { this.msisdn = msisdn; }

        public String getImsi() { return imsi; }
        public void setImsi(String imsi) { this.imsi = imsi; }

        public String getNai() { return nai; }
        public void setNai(String nai) { this.nai = nai; }
    }

    public static class MessageContent {
        @JsonProperty("content")
        private String content;  // Base64 encoded

        @JsonProperty("encoding")
        private String encoding;  // GSM7, UCS2, etc.

        @JsonProperty("udh")
        private String udh;  // User Data Header (Base64)

        @JsonProperty("messageClass")
        private Integer messageClass;

        // Getters/setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }

        public String getUdh() { return udh; }
        public void setUdh(String udh) { this.udh = udh; }

        public Integer getMessageClass() { return messageClass; }
        public void setMessageClass(Integer messageClass) { this.messageClass = messageClass; }
    }

    public static class NetworkInfo {
        @JsonProperty("mscAddress")
        private String mscAddress;

        @JsonProperty("vlrNumber")
        private String vlrNumber;

        @JsonProperty("locationInfo")
        private LocationInfo locationInfo;

        // Getters/setters
        public String getMscAddress() { return mscAddress; }
        public void setMscAddress(String mscAddress) { this.mscAddress = mscAddress; }

        public String getVlrNumber() { return vlrNumber; }
        public void setVlrNumber(String vlrNumber) { this.vlrNumber = vlrNumber; }

        public LocationInfo getLocationInfo() { return locationInfo; }
        public void setLocationInfo(LocationInfo locationInfo) { this.locationInfo = locationInfo; }
    }

    public static class LocationInfo {
        @JsonProperty("mcc")
        private String mcc;  // Mobile Country Code

        @JsonProperty("mnc")
        private String mnc;  // Mobile Network Code

        @JsonProperty("lac")
        private String lac;  // Location Area Code

        @JsonProperty("cellId")
        private String cellId;

        // Getters/setters
        public String getMcc() { return mcc; }
        public void setMcc(String mcc) { this.mcc = mcc; }

        public String getMnc() { return mnc; }
        public void setMnc(String mnc) { this.mnc = mnc; }

        public String getLac() { return lac; }
        public void setLac(String lac) { this.lac = lac; }

        public String getCellId() { return cellId; }
        public void setCellId(String cellId) { this.cellId = cellId; }
    }

    public static class ServiceInfo {
        @JsonProperty("serviceCode")
        private String serviceCode;

        @JsonProperty("priority")
        private String priority;  // NORMAL, HIGH, etc.

        @JsonProperty("moreMessagesToSend")
        private Boolean moreMessagesToSend;

        // Getters/setters
        public String getServiceCode() { return serviceCode; }
        public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }

        public Boolean getMoreMessagesToSend() { return moreMessagesToSend; }
        public void setMoreMessagesToSend(Boolean moreMessagesToSend) {
            this.moreMessagesToSend = moreMessagesToSend;
        }
    }

    // Main class getters/setters
    // Note: dialogId is inherited from SS7Message (String type)

    public Integer getInvokeId() {
        return invokeId;
    }

    public void setInvokeId(Integer invokeId) {
        this.invokeId = invokeId;
    }

    public AddressInfo getSender() {
        return sender;
    }

    public void setSender(AddressInfo sender) {
        this.sender = sender;
    }

    public AddressInfo getRecipient() {
        return recipient;
    }

    public void setRecipient(AddressInfo recipient) {
        this.recipient = recipient;
    }

    public MessageContent getMessage() {
        return message;
    }

    public void setMessage(MessageContent message) {
        this.message = message;
    }

    public NetworkInfo getNetworkInfo() {
        return networkInfo;
    }

    public void setNetworkInfo(NetworkInfo networkInfo) {
        this.networkInfo = networkInfo;
    }

    public ServiceInfo getServiceInfo() {
        return serviceInfo;
    }

    public void setServiceInfo(ServiceInfo serviceInfo) {
        this.serviceInfo = serviceInfo;
    }
}
