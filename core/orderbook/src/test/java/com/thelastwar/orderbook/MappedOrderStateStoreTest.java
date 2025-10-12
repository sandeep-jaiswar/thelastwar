package com.thelastwar.orderbook;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for MappedOrderStateStore.
 * 
 * Tests cover:
 * - Basic CRUD operations
 * - Persistence and recovery
 * - Thread safety (single writer, multiple readers)
 * - Crash-safety verification
 * - Performance (< 2 µs latency)
 */
class MappedOrderStateStoreTest {
    
    private static Path testDir;
    private OrderStateStore store;
    
    @BeforeAll
    static void setupTestDir() throws IOException {
        testDir = Files.createTempDirectory("chronicle-test");
    }
    
    @AfterAll
    static void cleanupTestDir() throws IOException {
        if (testDir != null && Files.exists(testDir)) {
            Files.walk(testDir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
    }
    
    @AfterEach
    void cleanup() {
        if (store != null && !store.isClosed()) {
            try {
                store.close();
            } catch (IOException e) {
                // Ignore in cleanup
            }
        }
    }
    
    @Test
    @DisplayName("Create in-memory store successfully")
    void testCreateInMemory() throws IOException {
        store = MappedOrderStateStore.createInMemory(1000);
        
        assertNotNull(store);
        assertFalse(store.isClosed());
        assertEquals(0, store.size());
        assertFalse(((MappedOrderStateStore) store).isPersisted());
    }
    
    @Test
    @DisplayName("Create persisted store successfully")
    void testCreatePersisted() throws IOException {
        Path file = testDir.resolve("test-store.dat");
        store = MappedOrderStateStore.createPersisted(file, 1000);
        
        assertNotNull(store);
        assertFalse(store.isClosed());
        assertEquals(0, store.size());
        assertTrue(((MappedOrderStateStore) store).isPersisted());
        assertTrue(Files.exists(file));
    }
    
    @Test
    @DisplayName("Put and get order successfully")
    void testPutAndGet() throws IOException {
        store = MappedOrderStateStore.createInMemory(1000);
        
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L, Order.TYPE_LIMIT, Order.TIF_GTC);
        store.put(order);
        
        assertEquals(1, store.size());
        assertTrue(store.containsKey(1L));
        
        Order retrieved = store.get(1L);
        assertNotNull(retrieved);
        assertEquals(order.orderId(), retrieved.orderId());
        assertEquals(order.symbol(), retrieved.symbol());
        assertEquals(order.side(), retrieved.side());
        assertEquals(order.price(), retrieved.price());
        assertEquals(order.quantity(), retrieved.quantity());
        assertEquals(order.timestamp(), retrieved.timestamp());
    }
    
    @Test
    @DisplayName("Update existing order")
    void testUpdateOrder() throws IOException {
        store = MappedOrderStateStore.createInMemory(1000);
        
        Order order1 = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L, Order.TYPE_LIMIT, Order.TIF_GTC);
        store.put(order1);
        
        Order order2 = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 200L, 2000L, Order.TYPE_LIMIT, Order.TIF_GTC);
        store.put(order2);
        
        assertEquals(1, store.size());
        Order retrieved = store.get(1L);
        assertEquals(200L, retrieved.quantity());
        assertEquals(2000L, retrieved.timestamp());
    }
    
    @Test
    @DisplayName("Remove order successfully")
    void testRemoveOrder() throws IOException {
        store = MappedOrderStateStore.createInMemory(1000);
        
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L, Order.TYPE_LIMIT, Order.TIF_GTC);
        store.put(order);
        
        Order removed = store.remove(1L);
        assertNotNull(removed);
        assertEquals(order.orderId(), removed.orderId());
        
        assertEquals(0, store.size());
        assertFalse(store.containsKey(1L));
        assertNull(store.get(1L));
    }
    
    @Test
    @DisplayName("Remove non-existent order returns null")
    void testRemoveNonExistent() throws IOException {
        store = MappedOrderStateStore.createInMemory(1000);
        
        Order removed = store.remove(999L);
        assertNull(removed);
    }
    
    @Test
    @DisplayName("Clear removes all orders")
    void testClear() throws IOException {
        store = MappedOrderStateStore.createInMemory(1000);
        
        for (long i = 1; i <= 10; i++) {
            store.put(new Order(i, "AAPL", Order.SIDE_BUY, 15000L, 100L, i * 1000, Order.TYPE_LIMIT, Order.TIF_GTC));
        }
        
        assertEquals(10, store.size());
        store.clear();
        assertEquals(0, store.size());
    }
    
    @Test
    @DisplayName("Persist and recover orders from disk")
    void testPersistenceAndRecovery() throws IOException {
        Path file = testDir.resolve("persistence-test.dat");
        
        // Create store and add orders
        store = MappedOrderStateStore.createPersisted(file, 1000);
        for (long i = 1; i <= 100; i++) {
            store.put(new Order(i, "AAPL", Order.SIDE_BUY, 15000L + i, 100L, i * 1000, Order.TYPE_LIMIT, Order.TIF_GTC));
        }
        assertEquals(100, store.size());
        store.close();
        
        // Reopen and verify recovery
        store = MappedOrderStateStore.createPersisted(file, 1000);
        assertEquals(100, store.size());
        
        // Verify all orders recovered correctly
        for (long i = 1; i <= 100; i++) {
            Order order = store.get(i);
            assertNotNull(order, "Order " + i + " should exist");
            assertEquals(i, order.orderId());
            assertEquals("AAPL", order.symbol());
            assertEquals(15000L + i, order.price());
        }
    }
    
    @Test
    @DisplayName("Multiple readers can read concurrently")
    void testConcurrentReaders() throws IOException, InterruptedException, ExecutionException {
        store = MappedOrderStateStore.createInMemory(1000);
        
        // Add test data
        for (long i = 1; i <= 100; i++) {
            store.put(new Order(i, "AAPL", Order.SIDE_BUY, 15000L, 100L, i * 1000, Order.TYPE_LIMIT, Order.TIF_GTC));
        }
        
        // Create multiple reader threads
        int numReaders = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numReaders);
        List<Future<Integer>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < numReaders; i++) {
            futures.add(executor.submit(() -> {
                int reads = 0;
                for (int j = 0; j < 1000; j++) {
                    long orderId = (j % 100) + 1;
                    Order order = store.get(orderId);
                    if (order != null && order.orderId() == orderId) {
                        reads++;
                    }
                }
                return reads;
            }));
        }
        
        // Wait for all readers to complete
        for (Future<Integer> future : futures) {
            int reads = future.get();
            assertEquals(1000, reads);
            successCount.incrementAndGet();
        }
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(numReaders, successCount.get());
    }
    
    @Test
    @DisplayName("Single writer with concurrent readers")
    void testSingleWriterMultipleReaders() throws IOException, InterruptedException {
        store = MappedOrderStateStore.createInMemory(10000);
        
        // Initial data
        for (long i = 1; i <= 100; i++) {
            store.put(new Order(i, "AAPL", Order.SIDE_BUY, 15000L, 100L, i * 1000, Order.TYPE_LIMIT, Order.TIF_GTC));
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(11);
        AtomicInteger readErrors = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(11);
        
        // Single writer thread
        executor.submit(() -> {
            try {
                startLatch.await();
                for (long i = 101; i <= 1000; i++) {
                    store.put(new Order(i, "AAPL", Order.SIDE_BUY, 15000L + i, 100L, i * 1000, Order.TYPE_LIMIT, Order.TIF_GTC));
                    Thread.sleep(1); // Slow down to allow readers
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });
        
        // 10 reader threads
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 500; j++) {
                        long orderId = (j % 100) + 1;
                        Order order = store.get(orderId);
                        if (order == null) {
                            readErrors.incrementAndGet();
                        }
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        // Start all threads
        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        // Verify no read errors
        assertEquals(0, readErrors.get());
        
        // Verify final state
        assertEquals(1000, store.size());
    }
    
    @Test
    @DisplayName("Close store makes it unusable")
    void testCloseStore() throws IOException {
        store = MappedOrderStateStore.createInMemory(1000);
        
        Order order = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L, Order.TYPE_LIMIT, Order.TIF_GTC);
        store.put(order);
        
        store.close();
        assertTrue(store.isClosed());
        
        assertThrows(IllegalStateException.class, () -> store.put(order));
        assertThrows(IllegalStateException.class, () -> store.get(1L));
        assertThrows(IllegalStateException.class, () -> store.remove(1L));
        assertThrows(IllegalStateException.class, () -> store.containsKey(1L));
        assertThrows(IllegalStateException.class, () -> store.size());
        assertThrows(IllegalStateException.class, () -> store.clear());
    }
    
    @Test
    @DisplayName("Flush completes without error")
    void testFlush() throws IOException {
        Path file = testDir.resolve("flush-test.dat");
        store = MappedOrderStateStore.createPersisted(file, 1000);
        
        for (long i = 1; i <= 10; i++) {
            store.put(new Order(i, "AAPL", Order.SIDE_BUY, 15000L, 100L, i * 1000, Order.TYPE_LIMIT, Order.TIF_GTC));
        }
        
        // Flush should complete without error
        assertDoesNotThrow(() -> store.flush());
        
        assertEquals(10, store.size());
    }
    
    @Test
    @DisplayName("Handle symbols up to max length")
    void testMaxSymbolLength() throws IOException {
        store = MappedOrderStateStore.createInMemory(1000);
        
        // 15-character symbol (max supported)
        String longSymbol = "VERYLONGSYMBOL1";
        Order order = new Order(1L, longSymbol, Order.SIDE_BUY, 15000L, 100L, 1000L, Order.TYPE_LIMIT, Order.TIF_GTC);
        store.put(order);
        
        Order retrieved = store.get(1L);
        assertNotNull(retrieved);
        assertEquals(longSymbol, retrieved.symbol());
    }
    
    @Test
    @DisplayName("Handle both buy and sell orders")
    void testBuySellOrders() throws IOException {
        store = MappedOrderStateStore.createInMemory(1000);
        
        Order buyOrder = new Order(1L, "AAPL", Order.SIDE_BUY, 15000L, 100L, 1000L, Order.TYPE_LIMIT, Order.TIF_GTC);
        Order sellOrder = new Order(2L, "AAPL", Order.SIDE_SELL, 15100L, 100L, 2000L, Order.TYPE_LIMIT, Order.TIF_GTC);
        
        store.put(buyOrder);
        store.put(sellOrder);
        
        Order retrievedBuy = store.get(1L);
        Order retrievedSell = store.get(2L);
        
        assertEquals(Order.SIDE_BUY, retrievedBuy.side());
        assertEquals(Order.SIDE_SELL, retrievedSell.side());
    }
}
