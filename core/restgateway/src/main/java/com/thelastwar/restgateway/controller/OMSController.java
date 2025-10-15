package com.thelastwar.restgateway.controller;

import com.thelastwar.oms.OMSService;
import com.thelastwar.restgateway.dto.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for OMS operations.
 * Provides endpoints for order submission, cancellation, and query.
 * 
 * All endpoints return correlation IDs for tracking and support idempotency.
 */
@RestController
@RequestMapping("/api/v1/oms")
public class OMSController {
    
    private final OMSService omsService;
    
    public OMSController(OMSService omsService) {
        this.omsService = omsService;
    }
    
    /**
     * Submit a new order.
     * POST /api/v1/oms/orders
     * 
     * @param request Order request with clientOrderId
     * @return Order submission response with correlation ID
     */
    @PostMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<OrderSubmissionResponse> submitOrder(@RequestBody OrderRequest request) {
        return Mono.fromCallable(() -> {
            // Validate request
            request.validate();
            
            // Generate correlation ID
            String correlationId = UUID.randomUUID().toString();
            
            // Submit order
            OMSService.SubmissionResult result = omsService.submitOrder(
                request.clientOrderId(),
                request.symbol(),
                request.getSideByte(),
                request.getOrderTypeByte(),
                request.quantity(),
                request.price(),
                request.account(),
                correlationId
            );
            
            if (result.success()) {
                return new OrderSubmissionResponse(
                    true,
                    result.orderId(),
                    request.clientOrderId(),
                    result.message(), // Use message from OMSService
                    result.correlationId(),
                    result.latencyNanos()
                );
            } else {
                return OrderSubmissionResponse.error(result.message(), result.correlationId());
            }
        });
    }
    
    /**
     * Cancel an order.
     * DELETE /api/v1/oms/orders/{clientOrderId}
     * 
     * @param clientOrderId Client order ID to cancel
     * @return Cancellation response with correlation ID
     */
    @DeleteMapping(value = "/orders/{clientOrderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<OrderCancellationResponse> cancelOrder(@PathVariable String clientOrderId) {
        return Mono.fromCallable(() -> {
            // Generate correlation ID
            String correlationId = UUID.randomUUID().toString();
            
            // Cancel order
            OMSService.CancellationResult result = omsService.cancelOrder(clientOrderId, correlationId);
            
            if (result.success()) {
                return new OrderCancellationResponse(
                    true,
                    result.orderId(),
                    clientOrderId,
                    result.message(), // Use message from OMSService
                    result.correlationId()
                );
            } else {
                return OrderCancellationResponse.error(result.message(), result.correlationId());
            }
        });
    }
    
    /**
     * Query order status.
     * GET /api/v1/oms/orders/{clientOrderId}
     * 
     * @param clientOrderId Client order ID to query
     * @return Order status response
     */
    @GetMapping(value = "/orders/{clientOrderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<OrderQueryResponse> queryOrder(@PathVariable String clientOrderId) {
        return Mono.fromCallable(() -> {
            // Query order
            OMSService.OrderStatus status = omsService.queryOrder(clientOrderId);
            
            if (status.found()) {
                return OrderQueryResponse.found(
                    status.orderId(),
                    clientOrderId,
                    status.currentState().name(),
                    status.isTerminal(),
                    status.correlationId()
                );
            } else {
                return OrderQueryResponse.notFound(status.message());
            }
        });
    }
    
    /**
     * Get OMS statistics.
     * GET /api/v1/oms/stats
     * 
     * @return OMS statistics
     */
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<OMSService.OMSStats> getStats() {
        return Mono.fromCallable(omsService::getStats);
    }
}
