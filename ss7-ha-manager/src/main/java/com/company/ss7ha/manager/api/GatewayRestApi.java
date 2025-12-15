package com.company.ss7ha.manager.api;

import com.company.ss7ha.manager.spi.GatewayStatusProvider;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API Server for SS7 HA Gateway management.
 * Provides Health Check and Metrics endpoints using Javalin.
 */
public class GatewayRestApi {
    private static final Logger logger = LoggerFactory.getLogger(GatewayRestApi.class);
    private final GatewayStatusProvider statusProvider;
    private Javalin app;

    public GatewayRestApi(GatewayStatusProvider statusProvider) {
        this.statusProvider = statusProvider;
    }

    public void start(int port) {
        logger.info("Starting Gateway REST API on port {}", port);
        
        // Javalin 6 syntax
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        }).start(port);

        app.get("/health", this::handleHealth);
        app.get("/metrics", this::handleMetrics);
        
        // Default error handler for JSON response
        app.exception(Exception.class, (e, ctx) -> {
            logger.error("API Error", e);
            respond(ctx, 500, "INTERNAL_ERROR", "Internal Server Error", null);
        });
        
        logger.info("Gateway REST API started successfully");
    }

    private void handleHealth(Context ctx) {
        boolean healthy = statusProvider.isHealthy();
        if (healthy) {
            respond(ctx, 200, "UP", "System is healthy", null);
        } else {
            respond(ctx, 503, "DOWN", "System is degraded", null);
        }
    }
    
    private void handleMetrics(Context ctx) {
        Map<String, Object> metrics = statusProvider.getMetrics();
        respond(ctx, 200, "METRICS_RETRIEVED", "Metrics retrieved successfully", metrics);
    }
    
    private void respond(Context ctx, int status, String code, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", status >= 200 && status < 300);
        response.put("message", message);
        response.put("code", code);
        response.put("data", data);
        response.put("meta", Map.of("timestamp", Instant.now().toString()));
        
        ctx.status(status).json(response);
    }

    public void stop() {
        if (app != null) {
            logger.info("Stopping Gateway REST API");
            app.stop();
        }
    }
}
