package com.thelastwar.risk;

import com.thelastwar.eventbus.model.OrderEvent;

/**
 * Credit risk validation module.
 * Checks if account has sufficient credit limit for the order.
 * 
 * Performance: < 1µs (simple comparison)
 */
public class CreditCheckModule implements RiskValidator {
    
    private final long maxNotionalPerOrder;
    private final boolean enabled;
    
    /**
     * Creates a credit check module with specified limits.
     * 
     * @param maxNotionalPerOrder Maximum notional value per order (0 = unlimited)
     * @param enabled             Whether this module is enabled
     */
    public CreditCheckModule(long maxNotionalPerOrder, boolean enabled) {
        this.maxNotionalPerOrder = maxNotionalPerOrder;
        this.enabled = enabled;
    }
    
    /**
     * Creates a credit check module with default settings.
     */
    public CreditCheckModule() {
        this(Long.MAX_VALUE, true);
    }
    
    @Override
    public RiskDecision validate(OrderEvent order) {
        if (!enabled) {
            return RiskDecision.APPROVED;
        }
        
        // Calculate notional value
        long notional = order.quantity() * order.price();
        
        if (maxNotionalPerOrder > 0 && notional > maxNotionalPerOrder) {
            return RiskDecision.reject(
                RiskReasonCode.CREDIT_LIMIT_EXCEEDED,
                "Order notional " + notional + " exceeds limit " + maxNotionalPerOrder
            );
        }
        
        return RiskDecision.APPROVED;
    }
}
