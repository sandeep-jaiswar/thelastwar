package com.thelastwar.risk;

import com.thelastwar.eventbus.model.OrderEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RiskDecision.
 */
class RiskDecisionTest {
    
    @Test
    void testApprovedDecision() {
        RiskDecision decision = RiskDecision.approve();
        
        assertTrue(decision.approved());
        assertEquals(0, decision.reasonCode());
        assertNull(decision.message());
    }
    
    @Test
    void testApprovedSingleton() {
        RiskDecision decision1 = RiskDecision.approve();
        RiskDecision decision2 = RiskDecision.approve();
        
        assertSame(decision1, decision2, "Should return same singleton instance");
    }
    
    @Test
    void testRejectedDecision() {
        RiskDecision decision = RiskDecision.reject(
            RiskReasonCode.QUANTITY_TOO_LARGE,
            "Quantity exceeds limit"
        );
        
        assertFalse(decision.approved());
        assertEquals(RiskReasonCode.QUANTITY_TOO_LARGE, decision.reasonCode());
        assertEquals("Quantity exceeds limit", decision.message());
    }
    
    @Test
    void testReasonCodeDescriptions() {
        assertEquals("Insufficient credit", 
            RiskReasonCode.getDescription(RiskReasonCode.INSUFFICIENT_CREDIT));
        assertEquals("Quantity too large", 
            RiskReasonCode.getDescription(RiskReasonCode.QUANTITY_TOO_LARGE));
        assertEquals("Position limit exceeded", 
            RiskReasonCode.getDescription(RiskReasonCode.POSITION_LIMIT_EXCEEDED));
    }
}
