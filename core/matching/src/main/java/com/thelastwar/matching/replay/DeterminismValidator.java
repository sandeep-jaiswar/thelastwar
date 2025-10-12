package com.thelastwar.matching.replay;

import com.thelastwar.matching.MatchingEngine;
import com.thelastwar.orderbook.LimitOrderBook;
import com.thelastwar.orderbook.Order;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Generates deterministic checksums for matching engine state.
 * 
 * Checksums are used to verify bit-for-bit identical state across replays.
 */
public class DeterminismValidator {
    
    private final MessageDigest digest;
    
    public DeterminismValidator() {
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    /**
     * Computes a checksum of the matching engine state.
     * 
     * @param engineSnapshot Matching engine snapshot
     * @return Hex string checksum
     */
    public String computeChecksum(MatchingEngine.MatchingEngineSnapshot engineSnapshot) {
        digest.reset();
        
        // Add counters to checksum
        addLong(engineSnapshot.executionIdCounter());
        addLong(engineSnapshot.tradeIdCounter());
        addLong(engineSnapshot.sequenceTracker());
        
        // Add all order book states in sorted order (for determinism)
        List<String> symbols = new ArrayList<>(engineSnapshot.bookSnapshots().keySet());
        Collections.sort(symbols);
        
        for (String symbol : symbols) {
            addString(symbol);
            LimitOrderBook.OrderBookSnapshot bookSnapshot = engineSnapshot.bookSnapshots().get(symbol);
            addOrderBookChecksum(bookSnapshot);
        }
        
        byte[] hash = digest.digest();
        return bytesToHex(hash);
    }
    
    /**
     * Computes a checksum of an order book snapshot.
     * 
     * @param snapshot Order book snapshot
     * @return Hex string checksum
     */
    public String computeOrderBookChecksum(LimitOrderBook.OrderBookSnapshot snapshot) {
        digest.reset();
        addOrderBookChecksum(snapshot);
        byte[] hash = digest.digest();
        return bytesToHex(hash);
    }
    
    private void addOrderBookChecksum(LimitOrderBook.OrderBookSnapshot snapshot) {
        addString(snapshot.symbol());
        
        // Sort orders by ID for determinism
        List<Order> orders = new ArrayList<>(snapshot.orders());
        orders.sort(Comparator.comparingLong(Order::orderId));
        
        for (Order order : orders) {
            addLong(order.orderId());
            addString(order.symbol());
            addByte(order.side());
            addByte(order.orderType());
            addLong(order.quantity());
            addLong(order.price());
            addLong(order.timestamp());
            addByte(order.timeInForce());
        }
    }
    
    private void addLong(long value) {
        digest.update((byte) (value >>> 56));
        digest.update((byte) (value >>> 48));
        digest.update((byte) (value >>> 40));
        digest.update((byte) (value >>> 32));
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }
    
    private void addInt(int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }
    
    private void addByte(byte value) {
        digest.update(value);
    }
    
    private void addString(String value) {
        if (value != null) {
            digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
