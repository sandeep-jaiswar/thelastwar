package com.thelastwar.restgateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFlux filter for JWT authentication.
 * Validates JWT tokens on incoming requests with minimal latency overhead.
 */
@Component
public class JwtAuthenticationFilter implements WebFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // Skip authentication for health check endpoint
        if (path.equals("/health")) {
            return chain.filter(exchange);
        }
        
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        
        String token = authHeader.substring(7);
        
        if (!jwtTokenProvider.validateToken(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        
        // Add username to request attributes for downstream use
        String username = jwtTokenProvider.getUsername(token);
        exchange.getAttributes().put("username", username);
        
        return chain.filter(exchange);
    }
}
