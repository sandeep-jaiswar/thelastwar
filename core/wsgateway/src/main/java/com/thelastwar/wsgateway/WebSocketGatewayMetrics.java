package com.thelastwar.wsgateway;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collector for WebSocket Gateway operations.
 * 
 * Tracks:
 * - Concurrent client connections
 * - Message throughput (inbound/outbound)
 * - Broadcast latency (tick-to-client)
 * - Subscription counts
 * - Backpressure events
 * - Session lifecycle events
 * 
 * All metrics are exported via Micrometer and can be scraped by Prometheus.
 */
public class WebSocketGatewayMetrics {
    
    private final MeterRegistry registry;
    private final String gatewayName;
    
    // Counters
    private final Counter inboundMessages;
    private final Counter outboundMessages;
    private final Counter subscriptions;
    private final Counter unsubscriptions;
    private final Counter connectionOpened;
    private final Counter connectionClosed;
    private final Counter backpressureEvents;
    private final Counter serializationErrors;
    
    // Timers for latency tracking
    private final Timer broadcastLatency;
    private final Timer serializationLatency;
    
    // Gauges
    private final AtomicLong concurrentClients;
    private final AtomicLong totalSubscriptions;
    
    /**
     * Creates a new WebSocketGatewayMetrics instance.
     * 
     * @param registry Micrometer registry to register metrics with
     * @param gatewayName Unique name for this gateway instance
     */
    public WebSocketGatewayMetrics(MeterRegistry registry, String gatewayName) {
        this.registry = registry;
        this.gatewayName = gatewayName;
        
        // Initialize counters
        this.inboundMessages = Counter.builder("wsgateway.messages.inbound")
                .description("Number of messages received from clients")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.outboundMessages = Counter.builder("wsgateway.messages.outbound")
                .description("Number of messages sent to clients")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.subscriptions = Counter.builder("wsgateway.subscriptions.total")
                .description("Total number of subscription requests")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.unsubscriptions = Counter.builder("wsgateway.unsubscriptions.total")
                .description("Total number of unsubscription requests")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.connectionOpened = Counter.builder("wsgateway.connections.opened")
                .description("Number of WebSocket connections opened")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.connectionClosed = Counter.builder("wsgateway.connections.closed")
                .description("Number of WebSocket connections closed")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.backpressureEvents = Counter.builder("wsgateway.backpressure.events")
                .description("Number of backpressure events (message drops)")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.serializationErrors = Counter.builder("wsgateway.serialization.errors")
                .description("Number of serialization errors")
                .tag("gateway", gatewayName)
                .register(registry);
        
        // Initialize timers with percentiles
        this.broadcastLatency = Timer.builder("wsgateway.broadcast.latency")
                .description("Broadcast latency from event to client")
                .tag("gateway", gatewayName)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        
        this.serializationLatency = Timer.builder("wsgateway.serialization.latency")
                .description("Message serialization latency")
                .tag("gateway", gatewayName)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        
        // Initialize gauges
        this.concurrentClients = new AtomicLong(0);
        this.totalSubscriptions = new AtomicLong(0);
        
        Gauge.builder("wsgateway.clients.concurrent", concurrentClients, AtomicLong::get)
                .description("Number of concurrent WebSocket clients")
                .tag("gateway", gatewayName)
                .register(registry);
        
        Gauge.builder("wsgateway.subscriptions.active", totalSubscriptions, AtomicLong::get)
                .description("Number of active subscriptions")
                .tag("gateway", gatewayName)
                .register(registry);
    }
    
    /**
     * Creates a WebSocketGatewayMetrics with default registry.
     */
    public WebSocketGatewayMetrics(String gatewayName) {
        this(new SimpleMeterRegistry(), gatewayName);
    }
    
    // Recording methods
    
    public void recordInboundMessage() {
        inboundMessages.increment();
    }
    
    public void recordOutboundMessage() {
        outboundMessages.increment();
    }
    
    public void recordSubscription() {
        subscriptions.increment();
        totalSubscriptions.incrementAndGet();
    }
    
    public void recordUnsubscription() {
        unsubscriptions.increment();
        totalSubscriptions.decrementAndGet();
    }
    
    public void recordConnectionOpened() {
        connectionOpened.increment();
        concurrentClients.incrementAndGet();
    }
    
    public void recordConnectionClosed() {
        connectionClosed.increment();
        concurrentClients.decrementAndGet();
    }
    
    public void recordBackpressureEvent() {
        backpressureEvents.increment();
    }
    
    public void recordSerializationError() {
        serializationErrors.increment();
    }
    
    public Timer.Sample startBroadcastTimer() {
        return Timer.start(registry);
    }
    
    public void recordBroadcastLatency(Timer.Sample sample) {
        sample.stop(broadcastLatency);
    }
    
    public Timer.Sample startSerializationTimer() {
        return Timer.start(registry);
    }
    
    public void recordSerializationLatency(Timer.Sample sample) {
        sample.stop(serializationLatency);
    }
    
    // Getters for metrics
    
    public long getInboundMessageCount() {
        return (long) inboundMessages.count();
    }
    
    public long getOutboundMessageCount() {
        return (long) outboundMessages.count();
    }
    
    public long getConcurrentClients() {
        return concurrentClients.get();
    }
    
    public long getTotalSubscriptions() {
        return totalSubscriptions.get();
    }
    
    public long getBackpressureEventCount() {
        return (long) backpressureEvents.count();
    }
    
    public double getP99BroadcastLatencyNanos() {
        return broadcastLatency.takeSnapshot().percentileValues()[2].value(java.util.concurrent.TimeUnit.NANOSECONDS);
    }
    
    public double getP99SerializationLatencyNanos() {
        return serializationLatency.takeSnapshot().percentileValues()[2].value(java.util.concurrent.TimeUnit.NANOSECONDS);
    }
    
    public MeterRegistry getRegistry() {
        return registry;
    }
}
