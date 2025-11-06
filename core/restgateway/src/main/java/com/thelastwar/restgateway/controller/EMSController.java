package com.thelastwar.restgateway.controller;

import com.thelastwar.ems.*;
import com.thelastwar.oms.ClientOrderId;
import com.thelastwar.oms.InternalOrderId;
import com.thelastwar.oms.ParentOrder;
import com.thelastwar.orderbook.OrderType;
import com.thelastwar.orderbook.Side;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for EMS (Execution Management System) operations.
 * 
 * Provides endpoints for:
 * - Starting execution strategies (TWAP, VWAP, etc.)
 * - Controlling strategy execution (pause/resume/stop)
 * - Monitoring strategy performance metrics
 */
@RestController
@RequestMapping("/api/v1/ems")
public class EMSController {
    
    private final EMSService emsService;
    
    public EMSController(EMSService emsService) {
        this.emsService = emsService;
    }
    
    /**
     * Start a new execution strategy.
     * POST /api/v1/ems/strategies
     * 
     * @param request Strategy start request
     * @return Strategy start response with strategy ID
     */
    @PostMapping(value = "/strategies", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<StrategyStartResponse> startStrategy(@RequestBody StrategyStartRequest request) {
        return Mono.fromCallable(() -> {
            try {
                // Build strategy config
                StrategyConfig config = new StrategyConfig.Builder()
                    .strategyId(request.strategyId())
                    .strategyType(StrategyType.valueOf(request.strategyType().toUpperCase()))
                    .symbol(request.symbol())
                    .duration(Duration.parse(request.duration()))
                    .numSlices(request.numSlices())
                    .minChildSize(request.minChildSize())
                    .maxChildSize(request.maxChildSize())
                    .priceLimit(request.priceLimit())
                    .allowPartialFills(request.allowPartialFills() != null ? request.allowPartialFills() : true)
                    .parameters(request.parameters() != null ? request.parameters() : Map.of())
                    .build();
                
                // Create parent order
                ParentOrder parentOrder = new ParentOrder(
                    new InternalOrderId(System.currentTimeMillis()),
                    new ClientOrderId(request.clientOrderId()),
                    request.symbol(),
                    Side.valueOf(request.side().toUpperCase()).getValue(),
                    OrderType.valueOf(request.orderType().toUpperCase()).getValue(),
                    request.quantity(),
                    request.price(),
                    request.account() != null ? request.account() : 0L,
                    System.nanoTime()
                );
                
                // Start strategy
                Strategy strategy = emsService.startStrategy(config, parentOrder);
                
                return new StrategyStartResponse(
                    request.strategyId(),
                    strategy.getState().toString(),
                    "Strategy started successfully"
                );
                
            } catch (StrategyException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to start strategy", e);
            }
        });
    }
    
    /**
     * Get strategy status and metrics.
     * GET /api/v1/ems/strategies/{strategyId}
     * 
     * @param strategyId Strategy ID
     * @return Strategy status response
     */
    @GetMapping(value = "/strategies/{strategyId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<StrategyStatusResponse> getStrategyStatus(@PathVariable String strategyId) {
        return Mono.fromCallable(() -> {
            Strategy strategy = emsService.getStrategy(strategyId);
            if (strategy == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Strategy not found: " + strategyId);
            }
            
            StrategyMetrics metrics = strategy.getMetrics();
            StrategyConfig config = strategy.getConfig();
            
            return new StrategyStatusResponse(
                strategyId,
                config.strategyType().toString(),
                strategy.getState().toString(),
                config.symbol(),
                metrics.totalQuantity(),
                metrics.filledQuantity(),
                metrics.childOrdersGenerated(),
                metrics.childOrdersFilled(),
                metrics.averageFillPrice(),
                metrics.getFillRate(),
                metrics.getExecutionDuration().toMillis()
            );
        });
    }
    
    /**
     * List all active strategies.
     * GET /api/v1/ems/strategies
     * 
     * @return List of strategy summaries
     */
    @GetMapping(value = "/strategies", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<StrategiesListResponse> listStrategies() {
        return Mono.fromCallable(() -> {
            Map<String, Strategy> strategies = emsService.getAllStrategies();
            
            var summaries = strategies.entrySet().stream()
                .map(entry -> new StrategySummary(
                    entry.getKey(),
                    entry.getValue().getConfig().strategyType().toString(),
                    entry.getValue().getState().toString(),
                    entry.getValue().getConfig().symbol(),
                    entry.getValue().getMetrics().getFillRate()
                ))
                .collect(Collectors.toList());
            
            return new StrategiesListResponse(summaries);
        });
    }
    
    /**
     * Pause a running strategy.
     * POST /api/v1/ems/strategies/{strategyId}/pause
     * 
     * @param strategyId Strategy ID
     * @return Control response
     */
    @PostMapping(value = "/strategies/{strategyId}/pause", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<StrategyControlResponse> pauseStrategy(@PathVariable String strategyId) {
        return Mono.fromCallable(() -> {
            boolean success = emsService.pauseStrategy(strategyId);
            if (!success) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Strategy not found: " + strategyId);
            }
            return new StrategyControlResponse(strategyId, "PAUSED", "Strategy paused successfully");
        });
    }
    
    /**
     * Resume a paused strategy.
     * POST /api/v1/ems/strategies/{strategyId}/resume
     * 
     * @param strategyId Strategy ID
     * @return Control response
     */
    @PostMapping(value = "/strategies/{strategyId}/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<StrategyControlResponse> resumeStrategy(@PathVariable String strategyId) {
        return Mono.fromCallable(() -> {
            boolean success = emsService.resumeStrategy(strategyId);
            if (!success) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Strategy not found: " + strategyId);
            }
            return new StrategyControlResponse(strategyId, "RUNNING", "Strategy resumed successfully");
        });
    }
    
    /**
     * Stop a strategy.
     * POST /api/v1/ems/strategies/{strategyId}/stop
     * 
     * @param strategyId Strategy ID
     * @return Control response
     */
    @PostMapping(value = "/strategies/{strategyId}/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<StrategyControlResponse> stopStrategy(@PathVariable String strategyId) {
        return Mono.fromCallable(() -> {
            boolean success = emsService.stopStrategy(strategyId);
            if (!success) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Strategy not found: " + strategyId);
            }
            return new StrategyControlResponse(strategyId, "STOPPED", "Strategy stopped successfully");
        });
    }
    
    /**
     * Get detailed metrics for a strategy.
     * GET /api/v1/ems/strategies/{strategyId}/metrics
     * 
     * @param strategyId Strategy ID
     * @return Detailed metrics response
     */
    @GetMapping(value = "/strategies/{strategyId}/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<StrategyMetricsResponse> getStrategyMetrics(@PathVariable String strategyId) {
        return Mono.fromCallable(() -> {
            StrategyMetrics metrics = emsService.getMetrics(strategyId);
            if (metrics == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Strategy not found: " + strategyId);
            }
            
            return new StrategyMetricsResponse(
                metrics.strategyId(),
                metrics.strategyType().toString(),
                metrics.totalQuantity(),
                metrics.filledQuantity(),
                metrics.childOrdersGenerated(),
                metrics.childOrdersFilled(),
                metrics.childOrdersPartiallyFilled(),
                metrics.childOrdersRejected(),
                metrics.averageFillPrice(),
                metrics.vwap(),
                metrics.bestPrice(),
                metrics.worstPrice(),
                metrics.totalSlippage(),
                metrics.getAverageSlippage(),
                metrics.ticksProcessed(),
                metrics.fillsProcessed(),
                metrics.getFillRate(),
                metrics.getChildOrderSuccessRate(),
                metrics.getExecutionDuration().toMillis()
            );
        });
    }
    
    // DTOs
    
    public record StrategyStartRequest(
        String strategyId,
        String strategyType,
        String clientOrderId,
        String symbol,
        String side,
        String orderType,
        long quantity,
        long price,
        Long account,
        String duration,
        Integer numSlices,
        Long minChildSize,
        Long maxChildSize,
        Long priceLimit,
        Boolean allowPartialFills,
        Map<String, Object> parameters
    ) {}
    
    public record StrategyStartResponse(
        String strategyId,
        String state,
        String message
    ) {}
    
    public record StrategyStatusResponse(
        String strategyId,
        String strategyType,
        String state,
        String symbol,
        long totalQuantity,
        long filledQuantity,
        int childOrdersGenerated,
        int childOrdersFilled,
        long averageFillPrice,
        double fillRate,
        long executionDurationMs
    ) {}
    
    public record StrategySummary(
        String strategyId,
        String strategyType,
        String state,
        String symbol,
        double fillRate
    ) {}
    
    public record StrategiesListResponse(
        java.util.List<StrategySummary> strategies
    ) {}
    
    public record StrategyControlResponse(
        String strategyId,
        String newState,
        String message
    ) {}
    
    public record StrategyMetricsResponse(
        String strategyId,
        String strategyType,
        long totalQuantity,
        long filledQuantity,
        int childOrdersGenerated,
        int childOrdersFilled,
        int childOrdersPartiallyFilled,
        int childOrdersRejected,
        long averageFillPrice,
        long vwap,
        long bestPrice,
        long worstPrice,
        long totalSlippage,
        long averageSlippage,
        long ticksProcessed,
        long fillsProcessed,
        double fillRate,
        double childOrderSuccessRate,
        long executionDurationMs
    ) {}
}
