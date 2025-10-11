package com.thelastwar.matching;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;
import com.thelastwar.orderbook.LimitOrderBook;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the IMatchingEngine interface implementation.
 * Validates onNewOrder, onCancel, onReplace, and onMarketDataUpdate operations.
 */
class IMatchingEngineTest {
    
    private InMemoryEventBus eventBus;
    private MatchingEngine engine;
    private List<Event> capturedEvents;
    
    @BeforeEach
    void setUp() {
        eventBus = new InMemoryEventBus();
        eventBus.start();
        engine = new MatchingEngine(eventBus);
        engine.start();
        
        capturedEvents = new ArrayList<>();
        
        // Capture all events
        eventBus.subscribe(EventType.ORDER_FILLED, capturedEvents::add);
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, capturedEvents::add);
        eventBus.subscribe(EventType.ORDER_REJECTED, capturedEvents::add);
        eventBus.subscribe(EventType.ORDER_CANCELLED, capturedEvents::add);
        eventBus.subscribe(EventType.ORDER_MODIFIED, capturedEvents::add);
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, capturedEvents::add);
    }
    
    @AfterEach
    void tearDown() {
        engine.stop();
        eventBus.stop();
    }
    
    @Test
    void testOnNewOrder() {
        // Create an order envelope
        OrderEvent orderEvent = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        OrderEnvelope envelope = OrderEnvelope.wrap(orderEvent, 1L, SourceId.OMS);
        
        // Process the order
        engine.onNewOrder(envelope);
        
        // Verify order book has the order
        LimitOrderBook book = engine.getOrderBook("AAPL");
        assertNotNull(book);
        assertEquals(15000L, book.getBestBid());
        assertEquals(0L, book.getBestAsk());
    }
    
    @Test
    void testOnNewOrderWithMatching() {
        // Add a sell order first
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        OrderEnvelope sellEnvelope = OrderEnvelope.wrap(sellOrder, 1L, SourceId.OMS);
        engine.onNewOrder(sellEnvelope);
        
        // Add a matching buy order
        OrderEvent buyOrder = OrderEvent.newOrder(
            2L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        OrderEnvelope buyEnvelope = OrderEnvelope.wrap(buyOrder, 2L, SourceId.OMS);
        engine.onNewOrder(buyEnvelope);
        
        // Verify trade was generated
        List<Event> tradeEvents = capturedEvents.stream()
            .filter(e -> e.eventType() == EventType.ORDER_FILLED)
            .toList();
        
        assertFalse(tradeEvents.isEmpty());
        assertTrue(tradeEvents.size() >= 1);
    }
    
    @Test
    void testOnCancel() {
        // Add an order first
        OrderEvent orderEvent = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        OrderEnvelope envelope = OrderEnvelope.wrap(orderEvent, 1L, SourceId.OMS);
        engine.onNewOrder(envelope);
        
        // Cancel the order
        OrderCancel cancel = OrderCancel.create(1L, "AAPL", 999L, 100L);
        engine.onCancel(cancel);
        
        // Verify order is removed from book
        LimitOrderBook book = engine.getOrderBook("AAPL");
        assertEquals(0L, book.getBestBid());
        
        // Verify cancellation event was generated
        List<Event> cancelEvents = capturedEvents.stream()
            .filter(e -> e.eventType() == EventType.ORDER_CANCELLED)
            .toList();
        
        assertFalse(cancelEvents.isEmpty());
    }
    
    @Test
    void testOnCancelNonExistentOrder() {
        // Try to cancel an order that doesn't exist
        OrderCancel cancel = OrderCancel.create(999L, "AAPL", 999L, 100L);
        engine.onCancel(cancel);
        
        // Verify rejection event was generated
        List<Event> rejectionEvents = capturedEvents.stream()
            .filter(e -> e.eventType() == EventType.ORDER_REJECTED)
            .toList();
        
        assertFalse(rejectionEvents.isEmpty());
    }
    
    @Test
    void testOnReplace() {
        // Add an order first
        OrderEvent orderEvent = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        OrderEnvelope envelope = OrderEnvelope.wrap(orderEvent, 1L, SourceId.OMS);
        engine.onNewOrder(envelope);
        
        // Modify the order (change price)
        OrderModify modify = OrderModify.modifyPrice(1L, "AAPL", 15100L, 999L, 100L);
        engine.onReplace(modify);
        
        // Verify order has new price
        LimitOrderBook book = engine.getOrderBook("AAPL");
        assertEquals(15100L, book.getBestBid());
        
        // Verify modification event was generated
        List<Event> modifyEvents = capturedEvents.stream()
            .filter(e -> e.eventType() == EventType.ORDER_MODIFIED)
            .toList();
        
        assertFalse(modifyEvents.isEmpty());
    }
    
    @Test
    void testOnReplaceWithQuantityChange() {
        // Add an order first
        OrderEvent orderEvent = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        OrderEnvelope envelope = OrderEnvelope.wrap(orderEvent, 1L, SourceId.OMS);
        engine.onNewOrder(envelope);
        
        // Modify the order (change quantity)
        OrderModify modify = OrderModify.modifyQuantity(1L, "AAPL", 200L, 999L, 100L);
        engine.onReplace(modify);
        
        // Verify modification event was generated
        List<Event> modifyEvents = capturedEvents.stream()
            .filter(e -> e.eventType() == EventType.ORDER_MODIFIED)
            .toList();
        
        assertFalse(modifyEvents.isEmpty());
    }
    
    @Test
    void testOnReplaceNonExistentOrder() {
        // Try to modify an order that doesn't exist
        OrderModify modify = OrderModify.modifyPrice(999L, "AAPL", 15100L, 999L, 100L);
        engine.onReplace(modify);
        
        // Verify rejection event was generated
        List<Event> rejectionEvents = capturedEvents.stream()
            .filter(e -> e.eventType() == EventType.ORDER_REJECTED)
            .toList();
        
        assertFalse(rejectionEvents.isEmpty());
    }
    
    @Test
    void testOnReplaceWithMatching() {
        // Add a sell order first
        OrderEvent sellOrder = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        OrderEnvelope sellEnvelope = OrderEnvelope.wrap(sellOrder, 1L, SourceId.OMS);
        engine.onNewOrder(sellEnvelope);
        
        // Add a buy order at lower price
        OrderEvent buyOrder = OrderEvent.newOrder(
            2L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 14900L, 999L, 1
        );
        OrderEnvelope buyEnvelope = OrderEnvelope.wrap(buyOrder, 2L, SourceId.OMS);
        engine.onNewOrder(buyEnvelope);
        
        // Clear captured events
        capturedEvents.clear();
        
        // Modify the buy order to cross the spread
        OrderModify modify = OrderModify.modifyPrice(2L, "AAPL", 15000L, 999L, 100L);
        engine.onReplace(modify);
        
        // Verify trade was generated
        List<Event> tradeEvents = capturedEvents.stream()
            .filter(e -> e.eventType() == EventType.ORDER_FILLED)
            .toList();
        
        assertFalse(tradeEvents.isEmpty());
    }
    
    @Test
    void testOnMarketDataUpdate() {
        // Create a tick event
        TickEvent tick = TickEvent.create(
            "AAPL", 14900L, 15000L, 14950L,
            1000L, 1000L, 1L, 1
        );
        
        // Process the tick
        engine.onMarketDataUpdate(tick);
        
        // Verify market data event was published
        List<Event> marketDataEvents = capturedEvents.stream()
            .filter(e -> e.eventType() == EventType.MARKET_DATA_UPDATE)
            .toList();
        
        assertFalse(marketDataEvents.isEmpty());
        
        // Verify the tick is in the event payload
        Event event = marketDataEvents.get(0);
        assertTrue(event.payload() instanceof TickEvent);
        TickEvent capturedTick = (TickEvent) event.payload();
        assertEquals("AAPL", capturedTick.symbol());
        assertEquals(14900L, capturedTick.bidPrice());
        assertEquals(15000L, capturedTick.askPrice());
    }
    
    @Test
    void testOnMarketDataUpdateWithNullTick() {
        // Process null tick (should be handled gracefully)
        engine.onMarketDataUpdate(null);
        
        // Verify no events were generated
        List<Event> marketDataEvents = capturedEvents.stream()
            .filter(e -> e.eventType() == EventType.MARKET_DATA_UPDATE)
            .toList();
        
        assertTrue(marketDataEvents.isEmpty());
    }
    
    @Test
    void testSequenceTracking() {
        long initialSequence = engine.getCurrentSequence();
        
        // Process multiple operations
        OrderEvent order1 = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        engine.onNewOrder(OrderEnvelope.wrap(order1, 1L, SourceId.OMS));
        
        OrderCancel cancel = OrderCancel.create(1L, "AAPL", 999L, 100L);
        engine.onCancel(cancel);
        
        TickEvent tick = TickEvent.create("AAPL", 14900L, 15000L, 14950L, 1000L, 1000L, 1L, 1);
        engine.onMarketDataUpdate(tick);
        
        // Verify sequence incremented for each operation
        long finalSequence = engine.getCurrentSequence();
        assertTrue(finalSequence > initialSequence);
        assertEquals(initialSequence + 3, finalSequence);
    }
    
    @Test
    void testDeterministicBehavior() {
        // First run
        OrderEvent order1 = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_SELL, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        engine.onNewOrder(OrderEnvelope.wrap(order1, 1L, SourceId.OMS));
        
        OrderEvent order2 = OrderEvent.newOrder(
            2L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        engine.onNewOrder(OrderEnvelope.wrap(order2, 2L, SourceId.OMS));
        
        int firstRunEvents = capturedEvents.size();
        long firstRunSequence = engine.getCurrentSequence();
        
        // Reset for second run
        tearDown();
        setUp();
        
        // Second run with same operations
        engine.onNewOrder(OrderEnvelope.wrap(order1, 1L, SourceId.OMS));
        engine.onNewOrder(OrderEnvelope.wrap(order2, 2L, SourceId.OMS));
        
        int secondRunEvents = capturedEvents.size();
        long secondRunSequence = engine.getCurrentSequence();
        
        // Verify deterministic behavior (same number of events and sequence)
        assertEquals(firstRunEvents, secondRunEvents);
        assertEquals(firstRunSequence, secondRunSequence);
    }
}
