package com.thelastwar.orderbook.example;

import com.thelastwar.orderbook.LimitOrderBook;
import com.thelastwar.orderbook.Order;
import com.thelastwar.orderbook.PriceLevel;

import java.util.List;

/**
 * Example demonstrating LimitOrderBook usage and features.
 * 
 * Run with: java -cp ... com.thelastwar.orderbook.example.OrderBookExample
 */
public class OrderBookExample {
    
    public static void main(String[] args) {
        System.out.println("=== Order Book Example ===\n");
        
        exampleBasicOperations();
        System.out.println();
        
        examplePriceTimePriority();
        System.out.println();
        
        exampleMarketDepth();
        System.out.println();
        
        exampleRealisticTrading();
    }
    
    /**
     * Example 1: Basic add, remove, modify operations.
     */
    private static void exampleBasicOperations() {
        System.out.println("--- Example 1: Basic Operations ---");
        
        LimitOrderBook book = new LimitOrderBook("AAPL");
        
        // Add a buy order
        Order buyOrder = new Order(
                1L,                 // orderId
                "AAPL",             // symbol
                Order.SIDE_BUY,     // side
                15000L,             // price ($150.00 in cents)
                100L,               // quantity
                System.nanoTime()   // timestamp
        );
        book.addOrder(buyOrder);
        System.out.println("Added buy order: ID=" + buyOrder.orderId() + 
                          " price=$" + (buyOrder.price() / 100.0) + 
                          " qty=" + buyOrder.quantity());
        
        // Add a sell order
        Order sellOrder = new Order(
                2L,
                "AAPL",
                Order.SIDE_SELL,
                15100L,             // price ($151.00 in cents)
                150L,
                System.nanoTime()
        );
        book.addOrder(sellOrder);
        System.out.println("Added sell order: ID=" + sellOrder.orderId() + 
                          " price=$" + (sellOrder.price() / 100.0) + 
                          " qty=" + sellOrder.quantity());
        
        // Check market state
        System.out.println("\nMarket State:");
        System.out.println("  Best Bid: $" + (book.getBestBid() / 100.0) + 
                          " (" + book.getBestBidQuantity() + " shares)");
        System.out.println("  Best Ask: $" + (book.getBestAsk() / 100.0) + 
                          " (" + book.getBestAskQuantity() + " shares)");
        System.out.println("  Spread: $" + (book.getSpread() / 100.0));
        
        // Modify order
        System.out.println("\nModifying buy order quantity to 200...");
        book.modifyOrder(1L, 0, 200L);
        System.out.println("  New bid quantity: " + book.getBestBidQuantity());
        
        // Remove order
        System.out.println("\nRemoving sell order...");
        Order removed = book.removeOrder(2L);
        System.out.println("  Removed: ID=" + removed.orderId());
        System.out.println("  Best Ask after removal: $" + (book.getBestAsk() / 100.0));
    }
    
    /**
     * Example 2: Price-Time Priority demonstration.
     */
    private static void examplePriceTimePriority() {
        System.out.println("--- Example 2: Price-Time Priority ---");
        
        LimitOrderBook book = new LimitOrderBook("AAPL");
        long price = 15000L;
        
        // Add multiple orders at the same price with different timestamps
        System.out.println("Adding 3 orders at the same price ($150.00):");
        
        Order order1 = new Order(1L, "AAPL", Order.SIDE_BUY, price, 100L, 1000L);
        book.addOrder(order1);
        System.out.println("  Order 1: timestamp=1000, qty=100");
        
        // Wait a bit to ensure different timestamp
        try { Thread.sleep(1); } catch (InterruptedException e) {}
        
        Order order2 = new Order(2L, "AAPL", Order.SIDE_BUY, price, 200L, 2000L);
        book.addOrder(order2);
        System.out.println("  Order 2: timestamp=2000, qty=200");
        
        try { Thread.sleep(1); } catch (InterruptedException e) {}
        
        Order order3 = new Order(3L, "AAPL", Order.SIDE_BUY, price, 150L, 3000L);
        book.addOrder(order3);
        System.out.println("  Order 3: timestamp=3000, qty=150");
        
        // Get the price level to show FIFO order
        List<PriceLevel> levels = book.getBidLevels(1);
        PriceLevel level = levels.get(0);
        
        System.out.println("\nTotal at $150.00: " + level.getTotalQuantity() + " shares");
        System.out.println("Number of orders: " + level.getOrderCount());
        System.out.println("\nFIFO Order (price-time priority):");
        
        // Poll orders to show they come out in time priority
        int position = 1;
        Order order;
        while ((order = level.poll()) != null) {
            System.out.println("  Position " + position + ": Order " + order.orderId() + 
                             " (qty=" + order.quantity() + ", timestamp=" + order.timestamp() + ")");
            position++;
        }
    }
    
    /**
     * Example 3: Market depth visualization.
     */
    private static void exampleMarketDepth() {
        System.out.println("--- Example 3: Market Depth ---");
        
        LimitOrderBook book = new LimitOrderBook("AAPL");
        
        // Build a realistic order book
        long basePrice = 15000L;
        
        // Add buy orders (bids)
        for (int i = 0; i < 5; i++) {
            book.addOrder(new Order(
                    i + 1L,
                    "AAPL",
                    Order.SIDE_BUY,
                    basePrice - i * 10L,
                    100L * (i + 1),
                    System.nanoTime()
            ));
        }
        
        // Add sell orders (asks)
        for (int i = 0; i < 5; i++) {
            book.addOrder(new Order(
                    i + 10L,
                    "AAPL",
                    Order.SIDE_SELL,
                    basePrice + 10L + i * 10L,
                    100L * (i + 1),
                    System.nanoTime()
            ));
        }
        
        // Display market depth
        System.out.println("Market Depth for AAPL:\n");
        System.out.println("       BIDS                    ASKS");
        System.out.println("  Price    Qty    |    Price    Qty");
        System.out.println("----------------------------------------");
        
        List<PriceLevel> bids = book.getBidLevels(5);
        List<PriceLevel> asks = book.getAskLevels(5);
        
        for (int i = 0; i < 5; i++) {
            PriceLevel bid = bids.get(i);
            PriceLevel ask = asks.get(i);
            
            System.out.printf("$%6.2f  %4d    |  $%6.2f  %4d%n",
                    bid.getPrice() / 100.0,
                    bid.getTotalQuantity(),
                    ask.getPrice() / 100.0,
                    ask.getTotalQuantity()
            );
        }
        
        System.out.println("\nMid Price: $" + 
                (((book.getBestBid() + book.getBestAsk()) / 2.0) / 100.0));
        System.out.println("Spread: $" + (book.getSpread() / 100.0));
    }
    
    /**
     * Example 4: Realistic trading scenario.
     */
    private static void exampleRealisticTrading() {
        System.out.println("--- Example 4: Realistic Trading Scenario ---");
        
        LimitOrderBook book = new LimitOrderBook("AAPL");
        long orderId = 1;
        
        // Market open: Initial liquidity providers
        System.out.println("Market opens with initial orders:");
        book.addOrder(new Order(orderId++, "AAPL", Order.SIDE_BUY, 14990L, 500L, System.nanoTime()));
        book.addOrder(new Order(orderId++, "AAPL", Order.SIDE_BUY, 14980L, 300L, System.nanoTime()));
        book.addOrder(new Order(orderId++, "AAPL", Order.SIDE_SELL, 15010L, 500L, System.nanoTime()));
        book.addOrder(new Order(orderId++, "AAPL", Order.SIDE_SELL, 15020L, 300L, System.nanoTime()));
        
        printMarketState(book, "Initial state");
        
        // Aggressive buyer enters
        System.out.println("\nAggressive buyer improves bid:");
        book.addOrder(new Order(orderId++, "AAPL", Order.SIDE_BUY, 15000L, 200L, System.nanoTime()));
        printMarketState(book, "After aggressive bid");
        
        // Some orders get cancelled
        System.out.println("\nLarge sell order cancelled:");
        book.removeOrder(3L);
        printMarketState(book, "After cancellation");
        
        // Market maker adjusts quotes
        System.out.println("\nMarket maker adjusts quote:");
        book.modifyOrder(1L, 14995L, 600L);
        printMarketState(book, "After quote adjustment");
        
        // Final statistics
        System.out.println("\nFinal Statistics:");
        System.out.println("  Total orders in book: " + book.getOrderCount());
        System.out.println("  Book is " + (book.isEmpty() ? "empty" : "active"));
    }
    
    private static void printMarketState(LimitOrderBook book, String label) {
        System.out.println("  " + label + ":");
        System.out.println("    Bid: $" + (book.getBestBid() / 100.0) + 
                          " x " + book.getBestBidQuantity());
        System.out.println("    Ask: $" + (book.getBestAsk() / 100.0) + 
                          " x " + book.getBestAskQuantity());
        System.out.println("    Spread: $" + (book.getSpread() / 100.0));
    }
}
