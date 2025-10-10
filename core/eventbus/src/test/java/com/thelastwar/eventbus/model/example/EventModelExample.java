package com.thelastwar.eventbus.model.example;

import com.thelastwar.eventbus.model.*;
import java.nio.ByteBuffer;

/**
 * Example usage of Order Event Model data structures.
 * 
 * Demonstrates:
 * - Creating immutable event objects
 * - Using factory methods
 * - Serialization/deserialization for low-latency transport
 * - Order lifecycle tracking
 */
public class EventModelExample {
    
    public static void main(String[] args) {
        System.out.println("=== Order Event Model Example ===\n");
        
        // Example 1: Creating a new order
        exampleNewOrder();
        
        // Example 2: Order execution flow
        exampleOrderExecutionFlow();
        
        // Example 3: Serialization/Deserialization
        exampleSerialization();
        
        // Example 4: Event immutability
        exampleImmutability();
    }
    
    private static void exampleNewOrder() {
        System.out.println("--- Example 1: Creating a New Order ---");
        
        OrderEvent order = OrderEvent.newOrder(
                12345L,              // orderId
                "AAPL",              // symbol
                OrderEvent.SIDE_BUY, // buy side
                OrderEvent.TYPE_LIMIT, // limit order
                100L,                // 100 shares
                15000L,              // $150.00 (in cents)
                999L,                // account ID
                1                    // exchange ID
        );
        
        System.out.println("Order created:");
        System.out.println("  Order ID: " + order.orderId());
        System.out.println("  Symbol: " + order.symbol());
        System.out.println("  Side: " + (order.isBuy() ? "BUY" : "SELL"));
        System.out.println("  Type: " + order.orderType());
        System.out.println("  Quantity: " + order.quantity());
        System.out.println("  Price: $" + (order.price() / 100.0));
        System.out.println("  Status: " + order.status());
        System.out.println();
    }
    
    private static void exampleOrderExecutionFlow() {
        System.out.println("--- Example 2: Order Execution Flow ---");
        
        // Step 1: Create order
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        System.out.println("1. Order submitted: " + order.orderId());
        
        // Step 2: Order accepted (new execution)
        ExecutionEvent newExec = ExecutionEvent.newOrder(11111L, order);
        System.out.println("2. Order accepted:");
        System.out.println("   Execution ID: " + newExec.executionId());
        System.out.println("   Order Status: " + newExec.orderStatus());
        System.out.println("   Leaves Qty: " + newExec.leavesQuantity());
        
        // Step 3: Partial fill
        ExecutionEvent partialFill = ExecutionEvent.fill(
                11112L,    // execution ID
                order,     // original order
                50L,       // filled 50 shares
                15050L,    // at $150.50
                50L,       // cumulative 50
                50L        // 50 shares remain
        );
        System.out.println("3. Partial fill:");
        System.out.println("   Filled Qty: " + partialFill.lastQuantity());
        System.out.println("   Fill Price: $" + (partialFill.lastPrice() / 100.0));
        System.out.println("   Cumulative: " + partialFill.cumulativeQty());
        System.out.println("   Leaves Qty: " + partialFill.leavesQuantity());
        
        // Step 4: Trade event from partial fill
        TradeEvent trade1 = TradeEvent.fromOrder(
                67890L,    // trade ID
                order,     // source order
                15050L,    // execution price
                25L        // $0.25 fees
        );
        System.out.println("4. Trade recorded:");
        System.out.println("   Trade ID: " + trade1.tradeId());
        System.out.println("   Trade Value: $" + (trade1.getTradeValue() / 100.0));
        System.out.println("   Net Proceeds: $" + (trade1.getNetProceeds() / 100.0));
        
        // Step 5: Complete fill
        ExecutionEvent completeFill = ExecutionEvent.fill(
                11113L,    // execution ID
                order,     // original order
                50L,       // filled remaining 50 shares
                15055L,    // at $150.55
                100L,      // cumulative 100 (all filled)
                0L         // no shares remain
        );
        System.out.println("5. Order completely filled:");
        System.out.println("   Status: " + (completeFill.isCompleteFill() ? "FILLED" : "PARTIAL"));
        System.out.println("   Terminal: " + completeFill.isTerminal());
        System.out.println();
    }
    
    private static void exampleSerialization() {
        System.out.println("--- Example 3: Serialization/Deserialization ---");
        
        OrderEvent order = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        // Allocate buffer
        ByteBuffer buffer = ByteBuffer.allocate(EventSerializer.getOrderEventSize());
        
        // Serialize
        long startSerialize = System.nanoTime();
        EventSerializer.serializeOrder(order, buffer);
        long serializeTime = System.nanoTime() - startSerialize;
        
        System.out.println("Serialization:");
        System.out.println("  Buffer size: " + EventSerializer.getOrderEventSize() + " bytes");
        System.out.println("  Time: " + serializeTime + " ns");
        System.out.println("  Target: < 200 ns");
        System.out.println("  Status: " + (serializeTime < 200 ? "✓ PASS" : "⚠ SLOW"));
        
        // Deserialize
        buffer.flip();
        long startDeserialize = System.nanoTime();
        OrderEvent deserialized = EventSerializer.deserializeOrder(buffer);
        long deserializeTime = System.nanoTime() - startDeserialize;
        
        System.out.println("Deserialization:");
        System.out.println("  Time: " + deserializeTime + " ns");
        System.out.println("  Target: < 200 ns");
        System.out.println("  Status: " + (deserializeTime < 200 ? "✓ PASS" : "⚠ SLOW"));
        
        // Verify
        System.out.println("Verification:");
        System.out.println("  Original Order ID: " + order.orderId());
        System.out.println("  Deserialized Order ID: " + deserialized.orderId());
        System.out.println("  Match: " + (order.orderId() == deserialized.orderId() ? "✓" : "✗"));
        System.out.println();
    }
    
    private static void exampleImmutability() {
        System.out.println("--- Example 4: Event Immutability ---");
        
        OrderEvent original = OrderEvent.newOrder(
                12345L, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
                100L, 15000L, 999L, 1
        );
        
        System.out.println("Original order status: " + original.status());
        
        // "Updating" creates a new instance
        OrderEvent updated = original.withStatus(OrderEvent.STATUS_FILLED);
        
        System.out.println("Updated order status: " + updated.status());
        System.out.println("Original order status (unchanged): " + original.status());
        System.out.println("Immutability preserved: " + (original.status() != updated.status() ? "✓" : "✗"));
        System.out.println();
    }
}
