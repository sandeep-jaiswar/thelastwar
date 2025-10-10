package com.thelastwar.risk;

/**
 * Immutable record representing the result of a risk validation check.
 * Designed for sub-microsecond allocation and validation.
 * 
 * @param approved    Whether the order is approved (true) or rejected (false)
 * @param reasonCode  Rejection reason code (0 if approved)
 * @param message     Optional human-readable message (null if approved)
 */
public record RiskDecision(boolean approved, int reasonCode, String message) {
    
    /**
     * Singleton instance for approved decisions (GC-neutral).
     */
    public static final RiskDecision APPROVED = new RiskDecision(true, 0, null);
    
    /**
     * Factory method for creating a rejection decision.
     * 
     * @param reasonCode Rejection reason code
     * @param message    Human-readable rejection message
     * @return new RiskDecision representing rejection
     */
    public static RiskDecision reject(int reasonCode, String message) {
        return new RiskDecision(false, reasonCode, message);
    }
    
    /**
     * Factory method for creating an approval decision.
     * Uses singleton to avoid allocation.
     * 
     * @return APPROVED singleton instance
     */
    public static RiskDecision approve() {
        return APPROVED;
    }
}
