package com.thelastwar.orderbook;

/**
 * Types of financial instruments supported by the order book.
 */
public enum InstrumentType {
    /**
     * Equity instrument (stocks, ETFs).
     */
    EQUITY,
    
    /**
     * Bond instrument (corporate bonds, government bonds, treasury bills).
     */
    BOND,
    
    /**
     * Derivative instrument (futures, options, swaps).
     */
    DERIVATIVE
}
