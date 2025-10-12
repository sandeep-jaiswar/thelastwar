package com.thelastwar.gateway.integration;

import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;

/**
 * Factory for creating FIX messages in tests.
 */
final class FixMessageFactory {
    
    private FixMessageFactory() {}
    
    static NewOrderSingle createTestOrder(long orderId) {
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
            throw new IllegalStateException("Failed to create test order", e);
        }
        
        return order;
    }
}
