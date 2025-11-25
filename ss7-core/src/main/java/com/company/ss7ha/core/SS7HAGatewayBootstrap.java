package com.company.ss7ha.core;

import com.company.ss7ha.core.events.EventPublisher;
import com.company.ss7ha.core.listeners.MapSmsServiceListener;
import com.company.ss7ha.core.redis.RedisDialogStore;
import com.company.ss7ha.core.redis.RedisDialogStoreImpl;
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
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.HostAndPort;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

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
    private RedisDialogStore dialogStore;
    private EventPublisher eventPublisher;
    private MapSmsServiceListener smsServiceListener;
    private SS7NatsPublisher natsPublisher;
    private SS7NatsSubscriber natsSubscriber;

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
            initializeRedis();
            initializeM3UA();
            initializeSCCP();
            initializeMAP();
            registerMapListeners();
            initializeNats();
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

    private void initializeRedis() {
        logger.info("Initializing Redis dialog store...");
        try {
            String redisNodes = System.getProperty("redis.cluster.nodes", 
                config.getProperty("redis.cluster.nodes", "localhost:6379"));
            String ttlStr = System.getProperty("redis.dialog.ttl", 
                config.getProperty("redis.dialog.ttl", "3600"));
            int dialogTTL = Integer.parseInt(ttlStr);

            String[] nodes = redisNodes.split(",");
            UnifiedJedis jedisClient;

            if (nodes.length == 1) {
                String[] parts = nodes[0].trim().split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 6379;
                logger.info("Initializing Redis in Standalone mode: {}:{}", host, port);
                jedisClient = new JedisPooled(host, port);
            } else {
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

            dialogStore = new RedisDialogStoreImpl(jedisClient, dialogTTL);
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
            boolean persistDialogs = Boolean.parseBoolean(config.getProperty("redis.persist.dialogs", "true"));
            boolean publishEvents = Boolean.parseBoolean(config.getProperty("events.publish.enabled", "true"));
            int dialogTTL = Integer.parseInt(config.getProperty("redis.dialog.ttl", "3600"));
            smsServiceListener = new MapSmsServiceListener(dialogStore, eventPublisher, persistDialogs, publishEvents, dialogTTL);
            MAPProvider mapProvider = mapStack.getProvider();
            mapProvider.getMAPServiceSms().addMAPServiceListener(smsServiceListener);
            logger.info("MAP service listeners registered");
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

            // Initialize NATS publisher first
            natsPublisher = new SS7NatsPublisher(natsUrl);
            natsPublisher.start();
            logger.info("NATS Publisher initialized and connected");

            // Initialize NATS subscriber with loopback handler
            natsSubscriber = new SS7NatsSubscriber(natsUrl, queueGroup);

            // Register MT SMS handler with loopback logic
            natsSubscriber.setMtSmsHandler(mtSmsMessage -> {
                logger.info("Processing MT SMS request: {} from {} to {}",
                    mtSmsMessage.getMessageId(),
                    mtSmsMessage.getSender(),
                    mtSmsMessage.getRecipient());

                // Create loopback MO SMS response (swap sender/recipient)
                MapSmsMessage moMessage = new MapSmsMessage();
                moMessage.setMessageId(mtSmsMessage.getMessageId() + "_MO");
                moMessage.setCorrelationId(mtSmsMessage.getMessageId());
                moMessage.setSender(mtSmsMessage.getRecipient());   // Swap
                moMessage.setRecipient(mtSmsMessage.getSender());   // Swap
                moMessage.setContent("Echo: " + mtSmsMessage.getContent());
                moMessage.setSmsType(SmsType.MO_FORWARD_SM);
                moMessage.setTimestamp(Instant.now());

                // Publish loopback MO SMS
                natsPublisher.publishMoSmsResponse(moMessage);
                logger.info("Loopback MO SMS sent (from {} to {})",
                    moMessage.getSender(), moMessage.getRecipient());
            });

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
            if (mapStack != null) mapStack.stop();
            if (sccpStack != null) sccpStack.stop();
            if (m3uaManagement != null) m3uaManagement.stop();
            if (sctpManagement != null) sctpManagement.stop();
            if (workerPool != null) workerPool.stop();
            if (eventPublisher != null) eventPublisher.close();
            if (dialogStore != null) dialogStore.close(); // Close Redis
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