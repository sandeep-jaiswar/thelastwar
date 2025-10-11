package com.thelastwar.wsgateway;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.gateway.GatewayAdapter;
import com.thelastwar.gateway.GatewayException;
import com.thelastwar.gateway.GatewayMetrics;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSocket Gateway implementation for real-time streaming interface.
 * 
 * Provides:
 * - Netty WebSocket Server with backpressure management
 * - Subscription model for order IDs and symbols
 * - Heartbeat/ping-pong mechanism for session liveness
 * - Event broadcasting from EventBus
 * - JSON and MessagePack transport options
 * 
 * Performance targets:
 * - Supports ≥ 250K concurrent clients on commodity server
 * - Broadcast latency ≤ 50 µs (tick-to-client)
 * - Proper backpressure handling (no dropped connections)
 * - Message consistency with FIX gateway
 */
public class WebSocketGateway implements GatewayAdapter {
    
    private static final Logger LOGGER = Logger.getLogger(WebSocketGateway.class.getName());
    private static final int DEFAULT_PORT = 8080;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int IDLE_TIMEOUT_SECONDS = 90;
    
    private final EventBus eventBus;
    private final WebSocketGatewayMetrics wsMetrics;
    private final GatewayMetrics gatewayMetrics;
    private final int port;
    private final TransportFormat defaultFormat;
    private final AtomicBoolean running;
    
    // Session management
    private final Map<String, WebSocketSession> sessions;
    
    // Netty components
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    // Heartbeat scheduler
    private ScheduledExecutorService heartbeatScheduler;
    
    // Serializers
    private final WebSocketMessageSerializer jsonSerializer;
    private final WebSocketMessageSerializer msgpackSerializer;
    
    /**
     * Creates a new WebSocketGateway.
     * 
     * @param eventBus EventBus for event broadcasting
     * @param port Port to listen on
     * @param defaultFormat Default transport format
     * @param gatewayMetrics Metrics collector
     */
    public WebSocketGateway(EventBus eventBus, int port, TransportFormat defaultFormat, 
                           GatewayMetrics gatewayMetrics) {
        if (eventBus == null) {
            throw new IllegalArgumentException("EventBus cannot be null");
        }
        if (gatewayMetrics == null) {
            throw new IllegalArgumentException("Metrics cannot be null");
        }
        
        this.eventBus = eventBus;
        this.port = port;
        this.defaultFormat = defaultFormat;
        this.gatewayMetrics = gatewayMetrics;
        this.wsMetrics = new WebSocketGatewayMetrics("websocket-gateway");
        this.running = new AtomicBoolean(false);
        this.sessions = new ConcurrentHashMap<>();
        this.jsonSerializer = new JsonWebSocketSerializer();
        this.msgpackSerializer = new MessagePackWebSocketSerializer();
    }
    
    /**
     * Creates a WebSocketGateway with default port and format.
     */
    public WebSocketGateway(EventBus eventBus, GatewayMetrics gatewayMetrics) {
        this(eventBus, DEFAULT_PORT, TransportFormat.JSON, gatewayMetrics);
    }
    
    /**
     * Creates a WebSocketGateway with default wsMetrics.
     */
    public WebSocketGateway(EventBus eventBus, int port, TransportFormat defaultFormat) {
        this(eventBus, port, defaultFormat, new GatewayMetrics("websocket-gateway"));
    }
    
    @Override
    public void start() throws GatewayException {
        if (running.getAndSet(true)) {
            throw new GatewayException("Gateway is already running");
        }
        
        try {
            // Initialize Netty event loop groups
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            
            // Subscribe to events from EventBus
            subscribeToEvents();
            
            // Start heartbeat scheduler
            startHeartbeatScheduler();
            
            // Bootstrap and start server
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new WebSocketServerInitializer(this, defaultFormat))
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, 
                            new WriteBufferWaterMark(32 * 1024, 64 * 1024));
            
            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            
            LOGGER.info("WebSocket Gateway started on port " + port + 
                       " with format " + defaultFormat);
        } catch (Exception e) {
            running.set(false);
            throw new GatewayException("Failed to start WebSocket Gateway", e);
        }
    }
    
    @Override
    public void stop() throws GatewayException {
        if (!running.getAndSet(false)) {
            return;
        }
        
        try {
            // Close all sessions
            for (WebSocketSession session : sessions.values()) {
                session.close();
            }
            sessions.clear();
            
            // Stop heartbeat scheduler
            if (heartbeatScheduler != null) {
                heartbeatScheduler.shutdown();
                heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS);
            }
            
            // Close server channel
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
            
            // Shutdown event loop groups
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().sync();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().sync();
            }
            
            LOGGER.info("WebSocket Gateway stopped");
        } catch (Exception e) {
            throw new GatewayException("Failed to stop WebSocket Gateway", e);
        }
    }
    
    @Override
    public boolean isRunning() {
        return running.get();
    }
    
    @Override
    public boolean sendMessage(Object message) {
        // Not applicable for WebSocket gateway
        // Messages are broadcast via event subscription
        return false;
    }
    
    @Override
    public EventBus getEventBus() {
        return eventBus;
    }
    
    @Override
    public SessionState getSessionState() {
        return running.get() ? SessionState.CONNECTED : SessionState.DISCONNECTED;
    }
    
    @Override
    public void close() throws Exception {
        stop();
    }
    
    /**
     * Gets the gateway wsMetrics.
     */
    @Override
    public GatewayMetrics getMetrics() {
        return gatewayMetrics;
    }
    
    /**
     * Gets the WebSocket-specific wsMetrics.
     */
    public WebSocketGatewayMetrics getWebSocketMetrics() {
        return wsMetrics;
    }
    
    /**
     * Registers a new WebSocket session.
     */
    public void registerSession(WebSocketSession session) {
        sessions.put(session.getSessionId(), session);
        wsMetrics.recordConnectionOpened();
        LOGGER.fine("Session registered: " + session.getSessionId());
    }
    
    /**
     * Unregisters a WebSocket session.
     */
    public void unregisterSession(WebSocketSession session) {
        sessions.remove(session.getSessionId());
        wsMetrics.recordConnectionClosed();
        LOGGER.fine("Session unregistered: " + session.getSessionId());
    }
    
    /**
     * Handles a message from a client.
     */
    public void handleClientMessage(WebSocketSession session, WebSocketMessage message) {
        wsMetrics.recordInboundMessage();
        
        switch (message.getType()) {
            case SUBSCRIBE_ORDER -> handleSubscribeOrder(session, message);
            case UNSUBSCRIBE_ORDER -> handleUnsubscribeOrder(session, message);
            case SUBSCRIBE_SYMBOL -> handleSubscribeSymbol(session, message);
            case UNSUBSCRIBE_SYMBOL -> handleUnsubscribeSymbol(session, message);
            case PING -> handlePing(session, message);
            case HEARTBEAT -> session.updateHeartbeat();
            default -> LOGGER.warning("Unhandled message type: " + message.getType());
        }
    }
    
    private void handleSubscribeOrder(WebSocketSession session, WebSocketMessage message) {
        if (message.getPayload() instanceof Number) {
            long orderId = ((Number) message.getPayload()).longValue();
            session.subscribeToOrder(orderId);
            wsMetrics.recordSubscription();
            LOGGER.fine("Session " + session.getSessionId() + " subscribed to order " + orderId);
        }
    }
    
    private void handleUnsubscribeOrder(WebSocketSession session, WebSocketMessage message) {
        if (message.getPayload() instanceof Number) {
            long orderId = ((Number) message.getPayload()).longValue();
            session.unsubscribeFromOrder(orderId);
            wsMetrics.recordUnsubscription();
        }
    }
    
    private void handleSubscribeSymbol(WebSocketSession session, WebSocketMessage message) {
        if (message.getPayload() instanceof String) {
            String symbol = (String) message.getPayload();
            session.subscribeToSymbol(symbol);
            wsMetrics.recordSubscription();
            LOGGER.fine("Session " + session.getSessionId() + " subscribed to symbol " + symbol);
        }
    }
    
    private void handleUnsubscribeSymbol(WebSocketSession session, WebSocketMessage message) {
        if (message.getPayload() instanceof String) {
            String symbol = (String) message.getPayload();
            session.unsubscribeFromSymbol(symbol);
            wsMetrics.recordUnsubscription();
        }
    }
    
    private void handlePing(WebSocketSession session, WebSocketMessage message) {
        session.updateHeartbeat();
        // Send pong response
        WebSocketMessage pong = WebSocketMessage.pong(
            message.getPayload() instanceof Number ? 
            ((Number) message.getPayload()).longValue() : System.nanoTime());
        sendToSession(session, pong);
    }
    
    /**
     * Subscribes to events from the EventBus.
     */
    private void subscribeToEvents() {
        // Subscribe to order events
        eventBus.subscribe(EventType.ORDER_ACCEPTED, event -> broadcastOrderEvent(event));
        eventBus.subscribe(EventType.ORDER_REJECTED, event -> broadcastOrderEvent(event));
        eventBus.subscribe(EventType.ORDER_FILLED, event -> broadcastOrderEvent(event));
        eventBus.subscribe(EventType.ORDER_PARTIALLY_FILLED, event -> broadcastOrderEvent(event));
        eventBus.subscribe(EventType.ORDER_CANCELLED, event -> broadcastOrderEvent(event));
        
        // Subscribe to market data events
        eventBus.subscribe(EventType.MARKET_DATA_UPDATE, event -> broadcastMarketDataEvent(event));
    }
    
    /**
     * Broadcasts an order event to subscribed clients.
     */
    private void broadcastOrderEvent(Event event) {
        var broadcastTimer = wsMetrics.startBroadcastTimer();
        
        try {
            Object payload = event.payload();
            if (payload instanceof OrderEvent orderEvent) {
                broadcastOrderEventToSubscribers(orderEvent);
            } else if (payload instanceof ExecutionEvent executionEvent) {
                broadcastExecutionEventToSubscribers(executionEvent);
            }
        } finally {
            wsMetrics.recordBroadcastLatency(broadcastTimer);
        }
    }
    
    /**
     * Broadcasts market data event to subscribed clients.
     */
    private void broadcastMarketDataEvent(Event event) {
        var broadcastTimer = wsMetrics.startBroadcastTimer();
        
        try {
            // Simplified: broadcast to all subscribed symbols
            // In production, would filter by symbol from event payload
            for (WebSocketSession session : sessions.values()) {
                if (!session.getSymbolSubscriptions().isEmpty()) {
                    sendEventToSession(session, event);
                }
            }
        } finally {
            wsMetrics.recordBroadcastLatency(broadcastTimer);
        }
    }
    
    private void broadcastOrderEventToSubscribers(OrderEvent orderEvent) {
        for (WebSocketSession session : sessions.values()) {
            if (session.isSubscribedToOrder(orderEvent.orderId()) || 
                session.isSubscribedToSymbol(orderEvent.symbol())) {
                sendOrderEventToSession(session, orderEvent);
            }
        }
    }
    
    private void broadcastExecutionEventToSubscribers(ExecutionEvent executionEvent) {
        for (WebSocketSession session : sessions.values()) {
            if (session.isSubscribedToOrder(executionEvent.orderId()) || 
                session.isSubscribedToSymbol(executionEvent.symbol())) {
                sendExecutionEventToSession(session, executionEvent);
            }
        }
    }
    
    private void sendOrderEventToSession(WebSocketSession session, OrderEvent orderEvent) {
        try {
            var serializationTimer = wsMetrics.startSerializationTimer();
            WebSocketMessageSerializer serializer = getSerializerForSession(session);
            byte[] data = serializer.serializeOrderEvent(orderEvent);
            wsMetrics.recordSerializationLatency(serializationTimer);
            
            WebSocketFrame frame = createFrameForSession(session, data);
            if (session.sendMessage(frame)) {
                wsMetrics.recordOutboundMessage();
            } else {
                wsMetrics.recordBackpressureEvent();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send order event", e);
            wsMetrics.recordSerializationError();
        }
    }
    
    private void sendExecutionEventToSession(WebSocketSession session, ExecutionEvent executionEvent) {
        try {
            var serializationTimer = wsMetrics.startSerializationTimer();
            WebSocketMessageSerializer serializer = getSerializerForSession(session);
            byte[] data = serializer.serializeExecutionEvent(executionEvent);
            wsMetrics.recordSerializationLatency(serializationTimer);
            
            WebSocketFrame frame = createFrameForSession(session, data);
            if (session.sendMessage(frame)) {
                wsMetrics.recordOutboundMessage();
            } else {
                wsMetrics.recordBackpressureEvent();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send execution event", e);
            wsMetrics.recordSerializationError();
        }
    }
    
    private void sendEventToSession(WebSocketSession session, Event event) {
        try {
            var serializationTimer = wsMetrics.startSerializationTimer();
            WebSocketMessageSerializer serializer = getSerializerForSession(session);
            byte[] data = serializer.serialize(event);
            wsMetrics.recordSerializationLatency(serializationTimer);
            
            WebSocketFrame frame = createFrameForSession(session, data);
            if (session.sendMessage(frame)) {
                wsMetrics.recordOutboundMessage();
            } else {
                wsMetrics.recordBackpressureEvent();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send event", e);
            wsMetrics.recordSerializationError();
        }
    }
    
    private void sendToSession(WebSocketSession session, WebSocketMessage message) {
        // Simplified implementation
        LOGGER.fine("Sending message to session: " + message.getType());
    }
    
    private WebSocketMessageSerializer getSerializerForSession(WebSocketSession session) {
        return session.getFormat() == TransportFormat.JSON ? jsonSerializer : msgpackSerializer;
    }
    
    private WebSocketFrame createFrameForSession(WebSocketSession session, byte[] data) {
        if (session.getFormat() == TransportFormat.JSON) {
            return new TextWebSocketFrame(new String(data));
        } else {
            return new BinaryWebSocketFrame(io.netty.buffer.Unpooled.wrappedBuffer(data));
        }
    }
    
    /**
     * Starts the heartbeat scheduler to monitor session liveness.
     */
    private void startHeartbeatScheduler() {
        heartbeatScheduler = Executors.newScheduledThreadPool(1);
        heartbeatScheduler.scheduleAtFixedRate(
            this::checkSessionHeartbeats,
            HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }
    
    /**
     * Checks all sessions for heartbeat timeout and closes stale connections.
     */
    private void checkSessionHeartbeats() {
        long now = System.nanoTime();
        long timeoutNanos = TimeUnit.SECONDS.toNanos(IDLE_TIMEOUT_SECONDS);
        
        for (WebSocketSession session : sessions.values()) {
            if (now - session.getLastHeartbeat() > timeoutNanos) {
                LOGGER.warning("Session timeout: " + session.getSessionId());
                session.close();
            }
        }
    }
    
    /**
     * Netty channel initializer for WebSocket connections.
     */
    private static class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {
        
        private final WebSocketGateway gateway;
        private final TransportFormat format;
        
        public WebSocketServerInitializer(WebSocketGateway gateway, TransportFormat format) {
            this.gateway = gateway;
            this.format = format;
        }
        
        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            
            // HTTP codec for WebSocket upgrade
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(65536));
            
            // WebSocket upgrade handler
            pipeline.addLast(new WebSocketServerProtocolHandler("/ws", null, true, 65536));
            
            // Idle state handler for heartbeat
            pipeline.addLast(new IdleStateHandler(IDLE_TIMEOUT_SECONDS, 0, 0));
            
            // Custom WebSocket handler
            pipeline.addLast(new WebSocketServerHandler(gateway, format));
        }
    }
}
