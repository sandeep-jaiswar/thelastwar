package com.thelastwar.gateway.benchmark;

import com.thelastwar.gateway.MessageEnvelope;
import com.thelastwar.gateway.MessageEnvelopePool;
import com.thelastwar.gateway.MessageEnvelopeSerializer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for MessageEnvelope serialization/deserialization.
 * 
 * Performance target: &lt; 5 µs for small payloads (128 bytes)
 * 
 * Run with:
 * ./gradlew :core:gateway:jmh -Pargs="MessageEnvelopeBenchmark"
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class MessageEnvelopeBenchmark {
    
    private MessageEnvelope envelope;
    private MessageEnvelopePool pool;
    private UnsafeBuffer serializeBuffer;
    private UnsafeBuffer deserializeBuffer;
    private byte[] smallPayload;
    private byte[] mediumPayload;
    private byte[] largePayload;
    
    @Setup(Level.Trial)
    public void setup() {
        // Setup pool
        pool = new MessageEnvelopePool(1024, 8192);
        
        // Setup envelope
        envelope = new MessageEnvelope();
        envelope.setProtocolType(MessageEnvelope.ProtocolType.FIX);
        envelope.setCorrelationId(12345L);
        envelope.setTimestamp(System.nanoTime());
        envelope.setClientId("benchmark-client");
        
        // Prepare payloads of different sizes
        smallPayload = new byte[128];
        mediumPayload = new byte[1024];
        largePayload = new byte[4096];
        
        for (int i = 0; i < smallPayload.length; i++) {
            smallPayload[i] = (byte) (i % 256);
        }
        for (int i = 0; i < mediumPayload.length; i++) {
            mediumPayload[i] = (byte) (i % 256);
        }
        for (int i = 0; i < largePayload.length; i++) {
            largePayload[i] = (byte) (i % 256);
        }
        
        // Setup buffers
        serializeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(16384));
        deserializeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(16384));
        
        // Pre-serialize some data for deserialization benchmarks
        envelope.setPayload(smallPayload, 0, smallPayload.length);
        MessageEnvelopeSerializer.serialize(envelope, deserializeBuffer);
    }
    
    @Benchmark
    public int serializeSmallPayload(Blackhole bh) {
        envelope.setPayload(smallPayload, 0, smallPayload.length);
        int size = MessageEnvelopeSerializer.serialize(envelope, serializeBuffer);
        bh.consume(size);
        return size;
    }
    
    @Benchmark
    public int serializeMediumPayload(Blackhole bh) {
        envelope.setPayload(mediumPayload, 0, mediumPayload.length);
        int size = MessageEnvelopeSerializer.serialize(envelope, serializeBuffer);
        bh.consume(size);
        return size;
    }
    
    @Benchmark
    public int serializeLargePayload(Blackhole bh) {
        envelope.setPayload(largePayload, 0, largePayload.length);
        int size = MessageEnvelopeSerializer.serialize(envelope, serializeBuffer);
        bh.consume(size);
        return size;
    }
    
    @Benchmark
    public void deserializeSmallPayload(Blackhole bh) {
        MessageEnvelope target = new MessageEnvelope();
        MessageEnvelopeSerializer.deserialize(deserializeBuffer, 0, target);
        bh.consume(target);
    }
    
    @Benchmark
    public void poolAcquireRelease(Blackhole bh) {
        MessageEnvelope env = pool.acquire();
        bh.consume(env);
        pool.release(env);
    }
    
    @Benchmark
    public void envelopeCreation(Blackhole bh) {
        MessageEnvelope env = new MessageEnvelope();
        bh.consume(env);
    }
    
    @Benchmark
    public void fullCycleSmallPayload(Blackhole bh) {
        // Acquire from pool
        MessageEnvelope env = pool.acquire();
        
        // Set fields
        env.setProtocolType(MessageEnvelope.ProtocolType.FIX);
        env.setCorrelationId(12345L);
        env.setTimestamp(System.nanoTime());
        env.setClientId("client");
        env.setPayload(smallPayload, 0, smallPayload.length);
        
        // Serialize
        int size = MessageEnvelopeSerializer.serialize(env, serializeBuffer);
        
        // Deserialize
        MessageEnvelope target = pool.acquire();
        MessageEnvelopeSerializer.deserialize(serializeBuffer, 0, target);
        
        bh.consume(target);
        
        // Return to pool
        pool.release(env);
        pool.release(target);
    }
    
    @Benchmark
    public void setPayloadSmall(Blackhole bh) {
        envelope.setPayload(smallPayload, 0, smallPayload.length);
        bh.consume(envelope);
    }
    
    @Benchmark
    public void setPayloadMedium(Blackhole bh) {
        envelope.setPayload(mediumPayload, 0, mediumPayload.length);
        bh.consume(envelope);
    }
    
    @Benchmark
    public void setPayloadLarge(Blackhole bh) {
        envelope.setPayload(largePayload, 0, largePayload.length);
        bh.consume(envelope);
    }
}
