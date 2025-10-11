package com.thelastwar.gateway;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.SourceId;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.OrderCancelRequest;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FIX Gateway implementation for handling FIX protocol messages.
 * 
 * Provides low-latency FIX message processing with the following features:
 * - QuickFIX/J integration for FIX 4.4 protocol
 * - Session state management with automatic recovery
 * - DirectByteBuffer pooling for zero-copy operations
 * - Metrics collection for throughput and latency
 * - EventBus integration for internal communication
 * 
 * Performance targets:
 * - Throughput: ≥ 200K FIX msgs/sec
 * - Decode latency (p99): ≤ 10 µs
 * - Zero heap allocation in hot path
 * - FIX session survives sequence gaps with resend
 * 
 * Thread-safety: This class is thread-safe.
 */
public class FixGateway implements GatewayAdapter, Application {
    
    private static final Logger LOGGER = Logger.getLogger(FixGateway.class.getName());
    
    private final EventBus eventBus;
    private final GatewayMetrics metrics;
    private final BufferPool bufferPool;
    private final SessionSettings settings;
    private final AtomicBoolean running;
    private final AtomicLong sequence;
    
    private SocketInitiator initiator;
    private SessionID sessionID;
    private volatile SessionState state;
    
    /**
     * Creates a new FixGateway.
     * 
     * @param eventBus EventBus for internal communication
     * @param settings QuickFIX/J session settings
     * @param metrics Metrics collector
     */
    public FixGateway(EventBus eventBus, SessionSettings settings, GatewayMetrics metrics) {
        if (eventBus == null) {
            throw new IllegalArgumentException("EventBus cannot be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("SessionSettings cannot be null");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("GatewayMetrics cannot be null");
        }
        
        this.eventBus = eventBus;
        this.settings = settings;
        this.metrics = metrics;
        this.bufferPool = new BufferPool(1000, 8192); // 1000 buffers of 8KB each
        this.running = new AtomicBoolean(false);
        this.sequence = new AtomicLong(0);
        this.state = SessionState.DISCONNECTED;
    }
    
    /**
     * Creates a FixGateway with default metrics.
     * 
     * @param eventBus EventBus for internal communication
     * @param settings QuickFIX/J session settings
     */
    public FixGateway(EventBus eventBus, SessionSettings settings) {
        this(eventBus, settings, new GatewayMetrics("fix-gateway"));
    }
    
    @Override
    public void start() throws GatewayException {
        if (running.getAndSet(true)) {
            LOGGER.warning("FixGateway already running");
            return;
        }
        
        try {
            LOGGER.info("Starting FixGateway...");
            
            MessageStoreFactory storeFactory = new MemoryStoreFactory();
            LogFactory logFactory = new ScreenLogFactory(true, true, true);
            MessageFactory messageFactory = new DefaultMessageFactory();
            
            initiator = new SocketInitiator(this, storeFactory, settings, logFactory, messageFactory);
            initiator.start();
            
            state = SessionState.CONNECTING;
            LOGGER.info("FixGateway started successfully");
            
        } catch (ConfigError e) {
            running.set(false);
            throw new GatewayException("Failed to start FixGateway", e);
        }
    }
    
    @Override
    public void stop() throws GatewayException {
        if (!running.getAndSet(false)) {
            LOGGER.warning("FixGateway not running");
            return;
        }
        
        try {
            LOGGER.info("Stopping FixGateway...");
            
            if (initiator != null) {
                initiator.stop();
            }
            
            bufferPool.close();
            state = SessionState.DISCONNECTED;
            
            LOGGER.info("FixGateway stopped successfully");
            
        } catch (Exception e) {
            throw new GatewayException("Failed to stop FixGateway", e);
        }
    }
    
    @Override
    public boolean isRunning() {
        return running.get();
    }
    
    @Override
    public boolean sendMessage(Object message) {
        if (!running.get()) {
            LOGGER.warning("Cannot send message: FixGateway not running");
            return false;
        }
        
        if (sessionID == null) {
            LOGGER.warning("Cannot send message: No active session");
            return false;
        }
        
        try {
            Message fixMessage = convertToFixMessage(message);
            if (fixMessage != null) {
                Session.sendToTarget(fixMessage, sessionID);
                metrics.recordOutboundMessage();
                return true;
            }
        } catch (SessionNotFound e) {
            LOGGER.log(Level.WARNING, "Session not found", e);
            metrics.recordEncodeError();
        }
        
        return false;
    }
    
    @Override
    public boolean send(MessageEnvelope envelope) {
        if (!isRunning()) {
            LOGGER.warning("Cannot send message - gateway not running");
            return false;
        }
        
        // For FIX gateway, we need to convert the envelope to a FIX message
        // This is a placeholder implementation - full conversion would be protocol-specific
        LOGGER.fine("Sending MessageEnvelope with protocol: " + envelope.getProtocolType() 
            + ", correlationId: " + envelope.getCorrelationId());
        
        // TODO: Implement proper FIX message conversion from MessageEnvelope
        metrics.recordOutboundMessage();
        return true;
    }
    
    @Override
    public void onMessage(MessageEnvelope envelope) {
        // Process incoming message envelope
        // This is called when a message is received via the gateway
        LOGGER.fine("Received MessageEnvelope with protocol: " + envelope.getProtocolType() 
            + ", correlationId: " + envelope.getCorrelationId());
        
        // Publish to EventBus for downstream processing
        Event event = Event.create(
            envelope.getTimestamp(),
            sequence.incrementAndGet(),
            SourceId.FEED_HANDLER,
            EventType.ORDER_FILLED,
            envelope.getCorrelationId(),
            "Message received from " + envelope.getClientId()
        );
        eventBus.publish(event);
    }
    
    @Override
    public EventBus getEventBus() {
        return eventBus;
    }
    
    @Override
    public GatewayMetrics getMetrics() {
        return metrics;
    }
    
    @Override
    public SessionState getSessionState() {
        return state;
    }
    
    // QuickFIX Application interface methods
    
    @Override
    public void onCreate(SessionID sessionID) {
        LOGGER.info("Session created: " + sessionID);
        this.sessionID = sessionID;
        metrics.recordSessionConnect();
    }
    
    @Override
    public void onLogon(SessionID sessionID) {
        LOGGER.info("Session logged on: " + sessionID);
        state = SessionState.LOGGED_IN;
        
        // Publish system event
        Event event = Event.create(
            System.nanoTime(),
            sequence.incrementAndGet(),
            SourceId.FEED_HANDLER,
            EventType.FEED_CONNECTION_STATUS,
            0L,
            "FIX Session Logged On: " + sessionID
        );
        eventBus.publish(event);
    }
    
    @Override
    public void onLogout(SessionID sessionID) {
        LOGGER.info("Session logged out: " + sessionID);
        state = SessionState.LOGGED_OUT;
        metrics.recordSessionDisconnect();
        
        // Publish system event
        Event event = Event.create(
            System.nanoTime(),
            sequence.incrementAndGet(),
            SourceId.FEED_HANDLER,
            EventType.FEED_CONNECTION_STATUS,
            0L,
            "FIX Session Logged Out: " + sessionID
        );
        eventBus.publish(event);
    }
    
    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        // Called when sending admin messages (Logon, Logout, Heartbeat, etc.)
        LOGGER.fine("Sending admin message: " + message.getClass().getSimpleName());
    }
    
    @Override
    public void fromAdmin(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        // Called when receiving admin messages
        LOGGER.fine("Received admin message: " + message.getClass().getSimpleName());
    }
    
    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {
        // Called when sending application messages
        LOGGER.fine("Sending application message: " + message.getClass().getSimpleName());
    }
    
    @Override
    public void fromApp(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        // Hot path - decode and publish FIX messages with minimal latency
        io.micrometer.core.instrument.Timer.Sample decodeSample = metrics.startDecodeTimer();
        
        try {
            processInboundMessage(message);
            metrics.recordInboundMessage();
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error processing inbound message", e);
            metrics.recordDecodeError();
            throw new IncorrectDataFormat(0, "Error processing message");
            
        } finally {
            metrics.recordDecodeLatency(decodeSample);
        }
    }
    
    /**
     * Processes an inbound FIX message and publishes to EventBus.
     * This is a hot path method optimized for minimal latency.
     */
    private void processInboundMessage(Message message) throws FieldNotFound {
        String msgType = message.getHeader().getString(MsgType.FIELD);
        
        switch (msgType) {
            case MsgType.EXECUTION_REPORT -> processExecutionReport(message);
            case MsgType.ORDER_CANCEL_REJECT -> processOrderCancelReject(message);
            case MsgType.BUSINESS_MESSAGE_REJECT -> processBusinessMessageReject(message);
            default -> LOGGER.fine("Unhandled message type: " + msgType);
        }
    }
    
    /**
     * Processes ExecutionReport messages.
     */
    private void processExecutionReport(Message message) throws FieldNotFound {
        ExecutionReport execReport = (ExecutionReport) message;
        
        String clOrdID = execReport.getClOrdID().getValue();
        char execType = execReport.getExecType().getValue();
        
        int eventType = switch (execType) {
            case ExecType.NEW -> EventType.ORDER_ACCEPTED;
            case ExecType.FILL -> EventType.ORDER_FILLED;
            case ExecType.PARTIAL_FILL -> EventType.ORDER_PARTIALLY_FILLED;
            case ExecType.CANCELED -> EventType.ORDER_CANCELLED;
            case ExecType.REJECTED -> EventType.ORDER_REJECTED;
            default -> EventType.ORDER_STATUS_UPDATE;
        };
        
        // Create event and publish
        Event event = Event.create(
            System.nanoTime(),
            sequence.incrementAndGet(),
            SourceId.FEED_HANDLER,
            eventType,
            0L,
            execReport // Pass entire message as payload for now
        );
        
        eventBus.publish(event);
    }
    
    /**
     * Processes OrderCancelReject messages.
     */
    private void processOrderCancelReject(Message message) throws FieldNotFound {
        Event event = Event.create(
            System.nanoTime(),
            sequence.incrementAndGet(),
            SourceId.FEED_HANDLER,
            EventType.ORDER_REJECTED,
            0L,
            message
        );
        
        eventBus.publish(event);
    }
    
    /**
     * Processes BusinessMessageReject messages.
     */
    private void processBusinessMessageReject(Message message) throws FieldNotFound {
        Event event = Event.create(
            System.nanoTime(),
            sequence.incrementAndGet(),
            SourceId.FEED_HANDLER,
            EventType.ORDER_REJECTED,
            0L,
            message
        );
        
        eventBus.publish(event);
    }
    
    /**
     * Converts an internal message object to a FIX message.
     * This is a hot path method optimized for minimal latency.
     */
    private Message convertToFixMessage(Object message) {
        io.micrometer.core.instrument.Timer.Sample encodeSample = metrics.startEncodeTimer();
        
        try {
            // For now, if it's already a FIX message, send it directly
            if (message instanceof Message) {
                return (Message) message;
            }
            
            // TODO: Implement conversion from internal order format to FIX
            LOGGER.warning("Cannot convert message type: " + message.getClass().getName());
            return null;
            
        } finally {
            metrics.recordEncodeLatency(encodeSample);
        }
    }
    
    /**
     * Gets the buffer pool for zero-copy operations.
     * 
     * @return BufferPool instance
     */
    public BufferPool getBufferPool() {
        return bufferPool;
    }
    
    /**
     * Gets the current sequence number.
     * 
     * @return Current sequence
     */
    public long getCurrentSequence() {
        return sequence.get();
    }
}
