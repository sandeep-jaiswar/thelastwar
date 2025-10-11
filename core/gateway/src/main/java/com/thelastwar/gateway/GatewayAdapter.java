package com.thelastwar.gateway;

import com.thelastwar.eventbus.EventBus;

/**
 * Gateway adapter interface for handling external protocol communication.
 * 
 * Provides abstraction for different gateway implementations (FIX, REST, WebSocket, etc.)
 * that bridge external systems with the internal EventBus.
 * 
 * Key responsibilities:
 * - Protocol translation (external protocol <-> internal events)
 * - Session management and connection lifecycle
 * - Message routing and delivery guarantees
 * - Performance monitoring and metrics collection
 * 
 * Performance targets (per architecture):
 * - Throughput: ≥ 200K msgs/sec
 * - Latency (p99): ≤ 10 µs decode time
 * - Zero heap allocation in hot path
 * 
 * Thread-safety: Implementations must be thread-safe.
 */
public interface GatewayAdapter extends AutoCloseable {
    
    /**
     * Initializes and starts the gateway.
     * Establishes connections, initializes sessions, and begins message processing.
     * 
     * @throws GatewayException if initialization fails
     */
    void start() throws GatewayException;
    
    /**
     * Stops the gateway gracefully.
     * Closes all sessions, flushes pending messages, and releases resources.
     * 
     * @throws GatewayException if shutdown fails
     */
    void stop() throws GatewayException;
    
    /**
     * Checks if the gateway is currently running.
     * 
     * @return true if running, false otherwise
     */
    boolean isRunning();
    
    /**
     * Sends an outbound message through the gateway.
     * 
     * @param message Message to send
     * @return true if successfully sent, false otherwise
     */
    boolean sendMessage(Object message);
    
    /**
     * Gets the EventBus used by this gateway for internal communication.
     * 
     * @return EventBus instance
     */
    EventBus getEventBus();
    
    /**
     * Gets gateway metrics for monitoring.
     * 
     * @return GatewayMetrics snapshot
     */
    GatewayMetrics getMetrics();
    
    /**
     * Gets the current session state.
     * 
     * @return SessionState indicating connection status
     */
    SessionState getSessionState();
    
    /**
     * Default implementation of close that calls stop().
     */
    @Override
    default void close() throws Exception {
        stop();
    }
    
    /**
     * Session state enumeration.
     */
    enum SessionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        LOGGED_IN,
        LOGGED_OUT,
        ERROR
    }
}
