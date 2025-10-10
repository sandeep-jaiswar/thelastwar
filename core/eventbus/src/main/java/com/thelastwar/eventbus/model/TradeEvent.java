package com.thelastwar.eventbus.model;

/**
 * Immutable Trade Event record representing a completed trade transaction.
 * Uses Java record for compact, efficient representation optimized for low-latency systems.
 * 
 * Performance characteristics:
 * - Compact memory layout via record
 * - All fields are primitives or immutable strings (no autoboxing in hot path)
 * - Binary serialization target: < 200 ns
 * - Suitable for deterministic replay
 * 
 * @param tradeId       Unique trade identifier
 * @param orderId       Associated order identifier
 * @param symbol        Trading symbol/instrument
 * @param side          Trade side: 1=Buy, 2=Sell
 * @param quantity      Trade quantity (executed)
 * @param price         Execution price (in minimum price increments)
 * @param timestamp     Trade timestamp in nanoseconds
 * @param account       Trading account identifier
 * @param exchange      Exchange identifier
 * @param counterparty  Counterparty identifier (0 if not applicable)
 * @param fees          Trade fees/commissions (in minimum currency units)
 */
public record TradeEvent(
        long tradeId,
        long orderId,
        String symbol,
        byte side,
        long quantity,
        long price,
        long timestamp,
        long account,
        int exchange,
        long counterparty,
        long fees) {
    
    // Trade Side constants (aligned with OrderEvent)
    public static final byte SIDE_BUY = 1;
    public static final byte SIDE_SELL = 2;
    
    /**
     * Compact constructor with validation.
     */
    public TradeEvent {
        if (tradeId <= 0) {
            throw new IllegalArgumentException("Trade ID must be positive");
        }
        if (orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (side != SIDE_BUY && side != SIDE_SELL) {
            throw new IllegalArgumentException("Invalid side: " + side);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative");
        }
        if (fees < 0) {
            throw new IllegalArgumentException("Fees cannot be negative");
        }
    }
    
    /**
     * Factory method for creating a trade event.
     * 
     * @param tradeId      Trade identifier
     * @param orderId      Associated order identifier
     * @param symbol       Trading symbol
     * @param side         Buy or Sell
     * @param quantity     Executed quantity
     * @param price        Execution price
     * @param account      Account identifier
     * @param exchange     Exchange identifier
     * @param counterparty Counterparty identifier
     * @param fees         Trade fees
     * @return new TradeEvent with current timestamp
     */
    public static TradeEvent create(long tradeId, long orderId, String symbol, byte side,
                                    long quantity, long price, long account, int exchange,
                                    long counterparty, long fees) {
        return new TradeEvent(tradeId, orderId, symbol, side, quantity, price,
                System.nanoTime(), account, exchange, counterparty, fees);
    }
    
    /**
     * Factory method for creating a trade from an order event.
     * 
     * @param tradeId  Trade identifier
     * @param order    Source order event
     * @param price    Execution price (may differ from order price for market orders)
     * @param fees     Trade fees
     * @return new TradeEvent
     */
    public static TradeEvent fromOrder(long tradeId, OrderEvent order, long price, long fees) {
        return new TradeEvent(tradeId, order.orderId(), order.symbol(), order.side(),
                order.quantity(), price, System.nanoTime(), order.account(),
                order.exchange(), 0L, fees);
    }
    
    /**
     * Checks if this is a buy trade.
     * 
     * @return true if buy trade
     */
    public boolean isBuy() {
        return side == SIDE_BUY;
    }
    
    /**
     * Checks if this is a sell trade.
     * 
     * @return true if sell trade
     */
    public boolean isSell() {
        return side == SIDE_SELL;
    }
    
    /**
     * Calculates the total trade value (quantity * price).
     * 
     * @return trade value
     */
    public long getTradeValue() {
        return quantity * price;
    }
    
    /**
     * Calculates net proceeds (value - fees).
     * 
     * @return net proceeds
     */
    public long getNetProceeds() {
        return getTradeValue() - fees;
    }
}
