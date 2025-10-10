package com.thelastwar.risk;

/**
 * Standard rejection reason codes for pre-trade risk validation.
 * Using integer constants to avoid autoboxing and improve performance.
 */
public final class RiskReasonCode {
    
    // Credit risk (100-199)
    public static final int INSUFFICIENT_CREDIT = 100;
    public static final int CREDIT_LIMIT_EXCEEDED = 101;
    public static final int ACCOUNT_SUSPENDED = 102;
    
    // Margin risk (200-299)
    public static final int INSUFFICIENT_MARGIN = 200;
    public static final int MARGIN_CALL_OUTSTANDING = 201;
    public static final int POSITION_LIMIT_EXCEEDED = 202;
    
    // Fat-finger checks (300-399)
    public static final int QUANTITY_TOO_LARGE = 300;
    public static final int PRICE_OUT_OF_RANGE = 301;
    public static final int NOTIONAL_VALUE_EXCEEDED = 302;
    public static final int DUPLICATE_ORDER = 303;
    
    // System/configuration (400-499)
    public static final int SYMBOL_NOT_TRADABLE = 400;
    public static final int MARKET_CLOSED = 401;
    public static final int TRADING_HALTED = 402;
    
    private RiskReasonCode() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Gets a human-readable description for a reason code.
     * 
     * @param reasonCode The reason code
     * @return Human-readable description
     */
    public static String getDescription(int reasonCode) {
        return switch (reasonCode) {
            case INSUFFICIENT_CREDIT -> "Insufficient credit";
            case CREDIT_LIMIT_EXCEEDED -> "Credit limit exceeded";
            case ACCOUNT_SUSPENDED -> "Account suspended";
            case INSUFFICIENT_MARGIN -> "Insufficient margin";
            case MARGIN_CALL_OUTSTANDING -> "Margin call outstanding";
            case POSITION_LIMIT_EXCEEDED -> "Position limit exceeded";
            case QUANTITY_TOO_LARGE -> "Quantity too large";
            case PRICE_OUT_OF_RANGE -> "Price out of range";
            case NOTIONAL_VALUE_EXCEEDED -> "Notional value exceeded";
            case DUPLICATE_ORDER -> "Duplicate order detected";
            case SYMBOL_NOT_TRADABLE -> "Symbol not tradable";
            case MARKET_CLOSED -> "Market closed";
            case TRADING_HALTED -> "Trading halted";
            default -> "Unknown reason code: " + reasonCode;
        };
    }
}
