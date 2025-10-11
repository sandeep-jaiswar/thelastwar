package com.thelastwar.restgateway.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Metrics endpoint controller.
 * Exposes Prometheus metrics for scraping.
 */
@RestController
public class MetricsController {
    
    private final MeterRegistry meterRegistry;
    
    @Autowired
    public MetricsController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Metrics endpoint for Prometheus scraping.
     * GET /metrics
     * 
     * @return Prometheus-formatted metrics
     */
    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> metrics() {
        if (meterRegistry instanceof PrometheusMeterRegistry prometheusRegistry) {
            return Mono.just(prometheusRegistry.scrape());
        }
        return Mono.just("# Metrics not available - PrometheusMeterRegistry not configured\n");
    }
}
