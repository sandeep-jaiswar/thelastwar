package com.thelastwar.restgateway.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebFlux filter for rate limiting using Bucket4j.
 * Implements token bucket algorithm for efficient rate limiting.
 */
@Component
public class RateLimitFilter implements WebFilter {
    
    // Rate limit: 10,000 requests per second per user
    private static final long CAPACITY = 10000;
    private static final Duration REFILL_DURATION = Duration.ofSeconds(1);
    
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // Skip rate limiting for health check endpoint
        if (path.equals("/health")) {
            return chain.filter(exchange);
        }
        
        // Get username from attributes (set by JWT filter)
        String username = exchange.getAttribute("username");
        if (username == null) {
            username = "anonymous";
        }
        
        Bucket bucket = buckets.computeIfAbsent(username, this::createBucket);
        
        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        } else {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
    }
    
    /**
     * Creates a new bucket with the configured rate limit.
     */
    private Bucket createBucket(String username) {
        Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.intervally(CAPACITY, REFILL_DURATION));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
