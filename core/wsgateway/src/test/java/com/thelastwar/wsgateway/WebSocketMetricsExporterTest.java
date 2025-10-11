package com.thelastwar.wsgateway;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketMetricsExporterTest {

    @Test
    void testScrapeWithPrometheusRegistry() {
        // Given
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        WebSocketGatewayMetrics metrics = new WebSocketGatewayMetrics(registry, "test-ws-gateway");
        WebSocketMetricsExporter exporter = new WebSocketMetricsExporter(metrics, 8082);
        
        // Record some metrics
        metrics.recordInboundMessage();
        metrics.recordOutboundMessage();
        metrics.recordConnectionOpened();
        
        // When
        String scraped = exporter.scrape();
        
        // Then
        assertNotNull(scraped);
        assertTrue(scraped.contains("# HELP"));
        assertTrue(scraped.contains("# TYPE"));
        assertTrue(scraped.contains("wsgateway_messages_inbound"));
        assertTrue(scraped.contains("wsgateway_messages_outbound"));
        assertTrue(scraped.contains("wsgateway_clients_concurrent"));
    }
    
    @Test
    void testGetPort() {
        // Given
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        WebSocketGatewayMetrics metrics = new WebSocketGatewayMetrics(registry, "test-ws-gateway");
        WebSocketMetricsExporter exporter = new WebSocketMetricsExporter(metrics, 8082);
        
        // When
        int port = exporter.getPort();
        
        // Then
        assertEquals(8082, port);
    }
    
    @Test
    void testGetMetrics() {
        // Given
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        WebSocketGatewayMetrics metrics = new WebSocketGatewayMetrics(registry, "test-ws-gateway");
        WebSocketMetricsExporter exporter = new WebSocketMetricsExporter(metrics, 8082);
        
        // When
        WebSocketGatewayMetrics retrievedMetrics = exporter.getMetrics();
        
        // Then
        assertNotNull(retrievedMetrics);
        assertSame(metrics, retrievedMetrics);
    }
}
