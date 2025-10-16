package com.thelastwar.oms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ChildOrder domain model and RoutingDecision.
 */
class ChildOrderTest {
    
    private InternalOrderId parentOrderId;
    private InternalOrderId childOrderId;
    private ParentOrder parentOrder;
    
    @BeforeEach
    void setUp() {
        parentOrderId = InternalOrderId.of(1000L);
        childOrderId = InternalOrderId.of(2000L);
        parentOrder = new ParentOrder(
            parentOrderId,
            ClientOrderId.of("PARENT-ORDER-001"),
            "AAPL",
            (byte) 1,
            (byte) 2,
            500L,
            15000L,
            123L,
            System.nanoTime()
        );
    }
    
    @Test
    void testCreateChildOrder() {
        ChildOrder child = new ChildOrder(
            childOrderId,
            parentOrderId,
            "AAPL",
            (byte) 1,
            (byte) 2,
            200L,
            15000L,
            "NYSE",
            "VWAP",
            System.nanoTime()
        );
        
        assertEquals(childOrderId, child.childOrderId());
        assertEquals(parentOrderId, child.parentOrderId());
        assertEquals("AAPL", child.symbol());
        assertEquals(200L, child.quantity());
        assertEquals("NYSE", child.venue());
        assertEquals("VWAP", child.routingStrategy());
    }
    
    @Test
    void testFromParent() {
        ChildOrder child = ChildOrder.fromParent(
            childOrderId,
            parentOrder,
            200L,
            "NYSE",
            "VWAP"
        );
        
        assertEquals(childOrderId, child.childOrderId());
        assertEquals(parentOrderId, child.parentOrderId());
        assertEquals("AAPL", child.symbol());
        assertEquals((byte) 1, child.side());
        assertEquals((byte) 2, child.orderType());
        assertEquals(200L, child.quantity());
        assertEquals(15000L, child.price());
        assertEquals("NYSE", child.venue());
        assertEquals("VWAP", child.routingStrategy());
    }
    
    @Test
    void testFromParentWithPrice() {
        ChildOrder child = ChildOrder.fromParentWithPrice(
            childOrderId,
            parentOrder,
            200L,
            15100L, // Different price
            "NASDAQ",
            "TWAP"
        );
        
        assertEquals(15100L, child.price()); // Custom price
        assertEquals(parentOrderId, child.parentOrderId());
    }
    
    @Test
    void testToDisplayString() {
        ChildOrder child = ChildOrder.fromParent(
            childOrderId,
            parentOrder,
            200L,
            "NYSE",
            "VWAP"
        );
        
        String str = child.toDisplayString();
        assertTrue(str.contains("ChildOrder"));
        assertTrue(str.contains("AAPL"));
        assertTrue(str.contains("qty=200"));
        assertTrue(str.contains("NYSE"));
        assertTrue(str.contains("VWAP"));
    }
    
    @Test
    void testNullChildOrderIdThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new ChildOrder(
                null,
                parentOrderId,
                "AAPL",
                (byte) 1,
                (byte) 2,
                200L,
                15000L,
                "NYSE",
                "VWAP",
                System.nanoTime()
            );
        });
    }
    
    @Test
    void testNullParentOrderIdThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new ChildOrder(
                childOrderId,
                null,
                "AAPL",
                (byte) 1,
                (byte) 2,
                200L,
                15000L,
                "NYSE",
                "VWAP",
                System.nanoTime()
            );
        });
    }
    
    @Test
    void testEmptySymbolThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ChildOrder(
                childOrderId,
                parentOrderId,
                "",
                (byte) 1,
                (byte) 2,
                200L,
                15000L,
                "NYSE",
                "VWAP",
                System.nanoTime()
            );
        });
    }
    
    @Test
    void testZeroQuantityThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ChildOrder(
                childOrderId,
                parentOrderId,
                "AAPL",
                (byte) 1,
                (byte) 2,
                0L,
                15000L,
                "NYSE",
                "VWAP",
                System.nanoTime()
            );
        });
    }
    
    @Test
    void testNegativePriceThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ChildOrder(
                childOrderId,
                parentOrderId,
                "AAPL",
                (byte) 1,
                (byte) 2,
                200L,
                -15000L,
                "NYSE",
                "VWAP",
                System.nanoTime()
            );
        });
    }
    
    @Test
    void testEmptyVenueThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ChildOrder(
                childOrderId,
                parentOrderId,
                "AAPL",
                (byte) 1,
                (byte) 2,
                200L,
                15000L,
                "",
                "VWAP",
                System.nanoTime()
            );
        });
    }
}

/**
 * Test suite for RoutingDecision.
 */
class RoutingDecisionTest {
    
    private InternalOrderId parentOrderId;
    
    @BeforeEach
    void setUp() {
        parentOrderId = InternalOrderId.of(1000L);
    }
    
    @Test
    void testCreateRoutingDecision() {
        List<RoutingDecision.RoutingInstruction> instructions = List.of(
            RoutingDecision.RoutingInstruction.create("NYSE", 300L, 15000L, 1),
            RoutingDecision.RoutingInstruction.create("NASDAQ", 200L, 15000L, 2)
        );
        
        RoutingDecision decision = RoutingDecision.create(
            parentOrderId,
            instructions,
            "SPLIT_STRATEGY",
            "Test routing"
        );
        
        assertEquals(parentOrderId, decision.parentOrderId());
        assertEquals(2, decision.getChildOrderCount());
        assertEquals("SPLIT_STRATEGY", decision.strategyName());
    }
    
    @Test
    void testValidateQuantitiesSuccess() {
        List<RoutingDecision.RoutingInstruction> instructions = List.of(
            RoutingDecision.RoutingInstruction.create("NYSE", 300L, 15000L, 1),
            RoutingDecision.RoutingInstruction.create("NASDAQ", 200L, 15000L, 2)
        );
        
        RoutingDecision decision = RoutingDecision.create(
            parentOrderId,
            instructions,
            "SPLIT_STRATEGY",
            "Test routing"
        );
        
        // Should not throw
        decision.validateQuantities(500L);
    }
    
    @Test
    void testValidateQuantitiesFailure() {
        List<RoutingDecision.RoutingInstruction> instructions = List.of(
            RoutingDecision.RoutingInstruction.create("NYSE", 300L, 15000L, 1),
            RoutingDecision.RoutingInstruction.create("NASDAQ", 200L, 15000L, 2)
        );
        
        RoutingDecision decision = RoutingDecision.create(
            parentOrderId,
            instructions,
            "SPLIT_STRATEGY",
            "Test routing"
        );
        
        // Should throw - quantities don't match
        assertThrows(IllegalStateException.class, () -> {
            decision.validateQuantities(600L);
        });
    }
    
    @Test
    void testRoutingInstructionsAreImmutable() {
        List<RoutingDecision.RoutingInstruction> instructions = List.of(
            RoutingDecision.RoutingInstruction.create("NYSE", 300L, 15000L, 1)
        );
        
        RoutingDecision decision = RoutingDecision.create(
            parentOrderId,
            instructions,
            "SPLIT_STRATEGY",
            "Test routing"
        );
        
        assertThrows(UnsupportedOperationException.class, () -> {
            decision.routingInstructions().add(
                RoutingDecision.RoutingInstruction.create("NASDAQ", 200L, 15000L, 2)
            );
        });
    }
    
    @Test
    void testEmptyRoutingInstructionsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            RoutingDecision.create(
                parentOrderId,
                List.of(),
                "SPLIT_STRATEGY",
                "Test routing"
            );
        });
    }
    
    @Test
    void testRoutingInstructionValidation() {
        RoutingDecision.RoutingInstruction instruction = 
            RoutingDecision.RoutingInstruction.create("NYSE", 300L, 15000L, 1);
        
        assertEquals("NYSE", instruction.venue());
        assertEquals(300L, instruction.quantity());
        assertEquals(15000L, instruction.price());
        assertEquals(1, instruction.priority());
        assertEquals(5000L, instruction.timeoutMillis());
    }
    
    @Test
    void testRoutingInstructionWithCustomTimeout() {
        RoutingDecision.RoutingInstruction instruction = 
            new RoutingDecision.RoutingInstruction("NYSE", 300L, 15000L, 1, 10000L);
        
        assertEquals(10000L, instruction.timeoutMillis());
    }
    
    @Test
    void testRoutingInstructionInvalidVenue() {
        assertThrows(IllegalArgumentException.class, () -> {
            RoutingDecision.RoutingInstruction.create("", 300L, 15000L, 1);
        });
    }
    
    @Test
    void testRoutingInstructionInvalidQuantity() {
        assertThrows(IllegalArgumentException.class, () -> {
            RoutingDecision.RoutingInstruction.create("NYSE", 0L, 15000L, 1);
        });
    }
    
    @Test
    void testRoutingInstructionInvalidPrice() {
        assertThrows(IllegalArgumentException.class, () -> {
            RoutingDecision.RoutingInstruction.create("NYSE", 300L, -15000L, 1);
        });
    }
    
    @Test
    void testRoutingInstructionInvalidPriority() {
        assertThrows(IllegalArgumentException.class, () -> {
            RoutingDecision.RoutingInstruction.create("NYSE", 300L, 15000L, -1);
        });
    }
}
