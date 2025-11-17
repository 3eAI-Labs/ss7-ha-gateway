package com.company.ss7ha.core;

import com.company.ss7ha.core.listeners.MapSmsServiceListener;
import com.company.ss7ha.core.redis.RedisDialogStore;
import com.company.ss7ha.core.redis.RedisDialogStoreImpl;
import com.company.ss7ha.kafka.producer.SS7KafkaProducer;
import com.mobius.software.telco.protocols.ss7.m3ua.api.M3UAManagement;
import com.mobius.software.telco.protocols.ss7.m3ua.impl.M3UAManagementImpl;
import com.mobius.software.telco.protocols.ss7.map.api.MAPProvider;
import com.mobius.software.telco.protocols.ss7.map.api.MAPStack;
import com.mobius.software.telco.protocols.ss7.map.impl.MAPStackImpl;
import com.mobius.software.telco.protocols.ss7.sccp.api.SccpStack;
import com.mobius.software.telco.protocols.ss7.sccp.impl.SccpStackImpl;
import com.mobius.software.telco.protocols.ss7.tcap.api.TCAPStack;
import com.mobius.software.telco.protocols.ss7.tcap.impl.TCAPStackImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.HostAndPort;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * SS7 HA Gateway Bootstrap
 *
 * This class demonstrates how to wire together:
 * - Redis dialog persistence
 * - Kafka message publishing
 * - MAP service listeners
 * - SS7 stack (M3UA, SCCP, TCAP, MAP)
 *
 * ARCHITECTURE:
 * 1. SS7 messages arrive via M3UA/SCCP/TCAP
 * 2. MAP listener receives MO-ForwardSM
 * 3. Dialog state saved to Redis (primitive types)
 * 4. Message converted to JSON
 * 5. JSON published to Kafka
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
    private M3UAManagement m3uaManagement;
    private SccpStack sccpStack;
    private TCAPStack tcapStack;
    private MAPStack mapStack;

    // HA components
    private RedisDialogStore dialogStore;
    private SS7KafkaProducer kafkaProducer;
    private MapSmsServiceListener smsServiceListener;

    // Configuration
    private final Properties config;

    /**
     * Constructor with configuration.
     *
     * @param config Configuration properties
     */
    public SS7HAGatewayBootstrap(Properties config) {
        this.config = config;
    }

    /**
     * Initialize and start the gateway.
     */
    public void start() throws Exception {
        logger.info("Starting SS7 HA Gateway...");

        try {
            // 1. Initialize Redis dialog store
            initializeRedis();

            // 2. Initialize Kafka producer
            initializeKafka();

            // 3. Initialize SS7 stack
            initializeM3UA();
            initializeSCCP();
            initializeTCAP();
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
     * Initialize Redis dialog store.
     */
    private void initializeRedis() {
        logger.info("Initializing Redis dialog store...");

        try {
            // Parse Redis cluster nodes from config
            String redisNodes = config.getProperty("redis.cluster.nodes",
                    "localhost:7000,localhost:7001,localhost:7002");

            Set<HostAndPort> clusterNodes = new HashSet<>();
            for (String node : redisNodes.split(",")) {
                String[] parts = node.trim().split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 6379;
                clusterNodes.add(new HostAndPort(host, port));
            }

            // Create Jedis cluster
            JedisCluster jedisCluster = new JedisCluster(clusterNodes);

            // Create dialog store
            int dialogTTL = Integer.parseInt(config.getProperty("redis.dialog.ttl", "3600"));
            dialogStore = new RedisDialogStoreImpl(jedisCluster, dialogTTL);

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
     * Initialize Kafka producer.
     */
    private void initializeKafka() {
        logger.info("Initializing Kafka producer...");

        try {
            // Kafka configuration
            Properties kafkaConfig = new Properties();
            kafkaConfig.setProperty("bootstrap.servers",
                    config.getProperty("kafka.bootstrap.servers", "localhost:9092"));
            kafkaConfig.setProperty("client.id",
                    config.getProperty("kafka.client.id", "ss7-ha-gateway"));

            // Topic prefix
            String topicPrefix = config.getProperty("kafka.topic.prefix", "ss7.");

            // Create producer
            kafkaProducer = new SS7KafkaProducer(kafkaConfig, topicPrefix);

            logger.info("Kafka producer initialized successfully");

        } catch (Exception e) {
            logger.error("Failed to initialize Kafka", e);
            throw new RuntimeException("Kafka initialization failed", e);
        }
    }

    /**
     * Initialize M3UA layer.
     */
    private void initializeM3UA() {
        logger.info("Initializing M3UA layer...");

        try {
            String m3uaName = config.getProperty("m3ua.name", "M3UA-STACK");

            m3uaManagement = new M3UAManagementImpl(m3uaName, null);

            // Configure M3UA (simplified - production needs full config)
            // - Add SCTP associations
            // - Add application servers
            // - Add routes
            // See Mobius JSS7 documentation for details

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

            sccpStack = new SccpStackImpl(sccpName);
            // sccpStack.setMtp3UserPart(1, m3uaManagement);

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
     * Initialize TCAP layer.
     */
    private void initializeTCAP() {
        logger.info("Initializing TCAP layer...");

        try {
            String tcapName = config.getProperty("tcap.name", "TCAP-STACK");

            tcapStack = new TCAPStackImpl(tcapName, sccpStack.getSccpProvider(), 8);

            // Configure TCAP
            tcapStack.setDialogIdleTimeout(60000);  // 60 seconds
            tcapStack.setInvokeTimeout(30000);      // 30 seconds
            tcapStack.setMaxDialogs(5000);

            logger.info("TCAP layer initialized");

        } catch (Exception e) {
            logger.error("Failed to initialize TCAP", e);
            throw new RuntimeException("TCAP initialization failed", e);
        }
    }

    /**
     * Initialize MAP layer.
     */
    private void initializeMAP() {
        logger.info("Initializing MAP layer...");

        try {
            String mapName = config.getProperty("map.name", "MAP-STACK");

            mapStack = new MAPStackImpl(mapName, tcapStack.getProvider());

            logger.info("MAP layer initialized");

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
            boolean publishToKafka = Boolean.parseBoolean(
                    config.getProperty("kafka.publish.enabled", "true"));
            int dialogTTL = Integer.parseInt(
                    config.getProperty("redis.dialog.ttl", "3600"));

            // Create SMS service listener with Redis + Kafka
            smsServiceListener = new MapSmsServiceListener(
                    dialogStore,
                    kafkaProducer,
                    persistDialogs,
                    publishToKafka,
                    dialogTTL
            );

            // Register with MAP stack
            MAPProvider mapProvider = mapStack.getMAPProvider();
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
            m3uaManagement.start();
            logger.info("M3UA started");

            sccpStack.start();
            logger.info("SCCP started");

            tcapStack.start();
            logger.info("TCAP started");

            mapStack.start();
            logger.info("MAP started");

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

            if (tcapStack != null) {
                tcapStack.stop();
                logger.info("TCAP stopped");
            }

            if (sccpStack != null) {
                sccpStack.stop();
                logger.info("SCCP stopped");
            }

            if (m3uaManagement != null) {
                m3uaManagement.stop();
                logger.info("M3UA stopped");
            }

            // Close Kafka producer
            if (kafkaProducer != null) {
                kafkaProducer.close();
                logger.info("Kafka producer closed");
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

        // Create and start gateway
        SS7HAGatewayBootstrap gateway = new SS7HAGatewayBootstrap(config);

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
