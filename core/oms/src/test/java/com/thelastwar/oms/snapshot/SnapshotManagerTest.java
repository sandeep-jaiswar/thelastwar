package com.thelastwar.oms.snapshot;

import com.thelastwar.oms.OrderState;
import com.thelastwar.oms.persistence.OrderStateRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SnapshotManager.
 */
class SnapshotManagerTest {
    
    @TempDir
    Path tempDir;
    
    private SnapshotManager snapshotManager;
    
    @BeforeEach
    void setUp() throws IOException {
        SnapshotConfig config = SnapshotConfig.manualOnly(); // No periodic snapshots for tests
        snapshotManager = new SnapshotManager(tempDir, config);
    }
    
    @AfterEach
    void tearDown() {
        if (snapshotManager != null) {
            snapshotManager.close();
        }
    }
    
    @Test
    void testCreateAndRestoreSnapshot() throws IOException {
        // Create test data
        Map<Long, OrderStateRecord> orders = createTestOrders(100);
        TestSnapshotProvider provider = new TestSnapshotProvider(orders, 12345L);
        
        // Create snapshot
        SnapshotMetadata metadata = snapshotManager.createSnapshot(provider);
        
        assertNotNull(metadata);
        assertEquals(100, metadata.orderCount());
        assertEquals(12345L, metadata.lastEventOffset());
        assertTrue(metadata.fileSizeBytes() > 0);
        
        // Restore snapshot
        SnapshotData restored = snapshotManager.restoreSnapshot(metadata.snapshotId());
        
        assertTrue(restored.hasData());
        assertEquals(100, restored.orders().size());
        assertEquals(12345L, restored.metadata().lastEventOffset());
    }
    
    @Test
    void testCreateSnapshotCreatesFile() throws IOException {
        Map<Long, OrderStateRecord> orders = createTestOrders(10);
        TestSnapshotProvider provider = new TestSnapshotProvider(orders, 100L);
        
        SnapshotMetadata metadata = snapshotManager.createSnapshot(provider);
        
        Path snapshotFile = tempDir.resolve(metadata.snapshotId() + ".snapshot.gz");
        assertTrue(Files.exists(snapshotFile));
        assertTrue(Files.size(snapshotFile) > 0);
    }
    
    @Test
    void testRestoreLatestSnapshot() throws Exception {
        // Create multiple snapshots
        for (int i = 1; i <= 3; i++) {
            Thread.sleep(10); // Ensure different timestamps
            Map<Long, OrderStateRecord> orders = createTestOrders(i * 10);
            TestSnapshotProvider provider = new TestSnapshotProvider(orders, i * 100L);
            snapshotManager.createSnapshot(provider);
        }
        
        // Restore latest
        SnapshotData latest = snapshotManager.restoreLatestSnapshot();
        
        assertTrue(latest.hasData());
        assertEquals(30, latest.orders().size()); // Latest had 30 orders
        assertEquals(300L, latest.metadata().lastEventOffset()); // Latest offset
    }
    
    @Test
    void testListSnapshots() throws Exception {
        // Create snapshots
        for (int i = 1; i <= 5; i++) {
            Thread.sleep(10);
            Map<Long, OrderStateRecord> orders = createTestOrders(i * 10);
            TestSnapshotProvider provider = new TestSnapshotProvider(orders, i * 100L);
            snapshotManager.createSnapshot(provider);
        }
        
        List<SnapshotMetadata> snapshots = snapshotManager.listSnapshots();
        
        assertEquals(5, snapshots.size());
        // Should be sorted by timestamp descending
        assertTrue(snapshots.get(0).timestamp() > snapshots.get(4).timestamp());
    }
    
    @Test
    void testFindLatestSnapshot() throws IOException {
        assertNull(snapshotManager.findLatestSnapshot()); // No snapshots yet
        
        Map<Long, OrderStateRecord> orders = createTestOrders(50);
        TestSnapshotProvider provider = new TestSnapshotProvider(orders, 500L);
        SnapshotMetadata created = snapshotManager.createSnapshot(provider);
        
        SnapshotMetadata latest = snapshotManager.findLatestSnapshot();
        
        assertNotNull(latest);
        assertEquals(created.snapshotId(), latest.snapshotId());
        assertEquals(50, latest.orderCount());
    }
    
    @Test
    void testRestoreNonExistentSnapshot() {
        assertThrows(IOException.class, () -> {
            snapshotManager.restoreSnapshot("non-existent-snapshot");
        });
    }
    
    @Test
    void testRestoreLatestSnapshotWhenNoneExists() throws IOException {
        SnapshotData result = snapshotManager.restoreLatestSnapshot();
        
        assertFalse(result.hasData());
        assertNull(result.metadata());
        assertTrue(result.orders().isEmpty());
    }
    
    @Test
    void testSnapshotWithLargeOrderCount() throws IOException {
        // Test with 10,000 orders
        Map<Long, OrderStateRecord> orders = createTestOrders(10_000);
        TestSnapshotProvider provider = new TestSnapshotProvider(orders, 100_000L);
        
        long startTime = System.currentTimeMillis();
        SnapshotMetadata metadata = snapshotManager.createSnapshot(provider);
        long createTime = System.currentTimeMillis() - startTime;
        
        System.out.println("Created snapshot with 10K orders in " + createTime + "ms");
        
        startTime = System.currentTimeMillis();
        SnapshotData restored = snapshotManager.restoreSnapshot(metadata.snapshotId());
        long restoreTime = System.currentTimeMillis() - startTime;
        
        System.out.println("Restored snapshot with 10K orders in " + restoreTime + "ms");
        
        assertEquals(10_000, restored.orders().size());
        
        // Performance expectations: should be fast even for 10K orders
        assertTrue(createTime < 5000, "Create should be < 5s");
        assertTrue(restoreTime < 3000, "Restore should be < 3s");
    }
    
    @Test
    void testSnapshotRetention() throws Exception {
        // Create manager with retention policy
        SnapshotConfig config = new SnapshotConfig(0, 3); // Keep only 3 snapshots
        try (SnapshotManager manager = new SnapshotManager(tempDir, config)) {
            
            // Create 5 snapshots
            for (int i = 1; i <= 5; i++) {
                Thread.sleep(10);
                Map<Long, OrderStateRecord> orders = createTestOrders(i * 10);
                TestSnapshotProvider provider = new TestSnapshotProvider(orders, i * 100L);
                manager.createSnapshot(provider);
            }
            
            // Should only have 3 snapshots (most recent)
            List<SnapshotMetadata> snapshots = manager.listSnapshots();
            assertEquals(3, snapshots.size());
        }
    }
    
    @Test
    void testSnapshotPreservesOrderData() throws IOException {
        // Create order with specific data
        OrderStateRecord originalOrder = new OrderStateRecord(
            123456L,
            "CLIENT-ORDER-001",
            "AAPL",
            (short) 1,
            (short) 2,
            1000L,
            150000L,
            987654L,
            OrderState.WORKING,
            250L,
            750L,
            3L
        );
        
        Map<Long, OrderStateRecord> orders = new HashMap<>();
        orders.put(originalOrder.internalOrderId(), originalOrder);
        
        TestSnapshotProvider provider = new TestSnapshotProvider(orders, 12345L);
        SnapshotMetadata metadata = snapshotManager.createSnapshot(provider);
        
        // Restore and verify
        SnapshotData restored = snapshotManager.restoreSnapshot(metadata.snapshotId());
        OrderStateRecord restoredOrder = restored.orders().get(0);
        
        assertEquals(originalOrder.internalOrderId(), restoredOrder.internalOrderId());
        assertEquals(originalOrder.clientOrderId(), restoredOrder.clientOrderId());
        assertEquals(originalOrder.symbol(), restoredOrder.symbol());
        assertEquals(originalOrder.side(), restoredOrder.side());
        assertEquals(originalOrder.orderType(), restoredOrder.orderType());
        assertEquals(originalOrder.quantity(), restoredOrder.quantity());
        assertEquals(originalOrder.price(), restoredOrder.price());
        assertEquals(originalOrder.account(), restoredOrder.account());
        assertEquals(originalOrder.currentState(), restoredOrder.currentState());
        assertEquals(originalOrder.filledQuantity(), restoredOrder.filledQuantity());
        assertEquals(originalOrder.remainingQuantity(), restoredOrder.remainingQuantity());
        assertEquals(originalOrder.version(), restoredOrder.version());
    }
    
    private Map<Long, OrderStateRecord> createTestOrders(int count) {
        Map<Long, OrderStateRecord> orders = new HashMap<>();
        
        for (int i = 1; i <= count; i++) {
            OrderStateRecord order = new OrderStateRecord(
                (long) i,
                "CLIENT-ORDER-" + i,
                "SYM" + (i % 100),
                (short) (i % 2 + 1), // side: 1 or 2
                (short) 2, // limit order
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
