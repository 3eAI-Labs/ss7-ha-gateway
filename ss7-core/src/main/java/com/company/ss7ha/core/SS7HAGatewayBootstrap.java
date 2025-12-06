package com.company.ss7ha.core;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.handlers.MtSmsMessageHandler;
import com.company.ss7ha.core.handlers.SriMessageHandler;
import com.company.ss7ha.core.listeners.MapSmsServiceListener;
import com.company.ss7ha.core.store.DialogStore;
import com.company.ss7ha.core.store.NatsDialogStore;
import com.company.ss7ha.nats.publisher.SS7NatsPublisher;
import com.company.ss7ha.nats.subscriber.SS7NatsSubscriber;
import com.company.ss7ha.messages.MapSmsMessage;
import com.company.ss7ha.messages.MapSmsMessage.SmsType;
import java.time.Instant;
import com.mobius.software.common.dal.timers.WorkerPool;
import com.mobius.software.telco.protocols.ss7.common.UUIDGenerator;
import org.restcomm.protocols.sctp.SctpManagementImpl;
import org.restcomm.protocols.ss7.m3ua.M3UAManagement;
import org.restcomm.protocols.ss7.m3ua.impl.M3UAManagementImpl;
import org.restcomm.protocols.ss7.map.api.MAPProvider;
import org.restcomm.protocols.ss7.map.api.MAPStack;
import org.restcomm.protocols.ss7.map.MAPStackImpl;
import org.restcomm.protocols.ss7.sccp.SccpProvider;
import org.restcomm.protocols.ss7.sccp.SccpStack;
import org.restcomm.protocols.ss7.sccp.impl.SccpStackImpl;
import org.restcomm.protocols.ss7.tcap.api.TCAPProvider;
import org.restcomm.protocols.ss7.tcap.api.TCAPStack;
import org.restcomm.protocols.ss7.tcap.TCAPStackImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Properties;

/**
 * SS7 HA Gateway Bootstrap (Corsac JSS7 API)
 * ...
 */
public class SS7HAGatewayBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(SS7HAGatewayBootstrap.class);

    // SS7 Stack components
    private WorkerPool workerPool;
    private UUIDGenerator uuidGenerator;
    private SctpManagementImpl sctpManagement;
    private M3UAManagementImpl m3uaManagement;
    private SccpStack sccpStack;
    private TCAPStack tcapStack;
    private MAPStackImpl mapStack;

    // HA components
    private EventPublisher eventPublisher;
    private MapSmsServiceListener smsServiceListener;
    private SS7NatsPublisher natsPublisher;
    private SS7NatsSubscriber natsSubscriber;
    private DialogStore dialogStore;

    // Configuration
    private final Properties config;

    public SS7HAGatewayBootstrap(Properties config, EventPublisher eventPublisher) {
        this.config = config;
        this.eventPublisher = eventPublisher;
    }

    public void start() throws Exception {
        logger.info("Starting SS7 HA Gateway...");

        try {
            initializeWorkerPool();
            initializeM3UA();
            initializeSCCP();
            initializeMAP();
            initializeNats(); // Initialize NATS (and Store) BEFORE listeners
            registerMapListeners();
            startComponents();

            logger.info("SS7 HA Gateway started successfully");

        } catch (Exception e) {
            logger.error("Failed to start SS7 HA Gateway", e);
            throw e;
        }
    }

    private void initializeWorkerPool() throws Exception {
        logger.info("Initializing WorkerPool...");
        String stackName = config.getProperty("stack.name", "SS7-HA-Gateway");
        workerPool = new WorkerPool(stackName + "-WorkerPool");
        workerPool.start(4);
        byte[] prefix = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x01 };
        uuidGenerator = new UUIDGenerator(prefix);
        logger.info("WorkerPool initialized with 4 threads");
    }

    private void initializeM3UA() {
        logger.info("Initializing M3UA layer...");
        try {
            String m3uaName = config.getProperty("m3ua.name", "M3UA-STACK");
            // Use the 4-arg constructor found in GatewayTest
            sctpManagement = new SctpManagementImpl("SCTP-" + m3uaName, 4, 4, 4);
            
            // Corsac API: M3UAManagementImpl(name, persistDir, uuidGenerator, workerPool)
            m3uaManagement = new M3UAManagementImpl(m3uaName, null, uuidGenerator, workerPool);
            m3uaManagement.setTransportManagement(sctpManagement);
            logger.info("M3UA layer initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize M3UA", e);
            throw new RuntimeException("M3UA initialization failed", e);
        }
    }

    private void initializeSCCP() {
        logger.info("Initializing SCCP layer...");
        try {
            String sccpName = config.getProperty("sccp.name", "SCCP-STACK");
            sccpStack = new SccpStackImpl(sccpName, false, workerPool);
            logger.info("SCCP layer initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize SCCP", e);
            throw new RuntimeException("SCCP initialization failed", e);
        }
    }

    private void initializeMAP() {
        logger.info("Initializing MAP layer...");
        try {
            String mapName = config.getProperty("map.name", "MAP-STACK");
            int ssn = Integer.parseInt(config.getProperty("sccp.local.ssn", "8"));
            SccpProvider sccpProvider = sccpStack.getSccpProvider();
            mapStack = new MAPStackImpl(mapName, sccpProvider, ssn, workerPool);
            tcapStack = mapStack.getTCAPStack();
            logger.info("MAP layer initialized with SSN: {}", ssn);
        } catch (Exception e) {
            logger.error("Failed to initialize MAP", e);
            throw new RuntimeException("MAP initialization failed", e);
        }
    }

    private void registerMapListeners() {
        logger.info("Registering MAP service listeners...");
        try {
            // NATS-based architecture with distributed state
            boolean publishEvents = Boolean.parseBoolean(config.getProperty("events.publish.enabled", "true"));
            boolean persistDialogs = Boolean.parseBoolean(config.getProperty("dialog.persist.enabled", "true"));
            
            // Pass the initialized dialogStore here
            smsServiceListener = new MapSmsServiceListener(dialogStore, eventPublisher, natsPublisher, persistDialogs, publishEvents);
            
            MAPProvider mapProvider = mapStack.getProvider();
            mapProvider.getMAPServiceSms().addMAPServiceListener(smsServiceListener);
            logger.info("MAP service listeners registered (Persistence: {}, Events: {})", persistDialogs, publishEvents);
        } catch (Exception e) {
            logger.error("Failed to register MAP listeners", e);
            throw new RuntimeException("MAP listener registration failed", e);
        }
    }

    private void initializeNats() {
        logger.info("Initializing NATS...");
        try {
            // Get NATS configuration from system properties or config file
            String natsUrl = System.getProperty("nats.server.url",
                config.getProperty("nats.server.url", "nats://localhost:4222"));
            String queueGroup = System.getProperty("nats.queue.group",
                config.getProperty("nats.queue.group", "ss7-gateway-group-v2"));

            // Initialize Dialog Store (KV)
            try {
                int ttl = Integer.parseInt(config.getProperty("dialog.ttl.seconds", "600"));
                dialogStore = new NatsDialogStore(natsUrl, ttl);
                logger.info("NATS Dialog Store initialized (TTL: {}s)", ttl);
            } catch (Exception e) {
                logger.error("Failed to initialize NATS Dialog Store (Continuing in stateless mode)", e);
                dialogStore = null;
            }

            // Initialize NATS publisher first
            natsPublisher = new SS7NatsPublisher(natsUrl);
            natsPublisher.start();
            logger.info("NATS Publisher initialized and connected");

            // Initialize NATS subscriber with loopback handler
            natsSubscriber = new SS7NatsSubscriber(natsUrl, queueGroup);

            // Register MT SMS handler with actual MAP sending logic
            MtSmsMessageHandler mtSmsHandler = new MtSmsMessageHandler(mapStack, eventPublisher);
            natsSubscriber.setMtSmsHandler(mtSmsHandler);

            // Register SRI handler with actual MAP sending logic
            SriMessageHandler sriHandler = new SriMessageHandler(mapStack, eventPublisher);
            natsSubscriber.setSriHandler(sriHandler);

            logger.info("NATS Subscriber initialized with loopback handler");
        } catch (Exception e) {
            logger.error("Failed to initialize NATS", e);
            throw new RuntimeException("NATS initialization failed", e);
        }
    }

    private void startComponents() throws Exception {
        logger.info("Starting SS7 stack components...");
        try {
            if (sctpManagement != null) {
                sctpManagement.start();
                logger.info("SCTP started");
            }
            m3uaManagement.start();
            logger.info("M3UA started");
            sccpStack.start();
            logger.info("SCCP started");
            mapStack.start();
            logger.info("MAP started (TCAP started internally)");

            if (natsSubscriber != null) {
                natsSubscriber.start();
                logger.info("NATS Subscriber started");
            }

            logger.info("All SS7 stack components started successfully");
        } catch (Exception e) {
            logger.error("Failed to start SS7 components", e);
            throw e;
        }
    }

    public void stop() {
        logger.info("Stopping SS7 HA Gateway...");
        try {
            if (natsSubscriber != null) natsSubscriber.stop();
            if (natsPublisher != null) natsPublisher.stop();
            if (dialogStore != null) dialogStore.close();
            if (mapStack != null) mapStack.stop();
            if (sccpStack != null) sccpStack.stop();
            if (m3uaManagement != null) m3uaManagement.stop();
            if (sctpManagement != null) sctpManagement.stop();
            if (workerPool != null) workerPool.stop();
            if (eventPublisher != null) eventPublisher.close();
            logger.info("SS7 HA Gateway stopped successfully");
        } catch (Exception e) {
            logger.error("Error stopping SS7 HA Gateway", e);
        }
    }

    public static void main(String[] args) {
        logger.info("SS7 HA Gateway starting...");
        Properties config = new Properties();
        try {
            if (args.length > 0) {
                config.load(new java.io.FileInputStream(args[0]));
            } else {
                logger.warn("No configuration file provided, using defaults");
            }
        } catch (Exception e) {
            logger.error("Failed to load configuration", e);
            System.exit(1);
        }
        EventPublisher eventPublisher = null; 
        SS7HAGatewayBootstrap gateway = new SS7HAGatewayBootstrap(config, eventPublisher);
        try {
            gateway.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered");
                gateway.stop();
            }));
            logger.info("SS7 HA Gateway is running. Press Ctrl+C to stop.");
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Failed to start gateway", e);
            System.exit(1);
        }
    }
}