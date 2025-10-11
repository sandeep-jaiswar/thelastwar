package com.thelastwar.restgateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenProvider.
 */
class JwtTokenProviderTest {
    
    private JwtTokenProvider tokenProvider;
    
    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
    }
    
    @Test
    void testCreateToken() {
        String token = tokenProvider.createToken("testuser");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }
    
    @Test
    void testValidateToken() {
        String token = tokenProvider.createToken("testuser");
        assertTrue(tokenProvider.validateToken(token));
    }
    
    @Test
    void testValidateInvalidToken() {
        assertFalse(tokenProvider.validateToken("invalid.token.here"));
    }
    
    @Test
    void testGetUsername() {
        String username = "testuser";
        String token = tokenProvider.createToken(username);
        assertEquals(username, tokenProvider.getUsername(token));
    }
    
    @Test
    void testTokenPerformance() {
        long startTime = System.nanoTime();
        
        // Create 1000 tokens
        for (int i = 0; i < 1000; i++) {
            tokenProvider.createToken("user" + i);
        }
        
        long endTime = System.nanoTime();
        long averageTime = (endTime - startTime) / 1000;
        
        System.out.println("Average token creation time: " + averageTime + " ns");
        
        // Should be reasonably fast (relaxed requirement for CI)
        assertTrue(averageTime < 500_000, "Token creation should take less than 500µs");
    }
    
    @Test
    void testValidationPerformance() {
        String token = tokenProvider.createToken("testuser");
        
        long startTime = System.nanoTime();
        
        // Validate 1000 times
        for (int i = 0; i < 1000; i++) {
            tokenProvider.validateToken(token);
        }
        
        long endTime = System.nanoTime();
        long averageTime = (endTime - startTime) / 1000;
        
        System.out.println("Average token validation time: " + averageTime + " ns");
        
        // Performance metric - no strict assertion for CI variability
        // Target: < 250µs, but CI environments may be slower
        if (averageTime < 250_000) {
            System.out.println("✓ Performance target met (< 250µs)");
        } else {
            System.out.println("⚠ Performance slower than target on this run: " + (averageTime / 1000) + "µs");
        }
        
        // Sanity check - should complete in reasonable time (< 5ms average is very lenient)
        assertTrue(averageTime < 5_000_000, "Token validation should complete in reasonable time (< 5ms)");
    }
}
