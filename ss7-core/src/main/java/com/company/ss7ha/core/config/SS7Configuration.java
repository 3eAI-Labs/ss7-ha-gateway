package com.company.ss7ha.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * SS7 Stack configuration with support for hierarchical GT routing
 */
public class SS7Configuration {
    
    // M3UA Configuration
    private String m3uaLocalHost = "127.0.0.1";
    private int m3uaLocalPort = 2905;
    private String m3uaRemoteHost = "127.0.0.1";
    private int m3uaRemotePort = 2906;
    private int m3uaRoutingContext = 101;
    private String m3uaAspName = "ASP1";
    private String m3uaAsName = "AS1";
    
        // SCCP Configuration
    
        private int sccpLocalSpc = 1;
    
        private int sccpRemoteSpc = 2;
    
        private int sccpLocalSsn = 8;  // Default SMSC SSN
    
        private int sccpRemoteSsn = 6; // HLR SSN
    
        private int sccpCapSsn = 146;  // CAP SSN for IN services
    
        
    
        // Local Address Configurations (GT + SSNs)
    
        // Replaces genericGT1/genericGT2 with a flexible list
    
        private List<LocalAddressConfig> localAddressConfigs = new ArrayList<>();
    
        
    
        // Internal GTs for load distribution
    
        private List<InternalGT> internalGTs = new ArrayList<>();
    
        
    
        // TCAP Configuration
    
        private long tcapDialogTimeout = 60000; // 60 seconds
    
        private long tcapInvokeTimeout = 30000; // 30 seconds
    
        private int tcapMaxDialogs = 1000;
    
        
    
        // MAP Configuration
    
        private int mapVersion = 3;
    
        private boolean mapSmsEnabled = true;
    
        private boolean capEnabled = true;
    
        
    
        // HA Configuration
    
        private boolean haEnabled = true;
    
        private String haMode = "ACTIVE_STANDBY"; // ACTIVE_STANDBY or ACTIVE_ACTIVE
    
        private long haHeartbeatInterval = 5000; // 5 seconds
    
        private int haFailoverTimeout = 30000; // 30 seconds
    
        
    
        public SS7Configuration() {
    
            // Initialize default internal GTs
    
            internalGTs.add(new InternalGT("GT1", "123456791", "International", "^\\+.*"));
    
            internalGTs.add(new InternalGT("GT2", "123456792", "National", "^0.*"));
    
            internalGTs.add(new InternalGT("GT3", "123456793", "Premium", "^[1-9][0-9]{3,6}$"));
    
            internalGTs.add(new InternalGT("GT4", "123456794", "Bulk", "BULK"));
    
            internalGTs.add(new InternalGT("GT5", "123456795", "Emergency", "^(911|112|999)$"));
    
            
    
            // Default Local Address (Backward compatibility)
    
            localAddressConfigs.add(new LocalAddressConfig("123456789", sccpLocalSsn));
    
        }
    
        
    
        public void loadFromProperties(Properties props) {
    
            // M3UA
    
            m3uaLocalHost = props.getProperty("m3ua.local.host", m3uaLocalHost);
    
            m3uaLocalPort = Integer.parseInt(props.getProperty("m3ua.local.port", String.valueOf(m3uaLocalPort)));
    
            m3uaRemoteHost = props.getProperty("m3ua.remote.host", m3uaRemoteHost);
    
            m3uaRemotePort = Integer.parseInt(props.getProperty("m3ua.remote.port", String.valueOf(m3uaRemotePort)));
    
            m3uaRoutingContext = Integer.parseInt(props.getProperty("m3ua.routing.context", String.valueOf(m3uaRoutingContext)));
    
            
    
            // SCCP
    
            sccpLocalSpc = Integer.parseInt(props.getProperty("sccp.local.spc", String.valueOf(sccpLocalSpc)));
    
            sccpRemoteSpc = Integer.parseInt(props.getProperty("sccp.remote.spc", String.valueOf(sccpRemoteSpc)));
    
            sccpLocalSsn = Integer.parseInt(props.getProperty("sccp.local.ssn", String.valueOf(sccpLocalSsn)));
    
            sccpRemoteSsn = Integer.parseInt(props.getProperty("sccp.remote.ssn", String.valueOf(sccpRemoteSsn)));
    
            
    
            // Local Addresses (Flexible GT:SSN config)
    
            String addressesStr = props.getProperty("sccp.local.addresses");
    
            if (addressesStr != null && !addressesStr.isEmpty()) {
    
                localAddressConfigs.clear();
    
                String[] addresses = addressesStr.split(",");
    
                for (String addr : addresses) {
    
                    parseAndAddLocalAddress(addr.trim());
    
                }
    
            }
    
            
    
            // Load internal GTs from properties
    
            loadInternalGTs(props);
    
            
    
            // HA
    
            haEnabled = Boolean.parseBoolean(props.getProperty("ha.enabled", String.valueOf(haEnabled)));
    
            haMode = props.getProperty("ha.mode", haMode);
    
            haHeartbeatInterval = Long.parseLong(props.getProperty("ha.heartbeat.interval", String.valueOf(haHeartbeatInterval)));
    
        }
    
        
    
        private void parseAndAddLocalAddress(String addressStr) {
    
            // Format: GT or GT:SSN1/SSN2
    
            String gt;
    
            List<Integer> ssns = new ArrayList<>();
    
            
    
            if (addressStr.contains(":")) {
    
                String[] parts = addressStr.split(":");
    
                gt = parts[0].trim();
    
                String[] ssnParts = parts[1].split("/");
    
                for (String ssnStr : ssnParts) {
    
                    try {
    
                        ssns.add(Integer.parseInt(ssnStr.trim()));
    
                    } catch (NumberFormatException e) {
    
                        // Log error or ignore
    
                    }
    
                }
    
            } else {
    
                gt = addressStr.trim();
    
                // If no SSN specified, use default Local SSN and CAP SSN if enabled
    
                ssns.add(sccpLocalSsn);
    
                if (capEnabled) {
    
                    ssns.add(sccpCapSsn);
    
                }
    
            }
    
            
    
            if (!gt.isEmpty()) {
    
                for (Integer ssn : ssns) {
    
                    localAddressConfigs.add(new LocalAddressConfig(gt, ssn));
    
                }
    
            }
    
        }
    
        
    
        private void loadInternalGTs(Properties props) {
    
            internalGTs.clear();
    
            int i = 0;
    
            while (props.containsKey("sccp.gt.internal[" + i + "].name")) {
    
                String name = props.getProperty("sccp.gt.internal[" + i + "].name");
    
                String address = props.getProperty("sccp.gt.internal[" + i + "].address");
    
                String description = props.getProperty("sccp.gt.internal[" + i + "].description", "");
    
                String routingPattern = props.getProperty("sccp.gt.internal[" + i + "].pattern", ".*");
    
                internalGTs.add(new InternalGT(name, address, description, routingPattern));
    
                i++;
    
            }
    
        }
    
        
    
        /**
    
         * Configuration for a single Local Address (GT + SSN pair)
    
         */
    
        public static class LocalAddressConfig {
    
            private String globalTitle;
    
            private int ssn;
    
    
    
            public LocalAddressConfig(String globalTitle, int ssn) {
    
                this.globalTitle = globalTitle;
    
                this.ssn = ssn;
    
            }
    
    
    
            public String getGlobalTitle() { return globalTitle; }
    
            public int getSsn() { return ssn; }
    
        }
    
        
    
        /**
    
         * Internal GT configuration
    
         */
    
        public static class InternalGT {
    
            private String name;
    
            private String address;
    
            private String description;
    
            private String routingPattern;
    
            private boolean active = true;
    
            
    
            public InternalGT(String name, String address, String description, String routingPattern) {
    
                this.name = name;
    
                this.address = address;
    
                this.description = description;
    
                this.routingPattern = routingPattern;
    
            }
    
            
    
            // Getters and setters
    
            public String getName() { return name; }
    
            public String getAddress() { return address; }
    
            public String getDescription() { return description; }
    
            public String getRoutingPattern() { return routingPattern; }
    
            public boolean isActive() { return active; }
    
            public void setActive(boolean active) { this.active = active; }
    
        }
    
        
    
        // Getters and setters
    
        public String getM3uaLocalHost() { return m3uaLocalHost; }
    
        public int getM3uaLocalPort() { return m3uaLocalPort; }
    
        public String getM3uaRemoteHost() { return m3uaRemoteHost; }
    
        public int getM3uaRemotePort() { return m3uaRemotePort; }
    
        public int getM3uaRoutingContext() { return m3uaRoutingContext; }
    
        public String getM3uaAspName() { return m3uaAspName; }
    
        public String getM3uaAsName() { return m3uaAsName; }
    
        
    
        public int getSccpLocalSpc() { return sccpLocalSpc; }
    
        public int getSccpRemoteSpc() { return sccpRemoteSpc; }
    
        public int getSccpLocalSsn() { return sccpLocalSsn; }
    
        public int getSccpRemoteSsn() { return sccpRemoteSsn; }
    
        public int getSccpCapSsn() { return sccpCapSsn; }
    
        
    
        public List<LocalAddressConfig> getLocalAddressConfigs() { return localAddressConfigs; }
    
        public List<InternalGT> getInternalGTs() { return internalGTs; }
    
        
    
        public long getTcapDialogTimeout() { return tcapDialogTimeout; }
    
        public long getTcapInvokeTimeout() { return tcapInvokeTimeout; }
    
        public int getTcapMaxDialogs() { return tcapMaxDialogs; }
    
        
    
        public int getMapVersion() { return mapVersion; }
    
        public boolean isMapSmsEnabled() { return mapSmsEnabled; }
    
        public boolean isCapEnabled() { return capEnabled; }
    
        
    
        public boolean isHaEnabled() { return haEnabled; }
    
        public String getHaMode() { return haMode; }
    
        public long getHaHeartbeatInterval() { return haHeartbeatInterval; }
    
        public int getHaFailoverTimeout() { return haFailoverTimeout; }
    
    }