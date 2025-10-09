package com.thelastwar.eventbus;

/**
 * Event types for the Event Bus system.
 * Using integer constants instead of enums to avoid autoboxing and improve performance.
 */
public final class EventType {
    
    // Feed Handler Events (1000-1999)
    public static final int MARKET_DATA_UPDATE = 1000;
    public static final int MARKET_DATA_SNAPSHOT = 1001;
    public static final int FEED_CONNECTION_STATUS = 1002;
    
    // Matching Engine Events (2000-2999)
    public static final int ORDER_ACCEPTED = 2000;
    public static final int ORDER_REJECTED = 2001;
    public static final int ORDER_FILLED = 2002;
    public static final int ORDER_PARTIALLY_FILLED = 2003;
    public static final int ORDER_CANCELLED = 2004;
    
    // Risk Management Events (3000-3999)
    public static final int RISK_CHECK_PASSED = 3000;
    public static final int RISK_CHECK_FAILED = 3001;
    public static final int POSITION_UPDATE = 3002;
    public static final int LIMIT_BREACH = 3003;
    
    // OMS Events (4000-4999)
    public static final int ORDER_SUBMITTED = 4000;
    public static final int ORDER_MODIFIED = 4001;
    public static final int ORDER_STATUS_UPDATE = 4002;
    
    // Analytics Events (5000-5999)
    public static final int PERFORMANCE_METRIC = 5000;
    public static final int LATENCY_SAMPLE = 5001;
    public static final int TRADE_ANALYTICS = 5002;
    
    // System Events (9000-9999)
    public static final int SYSTEM_STARTUP = 9000;
    public static final int SYSTEM_SHUTDOWN = 9001;
    public static final int HEARTBEAT = 9002;
    
    private EventType() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Checks if an event type is valid.
     * 
     * @param eventType The event type to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(int eventType) {
        return (eventType >= 1000 && eventType < 2000) ||
               (eventType >= 2000 && eventType < 3000) ||
               (eventType >= 3000 && eventType < 4000) ||
               (eventType >= 4000 && eventType < 5000) ||
               (eventType >= 5000 && eventType < 6000) ||
               (eventType >= 9000 && eventType < 10000);
    }
    
    /**
     * Gets the subsystem category for an event type.
     * 
     * @param eventType The event type
     * @return subsystem name
     */
    public static String getSubsystem(int eventType) {
        if (eventType >= 1000 && eventType < 2000) return "FeedHandler";
        if (eventType >= 2000 && eventType < 3000) return "MatchingEngine";
        if (eventType >= 3000 && eventType < 4000) return "Risk";
        if (eventType >= 4000 && eventType < 5000) return "OMS";
        if (eventType >= 5000 && eventType < 6000) return "Analytics";
        if (eventType >= 9000 && eventType < 10000) return "System";
        return "Unknown";
    }
}
