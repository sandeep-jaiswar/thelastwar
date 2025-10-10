package com.thelastwar.risk;

import com.thelastwar.eventbus.model.OrderEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CreditCheckModule.
 */
class CreditCheckModuleTest {
    
    @Test
    void testApproveWithinLimit() {
        CreditCheckModule module = new CreditCheckModule(1_000_000L, true);
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 10000L, 999L, 1
        );
        
        RiskDecision decision = module.validate(order);
        assertTrue(decision.approved());
    }
    
    @Test
    void testRejectExceedingLimit() {
        CreditCheckModule module = new CreditCheckModule(500_000L, true);
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 10000L, 999L, 1  // notional = 1,000,000
        );
        
        RiskDecision decision = module.validate(order);
        assertFalse(decision.approved());
        assertEquals(RiskReasonCode.CREDIT_LIMIT_EXCEEDED, decision.reasonCode());
    }
    
    @Test
    void testDisabledModuleAlwaysApproves() {
        CreditCheckModule module = new CreditCheckModule(1L, false);
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            1_000_000L, 10000L, 999L, 1
        );
        
        RiskDecision decision = module.validate(order);
        assertTrue(decision.approved());
    }
    
    @Test
    void testDefaultConstructor() {
        CreditCheckModule module = new CreditCheckModule();
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            1_000_000L, 10000L, 999L, 1
        );
        
        RiskDecision decision = module.validate(order);
        assertTrue(decision.approved());
    }
}
