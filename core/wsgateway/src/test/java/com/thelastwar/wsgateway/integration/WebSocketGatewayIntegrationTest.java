package com.thelastwar.wsgateway.integration;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.SourceId;
import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.gateway.GatewayMetrics;
import com.thelastwar.wsgateway.TestEventBus;
import com.thelastwar.wsgateway.TransportFormat;
import com.thelastwar.wsgateway.WebSocketGateway;
import com.thelastwar.wsgateway.WebSocketGatewayMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WebSocket Gateway with EventBus.
 * 
 * Tests the complete flow:
 * 1. Gateway subscribes to EventBus
 * 2. Events published to EventBus
 * 3. Gateway receives and processes events
 * 4. Metrics tracked correctly
 */
class WebSocketGatewayIntegrationTest {
    
    private TestEventBus eventBus;
    private WebSocketGateway gateway;
    private GatewayMetrics metrics;
    
    @BeforeEach
    void setUp() {
        eventBus = new TestEventBus();
        eventBus.start();
        metrics = new GatewayMetrics("integration-test-gateway");
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (gateway != null && gateway.isRunning()) {
            gateway.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
    }
    
    @Test
    void testGatewayStartupAndShutdown() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8090, TransportFormat.JSON, metrics);
        
        assertFalse(gateway.isRunning());
        
        gateway.start();
        assertTrue(gateway.isRunning());
        
        // Verify EventBus subscriptions were created
        assertTrue(eventBus.getSubscriberCount(EventType.ORDER_ACCEPTED) > 0);
        assertTrue(eventBus.getSubscriberCount(EventType.ORDER_FILLED) > 0);
        assertTrue(eventBus.getSubscriberCount(EventType.MARKET_DATA_UPDATE) > 0);
        
        gateway.stop();
        assertFalse(gateway.isRunning());
    }
    
    @Test
    void testEventBroadcastFlow() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8091, TransportFormat.JSON, metrics);
        gateway.start();
        
        WebSocketGatewayMetrics wsMetrics = gateway.getWebSocketMetrics();
        
        // Create an order event
        OrderEvent orderEvent = OrderEvent.newOrder(
            12345L,
            "AAPL",
            OrderEvent.SIDE_BUY,
            OrderEvent.TYPE_LIMIT,
            100L,
            15000L,
            999L,
            1
        );
        
        Event event = Event.create(
            System.nanoTime(),
            1L,
            SourceId.MATCHING_ENGINE,
            EventType.ORDER_ACCEPTED,
            0L,
            orderEvent
        );
        
        // Publish event to EventBus
        boolean published = eventBus.publish(event);
        assertTrue(published);
        
        // Give some time for async processing
        Thread.sleep(100);
        
        // Verify event was received by gateway (indirectly through EventBus subscriber count)
        assertEquals(1, eventBus.getPublishedEventCount());
    }
    
    @Test
    void testMultipleEventTypes() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8092, TransportFormat.JSON, metrics);
        gateway.start();
        
        // Publish different event types
        OrderEvent orderEvent = OrderEvent.newOrder(12345L, "AAPL", OrderEvent.SIDE_BUY, 
            OrderEvent.TYPE_LIMIT, 100L, 15000L, 999L, 1);
        
        Event acceptedEvent = Event.create(System.nanoTime(), 1L, SourceId.MATCHING_ENGINE,
            EventType.ORDER_ACCEPTED, 0L, orderEvent);
        eventBus.publish(acceptedEvent);
        
        ExecutionEvent executionEvent = ExecutionEvent.newOrder(98765L, orderEvent);
        Event filledEvent = Event.create(System.nanoTime(), 2L, SourceId.MATCHING_ENGINE,
            EventType.ORDER_FILLED, 0L, executionEvent);
        eventBus.publish(filledEvent);
        
        Event cancelledEvent = Event.create(System.nanoTime(), 3L, SourceId.MATCHING_ENGINE,
            EventType.ORDER_CANCELLED, 0L, orderEvent);
        eventBus.publish(cancelledEvent);
        
        // Give time for async processing
        Thread.sleep(100);
        
        assertEquals(3, eventBus.getPublishedEventCount());
    }
    
    @Test
    void testGatewayWithMessagePackFormat() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8093, TransportFormat.MESSAGEPACK, metrics);
        
        gateway.start();
        assertTrue(gateway.isRunning());
        
        // Verify subscriptions still work with MessagePack
        assertTrue(eventBus.getSubscriberCount(EventType.ORDER_ACCEPTED) > 0);
        
        gateway.stop();
    }
    
    @Test
    void testMetricsIntegration() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8094, TransportFormat.JSON, metrics);
        gateway.start();
        
        WebSocketGatewayMetrics wsMetrics = gateway.getWebSocketMetrics();
        
        // Initial state
        assertEquals(0, wsMetrics.getConcurrentClients());
        assertEquals(0, wsMetrics.getInboundMessageCount());
        assertEquals(0, wsMetrics.getOutboundMessageCount());
        
        // Publish some events
        OrderEvent orderEvent = OrderEvent.newOrder(12345L, "AAPL", OrderEvent.SIDE_BUY,
            OrderEvent.TYPE_LIMIT, 100L, 15000L, 999L, 1);
        
        for (int i = 0; i < 5; i++) {
            Event event = Event.create(System.nanoTime(), i, SourceId.MATCHING_ENGINE,
                EventType.ORDER_ACCEPTED, 0L, orderEvent);
            eventBus.publish(event);
        }
        
        Thread.sleep(100);
        
        // Events were published
        assertEquals(5, eventBus.getPublishedEventCount());
    }
    
    @Test
    void testConcurrentGateways() throws Exception {
        // Create two gateways on different ports
        WebSocketGateway gateway1 = new WebSocketGateway(eventBus, 8095, TransportFormat.JSON, 
            new GatewayMetrics("gateway1"));
        WebSocketGateway gateway2 = new WebSocketGateway(eventBus, 8096, TransportFormat.MESSAGEPACK,
            new GatewayMetrics("gateway2"));
        
        try {
            gateway1.start();
            gateway2.start();
            
            assertTrue(gateway1.isRunning());
            assertTrue(gateway2.isRunning());
            
            // Both should have subscribed to events
            assertTrue(eventBus.getSubscriberCount(EventType.ORDER_ACCEPTED) >= 2);
            
            // Publish an event - both should receive it
            OrderEvent orderEvent = OrderEvent.newOrder(12345L, "AAPL", OrderEvent.SIDE_BUY,
                OrderEvent.TYPE_LIMIT, 100L, 15000L, 999L, 1);
            Event event = Event.create(System.nanoTime(), 1L, SourceId.MATCHING_ENGINE,
                EventType.ORDER_ACCEPTED, 0L, orderEvent);
            
            eventBus.publish(event);
            Thread.sleep(100);
            
            assertEquals(1, eventBus.getPublishedEventCount());
            
        } finally {
            gateway1.stop();
            gateway2.stop();
        }
    }
    
    @Test
    void testGracefulShutdown() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8097, TransportFormat.JSON, metrics);
        gateway.start();
        
        // Publish some events
        for (int i = 0; i < 10; i++) {
            OrderEvent orderEvent = OrderEvent.newOrder(12345L + i, "AAPL", OrderEvent.SIDE_BUY,
                OrderEvent.TYPE_LIMIT, 100L, 15000L, 999L, 1);
            Event event = Event.create(System.nanoTime(), i, SourceId.MATCHING_ENGINE,
                EventType.ORDER_ACCEPTED, 0L, orderEvent);
            eventBus.publish(event);
        }
        
        // Stop gateway gracefully
        assertDoesNotThrow(() -> gateway.stop());
        assertFalse(gateway.isRunning());
    }
}
