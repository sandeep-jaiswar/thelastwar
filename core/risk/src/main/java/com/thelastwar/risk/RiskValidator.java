package com.thelastwar.risk;

import com.thelastwar.eventbus.model.OrderEvent;

/**
 * Interface for synchronous pre-trade risk validation.
 * 
 * Implementations must be:
 * - Thread-safe (can be called concurrently)
 * - Fast (< 5µs per validation)
 * - Deterministic (same input produces same output)
 * - Non-blocking
 * 
 * Performance targets:
 * - p99 latency: < 5µs
 * - p999 latency: < 10µs
 * - Zero allocation in hot path (use RiskDecision.APPROVED singleton)
 */
@FunctionalInterface
public interface RiskValidator {
    
    /**
     * Validates an order against risk rules.
     * 
     * @param order Order to validate
     * @return RiskDecision indicating approval or rejection
     */
    RiskDecision validate(OrderEvent order);
}
