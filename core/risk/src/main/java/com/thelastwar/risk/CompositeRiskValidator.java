package com.thelastwar.risk;

import com.thelastwar.eventbus.model.OrderEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite risk validator that chains multiple risk validators.
 * Executes validators sequentially and fails fast on first rejection.
 * 
 * Performance characteristics:
 * - Sequential execution (no parallelization overhead)
 * - Fail-fast: stops on first rejection
 * - Zero allocation when all validators approve
 */
public class CompositeRiskValidator implements RiskValidator {
    
    private final List<RiskValidator> validators;
    
    /**
     * Creates a composite validator with the given validators.
     * 
     * @param validators List of validators to chain
     */
    public CompositeRiskValidator(List<RiskValidator> validators) {
        this.validators = new ArrayList<>(validators);
    }
    
    /**
     * Builder for creating composite validators.
     */
    public static class Builder {
        private final List<RiskValidator> validators = new ArrayList<>();
        
        public Builder add(RiskValidator validator) {
            validators.add(validator);
            return this;
        }
        
        public CompositeRiskValidator build() {
            return new CompositeRiskValidator(validators);
        }
    }
    
    @Override
    public RiskDecision validate(OrderEvent order) {
        for (RiskValidator validator : validators) {
            RiskDecision decision = validator.validate(order);
            if (!decision.approved()) {
                return decision; // Fail fast
            }
        }
        return RiskDecision.APPROVED;
    }
}
