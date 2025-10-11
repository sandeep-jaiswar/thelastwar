package com.thelastwar.wsgateway;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a WebSocket client session with subscription tracking.
 * 
 * Thread-safe session management with:
 * - Subscription tracking (order IDs, symbols)
 * - Message queue with backpressure
 * - Heartbeat tracking
 * - Connection lifecycle management
 */
public class WebSocketSession {
    
    private final String sessionId;
    private final ChannelHandlerContext ctx;
    private final Set<Long> orderSubscriptions;
    private final Set<String> symbolSubscriptions;
    private final AtomicLong messagesSent;
    private final AtomicLong messagesReceived;
    private volatile long lastHeartbeat;
    private volatile boolean active;
    private final TransportFormat format;
    
    /**
     * Creates a new WebSocket session.
     * 
     * @param sessionId Unique session identifier
     * @param ctx Netty channel context
     * @param format Transport format (JSON or MessagePack)
     */
    public WebSocketSession(String sessionId, ChannelHandlerContext ctx, TransportFormat format) {
        this.sessionId = sessionId;
        this.ctx = ctx;
        this.format = format;
        this.orderSubscriptions = ConcurrentHashMap.newKeySet();
        this.symbolSubscriptions = ConcurrentHashMap.newKeySet();
        this.messagesSent = new AtomicLong(0);
        this.messagesReceived = new AtomicLong(0);
        this.lastHeartbeat = System.nanoTime();
        this.active = true;
    }
    
    /**
     * Gets the session ID.
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Gets the Netty channel context.
     */
    public ChannelHandlerContext getContext() {
        return ctx;
    }
    
    /**
     * Gets the transport format for this session.
     */
    public TransportFormat getFormat() {
        return format;
    }
    
    /**
     * Subscribes to order updates for a specific order ID.
     */
    public void subscribeToOrder(long orderId) {
        orderSubscriptions.add(orderId);
    }
    
    /**
     * Unsubscribes from order updates.
     */
    public void unsubscribeFromOrder(long orderId) {
        orderSubscriptions.remove(orderId);
    }
    
    /**
     * Checks if subscribed to a specific order.
     */
    public boolean isSubscribedToOrder(long orderId) {
        return orderSubscriptions.contains(orderId);
    }
    
    /**
     * Subscribes to market data for a symbol.
     */
    public void subscribeToSymbol(String symbol) {
        symbolSubscriptions.add(symbol);
    }
    
    /**
     * Unsubscribes from symbol updates.
     */
    public void unsubscribeFromSymbol(String symbol) {
        symbolSubscriptions.remove(symbol);
    }
    
    /**
     * Checks if subscribed to a specific symbol.
     */
    public boolean isSubscribedToSymbol(String symbol) {
        return symbolSubscriptions.contains(symbol);
    }
    
    /**
     * Gets all subscribed order IDs.
     */
    public Set<Long> getOrderSubscriptions() {
        return Set.copyOf(orderSubscriptions);
    }
    
    /**
     * Gets all subscribed symbols.
     */
    public Set<String> getSymbolSubscriptions() {
        return Set.copyOf(symbolSubscriptions);
    }
    
    /**
     * Sends a message to the client.
     * Applies backpressure if channel is not writable.
     * 
     * @param frame WebSocket frame to send
     * @return true if sent successfully, false if backpressure active
     */
    public boolean sendMessage(WebSocketFrame frame) {
        if (!active || !ctx.channel().isActive()) {
            return false;
        }
        
        // Check if channel is writable (backpressure)
        if (!ctx.channel().isWritable()) {
            return false;
        }
        
        ctx.writeAndFlush(frame);
        messagesSent.incrementAndGet();
        return true;
    }
    
    /**
     * Records a received message.
     */
    public void recordReceivedMessage() {
        messagesReceived.incrementAndGet();
    }
    
    /**
     * Updates the last heartbeat timestamp.
     */
    public void updateHeartbeat() {
        lastHeartbeat = System.nanoTime();
    }
    
    /**
     * Gets the last heartbeat timestamp in nanoseconds.
     */
    public long getLastHeartbeat() {
        return lastHeartbeat;
    }
    
    /**
     * Checks if session is still active.
     */
    public boolean isActive() {
        return active && ctx.channel().isActive();
    }
    
    /**
     * Closes the session.
     */
    public void close() {
        active = false;
        orderSubscriptions.clear();
        symbolSubscriptions.clear();
        if (ctx.channel().isActive()) {
            ctx.close();
        }
    }
    
    /**
     * Gets the number of messages sent.
     */
    public long getMessagesSent() {
        return messagesSent.get();
    }
    
    /**
     * Gets the number of messages received.
     */
    public long getMessagesReceived() {
        return messagesReceived.get();
    }
    
    /**
     * Gets the Netty channel.
     */
    public Channel getChannel() {
        return ctx.channel();
    }
}
