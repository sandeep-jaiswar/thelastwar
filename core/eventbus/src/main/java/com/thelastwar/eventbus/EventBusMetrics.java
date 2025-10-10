package com.thelastwar.eventbus;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collector for EventBus using Micrometer.
 * 
 * Tracks:
 * - Throughput (events published per second)
 * - Latency (publish latency distribution)
 * - Backpressure events (count of backpressure occurrences)
 * - Dropped messages (count of messages that could not be published)
 * - Active subscribers (gauge)
 * - Queue depth (ring buffer utilization)
 * 
 * All metrics are exported via Micrometer and can be scraped by Prometheus.
 */
public class EventBusMetrics {
    
    private final MeterRegistry registry;
    private final String busName;
    
    // Counters
    private final Counter publishedEvents;
    private final Counter backpressureEvents;
    private final Counter droppedMessages;
    private final Counter failedPublishes;
    
    // Timers for latency tracking
    private final Timer publishLatency;
    
    // Gauges
    private final AtomicLong activeSubscribers = new AtomicLong(0);
    private final AtomicLong queueDepth = new AtomicLong(0);
    
    /**
     * Creates a new EventBusMetrics instance.
     * 
     * @param registry the Micrometer registry to register metrics with
     * @param busName unique name for this event bus instance
     */
    public EventBusMetrics(MeterRegistry registry, String busName) {
        this.registry = registry;
        this.busName = busName;
        
        // Initialize counters
        this.publishedEvents = Counter.builder("eventbus.events.published")
                .description("Total number of events successfully published")
                .tag("bus", busName)
                .register(registry);
        
        this.backpressureEvents = Counter.builder("eventbus.backpressure.events")
                .description("Number of backpressure events detected")
                .tag("bus", busName)
                .register(registry);
        
        this.droppedMessages = Counter.builder("eventbus.messages.dropped")
                .description("Number of messages dropped due to backpressure")
                .tag("bus", busName)
                .register(registry);
        
        this.failedPublishes = Counter.builder("eventbus.publishes.failed")
                .description("Number of failed publish attempts")
                .tag("bus", busName)
                .register(registry);
        
        // Initialize timer for latency distribution
        this.publishLatency = Timer.builder("eventbus.publish.latency")
                .description("Event publish latency distribution")
                .tag("bus", busName)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
        
        // Initialize gauges
        Gauge.builder("eventbus.subscribers.active", activeSubscribers, AtomicLong::get)
                .description("Number of active subscribers")
                .tag("bus", busName)
                .register(registry);
        
        Gauge.builder("eventbus.queue.depth", queueDepth, AtomicLong::get)
                .description("Current queue depth (ring buffer utilization)")
                .tag("bus", busName)
                .register(registry);
    }
    
    /**
     * Records a successful publish operation with latency.
     * 
     * @param latencyNanos the publish latency in nanoseconds
     */
    public void recordPublish(long latencyNanos) {
        publishedEvents.increment();
        publishLatency.record(latencyNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
    
    /**
     * Records a backpressure event.
     * This is called when the ring buffer watermark indicates high pressure.
     */
    public void recordBackpressureEvent() {
        backpressureEvents.increment();
    }
    
    /**
     * Records a dropped message.
     * This is called when a message cannot be published due to backpressure.
     */
    public void recordDroppedMessage() {
        droppedMessages.increment();
    }
    
    /**
     * Records a failed publish attempt.
     * This is called when publish fails for reasons other than backpressure.
     */
    public void recordFailedPublish() {
        failedPublishes.increment();
    }
    
    /**
     * Updates the active subscriber count.
     * 
     * @param count the current number of active subscribers
     */
    public void setActiveSubscribers(long count) {
        activeSubscribers.set(count);
    }
    
    /**
     * Updates the queue depth.
     * 
     * @param depth the current queue depth (0-100 representing percentage)
     */
    public void setQueueDepth(long depth) {
        queueDepth.set(depth);
    }
    
    /**
     * Gets the current count of published events.
     * 
     * @return total published events
     */
    public long getPublishedEventCount() {
        return (long) publishedEvents.count();
    }
    
    /**
     * Gets the current count of backpressure events.
     * 
     * @return total backpressure events
     */
    public long getBackpressureEventCount() {
        return (long) backpressureEvents.count();
    }
    
    /**
     * Gets the current count of dropped messages.
     * 
     * @return total dropped messages
     */
    public long getDroppedMessageCount() {
        return (long) droppedMessages.count();
    }
    
    /**
     * Gets the p99 latency in nanoseconds.
     * 
     * @return p99 latency in nanoseconds
     */
    public double getP99LatencyNanos() {
        HistogramSnapshot snapshot = publishLatency.takeSnapshot();
        var percentileValues = snapshot.percentileValues();
        for (var p : percentileValues) {
            if (p.percentile() == 0.99) {
                return p.value(java.util.concurrent.TimeUnit.NANOSECONDS);
            }
        }
        return 0.0;
    }
    
    /**
     * Gets the current active subscriber count.
     * 
     * @return active subscriber count
     */
    public long getActiveSubscribers() {
        return activeSubscribers.get();
    }
    
    /**
     * Gets the current queue depth.
     * 
     * @return queue depth percentage (0-100)
     */
    public long getQueueDepth() {
        return queueDepth.get();
    }
}
