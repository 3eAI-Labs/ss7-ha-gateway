package com.company.ss7ha.core.registry;

import com.company.ss7ha.core.api.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for CAP Message Handlers.
 * Maps command types (e.g. "CONNECT") to their handler implementations.
 */
public class MessageHandlerRegistry {
    private static final Logger logger = LoggerFactory.getLogger(MessageHandlerRegistry.class);
    private final Map<String, MessageHandler> handlers = new HashMap<>();

    public void register(String type, MessageHandler handler) {
        handlers.put(type.toUpperCase(), handler);
        logger.info("Registered handler for CAP command: {}", type.toUpperCase());
    }

    public MessageHandler getHandler(String type) {
        return handlers.get(type.toUpperCase());
    }
}
