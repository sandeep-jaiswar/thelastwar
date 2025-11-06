package com.thelastwar.ems;

import com.thelastwar.oms.ChildOrder;
import com.thelastwar.oms.ClientOrderId;
import com.thelastwar.oms.InternalOrderId;
import com.thelastwar.oms.ParentOrder;
import com.thelastwar.orderbook.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TWAPStrategy.
 */
class TWAPStrategyTest {
    
    private ScheduledExecutorService scheduler;
    private MockEMSService mockEMS;
    private StrategyConfig config;
    private ParentOrder parentOrder;
    
    @BeforeEach
    void setUp() {
        scheduler = Executors.newScheduledThreadPool(2);
        mockEMS = new MockEMSService();
        
        config = new StrategyConfig.Builder()
            .strategyId("twap-test-001")
            .strategyType(StrategyType.TWAP)
            .symbol("AAPL")
            .duration(Duration.ofSeconds(5))
            .numSlices(5)
            .minChildSize(100L)
            .maxChildSize(1000L)
            .allowPartialFills(true)
            .build();
        
        parentOrder = new ParentOrder(
            new InternalOrderId(1L),
            new ClientOrderId("client-001"),
            "AAPL",
            Side.BUY.getValue(),
            com.thelastwar.orderbook.OrderType.LIMIT.getValue(),
            1000L,
            15000L,
            0L, // account
            System.nanoTime()
        );
    }
    
    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }
    
    @Test
    void testTWAPStrategyCreation() {
        TWAPStrategy strategy = new TWAPStrategy(config, mockEMS, scheduler);
        
        assertNotNull(strategy);
        assertEquals(StrategyState.CREATED, strategy.getState());
        assertEquals(config, strategy.getConfig());
    }
    
    @Test
    void testTWAPStrategyStart() throws Exception {
        TWAPStrategy strategy = new TWAPStrategy(config, mockEMS, scheduler);
        
        strategy.start(parentOrder);
        
        assertEquals(StrategyState.RUNNING, strategy.getState());
        
        // Verify schedule was created
        List<TWAPStrategy.ChildOrderSchedule> schedule = strategy.getSchedule();
        assertEquals(5, schedule.size());
        
        // Verify quantities add up to parent quantity
        long totalQuantity = schedule.stream()
            .mapToLong(TWAPStrategy.ChildOrderSchedule::quantity)
            .sum();
        assertEquals(1000L, totalQuantity);
    }
    
    @Test
    void testTWAPGeneratesChildOrders() throws Exception {
        TWAPStrategy strategy = new TWAPStrategy(config, mockEMS, scheduler);
        
        strategy.start(parentOrder);
        
        // Wait for at least 2 slices to be executed (2 seconds at 1 second intervals)
        Thread.sleep(2500);
        
        // Verify child orders were generated
        assertTrue(mockEMS.getChildOrders().size() >= 2);
        
        // Verify first child order
        ChildOrder firstChild = mockEMS.getChildOrders().get(0);
        assertEquals(200L, firstChild.quantity()); // 1000 / 5 = 200
        assertEquals(parentOrder.getParentOrderId(), firstChild.parentOrderId());
    }
    
    @Test
    void testTWAPScheduleWithRemainder() throws Exception {
        // Create order with quantity that doesn't divide evenly
        ParentOrder unevenOrder = new ParentOrder(
            new InternalOrderId(2L),
            new ClientOrderId("client-002"),
            "AAPL",
            Side.BUY.getValue(),
            com.thelastwar.orderbook.OrderType.LIMIT.getValue(),
            1003L, // Not evenly divisible by 5
            15000L,
            0L, // account
            System.nanoTime()
        );
        
        TWAPStrategy strategy = new TWAPStrategy(config, mockEMS, scheduler);
        strategy.start(unevenOrder);
        
        List<TWAPStrategy.ChildOrderSchedule> schedule = strategy.getSchedule();
        
        // First 4 slices should be 200 each, last should be 203
        assertEquals(200L, schedule.get(0).quantity());
        assertEquals(200L, schedule.get(1).quantity());
        assertEquals(200L, schedule.get(2).quantity());
        assertEquals(200L, schedule.get(3).quantity());
        assertEquals(203L, schedule.get(4).quantity()); // 200 + remainder of 3
    }
    
    @Test
    void testTWAPPauseAndResume() throws Exception {
        TWAPStrategy strategy = new TWAPStrategy(config, mockEMS, scheduler);
        strategy.start(parentOrder);
        
        // Wait for first slice
        Thread.sleep(100);
        
        // Pause
        strategy.pause();
        assertEquals(StrategyState.PAUSED, strategy.getState());
        
        int ordersAfterPause = mockEMS.getChildOrders().size();
        
        // Wait a bit - no new orders should be generated
        Thread.sleep(1500);
        assertEquals(ordersAfterPause, mockEMS.getChildOrders().size());
        
        // Resume
        strategy.resume();
        assertEquals(StrategyState.RUNNING, strategy.getState());
        
        // Wait for next slice
        Thread.sleep(1500);
        
        // Verify new orders were generated
        assertTrue(mockEMS.getChildOrders().size() > ordersAfterPause);
    }
    
    @Test
    void testTWAPStop() throws Exception {
        TWAPStrategy strategy = new TWAPStrategy(config, mockEMS, scheduler);
        strategy.start(parentOrder);
        
        // Wait for first slice
        Thread.sleep(100);
        
        // Stop
        strategy.stop();
        assertEquals(StrategyState.STOPPED, strategy.getState());
        
        int ordersAfterStop = mockEMS.getChildOrders().size();
        
        // Wait - no new orders should be generated
        Thread.sleep(2000);
        assertEquals(ordersAfterStop, mockEMS.getChildOrders().size());
    }
    
    @Test
    void testTWAPMetrics() throws Exception {
        TWAPStrategy strategy = new TWAPStrategy(config, mockEMS, scheduler);
        strategy.start(parentOrder);
        
        // Simulate fills
        FillEvent fill1 = new FillEvent(
            new InternalOrderId(10L),
            parentOrder.getParentOrderId(),
            Instant.now(),
            15000L,
            100L,
            100L,
            "NYSE",
            "exec-001"
        );
        strategy.onFill(fill1);
        
        StrategyMetrics metrics = strategy.getMetrics();
        
        assertNotNull(metrics);
        assertEquals("twap-test-001", metrics.strategyId());
        assertEquals(StrategyType.TWAP, metrics.strategyType());
        assertEquals(1L, metrics.fillsProcessed());
        assertTrue(metrics.childOrdersGenerated() > 0);
    }
    
    @Test
    void testTWAPRequiresDuration() {
        StrategyConfig invalidConfig = new StrategyConfig.Builder()
            .strategyId("twap-test-invalid")
            .strategyType(StrategyType.TWAP)
            .symbol("AAPL")
            .numSlices(5)
            .build();
        
        TWAPStrategy strategy = new TWAPStrategy(invalidConfig, mockEMS, scheduler);
        
        assertThrows(StrategyException.class, () -> strategy.start(parentOrder));
    }
    
    @Test
    void testTWAPRequiresNumSlices() {
        StrategyConfig invalidConfig = new StrategyConfig.Builder()
            .strategyId("twap-test-invalid")
            .strategyType(StrategyType.TWAP)
            .symbol("AAPL")
            .duration(Duration.ofSeconds(5))
            .build();
        
        TWAPStrategy strategy = new TWAPStrategy(invalidConfig, mockEMS, scheduler);
        
        assertThrows(StrategyException.class, () -> strategy.start(parentOrder));
    }
    
    @Test
    void testTWAPCompletesWhenParentFilled() throws Exception {
        TWAPStrategy strategy = new TWAPStrategy(config, mockEMS, scheduler);
        strategy.start(parentOrder);
        
        // Simulate parent being filled
        parentOrder.updateFill(1000L);
        
        // Trigger fill callback
        FillEvent fill = new FillEvent(
            new InternalOrderId(10L),
            parentOrder.getParentOrderId(),
            Instant.now(),
            15000L,
            1000L,
            0L,
            "NYSE",
            "exec-001"
        );
        strategy.onFill(fill);
        
        // Wait a bit for state to update
        Thread.sleep(100);
        
        // Strategy should be completed
        assertEquals(StrategyState.COMPLETED, strategy.getState());
    }
    
    /**
     * Mock EMS service for testing.
     */
    private static class MockEMSService extends EMSService {
        private final List<ChildOrder> childOrders = new ArrayList<>();
        
        public MockEMSService() {
            super(null, null);
        }
        
        @Override
        public void submitChildOrder(ChildOrder childOrder) {
            childOrders.add(childOrder);
        }
        
        public List<ChildOrder> getChildOrders() {
            return childOrders;
        }
    }
}
