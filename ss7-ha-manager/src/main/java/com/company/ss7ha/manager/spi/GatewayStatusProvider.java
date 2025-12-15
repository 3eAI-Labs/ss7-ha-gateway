package com.company.ss7ha.manager.spi;

import java.util.Map;

/**
 * Interface to decouple the REST API from the SS7 Core implementation.
 * ss7-core will implement this to provide status and metrics to ss7-ha-manager.
 */
public interface GatewayStatusProvider {
    /**
     * Check if the gateway is healthy.
     * @return true if healthy, false otherwise.
     */
    boolean isHealthy();

    /**
     * Get system metrics (SS7 stack stats, NATS stats, JVM stats).
     * @return Map of metrics.
     */
    Map<String, Object> getMetrics();
}
