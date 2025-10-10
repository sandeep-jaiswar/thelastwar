package com.thelastwar.matching;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;
import com.thelastwar.orderbook.*;
import com.thelastwar.risk.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance matching engine that consumes order events, matches against order books,
 * generates trades, and publishes executions to Event Bus.
 * 
 * Architecture:
 * - Maintains separate order books per symbol
 * - Subscribes to ORDER_SUBMITTED events from Event Bus
 * - Matches orders using price-time priority
 * - Generates ExecutionEvent and TradeEvent for each match
 * - Publishes events back to Event Bus for downstream consumers
 * 
 * Performance targets:
 * - < 5 µs per match (p99)
 * - Deterministic replay via sequence-based event ordering
 * - GC-neutral design with object pooling
 * 
 * Thread Safety:
 * - Each symbol's order book has single-writer guarantee
 * - Event bus handles thread coordination
 */
public class MatchingEngine {
    
    private final EventBus eventBus;
    private final ConcurrentHashMap<String, LimitOrderBook> books;
    private final AtomicLong executionIdCounter;
    private final AtomicLong tradeIdCounter;
    private final AtomicLong sequenceTracker;
    private volatile boolean running;
    private final RiskValidator riskValidator;
    
    /**
     * Creates a new matching engine with default risk validation.
     * 
     * @param eventBus Event bus for publishing/subscribing events
     */
    public MatchingEngine(EventBus eventBus) {
        this(eventBus, createDefaultRiskValidator());
    }
    
    /**
     * Creates a new matching engine with custom risk validation.
     * 
     * @param eventBus      Event bus for publishing/subscribing events
     * @param riskValidator Risk validator for pre-trade checks
     */
    public MatchingEngine(EventBus eventBus, RiskValidator riskValidator) {
        this.eventBus = eventBus;
        this.books = new ConcurrentHashMap<>();
        this.executionIdCounter = new AtomicLong(0);
        this.tradeIdCounter = new AtomicLong(0);
        this.sequenceTracker = new AtomicLong(0);
        this.running = false;
        this.riskValidator = riskValidator;
    }
    
    /**
     * Creates default risk validator with standard modules.
     * 
     * @return Composite risk validator with credit, margin, and fat-finger checks
     */
    private static RiskValidator createDefaultRiskValidator() {
        return new CompositeRiskValidator.Builder()
            .add(new CreditCheckModule())
            .add(new MarginCheckModule())
            .add(new FatFingerCheckModule())
            .build();
    }
    
    /**
     * Starts the matching engine and subscribes to order events.
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("Matching engine is already running");
        }
        
        // Subscribe to order submission events
        eventBus.subscribe(EventType.ORDER_SUBMITTED, this::handleOrderEvent);
        
        running = true;
    }
    
    /**
     * Stops the matching engine.
     */
    public void stop() {
        running = false;
    }
    
    /**
     * Checks if the matching engine is running.
     * 
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Gets the order book for a symbol, creating it if needed.
     * 
     * @param symbol Trading symbol
     * @return Order book for the symbol
     */
    public LimitOrderBook getOrderBook(String symbol) {
        return books.computeIfAbsent(symbol, LimitOrderBook::new);
    }
    
    /**
     * Gets the current sequence number for deterministic replay.
     * 
     * @return Current sequence number
     */
    public long getCurrentSequence() {
        return sequenceTracker.get();
    }
    
    /**
     * Handles incoming order events from the event bus.
     * 
     * @param event Event containing OrderEvent payload
     */
    private void handleOrderEvent(Event event) {
        if (!(event.payload() instanceof OrderEvent orderEvent)) {
            return;
        }
        
        // Track sequence for deterministic replay
        long sequence = sequenceTracker.incrementAndGet();
        
        // Pre-trade risk validation (inline, synchronous)
        RiskDecision riskDecision = riskValidator.validate(orderEvent);
        
        if (!riskDecision.approved()) {
            // Reject order and publish rejection event
            rejectOrder(orderEvent, riskDecision.reasonCode());
            return;
        }
        
        // Process the order if risk check passed
        processOrder(orderEvent, event.timestamp());
    }
    
    /**
     * Rejects an order and publishes rejection event.
     * 
     * @param orderEvent Order to reject
     * @param reasonCode Rejection reason code
     */
    private void rejectOrder(OrderEvent orderEvent, int reasonCode) {
        long executionId = executionIdCounter.incrementAndGet();
        long timestamp = System.nanoTime();
        
        // Create rejection execution
        ExecutionEvent rejection = ExecutionEvent.reject(executionId, orderEvent, reasonCode);
        
        // Publish rejection event
        Event rejectionEvent = Event.create(
            timestamp,
            eventBus.getCurrentSequence() + 1,
            SourceId.MATCHING_ENGINE,
            EventType.ORDER_REJECTED,
            0L,
            rejection
        );
        eventBus.publish(rejectionEvent);
    }
    
    /**
     * Processes an order event and attempts to match it.
     * 
     * @param orderEvent Order to process
     * @param timestamp Event timestamp
     */
    private void processOrder(OrderEvent orderEvent, long timestamp) {
        LimitOrderBook book = getOrderBook(orderEvent.symbol());
        
        // Convert OrderEvent to Order for the book
        Order order = new Order(
            orderEvent.orderId(),
            orderEvent.symbol(),
            orderEvent.side(),
            orderEvent.price(),
            orderEvent.quantity(),
            timestamp
        );
        
        // Attempt to match the order
        long remainingQuantity = matchOrder(book, order, orderEvent);
        
        // If order has remaining quantity, add to book
        if (remainingQuantity > 0) {
            Order residualOrder = order.withQuantity(remainingQuantity);
            book.addOrder(residualOrder);
            
            // Publish execution event for remaining quantity
            if (remainingQuantity < orderEvent.quantity()) {
                // Partial fill
                publishPartialFillExecution(orderEvent, orderEvent.quantity() - remainingQuantity, remainingQuantity);
            }
        } else {
            // Fully filled - execution already published in matchOrder
        }
    }
    
    /**
     * Attempts to match an order against the book.
     * 
     * @param book Order book
     * @param order Order to match
     * @param orderEvent Original order event
     * @return Remaining unfilled quantity
     */
    private long matchOrder(LimitOrderBook book, Order order, OrderEvent orderEvent) {
        long remainingQty = order.quantity();
        
        // Match against opposite side
        while (remainingQty > 0) {
            // Get best price on opposite side
            long bestPrice = order.isBuy() ? book.getBestAsk() : book.getBestBid();
            
            if (bestPrice == 0) {
                // No orders on opposite side
                break;
            }
            
            // Check if prices cross (can match)
            boolean canMatch = order.isBuy() ? 
                (order.price() >= bestPrice) : 
                (order.price() <= bestPrice);
            
            if (!canMatch) {
                // No matching price
                break;
            }
            
            // Find the best matching order
            Order matchingOrder = findBestMatchingOrder(book, order.side());
            if (matchingOrder == null) {
                break;
            }
            
            // Calculate fill quantity
            long fillQty = Math.min(remainingQty, matchingOrder.quantity());
            long fillPrice = matchingOrder.price(); // Price-time priority - use resting order's price
            
            // Generate trade
            generateTrade(orderEvent, matchingOrder, fillQty, fillPrice);
            
            // Update remaining quantity
            remainingQty -= fillQty;
            
            // Update or remove matching order
            long newMatchingQty = matchingOrder.quantity() - fillQty;
            if (newMatchingQty > 0) {
                // Partial fill of resting order
                book.modifyOrder(matchingOrder.orderId(), 0, newMatchingQty);
            } else {
                // Fully filled - remove from book
                book.removeOrder(matchingOrder.orderId());
            }
        }
        
        return remainingQty;
    }
    
    /**
     * Finds the best matching order on the opposite side.
     * 
     * @param book Order book
     * @param incomingSide Side of incoming order
     * @return Best matching order or null
     */
    private Order findBestMatchingOrder(LimitOrderBook book, byte incomingSide) {
        // Get opposite side
        boolean findOnBidSide = (incomingSide == Order.SIDE_SELL);
        
        long bestPrice = findOnBidSide ? book.getBestBid() : book.getBestAsk();
        if (bestPrice == 0) {
            return null;
        }
        
        // Get price levels and find first order (FIFO - price-time priority)
        var levels = findOnBidSide ? book.getBidLevels(1) : book.getAskLevels(1);
        if (levels.isEmpty()) {
            return null;
        }
        
        var level = levels.get(0);
        return level.peek(); // Get first order without removing
    }
    
    /**
     * Generates trade events for a match.
     * 
     * @param incomingOrder Incoming order event
     * @param restingOrder Resting order in book
     * @param fillQty Fill quantity
     * @param fillPrice Fill price
     */
    private void generateTrade(OrderEvent incomingOrder, Order restingOrder, 
                               long fillQty, long fillPrice) {
        long tradeId = tradeIdCounter.incrementAndGet();
        long executionId = executionIdCounter.incrementAndGet();
        long timestamp = System.nanoTime();
        
        // Create trade event
        TradeEvent trade = new TradeEvent(
            tradeId,
            incomingOrder.orderId(),
            incomingOrder.symbol(),
            incomingOrder.side(),
            fillQty,
            fillPrice,
            timestamp,
            incomingOrder.account(),
            incomingOrder.exchange(),
            0L, // counterparty - could be enhanced
            0L  // fees - could be calculated
        );
        
        // Publish trade event
        Event tradeEventWrapper = Event.create(
            timestamp,
            eventBus.getCurrentSequence() + 1,
            SourceId.MATCHING_ENGINE,
            EventType.ORDER_FILLED,
            0L,
            trade
        );
        eventBus.publish(tradeEventWrapper);
        
        // Create execution event for incoming order
        long cumulativeFilled = incomingOrder.quantity() - (incomingOrder.quantity() - fillQty);
        long leavesQty = incomingOrder.quantity() - cumulativeFilled;
        
        ExecutionEvent execution = ExecutionEvent.fill(
            executionId,
            incomingOrder,
            fillQty,
            fillPrice,
            cumulativeFilled,
            leavesQty
        );
        
        // Publish execution event
        Event executionEventWrapper = Event.create(
            timestamp,
            eventBus.getCurrentSequence() + 1,
            SourceId.MATCHING_ENGINE,
            leavesQty > 0 ? EventType.ORDER_PARTIALLY_FILLED : EventType.ORDER_FILLED,
            0L,
            execution
        );
        eventBus.publish(executionEventWrapper);
    }
    
    /**
     * Publishes a partial fill execution event.
     * 
     * @param orderEvent Original order event
     * @param filledQty Quantity filled
     * @param remainingQty Remaining quantity
     */
    private void publishPartialFillExecution(OrderEvent orderEvent, long filledQty, long remainingQty) {
        long executionId = executionIdCounter.incrementAndGet();
        long timestamp = System.nanoTime();
        
        ExecutionEvent execution = ExecutionEvent.fill(
            executionId,
            orderEvent,
            filledQty,
            orderEvent.price(),
            filledQty,
            remainingQty
        );
        
        Event executionEventWrapper = Event.create(
            timestamp,
            eventBus.getCurrentSequence() + 1,
            SourceId.MATCHING_ENGINE,
            EventType.ORDER_PARTIALLY_FILLED,
            0L,
            execution
        );
        eventBus.publish(executionEventWrapper);
    }
    
    /**
     * Gets metrics about the matching engine.
     * 
     * @return Matching engine metrics
     */
    public MatchingEngineMetrics getMetrics() {
        return new MatchingEngineMetrics(
            books.size(),
            executionIdCounter.get(),
            tradeIdCounter.get(),
            sequenceTracker.get()
        );
    }
    
    /**
     * Metrics record for matching engine state.
     */
    public record MatchingEngineMetrics(
        int symbolCount,
        long totalExecutions,
        long totalTrades,
        long currentSequence
    ) {}
}
