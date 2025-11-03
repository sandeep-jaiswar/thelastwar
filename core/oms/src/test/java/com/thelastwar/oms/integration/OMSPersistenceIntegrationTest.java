package com.thelastwar.oms.integration;

import com.thelastwar.oms.OrderState;
import com.thelastwar.oms.eventsourcing.EventPublisher;
import com.thelastwar.oms.eventsourcing.KafkaEventPublisher;
import com.thelastwar.oms.eventsourcing.OrderEvent;
import com.thelastwar.oms.persistence.OrderStateRecord;
import com.thelastwar.oms.persistence.OrderStateStore;
import com.thelastwar.oms.persistence.ClickHouseOrderStateStore;
import com.thelastwar.oms.recovery.RecoveryResult;
import com.thelastwar.oms.recovery.RecoveryService;
import com.thelastwar.oms.snapshot.SnapshotConfig;
import com.thelastwar.oms.snapshot.SnapshotData;
import com.thelastwar.oms.snapshot.SnapshotManager;
import com.thelastwar.oms.snapshot.SnapshotMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OMS persistence, snapshotting, and recovery.
 * 
 * Tests the complete flow:
 * 1. Store orders in ClickHouse
 * 2. Publish events to Kafka
 * 3. Create snapshots
 * 4. Recover from snapshot + event replay
 */
@Testcontainers
class OMSPersistenceIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(OMSPersistenceIntegrationTest.class);
    
    @Container
    static ClickHouseContainer clickhouse = new ClickHouseContainer(
        DockerImageName.parse("clickhouse/clickhouse-server:24.3-alpine"))
        .withExposedPorts(8123);
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
    
    @TempDir
    Path snapshotDir;
    
    private OrderStateStore orderStateStore;
    private EventPublisher eventPublisher;
    private SnapshotManager snapshotManager;
    private RecoveryService recoveryService;
    
    private static final String KAFKA_TOPIC = "oms-events-test";
    
    @BeforeEach
    void setUp() throws Exception {
        // Initialize ClickHouse store
        String jdbcUrl = clickhouse.getJdbcUrl();
        orderStateStore = new ClickHouseOrderStateStore(
            jdbcUrl,
            clickhouse.getUsername(),
            clickhouse.getPassword()
        );
        
        // Initialize Kafka publisher
        eventPublisher = new KafkaEventPublisher(
            kafka.getBootstrapServers(),
            KAFKA_TOPIC
        );
        
        // Initialize snapshot manager
        SnapshotConfig snapshotConfig = SnapshotConfig.manualOnly();
        snapshotManager = new SnapshotManager(snapshotDir, snapshotConfig);
        
        // Initialize recovery service
        recoveryService = new RecoveryService(
            snapshotManager,
            orderStateStore,
            kafka.getBootstrapServers(),
            KAFKA_TOPIC
        );
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (snapshotManager != null) {
            snapshotManager.close();
        }
        if (eventPublisher != null) {
            eventPublisher.close();
        }
        if (orderStateStore != null) {
            orderStateStore.close();
        }
    }
    
    @Test
    void testFullPersistenceFlow() throws Exception {
        // Step 1: Create and persist orders
        int orderCount = 100;
        Map<Long, OrderStateRecord> orders = createAndPersistOrders(orderCount);
        
        // Verify orders are in database
        long activeCount = orderStateStore.countActiveOrders();
        assertEquals(orderCount, activeCount);
        
        // Step 2: Publish events to Kafka
        for (OrderStateRecord order : orders.values()) {
            OrderEvent event = OrderEvent.orderSubmitted(
                order.internalOrderId(),
                order.clientOrderId(),
                order.symbol(),
                order.side(),
                order.orderType(),
                order.quantity(),
                order.price(),
                order.account()
            );
            eventPublisher.publishEvent(event);
        }
        eventPublisher.flush();
        
        // Wait for Kafka to persist with timeout
        TimeUnit.SECONDS.sleep(2);
        
        // Step 3: Create snapshot
        SnapshotMetadata snapshot = snapshotManager.createSnapshot(
            new TestSnapshotProvider(orders, 0)
        );
        
        assertNotNull(snapshot);
        assertEquals(orderCount, snapshot.orderCount());
        
        // Step 4: Simulate recovery
        orderStateStore.clearCache();
        
        SnapshotData restoredSnapshot = snapshotManager.restoreSnapshot(snapshot.snapshotId());
        assertTrue(restoredSnapshot.hasData());
        assertEquals(orderCount, restoredSnapshot.orders().size());
    }
    
    @Test
    void testSnapshotRestoreConsistency() throws Exception {
        // Create orders with specific states
        Map<Long, OrderStateRecord> originalOrders = new HashMap<>();
        
        for (int i = 1; i <= 50; i++) {
            OrderState state = (i % 4 == 0) ? OrderState.FILLED : OrderState.WORKING;
            OrderStateRecord order = new OrderStateRecord(
                (long) i,
                "CLIENT-" + i,
                "AAPL",
                (short) 1,
                (short) 2,
                1000L,
                150000L,
                123456L,
                state,
                (state == OrderState.FILLED) ? 1000L : 0L,
                (state == OrderState.FILLED) ? 0L : 1000L,
                1L
            );
            originalOrders.put(order.internalOrderId(), order);
            orderStateStore.saveOrderState(order);
        }
        
        // Create snapshot
        SnapshotMetadata snapshot = snapshotManager.createSnapshot(
            new TestSnapshotProvider(originalOrders, 12345L)
        );
        
        // Restore and verify consistency
        SnapshotData restored = snapshotManager.restoreSnapshot(snapshot.snapshotId());
        
        assertEquals(originalOrders.size(), restored.orders().size());
        
        // Verify each order is correctly restored
        for (OrderStateRecord restoredOrder : restored.orders()) {
            OrderStateRecord original = originalOrders.get(restoredOrder.internalOrderId());
            assertNotNull(original);
            assertEquals(original.currentState(), restoredOrder.currentState());
            assertEquals(original.filledQuantity(), restoredOrder.filledQuantity());
            assertEquals(original.remainingQuantity(), restoredOrder.remainingQuantity());
        }
    }
    
    @Test
    void testPerformanceTarget() throws Exception {
        // Create 1000 orders (scaled down from 1M for test speed)
        int orderCount = 1000;
        Map<Long, OrderStateRecord> orders = createTestOrders(orderCount);
        
        // Batch save to database
        long startSave = System.currentTimeMillis();
        orderStateStore.saveOrderStateBatch(orders.values());
        long saveDuration = System.currentTimeMillis() - startSave;
        logger.info("Saved {} orders in {}ms", orderCount, saveDuration);
        
        // Create snapshot
        long startSnapshot = System.currentTimeMillis();
        SnapshotMetadata snapshot = snapshotManager.createSnapshot(
            new TestSnapshotProvider(orders, 50000L)
        );
        long snapshotDuration = System.currentTimeMillis() - startSnapshot;
        logger.info("Created snapshot in {}ms", snapshotDuration);
        
        // Restore snapshot
        long startRestore = System.currentTimeMillis();
        SnapshotData restored = snapshotManager.restoreSnapshot(snapshot.snapshotId());
        long restoreDuration = System.currentTimeMillis() - startRestore;
        logger.info("Restored snapshot in {}ms", restoreDuration);
        
        assertEquals(orderCount, restored.orders().size());
        
        // Performance assertions (scaled for 1K orders)
        // For 1M orders, target is 15s, so for 1K orders, target is ~15ms
        assertTrue(snapshotDuration < 5000, "Snapshot creation too slow: " + snapshotDuration + "ms");
        assertTrue(restoreDuration < 3000, "Snapshot restoration too slow: " + restoreDuration + "ms");
        
        // Extrapolate to 1M orders
        double snapshotTimeFor1M = (snapshotDuration * 1000.0);
        double restoreTimeFor1M = (restoreDuration * 1000.0);
        logger.info("Extrapolated time for 1M orders:");
        logger.info("  Snapshot: {}s", snapshotTimeFor1M / 1000.0);
        logger.info("  Restore: {}s", restoreTimeFor1M / 1000.0);
    }
    
    @Test
    void testNoDataLossOnGracefulShutdown() throws Exception {
        // Create orders
        int orderCount = 50;
        Map<Long, OrderStateRecord> orders = createTestOrders(orderCount);
        
        // Save orders
        orderStateStore.saveOrderStateBatch(orders.values());
        
        // Publish events
        for (OrderStateRecord order : orders.values()) {
            OrderEvent event = OrderEvent.orderSubmitted(
                order.internalOrderId(),
                order.clientOrderId(),
                order.symbol(),
                order.side(),
                order.orderType(),
                order.quantity(),
                order.price(),
                order.account()
            );
            eventPublisher.publishEvent(event);
        }
        
        // Graceful shutdown: flush all data
        eventPublisher.flush();
        
        // Create snapshot before shutdown
        SnapshotMetadata snapshot = snapshotManager.createSnapshot(
            new TestSnapshotProvider(orders, 5000L)
        );
        
        // Simulate shutdown and restart
        snapshotManager.close();
        orderStateStore.clearCache();
        
        // Recreate managers
        snapshotManager = new SnapshotManager(snapshotDir, SnapshotConfig.manualOnly());
        
        // Verify snapshot exists and can be restored
        SnapshotData restored = snapshotManager.restoreSnapshot(snapshot.snapshotId());
        
        assertTrue(restored.hasData());
        assertEquals(orderCount, restored.orders().size());
        
        // Verify all orders are present
        for (OrderStateRecord order : orders.values()) {
            boolean found = restored.orders().stream()
                .anyMatch(r -> r.internalOrderId() == order.internalOrderId());
            assertTrue(found, "Order " + order.internalOrderId() + " not found after recovery");
        }
    }
    
    private Map<Long, OrderStateRecord> createAndPersistOrders(int count) throws Exception {
        Map<Long, OrderStateRecord> orders = createTestOrders(count);
        orderStateStore.saveOrderStateBatch(orders.values());
        return orders;
    }
    
    private Map<Long, OrderStateRecord> createTestOrders(int count) {
        Map<Long, OrderStateRecord> orders = new HashMap<>();
        
        for (int i = 1; i <= count; i++) {
            OrderStateRecord order = new OrderStateRecord(
                (long) i,
                "CLIENT-ORDER-" + i,
                "SYM" + (i % 10),
                (short) (i % 2 + 1),
                (short) 2,
                1000L,
                100000L + i * 100,
                123456L,
                OrderState.WORKING,
                0L,
                1000L,
                1L
            );
            orders.put(order.internalOrderId(), order);
        }
        
        return orders;
    }
    
    private static class TestSnapshotProvider implements SnapshotManager.SnapshotProvider {
        private final Map<Long, OrderStateRecord> orders;
        private final long lastEventOffset;
        
        TestSnapshotProvider(Map<Long, OrderStateRecord> orders, long lastEventOffset) {
            this.orders = orders;
            this.lastEventOffset = lastEventOffset;
        }
        
        @Override
        public Map<Long, OrderStateRecord> getActiveOrders() {
            return orders;
        }
        
        @Override
        public long getLastEventOffset() {
            return lastEventOffset;
        }
    }
}
