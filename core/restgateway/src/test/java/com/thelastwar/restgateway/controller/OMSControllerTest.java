package com.thelastwar.restgateway.controller;

import com.thelastwar.eventbus.EventBus;
import com.thelastwar.oms.OMSService;
import com.thelastwar.oms.OrderState;
import com.thelastwar.restgateway.dto.*;
import com.thelastwar.restgateway.test.TestEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OMSController.
 */
class OMSControllerTest {
    
    private EventBus eventBus;
    private OMSService omsService;
    private OMSController omsController;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() throws Exception {
        eventBus = new TestEventBus();
        eventBus.start();
        
        Path commandLogPath = tempDir.resolve("oms-commands.log");
        omsService = new OMSService(eventBus, commandLogPath);
        omsController = new OMSController(omsService);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (omsService != null) {
            omsService.close();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
    }
    
    @Test
    void testSubmitOrder_Success() {
        OrderRequest request = new OrderRequest(
            "CLIENT-API-001", "AAPL", "BUY", "LIMIT", 100, 15000, 999
        );
        
        StepVerifier.create(omsController.submitOrder(request))
            .assertNext(response -> {
                assertTrue(response.success());
                assertTrue(response.orderId() > 0);
                assertEquals("CLIENT-API-001", response.clientOrderId());
                assertNotNull(response.correlationId());
                assertTrue(response.latencyNanos() > 0);
                assertEquals("Order submitted successfully", response.message());
            })
            .verifyComplete();
    }
    
    @Test
    void testSubmitOrder_Idempotency() {
        OrderRequest request = new OrderRequest(
            "CLIENT-API-002", "MSFT", "SELL", "LIMIT", 50, 30000, 999
        );
        
        // First submission
        OrderSubmissionResponse response1 = omsController.submitOrder(request).block();
        assertNotNull(response1);
        assertTrue(response1.success(), "First submission should succeed: " + response1.message());
        long orderId = response1.orderId();
        
        // Second submission with same clientOrderId (idempotent)
        OrderSubmissionResponse response2 = omsController.submitOrder(request).block();
        assertNotNull(response2);
        assertTrue(response2.success(), "Second submission should succeed (idempotent): " + response2.message());
        assertEquals(orderId, response2.orderId(), "Order IDs should match");
        assertTrue(response2.message().contains("idempotent") || response2.message().contains("already"), 
            "Message should indicate idempotency: " + response2.message());
    }
    
    @Test
    void testSubmitOrder_ValidationError() {
        OrderRequest request = new OrderRequest(
            "", "AAPL", "BUY", "LIMIT", 100, 15000, 999
        );
        
        StepVerifier.create(omsController.submitOrder(request))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
    
    @Test
    void testCancelOrder_Success() {
        // Submit order first
        OrderRequest request = new OrderRequest(
            "CLIENT-API-003", "AAPL", "BUY", "LIMIT", 100, 15000, 999
        );
        OrderSubmissionResponse submitResponse = omsController.submitOrder(request).block();
        assertNotNull(submitResponse);
        assertTrue(submitResponse.success());
        
        // Cancel order
        StepVerifier.create(omsController.cancelOrder("CLIENT-API-003"))
            .assertNext(response -> {
                assertTrue(response.success());
                assertEquals(submitResponse.orderId(), response.orderId());
                assertEquals("CLIENT-API-003", response.clientOrderId());
                assertNotNull(response.correlationId());
                assertEquals("Order cancelled successfully", response.message());
            })
            .verifyComplete();
    }
    
    @Test
    void testCancelOrder_NotFound() {
        StepVerifier.create(omsController.cancelOrder("NON-EXISTENT"))
            .assertNext(response -> {
                assertFalse(response.success());
                assertEquals("Order not found", response.message());
            })
            .verifyComplete();
    }
    
    @Test
    void testQueryOrder_Found() {
        // Submit order first
        OrderRequest request = new OrderRequest(
            "CLIENT-API-004", "TSLA", "BUY", "MARKET", 10, 0, 999
        );
        OrderSubmissionResponse submitResponse = omsController.submitOrder(request).block();
        assertNotNull(submitResponse);
        assertTrue(submitResponse.success());
        
        // Query order
        StepVerifier.create(omsController.queryOrder("CLIENT-API-004"))
            .assertNext(response -> {
                assertTrue(response.found());
                assertEquals(submitResponse.orderId(), response.orderId());
                assertEquals("CLIENT-API-004", response.clientOrderId());
                assertEquals(OrderState.NEW.name(), response.state());
                assertFalse(response.isTerminal());
            })
            .verifyComplete();
    }
    
    @Test
    void testQueryOrder_NotFound() {
        StepVerifier.create(omsController.queryOrder("NON-EXISTENT"))
            .assertNext(response -> {
                assertFalse(response.found());
                assertEquals("Order not found", response.message());
            })
            .verifyComplete();
    }
    
    @Test
    void testGetStats() {
        // Submit a few orders
        for (int i = 0; i < 5; i++) {
            OrderRequest request = new OrderRequest(
                "CLIENT-API-STATS-" + i, "AAPL", "BUY", "LIMIT", 100, 15000, 999
            );
            omsController.submitOrder(request).block();
        }
        
        // Get stats
        StepVerifier.create(omsController.getStats())
            .assertNext(stats -> {
                assertEquals(5, stats.totalCommands());
                assertEquals(5, stats.activeOrders());
                assertEquals(5, stats.totalOrders());
            })
            .verifyComplete();
    }
    
    @Test
    void testLatencyRequirement() {
        OrderRequest request = new OrderRequest(
            "CLIENT-API-LATENCY", "AAPL", "BUY", "LIMIT", 100, 15000, 999
        );
        
        OrderSubmissionResponse response = omsController.submitOrder(request).block();
        assertNotNull(response);
        assertTrue(response.success());
        
        // Verify latency is within 2ms (2,000,000 nanoseconds)
        long latencyNanos = response.latencyNanos();
        assertTrue(latencyNanos < 2_000_000, 
            "Latency " + latencyNanos + " ns exceeds 2ms requirement");
    }
}
