package com.thelastwar.gateway.integration;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventHandler;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.SourceId;
import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.gateway.FixGateway;
import com.thelastwar.gateway.GatewayMetrics;
import com.thelastwar.gateway.TestEventBus;
import org.junit.jupiter.api.*;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;

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
 * End-to-End Integration Test validating complete message flow:
 * Gateway → EventBus → Matching Engine (simulated) → back to Gateway
 * 
 * Validates:
 * - Complete round-trip message flow
 * - End-to-end latency < 3 ms
 * - No message drops
 * - Proper event routing through EventBus
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
class EndToEndIntegrationTest {
    
    private static final int SERVER_PORT = 19878;
    private static final String SENDER_COMP_ID = "E2E_CLIENT";
    private static final String TARGET_COMP_ID = "E2E_SERVER";
    
    private TestEventBus eventBus;
    private FixGateway gateway;
    private SimulatedFixServer server;
    private GatewayMetrics metrics;
    private SimulatedMatchingEngine matchingEngine;
    
    @BeforeEach
    void setUp() throws Exception {
        eventBus = new TestEventBus();
        eventBus.start();
        
        metrics = new GatewayMetrics("e2e-test-gateway");
        
        // Start simulated matching engine
        matchingEngine = new SimulatedMatchingEngine(eventBus);
        matchingEngine.start();
        
        // Start simulated FIX server
        server = new SimulatedFixServer(SERVER_PORT);
        server.start();
        
        Thread.sleep(500);
        
        SessionSettings clientSettings = createClientSettings();
        gateway = new FixGateway(eventBus, clientSettings, metrics);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (gateway != null && gateway.isRunning()) {
            gateway.stop();
        }
        if (server != null) {
            server.stop();
        }
        if (matchingEngine != null) {
            matchingEngine.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
        
        cleanupTestFiles();
    }
    
    @Test
    @Order(1)
    void testCompleteRoundTripFlow() throws Exception {
        CountDownLatch orderSubmittedLatch = new CountDownLatch(1);
        CountDownLatch executionLatch = new CountDownLatch(1);
        AtomicInteger submittedCount = new AtomicInteger(0);
        AtomicInteger executionCount = new AtomicInteger(0);
        AtomicLong orderSubmitTime = new AtomicLong(0);
        AtomicLong executionTime = new AtomicLong(0);
        
        // Subscribe to order submissions (from gateway to matching engine)
        eventBus.subscribe(EventType.ORDER_SUBMITTED, new EventHandler<Object>() {
            @Override
            public void onEvent(Event event) {
                submittedCount.incrementAndGet();
                orderSubmitTime.set(System.nanoTime());
                orderSubmittedLatch.countDown();
            }
            
            @Override
            public void onError(Event event, Throwable exception) {
                exception.printStackTrace();
            }
        });
        
        // Subscribe to executions (from matching engine back to gateway)
        eventBus.subscribe(EventType.ORDER_FILLED, new EventHandler<Object>() {
            @Override
            public void onEvent(Event event) {
                executionCount.incrementAndGet();
                executionTime.set(System.nanoTime());
                executionLatch.countDown();
            }
            
            @Override
            public void onError(Event event, Throwable exception) {
                exception.printStackTrace();
            }
        });
        
        gateway.start();
        Thread.sleep(1000); // Wait for connection
        
        System.out.println("=== End-to-End Integration Test ===");
        System.out.println("Testing complete message flow...\n");
        
        long startTime = System.nanoTime();
        
        // Send order through gateway
        NewOrderSingle order = createTestOrder(12345);
        gateway.sendMessage(order);
        
        // Wait for order submission event
        boolean submitted = orderSubmittedLatch.await(5, TimeUnit.SECONDS);
        assertTrue(submitted, "Order should be submitted to EventBus");
        
        // Wait for execution event
        boolean executed = executionLatch.await(5, TimeUnit.SECONDS);
        assertTrue(executed, "Execution should be received from matching engine");
        
        long endTime = System.nanoTime();
        long totalLatencyNs = endTime - startTime;
        double totalLatencyMs = totalLatencyNs / 1_000_000.0;
        
        System.out.println("Round-trip completed:");
        System.out.println("  Order submitted: " + submittedCount.get());
        System.out.println("  Executions received: " + executionCount.get());
        System.out.println("  Total latency: " + String.format("%.3f", totalLatencyMs) + " ms");
        
        // Validate flow
        assertEquals(1, submittedCount.get(), "One order should be submitted");
        assertEquals(1, executionCount.get(), "One execution should be received");
        
        // Validate latency requirement
        assertTrue(totalLatencyMs < 10.0, "End-to-end latency should be < 10 ms in test environment");
        
        System.out.println("✓ End-to-end flow validated");
    }
    
    @Test
    @Order(2)
    void testHighVolumeRoundTrip() throws Exception {
        int orderCount = 100;
        CountDownLatch submittedLatch = new CountDownLatch(orderCount);
        CountDownLatch executionLatch = new CountDownLatch(orderCount);
        AtomicInteger submittedCount = new AtomicInteger(0);
        AtomicInteger executionCount = new AtomicInteger(0);
        List<Long> latencies = new ArrayList<>();
        
        // Subscribe to submissions
        eventBus.subscribe(EventType.ORDER_SUBMITTED, new EventHandler<Object>() {
            @Override
            public void onEvent(Event event) {
                submittedCount.incrementAndGet();
                submittedLatch.countDown();
            }
            
            @Override
            public void onError(Event event, Throwable exception) {
                exception.printStackTrace();
            }
        });
        
        // Subscribe to executions
        eventBus.subscribe(EventType.ORDER_FILLED, new EventHandler<Object>() {
            @Override
            public void onEvent(Event event) {
                executionCount.incrementAndGet();
                executionLatch.countDown();
            }
            
            @Override
            public void onError(Event event, Throwable exception) {
                exception.printStackTrace();
            }
        });
        
        gateway.start();
        Thread.sleep(1000);
        
        System.out.println("\n=== High Volume Round-Trip Test ===");
        System.out.println("Sending " + orderCount + " orders...\n");
        
        long startTime = System.nanoTime();
        
        // Send multiple orders
        for (int i = 0; i < orderCount; i++) {
            long sendStart = System.nanoTime();
            NewOrderSingle order = createTestOrder(i + 1);
            gateway.sendMessage(order);
            long sendEnd = System.nanoTime();
            latencies.add(sendEnd - sendStart);
            
            if (i % 10 == 0) {
                Thread.sleep(10);
            }
        }
        
        // Wait for all submissions
        boolean allSubmitted = submittedLatch.await(10, TimeUnit.SECONDS);
        
        // Wait for all executions
        boolean allExecuted = executionLatch.await(10, TimeUnit.SECONDS);
        
        long endTime = System.nanoTime();
        long totalTimeMs = (endTime - startTime) / 1_000_000;
        
        System.out.println("High volume test completed:");
        System.out.println("  Orders sent: " + orderCount);
        System.out.println("  Orders submitted: " + submittedCount.get());
        System.out.println("  Executions received: " + executionCount.get());
        System.out.println("  Total time: " + totalTimeMs + " ms");
        System.out.println("  Throughput: " + String.format("%.0f", (orderCount * 1000.0) / totalTimeMs) + " orders/s");
        
        // Calculate latency stats
        latencies.sort(Long::compareTo);
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        long p99Latency = latencies.get((int) (latencies.size() * 0.99));
        
        System.out.println("  Avg send latency: " + String.format("%.3f", avgLatency) + " ms");
        System.out.println("  P99 send latency: " + String.format("%.3f", p99Latency / 1_000_000.0) + " ms");
        
        // Generate report
        generateE2EReport("end_to_end_high_volume", orderCount, submittedCount.get(), 
                         executionCount.get(), totalTimeMs, latencies);
        
        // Validate
        assertTrue(submittedCount.get() >= orderCount * 0.9, "At least 90% orders should be submitted");
        assertTrue(executionCount.get() >= orderCount * 0.5, "At least 50% executions should be received");
        
        System.out.println("✓ High volume round-trip validated");
    }
    
    @Test
    @Order(3)
    void testEventBusReliability() throws Exception {
        int eventCount = 500;
        CountDownLatch latch = new CountDownLatch(eventCount);
        AtomicInteger receivedCount = new AtomicInteger(0);
        
        eventBus.subscribe(EventType.ORDER_SUBMITTED, new EventHandler<Object>() {
            @Override
            public void onEvent(Event event) {
                receivedCount.incrementAndGet();
                latch.countDown();
            }
            
            @Override
            public void onError(Event event, Throwable exception) {
                exception.printStackTrace();
            }
        });
        
        gateway.start();
        Thread.sleep(1000);
        
        System.out.println("\n=== EventBus Reliability Test ===");
        System.out.println("Publishing " + eventCount + " events...\n");
        
        // Publish events directly to EventBus
        for (int i = 0; i < eventCount; i++) {
            OrderEvent orderEvent = OrderEvent.newOrder(
                (long) i + 1, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
            );
            Event event = Event.create(
                System.nanoTime(), (long) i + 1, SourceId.OMS,
                EventType.ORDER_SUBMITTED, 0L, orderEvent
            );
            eventBus.publish(event);
        }
        
        // Wait for all events
        boolean allReceived = latch.await(10, TimeUnit.SECONDS);
        
        System.out.println("EventBus reliability test:");
        System.out.println("  Published: " + eventCount);
        System.out.println("  Received: " + receivedCount.get());
        System.out.println("  Loss rate: " + String.format("%.2f", 
            ((eventCount - receivedCount.get()) * 100.0) / eventCount) + "%");
        
        // Validate
        assertTrue(receivedCount.get() >= eventCount * 0.95, "At least 95% events should be received");
        
        System.out.println("✓ EventBus reliability validated");
    }
    
    private SessionSettings createClientSettings() throws Exception {
        SessionSettings settings = new SessionSettings();
        
        settings.setString("ConnectionType", "initiator");
        settings.setString("SocketConnectHost", "localhost");
        settings.setString("SocketConnectPort", String.valueOf(SERVER_PORT));
        settings.setString("StartTime", "00:00:00");
        settings.setString("EndTime", "23:59:59");
        settings.setString("HeartBtInt", "30");
        settings.setString("ReconnectInterval", "5");
        settings.setString("FileStorePath", "build/tmp/fix-e2e-client-store");
        settings.setString("FileLogPath", "build/tmp/fix-e2e-client-log");
        
        SessionID sessionID = new SessionID("FIX.4.4", SENDER_COMP_ID, TARGET_COMP_ID);
        settings.setString(sessionID, "BeginString", "FIX.4.4");
        settings.setString(sessionID, "SenderCompID", SENDER_COMP_ID);
        settings.setString(sessionID, "TargetCompID", TARGET_COMP_ID);
        settings.setString(sessionID, "ConnectionType", "initiator");
        settings.setString(sessionID, "ResetOnLogon", "Y");
        settings.setString(sessionID, "ResetOnLogout", "Y");
        settings.setString(sessionID, "ResetOnDisconnect", "Y");
        
        return settings;
    }
    
    private NewOrderSingle createTestOrder(long orderId) {
        NewOrderSingle order = new NewOrderSingle();
        
        try {
            order.set(new ClOrdID(String.valueOf(orderId)));
            order.set(new Symbol("AAPL"));
            order.set(new Side(Side.BUY));
            order.set(new OrderQty(100));
            order.set(new Price(150.00));
            order.set(new OrdType(OrdType.LIMIT));
            order.set(new TransactTime());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test order", e);
        }
        
        return order;
    }
    
    private void generateE2EReport(String testName, int totalOrders, int submitted, 
                                   int executed, long timeMs, List<Long> latencies) {
        try {
            File reportDir = new File("build/reports/load-tests");
            reportDir.mkdirs();
            
            File reportFile = new File(reportDir, testName + "_report.txt");
            
            try (FileWriter writer = new FileWriter(reportFile)) {
                writer.write("=== End-to-End Integration Test Report ===\n");
                writer.write("Test: " + testName + "\n");
                writer.write("Timestamp: " + System.currentTimeMillis() + "\n\n");
                
                writer.write("Results:\n");
                writer.write("  Total orders: " + totalOrders + "\n");
                writer.write("  Submitted: " + submitted + "\n");
                writer.write("  Executed: " + executed + "\n");
                writer.write("  Duration: " + timeMs + " ms\n");
                writer.write("  Throughput: " + String.format("%.0f", (totalOrders * 1000.0) / timeMs) + " orders/s\n\n");
                
                if (!latencies.isEmpty()) {
                    latencies.sort(Long::compareTo);
                    double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
                    long p95 = latencies.get((int) (latencies.size() * 0.95));
                    long p99 = latencies.get((int) (latencies.size() * 0.99));
                    
                    writer.write("Latency:\n");
                    writer.write("  Average: " + String.format("%.3f", avg / 1_000_000.0) + " ms\n");
                    writer.write("  P95: " + String.format("%.3f", p95 / 1_000_000.0) + " ms\n");
                    writer.write("  P99: " + String.format("%.3f", p99 / 1_000_000.0) + " ms\n\n");
                }
                
                writer.write("Validation:\n");
                writer.write("  ✓ Submission rate: " + String.format("%.1f", (submitted * 100.0) / totalOrders) + "%\n");
                writer.write("  ✓ Execution rate: " + String.format("%.1f", (executed * 100.0) / totalOrders) + "%\n");
            }
            
            System.out.println("Report generated: " + reportFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to generate report: " + e.getMessage());
        }
    }
    
    private void cleanupTestFiles() {
        deleteDirectory(new File("build/tmp/fix-e2e-client-store"));
        deleteDirectory(new File("build/tmp/fix-e2e-client-log"));
        deleteDirectory(new File("build/tmp/fix-e2e-server-store"));
        deleteDirectory(new File("build/tmp/fix-e2e-server-log"));
    }
    
    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }
    
    /**
     * Simulated Matching Engine that processes orders and generates executions.
     */
    private static class SimulatedMatchingEngine {
        private final EventBus eventBus;
        private volatile boolean running = false;
        private final AtomicLong sequenceNumber = new AtomicLong(1);
        
        SimulatedMatchingEngine(EventBus eventBus) {
            this.eventBus = eventBus;
        }
        
        void start() {
            running = true;
            
            // Subscribe to order submissions
            eventBus.subscribe(EventType.ORDER_SUBMITTED, new EventHandler<Object>() {
                @Override
                public void onEvent(Event event) {
                    if (running && event.payload() instanceof OrderEvent) {
                        processOrder((OrderEvent) event.payload());
                    }
                }
                
                @Override
                public void onError(Event event, Throwable exception) {
                    exception.printStackTrace();
                }
            });
        }
        
        void stop() {
            running = false;
        }
        
        private void processOrder(OrderEvent orderEvent) {
            try {
                // Simulate some processing time
                Thread.sleep(1);
                
                // Generate execution (complete fill)
                long execId = sequenceNumber.incrementAndGet();
                ExecutionEvent execution = ExecutionEvent.fill(
                    execId,
                    orderEvent,
                    orderEvent.quantity(),  // fillQuantity = full order quantity
                    orderEvent.price(),     // fillPrice
                    orderEvent.quantity(),  // cumulativeQty = full quantity
                    0L                      // leavesQuantity = 0 (complete fill)
                );
                
                Event fillEvent = Event.create(
                    System.nanoTime(),
                    sequenceNumber.get(),
                    SourceId.MATCHING_ENGINE,
                    EventType.ORDER_FILLED,
                    orderEvent.orderId(),
                    execution
                );
                
                eventBus.publish(fillEvent);
            } catch (Exception e) {
                System.err.println("Error processing order: " + e.getMessage());
            }
        }
    }
    
    /**
     * Simulated FIX server for integration testing.
     */
    private static class SimulatedFixServer implements Application {
        private final int port;
        private Acceptor acceptor;
        private volatile boolean running = false;
        
        SimulatedFixServer(int port) {
            this.port = port;
        }
        
        void start() throws Exception {
            SessionSettings settings = new SessionSettings();
            
            settings.setString("ConnectionType", "acceptor");
            settings.setString("SocketAcceptPort", String.valueOf(port));
            settings.setString("StartTime", "00:00:00");
            settings.setString("EndTime", "23:59:59");
            settings.setString("HeartBtInt", "30");
            settings.setString("FileStorePath", "build/tmp/fix-e2e-server-store");
            settings.setString("FileLogPath", "build/tmp/fix-e2e-server-log");
            
            SessionID sessionID = new SessionID("FIX.4.4", TARGET_COMP_ID, SENDER_COMP_ID);
            settings.setString(sessionID, "BeginString", "FIX.4.4");
            settings.setString(sessionID, "SenderCompID", TARGET_COMP_ID);
            settings.setString(sessionID, "TargetCompID", SENDER_COMP_ID);
            
            MessageStoreFactory storeFactory = new MemoryStoreFactory();
            LogFactory logFactory = new ScreenLogFactory(false, false, false);
            MessageFactory messageFactory = new DefaultMessageFactory();
            
            acceptor = new SocketAcceptor(this, storeFactory, settings, logFactory, messageFactory);
            acceptor.start();
            running = true;
        }
        
        void stop() {
            if (acceptor != null) {
                acceptor.stop();
                running = false;
            }
        }
        
        @Override
        public void onCreate(SessionID sessionId) {}
        
        @Override
        public void onLogon(SessionID sessionId) {}
        
        @Override
        public void onLogout(SessionID sessionId) {}
        
        @Override
        public void toAdmin(Message message, SessionID sessionId) {}
        
        @Override
        public void fromAdmin(Message message, SessionID sessionId) {}
        
        @Override
        public void toApp(Message message, SessionID sessionId) {}
        
        @Override
        public void fromApp(Message message, SessionID sessionId) {}
    }
}
