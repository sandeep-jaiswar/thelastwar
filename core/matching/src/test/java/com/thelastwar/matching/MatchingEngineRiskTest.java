package com.thelastwar.matching;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.*;
import com.thelastwar.risk.*;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MatchingEngine with risk validation.
 */
class MatchingEngineRiskTest {
    
    private EventBus eventBus;
    private MatchingEngine engine;
    
    @BeforeEach
    void setUp() {
        eventBus = new InMemoryEventBus();
        eventBus.start();
    }
    
    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.stop();
        }
        eventBus.stop();
    }
    
    @Test
    void testOrderApprovedByDefaultRiskValidator() throws InterruptedException {
        engine = new MatchingEngine(eventBus);
        engine.start();
        
        List<Event> acceptedEvents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        // This event is not used for accepted orders in current implementation
        // Orders are accepted implicitly when not rejected
        eventBus.subscribe(EventType.ORDER_REJECTED, event -> {
            fail("Order should not be rejected");
        });
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        Event orderEvent = Event.create(
            System.nanoTime(),
            1L,
            SourceId.OMS,
            EventType.ORDER_SUBMITTED,
            0L,
            order
        );
        
        eventBus.publish(orderEvent);
        
        // Wait a bit for processing
        Thread.sleep(100);
        
        // If we get here without rejection, order was accepted
        assertTrue(engine.isRunning());
    }
    
    @Test
    void testOrderRejectedByRiskValidator() throws InterruptedException {
        // Create custom risk validator that always rejects
        RiskValidator alwaysReject = order -> RiskDecision.reject(
            RiskReasonCode.INSUFFICIENT_CREDIT,
            "Test rejection"
        );
        
        engine = new MatchingEngine(eventBus, alwaysReject);
        engine.start();
        
        List<ExecutionEvent> rejections = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.ORDER_REJECTED, event -> {
            if (event.payload() instanceof ExecutionEvent exec) {
                rejections.add(exec);
                latch.countDown();
            }
        });
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        Event orderEvent = Event.create(
            System.nanoTime(),
            1L,
            SourceId.OMS,
            EventType.ORDER_SUBMITTED,
            0L,
            order
        );
        
        eventBus.publish(orderEvent);
        
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Should receive rejection event");
        assertEquals(1, rejections.size());
        
        ExecutionEvent rejection = rejections.get(0);
        assertTrue(rejection.isRejected());
        assertEquals(RiskReasonCode.INSUFFICIENT_CREDIT, rejection.rejectReason());
    }
    
    @Test
    void testFatFingerCheckRejectsLargeOrder() throws InterruptedException {
        // Create risk validator with restrictive fat-finger check
        RiskValidator validator = new CompositeRiskValidator.Builder()
            .add(new FatFingerCheckModule(1000L, 100_000_00L, 1L, 10_000_000_00L, true))
            .build();
        
        engine = new MatchingEngine(eventBus, validator);
        engine.start();
        
        List<ExecutionEvent> rejections = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.ORDER_REJECTED, event -> {
            if (event.payload() instanceof ExecutionEvent exec) {
                rejections.add(exec);
                latch.countDown();
            }
        });
        
        // Create order with quantity exceeding limit
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            10000L, 15000L, 999L, 1  // quantity > 1000
        );
        
        Event orderEvent = Event.create(
            System.nanoTime(),
            1L,
            SourceId.OMS,
            EventType.ORDER_SUBMITTED,
            0L,
            order
        );
        
        eventBus.publish(orderEvent);
        
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Should receive rejection event");
        assertEquals(1, rejections.size());
        
        ExecutionEvent rejection = rejections.get(0);
        assertTrue(rejection.isRejected());
        assertEquals(RiskReasonCode.QUANTITY_TOO_LARGE, rejection.rejectReason());
    }
    
    @Test
    void testCreditCheckRejectsHighNotional() throws InterruptedException {
        // Create risk validator with credit limit
        RiskValidator validator = new CompositeRiskValidator.Builder()
            .add(new CreditCheckModule(500_000L, true))
            .build();
        
        engine = new MatchingEngine(eventBus, validator);
        engine.start();
        
        List<ExecutionEvent> rejections = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.ORDER_REJECTED, event -> {
            if (event.payload() instanceof ExecutionEvent exec) {
                rejections.add(exec);
                latch.countDown();
            }
        });
        
        // Create order with notional exceeding limit
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 10000L, 999L, 1  // notional = 1,000,000 > 500,000
        );
        
        Event orderEvent = Event.create(
            System.nanoTime(),
            1L,
            SourceId.OMS,
            EventType.ORDER_SUBMITTED,
            0L,
            order
        );
        
        eventBus.publish(orderEvent);
        
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Should receive rejection event");
        assertEquals(1, rejections.size());
        
        ExecutionEvent rejection = rejections.get(0);
        assertTrue(rejection.isRejected());
        assertEquals(RiskReasonCode.CREDIT_LIMIT_EXCEEDED, rejection.rejectReason());
    }
    
    @Test
    void testMarginCheckRejectsLargePosition() throws InterruptedException {
        // Create risk validator with position limit
        RiskValidator validator = new CompositeRiskValidator.Builder()
            .add(new MarginCheckModule(1000L, true))
            .build();
        
        engine = new MatchingEngine(eventBus, validator);
        engine.start();
        
        List<ExecutionEvent> rejections = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.ORDER_REJECTED, event -> {
            if (event.payload() instanceof ExecutionEvent exec) {
                rejections.add(exec);
                latch.countDown();
            }
        });
        
        // Create order with quantity exceeding position limit
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            5000L, 15000L, 999L, 1  // quantity > 1000
        );
        
        Event orderEvent = Event.create(
            System.nanoTime(),
            1L,
            SourceId.OMS,
            EventType.ORDER_SUBMITTED,
            0L,
            order
        );
        
        eventBus.publish(orderEvent);
        
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Should receive rejection event");
        assertEquals(1, rejections.size());
        
        ExecutionEvent rejection = rejections.get(0);
        assertTrue(rejection.isRejected());
        assertEquals(RiskReasonCode.POSITION_LIMIT_EXCEEDED, rejection.rejectReason());
    }
    
    @Test
    void testCompositeValidatorFailsFast() throws InterruptedException {
        // Create composite with multiple validators
        // First one should fail and stop chain
        RiskValidator validator = new CompositeRiskValidator.Builder()
            .add(new CreditCheckModule(100L, true))  // Will fail
            .add(new MarginCheckModule(1L, true))    // Should not be reached
            .build();
        
        engine = new MatchingEngine(eventBus, validator);
        engine.start();
        
        List<ExecutionEvent> rejections = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        eventBus.subscribe(EventType.ORDER_REJECTED, event -> {
            if (event.payload() instanceof ExecutionEvent exec) {
                rejections.add(exec);
                latch.countDown();
            }
        });
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 10000L, 999L, 1  // notional = 1,000,000 > 100
        );
        
        Event orderEvent = Event.create(
            System.nanoTime(),
            1L,
            SourceId.OMS,
            EventType.ORDER_SUBMITTED,
            0L,
            order
        );
        
        eventBus.publish(orderEvent);
        
        assertTrue(latch.await(1, TimeUnit.SECONDS), "Should receive rejection event");
        assertEquals(1, rejections.size());
        
        ExecutionEvent rejection = rejections.get(0);
        assertTrue(rejection.isRejected());
        // Should be credit check rejection (first validator)
        assertEquals(RiskReasonCode.CREDIT_LIMIT_EXCEEDED, rejection.rejectReason());
    }
}
