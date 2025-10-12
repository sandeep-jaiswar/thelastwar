package com.thelastwar.wsgateway.integration;

import com.thelastwar.eventbus.EventType;
import com.thelastwar.gateway.GatewayMetrics;
import com.thelastwar.wsgateway.TestEventBus;
import com.thelastwar.wsgateway.TransportFormat;
import com.thelastwar.wsgateway.WebSocketGateway;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.thelastwar.wsgateway.integration.WebSocketTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Load tests for WebSocket Gateway.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("load")
class WebSocketGatewayLoadTest {
    
    private static final Logger log = LoggerFactory.getLogger(WebSocketGatewayLoadTest.class);
    private static final int BASE_PORT = 8092;
    
    private TestEventBus eventBus;
    private WebSocketGateway gateway;
    private GatewayMetrics metrics;
    
    @BeforeEach
    void setUp() {
        eventBus = new TestEventBus();
        eventBus.start();
        metrics = new GatewayMetrics("ws-load-test-gateway");
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (gateway != null && gateway.isRunning()) {
            gateway.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
    }
    
    @Test
    @Order(1)
    void testHighThroughputEventBroadcast() throws Exception {
        gateway = new WebSocketGateway(eventBus, BASE_PORT, TransportFormat.JSON, metrics);
        gateway.start();
        
        int eventCount = 10_000;
        List<Long> latencies = new ArrayList<>();
        AtomicInteger publishedCount = new AtomicInteger(0);
        
        log.info("Starting high throughput test with {} events", eventCount);
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < eventCount; i++) {
            long sendStart = System.nanoTime();
            
            if (eventBus.publish(createOrderEvent(i + 1))) {
                publishedCount.incrementAndGet();
            }
            
            latencies.add(System.nanoTime() - sendStart);
            
            if (i % 100 == 0 && i > 0) {
                waitFor(10);
            }
        }
        
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        
        waitFor(500); // Allow processing to complete
        
        LatencyStats stats = calculateStats(latencies);
        double throughput = (eventCount * 1000.0) / durationMs;
        double avgLatencyMs = stats.avg / 1_000_000.0;
        
        log.info("Throughput: {} events/s, Avg Latency: {} ms", throughput, avgLatencyMs);
        
        generateReport("ws_gateway_load_test", eventCount, durationMs, publishedCount.get(), stats);
        
        assertTrue(throughput >= 5000, "Throughput should be at least 5K events/s");
        assertTrue(avgLatencyMs < 5.0, "Average latency should be < 5 ms");
        assertEquals(eventCount, publishedCount.get(), "All events should be published");
    }
    
    @Test
    @Order(2)
    void testMultipleEventTypesUnderLoad() throws Exception {
        gateway = new WebSocketGateway(eventBus, BASE_PORT + 1, TransportFormat.JSON, metrics);
        gateway.start();
        
        int eventsPerType = 1000;
        AtomicInteger publishedCount = new AtomicInteger(0);
        
        log.info("Testing multiple event types under load");
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < eventsPerType; i++) {
            if (eventBus.publish(createOrderEvent(i + 1))) {
                publishedCount.incrementAndGet();
            }
            
            if (eventBus.publish(createExecutionEvent(i + 1000))) {
                publishedCount.incrementAndGet();
            }
            
            if (i % 100 == 0) {
                waitFor(10);
            }
        }
        
        long durationMs = System.currentTimeMillis() - startTime;
        double throughput = (publishedCount.get() * 1000.0) / durationMs;
        
        log.info("Mixed events - Published: {}, Throughput: {} events/s", 
                publishedCount.get(), throughput);
        
        assertEquals(eventsPerType * 2, publishedCount.get(), "All events should be published");
    }
    
    @Test
    @Order(3)
    void testLatencyDistribution() throws Exception {
        gateway = new WebSocketGateway(eventBus, BASE_PORT + 2, TransportFormat.JSON, metrics);
        gateway.start();
        
        int eventCount = 1000;
        List<Long> latencies = new ArrayList<>();
        
        log.info("Testing latency distribution");
        
        // Warmup
        for (int i = 0; i < 100; i++) {
            eventBus.publish(createOrderEvent(i + 1));
        }
        waitFor(100);
        
        // Measure
        for (int i = 0; i < eventCount; i++) {
            long start = System.nanoTime();
            eventBus.publish(createOrderEvent(i + 1000));
            latencies.add(System.nanoTime() - start);
        }
        
        LatencyStats stats = calculateStats(latencies);
        double p99Ms = stats.p99 / 1_000_000.0;
        
        log.info("Latency P99: {} ms", p99Ms);
        
        assertTrue(p99Ms < 5.0, "P99 latency should be < 5 ms");
    }
    
    @Test
    @Order(4)
    void testNoMessageDropsUnderLoad() throws Exception {
        gateway = new WebSocketGateway(eventBus, BASE_PORT + 3, TransportFormat.JSON, metrics);
        gateway.start();
        
        int eventCount = 5000;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        log.info("Testing message drop rate with {} events", eventCount);
        
        for (int i = 0; i < eventCount; i++) {
            try {
                if (eventBus.publish(createOrderEvent(i + 1))) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
            
            if (i % 500 == 0) {
                waitFor(50);
            }
        }
        
        log.info("Success: {}, Failed: {}", successCount.get(), failCount.get());
        
        assertEquals(eventCount, successCount.get(), "All events should be published");
        assertEquals(0, failCount.get(), "No events should fail");
    }
    
    @Test
    @Order(5)
    void testMessagePackFormatUnderLoad() throws Exception {
        gateway = new WebSocketGateway(eventBus, BASE_PORT + 4, TransportFormat.MESSAGEPACK, metrics);
        gateway.start();
        
        int eventCount = 2000;
        AtomicInteger publishedCount = new AtomicInteger(0);
        
        log.info("Testing MessagePack format under load");
        
        for (int i = 0; i < eventCount; i++) {
            if (eventBus.publish(createOrderEvent(i + 1))) {
                publishedCount.incrementAndGet();
            }
            
            if (i % 200 == 0) {
                waitFor(20);
            }
        }
        
        log.info("Published: {} events (MessagePack)", publishedCount.get());
        
        assertEquals(eventCount, publishedCount.get(), "All events should be published");
    }
}
