package com.thelastwar.oms;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.SourceId;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.oms.sor.SORClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ChildOrderManager is responsible for:
 * - Creating child orders from routing decisions
 * - Tracking child order lifecycle
 * - Reconciling child order fills back to parent orders
 * - Aggregating partial fills across children
 * - Publishing child order events to EventBus
 * 
 * Thread-safety: This class is thread-safe for concurrent operations.
 * 
 * Performance targets:
 * - Child order creation: < 10 µs per child
 * - Fill reconciliation: < 5 µs per fill
 * - Parent state update: < 1 µs
 */
public class ChildOrderManager {
    
    private final EventBus eventBus;
    private final SORClient sorClient;
    private final AtomicLong childOrderIdGenerator;
    
    // Parent order tracking: ParentOrderId -> ParentOrder
    private final Map<InternalOrderId, ParentOrder> parentOrders;
    
    // Child order tracking: ChildOrderId -> ChildOrder
    private final Map<InternalOrderId, ChildOrder> childOrders;
    
    // Reverse lookup: ChildOrderId -> ParentOrderId
    private final Map<InternalOrderId, InternalOrderId> childToParentMap;
    
    // Child order state machines: ChildOrderId -> OrderStateMachine
    private final Map<InternalOrderId, OrderStateMachine> childStateMachines;
    
    // Child order fill tracking: ChildOrderId -> FilledQuantity
    private final Map<InternalOrderId, Long> childFillQuantities;
    
    // Metrics
    private final AtomicLong totalChildOrdersCreated;
    private final AtomicLong totalFillsReconciled;
    private final AtomicLong totalReconciliationErrors;
    
    /**
     * Creates a new ChildOrderManager.
     * 
     * @param eventBus  EventBus for publishing child order events
     * @param sorClient SOR client for routing decisions
     */
    public ChildOrderManager(EventBus eventBus, SORClient sorClient) {
        this.eventBus = Objects.requireNonNull(eventBus, "EventBus cannot be null");
        this.sorClient = Objects.requireNonNull(sorClient, "SOR client cannot be null");
        this.childOrderIdGenerator = new AtomicLong(1_000_000); // Start from 1M to avoid conflicts
        this.parentOrders = new ConcurrentHashMap<>();
        this.childOrders = new ConcurrentHashMap<>();
        this.childToParentMap = new ConcurrentHashMap<>();
        this.childStateMachines = new ConcurrentHashMap<>();
        this.childFillQuantities = new ConcurrentHashMap<>();
        this.totalChildOrdersCreated = new AtomicLong(0);
        this.totalFillsReconciled = new AtomicLong(0);
        this.totalReconciliationErrors = new AtomicLong(0);
    }
    
    /**
     * Registers a parent order for child order tracking.
     * 
     * @param parentOrder The parent order to register
     */
    public void registerParentOrder(ParentOrder parentOrder) {
        Objects.requireNonNull(parentOrder, "Parent order cannot be null");
        parentOrders.put(parentOrder.getParentOrderId(), parentOrder);
    }
    
    /**
     * Creates child orders from a routing decision and publishes them to EventBus.
     * 
     * @param decision The routing decision
     * @return List of created child orders
     * @throws IllegalArgumentException if routing decision is invalid
     * @throws IllegalStateException if parent order not found
     */
    public List<ChildOrder> createChildOrders(RoutingDecision decision) {
        Objects.requireNonNull(decision, "Routing decision cannot be null");
        
        // Get parent order
        ParentOrder parentOrder = parentOrders.get(decision.parentOrderId());
        if (parentOrder == null) {
            throw new IllegalStateException("Parent order not found: " + decision.parentOrderId());
        }
        
        // Validate quantities
        decision.validateQuantities(parentOrder.getTotalQuantity());
        
        // Create child orders from routing instructions
        List<ChildOrder> createdChildren = decision.routingInstructions().stream()
            .map(instruction -> createChildOrder(parentOrder, instruction, decision.strategyName()))
            .toList();
        
        // Register child orders
        for (ChildOrder child : createdChildren) {
            childOrders.put(child.childOrderId(), child);
            childToParentMap.put(child.childOrderId(), child.parentOrderId());
            childFillQuantities.put(child.childOrderId(), 0L);
            parentOrder.addChildOrder(child.childOrderId());
            
            // Create state machine for child
            OrderStateMachine childStateMachine = new OrderStateMachine(child.childOrderId());
            childStateMachines.put(child.childOrderId(), childStateMachine);
            
            // Publish child order to EventBus
            publishChildOrder(child);
            
            totalChildOrdersCreated.incrementAndGet();
        }
        
        return createdChildren;
    }
    
    /**
     * Creates a single child order from a routing instruction.
     */
    private ChildOrder createChildOrder(
            ParentOrder parentOrder,
            RoutingDecision.RoutingInstruction instruction,
            String routingStrategy) {
        
        InternalOrderId childOrderId = InternalOrderId.of(childOrderIdGenerator.getAndIncrement());
        
        return ChildOrder.fromParentWithPrice(
            childOrderId,
            parentOrder,
            instruction.quantity(),
            instruction.price(),
            instruction.venue(),
            routingStrategy
        );
    }
    
    /**
     * Publishes a child order to the EventBus.
     */
    private void publishChildOrder(ChildOrder childOrder) {
        OrderEvent orderEvent = OrderEvent.newOrder(
            childOrder.childOrderId().value(),
            childOrder.symbol(),
            childOrder.side(),
            childOrder.orderType(),
            childOrder.quantity(),
            childOrder.price(),
            0L, // account - not tracked at child level
            1   // exchange
        );
        
        Event event = Event.now(
            childOrder.childOrderId().value(),
            SourceId.OMS,
            EventType.ORDER_SUBMITTED,
            0L,
            orderEvent
        );
        
        eventBus.publish(event);
    }
    
    /**
     * Reconciles a fill on a child order back to the parent order.
     * 
     * @param childOrderId  The child order that was filled
     * @param fillQuantity  The quantity filled
     * @throws IllegalArgumentException if child order not found
     * @throws IllegalStateException if parent order not found
     */
    public void reconcileFill(InternalOrderId childOrderId, long fillQuantity) {
        Objects.requireNonNull(childOrderId, "Child order ID cannot be null");
        if (fillQuantity <= 0) {
            throw new IllegalArgumentException("Fill quantity must be positive");
        }
        
        try {
            // Get child order
            ChildOrder childOrder = childOrders.get(childOrderId);
            if (childOrder == null) {
                throw new IllegalArgumentException("Child order not found: " + childOrderId);
            }
            
            // Get parent order ID
            InternalOrderId parentOrderId = childToParentMap.get(childOrderId);
            if (parentOrderId == null) {
                throw new IllegalStateException("Parent order mapping not found for child: " + childOrderId);
            }
            
            // Get parent order
            ParentOrder parentOrder = parentOrders.get(parentOrderId);
            if (parentOrder == null) {
                throw new IllegalStateException("Parent order not found: " + parentOrderId);
            }
            
            // Get current fill quantity for child
            long currentChildFill = childFillQuantities.getOrDefault(childOrderId, 0L);
            long newChildFillTotal = currentChildFill + fillQuantity;
            
            // Validate we're not overfilling the child order
            if (newChildFillTotal > childOrder.quantity()) {
                throw new IllegalArgumentException(
                    "Fill would exceed child order quantity. Child qty: " + childOrder.quantity() +
                    ", already filled: " + currentChildFill + ", new fill: " + fillQuantity
                );
            }
            
            // Update child fill tracking
            childFillQuantities.put(childOrderId, newChildFillTotal);
            
            // Update parent order fill state
            synchronized (parentOrder) {
                parentOrder.updateFill(fillQuantity);
            }
            
            // Update child order state machine
            OrderStateMachine childStateMachine = childStateMachines.get(childOrderId);
            if (childStateMachine != null) {
                // Transition to ACCEPTED and WORKING if not already
                if (childStateMachine.getCurrentState() == OrderState.NEW) {
                    childStateMachine.transition(OrderState.ACCEPTED);
                }
                if (childStateMachine.getCurrentState() == OrderState.ACCEPTED) {
                    childStateMachine.transition(OrderState.WORKING);
                }
                
                // Now handle the fill
                if (newChildFillTotal >= childOrder.quantity()) {
                    // Fully filled
                    childStateMachine.transition(OrderState.FILLED);
                } else {
                    // Partially filled
                    childStateMachine.transition(OrderState.PARTIAL_FILL);
                }
            }
            
            totalFillsReconciled.incrementAndGet();
            
        } catch (Exception e) {
            totalReconciliationErrors.incrementAndGet();
            throw e;
        }
    }
    
    /**
     * Gets a parent order by ID.
     * 
     * @param parentOrderId The parent order ID
     * @return ParentOrder or null if not found
     */
    public ParentOrder getParentOrder(InternalOrderId parentOrderId) {
        return parentOrders.get(parentOrderId);
    }
    
    /**
     * Gets a child order by ID.
     * 
     * @param childOrderId The child order ID
     * @return ChildOrder or null if not found
     */
    public ChildOrder getChildOrder(InternalOrderId childOrderId) {
        return childOrders.get(childOrderId);
    }
    
    /**
     * Gets the parent order ID for a child order.
     * 
     * @param childOrderId The child order ID
     * @return Parent order ID or null if not found
     */
    public InternalOrderId getParentOrderId(InternalOrderId childOrderId) {
        return childToParentMap.get(childOrderId);
    }
    
    /**
     * Gets metrics for monitoring.
     * 
     * @return ChildOrderMetrics
     */
    public ChildOrderMetrics getMetrics() {
        return new ChildOrderMetrics(
            totalChildOrdersCreated.get(),
            totalFillsReconciled.get(),
            totalReconciliationErrors.get(),
            parentOrders.size(),
            childOrders.size()
        );
    }
    
    /**
     * Metrics for child order management.
     */
    public record ChildOrderMetrics(
            long totalChildOrdersCreated,
            long totalFillsReconciled,
            long totalReconciliationErrors,
            int activeParentOrders,
            int activeChildOrders) {
        
        /**
         * Gets the average number of child orders per parent.
         * 
         * @return average child orders per parent
         */
        public double getAverageChildOrdersPerParent() {
            if (activeParentOrders == 0) {
                return 0.0;
            }
            return (double) activeChildOrders / activeParentOrders;
        }
        
        /**
         * Gets the reconciliation error rate.
         * 
         * @return error rate (0-100)
         */
        public double getReconciliationErrorRate() {
            long total = totalFillsReconciled + totalReconciliationErrors;
            if (total == 0) {
                return 0.0;
            }
            return (totalReconciliationErrors * 100.0) / total;
        }
    }
}
