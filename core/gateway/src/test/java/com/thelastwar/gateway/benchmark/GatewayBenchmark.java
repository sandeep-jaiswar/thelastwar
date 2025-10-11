package com.thelastwar.gateway.benchmark;

import com.thelastwar.gateway.BufferPool;
import com.thelastwar.gateway.FixGateway;
import com.thelastwar.gateway.GatewayMetrics;
import com.thelastwar.gateway.TestEventBus;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import quickfix.SessionID;
import quickfix.SessionSettings;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for FIX Gateway components.
 * 
 * Run with:
 * ./gradlew :core:gateway:test --tests GatewayBenchmark
 * 
 * Or via JMH directly:
 * java -cp "target/test-classes:target/classes:..." org.openjdk.jmh.Main GatewayBenchmark
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class GatewayBenchmark {
    
    private BufferPool bufferPool;
    private GatewayMetrics metrics;
    private FixGateway gateway;
    private TestEventBus eventBus;
    private SessionSettings settings;
    
    @Setup(Level.Trial)
    public void setup() throws Exception {
        // Setup buffer pool
        bufferPool = new BufferPool(1000, 8192);
        
        // Setup metrics
        metrics = new GatewayMetrics("benchmark-gateway");
        
        // Setup FIX gateway
        eventBus = new TestEventBus();
        eventBus.start();
        
        settings = new SessionSettings();
        settings.setString("ConnectionType", "initiator");
        settings.setString("ReconnectInterval", "5");
        settings.setString("FileStorePath", "/tmp/fix-store");
        settings.setString("FileLogPath", "/tmp/fix-log");
        settings.setString("StartTime", "00:00:00");
        settings.setString("EndTime", "00:00:00");
        settings.setString("HeartBtInt", "30");
        settings.setString("SocketConnectHost", "localhost");
        settings.setString("SocketConnectPort", "9876");
        
        SessionID sessionID = new SessionID("FIX.4.4", "SENDER", "TARGET");
        settings.setString(sessionID, "BeginString", "FIX.4.4");
        settings.setString(sessionID, "SenderCompID", "SENDER");
        settings.setString(sessionID, "TargetCompID", "TARGET");
    }
    
    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        if (gateway != null && gateway.isRunning()) {
            gateway.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
        if (bufferPool != null) {
            bufferPool.close();
        }
    }
    
    /**
     * Benchmark buffer pool acquire operation.
     * Target: < 1000 ns (< 1 µs)
     */
    @Benchmark
    public void benchmarkBufferAcquire(Blackhole bh) throws InterruptedException {
        ByteBuffer buffer = bufferPool.acquire();
        bh.consume(buffer);
        bufferPool.release(buffer);
    }
    
    /**
     * Benchmark buffer pool tryAcquire operation.
     * Target: < 500 ns
     */
    @Benchmark
    public void benchmarkBufferTryAcquire(Blackhole bh) {
        ByteBuffer buffer = bufferPool.tryAcquire();
        bh.consume(buffer);
        if (buffer != null) {
            bufferPool.release(buffer);
        }
    }
    
    /**
     * Benchmark metrics recording for inbound messages.
     * Target: < 100 ns
     */
    @Benchmark
    public void benchmarkMetricsInbound() {
        metrics.recordInboundMessage();
    }
    
    /**
     * Benchmark metrics recording for outbound messages.
     * Target: < 100 ns
     */
    @Benchmark
    public void benchmarkMetricsOutbound() {
        metrics.recordOutboundMessage();
    }
    
    /**
     * Benchmark decode latency timer.
     * Target: < 500 ns
     */
    @Benchmark
    public void benchmarkDecodeTimer() {
        var sample = metrics.startDecodeTimer();
        metrics.recordDecodeLatency(sample);
    }
    
    /**
     * Benchmark encode latency timer.
     * Target: < 500 ns
     */
    @Benchmark
    public void benchmarkEncodeTimer() {
        var sample = metrics.startEncodeTimer();
        metrics.recordEncodeLatency(sample);
    }
    
    /**
     * Simulates a full message processing cycle:
     * 1. Acquire buffer
     * 2. Record metrics
     * 3. Release buffer
     * 
     * Target: < 10000 ns (< 10 µs) for full cycle
     */
    @Benchmark
    public void benchmarkFullMessageCycle(Blackhole bh) throws InterruptedException {
        // Acquire buffer
        ByteBuffer buffer = bufferPool.acquire();
        
        // Simulate decode
        var decodeSample = metrics.startDecodeTimer();
        buffer.putLong(System.nanoTime());
        metrics.recordDecodeLatency(decodeSample);
        
        // Record message
        metrics.recordInboundMessage();
        
        bh.consume(buffer);
        
        // Release buffer
        bufferPool.release(buffer);
    }
    
    /**
     * Benchmark buffer pool under contention (multiple threads).
     * Target: < 5000 ns (< 5 µs) p99
     */
    @Benchmark
    @Threads(4)
    public void benchmarkBufferPoolContention(Blackhole bh) throws InterruptedException {
        ByteBuffer buffer = bufferPool.acquire();
        bh.consume(buffer);
        bufferPool.release(buffer);
    }
    
    /**
     * Benchmark metrics under contention.
     * Target: < 1000 ns (< 1 µs) p99
     */
    @Benchmark
    @Threads(4)
    public void benchmarkMetricsContention() {
        metrics.recordInboundMessage();
        metrics.recordOutboundMessage();
    }
}
