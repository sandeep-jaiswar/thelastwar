package com.thelastwar.eventbus.model.benchmark;

import com.thelastwar.eventbus.model.*;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for Event Model serialization/deserialization.
 * 
 * Target: < 200 ns average for serialization operations
 * 
 * Run with: ./gradlew :core:eventbus:jmh -Pargs="EventModelBenchmark"
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class EventModelBenchmark {
    
    private OrderEvent orderEvent;
    private TradeEvent tradeEvent;
    private ExecutionEvent executionEvent;
    
    private ByteBuffer orderBuffer;
    private ByteBuffer tradeBuffer;
    private ByteBuffer executionBuffer;
    
    @Setup
    public void setup() {
        // Create sample events
        orderEvent = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        tradeEvent = TradeEvent.create(
                67890L, 12345L, "AAPL", TradeEvent.SIDE_BUY,
                100L, 15050L, 999L, 1, 888L, 50L
        );
        
        executionEvent = ExecutionEvent.fill(
                11111L, orderEvent, 100L, 15050L, 100L, 0L
        );
        
        // Pre-allocate buffers
        orderBuffer = ByteBuffer.allocate(EventSerializer.getOrderEventSize());
        tradeBuffer = ByteBuffer.allocate(EventSerializer.getTradeEventSize());
        executionBuffer = ByteBuffer.allocate(EventSerializer.getExecutionEventSize());
    }
    
    @Benchmark
    public void serializeOrderEvent() {
        orderBuffer.clear();
        EventSerializer.serializeOrder(orderEvent, orderBuffer);
    }
    
    @Benchmark
    public OrderEvent deserializeOrderEvent() {
        orderBuffer.clear();
        EventSerializer.serializeOrder(orderEvent, orderBuffer);
        orderBuffer.flip();
        return EventSerializer.deserializeOrder(orderBuffer);
    }
    
    @Benchmark
    public void serializeTradeEvent() {
        tradeBuffer.clear();
        EventSerializer.serializeTrade(tradeEvent, tradeBuffer);
    }
    
    @Benchmark
    public TradeEvent deserializeTradeEvent() {
        tradeBuffer.clear();
        EventSerializer.serializeTrade(tradeEvent, tradeBuffer);
        tradeBuffer.flip();
        return EventSerializer.deserializeTrade(tradeBuffer);
    }
    
    @Benchmark
    public void serializeExecutionEvent() {
        executionBuffer.clear();
        EventSerializer.serializeExecution(executionEvent, executionBuffer);
    }
    
    @Benchmark
    public ExecutionEvent deserializeExecutionEvent() {
        executionBuffer.clear();
        EventSerializer.serializeExecution(executionEvent, executionBuffer);
        executionBuffer.flip();
        return EventSerializer.deserializeExecution(executionBuffer);
    }
    
    @Benchmark
    public OrderEvent roundTripOrderEvent() {
        orderBuffer.clear();
        EventSerializer.serializeOrder(orderEvent, orderBuffer);
        orderBuffer.flip();
        return EventSerializer.deserializeOrder(orderBuffer);
    }
    
    @Benchmark
    public TradeEvent roundTripTradeEvent() {
        tradeBuffer.clear();
        EventSerializer.serializeTrade(tradeEvent, tradeBuffer);
        tradeBuffer.flip();
        return EventSerializer.deserializeTrade(tradeBuffer);
    }
    
    @Benchmark
    public ExecutionEvent roundTripExecutionEvent() {
        executionBuffer.clear();
        EventSerializer.serializeExecution(executionEvent, executionBuffer);
        executionBuffer.flip();
        return EventSerializer.deserializeExecution(executionBuffer);
    }
    
    @Benchmark
    public OrderEvent createOrderEvent() {
        return OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
    }
    
    @Benchmark
    public TradeEvent createTradeEvent() {
        return TradeEvent.create(
                67890L, 12345L, "AAPL", TradeEvent.SIDE_BUY,
                100L, 15050L, 999L, 1, 888L, 50L
        );
    }
    
    @Benchmark
    public ExecutionEvent createExecutionEvent() {
        return ExecutionEvent.fill(
                11111L, orderEvent, 100L, 15050L, 100L, 0L
        );
    }
}
