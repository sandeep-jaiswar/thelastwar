package com.thelastwar.gateway;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Utility class to expose metrics from gateway components.
 * Provides a simple HTTP server to export Prometheus metrics on /metrics endpoint.
 */
public class GatewayMetricsExporter {
    
    private final GatewayMetrics metrics;
    private final int port;
    
    /**
     * Creates a new metrics exporter.
     * 
     * @param metrics Gateway metrics instance
     * @param port Port to bind the HTTP server to
     */
    public GatewayMetricsExporter(GatewayMetrics metrics, int port) {
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
     * @return GatewayMetrics instance
     */
    public GatewayMetrics getMetrics() {
        return metrics;
    }
}
