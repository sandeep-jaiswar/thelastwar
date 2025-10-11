package com.thelastwar.restgateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT token provider for authentication.
 * Provides token generation and validation with minimal latency overhead.
 */
@Component
public class JwtTokenProvider {
    
    private final SecretKey secretKey;
    private final long validityInMilliseconds = 3600000; // 1 hour
    
    /**
     * Constructor initializes the secret key.
     * In production, this should come from secure configuration.
     */
    public JwtTokenProvider() {
        // For demo purposes - in production use proper key management
        String secret = "thelastwar-secret-key-must-be-at-least-256-bits-long-for-hs256";
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Creates a JWT token for the given username.
     */
    public String createToken(String username) {
        Claims claims = Jwts.claims().subject(username).build();
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);
        
        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(validity)
                .signWith(secretKey)
                .compact();
    }
    
    /**
     * Validates a JWT token.
     * 
     * @param token JWT token to validate
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Extracts username from JWT token.
     */
    public String getUsername(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
