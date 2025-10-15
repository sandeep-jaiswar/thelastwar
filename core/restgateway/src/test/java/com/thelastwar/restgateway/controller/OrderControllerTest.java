package com.thelastwar.restgateway.controller;

import com.thelastwar.eventbus.EventBus;
import com.thelastwar.restgateway.dto.OrderRequest;
import com.thelastwar.restgateway.service.OrderService;
import com.thelastwar.restgateway.test.TestEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Unit tests for OrderController.
 */
class OrderControllerTest {
    
    private OrderController orderController;
    private EventBus eventBus;
    
    @BeforeEach
    void setUp() {
        eventBus = new TestEventBus();
        eventBus.start();
        OrderService orderService = new OrderService(eventBus);
        orderController = new OrderController(orderService);
    }
    
    @Test
    void testSubmitOrderSuccess() {
        OrderRequest request = new OrderRequest("CLIENT-AUTO-AAPL", "AAPL", "BUY", "LIMIT", 100, 15000, 999);
        
        StepVerifier.create(orderController.submitOrder(request))
                .assertNext(response -> {
                    assert response.success();
                    assert response.data() != null;
                    assert response.data() > 0;
                })
                .verifyComplete();
    }
    
    @Test
    void testSubmitInvalidOrder() {
        OrderRequest request = new OrderRequest("CLIENT-AUTO-", "", "BUY", "LIMIT", 100, 15000, 999);
        
        StepVerifier.create(orderController.submitOrder(request))
                .assertNext(response -> {
                    assert !response.success();
                    assert response.message() != null;
                })
                .verifyComplete();
    }
    
    @Test
    void testGetOrderStatus() throws InterruptedException {
        // Submit an order first
        OrderRequest request = new OrderRequest("CLIENT-AUTO-AAPL", "AAPL", "BUY", "LIMIT", 100, 15000, 999);
        Long orderId = orderController.submitOrder(request).block().data();
        
        Thread.sleep(100);
        
        StepVerifier.create(orderController.getOrderStatus(orderId))
                .assertNext(response -> {
                    assert response.success();
                    assert response.data() != null;
                    assert response.data().orderId() == orderId;
                })
                .verifyComplete();
    }
    
    @Test
    void testGetNonExistentOrder() {
        StepVerifier.create(orderController.getOrderStatus(999999))
                .assertNext(response -> {
                    assert !response.success();
                })
                .verifyComplete();
    }
    
    @Test
    void testCancelOrder() throws InterruptedException {
        // Submit an order first
        OrderRequest request = new OrderRequest("CLIENT-AUTO-AAPL", "AAPL", "BUY", "LIMIT", 100, 15000, 999);
        Long orderId = orderController.submitOrder(request).block().data();
        
        Thread.sleep(100);
        
        StepVerifier.create(orderController.cancelOrder(orderId))
                .assertNext(response -> {
                    assert response.success();
                    assert response.data() != null;
                    assert response.data();
                })
                .verifyComplete();
    }
    
    @Test
    void testCancelNonExistentOrder() {
        StepVerifier.create(orderController.cancelOrder(999999))
                .assertNext(response -> {
                    assert !response.success();
                })
                .verifyComplete();
    }
}
