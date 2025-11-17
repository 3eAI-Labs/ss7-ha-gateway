package com.company.ss7ha.core.stack;

import com.company.ss7ha.core.config.SS7Configuration;
import org.restcomm.protocols.ss7.m3ua.M3UAManagement;
import org.restcomm.protocols.ss7.m3ua.impl.M3UAManagementImpl;
import org.restcomm.protocols.ss7.sccp.SccpStack;
import org.restcomm.protocols.ss7.sccp.impl.SccpStackImpl;
import org.restcomm.protocols.ss7.tcap.TCAPStack;
import org.restcomm.protocols.ss7.tcap.api.TCAPProvider;
import org.restcomm.protocols.ss7.tcap.asn.comp.Problem;
import org.restcomm.protocols.ss7.map.MAPStackImpl;
import org.restcomm.protocols.ss7.map.api.MAPProvider;
import org.restcomm.protocols.ss7.cap.CAPStackImpl;
import org.restcomm.protocols.ss7.cap.api.CAPProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core SS7 Stack management with MAP and CAP support
 */
public class SS7Stack {
    private static final Logger logger = LoggerFactory.getLogger(SS7Stack.class);
    
    private final SS7Configuration config;
    private final String stackName;
    
    // SS7 Stack components
    private M3UAManagement m3uaManagement;
    private SccpStack sccpStack;
    private TCAPStack tcapStack;
    private MAPProvider mapProvider;
    private CAPProvider capProvider;
    
    // Stack state
    private volatile boolean initialized = false;
    private volatile boolean started = false;
    
    public SS7Stack(SS7Configuration config, String stackName) {
        this.config = config;
        this.stackName = stackName;
    }
    
    /**
     * Initialize the SS7 stack
     */
    public void initialize() throws Exception {
        if (initialized) {
            logger.warn("SS7 Stack already initialized");
            return;
        }
        
        logger.info("Initializing SS7 Stack: {}", stackName);
        
        try {
            // Initialize M3UA
            initializeM3UA();
            
            // Initialize SCCP
            initializeSCCP();
            
            // Initialize TCAP
            initializeTCAP();
            
            // Initialize MAP if enabled
            if (config.isMapSmsEnabled()) {
                initializeMAP();
            }
            
            // Initialize CAP if enabled
            if (config.isCapEnabled()) {
                initializeCAP();
            }
            
            initialized = true;
            logger.info("SS7 Stack initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize SS7 Stack", e);
            cleanup();
            throw e;
        }
    }
    
    /**
     * Start the SS7 stack
     */
    public void start() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("SS7 Stack not initialized");
        }
        
        if (started) {
            logger.warn("SS7 Stack already started");
            return;
        }
        
        logger.info("Starting SS7 Stack: {}", stackName);
        
        try {
            // Start M3UA
            m3uaManagement.start();
            logger.info("M3UA started");
            
            // Start SCCP
            sccpStack.start();
            logger.info("SCCP started");
            
            // Start TCAP
            tcapStack.start();
            logger.info("TCAP started");
            
            // Start MAP if enabled
            if (mapProvider != null) {
                ((MAPStackImpl) mapProvider.getMAPStack()).start();
                logger.info("MAP started");
            }
            
            // Start CAP if enabled
            if (capProvider != null) {
                ((CAPStackImpl) capProvider.getCAPStack()).start();
                logger.info("CAP started");
            }
            
            started = true;
            logger.info("SS7 Stack started successfully");
            
        } catch (Exception e) {
            logger.error("Failed to start SS7 Stack", e);
            stop();
            throw e;
        }
    }
    
    /**
     * Stop the SS7 stack
     */
    public void stop() {
        logger.info("Stopping SS7 Stack: {}", stackName);
        
        started = false;
        
        try {
            // Stop MAP
            if (mapProvider != null) {
                ((MAPStackImpl) mapProvider.getMAPStack()).stop();
            }
            
            // Stop CAP
            if (capProvider != null) {
                ((CAPStackImpl) capProvider.getCAPStack()).stop();
            }
            
            // Stop TCAP
            if (tcapStack != null) {
                tcapStack.stop();
            }
            
            // Stop SCCP
            if (sccpStack != null) {
                sccpStack.stop();
            }
            
            // Stop M3UA
            if (m3uaManagement != null) {
                m3uaManagement.stop();
            }
            
            logger.info("SS7 Stack stopped successfully");
            
        } catch (Exception e) {
            logger.error("Error stopping SS7 Stack", e);
        }
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        stop();
        initialized = false;
        
        // Clear references
        mapProvider = null;
        capProvider = null;
        tcapStack = null;
        sccpStack = null;
        m3uaManagement = null;
    }
    
    private void initializeM3UA() throws Exception {
        logger.info("Initializing M3UA layer");
        
        m3uaManagement = new M3UAManagementImpl(stackName, null);
        m3uaManagement.setPersistDir("./ss7/m3ua");
        
        // Configure M3UA parameters
        // Note: Actual transport and ASP/AS configuration would be done here
        // This is simplified for the example
        
        logger.info("M3UA initialized with ASP: {}, AS: {}", 
                   config.getM3uaAspName(), config.getM3uaAsName());
    }
    
    private void initializeSCCP() throws Exception {
        logger.info("Initializing SCCP layer");
        
        sccpStack = new SccpStackImpl(stackName);
        sccpStack.setPersistDir("./ss7/sccp");
        sccpStack.setMtp3UserPart(1, m3uaManagement);
        sccpStack.init();
        
        // Configure SCCP routing and GT translation
        configureSCCPRouting();
        
        logger.info("SCCP initialized with local SPC: {}, remote SPC: {}", 
                   config.getSccpLocalSpc(), config.getSccpRemoteSpc());
    }
    
    private void configureSCCPRouting() {
        // Configure routing for generic GTs to internal GTs
        logger.info("Configuring SCCP routing with {} internal GTs", 
                   config.getInternalGTs().size());
        
        // This would include:
        // - Global Title Translation rules
        // - Routing based on patterns
        // - Load balancing configuration
    }
    
    private void initializeTCAP() throws Exception {
        logger.info("Initializing TCAP layer");
        
        tcapStack = new org.restcomm.protocols.ss7.tcap.TCAPStackImpl(stackName, sccpStack.getSccpProvider(), 
                                                                      config.getSccpLocalSsn());
        tcapStack.setPersistDir("./ss7/tcap");
        tcapStack.setDialogIdleTimeout(config.getTcapDialogTimeout());
        tcapStack.setInvokeTimeout(config.getTcapInvokeTimeout());
        tcapStack.setMaxDialogs(config.getTcapMaxDialogs());
        tcapStack.init();
        
        logger.info("TCAP initialized with SSN: {}, max dialogs: {}", 
                   config.getSccpLocalSsn(), config.getTcapMaxDialogs());
    }
    
    private void initializeMAP() throws Exception {
        logger.info("Initializing MAP layer");
        
        MAPStackImpl mapStack = new MAPStackImpl(stackName, 
                                                 sccpStack.getSccpProvider(), 
                                                 config.getSccpLocalSsn());
        mapStack.setPersistDir("./ss7/map");
        
        TCAPProvider tcapProvider = tcapStack.getProvider();
        mapStack.setTCAPProvider(tcapProvider);
        mapStack.init();
        
        mapProvider = mapStack.getMAPProvider();
        
        // Activate MAP services
        mapProvider.getMAPServiceSms().activate();
        mapProvider.getMAPServiceMobility().activate();
        
        logger.info("MAP initialized and services activated");
    }
    
    private void initializeCAP() throws Exception {
        logger.info("Initializing CAP layer");
        
        CAPStackImpl capStack = new CAPStackImpl(stackName, 
                                                 sccpStack.getSccpProvider(), 
                                                 config.getSccpCapSsn());
        capStack.setPersistDir("./ss7/cap");
        
        TCAPProvider tcapProvider = tcapStack.getProvider();
        capStack.setTCAPProvider(tcapProvider);
        capStack.init();
        
        capProvider = capStack.getCAPProvider();
        
        // Activate CAP services
        capProvider.getCAPServiceCircuitSwitchedCall().activate();
        capProvider.getCAPServiceGprs().activate();
        capProvider.getCAPServiceSms().activate();
        
        logger.info("CAP initialized and services activated");
    }
    
    // Getters
    public MAPProvider getMapProvider() { return mapProvider; }
    public CAPProvider getCapProvider() { return capProvider; }
    public TCAPProvider getTcapProvider() { return tcapStack.getProvider(); }
    public SccpStack getSccpStack() { return sccpStack; }
    public M3UAManagement getM3uaManagement() { return m3uaManagement; }
    public boolean isStarted() { return started; }
    public boolean isInitialized() { return initialized; }
}