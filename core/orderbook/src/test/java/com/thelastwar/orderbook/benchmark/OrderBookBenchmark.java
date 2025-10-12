package com.thelastwar.orderbook.benchmark;

import com.thelastwar.orderbook.LimitOrderBook;
import com.thelastwar.orderbook.Order;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for LimitOrderBook operations.
 * 
 * Target: < 1 µs p99 for add/remove/modify operations
 * 
 * Run with: ./gradlew :core:orderbook:jmh -Pargs="OrderBookBenchmark"
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class OrderBookBenchmark {
    
    private LimitOrderBook book;
    private long orderIdCounter;
    
    @Setup(Level.Trial)
    public void setupTrial() {
        book = new LimitOrderBook("AAPL");
        orderIdCounter = 1;
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() {
        // Pre-populate book with realistic market depth
        book.clear();
        orderIdCounter = 1;
        
        // Add 100 buy orders
        for (int i = 0; i < 100; i++) {
            book.addOrder(new Order(
                    orderIdCounter++,
                    "AAPL",
                    Order.SIDE_BUY,
                    15000L - i,
                    100L,
                    System.nanoTime(),
                    Order.TYPE_LIMIT,
                    Order.TIF_GTC
            ));
        }
        
        // Add 100 sell orders
        for (int i = 0; i < 100; i++) {
            book.addOrder(new Order(
                    orderIdCounter++,
                    "AAPL",
                    Order.SIDE_SELL,
                    15100L + i,
                    100L,
                    System.nanoTime(),
                    Order.TYPE_LIMIT,
                    Order.TIF_GTC
            ));
        }
    }
    
    @Benchmark
    public void addBuyOrder(Blackhole bh) {
        long orderId = orderIdCounter++;
        Order order = new Order(
                orderId,
                "AAPL",
                Order.SIDE_BUY,
                14900L - (orderId % 100),
                100L,
                System.nanoTime(),
                Order.TYPE_LIMIT,
                Order.TIF_GTC
        );
        bh.consume(book.addOrder(order));
    }
    
    @Benchmark
    public void addSellOrder(Blackhole bh) {
        long orderId = orderIdCounter++;
        Order order = new Order(
                orderId,
                "AAPL",
                Order.SIDE_SELL,
                15200L + (orderId % 100),
                100L,
                System.nanoTime(),
                Order.TYPE_LIMIT,
                Order.TIF_GTC
        );
        bh.consume(book.addOrder(order));
    }
    
    @Benchmark
    public void removeOrder(Blackhole bh) {
        // Remove from the middle of the book (realistic scenario)
        long removeId = 50 + (orderIdCounter % 150);
        bh.consume(book.removeOrder(removeId));
        
        // Re-add to maintain book state
        if (removeId <= 100) {
            book.addOrder(new Order(
                    removeId,
                    "AAPL",
                    Order.SIDE_BUY,
                    15000L - removeId,
                    100L,
                    System.nanoTime(),
                    Order.TYPE_LIMIT,
                    Order.TIF_GTC
            ));
        } else {
            book.addOrder(new Order(
                    removeId,
                    "AAPL",
                    Order.SIDE_SELL,
                    15100L + (removeId - 100),
                    100L,
                    System.nanoTime(),
                    Order.TYPE_LIMIT,
                    Order.TIF_GTC
            ));
        }
        orderIdCounter++;
    }
    
    @Benchmark
    public void modifyOrderQuantity(Blackhole bh) {
        long modifyId = 50 + (orderIdCounter % 150);
        long newQuantity = 150L + (orderIdCounter % 100);
        bh.consume(book.modifyOrder(modifyId, 0, newQuantity));
        orderIdCounter++;
    }
    
    @Benchmark
    public void modifyOrderPrice(Blackhole bh) {
        long modifyId = 50 + (orderIdCounter % 100);
        long newPrice = 14900L - (orderIdCounter % 50);
        bh.consume(book.modifyOrder(modifyId, newPrice, 100L));
        orderIdCounter++;
    }
    
    @Benchmark
    public void getBestBidAsk(Blackhole bh) {
        bh.consume(book.getBestBid());
        bh.consume(book.getBestAsk());
    }
    
    @Benchmark
    public void getOrderById(Blackhole bh) {
        long lookupId = 50 + (orderIdCounter % 150);
        bh.consume(book.getOrder(lookupId));
        orderIdCounter++;
    }
    
    @Benchmark
    public void getMarketDepth(Blackhole bh) {
        bh.consume(book.getBidLevels(10));
        bh.consume(book.getAskLevels(10));
    }
    
    /**
     * Realistic mixed workload benchmark.
     * Simulates typical order flow pattern.
     */
    @Benchmark
    public void mixedWorkload(Blackhole bh) {
        long op = orderIdCounter % 100;
        
        if (op < 40) {
            // 40% add order
            long orderId = orderIdCounter++;
            byte side = (orderId % 2 == 0) ? Order.SIDE_BUY : Order.SIDE_SELL;
            long price = side == Order.SIDE_BUY 
                ? 14900L - (orderId % 100)
                : 15200L + (orderId % 100);
            
            Order order = new Order(orderId, "AAPL", side, price, 100L, System.nanoTime(), Order.TYPE_LIMIT, Order.TIF_GTC);
            bh.consume(book.addOrder(order));
            
        } else if (op < 60) {
            // 20% remove order
            long removeId = 50 + (orderIdCounter % 150);
            bh.consume(book.removeOrder(removeId));
            
        } else if (op < 80) {
            // 20% modify order
            long modifyId = 50 + (orderIdCounter % 150);
            bh.consume(book.modifyOrder(modifyId, 0, 150L));
            
        } else {
            // 20% query
            bh.consume(book.getBestBid());
            bh.consume(book.getBestAsk());
        }
        
        orderIdCounter++;
    }
}
