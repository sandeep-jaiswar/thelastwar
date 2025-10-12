package com.thelastwar.gateway.integration;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventHandler;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.gateway.FixGateway;
import com.thelastwar.gateway.GatewayMetrics;
import com.thelastwar.gateway.TestEventBus;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.thelastwar.gateway.integration.IntegrationTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Load tests for FIX Gateway.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("load")
class FixGatewayLoadTest {
    
    private static final Logger log = LoggerFactory.getLogger(FixGatewayLoadTest.class);
    private static final int SERVER_PORT = 19877;
    private static final String SENDER_COMP_ID = "LOADTEST_CLIENT";
    private static final String TARGET_COMP_ID = "LOADTEST_SERVER";
    
    private TestEventBus eventBus;
    private FixGateway gateway;
    private SimulatedFixServer server;
    private GatewayMetrics metrics;
    
    @BeforeEach
    void setUp() throws Exception {
        eventBus = new TestEventBus();
        eventBus.start();
        metrics = new GatewayMetrics("fix-load-test-gateway");
        server = new SimulatedFixServer(SERVER_PORT);
        server.start();
        waitFor(500);
        gateway = new FixGateway(eventBus, FixServerConfig.createClientSettings(SERVER_PORT, SENDER_COMP_ID, TARGET_COMP_ID, "load-client"), metrics);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (gateway != null && gateway.isRunning()) {
            gateway.stop();
        }
        if (server != null) {
            server.stop();
        }
        if (eventBus != null) {
            eventBus.stop();
        }
        cleanupTestFiles("load-client", "load-server");
    }
    
    @Test
    @Order(1)
    void testSustainedThroughput() throws Exception {
        int totalMessages = 10_000;
        List<Long> latencies = new ArrayList<>();
        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalMessages);
        
        eventBus.subscribe(EventType.ORDER_ACCEPTED, createCountingHandler(receivedCount, latch));
        
        gateway.start();
        waitFor(1000);
        
        log.info("Starting throughput test with {} messages", totalMessages);
        long startTime = System.nanoTime();
        sendMessages(gateway, totalMessages, latencies);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        
        latch.await(5, TimeUnit.SECONDS);
        
        LatencyStats stats = calculateStats(latencies);
        double throughput = (totalMessages * 1000.0) / durationMs;
        
        log.info("Throughput: {} msg/s, Avg Latency: {} ms", throughput, stats.avg / 1_000_000.0);
        
        generateReport("fix_gateway_load_test", 
                      new ReportData(totalMessages, durationMs, receivedCount.get(), stats));
        
        assertTrue(throughput >= 800, "Throughput should be at least 800 msg/s");
        assertTrue(stats.avg / 1_000_000.0 < 10.0, "Average latency should be < 10 ms");
    }
    
    @Test
    @Order(2)
    void testLatencyDistribution() throws Exception {
        List<Long> latencies = new ArrayList<>();
        
        gateway.start();
        waitFor(1000);
        
        log.info("Testing latency distribution");
        
        // Warmup
        sendMessages(gateway, 100, new ArrayList<>());
        waitFor(100);
        
        // Measure
        for (int i = 0; i < 1000; i++) {
            long start = System.nanoTime();
            gateway.sendMessage(FixMessageFactory.createTestOrder(i + 1000));
            latencies.add(System.nanoTime() - start);
        }
        
        LatencyStats stats = calculateStats(latencies);
        log.info("Latency P99: {} ms", stats.p99 / 1_000_000.0);
        
        assertTrue(stats.p99 / 1_000_000.0 < 5.0, "P99 latency should be < 5 ms");
    }
    
    @Test
    @Order(3)
    void testNoMessageDrops() throws Exception {
        int messageCount = 5000;
        AtomicInteger sentCount = new AtomicInteger(0);
        
        gateway.start();
        waitFor(1000);
        
        log.info("Testing message drop rate");
        
        for (int i = 0; i < messageCount; i++) {
            try {
                gateway.sendMessage(FixMessageFactory.createTestOrder(i + 1));
                sentCount.incrementAndGet();
            } catch (Exception e) {
                log.warn("Failed to send message {}", i, e);
            }
            
            if (i % 500 == 0) {
                waitFor(50);
            }
        }
        
        assertEquals(messageCount, sentCount.get(), "All messages should be sent");
    }
    
    private void sendMessages(FixGateway gw, int count, List<Long> latencies) {
        for (int i = 0; i < count; i++) {
            long start = System.nanoTime();
            try {
                gw.sendMessage(FixMessageFactory.createTestOrder(i + 1));
                latencies.add(System.nanoTime() - start);
            } catch (Exception e) {
                log.warn("Session not found for message {}", i);
            }
            
            if (i % 100 == 0 && i > 0) {
                try {
                    waitFor(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
    
    private EventHandler<Object> createCountingHandler(AtomicInteger counter, CountDownLatch latch) {
        return new EventHandler<Object>() {
            @Override
            public void onEvent(Event event) {
                counter.incrementAndGet();
                latch.countDown();
            }
            
            @Override
            public void onError(Event event, Throwable exception) {
                log.error("Error processing event", exception);
            }
        };
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
            SessionSettings settings = FixServerConfig.createServerSettings(port, TARGET_COMP_ID, SENDER_COMP_ID, "load-server");
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
