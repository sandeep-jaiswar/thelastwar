package com.thelastwar.orderbook;

/**
 * Ultra-efficient order book for bond instruments.
 * 
 * Bond-specific optimizations:
 * - Yield-based pricing support
 * - Large order size handling
 * - Less frequent price updates compared to equities
 * 
 * Performance targets:
 * - Add/update/remove: O(1)
 * - Best bid/ask: O(1) < 200 ns
 * - Heap usage: < 10 MB for 1M orders
 * 
 * @see OffHeapOrderBook
 */
public class BondOrderBook extends OffHeapOrderBook {
    
    /**
     * Creates a new bond order book.
     * 
     * @param symbol Bond identifier (e.g., "US10Y", "CORP_AAA_5Y")
     */
    public BondOrderBook(String symbol) {
        super(symbol);
    }
    
    /**
     * Gets the instrument type.
     * 
     * @return BOND
     */
    public InstrumentType getInstrumentType() {
        return InstrumentType.BOND;
    }
}
