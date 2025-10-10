package com.thelastwar.risk;

import com.thelastwar.eventbus.model.OrderEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FatFingerCheckModule.
 */
class FatFingerCheckModuleTest {
    
    @Test
    void testApproveNormalOrder() {
        FatFingerCheckModule module = new FatFingerCheckModule();
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        RiskDecision decision = module.validate(order);
        assertTrue(decision.approved());
    }
    
    @Test
    void testRejectQuantityTooLarge() {
        FatFingerCheckModule module = new FatFingerCheckModule(
            1000L, 100_000_00L, 1L, 10_000_000_00L, true
        );
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            10000L, 10000L, 999L, 1
        );
        
        RiskDecision decision = module.validate(order);
        assertFalse(decision.approved());
        assertEquals(RiskReasonCode.QUANTITY_TOO_LARGE, decision.reasonCode());
    }
    
    @Test
    void testRejectPriceTooHigh() {
        FatFingerCheckModule module = new FatFingerCheckModule(
            1_000_000L, 20000L, 1L, 10_000_000_00L, true
        );
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 30000L, 999L, 1
        );
        
        RiskDecision decision = module.validate(order);
        assertFalse(decision.approved());
        assertEquals(RiskReasonCode.PRICE_OUT_OF_RANGE, decision.reasonCode());
    }
    
    @Test
    void testRejectPriceTooLow() {
        FatFingerCheckModule module = new FatFingerCheckModule(
            1_000_000L, 100_000_00L, 5000L, 10_000_000_00L, true
        );
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 100L, 999L, 1
        );
        
        RiskDecision decision = module.validate(order);
        assertFalse(decision.approved());
        assertEquals(RiskReasonCode.PRICE_OUT_OF_RANGE, decision.reasonCode());
    }
    
    @Test
    void testRejectNotionalTooLarge() {
        FatFingerCheckModule module = new FatFingerCheckModule(
            1_000_000L, 100_000_00L, 1L, 1_000_000L, true
        );
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            1000L, 10000L, 999L, 1  // notional = 10,000,000
        );
        
        RiskDecision decision = module.validate(order);
        assertFalse(decision.approved());
        assertEquals(RiskReasonCode.NOTIONAL_VALUE_EXCEEDED, decision.reasonCode());
    }
    
    @Test
    void testDisabledModuleAlwaysApproves() {
        FatFingerCheckModule module = new FatFingerCheckModule(
            1L, 1L, 999999L, 1L, false
        );
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            1_000_000L, 100L, 999L, 1
        );
        
        RiskDecision decision = module.validate(order);
        assertTrue(decision.approved());
    }
}
