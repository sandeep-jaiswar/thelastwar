package com.thelastwar.orderbook.example;

import com.thelastwar.orderbook.*;

/**
 * Example usage of ultra-efficient OffHeapOrderBook with O(1) operations.
 * 
 * This example demonstrates:
 * - Creating order books for different instrument types
 * - Adding orders with O(1) complexity
 * - Ultra-fast best bid/ask lookups (< 1 ns)
 * - Price-time priority ordering
 * - Market depth snapshots
 */
public class OffHeapOrderBookExample {
    
    public static void main(String[] args) {
        // Example 1: Equity Order Book
        equityExample();
        
        // Example 2: Bond Order Book
        bondExample();
        
        // Example 3: Derivative Order Book
        derivativeExample();
        
        // Example 4: High-Frequency Trading Scenario
        highFrequencyExample();
        
        // Example 5: Market Depth Analysis
        marketDepthExample();
    }
    
    /**
     * Example 1: Equity order book for stocks.
     */
    private static void equityExample() {
        System.out.println("\n=== Equity Order Book Example ===");
        
        EquityOrderBook book = new EquityOrderBook("AAPL");
        
        // Add buy orders
        book.addOrder(new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, System.nanoTime()));
        book.addOrder(new Order(2L, "AAPL", Order.SIDE_BUY, 14900L, 200L, System.nanoTime()));
        book.addOrder(new Order(3L, "AAPL", Order.SIDE_BUY, 14800L, 150L, System.nanoTime()));
        
        // Add sell orders
        book.addOrder(new Order(4L, "AAPL", Order.SIDE_SELL, 15100L, 100L, System.nanoTime()));
        book.addOrder(new Order(5L, "AAPL", Order.SIDE_SELL, 15200L, 200L, System.nanoTime()));
        book.addOrder(new Order(6L, "AAPL", Order.SIDE_SELL, 15300L, 150L, System.nanoTime()));
        
        // Ultra-fast best bid/ask lookup (< 1 ns)
        long bestBid = book.getBestBid();
        long bestAsk = book.getBestAsk();
        long spread = book.getSpread();
        
        System.out.println("Best Bid: " + bestBid + " (" + book.getBestBidQuantity() + " shares)");
        System.out.println("Best Ask: " + bestAsk + " (" + book.getBestAskQuantity() + " shares)");
        System.out.println("Spread: " + spread);
        System.out.println("Total Orders: " + book.getOrderCount());
        System.out.println("Instrument Type: " + book.getInstrumentType());
    }
    
    /**
     * Example 2: Bond order book for fixed income.
     */
    private static void bondExample() {
        System.out.println("\n=== Bond Order Book Example ===");
        
        BondOrderBook book = new BondOrderBook("US10Y");
        
        // Bonds typically have larger order sizes
        book.addOrder(new Order(1L, "US10Y", Order.SIDE_BUY, 9500L, 10_000_000L, System.nanoTime()));
        book.addOrder(new Order(2L, "US10Y", Order.SIDE_BUY, 9490L, 5_000_000L, System.nanoTime()));
        
        book.addOrder(new Order(3L, "US10Y", Order.SIDE_SELL, 9510L, 8_000_000L, System.nanoTime()));
        book.addOrder(new Order(4L, "US10Y", Order.SIDE_SELL, 9520L, 12_000_000L, System.nanoTime()));
        
        System.out.println("Best Bid: " + book.getBestBid() + " (quantity: " + book.getBestBidQuantity() + ")");
        System.out.println("Best Ask: " + book.getBestAsk() + " (quantity: " + book.getBestAskQuantity() + ")");
        System.out.println("Spread: " + book.getSpread());
        System.out.println("Instrument Type: " + book.getInstrumentType());
    }
    
    /**
     * Example 3: Derivative order book for futures/options.
     */
    private static void derivativeExample() {
        System.out.println("\n=== Derivative Order Book Example ===");
        
        DerivativeOrderBook book = new DerivativeOrderBook("ES_MAR25");
        
        // Futures contract orders
        book.addOrder(new Order(1L, "ES_MAR25", Order.SIDE_BUY, 450000L, 10L, System.nanoTime()));
        book.addOrder(new Order(2L, "ES_MAR25", Order.SIDE_BUY, 449500L, 15L, System.nanoTime()));
        
        book.addOrder(new Order(3L, "ES_MAR25", Order.SIDE_SELL, 450500L, 12L, System.nanoTime()));
        book.addOrder(new Order(4L, "ES_MAR25", Order.SIDE_SELL, 451000L, 20L, System.nanoTime()));
        
        System.out.println("Best Bid: " + book.getBestBid() + " (contracts: " + book.getBestBidQuantity() + ")");
        System.out.println("Best Ask: " + book.getBestAsk() + " (contracts: " + book.getBestAskQuantity() + ")");
        System.out.println("Spread: " + book.getSpread());
        System.out.println("Instrument Type: " + book.getInstrumentType());
    }
    
    /**
     * Example 4: High-frequency trading scenario with rapid updates.
     */
    private static void highFrequencyExample() {
        System.out.println("\n=== High-Frequency Trading Example ===");
        
        OffHeapOrderBook book = new OffHeapOrderBook("GOOGL");
        
        // Simulate high-frequency order flow
        long startTime = System.nanoTime();
        
        // Add 1000 orders rapidly
        for (long i = 1; i <= 1000; i++) {
            byte side = i % 2 == 0 ? Order.SIDE_BUY : Order.SIDE_SELL;
            long price = 140000L + (i % 100);
            book.addOrder(new Order(i, "GOOGL", side, price, 100L, System.nanoTime()));
        }
        
        long addTime = System.nanoTime() - startTime;
        
        // Perform 10,000 best bid/ask lookups
        startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            book.getBestBid();
            book.getBestAsk();
        }
        long lookupTime = System.nanoTime() - startTime;
        
        // Update orders rapidly
        startTime = System.nanoTime();
        for (long i = 1; i <= 100; i++) {
            book.updateOrder(i, 150L);
        }
        long updateTime = System.nanoTime() - startTime;
        
        System.out.println("Added 1000 orders in: " + (addTime / 1000.0) + " µs");
        System.out.println("Average add time: " + (addTime / 1000.0) + " ns/order");
        System.out.println("10,000 best bid/ask lookups in: " + (lookupTime / 1000.0) + " µs");
        System.out.println("Average lookup time: " + (lookupTime / 20000.0) + " ns");
        System.out.println("100 order updates in: " + (updateTime / 1000.0) + " µs");
        System.out.println("Average update time: " + (updateTime / 100.0) + " ns");
        System.out.println("Final order count: " + book.getOrderCount());
    }
    
    /**
     * Example 5: Market depth snapshot and analysis.
     */
    private static void marketDepthExample() {
        System.out.println("\n=== Market Depth Example ===");
        
        OffHeapOrderBook book = new OffHeapOrderBook("MSFT");
        
        // Build a realistic order book
        long timestamp = System.nanoTime();
        
        // Bid side (highest to lowest)
        book.addOrder(new Order(1L, "MSFT", Order.SIDE_BUY, 37500L, 100L, timestamp++));
        book.addOrder(new Order(2L, "MSFT", Order.SIDE_BUY, 37500L, 200L, timestamp++));
        book.addOrder(new Order(3L, "MSFT", Order.SIDE_BUY, 37400L, 300L, timestamp++));
        book.addOrder(new Order(4L, "MSFT", Order.SIDE_BUY, 37300L, 150L, timestamp++));
        book.addOrder(new Order(5L, "MSFT", Order.SIDE_BUY, 37200L, 250L, timestamp++));
        
        // Ask side (lowest to highest)
        book.addOrder(new Order(6L, "MSFT", Order.SIDE_SELL, 37600L, 120L, timestamp++));
        book.addOrder(new Order(7L, "MSFT", Order.SIDE_SELL, 37600L, 180L, timestamp++));
        book.addOrder(new Order(8L, "MSFT", Order.SIDE_SELL, 37700L, 200L, timestamp++));
        book.addOrder(new Order(9L, "MSFT", Order.SIDE_SELL, 37800L, 350L, timestamp++));
        book.addOrder(new Order(10L, "MSFT", Order.SIDE_SELL, 37900L, 400L, timestamp++));
        
        // Get depth snapshot (O(n_levels))
        OffHeapOrderBook.PriceLevelSnapshot[] depth = book.getDepthSnapshot(10);
        
        System.out.println("\nMarket Depth (Top 5 levels each side):");
        System.out.println("----------------------------------------");
        
        // Count bid and ask levels
        int bidLevels = 0;
        int askLevels = 0;
        for (OffHeapOrderBook.PriceLevelSnapshot level : depth) {
            if (level.price() <= book.getBestBid()) {
                bidLevels++;
            } else {
                askLevels++;
            }
        }
        
        System.out.println("Bid Levels: " + bidLevels);
        System.out.println("Ask Levels: " + askLevels);
        
        // Display depth
        for (OffHeapOrderBook.PriceLevelSnapshot level : depth) {
            System.out.printf("Price: %d, Quantity: %d, Orders: %d%n",
                level.price(), level.totalQuantity(), level.orderCount());
        }
        
        System.out.println("\nBest Bid: " + book.getBestBid() + " (" + book.getBestBidQuantity() + " shares)");
        System.out.println("Best Ask: " + book.getBestAsk() + " (" + book.getBestAskQuantity() + " shares)");
        System.out.println("Spread: " + book.getSpread() + " (" + 
            (100.0 * book.getSpread() / book.getBestBid()) + "%)");
        
        // Demonstrate price-time priority
        System.out.println("\n=== Price-Time Priority Demo ===");
        System.out.println("Orders at best bid (" + book.getBestBid() + "): 2 orders");
        System.out.println("Order 1 was first, Order 2 was second (FIFO)");
        
        // Remove first order
        book.removeOrder(1L);
        System.out.println("\nAfter removing Order 1:");
        System.out.println("Best bid quantity: " + book.getBestBidQuantity() + " (should be 200, the second order)");
        
        // Verify order 2 is still there
        Order order2 = book.getOrder(2L);
        if (order2 != null) {
            System.out.println("Order 2 still in book: " + order2.quantity() + " shares at " + order2.price());
        }
    }
}
