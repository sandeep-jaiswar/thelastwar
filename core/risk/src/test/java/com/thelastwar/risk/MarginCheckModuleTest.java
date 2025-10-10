package com.thelastwar.risk;

import com.thelastwar.eventbus.model.OrderEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MarginCheckModule.
 */
class MarginCheckModuleTest {
    
    @Test
    void testApproveWithinLimit() {
        MarginCheckModule module = new MarginCheckModule(10_000L, true);
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            1000L, 10000L, 999L, 1
        );
        
        RiskDecision decision = module.validate(order);
        assertTrue(decision.approved());
    }
    
    @Test
    void testRejectExceedingPositionLimit() {
        MarginCheckModule module = new MarginCheckModule(1000L, true);
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            5000L, 10000L, 999L, 1
        );
        
        RiskDecision decision = module.validate(order);
        assertFalse(decision.approved());
        assertEquals(RiskReasonCode.POSITION_LIMIT_EXCEEDED, decision.reasonCode());
    }
    
    @Test
    void testDisabledModuleAlwaysApproves() {
        MarginCheckModule module = new MarginCheckModule(1L, false);
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            1_000_000L, 10000L, 999L, 1
        );
        
        RiskDecision decision = module.validate(order);
        assertTrue(decision.approved());
    }
}
