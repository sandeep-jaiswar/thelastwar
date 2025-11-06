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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VWAPStrategy.
 */
class VWAPStrategyTest {
    
    private ScheduledExecutorService scheduler;
    private MockEMSService mockEMS;
    private StrategyConfig config;
    private ParentOrder parentOrder;
    
    @BeforeEach
    void setUp() {
        scheduler = Executors.newScheduledThreadPool(2);
        mockEMS = new MockEMSService();
        
        Map<String, Object> params = new HashMap<>();
        params.put("expectedDayVolume", 100000L);
        
        config = new StrategyConfig.Builder()
            .strategyId("vwap-test-001")
            .strategyType(StrategyType.VWAP)
            .symbol("AAPL")
            .duration(Duration.ofSeconds(10))
            .minChildSize(100L)
            .maxChildSize(1000L)
            .allowPartialFills(true)
            .parameters(params)
            .build();
        
        parentOrder = new ParentOrder(
            new InternalOrderId(1L),
            new ClientOrderId("client-001"),
            "AAPL",
            Side.BUY.getValue(),
            com.thelastwar.orderbook.OrderType.LIMIT.getValue(),
            10000L,
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
    void testVWAPStrategyCreation() {
        VWAPStrategy strategy = new VWAPStrategy(config, mockEMS, scheduler);
        
        assertNotNull(strategy);
        assertEquals(StrategyState.CREATED, strategy.getState());
        assertEquals(config, strategy.getConfig());
    }
    
    @Test
    void testVWAPStrategyStart() throws Exception {
        VWAPStrategy strategy = new VWAPStrategy(config, mockEMS, scheduler);
        
        strategy.start(parentOrder);
        
        assertEquals(StrategyState.RUNNING, strategy.getState());
    }
    
    @Test
    void testVWAPReactsToMarketTicks() throws Exception {
        VWAPStrategy strategy = new VWAPStrategy(config, mockEMS, scheduler);
        strategy.start(parentOrder);
        
        // Send market ticks with increasing volume
        for (int i = 1; i <= 5; i++) {
            MarketTick tick = new MarketTick(
                "AAPL",
                Instant.now(),
                14950L,
                15050L,
                1000L,
                1000L,
                15000L,
                100L,
                i * 10000L // Increasing cumulative volume
            );
            strategy.onTick(tick);
            Thread.sleep(100);
        }
        
        // Wait for strategy to react
        Thread.sleep(2000);
        
        // Verify ticks were processed
        StrategyMetrics metrics = strategy.getMetrics();
        assertTrue(metrics.ticksProcessed() >= 5);
    }
    
    @Test
    void testVWAPGeneratesChildOrdersBasedOnVolume() throws Exception {
        VWAPStrategy strategy = new VWAPStrategy(config, mockEMS, scheduler);
        strategy.start(parentOrder);
        
        // Simulate market volume progress
        MarketTick tick1 = new MarketTick(
            "AAPL",
            Instant.now(),
            14950L,
            15050L,
            1000L,
            1000L,
            15000L,
            100L,
            10000L // 10% of expected day volume
        );
        strategy.onTick(tick1);
        
        // Wait for strategy to execute
        Thread.sleep(2000);
        
        // Should have generated some child orders
        assertTrue(mockEMS.getChildOrders().size() > 0);
    }
    
    @Test
    void testVWAPTargetQuantity() throws Exception {
        VWAPStrategy strategy = new VWAPStrategy(config, mockEMS, scheduler);
        strategy.start(parentOrder);
        
        // Simulate 20% volume progress
        MarketTick tick = new MarketTick(
            "AAPL",
            Instant.now(),
            14950L,
            15050L,
            1000L,
            1000L,
            15000L,
            100L,
            20000L // 20% of expected day volume (100,000)
        );
        strategy.onTick(tick);
        
        // Target should be approximately 20% of parent quantity
        long target = strategy.getTargetExecutedQuantity();
        assertTrue(target > 0);
        assertTrue(target <= parentOrder.getTotalQuantity());
    }
    
    @Test
    void testVWAPPauseAndResume() throws Exception {
        VWAPStrategy strategy = new VWAPStrategy(config, mockEMS, scheduler);
        strategy.start(parentOrder);
        
        // Send some ticks
        MarketTick tick = new MarketTick(
            "AAPL",
            Instant.now(),
            14950L,
            15050L,
            1000L,
            1000L,
            15000L,
            100L,
            10000L
        );
        strategy.onTick(tick);
        
        Thread.sleep(100);
        
        // Pause
        strategy.pause();
        assertEquals(StrategyState.PAUSED, strategy.getState());
        
        int ordersAfterPause = mockEMS.getChildOrders().size();
        
        // Wait - should not generate new orders
        Thread.sleep(2000);
        assertEquals(ordersAfterPause, mockEMS.getChildOrders().size());
        
        // Resume
        strategy.resume();
        assertEquals(StrategyState.RUNNING, strategy.getState());
    }
    
    @Test
    void testVWAPStop() throws Exception {
        VWAPStrategy strategy = new VWAPStrategy(config, mockEMS, scheduler);
        strategy.start(parentOrder);
        
        Thread.sleep(100);
        
        // Stop
        strategy.stop();
        assertEquals(StrategyState.STOPPED, strategy.getState());
        
        int ordersAfterStop = mockEMS.getChildOrders().size();
        
        // Wait - no new orders
        Thread.sleep(2000);
        assertEquals(ordersAfterStop, mockEMS.getChildOrders().size());
    }
    
    @Test
    void testVWAPHandlesFills() throws Exception {
        VWAPStrategy strategy = new VWAPStrategy(config, mockEMS, scheduler);
        strategy.start(parentOrder);
        
        // Simulate fills
        FillEvent fill1 = new FillEvent(
            new InternalOrderId(10L),
            parentOrder.getParentOrderId(),
            Instant.now(),
            15000L,
            500L,
            500L,
            "NYSE",
            "exec-001"
        );
        
        parentOrder.updateFill(500L);
        strategy.onFill(fill1);
        
        StrategyMetrics metrics = strategy.getMetrics();
        assertEquals(1L, metrics.fillsProcessed());
        assertEquals(500L, metrics.filledQuantity());
    }
    
    @Test
    void testVWAPMetrics() throws Exception {
        VWAPStrategy strategy = new VWAPStrategy(config, mockEMS, scheduler);
        strategy.start(parentOrder);
        
        // Send ticks
        for (int i = 0; i < 3; i++) {
            MarketTick tick = new MarketTick(
                "AAPL",
                Instant.now(),
                14950L,
                15050L,
                1000L,
                1000L,
                15000L,
                100L,
                10000L * (i + 1)
            );
            strategy.onTick(tick);
        }
        
        Thread.sleep(500);
        
        StrategyMetrics metrics = strategy.getMetrics();
        
        assertNotNull(metrics);
        assertEquals("vwap-test-001", metrics.strategyId());
        assertEquals(StrategyType.VWAP, metrics.strategyType());
        assertTrue(metrics.ticksProcessed() >= 3);
    }
    
    @Test
    void testVWAPRequiresDuration() {
        StrategyConfig invalidConfig = new StrategyConfig.Builder()
            .strategyId("vwap-test-invalid")
            .strategyType(StrategyType.VWAP)
            .symbol("AAPL")
            .minChildSize(100L)
            .build();
        
        VWAPStrategy strategy = new VWAPStrategy(invalidConfig, mockEMS, scheduler);
        
        assertThrows(StrategyException.class, () -> strategy.start(parentOrder));
    }
    
    @Test
    void testVWAPCompletesWhenParentFilled() throws Exception {
        VWAPStrategy strategy = new VWAPStrategy(config, mockEMS, scheduler);
        strategy.start(parentOrder);
        
        // Simulate parent being fully filled
        parentOrder.updateFill(10000L);
        
        FillEvent fill = new FillEvent(
            new InternalOrderId(10L),
            parentOrder.getParentOrderId(),
            Instant.now(),
            15000L,
            10000L,
            0L,
            "NYSE",
            "exec-001"
        );
        strategy.onFill(fill);
        
        // Wait for state update
        Thread.sleep(100);
        
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
