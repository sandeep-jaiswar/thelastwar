package com.thelastwar.restgateway;

import com.thelastwar.eventbus.AeronEventBus;
import com.thelastwar.eventbus.EventBus;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.config.EnableWebFlux;

/**
 * REST Gateway application for reactive non-blocking API layer.
 * 
 * Provides:
 * - Spring WebFlux (Reactor Netty) for reactive REST endpoints
 * - JWT-based authentication
 * - Rate limiting (10K req/sec per user)
 * - EventBus integration for asynchronous order dispatch
 * - DSL-JSON for ultra-fast serialization
 * 
 * Performance targets:
 * - Throughput: ≥ 10K req/sec
 * - End-to-end latency: ≤ 2 ms at p99
 * - JSON serialization: < 10 µs per request
 */
@SpringBootApplication
@EnableWebFlux
public class RestGateway {
    
    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        SpringApplication.run(RestGateway.class, args);
    }
    
    /**
     * Provides EventBus bean.
     * Uses AeronEventBus for ultra-low latency IPC communication.
     */
    @Bean
    public EventBus eventBus() {
        // Use AeronEventBus for production-grade ultra-low latency
        AeronEventBus eventBus = new AeronEventBus();
        eventBus.start();
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                eventBus.stop();
            } catch (Exception e) {
                // Ignore shutdown errors
            }
        }));
        
        return eventBus;
    }
}

