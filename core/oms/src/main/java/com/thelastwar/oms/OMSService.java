package com.thelastwar.oms;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.EventBus;
import com.thelastwar.eventbus.EventType;
import com.thelastwar.eventbus.SourceId;
import com.thelastwar.eventbus.model.OrderEvent;
import com.thelastwar.risk.CompositeRiskValidator;
import com.thelastwar.risk.CreditCheckModule;
import com.thelastwar.risk.FatFingerCheckModule;
import com.thelastwar.risk.MarginCheckModule;
import com.thelastwar.risk.RiskValidator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OMSService is the core Order Management System service that handles order lifecycle operations.
 * 
 * Responsibilities:
 * - Validates incoming order requests
 * - Persists commands to write-ahead log
 * - Publishes canonical order events to EventBus
 * - Maintains idempotency using ClientOrderId
 * - Manages order state machines
 * 
 * Performance targets:
 * - Order submission: < 1ms from API to EventBus publish
 * - Idempotency check: < 1 µs
 * - Command log append: < 100 µs
 * 
 * Thread-safety: This class is thread-safe for concurrent order operations.
 */
public class OMSService implements AutoCloseable {
    
    private final EventBus eventBus;
    private final CommandLog commandLog;
    private final RiskCheckService riskCheckService;
    private final AtomicLong internalOrderIdGenerator;
    private final AtomicLong commandIdGenerator;
    
    // Idempotency index: ClientOrderId -> InternalOrderId
    private final Map<ClientOrderId, InternalOrderId> clientOrderIndex;
    
    // Order state machines: InternalOrderId -> OrderStateMachine
    private final Map<InternalOrderId, OrderStateMachine> orderStateMachines;
    
    // Correlation tracking for responses
    private final Map<InternalOrderId, String> orderCorrelationIds;
    
    public OMSService(EventBus eventBus, Path commandLogPath) throws IOException {
        this(eventBus, commandLogPath, createDefaultRiskCheckService());
    }
    
    public OMSService(EventBus eventBus, Path commandLogPath, RiskCheckService riskCheckService) throws IOException {
        this.eventBus = eventBus;
        this.commandLog = new FileBasedCommandLog(commandLogPath);
        this.riskCheckService = riskCheckService;
        this.internalOrderIdGenerator = new AtomicLong(1);
        this.commandIdGenerator = new AtomicLong(1);
        this.clientOrderIndex = new ConcurrentHashMap<>();
        this.orderStateMachines = new ConcurrentHashMap<>();
        this.orderCorrelationIds = new ConcurrentHashMap<>();
        
        // Restore state from command log
        restoreFromCommandLog();
    }
    
    /**
     * Creates default risk check service with composite validator.
     */
    private static RiskCheckService createDefaultRiskCheckService() {
        RiskValidator validator = new CompositeRiskValidator.Builder()
            .add(new CreditCheckModule(10_000_000L, true))
            .add(new MarginCheckModule(100_000L, true))
            .add(new FatFingerCheckModule(100_000L, 1_000_000L, 100L, 100_000_000L, true))
            .build();
        return new RiskCheckService(validator, RiskCheckService.RiskCheckConfig.failClosed());
    }
    
    /**
     * Restores OMS state from the command log on startup.
     */
    private void restoreFromCommandLog() throws IOException {
        long latestCommandId = commandLog.getLatestCommandId();
        if (latestCommandId >= 0) {
            commandIdGenerator.set(latestCommandId + 1);
            
            // Replay commands to rebuild state
            commandLog.replay(0, latestCommandId, this::replayCommand);
        }
    }
    
    private void replayCommand(OrderCommand command) {
        try {
            switch (command.commandType()) {
                case SUBMIT -> replaySubmitCommand(command);
                case CANCEL -> replayCancelCommand(command);
                case MODIFY -> replayModifyCommand(command);
            }
        } catch (Exception e) {
            // Log error but continue replay
            System.err.println("Error replaying command " + command.commandId() + ": " + e.getMessage());
        }
    }
    
    private void replaySubmitCommand(OrderCommand command) {
        ClientOrderId clientOrderId = ClientOrderId.of(command.clientOrderId());
        
        // Check idempotency
        if (clientOrderIndex.containsKey(clientOrderId)) {
            return; // Already processed
        }
        
        // Generate or restore internal order ID
        InternalOrderId internalOrderId = InternalOrderId.of(internalOrderIdGenerator.getAndIncrement());
        
        // Register in idempotency index
        clientOrderIndex.put(clientOrderId, internalOrderId);
        
        // Create state machine
        OrderStateMachine stateMachine = new OrderStateMachine(internalOrderId);
        orderStateMachines.put(internalOrderId, stateMachine);
    }
    
    private void replayCancelCommand(OrderCommand command) {
        if (command.internalOrderId() != null) {
            InternalOrderId internalOrderId = InternalOrderId.of(command.internalOrderId());
            OrderStateMachine stateMachine = orderStateMachines.get(internalOrderId);
            if (stateMachine != null && stateMachine.isCancellable()) {
                try {
                    stateMachine.transition(OrderState.CANCELLED);
                } catch (Exception e) {
                    // Ignore invalid transitions during replay
                }
            }
        }
    }
    
    private void replayModifyCommand(OrderCommand command) {
        // Modification handling can be added here if needed
        // For now, we'll keep it simple
    }
    
    /**
     * Submits a new order.
     * 
     * @param clientOrderId Client-assigned unique order ID
     * @param symbol        Trading symbol
     * @param side          Order side (1=Buy, 2=Sell)
     * @param orderType     Order type (1=Market, 2=Limit, etc.)
     * @param quantity      Order quantity
     * @param price         Order price
     * @param account       Account identifier
     * @param correlationId Correlation ID for tracking
     * @return SubmissionResult with order ID and status
     */
    public SubmissionResult submitOrder(String clientOrderId, String symbol, byte side, 
                                       byte orderType, long quantity, long price, 
                                       long account, String correlationId) {
        long submissionStartNanos = System.nanoTime();
        
        try {
            // Validate clientOrderId
            ClientOrderId coid = ClientOrderId.of(clientOrderId);
            
            // Check idempotency
            InternalOrderId existingOrderId = clientOrderIndex.get(coid);
            if (existingOrderId != null) {
                return SubmissionResult.alreadyExists(existingOrderId.value(), correlationId);
            }
            
            // Generate IDs
            long commandId = commandIdGenerator.getAndIncrement();
            InternalOrderId internalOrderId = InternalOrderId.of(internalOrderIdGenerator.getAndIncrement());
            
            // Create order event for risk check
            OrderEvent orderEvent = OrderEvent.newOrder(
                internalOrderId.value(), symbol, side, orderType, quantity, price, account, 1
            );
            
            // *** PRE-TRADE RISK CHECK (SYNCHRONOUS) ***
            RiskCheckService.RiskCheckResult riskResult = riskCheckService.check(orderEvent);
            
            // If risk check rejects the order, return rejection immediately
            if (!riskResult.approved()) {
                // Log risk rejection for audit
                logRiskDecision(internalOrderId, orderEvent, riskResult, false);
                
                return SubmissionResult.riskRejection(
                    internalOrderId.value(),
                    riskResult.decision().message(),
                    riskResult.decision().reasonCode(),
                    correlationId,
                    riskResult.latencyNanos()
                );
            }
            
            // Log risk approval for audit
            logRiskDecision(internalOrderId, orderEvent, riskResult, true);
            
            // Create command
            OrderCommand command = OrderCommand.submit(
                commandId, clientOrderId, symbol, side, orderType, quantity, price, account
            );
            
            // Persist to command log (write-ahead)
            long startAppend = System.nanoTime();
            commandLog.append(command);
            long appendLatency = System.nanoTime() - startAppend;
            
            // Register in idempotency index
            clientOrderIndex.put(coid, internalOrderId);
            
            // Create state machine
            OrderStateMachine stateMachine = new OrderStateMachine(internalOrderId);
            orderStateMachines.put(internalOrderId, stateMachine);
            
            // Store correlation ID
            if (correlationId != null) {
                orderCorrelationIds.put(internalOrderId, correlationId);
            }
            
            // Create and publish order event
            Event event = Event.now(
                internalOrderId.value(),
                SourceId.OMS,
                EventType.ORDER_SUBMITTED,
                0L,
                orderEvent
            );
            
            long startPublish = System.nanoTime();
            boolean published = eventBus.publish(event);
            long publishLatency = System.nanoTime() - startPublish;
            
            if (!published) {
                return SubmissionResult.error("Failed to publish order to EventBus", correlationId);
            }
            
            // Total latency should be < 10ms (including risk check)
            long totalLatency = System.nanoTime() - submissionStartNanos;
            
            return SubmissionResult.success(internalOrderId.value(), correlationId, totalLatency);
            
        } catch (IllegalArgumentException e) {
            return SubmissionResult.validationError(e.getMessage(), correlationId);
        } catch (IOException e) {
            return SubmissionResult.error("Failed to persist command: " + e.getMessage(), correlationId);
        }
    }
    
    /**
     * Logs risk decision for audit trail.
     */
    private void logRiskDecision(InternalOrderId orderId, OrderEvent orderEvent, 
                                 RiskCheckService.RiskCheckResult riskResult, boolean approved) {
        // Create audit log entry
        String logMessage = String.format(
            "[RISK_CHECK] OrderID=%d, Symbol=%s, Side=%d, Qty=%d, Price=%d, " +
            "Decision=%s, ReasonCode=%d, Latency=%dus, Attempts=%d, ServiceAvailable=%s",
            orderId.value(),
            orderEvent.symbol(),
            orderEvent.side(),
            orderEvent.quantity(),
            orderEvent.price(),
            approved ? "APPROVED" : "REJECTED",
            riskResult.decision().reasonCode(),
            riskResult.latencyNanos() / 1000,
            riskResult.attempts(),
            riskResult.serviceAvailable()
        );
        
        // Use SLF4J logger in production; using System.out for now
        System.out.println(logMessage);
    }
    
    /**
     * Cancels an order.
     * 
     * @param clientOrderId Client order ID
     * @param correlationId Correlation ID for tracking
     * @return CancellationResult with status
     */
    public CancellationResult cancelOrder(String clientOrderId, String correlationId) {
        try {
            ClientOrderId coid = ClientOrderId.of(clientOrderId);
            
            // Find internal order ID
            InternalOrderId internalOrderId = clientOrderIndex.get(coid);
            if (internalOrderId == null) {
                return CancellationResult.notFound(correlationId);
            }
            
            // Check if order can be cancelled
            OrderStateMachine stateMachine = orderStateMachines.get(internalOrderId);
            if (stateMachine == null) {
                return CancellationResult.error("Order state machine not found", correlationId);
            }
            
            // If order is in NEW state, transition to ACCEPTED first (implicit acceptance)
            if (stateMachine.getCurrentState() == OrderState.NEW) {
                try {
                    stateMachine.transition(OrderState.ACCEPTED);
                } catch (Exception e) {
                    return CancellationResult.error("Failed to accept order before cancellation: " + e.getMessage(), correlationId);
                }
            }
            
            // Now check if order can be cancelled
            if (!stateMachine.isCancellable()) {
                return CancellationResult.error("Order cannot be cancelled in current state: " + stateMachine.getCurrentState(), correlationId);
            }
            
            // Generate command ID
            long commandId = commandIdGenerator.getAndIncrement();
            
            // Create cancel command
            OrderCommand command = OrderCommand.cancel(commandId, clientOrderId, internalOrderId.value());
            
            // Persist to command log
            commandLog.append(command);
            
            // Update state machine
            stateMachine.transition(OrderState.CANCELLED);
            
            // Publish cancellation event
            Event event = Event.now(
                internalOrderId.value(),
                SourceId.OMS,
                EventType.ORDER_CANCELLED,
                0L,
                null
            );
            
            eventBus.publish(event);
            
            return CancellationResult.success(internalOrderId.value(), correlationId);
            
        } catch (IllegalArgumentException e) {
            return CancellationResult.validationError(e.getMessage(), correlationId);
        } catch (IOException e) {
            return CancellationResult.error("Failed to persist command: " + e.getMessage(), correlationId);
        }
    }
    
    /**
     * Queries order status.
     * 
     * @param clientOrderId Client order ID
     * @return OrderStatus with current state
     */
    public OrderStatus queryOrder(String clientOrderId) {
        try {
            ClientOrderId coid = ClientOrderId.of(clientOrderId);
            
            InternalOrderId internalOrderId = clientOrderIndex.get(coid);
            if (internalOrderId == null) {
                return OrderStatus.notFound();
            }
            
            OrderStateMachine stateMachine = orderStateMachines.get(internalOrderId);
            if (stateMachine == null) {
                return OrderStatus.notFound();
            }
            
            String correlationId = orderCorrelationIds.get(internalOrderId);
            
            return OrderStatus.found(
                internalOrderId.value(),
                stateMachine.getCurrentState(),
                stateMachine.isTerminal(),
                correlationId
            );
            
        } catch (IllegalArgumentException e) {
            return OrderStatus.validationError(e.getMessage());
        }
    }
    
    /**
     * Gets statistics about the OMS.
     */
    public OMSStats getStats() {
        return new OMSStats(
            commandLog.getCommandCount(),
            clientOrderIndex.size(),
            orderStateMachines.size(),
            internalOrderIdGenerator.get() - 1
        );
    }
    
    /**
     * Gets risk check metrics.
     */
    public RiskCheckService.RiskMetrics getRiskMetrics() {
        return riskCheckService.getMetrics();
    }
    
    /**
     * Gets the risk check service for direct access.
     * Used by REST API for standalone risk checks.
     */
    public RiskCheckService getRiskCheckService() {
        return riskCheckService;
    }
    
    @Override
    public void close() throws IOException {
        commandLog.close();
    }
    
    // Result classes
    
    public record SubmissionResult(
        boolean success,
        long orderId,
        String message,
        String correlationId,
        long latencyNanos,
        boolean riskRejected,
        int riskReasonCode
    ) {
        public static SubmissionResult success(long orderId, String correlationId, long latencyNanos) {
            return new SubmissionResult(true, orderId, "Order submitted successfully", correlationId, latencyNanos, false, 0);
        }
        
        public static SubmissionResult alreadyExists(long orderId, String correlationId) {
            return new SubmissionResult(true, orderId, "Order already exists (idempotent)", correlationId, 0, false, 0);
        }
        
        public static SubmissionResult riskRejection(long orderId, String message, int reasonCode, String correlationId, long latencyNanos) {
            return new SubmissionResult(false, orderId, "Risk rejection: " + message, correlationId, latencyNanos, true, reasonCode);
        }
        
        public static SubmissionResult validationError(String message, String correlationId) {
            return new SubmissionResult(false, 0, "Validation error: " + message, correlationId, 0, false, 0);
        }
        
        public static SubmissionResult error(String message, String correlationId) {
            return new SubmissionResult(false, 0, message, correlationId, 0, false, 0);
        }
    }
    
    public record CancellationResult(
        boolean success,
        long orderId,
        String message,
        String correlationId
    ) {
        public static CancellationResult success(long orderId, String correlationId) {
            return new CancellationResult(true, orderId, "Order cancelled successfully", correlationId);
        }
        
        public static CancellationResult notFound(String correlationId) {
            return new CancellationResult(false, 0, "Order not found", correlationId);
        }
        
        public static CancellationResult validationError(String message, String correlationId) {
            return new CancellationResult(false, 0, "Validation error: " + message, correlationId);
        }
        
        public static CancellationResult error(String message, String correlationId) {
            return new CancellationResult(false, 0, message, correlationId);
        }
    }
    
    public record OrderStatus(
        boolean found,
        long orderId,
        OrderState currentState,
        boolean isTerminal,
        String correlationId,
        String message
    ) {
        public static OrderStatus found(long orderId, OrderState state, boolean terminal, String correlationId) {
            return new OrderStatus(true, orderId, state, terminal, correlationId, null);
        }
        
        public static OrderStatus notFound() {
            return new OrderStatus(false, 0, null, false, null, "Order not found");
        }
        
        public static OrderStatus validationError(String message) {
            return new OrderStatus(false, 0, null, false, null, "Validation error: " + message);
        }
    }
    
    public record OMSStats(
        long totalCommands,
        long activeOrders,
        long totalOrders,
        long lastOrderId
    ) {}
}
