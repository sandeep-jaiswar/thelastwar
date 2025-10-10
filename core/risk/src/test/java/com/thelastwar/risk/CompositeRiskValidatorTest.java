package com.thelastwar.risk;

import com.thelastwar.eventbus.model.OrderEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CompositeRiskValidator.
 */
class CompositeRiskValidatorTest {
    
    @Test
    void testAllValidatorsApprove() {
        RiskValidator validator = new CompositeRiskValidator.Builder()
            .add(order -> RiskDecision.APPROVED)
            .add(order -> RiskDecision.APPROVED)
            .add(order -> RiskDecision.APPROVED)
            .build();
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        RiskDecision decision = validator.validate(order);
        assertTrue(decision.approved());
    }
    
    @Test
    void testFirstValidatorRejects() {
        RiskValidator validator = new CompositeRiskValidator.Builder()
            .add(order -> RiskDecision.reject(100, "First rejection"))
            .add(order -> RiskDecision.APPROVED)
            .build();
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        RiskDecision decision = validator.validate(order);
        assertFalse(decision.approved());
        assertEquals(100, decision.reasonCode());
        assertEquals("First rejection", decision.message());
    }
    
    @Test
    void testSecondValidatorRejects() {
        RiskValidator validator = new CompositeRiskValidator.Builder()
            .add(order -> RiskDecision.APPROVED)
            .add(order -> RiskDecision.reject(200, "Second rejection"))
            .add(order -> RiskDecision.APPROVED)
            .build();
        
        OrderEvent order = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        
        RiskDecision decision = validator.validate(order);
        assertFalse(decision.approved());
        assertEquals(200, decision.reasonCode());
    }
    
    @Test
    void testIntegrationWithRealModules() {
        RiskValidator validator = new CompositeRiskValidator.Builder()
            .add(new CreditCheckModule(500_000L, true))
            .add(new MarginCheckModule(1000L, true))
            .add(new FatFingerCheckModule(10_000L, 100_000_00L, 1L, 10_000_000_00L, true))
            .build();
        
        // Should pass all checks
        OrderEvent goodOrder = OrderEvent.newOrder(
            1L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 1000L, 999L, 1
        );
        
        RiskDecision decision = validator.validate(goodOrder);
        assertTrue(decision.approved());
        
        // Should fail credit check
        OrderEvent badOrder = OrderEvent.newOrder(
            2L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 10000L, 999L, 1  // notional = 1,000,000 > 500,000
        );
        
        decision = validator.validate(badOrder);
        assertFalse(decision.approved());
        assertEquals(RiskReasonCode.CREDIT_LIMIT_EXCEEDED, decision.reasonCode());
    }
}
