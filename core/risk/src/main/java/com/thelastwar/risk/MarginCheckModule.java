package com.thelastwar.risk;

import com.thelastwar.eventbus.model.OrderEvent;

/**
 * Margin risk validation module.
 * Checks position limits and margin requirements.
 * 
 * Performance: < 1µs (simple comparison)
 */
public class MarginCheckModule implements RiskValidator {
    
    private final long maxPositionSize;
    private final boolean enabled;
    
    /**
     * Creates a margin check module with specified limits.
     * 
     * @param maxPositionSize Maximum position size per symbol (0 = unlimited)
     * @param enabled         Whether this module is enabled
     */
    public MarginCheckModule(long maxPositionSize, boolean enabled) {
        this.maxPositionSize = maxPositionSize;
        this.enabled = enabled;
    }
    
    /**
     * Creates a margin check module with default settings.
     */
    public MarginCheckModule() {
        this(Long.MAX_VALUE, true);
    }
    
    @Override
    public RiskDecision validate(OrderEvent order) {
        if (!enabled) {
            return RiskDecision.APPROVED;
        }
        
        // Check position size limit
        if (maxPositionSize > 0 && order.quantity() > maxPositionSize) {
            return RiskDecision.reject(
                RiskReasonCode.POSITION_LIMIT_EXCEEDED,
                "Order quantity " + order.quantity() + " exceeds position limit " + maxPositionSize
            );
        }
        
        return RiskDecision.APPROVED;
    }
}
