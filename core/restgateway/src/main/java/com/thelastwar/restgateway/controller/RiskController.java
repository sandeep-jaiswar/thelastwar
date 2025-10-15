package com.thelastwar.restgateway.controller;

import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.oms.OMSService;
import com.thelastwar.oms.RiskCheckService;
import com.thelastwar.restgateway.dto.RiskCheckRequest;
import com.thelastwar.restgateway.dto.RiskCheckResponse;
import com.thelastwar.restgateway.dto.RiskMetricsResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for risk check operations.
 * Provides endpoints for pre-trade risk validation and metrics.
 */
@RestController
@RequestMapping("/api/v1/risk")
public class RiskController {
    
    private final OMSService omsService;
    
    public RiskController(OMSService omsService) {
        this.omsService = omsService;
    }
    
    /**
     * Perform synchronous pre-trade risk check.
     * POST /api/v1/risk/check
     * 
     * @param request Risk check request
     * @return Risk check response with decision
     */
    @PostMapping(value = "/check", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RiskCheckResponse> checkRisk(@RequestBody RiskCheckRequest request) {
        return Mono.fromCallable(() -> {
            // Validate request
            request.validate();
            
            // Generate correlation ID
            String correlationId = UUID.randomUUID().toString();
            
            // Create OrderEvent for risk check
            OrderEvent orderEvent = OrderEvent.newOrder(
                1L,  // temporary orderId for risk check (actual order not yet created)
                request.symbol(),
                request.getSideByte(),
                request.getOrderTypeByte(),
                request.quantity(),
                request.price(),
                request.account(),
                1  // status
            );
            
            // Perform risk check through OMS service
            // Note: We're accessing the risk check service through a hypothetical method
            // For now, we'll create a standalone risk check service
            RiskCheckService.RiskCheckResult result = getRiskCheckService().check(orderEvent);
            
            return new RiskCheckResponse(
                result.approved(),
                result.decision().reasonCode(),
                result.decision().message(),
                correlationId,
                result.latencyNanos(),
                result.serviceAvailable()
            );
        });
    }
    
    /**
     * Get risk check metrics.
     * GET /api/v1/risk/metrics
     * 
     * @return Risk metrics
     */
    @GetMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RiskMetricsResponse> getMetrics() {
        return Mono.fromCallable(() -> {
            RiskCheckService.RiskMetrics metrics = omsService.getRiskMetrics();
            
            return new RiskMetricsResponse(
                metrics.getTotalChecks(),
                metrics.getApprovedCount(),
                metrics.getRejectedCount(),
                metrics.getFailureCount(),
                metrics.getApprovalRate(),
                metrics.getRejectionRate(),
                metrics.getFailureRate(),
                metrics.getAverageLatencyNanos(),
                metrics.getMaxLatencyNanos()
            );
        });
    }
    
    /**
     * Gets the risk check service from OMS.
     */
    private RiskCheckService getRiskCheckService() {
        return omsService.getRiskCheckService();
    }
}
