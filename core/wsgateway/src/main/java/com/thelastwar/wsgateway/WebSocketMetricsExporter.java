package com.thelastwar.wsgateway;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Utility class to expose metrics from WebSocket gateway.
 * Provides a simple way to scrape Prometheus metrics.
 */
public class WebSocketMetricsExporter {
    
    private final WebSocketGatewayMetrics metrics;
    private final int port;
    
    /**
     * Creates a new metrics exporter.
     * 
     * @param metrics WebSocket gateway metrics instance
     * @param port Port to bind the metrics endpoint to
     */
    public WebSocketMetricsExporter(WebSocketGatewayMetrics metrics, int port) {
        this.metrics = metrics;
        this.port = port;
    }
    
    /**
     * Gets Prometheus-formatted metrics string.
     * 
     * @return Prometheus metrics text
     */
    public String scrape() {
        MeterRegistry registry = metrics.getRegistry();
        if (registry instanceof PrometheusMeterRegistry prometheusRegistry) {
            return prometheusRegistry.scrape();
        }
        return "# Metrics not available - PrometheusMeterRegistry not configured\n";
    }
    
    /**
     * Gets the configured port.
     * 
     * @return port number
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Gets the metrics instance.
     * 
     * @return WebSocketGatewayMetrics instance
     */
    public WebSocketGatewayMetrics getMetrics() {
        return metrics;
    }
}
