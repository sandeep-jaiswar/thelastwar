package com.thelastwar.orderbook.benchmark;

import com.thelastwar.orderbook.*;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for OffHeapOrderBook with O(1) operations.
 * 
 * Performance targets:
 * - Add/update/remove: O(1)
 * - Best bid/ask: < 200 ns
 * - Heap usage: < 10 MB for 1M orders
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class OffHeapOrderBookBenchmark {
    
    private OffHeapOrderBook book;
    private EquityOrderBook equityBook;
    private BondOrderBook bondBook;
    private DerivativeOrderBook derivativeBook;
    
    private long orderId;
    private long timestamp;
    
    @Setup(Level.Trial)
    public void setupTrial() {
        book = new OffHeapOrderBook("AAPL");
        equityBook = new EquityOrderBook("GOOGL");
        bondBook = new BondOrderBook("US10Y");
        derivativeBook = new DerivativeOrderBook("ES_MAR25");
        
        orderId = 1;
        timestamp = System.nanoTime();
        
        // Pre-populate with some orders to simulate realistic state
        for (int i = 0; i < 1000; i++) {
            long id = orderId++;
            long price = 15000L + (i % 100);
            byte side = i % 2 == 0 ? Order.SIDE_BUY : Order.SIDE_SELL;
            
            book.addOrder(new Order(id, "AAPL", side, price, 100L, timestamp++, Order.TYPE_LIMIT, Order.TIF_GTC));
        }
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() {
        // Reset counters for each iteration
        orderId = 10000;
        timestamp = System.nanoTime();
    }
    
    /**
     * Benchmark add order operation (target: O(1)).
     */
    @Benchmark
    public boolean benchmarkAddOrder() {
        long id = orderId++;
        Order order = new Order(id, "AAPL", Order.SIDE_BUY, 15000L + (id % 100), 100L, timestamp++, Order.TYPE_LIMIT, Order.TIF_GTC);
        return book.addOrder(order);
    }
    
    /**
     * Benchmark remove order operation (target: O(1)).
     */
    @Benchmark
    public boolean benchmarkRemoveOrder() {
        // Add an order first
        long id = orderId++;
        Order order = new Order(id, "AAPL", Order.SIDE_BUY, 15000L, 100L, timestamp++, Order.TYPE_LIMIT, Order.TIF_GTC);
        book.addOrder(order);
        
        // Now remove it
        return book.removeOrder(id);
    }
    
    /**
     * Benchmark update order operation (target: O(1)).
     */
    @Benchmark
    public boolean benchmarkUpdateOrder() {
        // Use an existing order ID (from setup)
        long id = 500L; // Should exist from setup
        return book.updateOrder(id, 200L);
    }
    
    /**
     * Benchmark get order operation (target: O(1)).
     */
    @Benchmark
    public Order benchmarkGetOrder() {
        // Use an existing order ID (from setup)
        return book.getOrder(500L);
    }
    
    /**
     * Benchmark best bid lookup (target: < 200 ns).
     */
    @Benchmark
    public long benchmarkGetBestBid() {
        return book.getBestBid();
    }
    
    /**
     * Benchmark best ask lookup (target: < 200 ns).
     */
    @Benchmark
    public long benchmarkGetBestAsk() {
        return book.getBestAsk();
    }
    
    /**
     * Benchmark spread calculation (target: < 200 ns).
     */
    @Benchmark
    public long benchmarkGetSpread() {
        return book.getSpread();
    }
    
    /**
     * Benchmark best bid quantity (target: O(1) for single order, O(n) for level).
     */
    @Benchmark
    public long benchmarkGetBestBidQuantity() {
        return book.getBestBidQuantity();
    }
    
    /**
     * Benchmark depth snapshot generation (target: O(n_levels)).
     */
    @Benchmark
    public OffHeapOrderBook.PriceLevelSnapshot[] benchmarkGetDepthSnapshot() {
        return book.getDepthSnapshot(10);
    }
    
    /**
     * Benchmark equity order book operations.
     */
    @Benchmark
    public boolean benchmarkEquityOrderBook() {
        long id = orderId++;
        Order order = new Order(id, "GOOGL", Order.SIDE_BUY, 140000L, 100L, timestamp++, Order.TYPE_LIMIT, Order.TIF_GTC);
        return equityBook.addOrder(order);
    }
    
    /**
     * Benchmark bond order book operations.
     */
    @Benchmark
    public boolean benchmarkBondOrderBook() {
        long id = orderId++;
        Order order = new Order(id, "US10Y", Order.SIDE_BUY, 9500L, 1000000L, timestamp++, Order.TYPE_LIMIT, Order.TIF_GTC);
        return bondBook.addOrder(order);
    }
    
    /**
     * Benchmark derivative order book operations.
     */
    @Benchmark
    public boolean benchmarkDerivativeOrderBook() {
        long id = orderId++;
        Order order = new Order(id, "ES_MAR25", Order.SIDE_BUY, 450000L, 10L, timestamp++, Order.TYPE_LIMIT, Order.TIF_GTC);
        return derivativeBook.addOrder(order);
    }
    
    /**
     * Benchmark mixed operations (realistic scenario).
     */
    @Benchmark
    public Object benchmarkMixedOperations() {
        long id = orderId++;
        
        // Add order
        Order order = new Order(id, "AAPL", Order.SIDE_BUY, 15000L + (id % 50), 100L, timestamp++, Order.TYPE_LIMIT, Order.TIF_GTC);
        book.addOrder(order);
        
        // Get best bid/ask
        long bestBid = book.getBestBid();
        long bestAsk = book.getBestAsk();
        
        // Update order
        book.updateOrder(id, 150L);
        
        // Get order
        Order retrieved = book.getOrder(id);
        
        // Remove order
        book.removeOrder(id);
        
        return retrieved;
    }
}
