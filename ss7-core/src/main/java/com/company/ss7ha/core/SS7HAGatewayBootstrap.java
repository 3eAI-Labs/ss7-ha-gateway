package com.company.ss7ha.core;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.listeners.MapSmsServiceListener;
import com.company.ss7ha.core.redis.RedisDialogStore;
import com.company.ss7ha.core.redis.RedisDialogStoreImpl;
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
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.HostAndPort;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * SS7 HA Gateway Bootstrap (Corsac JSS7 API)
 *
 * This class demonstrates how to wire together:
 * - Redis dialog persistence
 * - Event publishing (Kafka, gRPC, etc.)
 * - MAP service listeners
 * - SS7 stack (M3UA, SCCP, TCAP, MAP)
 *
 * ARCHITECTURE:
 * 1. SS7 messages arrive via M3UA/SCCP/TCAP
 * 2. MAP listener receives MO-ForwardSM
 * 3. Dialog state saved to Redis (primitive types)
 * 4. Message converted to JSON
 * 5. JSON published via EventPublisher
 * 6. Consumer (SMSC Gateway) processes JSON
 *
 * LICENSE: AGPL-3.0 (part of ss7-ha-gateway)
 *
 * @author SS7-HA-Gateway Team
 * @since 1.0.0
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
    private RedisDialogStore dialogStore;
    private EventPublisher eventPublisher;
    private MapSmsServiceListener smsServiceListener;

    // Configuration
    private final Properties config;

    /**
     * Constructor with configuration and event publisher.
     *
     * @param config Configuration properties
     * @param eventPublisher Event publisher implementation (Kafka, gRPC, etc.)
     */
    public SS7HAGatewayBootstrap(Properties config, EventPublisher eventPublisher) {
        this.config = config;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Initialize and start the gateway.
     */
    public void start() throws Exception {
        logger.info("Starting SS7 HA Gateway...");

        try {
            // 1. Initialize WorkerPool and UUIDGenerator
            initializeWorkerPool();

            // 2. Initialize Redis dialog store
            initializeRedis();

            // 3. Initialize SS7 stack
            initializeM3UA();
            initializeSCCP();
            initializeMAP();

            // 4. Register MAP listeners
            registerMapListeners();

            // 5. Start all components
            startComponents();

            logger.info("SS7 HA Gateway started successfully");

        } catch (Exception e) {
            logger.error("Failed to start SS7 HA Gateway", e);
            throw e;
        }
    }

    /**
     * Initialize WorkerPool and UUIDGenerator.
     */
    private void initializeWorkerPool() throws Exception {
        logger.info("Initializing WorkerPool...");

        String stackName = config.getProperty("stack.name", "SS7-HA-Gateway");
        workerPool = new WorkerPool(stackName + "-WorkerPool");
        workerPool.start(4); // 4 worker threads

        // Initialize UUIDGenerator (6-byte prefix)
        byte[] prefix = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x01 };
        uuidGenerator = new UUIDGenerator(prefix);

        logger.info("WorkerPool initialized with 4 threads");
    }

    /**
     * Initialize Redis dialog store.
     */
    private void initializeRedis() {
        logger.info("Initializing Redis dialog store...");

        try {
            // Parse Redis nodes from config (check System property first)
            String redisNodes = System.getProperty("redis.cluster.nodes", 
                config.getProperty("redis.cluster.nodes", "localhost:6379"));
            
            String ttlStr = System.getProperty("redis.dialog.ttl", 
                config.getProperty("redis.dialog.ttl", "3600"));
            int dialogTTL = Integer.parseInt(ttlStr);

            String[] nodes = redisNodes.split(",");
            UnifiedJedis jedisClient;

            if (nodes.length == 1) {
                // Single node -> Use JedisPooled (Standalone)
                String[] parts = nodes[0].trim().split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 6379;
                
                logger.info("Initializing Redis in Standalone mode: {}:{}", host, port);
                jedisClient = new JedisPooled(host, port);
            } else {
                // Multiple nodes -> Use JedisCluster
                Set<HostAndPort> clusterNodes = new HashSet<>();
                for (String node : nodes) {
                    String[] parts = node.trim().split(":");
                    String host = parts[0];
                    int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 6379;
                    clusterNodes.add(new HostAndPort(host, port));
                }
                
                logger.info("Initializing Redis in Cluster mode with {} nodes", clusterNodes.size());
                jedisClient = new JedisCluster(clusterNodes);
            }

            // Create dialog store
            dialogStore = new RedisDialogStoreImpl(jedisClient, dialogTTL);

            // Health check
            if (dialogStore.isHealthy()) {
                logger.info("Redis dialog store initialized successfully");
            } else {
                logger.warn("Redis dialog store initialized but health check failed");
            }

        } catch (Exception e) {
            logger.error("Failed to initialize Redis", e);
            throw new RuntimeException("Redis initialization failed", e);
        }
    }

    /**
     * Initialize M3UA layer.
     */
    private void initializeM3UA() {
        logger.info("Initializing M3UA layer...");

        try {
            String m3uaName = config.getProperty("m3ua.name", "M3UA-STACK");

            // Use the 4-arg constructor found in GatewayTest
            sctpManagement = new SctpManagementImpl("SCTP-" + m3uaName, 4, 4, 4);
            // sctpManagement.setSingleThread(true); // Removed
            // sctpManagement.setConnectDelay(10000); // Removed
            
            // Corsac API: M3UAManagementImpl(name, persistDir, uuidGenerator, workerPool)
            m3uaManagement = new M3UAManagementImpl(m3uaName, null, uuidGenerator, workerPool);
            m3uaManagement.setTransportManagement(sctpManagement);

            // Configure M3UA (simplified - production needs full config)
            // - Add SCTP associations
            // - Add application servers
            // - Add routes
            // See Corsac JSS7 documentation for details

            logger.info("M3UA layer initialized");

        } catch (Exception e) {
            logger.error("Failed to initialize M3UA", e);
            throw new RuntimeException("M3UA initialization failed", e);
        }
    }

    /**
     * Initialize SCCP layer.
     */
    private void initializeSCCP() {
        logger.info("Initializing SCCP layer...");

        try {
            String sccpName = config.getProperty("sccp.name", "SCCP-STACK");

            // Corsac API: SccpStackImpl(name, isStp, workerPool)
            sccpStack = new SccpStackImpl(sccpName, false, workerPool);

            // In Corsac, M3UA is set through management, not directly

            // Configure SCCP (simplified - production needs full config)
            // - Set local address
            // - Add remote addresses
            // - Configure Global Title Translation
            // - Add routing rules

            logger.info("SCCP layer initialized");

        } catch (Exception e) {
            logger.error("Failed to initialize SCCP", e);
            throw new RuntimeException("SCCP initialization failed", e);
        }
    }

    /**
     * Initialize MAP layer (TCAP is created internally).
     */
    private void initializeMAP() {
        logger.info("Initializing MAP layer...");

        try {
            String mapName = config.getProperty("map.name", "MAP-STACK");
            int ssn = Integer.parseInt(config.getProperty("sccp.local.ssn", "8"));

            // Corsac API: MAPStackImpl(name, sccpProvider, ssn, workerPool)
            // This creates TCAP stack internally
            SccpProvider sccpProvider = sccpStack.getSccpProvider();
            mapStack = new MAPStackImpl(mapName, sccpProvider, ssn, workerPool);

            // TCAP configuration done through MAPStack
            tcapStack = mapStack.getTCAPStack();

            logger.info("MAP layer initialized with SSN: {}", ssn);

        } catch (Exception e) {
            logger.error("Failed to initialize MAP", e);
            throw new RuntimeException("MAP initialization failed", e);
        }
    }

    /**
     * Register MAP service listeners.
     */
    private void registerMapListeners() {
        logger.info("Registering MAP service listeners...");

        try {
            // Configuration
            boolean persistDialogs = Boolean.parseBoolean(
                    config.getProperty("redis.persist.dialogs", "true"));
            boolean publishEvents = Boolean.parseBoolean(
                    config.getProperty("events.publish.enabled", "true"));
            int dialogTTL = Integer.parseInt(
                    config.getProperty("redis.dialog.ttl", "3600"));

            // Create SMS service listener with Redis + Event Publisher
            smsServiceListener = new MapSmsServiceListener(
                    dialogStore,
                    eventPublisher,
                    persistDialogs,
                    publishEvents,
                    dialogTTL
            );

            // Register with MAP stack
            MAPProvider mapProvider = mapStack.getProvider();
            mapProvider.getMAPServiceSms().addMAPServiceListener(smsServiceListener);

            logger.info("MAP service listeners registered");

        } catch (Exception e) {
            logger.error("Failed to register MAP listeners", e);
            throw new RuntimeException("MAP listener registration failed", e);
        }
    }

    /**
     * Start all components.
     */
    private void startComponents() throws Exception {
        logger.info("Starting SS7 stack components...");

        try {
            // Start in correct order (bottom-up)
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

            logger.info("All SS7 stack components started successfully");

        } catch (Exception e) {
            logger.error("Failed to start SS7 components", e);
            throw e;
        }
    }

    /**
     * Stop the gateway.
     */
    public void stop() {
        logger.info("Stopping SS7 HA Gateway...");

        try {
            // Stop in reverse order (top-down)
            if (mapStack != null) {
                mapStack.stop();
                logger.info("MAP stopped");
            }

            if (sccpStack != null) {
                sccpStack.stop();
                logger.info("SCCP stopped");
            }

            if (m3uaManagement != null) {
                m3uaManagement.stop();
                logger.info("M3UA stopped");
            }

            if (sctpManagement != null) {
                sctpManagement.stop();
                logger.info("SCTP stopped");
            }

            // Stop WorkerPool
            if (workerPool != null) {
                workerPool.stop();
                logger.info("WorkerPool stopped");
            }

            // Close Event Publisher
            if (eventPublisher != null) {
                eventPublisher.close();
                logger.info("Event publisher closed");
            }

            // Redis connection (Jedis) will be closed by GC

            logger.info("SS7 HA Gateway stopped successfully");

        } catch (Exception e) {
            logger.error("Error stopping SS7 HA Gateway", e);
        }
    }

    /**
     * Main method for standalone execution.
     */
    public static void main(String[] args) {
        logger.info("SS7 HA Gateway starting...");

        // Load configuration
        Properties config = new Properties();
        try {
            if (args.length > 0) {
                // Load from file
                config.load(new java.io.FileInputStream(args[0]));
            } else {
                // Use defaults
                logger.warn("No configuration file provided, using defaults");
            }
        } catch (Exception e) {
            logger.error("Failed to load configuration", e);
            System.exit(1);
        }

        // Note: In production, create appropriate EventPublisher implementation
        // For example: new KafkaEventPublisher(kafkaProducer)
        EventPublisher eventPublisher = null;  // TODO: Create actual implementation

        // Create and start gateway
        SS7HAGatewayBootstrap gateway = new SS7HAGatewayBootstrap(config, eventPublisher);

        try {
            gateway.start();

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered");
                gateway.stop();
            }));

            // Keep running
            logger.info("SS7 HA Gateway is running. Press Ctrl+C to stop.");
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Failed to start gateway", e);
            System.exit(1);
        }
    }
}
