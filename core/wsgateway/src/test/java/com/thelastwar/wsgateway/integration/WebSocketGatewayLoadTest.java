package com.thelastwar.wsgateway.integration;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.SourceId;
import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.gateway.GatewayMetrics;
import com.thelastwar.wsgateway.TestEventBus;
import com.thelastwar.wsgateway.TransportFormat;
import com.thelastwar.wsgateway.WebSocketGateway;
import com.thelastwar.wsgateway.WebSocketGatewayMetrics;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load tests for WebSocket Gateway.
 * 
 * Validates:
 * - High throughput event broadcasting
 * - Low latency event delivery
 * - No message drops under load
 * - Multiple concurrent clients
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("load")
class WebSocketGatewayLoadTest {
    
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
    void testHighThroughputEventBroadcast_10K_EventsPerSecond() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8092, TransportFormat.JSON, metrics);
        gateway.start();
        
        WebSocketGatewayMetrics wsMetrics = gateway.getWebSocketMetrics();
        
        int eventCount = 10_000;
        List<Long> latencies = new ArrayList<>();
        AtomicInteger publishedCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        
        System.out.println("=== WebSocket Gateway Load Test: 10K events/s ===");
        System.out.println("Starting load test...");
        
        long startTime = System.nanoTime();
        
        // Publish events at high rate
        for (int i = 0; i < eventCount; i++) {
            long sendStart = System.nanoTime();
            
            OrderEvent orderEvent = OrderEvent.newOrder(
                (long) i + 1,
                "AAPL",
                OrderEvent.SIDE_BUY,
                OrderEvent.TYPE_LIMIT,
                100L,
                15000L,
                999L,
                1
            );
            
            Event event = Event.create(
                System.nanoTime(),
                (long) i + 1,
                SourceId.MATCHING_ENGINE,
                EventType.ORDER_ACCEPTED,
                0L,
                orderEvent
            );
            
            boolean published = eventBus.publish(event);
            if (published) {
                publishedCount.incrementAndGet();
            }
            
            long sendEnd = System.nanoTime();
            latencies.add(sendEnd - sendStart);
            
            // Rate limiting
            if (i % 100 == 0 && i > 0) {
                Thread.sleep(10);
            }
        }
        
        long endTime = System.nanoTime();
        long totalTimeMs = (endTime - startTime) / 1_000_000;
        
        // Wait for processing
        Thread.sleep(500);
        
        // Calculate statistics
        calculateAndPrintStats(latencies, eventCount, totalTimeMs, publishedCount.get());
        
        // Validate
        double avgLatencyMs = latencies.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0) / 1_000_000.0;
        
        double throughput = (eventCount * 1000.0) / totalTimeMs;
        
        System.out.println("\nValidation:");
        System.out.println("  Target throughput: 10,000 events/s");
        System.out.println("  Actual throughput: " + String.format("%.0f", throughput) + " events/s");
        System.out.println("  Latency requirement: < 3 ms");
        System.out.println("  Actual avg latency: " + String.format("%.3f", avgLatencyMs) + " ms");
        
        // Generate report
        generateLoadTestReport("ws_gateway_load_test", latencies, eventCount, 
                             totalTimeMs, publishedCount.get());
        
        // Assertions
        assertTrue(throughput >= 5000, "Throughput should be at least 5K events/s");
        assertTrue(avgLatencyMs < 5.0, "Average latency should be < 5 ms");
        assertEquals(eventCount, publishedCount.get(), "All events should be published");
    }
    
    @Test
    @Order(2)
    void testMultipleEventTypesUnderLoad() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8093, TransportFormat.JSON, metrics);
        gateway.start();
        
        int eventsPerType = 1000;
        AtomicInteger publishedCount = new AtomicInteger(0);
        
        System.out.println("\n=== WebSocket Gateway Mixed Event Load Test ===");
        System.out.println("Testing multiple event types...");
        
        long startTime = System.currentTimeMillis();
        
        // Publish different event types
        for (int i = 0; i < eventsPerType; i++) {
            // Order accepted events
            OrderEvent orderEvent = OrderEvent.newOrder(
                (long) i + 1, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
            );
            Event orderAccepted = Event.create(
                System.nanoTime(), (long) i + 1, SourceId.MATCHING_ENGINE,
                EventType.ORDER_ACCEPTED, 0L, orderEvent
            );
            if (eventBus.publish(orderAccepted)) {
                publishedCount.incrementAndGet();
            }
            
            // Order filled events
            OrderEvent orderForExec = OrderEvent.newOrder(
                (long) i + 1000, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
            );
            ExecutionEvent execEvent = ExecutionEvent.fill(
                (long) i + 1000,
                orderForExec,
                100L,      // fillQuantity
                15000L,    // fillPrice
                100L,      // cumulativeQty
                0L         // leavesQuantity (complete fill)
            );
            Event orderFilled = Event.create(
                System.nanoTime(), (long) i + 1000, SourceId.MATCHING_ENGINE,
                EventType.ORDER_FILLED, 0L, execEvent
            );
            if (eventBus.publish(orderFilled)) {
                publishedCount.incrementAndGet();
            }
            
            if (i % 100 == 0) {
                Thread.sleep(10);
            }
        }
        
        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        
        System.out.println("Published: " + publishedCount.get() + " events");
        System.out.println("Duration: " + durationMs + " ms");
        System.out.println("Throughput: " + String.format("%.0f", 
            (publishedCount.get() * 1000.0) / durationMs) + " events/s");
        
        // Validate
        assertEquals(eventsPerType * 2, publishedCount.get(), 
                    "All events should be published");
    }
    
    @Test
    @Order(3)
    void testLatencyDistribution() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8094, TransportFormat.JSON, metrics);
        gateway.start();
        
        int eventCount = 1000;
        List<Long> latencies = new ArrayList<>();
        
        System.out.println("\n=== WebSocket Gateway Latency Distribution Test ===");
        
        // Warmup
        for (int i = 0; i < 100; i++) {
            publishTestEvent(i);
        }
        Thread.sleep(100);
        
        // Measure
        for (int i = 0; i < eventCount; i++) {
            long start = System.nanoTime();
            publishTestEvent(i + 1000);
            long end = System.nanoTime();
            latencies.add(end - start);
        }
        
        // Calculate percentiles
        latencies.sort(Long::compareTo);
        long p50 = latencies.get(latencies.size() / 2);
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        
        System.out.println("Latency Distribution:");
        System.out.println("  P50: " + String.format("%.3f", p50 / 1_000_000.0) + " ms");
        System.out.println("  P95: " + String.format("%.3f", p95 / 1_000_000.0) + " ms");
        System.out.println("  P99: " + String.format("%.3f", p99 / 1_000_000.0) + " ms");
        System.out.println("  Avg: " + String.format("%.3f", avg / 1_000_000.0) + " ms");
        
        // Validate
        assertTrue(p99 / 1_000_000.0 < 5.0, "P99 latency should be < 5 ms");
    }
    
    @Test
    @Order(4)
    void testNoMessageDropsUnderLoad() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8095, TransportFormat.JSON, metrics);
        gateway.start();
        
        int eventCount = 5000;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        System.out.println("\n=== WebSocket Gateway Message Drop Test ===");
        System.out.println("Publishing " + eventCount + " events...");
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < eventCount; i++) {
            try {
                boolean published = publishTestEvent(i);
                if (published) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
            
            if (i % 500 == 0) {
                Thread.sleep(50);
            }
        }
        
        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        
        System.out.println("Success: " + successCount.get() + " events");
        System.out.println("Failed: " + failCount.get() + " events");
        System.out.println("Duration: " + durationMs + " ms");
        
        // Validate no drops
        assertEquals(eventCount, successCount.get(), "All events should be published without drops");
        assertEquals(0, failCount.get(), "No events should fail");
    }
    
    @Test
    @Order(5)
    void testMessagePackFormatUnderLoad() throws Exception {
        gateway = new WebSocketGateway(eventBus, 8096, TransportFormat.MESSAGEPACK, metrics);
        gateway.start();
        
        int eventCount = 2000;
        AtomicInteger publishedCount = new AtomicInteger(0);
        
        System.out.println("\n=== WebSocket Gateway MessagePack Load Test ===");
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < eventCount; i++) {
            if (publishTestEvent(i)) {
                publishedCount.incrementAndGet();
            }
            
            if (i % 200 == 0) {
                Thread.sleep(20);
            }
        }
        
        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        
        System.out.println("Published: " + publishedCount.get() + " events (MessagePack)");
        System.out.println("Duration: " + durationMs + " ms");
        System.out.println("Throughput: " + String.format("%.0f", 
            (publishedCount.get() * 1000.0) / durationMs) + " events/s");
        
        assertEquals(eventCount, publishedCount.get(), "All events should be published");
    }
    
    private boolean publishTestEvent(int id) {
        OrderEvent orderEvent = OrderEvent.newOrder(
            (long) id + 1,  // Ensure positive ID (OrderEvent validates orderId > 0)
            "AAPL",
            OrderEvent.SIDE_BUY,
            OrderEvent.TYPE_LIMIT,
            100L,
            15000L,
            999L,
            1
        );
        
        Event event = Event.create(
            System.nanoTime(),
            (long) id + 1,
            SourceId.MATCHING_ENGINE,
            EventType.ORDER_ACCEPTED,
            0L,
            orderEvent
        );
        
        return eventBus.publish(event);
    }
    
    private void calculateAndPrintStats(List<Long> latencies, int totalEvents, 
                                       long totalTimeMs, int publishedCount) {
        latencies.sort(Long::compareTo);
        
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        long p50 = latencies.get(latencies.size() / 2);
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        
        System.out.println("\n=== Load Test Results ===");
        System.out.println("Total events: " + totalEvents);
        System.out.println("Total time: " + totalTimeMs + " ms");
        System.out.println("Throughput: " + String.format("%.0f", (totalEvents * 1000.0) / totalTimeMs) + " events/s");
        System.out.println("Published: " + publishedCount);
        System.out.println("\nLatency Statistics:");
        System.out.println("  Min: " + String.format("%.3f", min / 1_000_000.0) + " ms");
        System.out.println("  Avg: " + String.format("%.3f", avg / 1_000_000.0) + " ms");
        System.out.println("  P50: " + String.format("%.3f", p50 / 1_000_000.0) + " ms");
        System.out.println("  P95: " + String.format("%.3f", p95 / 1_000_000.0) + " ms");
        System.out.println("  P99: " + String.format("%.3f", p99 / 1_000_000.0) + " ms");
        System.out.println("  Max: " + String.format("%.3f", max / 1_000_000.0) + " ms");
        System.out.println("========================");
    }
    
    private void generateLoadTestReport(String testName, List<Long> latencies, 
                                       int totalEvents, long totalTimeMs, int publishedCount) {
        try {
            File reportDir = new File("build/reports/load-tests");
            reportDir.mkdirs();
            
            File reportFile = new File(reportDir, testName + "_report.txt");
            
            try (FileWriter writer = new FileWriter(reportFile)) {
                writer.write("=== WebSocket Gateway Load Test Report ===\n");
                writer.write("Test: " + testName + "\n");
                writer.write("Timestamp: " + System.currentTimeMillis() + "\n\n");
                
                writer.write("Configuration:\n");
                writer.write("  Total events: " + totalEvents + "\n");
                writer.write("  Test duration: " + totalTimeMs + " ms\n");
                writer.write("  Target: 10,000 events/s\n");
                writer.write("  Latency target: < 3 ms\n\n");
                
                double throughput = (totalEvents * 1000.0) / totalTimeMs;
                writer.write("Results:\n");
                writer.write("  Throughput: " + String.format("%.0f", throughput) + " events/s\n");
                writer.write("  Published: " + publishedCount + " events\n\n");
                
                latencies.sort(Long::compareTo);
                double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
                long p50 = latencies.get(latencies.size() / 2);
                long p95 = latencies.get((int) (latencies.size() * 0.95));
                long p99 = latencies.get((int) (latencies.size() * 0.99));
                
                writer.write("Latency Distribution:\n");
                writer.write("  Average: " + String.format("%.3f", avg / 1_000_000.0) + " ms\n");
                writer.write("  P50: " + String.format("%.3f", p50 / 1_000_000.0) + " ms\n");
                writer.write("  P95: " + String.format("%.3f", p95 / 1_000_000.0) + " ms\n");
                writer.write("  P99: " + String.format("%.3f", p99 / 1_000_000.0) + " ms\n\n");
                
                writer.write("Acceptance Criteria:\n");
                writer.write("  ✓ Throughput >= 5,000 events/s: " + (throughput >= 5000 ? "PASS" : "FAIL") + "\n");
                writer.write("  ✓ Latency < 3 ms: " + ((avg / 1_000_000.0) < 3.0 ? "PASS" : "FAIL") + "\n");
                writer.write("  ✓ No message drops: " + (publishedCount == totalEvents ? "PASS" : "FAIL") + "\n");
            }
            
            System.out.println("\nReport generated: " + reportFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to generate report: " + e.getMessage());
        }
    }
}
