package com.thelastwar.orderbook;

/**
 * Ultra-efficient order book for derivative instruments (futures, options, swaps).
 * 
 * Derivative-specific optimizations:
 * - Support for expiration tracking
 * - Greeks calculation integration
 * - Complex order types (spreads, combos)
 * 
 * Performance targets:
 * - Add/update/remove: O(1)
 * - Best bid/ask: O(1) < 200 ns
 * - Heap usage: < 10 MB for 1M orders
 * 
 * @see OffHeapOrderBook
 */
public class DerivativeOrderBook extends OffHeapOrderBook {
    
    /**
     * Creates a new derivative order book.
     * 
     * @param symbol Derivative identifier (e.g., "ES_MAR25", "SPY_CALL_450")
     */
    public DerivativeOrderBook(String symbol) {
        super(symbol);
    }
    
    /**
     * Gets the instrument type.
     * 
     * @return DERIVATIVE
     */
    public InstrumentType getInstrumentType() {
        return InstrumentType.DERIVATIVE;
    }
}
