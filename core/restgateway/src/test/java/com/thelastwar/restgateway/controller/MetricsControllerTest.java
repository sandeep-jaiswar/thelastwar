package com.thelastwar.restgateway.controller;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class MetricsControllerTest {

    @Test
    void testMetricsEndpointWithPrometheusRegistry() {
        // Given
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        // Register a test counter to ensure there's some metrics output
        registry.counter("test_metric", "type", "test").increment();
        MetricsController controller = new MetricsController(registry);
        
        // When
        Mono<String> result = controller.metrics();
        
        // Then
        StepVerifier.create(result)
            .assertNext(metrics -> {
                assertNotNull(metrics);
                assertFalse(metrics.isEmpty());
                // Prometheus format may or may not have HELP/TYPE depending on metrics
                // Just verify it returns something and not the error message
                assertFalse(metrics.contains("Metrics not available"));
            })
            .verifyComplete();
    }
    
    @Test
    void testMetricsEndpointWithSimpleRegistry() {
        // Given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsController controller = new MetricsController(registry);
        
        // When
        Mono<String> result = controller.metrics();
        
        // Then
        StepVerifier.create(result)
            .assertNext(metrics -> {
                assertNotNull(metrics);
                assertTrue(metrics.contains("Metrics not available"));
            })
            .verifyComplete();
    }
    
    @Test
    void testMetricsContentType() {
        // Given
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MetricsController controller = new MetricsController(registry);
        
        // Register a test counter
        registry.counter("test_counter", "type", "test").increment();
        
        // When
        Mono<String> result = controller.metrics();
        
        // Then
        StepVerifier.create(result)
            .assertNext(metrics -> {
                assertNotNull(metrics);
                // Verify Prometheus text format
                assertTrue(metrics.contains("# HELP") || metrics.contains("# TYPE"));
            })
            .verifyComplete();
    }
}
