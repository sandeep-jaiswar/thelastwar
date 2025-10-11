package com.thelastwar.restgateway.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Health check controller.
 */
@RestController
public class HealthController {
    
    /**
     * Health check endpoint.
     * GET /health
     * 
     * @return Health status
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of(
            "status", "UP",
            "timestamp", String.valueOf(System.currentTimeMillis())
        ));
    }
}
