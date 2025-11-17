package com.company.ss7ha.core.routing;

import com.company.ss7ha.core.config.SS7Configuration;
import com.company.ss7ha.core.config.SS7Configuration.InternalGT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Manages hierarchical GT routing and load balancing
 * Routes from generic GTs (GTg1, GTg2) to internal GTs (GT1...GTn)
 */
public class GTManager {
    private static final Logger logger = LoggerFactory.getLogger(GTManager.class);
    
    private final SS7Configuration config;
    private final Map<String, Pattern> routingPatterns = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();
    private final Map<String, GTStats> gtStatistics = new ConcurrentHashMap<>();
    
    // Current active generic GT
    private volatile String activeGenericGT;
    private volatile boolean useGenericGT2 = false;
    
    public GTManager(SS7Configuration config) {
        this.config = config;
        this.activeGenericGT = config.getGenericGT1();
        initializeRoutingPatterns();
        initializeStatistics();
    }
    
    /**
     * Initialize routing patterns from configuration
     */
    private void initializeRoutingPatterns() {
        for (InternalGT gt : config.getInternalGTs()) {
            try {
                Pattern pattern = Pattern.compile(gt.getRoutingPattern());
                routingPatterns.put(gt.getName(), pattern);
                roundRobinCounters.put(gt.getName(), new AtomicInteger(0));
                logger.info("Initialized routing pattern for {}: {}", 
                           gt.getName(), gt.getRoutingPattern());
            } catch (Exception e) {
                logger.error("Failed to compile pattern for GT {}: {}", 
                            gt.getName(), gt.getRoutingPattern(), e);
            }
        }
    }
    
    /**
     * Initialize statistics tracking
     */
    private void initializeStatistics() {
        gtStatistics.put(config.getGenericGT1(), new GTStats(config.getGenericGT1()));
        gtStatistics.put(config.getGenericGT2(), new GTStats(config.getGenericGT2()));
        
        for (InternalGT gt : config.getInternalGTs()) {
            gtStatistics.put(gt.getAddress(), new GTStats(gt.getAddress()));
        }
    }
    
    /**
     * Route a message to appropriate internal GT based on destination
     */
    public String routeToInternalGT(String destination) {
        // First, find matching GTs based on routing patterns
        List<InternalGT> matchingGTs = findMatchingGTs(destination);
        
        if (matchingGTs.isEmpty()) {
            logger.warn("No matching GT found for destination: {}", destination);
            return getDefaultGT();
        }
        
        // If single match, use it
        if (matchingGTs.size() == 1) {
            InternalGT selected = matchingGTs.get(0);
            updateStatistics(selected.getAddress(), true);
            return selected.getAddress();
        }
        
        // Multiple matches - use load balancing
        InternalGT selected = loadBalance(matchingGTs);
        updateStatistics(selected.getAddress(), true);
        return selected.getAddress();
    }
    
    /**
     * Find all GTs matching the destination pattern
     */
    private List<InternalGT> findMatchingGTs(String destination) {
        return config.getInternalGTs().stream()
            .filter(gt -> gt.isActive())
            .filter(gt -> {
                Pattern pattern = routingPatterns.get(gt.getName());
                return pattern != null && pattern.matcher(destination).matches();
            })
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Load balance among multiple matching GTs
     */
    private InternalGT loadBalance(List<InternalGT> gts) {
        // Simple round-robin for now
        // Could be enhanced with weighted distribution based on load
        String key = gts.stream()
            .map(InternalGT::getName)
            .collect(java.util.stream.Collectors.joining("-"));
        
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(key, 
            k -> new AtomicInteger(0));
        
        int index = counter.getAndIncrement() % gts.size();
        return gts.get(index);
    }
    
    /**
     * Get default GT when no pattern matches
     */
    private String getDefaultGT() {
        // Return first active GT as default
        return config.getInternalGTs().stream()
            .filter(InternalGT::isActive)
            .findFirst()
            .map(InternalGT::getAddress)
            .orElse(config.getGenericGT1());
    }
    
    /**
     * Handle failover between generic GTs
     */
    public void failoverGenericGT() {
        if (config.getHaMode().equals("ACTIVE_STANDBY")) {
            String previousGT = activeGenericGT;
            
            if (activeGenericGT.equals(config.getGenericGT1())) {
                activeGenericGT = config.getGenericGT2();
                useGenericGT2 = true;
            } else {
                activeGenericGT = config.getGenericGT1();
                useGenericGT2 = false;
            }
            
            logger.warn("Generic GT failover: {} -> {}", previousGT, activeGenericGT);
            
            // Update statistics
            gtStatistics.get(previousGT).recordFailure();
        }
    }
    
    /**
     * Mark an internal GT as failed
     */
    public void markGTFailed(String gtAddress) {
        config.getInternalGTs().stream()
            .filter(gt -> gt.getAddress().equals(gtAddress))
            .findFirst()
            .ifPresent(gt -> {
                gt.setActive(false);
                logger.warn("Marked GT {} as failed", gtAddress);
                gtStatistics.get(gtAddress).recordFailure();
            });
    }
    
    /**
     * Mark an internal GT as recovered
     */
    public void markGTRecovered(String gtAddress) {
        config.getInternalGTs().stream()
            .filter(gt -> gt.getAddress().equals(gtAddress))
            .findFirst()
            .ifPresent(gt -> {
                gt.setActive(true);
                logger.info("Marked GT {} as recovered", gtAddress);
                gtStatistics.get(gtAddress).recordRecovery();
            });
    }
    
    /**
     * Update statistics for a GT
     */
    private void updateStatistics(String gtAddress, boolean success) {
        GTStats stats = gtStatistics.get(gtAddress);
        if (stats != null) {
            if (success) {
                stats.recordSuccess();
            } else {
                stats.recordFailure();
            }
        }
    }
    
    /**
     * Get current active generic GT
     */
    public String getActiveGenericGT() {
        return activeGenericGT;
    }
    
    /**
     * Check if a GT is healthy based on statistics
     */
    public boolean isGTHealthy(String gtAddress) {
        GTStats stats = gtStatistics.get(gtAddress);
        return stats != null && stats.isHealthy();
    }
    
    /**
     * Get statistics for monitoring
     */
    public Map<String, GTStats> getStatistics() {
        return new ConcurrentHashMap<>(gtStatistics);
    }
    
    /**
     * Statistics tracking for each GT
     */
    public static class GTStats {
        private final String gtAddress;
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger totalMessages = new AtomicInteger(0);
        private volatile long lastSuccessTime = System.currentTimeMillis();
        private volatile long lastFailureTime = 0;
        
        public GTStats(String gtAddress) {
            this.gtAddress = gtAddress;
        }
        
        public void recordSuccess() {
            successCount.incrementAndGet();
            totalMessages.incrementAndGet();
            lastSuccessTime = System.currentTimeMillis();
        }
        
        public void recordFailure() {
            failureCount.incrementAndGet();
            totalMessages.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();
        }
        
        public void recordRecovery() {
            lastSuccessTime = System.currentTimeMillis();
        }
        
        public boolean isHealthy() {
            // Consider healthy if:
            // 1. No failures in last 30 seconds
            // 2. Success rate > 95%
            long now = System.currentTimeMillis();
            if (lastFailureTime > 0 && (now - lastFailureTime) < 30000) {
                return false;
            }
            
            int total = totalMessages.get();
            if (total > 100) {
                double successRate = (double) successCount.get() / total;
                return successRate > 0.95;
            }
            
            return true;
        }
        
        // Getters
        public String getGtAddress() { return gtAddress; }
        public int getSuccessCount() { return successCount.get(); }
        public int getFailureCount() { return failureCount.get(); }
        public int getTotalMessages() { return totalMessages.get(); }
        public long getLastSuccessTime() { return lastSuccessTime; }
        public long getLastFailureTime() { return lastFailureTime; }
    }
}