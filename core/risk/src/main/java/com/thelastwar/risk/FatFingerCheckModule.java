package com.thelastwar.risk;

import com.thelastwar.eventbus.model.OrderEvent;

/**
 * Fat-finger check module.
 * Validates order size and price are within reasonable bounds.
 * 
 * Performance: < 1µs (simple comparisons)
 */
public class FatFingerCheckModule implements RiskValidator {
    
    private final long maxQuantity;
    private final long maxPrice;
    private final long minPrice;
    private final long maxNotional;
    private final boolean enabled;
    
    /**
     * Creates a fat-finger check module with specified limits.
     * 
     * @param maxQuantity Maximum order quantity (0 = unlimited)
     * @param maxPrice    Maximum order price (0 = unlimited)
     * @param minPrice    Minimum order price (0 = unlimited)
     * @param maxNotional Maximum notional value (0 = unlimited)
     * @param enabled     Whether this module is enabled
     */
    public FatFingerCheckModule(long maxQuantity, long maxPrice, long minPrice, long maxNotional, boolean enabled) {
        this.maxQuantity = maxQuantity;
        this.maxPrice = maxPrice;
        this.minPrice = minPrice;
        this.maxNotional = maxNotional;
        this.enabled = enabled;
    }
    
    /**
     * Creates a fat-finger check module with default settings.
     */
    public FatFingerCheckModule() {
        this(1_000_000L, 100_000_00L, 1L, 10_000_000_00L, true);
    }
    
    @Override
    public RiskDecision validate(OrderEvent order) {
        if (!enabled) {
            return RiskDecision.APPROVED;
        }
        
        // Check quantity
        if (maxQuantity > 0 && order.quantity() > maxQuantity) {
            return RiskDecision.reject(
                RiskReasonCode.QUANTITY_TOO_LARGE,
                "Order quantity " + order.quantity() + " exceeds maximum " + maxQuantity
            );
        }
        
        // Check price range (only for limit orders with non-zero price)
        if (order.price() > 0) {
            if (maxPrice > 0 && order.price() > maxPrice) {
                return RiskDecision.reject(
                    RiskReasonCode.PRICE_OUT_OF_RANGE,
                    "Order price " + order.price() + " exceeds maximum " + maxPrice
                );
            }
            
            if (minPrice > 0 && order.price() < minPrice) {
                return RiskDecision.reject(
                    RiskReasonCode.PRICE_OUT_OF_RANGE,
                    "Order price " + order.price() + " below minimum " + minPrice
                );
            }
        }
        
        // Check notional value
        if (maxNotional > 0) {
            long notional = order.quantity() * order.price();
            if (notional > maxNotional) {
                return RiskDecision.reject(
                    RiskReasonCode.NOTIONAL_VALUE_EXCEEDED,
                    "Order notional " + notional + " exceeds maximum " + maxNotional
                );
            }
        }
        
        return RiskDecision.APPROVED;
    }
}
