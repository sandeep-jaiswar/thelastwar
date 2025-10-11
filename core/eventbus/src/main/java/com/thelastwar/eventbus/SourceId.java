package com.thelastwar.eventbus;

/**
 * Source identifiers for event producers.
 * Using integer constants for GC-neutral design.
 */
public final class SourceId {

    public static final int FEED_HANDLER = 1;
    public static final int MATCHING_ENGINE = 2;
    public static final int RISK_MANAGER = 3;
    public static final int OMS = 4;
    public static final int ANALYTICS = 5;
    public static final int REST_GATEWAY = 6;
    public static final int SYSTEM = 99;

    private SourceId() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets the name of a source.
     * 
     * @param sourceId The source identifier
     * @return source name (never null)
     */
    public static String getName(int sourceId) {
        return switch (sourceId) {
            case FEED_HANDLER -> "FeedHandler";
            case MATCHING_ENGINE -> "MatchingEngine";
            case RISK_MANAGER -> "RiskManager";
            case OMS -> "OMS";
            case ANALYTICS -> "Analytics";
            case REST_GATEWAY -> "RestGateway";
            case SYSTEM -> "System";
            default -> "Unknown";
        };
    }
}
