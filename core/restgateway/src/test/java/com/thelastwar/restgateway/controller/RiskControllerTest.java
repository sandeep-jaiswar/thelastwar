package com.thelastwar.restgateway.controller;

import com.thelastwar.eventbus.EventBus;
import com.thelastwar.oms.OMSService;
import com.thelastwar.oms.RiskCheckService;
import com.thelastwar.restgateway.dto.RiskCheckRequest;
import com.thelastwar.restgateway.dto.RiskCheckResponse;
import com.thelastwar.restgateway.dto.RiskMetricsResponse;
import com.thelastwar.risk.CompositeRiskValidator;
import com.thelastwar.risk.CreditCheckModule;
import com.thelastwar.risk.RiskDecision;
import com.thelastwar.risk.RiskReasonCode;
import com.thelastwar.risk.RiskValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RiskController REST API.
 */
class RiskControllerTest {
    
    private RiskController riskController;
    private OMSService omsService;
    private EventBus eventBus;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() throws Exception {
        // Create a mock EventBus
        eventBus = new MockEventBus();
        
        // Create OMS service with default risk validator
        omsService = new OMSService(eventBus, tempDir.resolve("commands.log"));
        
        // Create controller
        riskController = new RiskController(omsService);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (omsService != null) {
            omsService.close();
        }
    }
    
    @Test
    void testRiskCheck_Approved() {
        // Create request that will be approved
        RiskCheckRequest request = new RiskCheckRequest(
            "AAPL",
            "BUY",
            "LIMIT",
            100L,
            5000L,  // notional = 100 * 5000 = 500,000 (within default limit)
            999L
        );
        
        // Call risk check endpoint
        StepVerifier.create(riskController.checkRisk(request))
            .assertNext(response -> {
                assertTrue(response.approved());
                assertEquals(0, response.reasonCode());
                assertNull(response.message());
                assertNotNull(response.correlationId());
                assertTrue(response.latencyNanos() >= 0);
                assertTrue(response.serviceAvailable());
            })
            .verifyComplete();
    }
    
    @Test
    void testRiskCheck_Rejected() throws Exception {
        // Close existing OMS and create new one with stricter risk limits
        omsService.close();
        
        // Create OMS with lower credit limit
        RiskValidator validator = new CompositeRiskValidator.Builder()
            .add(new CreditCheckModule(100_000L, true)) // Max notional: 100K
            .build();
        RiskCheckService riskService = new RiskCheckService(
            validator,
            RiskCheckService.RiskCheckConfig.defaultConfig()
        );
        omsService = new OMSService(eventBus, tempDir.resolve("commands2.log"), riskService);
        riskController = new RiskController(omsService);
        
        // Create request that will be rejected
        RiskCheckRequest request = new RiskCheckRequest(
            "AAPL",
            "BUY",
            "LIMIT",
            100L,
            5000L,  // notional = 100 * 5000 = 500,000 (exceeds 100K limit)
            999L
        );
        
        // Call risk check endpoint
        StepVerifier.create(riskController.checkRisk(request))
            .assertNext(response -> {
                assertFalse(response.approved());
                assertEquals(RiskReasonCode.CREDIT_LIMIT_EXCEEDED, response.reasonCode());
                assertNotNull(response.message());
                assertTrue(response.message().contains("exceeds limit"));
                assertNotNull(response.correlationId());
                assertTrue(response.latencyNanos() >= 0);
                assertTrue(response.serviceAvailable());
            })
            .verifyComplete();
    }
    
    @Test
    void testRiskCheck_ValidationError() {
        // Create invalid request (missing symbol)
        RiskCheckRequest request = new RiskCheckRequest(
            null,  // Invalid: null symbol
            "BUY",
            "LIMIT",
            100L,
            5000L,
            999L
        );
        
        // Call risk check endpoint - should throw validation error
        StepVerifier.create(riskController.checkRisk(request))
            .expectError(IllegalArgumentException.class)
            .verify();
    }
    
    @Test
    void testGetMetrics_InitialState() {
        // Get metrics before any risk checks
        StepVerifier.create(riskController.getMetrics())
            .assertNext(response -> {
                assertEquals(0, response.totalChecks());
                assertEquals(0, response.approvedCount());
                assertEquals(0, response.rejectedCount());
                assertEquals(0, response.failureCount());
                assertEquals(0.0, response.approvalRate(), 0.001);
                assertEquals(0.0, response.rejectionRate(), 0.001);
                assertEquals(0.0, response.failureRate(), 0.001);
                assertEquals(0, response.averageLatencyNanos());
                assertEquals(0, response.maxLatencyNanos());
            })
            .verifyComplete();
    }
    
    @Test
    void testGetMetrics_AfterRiskChecks() {
        // Perform several risk checks
        RiskCheckRequest request1 = new RiskCheckRequest("AAPL", "BUY", "LIMIT", 100L, 5000L, 999L);
        RiskCheckRequest request2 = new RiskCheckRequest("AAPL", "BUY", "LIMIT", 100L, 5000L, 999L);
        RiskCheckRequest request3 = new RiskCheckRequest("AAPL", "BUY", "LIMIT", 100L, 5000L, 999L);
        
        // Execute checks
        riskController.checkRisk(request1).block();
        riskController.checkRisk(request2).block();
        riskController.checkRisk(request3).block();
        
        // Get metrics
        StepVerifier.create(riskController.getMetrics())
            .assertNext(response -> {
                assertEquals(3, response.totalChecks());
                assertEquals(3, response.approvedCount());
                assertEquals(0, response.rejectedCount());
                assertEquals(0, response.failureCount());
                assertEquals(1.0, response.approvalRate(), 0.001);
                assertEquals(0.0, response.rejectionRate(), 0.001);
                assertTrue(response.averageLatencyNanos() > 0);
                assertTrue(response.maxLatencyNanos() > 0);
            })
            .verifyComplete();
    }
    
    @Test
    void testRiskCheck_FailOpen() throws Exception {
        // Close existing OMS and create new one with fail-open config
        omsService.close();
        
        // Create validator that throws exception
        RiskValidator throwingValidator = order -> {
            throw new RuntimeException("Service unavailable");
        };
        RiskCheckService riskService = new RiskCheckService(
            throwingValidator,
            RiskCheckService.RiskCheckConfig.failOpen()
        );
        omsService = new OMSService(eventBus, tempDir.resolve("commands3.log"), riskService);
        riskController = new RiskController(omsService);
        
        // Create request
        RiskCheckRequest request = new RiskCheckRequest(
            "AAPL",
            "BUY",
            "LIMIT",
            100L,
            5000L,
            999L
        );
        
        // Call risk check endpoint
        StepVerifier.create(riskController.checkRisk(request))
            .assertNext(response -> {
                // Fail-open: should be approved despite service failure
                assertTrue(response.approved());
                assertEquals(0, response.reasonCode());
                assertFalse(response.serviceAvailable());
            })
            .verifyComplete();
    }
    
    @Test
    void testRiskCheck_FailClosed() throws Exception {
        // Close existing OMS and create new one with fail-closed config
        omsService.close();
        
        // Create validator that throws exception
        RiskValidator throwingValidator = order -> {
            throw new RuntimeException("Service unavailable");
        };
        RiskCheckService riskService = new RiskCheckService(
            throwingValidator,
            RiskCheckService.RiskCheckConfig.failClosed()
        );
        omsService = new OMSService(eventBus, tempDir.resolve("commands4.log"), riskService);
        riskController = new RiskController(omsService);
        
        // Create request
        RiskCheckRequest request = new RiskCheckRequest(
            "AAPL",
            "BUY",
            "LIMIT",
            100L,
            5000L,
            999L
        );
        
        // Call risk check endpoint
        StepVerifier.create(riskController.checkRisk(request))
            .assertNext(response -> {
                // Fail-closed: should be rejected due to service failure
                assertFalse(response.approved());
                assertEquals(999, response.reasonCode());
                assertTrue(response.message().contains("Risk service unavailable"));
                assertFalse(response.serviceAvailable());
            })
            .verifyComplete();
    }
    
    // Simple mock EventBus for testing
    private static class MockEventBus implements EventBus {
        @Override
        public boolean publish(com.thelastwar.eventbus.Event event) {
            return true;
        }
        
        @Override
        public Subscription subscribe(int eventType, com.thelastwar.eventbus.EventHandler<?> handler) {
            return new Subscription() {
                @Override
                public void unsubscribe() {}
                
                @Override
                public boolean isActive() {
                    return false;
                }
            };
        }
        
        @Override
        public long getPublishedEventCount() {
            return 0;
        }
        
        @Override
        public int getSubscriberCount(int eventType) {
            return 0;
        }
        
        @Override
        public void start() {}
        
        @Override
        public void stop() {}
    }
}
