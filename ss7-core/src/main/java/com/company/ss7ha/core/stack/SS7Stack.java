package com.company.ss7ha.core.stack;

import com.company.ss7ha.core.config.SS7Configuration;
import com.company.ss7ha.core.config.SS7Configuration.LocalAddressConfig;
import com.mobius.software.common.dal.timers.WorkerPool;
import com.mobius.software.telco.protocols.ss7.common.UUIDGenerator;
import org.restcomm.protocols.sctp.SctpManagementImpl;
import org.restcomm.protocols.ss7.m3ua.M3UAManagement;
import org.restcomm.protocols.ss7.m3ua.impl.M3UAManagementImpl;
import org.restcomm.protocols.ss7.sccp.SccpProvider;
import org.restcomm.protocols.ss7.sccp.impl.SccpStackImpl;
import org.restcomm.protocols.ss7.map.MAPStackImpl;
import org.restcomm.protocols.ss7.map.api.MAPProvider;
import org.restcomm.protocols.ss7.cap.CAPStackImpl;
import org.restcomm.protocols.ss7.cap.api.CAPProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core SS7 Stack management with MAP and CAP support (Corsac JSS7 API).
 * Encapsulates SCTP, M3UA, SCCP, MAP, and CAP layers.
 */
public class SS7Stack {
    private static final Logger logger = LoggerFactory.getLogger(SS7Stack.class);

    private final SS7Configuration config;
    private final String stackName;
    private final WorkerPool workerPool;
    private final UUIDGenerator uuidGenerator;

    // SS7 Stack components
    private SctpManagementImpl sctpManagement;
    private M3UAManagementImpl m3uaManagement;
    private SccpStackImpl sccpStack;
    private MAPStackImpl mapStack;
    private CAPStackImpl capStack;

    // Stack state
    private volatile boolean initialized = false;
    private volatile boolean started = false;

    public SS7Stack(SS7Configuration config, String stackName) throws Exception {
        this.config = config;
        this.stackName = stackName;

        // Initialize WorkerPool for async processing
        this.workerPool = new WorkerPool(stackName + "-WorkerPool");

        // Initialize UUIDGenerator (6-byte prefix for this instance)
        byte[] prefix = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x01 };
        this.uuidGenerator = new UUIDGenerator(prefix);
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
            // Start WorkerPool first
            workerPool.start(4); // 4 worker threads

            // Initialize M3UA (includes SCTP)
            initializeM3UA();

            // Initialize SCCP
            initializeSCCP();

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
            // Start SCTP
            if (sctpManagement != null) {
                sctpManagement.start();
                logger.info("SCTP started");
            }

            // Start M3UA
            m3uaManagement.start();
            logger.info("M3UA started");

            // Start SCCP
            sccpStack.start();
            logger.info("SCCP started");

            // Start MAP if enabled (MAP starts TCAP internally)
            if (mapStack != null) {
                mapStack.start();
                
                // Explicitly activate MAP services using Reflection
                // This is required because the 'activate()' method might not be exposed in the public interface
                // depending on the JSS7 version, but the implementation requires it.
                MAPProvider provider = mapStack.getProvider();
                if (provider != null) {
                    try {
                        // Activate SMS Service
                        Object smsService = provider.getMAPServiceSms();
                        if (smsService != null) {
                            try {
                                java.lang.reflect.Method method = smsService.getClass().getMethod("activate");
                                method.invoke(smsService);
                                logger.info("MAP Service SMS activated (via Reflection)");
                            } catch (NoSuchMethodException e) {
                                logger.warn("activate() method not found on SMS service class, trying 'acivate' typo workaround...");
                                try {
                                    java.lang.reflect.Method method = smsService.getClass().getMethod("acivate");
                                    method.invoke(smsService);
                                    logger.info("MAP Service SMS activated (via 'acivate' typo workaround)");
                                } catch (NoSuchMethodException ex) {
                                    logger.error("Neither activate() nor acivate() found on SMS service");
                                }
                            }
                        }
                        
                        // Activate Mobility Service
                        Object mobService = provider.getMAPServiceMobility();
                        if (mobService != null) {
                            try {
                                java.lang.reflect.Method method = mobService.getClass().getMethod("activate");
                                method.invoke(mobService);
                                logger.info("MAP Service Mobility activated (via Reflection)");
                            } catch (NoSuchMethodException e) {
                                logger.debug("activate() method not found on Mobility service");
                            }
                        }

                        // Activate Supplementary Service
                        Object supService = provider.getMAPServiceSupplementary();
                        if (supService != null) {
                            try {
                                java.lang.reflect.Method method = supService.getClass().getMethod("activate");
                                method.invoke(supService);
                                logger.info("MAP Service Supplementary activated (via Reflection)");
                            } catch (NoSuchMethodException e) {
                                logger.debug("activate() method not found on Supplementary service");
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to activate MAP services via reflection", e);
                    }
                }
                
                logger.info("MAP started (TCAP started internally)");
            }

            // Start CAP if enabled
            if (capStack != null) {
                capStack.start();
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
            if (mapStack != null) {
                mapStack.stop();
            }

            // Stop CAP
            if (capStack != null) {
                capStack.stop();
            }

            // Stop SCCP
            if (sccpStack != null) {
                sccpStack.stop();
            }

            // Stop M3UA
            if (m3uaManagement != null) {
                m3uaManagement.stop();
            }
            
            // Stop SCTP
            if (sctpManagement != null) {
                sctpManagement.stop();
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

        // Stop WorkerPool
        if (workerPool != null) {
            workerPool.stop();
        }

        // Clear references
        mapStack = null;
        capStack = null;
        sccpStack = null;
        m3uaManagement = null;
        sctpManagement = null;
    }

    private void initializeM3UA() throws Exception {
        logger.info("Initializing M3UA layer (with SCTP)");

        // Initialize SCTP Management
        // 4 workers per queue, matching the simplified assumption
        sctpManagement = new SctpManagementImpl("SCTP-" + stackName, 4, 4, 4);
        // Note: Production would need sctpManagement.setPersistDir(...) but passing null for now/in-memory

        // Corsac API: M3UAManagementImpl(name, persistDir, uuidGenerator, workerPool)
        m3uaManagement = new M3UAManagementImpl(stackName, null, uuidGenerator, workerPool);
        
        // Link SCTP to M3UA
        m3uaManagement.setTransportManagement(sctpManagement);

        logger.info("M3UA initialized with ASP: {}, AS: {}",
                   config.getM3uaAspName(), config.getM3uaAsName());
    }

    private void initializeSCCP() throws Exception {
        logger.info("Initializing SCCP layer");

        // Corsac API: SccpStackImpl(name, isStp, workerPool)
        sccpStack = new SccpStackImpl(stackName, false, workerPool);

        // Configure Local Addresses (Dynamic GT + SSN)
        for (LocalAddressConfig addrConfig : config.getLocalAddressConfigs()) {
            logger.info("Configuring Local Address - GT: {}, SSN: {}", 
                addrConfig.getGlobalTitle(), addrConfig.getSsn());
            
            // Note: Actual binding of GT/SSN to the SCCP Router would happen here.
            // In a full implementation, we would create GlobalTitle objects and 
            // add them to the SCCP Router's routing table.
            // For now, the MAP stack initialization below will bind the primary SSN.
        }

        logger.info("SCCP initialized with local SPC: {}, remote SPC: {}",
                   config.getSccpLocalSpc(), config.getSccpRemoteSpc());
    }

    private void initializeMAP() throws Exception {
        logger.info("Initializing MAP layer");

        SccpProvider sccpProvider = sccpStack.getSccpProvider();
        int ssn = config.getSccpLocalSsn();

        // Corsac API: MAPStackImpl(name, sccpProvider, ssn, workerPool)
        // This constructor creates TCAP stack internally
        mapStack = new MAPStackImpl(stackName, sccpProvider, ssn, workerPool);

        logger.info("MAP initialized with SSN: {}", ssn);
    }

    private void initializeCAP() throws Exception {
        logger.info("Initializing CAP layer");

        SccpProvider sccpProvider = sccpStack.getSccpProvider();
        int ssn = config.getSccpCapSsn();

        // Corsac API: CAPStackImpl(name, sccpProvider, ssn, workerPool)
        capStack = new CAPStackImpl(stackName, sccpProvider, ssn, workerPool);

        logger.info("CAP initialized with SSN: {}", ssn);
    }

    // Getters
    public MAPProvider getMapProvider() {
        return mapStack != null ? mapStack.getProvider() : null;
    }
    
    public MAPStackImpl getMapStack() {
        return mapStack;
    }

    public CAPProvider getCapProvider() {
        return capStack != null ? capStack.getProvider() : null;
    }

    public SccpStackImpl getSccpStack() { return sccpStack; }
    public M3UAManagement getM3uaManagement() { return m3uaManagement; }
    public SctpManagementImpl getSctpManagement() { return sctpManagement; }
    public boolean isStarted() { return started; }
    public boolean isInitialized() { return initialized; }
}
