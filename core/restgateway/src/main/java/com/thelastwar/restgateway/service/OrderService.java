package com.thelastwar.restgateway.service;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.SourceId;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.restgateway.dto.OrderRequest;
import com.thelastwar.restgateway.dto.OrderStatusResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for handling order operations.
 * Integrates with EventBus for asynchronous order dispatch.
 */
@Service
public class OrderService {
    
    private final EventBus eventBus;
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    
    // In-memory store for order status (in production, this would be a database or cache)
    private final Map<Long, OrderEvent> orders = new ConcurrentHashMap<>();
    
    public OrderService(EventBus eventBus) {
        this.eventBus = eventBus;
        
        // Subscribe to order events to update internal state
        eventBus.subscribe(EventType.ORDER_ACCEPTED, this::handleOrderAccepted);
        eventBus.subscribe(EventType.ORDER_FILLED, this::handleOrderFilled);
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, this::handleOrderPartiallyFilled);
        eventBus.subscribe(EventType.ORDER_CANCELLED, this::handleOrderCancelled);
        eventBus.subscribe(EventType.ORDER_REJECTED, this::handleOrderRejected);
    }
    
    /**
     * Submits a new order.
     * Publishes the order to the EventBus for asynchronous processing.
     */
    public Mono<Long> submitOrder(OrderRequest request) {
        return Mono.fromCallable(() -> {
            // Validate request
            request.validate();
            
            // Generate order ID
            long orderId = orderIdGenerator.getAndIncrement();
            
            // Create order event
            OrderEvent orderEvent = OrderEvent.newOrder(
                orderId,
                request.symbol(),
                request.getSideByte(),
                request.getOrderTypeByte(),
                request.quantity(),
                request.price(),
                request.account(),
                1 // Default exchange ID
            );
            
            // Store order
            orders.put(orderId, orderEvent);
            
            // Publish to EventBus
            Event event = Event.now(
                orderId,
                SourceId.REST_GATEWAY,
                EventType.ORDER_SUBMITTED,
                0L,
                orderEvent
            );
            
            if (!eventBus.publish(event)) {
                throw new RuntimeException("Failed to publish order to EventBus");
            }
            
            return orderId;
        });
    }
    
    /**
     * Gets order status by ID.
     */
    public Mono<OrderStatusResponse> getOrderStatus(long orderId) {
        return Mono.fromCallable(() -> {
            OrderEvent order = orders.get(orderId);
            if (order == null) {
                return null;
            }
            
            return OrderStatusResponse.fromStatus(
                order.orderId(),
                order.symbol(),
                order.isBuy() ? "BUY" : "SELL",
                getOrderTypeName(order.orderType()),
                order.quantity(),
                order.price(),
                order.status(),
                order.timestamp()
            );
        });
    }
    
    /**
     * Cancels an order.
     */
    public Mono<Boolean> cancelOrder(long orderId) {
        return Mono.fromCallable(() -> {
            OrderEvent order = orders.get(orderId);
            if (order == null) {
                return false;
            }
            
            // Check if order is already in terminal state
            if (order.isTerminal()) {
                return false;
            }
            
            // Update order status
            OrderEvent cancelledOrder = order.withStatus(OrderEvent.STATUS_CANCELLED);
            orders.put(orderId, cancelledOrder);
            
            // Publish cancellation event
            Event event = Event.now(
                orderId,
                SourceId.REST_GATEWAY,
                EventType.ORDER_CANCELLED,
                0L,
                cancelledOrder
            );
            
            return eventBus.publish(event);
        });
    }
    
    /**
     * Event handlers for order state updates.
     */
    private void handleOrderAccepted(Event event) {
        if (event.payload() instanceof OrderEvent orderEvent) {
            orders.put(orderEvent.orderId(), orderEvent);
        }
    }
    
    private void handleOrderFilled(Event event) {
        if (event.payload() instanceof OrderEvent orderEvent) {
            orders.put(orderEvent.orderId(), orderEvent);
        }
    }
    
    private void handleOrderPartiallyFilled(Event event) {
        if (event.payload() instanceof OrderEvent orderEvent) {
            orders.put(orderEvent.orderId(), orderEvent);
        }
    }
    
    private void handleOrderCancelled(Event event) {
        if (event.payload() instanceof OrderEvent orderEvent) {
            orders.put(orderEvent.orderId(), orderEvent);
        }
    }
    
    private void handleOrderRejected(Event event) {
        if (event.payload() instanceof OrderEvent orderEvent) {
            orders.put(orderEvent.orderId(), orderEvent);
        }
    }
    
    private String getOrderTypeName(byte orderType) {
        return switch (orderType) {
            case 1 -> "MARKET";
            case 2 -> "LIMIT";
            case 3 -> "STOP";
            case 4 -> "STOP_LIMIT";
            default -> "UNKNOWN";
        };
    }
}
