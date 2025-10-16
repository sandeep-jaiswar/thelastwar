package com.thelastwar.oms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ParentOrder domain model.
 */
class ParentOrderTest {
    
    private InternalOrderId parentOrderId;
    private ClientOrderId clientOrderId;
    
    @BeforeEach
    void setUp() {
        parentOrderId = InternalOrderId.of(1000L);
        clientOrderId = ClientOrderId.of("PARENT-ORDER-001");
    }
    
    @Test
    void testCreateParentOrder() {
        ParentOrder order = new ParentOrder(
            parentOrderId,
            clientOrderId,
            "AAPL",
            (byte) 1,
            (byte) 2,
            500L,
            15000L,
            123L,
            System.nanoTime()
        );
        
        assertEquals(parentOrderId, order.getParentOrderId());
        assertEquals(clientOrderId, order.getClientOrderId());
        assertEquals("AAPL", order.getSymbol());
        assertEquals(500L, order.getTotalQuantity());
        assertEquals(0L, order.getFilledQuantity());
        assertEquals(500L, order.getRemainingQuantity());
        assertEquals(0, order.getChildOrderCount());
        assertFalse(order.isFullyFilled());
        assertFalse(order.isPartiallyFilled());
    }
    
    @Test
    void testAddChildOrder() {
        ParentOrder order = createTestParentOrder(500L);
        
        InternalOrderId child1 = InternalOrderId.of(2000L);
        InternalOrderId child2 = InternalOrderId.of(2001L);
        
        order.addChildOrder(child1);
        order.addChildOrder(child2);
        
        assertEquals(2, order.getChildOrderCount());
        assertTrue(order.getChildOrderIds().contains(child1));
        assertTrue(order.getChildOrderIds().contains(child2));
    }
    
    @Test
    void testUpdateFill() {
        ParentOrder order = createTestParentOrder(500L);
        
        order.updateFill(100L);
        
        assertEquals(100L, order.getFilledQuantity());
        assertEquals(400L, order.getRemainingQuantity());
        assertTrue(order.isPartiallyFilled());
        assertFalse(order.isFullyFilled());
        assertEquals(20.0, order.getFillPercentage(), 0.01);
    }
    
    @Test
    void testMultiplePartialFills() {
        ParentOrder order = createTestParentOrder(500L);
        
        order.updateFill(100L);
        order.updateFill(200L);
        order.updateFill(150L);
        
        assertEquals(450L, order.getFilledQuantity());
        assertEquals(50L, order.getRemainingQuantity());
        assertTrue(order.isPartiallyFilled());
        assertFalse(order.isFullyFilled());
        assertEquals(90.0, order.getFillPercentage(), 0.01);
    }
    
    @Test
    void testFullyFilled() {
        ParentOrder order = createTestParentOrder(500L);
        
        order.updateFill(500L);
        
        assertEquals(500L, order.getFilledQuantity());
        assertEquals(0L, order.getRemainingQuantity());
        assertFalse(order.isPartiallyFilled());
        assertTrue(order.isFullyFilled());
        assertEquals(100.0, order.getFillPercentage(), 0.01);
    }
    
    @Test
    void testOverfillThrowsException() {
        ParentOrder order = createTestParentOrder(500L);
        
        order.updateFill(300L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            order.updateFill(250L); // Would exceed remaining quantity
        });
    }
    
    @Test
    void testNegativeFillThrowsException() {
        ParentOrder order = createTestParentOrder(500L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            order.updateFill(-100L);
        });
    }
    
    @Test
    void testZeroFillThrowsException() {
        ParentOrder order = createTestParentOrder(500L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            order.updateFill(0L);
        });
    }
    
    @Test
    void testNullParentOrderIdThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new ParentOrder(
                null,
                clientOrderId,
                "AAPL",
                (byte) 1,
                (byte) 2,
                500L,
                15000L,
                123L,
                System.nanoTime()
            );
        });
    }
    
    @Test
    void testNullClientOrderIdThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new ParentOrder(
                parentOrderId,
                null,
                "AAPL",
                (byte) 1,
                (byte) 2,
                500L,
                15000L,
                123L,
                System.nanoTime()
            );
        });
    }
    
    @Test
    void testNullSymbolThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new ParentOrder(
                parentOrderId,
                clientOrderId,
                null,
                (byte) 1,
                (byte) 2,
                500L,
                15000L,
                123L,
                System.nanoTime()
            );
        });
    }
    
    @Test
    void testEmptySymbolThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ParentOrder(
                parentOrderId,
                clientOrderId,
                "",
                (byte) 1,
                (byte) 2,
                500L,
                15000L,
                123L,
                System.nanoTime()
            );
        });
    }
    
    @Test
    void testNegativeQuantityThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ParentOrder(
                parentOrderId,
                clientOrderId,
                "AAPL",
                (byte) 1,
                (byte) 2,
                -500L,
                15000L,
                123L,
                System.nanoTime()
            );
        });
    }
    
    @Test
    void testZeroQuantityThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ParentOrder(
                parentOrderId,
                clientOrderId,
                "AAPL",
                (byte) 1,
                (byte) 2,
                0L,
                15000L,
                123L,
                System.nanoTime()
            );
        });
    }
    
    @Test
    void testNegativePriceThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ParentOrder(
                parentOrderId,
                clientOrderId,
                "AAPL",
                (byte) 1,
                (byte) 2,
                500L,
                -15000L,
                123L,
                System.nanoTime()
            );
        });
    }
    
    @Test
    void testGetChildOrderIdsIsImmutable() {
        ParentOrder order = createTestParentOrder(500L);
        order.addChildOrder(InternalOrderId.of(2000L));
        
        assertThrows(UnsupportedOperationException.class, () -> {
            order.getChildOrderIds().add(InternalOrderId.of(2001L));
        });
    }
    
    @Test
    void testToString() {
        ParentOrder order = createTestParentOrder(500L);
        order.addChildOrder(InternalOrderId.of(2000L));
        order.updateFill(100L);
        
        String str = order.toString();
        assertTrue(str.contains("ParentOrder"));
        assertTrue(str.contains("AAPL"));
        assertTrue(str.contains("qty=500"));
        assertTrue(str.contains("filled=100"));
        assertTrue(str.contains("remaining=400"));
        assertTrue(str.contains("children=1"));
    }
    
    @Test
    void testEquals() {
        ParentOrder order1 = createTestParentOrder(500L);
        ParentOrder order2 = createTestParentOrder(500L);
        ParentOrder order3 = new ParentOrder(
            InternalOrderId.of(9999L),
            ClientOrderId.of("OTHER-ORDER"),
            "AAPL",
            (byte) 1,
            (byte) 2,
            500L,
            15000L,
            123L,
            System.nanoTime()
        );
        
        assertEquals(order1, order2);
        assertNotEquals(order1, order3);
        assertEquals(order1.hashCode(), order2.hashCode());
    }
    
    @Test
    void testConcurrentFillUpdates() throws InterruptedException {
        ParentOrder order = createTestParentOrder(1000L);
        
        // Simulate concurrent fills from multiple child orders
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                order.updateFill(10L);
            }
        });
        
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                order.updateFill(10L);
            }
        });
        
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        
        // Total should be 200 (10*10 + 10*10)
        assertEquals(200L, order.getFilledQuantity());
        assertEquals(800L, order.getRemainingQuantity());
    }
    
    private ParentOrder createTestParentOrder(long quantity) {
        return new ParentOrder(
            parentOrderId,
            clientOrderId,
            "AAPL",
            (byte) 1,
            (byte) 2,
            quantity,
            15000L,
            123L,
            System.nanoTime()
        );
    }
}
