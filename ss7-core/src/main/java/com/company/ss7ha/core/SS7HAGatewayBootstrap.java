package com.company.ss7ha.core;

import com.company.ss7ha.core.config.SS7Configuration;
import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.handlers.MtSmsMessageHandler;
import com.company.ss7ha.core.handlers.SriMessageHandler;
import com.company.ss7ha.core.listeners.MapSmsServiceListener;
import com.company.ss7ha.core.manager.SS7StackManager;
import com.company.ss7ha.core.store.DialogStore;
import com.company.ss7ha.core.store.NatsDialogStore;
import com.company.ss7ha.nats.publisher.SS7NatsPublisher;
import com.company.ss7ha.nats.subscriber.SS7NatsSubscriber;
import com.company.ss7ha.manager.api.GatewayRestApi;
import com.company.ss7ha.manager.spi.GatewayStatusProvider;
import org.restcomm.protocols.ss7.map.api.MAPProvider;
import org.restcomm.protocols.ss7.map.api.MAPStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * SS7 HA Gateway Bootstrap (Corsac JSS7 API)
 * Refactored to use SS7StackManager and NatsConnectionManager.
 * Now includes REST API for health checks and metrics.
 */
public class SS7HAGatewayBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(SS7HAGatewayBootstrap.class);

    // HA components
    private EventPublisher eventPublisher;
    private MapSmsServiceListener smsServiceListener;
    private SS7NatsPublisher natsPublisher;
    private SS7NatsSubscriber natsSubscriber;
    private DialogStore dialogStore;
    
    // Management API
    private GatewayRestApi restApi;

    // Configuration
    private final Properties config;

    public SS7HAGatewayBootstrap(Properties config, EventPublisher eventPublisher) {
        this.config = config;
        this.eventPublisher = eventPublisher;
    }

    public void start() throws Exception {
        logger.info("Starting SS7 HA Gateway...");

        try {
            // Initialize SS7 Stack via Manager
            SS7Configuration ss7Config = new SS7Configuration();
            ss7Config.loadFromProperties(config);
            
            logger.info("Initializing SS7 Stack Manager...");
            SS7StackManager.getInstance().initialize(ss7Config);
            SS7StackManager.getInstance().start();

            // Initialize NATS and Listeners
            initializeNats();
            registerMapListeners();
            
            // Start NATS Subscriber (Publisher started in initializeNats)
            if (natsSubscriber != null) {
                natsSubscriber.start();
                logger.info("NATS Subscriber started");
            }
            
            // Start REST API
            startRestApi();

            logger.info("SS7 HA Gateway started successfully");

        } catch (Exception e) {
            logger.error("Failed to start SS7 HA Gateway", e);
            stop(); // Ensure cleanup
            throw e;
        }
    }
    
    private void startRestApi() {
        int apiPort = Integer.parseInt(config.getProperty("api.port", "8080"));
        
        GatewayStatusProvider statusProvider = new GatewayStatusProvider() {
            @Override
            public boolean isHealthy() {
                // Simple health check: Stack must be ready and NATS connected
                return SS7StackManager.getInstance().isReady() && 
                       natsPublisher != null && natsPublisher.isConnected();
            }

            @Override
            public Map<String, Object> getMetrics() {
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("stack.ready", SS7StackManager.getInstance().isReady());
                metrics.put("nats.publisher.stats", natsPublisher != null ? natsPublisher.getStats() : "Not initialized");
                metrics.put("nats.subscriber.stats", natsSubscriber != null ? natsSubscriber.getStats() : "Not initialized");
                
                // Add memory metrics
                Runtime rt = Runtime.getRuntime();
                metrics.put("jvm.memory.total", rt.totalMemory());
                metrics.put("jvm.memory.free", rt.freeMemory());
                metrics.put("jvm.memory.used", rt.totalMemory() - rt.freeMemory());
                
                return metrics;
            }
        };
        
        restApi = new GatewayRestApi(statusProvider);
        restApi.start(apiPort);
    }

    private void registerMapListeners() {
        logger.info("Registering MAP service listeners...");
        try {
            boolean publishEvents = Boolean.parseBoolean(config.getProperty("events.publish.enabled", "true"));
            boolean persistDialogs = Boolean.parseBoolean(config.getProperty("dialog.persist.enabled", "true"));
            
            smsServiceListener = new MapSmsServiceListener(dialogStore, eventPublisher, natsPublisher, persistDialogs, publishEvents);
            
            MAPProvider mapProvider = SS7StackManager.getInstance().getMapProvider();
            if (mapProvider != null) {
                mapProvider.getMAPServiceSms().addMAPServiceListener(smsServiceListener);
                logger.info("MAP service listeners registered (Persistence: {}, Events: {})", persistDialogs, publishEvents);
            } else {
                logger.error("MAP Provider is null, cannot register listeners");
            }
        } catch (Exception e) {
            logger.error("Failed to register MAP listeners", e);
            throw new RuntimeException("MAP listener registration failed", e);
        }
    }

    private void initializeNats() {
        logger.info("Initializing NATS...");
        try {
            String natsUrl = System.getProperty("nats.server.url",
                config.getProperty("nats.server.url", "nats://localhost:4222"));
            String queueGroup = System.getProperty("nats.queue.group",
                config.getProperty("nats.queue.group", "ss7-gateway-group-v2"));

            // Initialize Dialog Store (Uses NatsConnectionManager internally)
            try {
                int ttl = Integer.parseInt(config.getProperty("dialog.ttl.seconds", "600"));
                dialogStore = new NatsDialogStore(natsUrl, ttl);
                logger.info("NATS Dialog Store initialized (TTL: {}s)", ttl);
            } catch (Exception e) {
                logger.error("Failed to initialize NATS Dialog Store (Continuing in stateless mode)", e);
                dialogStore = null;
            }

            // Initialize NATS publisher (Uses NatsConnectionManager internally)
            natsPublisher = new SS7NatsPublisher(natsUrl);
            natsPublisher.start();
            logger.info("NATS Publisher initialized");

            // Initialize NATS subscriber (Uses NatsConnectionManager internally)
            natsSubscriber = new SS7NatsSubscriber(natsUrl, queueGroup);

            // Register handlers
            // Using SS7StackManager to get MAPStack
            MAPStack mapStack = SS7StackManager.getInstance().getStack().getMapStack();
            
            MtSmsMessageHandler mtSmsHandler = new MtSmsMessageHandler(mapStack, eventPublisher);
            natsSubscriber.setMtSmsHandler(mtSmsHandler);

            SriMessageHandler sriHandler = new SriMessageHandler(mapStack, eventPublisher);
            natsSubscriber.setSriHandler(sriHandler);

            logger.info("NATS Subscriber initialized with handlers");
        } catch (Exception e) {
            logger.error("Failed to initialize NATS", e);
            throw new RuntimeException("NATS initialization failed", e);
        }
    }

    public void stop() {
        logger.info("Stopping SS7 HA Gateway...");
        try {
            if (restApi != null) restApi.stop();
            if (natsSubscriber != null) natsSubscriber.stop();
            if (natsPublisher != null) natsPublisher.stop();
            if (dialogStore != null) dialogStore.close();
            if (eventPublisher != null) eventPublisher.close();
            
            // Stop SS7 Stack via Manager
            SS7StackManager.getInstance().stop();
            
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
        EventPublisher eventPublisher = null; // Placeholder
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
