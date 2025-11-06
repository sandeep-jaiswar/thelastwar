package com.thelastwar.ems;

import java.time.Duration;
import java.util.Map;

/**
 * StrategyConfig holds configuration parameters for execution strategies.
 * 
 * This is an immutable record that can be loaded from YAML/JSON configuration files.
 */
public record StrategyConfig(
    /** Unique identifier for this strategy instance */
    String strategyId,
    
    /** Type of strategy (TWAP, VWAP, IOC, ICEBERG) */
    StrategyType strategyType,
    
    /** Symbol to execute */
    String symbol,
    
    /** Total duration for the strategy execution */
    Duration duration,
    
    /** Number of child orders to generate (for TWAP) */
    Integer numSlices,
    
    /** Minimum child order size */
    Long minChildSize,
    
    /** Maximum child order size */
    Long maxChildSize,
    
    /** Price limit (optional) */
    Long priceLimit,
    
    /** Whether to allow partial fills */
    boolean allowPartialFills,
    
    /** Additional strategy-specific parameters */
    Map<String, Object> parameters
) {
    
    /**
     * Validates the configuration.
     * 
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (strategyId == null || strategyId.isBlank()) {
            throw new IllegalArgumentException("Strategy ID cannot be null or blank");
        }
        if (strategyType == null) {
            throw new IllegalArgumentException("Strategy type cannot be null");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or blank");
        }
        if (duration != null && duration.isNegative()) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
        if (numSlices != null && numSlices <= 0) {
            throw new IllegalArgumentException("Number of slices must be positive");
        }
        if (minChildSize != null && minChildSize <= 0) {
            throw new IllegalArgumentException("Min child size must be positive");
        }
        if (maxChildSize != null && maxChildSize <= 0) {
            throw new IllegalArgumentException("Max child size must be positive");
        }
        if (minChildSize != null && maxChildSize != null && minChildSize > maxChildSize) {
            throw new IllegalArgumentException("Min child size cannot exceed max child size");
        }
    }
    
    /**
     * Builder for StrategyConfig.
     */
    public static class Builder {
        private String strategyId;
        private StrategyType strategyType;
        private String symbol;
        private Duration duration;
        private Integer numSlices;
        private Long minChildSize;
        private Long maxChildSize;
        private Long priceLimit;
        private boolean allowPartialFills = true;
        private Map<String, Object> parameters = Map.of();
        
        public Builder strategyId(String strategyId) {
            this.strategyId = strategyId;
            return this;
        }
        
        public Builder strategyType(StrategyType strategyType) {
            this.strategyType = strategyType;
            return this;
        }
        
        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }
        
        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }
        
        public Builder numSlices(Integer numSlices) {
            this.numSlices = numSlices;
            return this;
        }
        
        public Builder minChildSize(Long minChildSize) {
            this.minChildSize = minChildSize;
            return this;
        }
        
        public Builder maxChildSize(Long maxChildSize) {
            this.maxChildSize = maxChildSize;
            return this;
        }
        
        public Builder priceLimit(Long priceLimit) {
            this.priceLimit = priceLimit;
            return this;
        }
        
        public Builder allowPartialFills(boolean allowPartialFills) {
            this.allowPartialFills = allowPartialFills;
            return this;
        }
        
        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
            return this;
        }
        
        public StrategyConfig build() {
            StrategyConfig config = new StrategyConfig(
                strategyId,
                strategyType,
                symbol,
                duration,
                numSlices,
                minChildSize,
                maxChildSize,
                priceLimit,
                allowPartialFills,
                parameters
            );
            config.validate();
            return config;
        }
    }
}
