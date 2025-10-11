package com.thelastwar.wsgateway;

/**
 * Transport format options for WebSocket communication.
 */
public enum TransportFormat {
    /**
     * JSON text format using Jackson.
     * Human-readable, widely compatible.
     */
    JSON,
    
    /**
     * MessagePack binary format.
     * Compact, efficient for high-throughput scenarios.
     */
    MESSAGEPACK
}
