package com.company.ss7ha.core.manager;

import com.company.ss7ha.core.config.SS7Configuration;
import com.company.ss7ha.core.stack.SS7Stack;
import org.restcomm.protocols.ss7.map.api.MAPProvider;
import org.restcomm.protocols.ss7.cap.api.CAPProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton Manager for SS7 Stack Lifecycle.
 * Ensures only one instance of the stack exists and manages its initialization, startup, and shutdown.
 */
public class SS7StackManager {
    private static final Logger logger = LoggerFactory.getLogger(SS7StackManager.class);
    private static volatile SS7StackManager instance;
    private SS7Stack ss7Stack;

    private SS7StackManager() {
        // Prevent instantiation
    }

    public static SS7StackManager getInstance() {
        if (instance == null) {
            synchronized (SS7StackManager.class) {
                if (instance == null) {
                    instance = new SS7StackManager();
                }
            }
        }
        return instance;
    }

    public synchronized void initialize(SS7Configuration config) throws Exception {
        if (ss7Stack != null && ss7Stack.isInitialized()) {
            logger.warn("SS7 Stack Manager: Stack already initialized");
            return;
        }
        
        logger.info("SS7 Stack Manager: Initializing SS7 Stack");
        // Create the stack instance (delegating detailed setup to SS7Stack class)
        ss7Stack = new SS7Stack(config, "SS7Gateway");
        ss7Stack.initialize();
    }

    public synchronized void start() throws Exception {
        if (ss7Stack == null) {
             throw new IllegalStateException("SS7 Stack not initialized. Call initialize(config) first.");
        }
        ss7Stack.start();
    }

    public synchronized void stop() {
        logger.info("SS7 Stack Manager: Stopping SS7 Stack");
        if (ss7Stack != null) {
            ss7Stack.stop();
        }
    }

    public MAPProvider getMapProvider() {
        return ss7Stack != null ? ss7Stack.getMapProvider() : null;
    }

    public CAPProvider getCapProvider() {
        return ss7Stack != null ? ss7Stack.getCapProvider() : null;
    }
    
    public SS7Stack getStack() {
        return ss7Stack;
    }
    
    public boolean isReady() {
        return ss7Stack != null && ss7Stack.isStarted();
    }
}
