package com.thelastwar.orderbook;

/**
 * Ultra-efficient order book for equity instruments (stocks, ETFs).
 * 
 * Equity-specific optimizations:
 * - Standard tick size for price levels
 * - High-frequency trading optimizations
 * - Market maker support with tight spreads
 * 
 * Performance targets:
 * - Add/update/remove: O(1)
 * - Best bid/ask: O(1) < 200 ns
 * - Heap usage: < 10 MB for 1M orders
 * 
 * @see OffHeapOrderBook
 */
public class EquityOrderBook extends OffHeapOrderBook {
    
    /**
     * Creates a new equity order book.
     * 
     * @param symbol Equity symbol (e.g., "AAPL", "GOOGL")
     */
    public EquityOrderBook(String symbol) {
        super(symbol);
    }
    
    /**
     * Gets the instrument type.
     * 
     * @return EQUITY
     */
    public InstrumentType getInstrumentType() {
        return InstrumentType.EQUITY;
    }
}
