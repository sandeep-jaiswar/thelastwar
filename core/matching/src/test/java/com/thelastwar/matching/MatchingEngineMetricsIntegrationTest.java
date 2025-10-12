package com.thelastwar.matching;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MatchingEngine with metrics collection.
 */
class MatchingEngineMetricsIntegrationTest {
    
    private InMemoryEventBus eventBus;
    private MatchingEngine engine;
    private MatchingEngineMetricsCollector metricsCollector;
    
    @BeforeEach
    void setUp() {
        eventBus = new InMemoryEventBus();
        eventBus.start();
        
        // Create metrics collector with Prometheus registry for testing
        MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        metricsCollector = new MatchingEngineMetricsCollector(registry, "test-integration");
        
        // Create matching engine with metrics (using the constructor that creates default risk validator)
        engine = new MatchingEngine(eventBus);
        // Replace the engine with one that has metrics
        engine.stop();
        
        // Create risk validator
        var riskValidator = new com.thelastwar.risk.CompositeRiskValidator.Builder()
            .add(new com.thelastwar.risk.CreditCheckModule())
            .add(new com.thelastwar.risk.MarginCheckModule())
            .add(new com.thelastwar.risk.FatFingerCheckModule())
            .build();
        
        engine = new MatchingEngine(eventBus, riskValidator, null, metricsCollector);
        engine.start();
    }
    
    @AfterEach
    void tearDown() {
        engine.stop();
        eventBus.stop();
    }
    
    @Test
    void testMetricsCollectionOnNewOrder() {
        // Initial state
        assertEquals(0, metricsCollector.getOrdersProcessedCount());
        assertEquals(0, metricsCollector.getTradesGeneratedCount());
        
        // Submit a buy order
        OrderEvent buyOrder = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        OrderEnvelope envelope = OrderEnvelope.wrap(buyOrder, 1L, SourceId.OMS);
        
        engine.onNewOrder(envelope);
        
        // Verify metrics
        assertEquals(1, metricsCollector.getOrdersProcessedCount());
        
        // No trade since there's no matching order
        assertEquals(0, metricsCollector.getTradesGeneratedCount());
        
        // Verify latency was recorded
        double p99 = metricsCollector.getP99MatchLatencyNanos();
        assertTrue(p99 >= 0);
    }
    
    @Test
    void testMetricsCollectionOnMatchingOrders() {
        // Submit a sell order first
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        OrderEnvelope sellEnvelope = OrderEnvelope.wrap(sellOrder, 1L, SourceId.OMS);
        
        engine.onNewOrder(sellEnvelope);
        
        // Submit a matching buy order
        OrderEvent buyOrder = OrderEvent.newOrder(
            2L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        OrderEnvelope buyEnvelope = OrderEnvelope.wrap(buyOrder, 2L, SourceId.OMS);
        
        engine.onNewOrder(buyEnvelope);
        
        // Verify metrics
        assertEquals(2, metricsCollector.getOrdersProcessedCount());
        assertEquals(1, metricsCollector.getTradesGeneratedCount());
    }
    
    @Test
    void testMetricsCollectionOnCancellation() {
        // Submit an order
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        OrderEnvelope envelope = OrderEnvelope.wrap(order, 1L, SourceId.OMS);
        
        engine.onNewOrder(envelope);
        
        // Cancel the order
        OrderCancel cancel = new OrderCancel(1L, "AAPL", System.nanoTime(), 999L, 1L);
        engine.onCancel(cancel);
        
        // Verify metrics
        assertEquals(1, metricsCollector.getOrdersCancelledCount());
    }
    
    @Test
    void testMetricsCollectionOnModification() {
        // Submit an order
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        OrderEnvelope envelope = OrderEnvelope.wrap(order, 1L, SourceId.OMS);
        
        engine.onNewOrder(envelope);
        
        // Modify the order
        OrderModify modify = OrderModify.modifyPrice(1L, "AAPL", 16000L, 999L, 1);
        engine.onReplace(modify);
        
        // Verify metrics
        assertEquals(1, metricsCollector.getOrdersModifiedCount());
    }
    
    @Test
    void testLatencyMeasurement() {
        // Process multiple orders to build latency distribution
        for (int i = 0; i < 10; i++) {
            OrderEvent order = OrderEvent.newOrder(
                1000L + i, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L + i, 999L, 1
            );
            
            OrderEnvelope envelope = OrderEnvelope.wrap(order, (long) i, SourceId.OMS);
            
            engine.onNewOrder(envelope);
        }
        
        // Verify latency metrics are available
        double p50 = metricsCollector.getP50MatchLatencyNanos();
        double p95 = metricsCollector.getP95MatchLatencyNanos();
        double p99 = metricsCollector.getP99MatchLatencyNanos();
        
        assertTrue(p50 >= 0, "p50 should be non-negative");
        assertTrue(p95 >= p50, "p95 should be >= p50");
        assertTrue(p99 >= p95, "p99 should be >= p95");
        
        // For this test, we expect latency to be well under 15 microseconds (15000 nanoseconds)
        // but we can't assert exact values due to system variance
        System.out.println("Match latency - p50: " + p50 + "ns, p95: " + p95 + "ns, p99: " + p99 + "ns");
    }
    
    @Test
    void testThroughputMeasurement() {
        // Process orders in batch
        int orderCount = 100;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < orderCount; i++) {
            OrderEvent order = OrderEvent.newOrder(
                2000L + i, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L + i, 999L, 1
            );
            
            OrderEnvelope envelope = OrderEnvelope.wrap(order, (long) i, SourceId.OMS);
            
            engine.onNewOrder(envelope);
        }
        
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        
        // Verify throughput
        assertEquals(orderCount, metricsCollector.getOrdersProcessedCount());
        
        double throughput = metricsCollector.getThroughput();
        assertEquals(orderCount, throughput, 0.001);
        
        // Calculate orders per second
        double ordersPerSecond = (orderCount * 1_000_000_000.0) / durationNanos;
        System.out.println("Throughput: " + ordersPerSecond + " orders/sec");
        System.out.println("Average latency: " + (durationNanos / orderCount) + " ns/order");
    }
    
    @Test
    void testPrometheusMetricsExport() {
        // Process some orders
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        OrderEnvelope envelope = OrderEnvelope.wrap(order, 1L, SourceId.OMS);
        
        engine.onNewOrder(envelope);
        
        // Export Prometheus metrics
        PrometheusMeterRegistry prometheusRegistry = (PrometheusMeterRegistry) metricsCollector.getRegistry();
        String metrics = prometheusRegistry.scrape();
        
        // Verify metrics are present
        assertNotNull(metrics);
        assertTrue(metrics.contains("matching_orders_processed_total"));
        assertTrue(metrics.contains("matching_latency_match"));
        
        System.out.println("Prometheus metrics sample:");
        System.out.println(metrics.substring(0, Math.min(500, metrics.length())));
    }
}
