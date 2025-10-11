package com.thelastwar.restgateway.controller;

import com.thelastwar.restgateway.dto.ApiResponse;
import com.thelastwar.restgateway.dto.OrderRequest;
import com.thelastwar.restgateway.dto.OrderStatusResponse;
import com.thelastwar.restgateway.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller for order operations.
 * Provides non-blocking reactive endpoints for order submission, status queries, and cancellations.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    private final OrderService orderService;
    
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    /**
     * Submit a new order.
     * POST /api/orders
     * 
     * @param request Order request
     * @return Order ID
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Long>> submitOrder(@RequestBody OrderRequest request) {
        return orderService.submitOrder(request)
                .map(orderId -> ApiResponse.success("Order submitted successfully", orderId))
                .onErrorResume(e -> Mono.just(ApiResponse.error(e.getMessage())));
    }
    
    /**
     * Get order status by ID.
     * GET /api/orders/{id}
     * 
     * @param id Order ID
     * @return Order status
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<OrderStatusResponse>> getOrderStatus(@PathVariable("id") long id) {
        return orderService.getOrderStatus(id)
                .map(status -> {
                    if (status == null) {
                        return ApiResponse.<OrderStatusResponse>error("Order not found");
                    }
                    return ApiResponse.success(status);
                })
                .defaultIfEmpty(ApiResponse.error("Order not found"))
                .onErrorResume(e -> Mono.just(ApiResponse.error(e.getMessage())));
    }
    
    /**
     * Cancel an order.
     * POST /api/orders/{id}/cancel
     * 
     * @param id Order ID
     * @return Success status
     */
    @PostMapping(value = "/{id}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Boolean>> cancelOrder(@PathVariable("id") long id) {
        return orderService.cancelOrder(id)
                .map(success -> {
                    if (success) {
                        return ApiResponse.success("Order cancelled successfully", true);
                    }
                    return ApiResponse.<Boolean>error("Order not found or already in terminal state");
                })
                .onErrorResume(e -> Mono.just(ApiResponse.error(e.getMessage())));
    }
}
