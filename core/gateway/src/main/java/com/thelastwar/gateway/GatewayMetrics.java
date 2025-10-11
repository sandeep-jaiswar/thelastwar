package com.thelastwar.gateway;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collector for gateway operations.
 * 
 * Tracks:
 * - Message throughput (inbound/outbound)
 * - Decode/encode latency distributions
 * - Session count and status
 * - Error rates
 * - Queue depths
 * 
 * All metrics are exported via Micrometer and can be scraped by Prometheus.
 */
public class GatewayMetrics {
    
    private final MeterRegistry registry;
    private final String gatewayName;
    
    // Counters
    private final Counter inboundMessages;
    private final Counter outboundMessages;
    private final Counter decodeErrors;
    private final Counter encodeErrors;
    private final Counter sessionConnects;
    private final Counter sessionDisconnects;
    
    // Timers for latency tracking
    private final Timer decodeLatency;
    private final Timer encodeLatency;
    private final Timer messageRoundTrip;
    
    // Gauges
    private final AtomicLong activeSessions;
    private final AtomicLong inboundQueueDepth;
    private final AtomicLong outboundQueueDepth;
    
    /**
     * Creates a new GatewayMetrics instance.
     * 
     * @param registry Micrometer registry to register metrics with
     * @param gatewayName Unique name for this gateway instance
     */
    public GatewayMetrics(MeterRegistry registry, String gatewayName) {
        this.registry = registry;
        this.gatewayName = gatewayName;
        
        // Initialize counters
        this.inboundMessages = Counter.builder("gateway.messages.inbound")
                .description("Number of inbound messages received")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.outboundMessages = Counter.builder("gateway.messages.outbound")
                .description("Number of outbound messages sent")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.decodeErrors = Counter.builder("gateway.errors.decode")
                .description("Number of message decode errors")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.encodeErrors = Counter.builder("gateway.errors.encode")
                .description("Number of message encode errors")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.sessionConnects = Counter.builder("gateway.sessions.connects")
                .description("Number of session connects")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.sessionDisconnects = Counter.builder("gateway.sessions.disconnects")
                .description("Number of session disconnects")
                .tag("gateway", gatewayName)
                .register(registry);
        
        // Initialize timers with percentiles
        this.decodeLatency = Timer.builder("gateway.decode.latency")
                .description("Message decode latency distribution")
                .tag("gateway", gatewayName)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
        
        this.encodeLatency = Timer.builder("gateway.encode.latency")
                .description("Message encode latency distribution")
                .tag("gateway", gatewayName)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
        
        this.messageRoundTrip = Timer.builder("gateway.message.roundtrip")
                .description("Message round-trip latency")
                .tag("gateway", gatewayName)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
        
        // Initialize gauges
        this.activeSessions = new AtomicLong(0);
        Gauge.builder("gateway.sessions.active", activeSessions, AtomicLong::get)
                .description("Number of active sessions")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.inboundQueueDepth = new AtomicLong(0);
        Gauge.builder("gateway.queue.inbound", inboundQueueDepth, AtomicLong::get)
                .description("Inbound queue depth")
                .tag("gateway", gatewayName)
                .register(registry);
        
        this.outboundQueueDepth = new AtomicLong(0);
        Gauge.builder("gateway.queue.outbound", outboundQueueDepth, AtomicLong::get)
                .description("Outbound queue depth")
                .tag("gateway", gatewayName)
                .register(registry);
    }
    
    /**
     * Creates a GatewayMetrics with a default SimpleMeterRegistry.
     * 
     * @param gatewayName Unique name for this gateway instance
     */
    public GatewayMetrics(String gatewayName) {
        this(new SimpleMeterRegistry(), gatewayName);
    }
    
    // Counter methods
    public void recordInboundMessage() {
        inboundMessages.increment();
    }
    
    public void recordOutboundMessage() {
        outboundMessages.increment();
    }
    
    public void recordDecodeError() {
        decodeErrors.increment();
    }
    
    public void recordEncodeError() {
        encodeErrors.increment();
    }
    
    public void recordSessionConnect() {
        sessionConnects.increment();
        activeSessions.incrementAndGet();
    }
    
    public void recordSessionDisconnect() {
        sessionDisconnects.increment();
        activeSessions.decrementAndGet();
    }
    
    // Timer methods
    public Timer.Sample startDecodeTimer() {
        return Timer.start(registry);
    }
    
    public void recordDecodeLatency(Timer.Sample sample) {
        sample.stop(decodeLatency);
    }
    
    public Timer.Sample startEncodeTimer() {
        return Timer.start(registry);
    }
    
    public void recordEncodeLatency(Timer.Sample sample) {
        sample.stop(encodeLatency);
    }
    
    public Timer.Sample startRoundTripTimer() {
        return Timer.start(registry);
    }
    
    public void recordRoundTripLatency(Timer.Sample sample) {
        sample.stop(messageRoundTrip);
    }
    
    // Gauge setters
    public void setInboundQueueDepth(long depth) {
        inboundQueueDepth.set(depth);
    }
    
    public void setOutboundQueueDepth(long depth) {
        outboundQueueDepth.set(depth);
    }
    
    // Getters for current values
    public long getInboundMessageCount() {
        return (long) inboundMessages.count();
    }
    
    public long getOutboundMessageCount() {
        return (long) outboundMessages.count();
    }
    
    public long getDecodeErrorCount() {
        return (long) decodeErrors.count();
    }
    
    public long getEncodeErrorCount() {
        return (long) encodeErrors.count();
    }
    
    public long getActiveSessionCount() {
        return activeSessions.get();
    }
    
    public double getP99DecodeLatencyNanos() {
        io.micrometer.core.instrument.distribution.HistogramSnapshot snapshot = decodeLatency.takeSnapshot();
        var percentileValues = snapshot.percentileValues();
        for (var p : percentileValues) {
            if (p.percentile() == 0.99) {
                return p.value(java.util.concurrent.TimeUnit.NANOSECONDS);
            }
        }
        return 0.0;
    }
    
    public double getP99EncodeLatencyNanos() {
        io.micrometer.core.instrument.distribution.HistogramSnapshot snapshot = encodeLatency.takeSnapshot();
        var percentileValues = snapshot.percentileValues();
        for (var p : percentileValues) {
            if (p.percentile() == 0.99) {
                return p.value(java.util.concurrent.TimeUnit.NANOSECONDS);
            }
        }
        return 0.0;
    }
    
    /**
     * Gets throughput in messages per second.
     * 
     * @return throughput (inbound + outbound)
     */
    public double getThroughput() {
        return inboundMessages.count() + outboundMessages.count();
    }
    
    public MeterRegistry getRegistry() {
        return registry;
    }
}
