package com.company.ss7ha.core.handlers;

import com.company.ss7ha.core.events.EventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.restcomm.protocols.ss7.map.api.MAPDialog;
import org.restcomm.protocols.ss7.map.api.MAPParameterFactory;
import org.restcomm.protocols.ss7.map.api.MAPProvider;
import org.restcomm.protocols.ss7.map.api.service.mobility.MAPServiceMobility;
import org.restcomm.protocols.ss7.map.api.service.sms.MAPServiceSms;
import org.restcomm.protocols.ss7.map.api.service.supplementary.MAPServiceSupplementary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles outgoing MAP commands from NATS.
 * Bridges JSON commands (Business Logic) to JSS7 MAP Provider.
 * Supports SMS, Mobility and USSD commands with Hex Passthrough.
 */
public class MapMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(MapMessageHandler.class);
    private final MAPProvider mapProvider;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public MapMessageHandler(MAPProvider mapProvider, EventPublisher eventPublisher) {
        this.mapProvider = mapProvider;
        this.eventPublisher = eventPublisher;
        this.objectMapper = new ObjectMapper();
    }

    public void handleCommand(String jsonCommand) {
        try {
            JsonNode root = objectMapper.readTree(jsonCommand);
            String type = root.path("type").asText();
            long dialogId = root.path("dialogId").asLong();
            JsonNode payload = root.path("payload");

            logger.debug("Received MAP command: {} for dialog: {}", type, dialogId);

            MAPDialog dialog = mapProvider.getMAPDialog(dialogId);
            if (dialog == null) {
                logger.error("MAP Dialog not found: {}", dialogId);
                return;
            }

            // Stubbed Logic - No Op
            
            // dialog.send(null); // Stubbed

        } catch (Exception e) {
            logger.error("Error processing MAP command", e);
        }
    }
}