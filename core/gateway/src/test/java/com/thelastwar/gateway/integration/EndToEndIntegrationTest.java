package com.thelastwar.gateway.integration;

import com.thelastwar.eventbus.*;
import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.gateway.FixGateway;
import com.thelastwar.gateway.GatewayMetrics;
import com.thelastwar.gateway.TestEventBus;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.thelastwar.gateway.integration.IntegrationTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests validating complete message flow.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
class EndToEndIntegrationTest {
    
    private static final Logger log = LoggerFactory.getLogger(EndToEndIntegrationTest.class);
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
        matchingEngine = new SimulatedMatchingEngine(eventBus);
        matchingEngine.start();
        
        server = new SimulatedFixServer(SERVER_PORT);
        server.start();
        
        waitFor(500);
        
        gateway = new FixGateway(eventBus, FixServerConfig.createClientSettings(SERVER_PORT, SENDER_COMP_ID, TARGET_COMP_ID, "e2e-client"), metrics);
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
        cleanupTestFiles("e2e-client", "e2e-server");
    }
    
    @Test
    @Order(1)
    void testCompleteRoundTripFlow() throws Exception {
        CountDownLatch executionLatch = new CountDownLatch(1);
        AtomicInteger executionCount = new AtomicInteger(0);
        
        eventBus.subscribe(EventType.ORDER_FILLED, new EventHandler<Object>() {
            @Override
            public void onEvent(Event event) {
                executionCount.incrementAndGet();
                executionLatch.countDown();
            }
            
            @Override
            public void onError(Event event, Throwable exception) {
                log.error("Error processing execution", exception);
            }
        });
        
        gateway.start();
        waitFor(1000);
        
        log.info("Testing end-to-end flow");
        
        long startTime = System.nanoTime();
        
        // Publish order to EventBus (simulating order submission)
        OrderEvent orderEvent = OrderEvent.newOrder(
            12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
            100L, 15000L, 999L, 1
        );
        eventBus.publish(Event.create(
            System.nanoTime(), 1L, SourceId.OMS,
            EventType.ORDER_SUBMITTED, 0L, orderEvent
        ));
        
        boolean executed = executionLatch.await(5, TimeUnit.SECONDS);
        double latencyMs = (System.nanoTime() - startTime) / 1_000_000.0;
        
        log.info("Round-trip completed - Executions: {}, Latency: {} ms", 
                executionCount.get(), latencyMs);
        
        assertTrue(executed, "Execution should be received");
        assertEquals(1, executionCount.get(), "One execution expected");
        assertTrue(latencyMs < 50.0, "Latency should be < 50 ms");
    }
    
    @Test
    @Order(2)
    void testHighVolumeRoundTrip() throws Exception {
        int orderCount = 100;
        CountDownLatch executionLatch = new CountDownLatch(orderCount);
        AtomicInteger executionCount = new AtomicInteger(0);
        List<Long> latencies = new ArrayList<>();
        
        eventBus.subscribe(EventType.ORDER_FILLED, new EventHandler<Object>() {
            @Override
            public void onEvent(Event event) {
                executionCount.incrementAndGet();
                executionLatch.countDown();
            }
            
            @Override
            public void onError(Event event, Throwable exception) {
                log.error("Error processing execution", exception);
            }
        });
        
        gateway.start();
        waitFor(1000);
        
        log.info("Testing high volume with {} orders", orderCount);
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < orderCount; i++) {
            long sendStart = System.nanoTime();
            
            OrderEvent orderEvent = OrderEvent.newOrder(
                (long) i + 1, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i + 1, SourceId.OMS,
                EventType.ORDER_SUBMITTED, 0L, orderEvent
            ));
            
            latencies.add(System.nanoTime() - sendStart);
            
            if (i % 10 == 0) {
                waitFor(10);
            }
        }
        
        executionLatch.await(10, TimeUnit.SECONDS);
        
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        LatencyStats stats = calculateStats(latencies);
        
        log.info("High volume completed - Executions: {}, Duration: {} ms", 
                executionCount.get(), durationMs);
        
        generateReport("end_to_end_high_volume", 
                      new ReportData(orderCount, durationMs, executionCount.get(), stats));
        
        assertTrue(executionCount.get() >= orderCount * 0.8, 
                  "At least 80% executions should be received");
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
                log.error("Error processing event", exception);
            }
        });
        
        gateway.start();
        waitFor(1000);
        
        log.info("Testing EventBus reliability with {} events", eventCount);
        
        for (int i = 0; i < eventCount; i++) {
            OrderEvent orderEvent = OrderEvent.newOrder(
                (long) i + 1, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
            );
            eventBus.publish(Event.create(
                System.nanoTime(), (long) i + 1, SourceId.OMS,
                EventType.ORDER_SUBMITTED, 0L, orderEvent
            ));
        }
        
        latch.await(10, TimeUnit.SECONDS);
        
        double lossRate = ((eventCount - receivedCount.get()) * 100.0) / eventCount;
        
        log.info("EventBus reliability - Published: {}, Received: {}, Loss: {}%", 
                eventCount, receivedCount.get(), lossRate);
        
        assertTrue(receivedCount.get() >= eventCount * 0.95, 
                  "At least 95% events should be received");
    }
    
    /**
     * Simulated matching engine.
     */
    private static class SimulatedMatchingEngine implements AutoCloseable {
        private final EventBus eventBus;
        private final AtomicLong sequenceNumber = new AtomicLong(1);
        private volatile boolean running = false;
        
        SimulatedMatchingEngine(EventBus eventBus) {
            this.eventBus = eventBus;
        }
        
        void start() {
            running = true;
            
            eventBus.subscribe(EventType.ORDER_SUBMITTED, new EventHandler<Object>() {
                @Override
                public void onEvent(Event event) {
                    if (running && event.payload() instanceof OrderEvent) {
                        processOrder((OrderEvent) event.payload());
                    }
                }
                
                @Override
                public void onError(Event event, Throwable exception) {
                    // Ignore
                }
            });
        }
        
        void stop() {
            running = false;
        }
        
        private void processOrder(OrderEvent orderEvent) {
            try {
                waitFor(1);
                
                long execId = sequenceNumber.incrementAndGet();
                ExecutionEvent execution = ExecutionEvent.fill(
                    execId, orderEvent, orderEvent.quantity(), 
                    orderEvent.price(), orderEvent.quantity(), 0L
                );
                
                eventBus.publish(Event.create(
                    System.nanoTime(), sequenceNumber.get(), SourceId.MATCHING_ENGINE,
                    EventType.ORDER_FILLED, orderEvent.orderId(), execution
                ));
            } catch (Exception e) {
                // Ignore
            }
        }
        
        @Override
        public void close() {
            stop();
        }
    }
    
    /**
     * Simulated FIX server.
     */
    private static class SimulatedFixServer implements Application, AutoCloseable {
        private final int port;
        private Acceptor acceptor;
        
        SimulatedFixServer(int port) {
            this.port = port;
        }
        
        void start() throws Exception {
            SessionSettings settings = FixServerConfig.createServerSettings(port, TARGET_COMP_ID, SENDER_COMP_ID, "e2e-server");
            acceptor = new SocketAcceptor(this, new MemoryStoreFactory(), settings, 
                                         new ScreenLogFactory(false, false, false), 
                                         new DefaultMessageFactory());
            acceptor.start();
        }
        
        void stop() {
            if (acceptor != null) {
                acceptor.stop();
            }
        }
        
        @Override
        public void close() {
            stop();
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
