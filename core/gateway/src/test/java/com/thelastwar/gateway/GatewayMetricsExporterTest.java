package com.thelastwar.gateway;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayMetricsExporterTest {

    @Test
    void testScrapeWithPrometheusRegistry() {
        // Given
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        GatewayMetrics metrics = new GatewayMetrics(registry, "test-gateway");
        GatewayMetricsExporter exporter = new GatewayMetricsExporter(metrics, 8081);
        
        // Record some metrics
        metrics.recordInboundMessage();
        metrics.recordOutboundMessage();
        
        // When
        String scraped = exporter.scrape();
        
        // Then
        assertNotNull(scraped);
        assertTrue(scraped.contains("# HELP"));
        assertTrue(scraped.contains("# TYPE"));
        assertTrue(scraped.contains("gateway_messages_inbound"));
        assertTrue(scraped.contains("gateway_messages_outbound"));
    }
    
    @Test
    void testGetPort() {
        // Given
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        GatewayMetrics metrics = new GatewayMetrics(registry, "test-gateway");
        GatewayMetricsExporter exporter = new GatewayMetricsExporter(metrics, 8081);
        
        // When
        int port = exporter.getPort();
        
        // Then
        assertEquals(8081, port);
    }
    
    @Test
    void testGetMetrics() {
        // Given
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        GatewayMetrics metrics = new GatewayMetrics(registry, "test-gateway");
        GatewayMetricsExporter exporter = new GatewayMetricsExporter(metrics, 8081);
        
        // When
        GatewayMetrics retrievedMetrics = exporter.getMetrics();
        
        // Then
        assertNotNull(retrievedMetrics);
        assertSame(metrics, retrievedMetrics);
    }
}
