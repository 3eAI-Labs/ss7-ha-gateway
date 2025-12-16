package com.company.ss7ha.core.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.restcomm.protocols.ss7.cap.api.CAPDialog;
import org.restcomm.protocols.ss7.cap.api.CAPProvider;

/**
 * Interface for handling specific CAP commands.
 * Enables dynamic loading and modularity.
 */
public interface MessageHandler {
    /**
     * Handle a specific CAP command.
     * @param payload The JSON payload of the command (parameters).
     * @param dialog The CAP Dialog associated with this command.
     * @param provider The CAP Provider to access factories and services.
     * @throws Exception If handling fails.
     */
    void handle(JsonNode payload, CAPDialog dialog, CAPProvider provider) throws Exception;
}
