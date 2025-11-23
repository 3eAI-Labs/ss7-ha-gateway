package com.company.ss7ha.core.stack;

import com.company.ss7ha.core.config.SS7Configuration;
import com.mobius.software.common.dal.timers.WorkerPool;
import com.mobius.software.telco.protocols.ss7.common.UUIDGenerator;
import org.restcomm.protocols.ss7.m3ua.M3UAManagement;
import org.restcomm.protocols.ss7.m3ua.impl.M3UAManagementImpl;
import org.restcomm.protocols.ss7.sccp.SccpProvider;
import org.restcomm.protocols.ss7.sccp.SccpStack;
import org.restcomm.protocols.ss7.sccp.impl.SccpStackImpl;
import org.restcomm.protocols.ss7.tcap.api.TCAPProvider;
import org.restcomm.protocols.ss7.tcap.api.TCAPStack;
import org.restcomm.protocols.ss7.map.MAPStackImpl;
import org.restcomm.protocols.ss7.map.api.MAPProvider;
import org.restcomm.protocols.ss7.cap.CAPStackImpl;
import org.restcomm.protocols.ss7.cap.api.CAPProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core SS7 Stack management with MAP and CAP support (Corsac JSS7 API)
 */
public class SS7Stack {
    private static final Logger logger = LoggerFactory.getLogger(SS7Stack.class);

    private final SS7Configuration config;
    private final String stackName;
    private final WorkerPool workerPool;
    private final UUIDGenerator uuidGenerator;

    // SS7 Stack components
    private M3UAManagement m3uaManagement;
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

            // Initialize M3UA
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
            // Start M3UA
            m3uaManagement.start();
            logger.info("M3UA started");

            // Start SCCP
            sccpStack.start();
            logger.info("SCCP started");

            // Start MAP if enabled (MAP starts TCAP internally)
            if (mapStack != null) {
                mapStack.start();
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
    }

    private void initializeM3UA() throws Exception {
        logger.info("Initializing M3UA layer");

        // Corsac API: M3UAManagementImpl(name, persistDir, uuidGenerator, workerPool)
        m3uaManagement = new M3UAManagementImpl(stackName, null, uuidGenerator, workerPool);

        // Configure M3UA (simplified - production needs full config)
        // - Add SCTP associations
        // - Add application servers
        // - Add routes
        // See Corsac JSS7 documentation for details

        logger.info("M3UA initialized with ASP: {}, AS: {}",
                   config.getM3uaAspName(), config.getM3uaAsName());
    }

    private void initializeSCCP() throws Exception {
        logger.info("Initializing SCCP layer");

        // Corsac API: SccpStackImpl(name, isStp, workerPool)
        sccpStack = new SccpStackImpl(stackName, false, workerPool);

        // In Corsac, M3UA is set through management, not directly
        // The SccpStack manages the MTP3 layer internally

        // Configure SCCP (simplified - production needs full config)
        // - Set local address
        // - Add remote addresses
        // - Configure Global Title Translation
        // - Add routing rules

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

    public CAPProvider getCapProvider() {
        return capStack != null ? capStack.getProvider() : null;
    }

    public TCAPProvider getTcapProvider() {
        return mapStack != null ? mapStack.getTCAPStack().getProvider() : null;
    }

    public SccpStack getSccpStack() { return sccpStack; }
    public M3UAManagement getM3uaManagement() { return m3uaManagement; }
    public boolean isStarted() { return started; }
    public boolean isInitialized() { return initialized; }
}
