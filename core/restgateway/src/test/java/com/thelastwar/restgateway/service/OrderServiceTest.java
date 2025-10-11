package com.thelastwar.restgateway.service;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.restgateway.dto.OrderRequest;
import com.thelastwar.restgateway.dto.OrderStatusResponse;
import com.thelastwar.restgateway.test.TestEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderService.
 */
class OrderServiceTest {
    
    private EventBus eventBus;
    private OrderService orderService;
    
    @BeforeEach
    void setUp() {
        eventBus = new TestEventBus();
        eventBus.start();
        orderService = new OrderService(eventBus);
    }
    
    @Test
    void testSubmitOrder() {
        OrderRequest request = new OrderRequest("AAPL", "BUY", "LIMIT", 100, 15000, 999);
        
        StepVerifier.create(orderService.submitOrder(request))
                .assertNext(orderId -> {
                    assertNotNull(orderId);
                    assertTrue(orderId > 0);
                })
                .verifyComplete();
    }
    
    @Test
    void testSubmitInvalidOrder() {
        OrderRequest request = new OrderRequest("", "BUY", "LIMIT", 100, 15000, 999);
        
        StepVerifier.create(orderService.submitOrder(request))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
    
    @Test
    void testGetOrderStatus() throws InterruptedException {
        OrderRequest request = new OrderRequest("AAPL", "BUY", "LIMIT", 100, 15000, 999);
        
        // Submit order
        Long orderId = orderService.submitOrder(request).block();
        assertNotNull(orderId);
        
        // Give time for the order to be processed
        Thread.sleep(100);
        
        // Get status
        StepVerifier.create(orderService.getOrderStatus(orderId))
                .assertNext(status -> {
                    assertNotNull(status);
                    assertEquals(orderId, status.orderId());
                    assertEquals("AAPL", status.symbol());
                    assertEquals("BUY", status.side());
                    assertEquals("LIMIT", status.orderType());
                })
                .verifyComplete();
    }
    
    @Test
    void testGetOrderStatusNotFound() {
        StepVerifier.create(orderService.getOrderStatus(999999))
                .expectNextCount(0)
                .verifyComplete();
    }
    
    @Test
    void testCancelOrder() throws InterruptedException {
        OrderRequest request = new OrderRequest("AAPL", "BUY", "LIMIT", 100, 15000, 999);
        
        // Submit order
        Long orderId = orderService.submitOrder(request).block();
        assertNotNull(orderId);
        
        // Give time for the order to be processed
        Thread.sleep(100);
        
        // Cancel order
        StepVerifier.create(orderService.cancelOrder(orderId))
                .assertNext(success -> assertTrue(success))
                .verifyComplete();
    }
    
    @Test
    void testCancelNonExistentOrder() {
        StepVerifier.create(orderService.cancelOrder(999999))
                .assertNext(success -> assertFalse(success))
                .verifyComplete();
    }
    
    @Test
    void testOrderEventPublished() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        
        // Subscribe to order submitted events
        eventBus.subscribe(EventType.ORDER_SUBMITTED, event -> {
            latch.countDown();
        });
        
        OrderRequest request = new OrderRequest("AAPL", "BUY", "LIMIT", 100, 15000, 999);
        orderService.submitOrder(request).block();
        
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Event should be published");
    }
    
    @Test
    void testMultipleOrders() {
        for (int i = 0; i < 10; i++) {
            OrderRequest request = new OrderRequest("AAPL", "BUY", "LIMIT", 100, 15000, 999);
            
            StepVerifier.create(orderService.submitOrder(request))
                    .assertNext(orderId -> assertTrue(orderId > 0))
                    .verifyComplete();
        }
        
        // Verify event count
        assertTrue(eventBus.getPublishedEventCount() >= 10);
    }
}
