package com.thelastwar.wsgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON serializer using Jackson for WebSocket messages.
 * 
 * Optimized for low-latency serialization with:
 * - Minimal object allocation
 * - Pre-configured ObjectMapper
 * - Efficient field ordering
 */
public class JsonWebSocketSerializer implements WebSocketMessageSerializer {
    
    private final ObjectMapper mapper;
    
    public JsonWebSocketSerializer() {
        this.mapper = new ObjectMapper();
        // Configure for performance
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    @Override
    public byte[] serialize(Event event) throws SerializationException {
        try {
            Map<String, Object> eventMap = new HashMap<>();
            eventMap.put("timestamp", event.timestamp());
            eventMap.put("sequence", event.sequence());
            eventMap.put("sourceId", event.sourceId());
            eventMap.put("eventType", event.eventType());
            eventMap.put("header", event.header());
            
            // Serialize payload based on type
            Object payload = event.payload();
            if (payload instanceof OrderEvent) {
                eventMap.put("payload", serializeOrderEventToMap((OrderEvent) payload));
            } else if (payload instanceof ExecutionEvent) {
                eventMap.put("payload", serializeExecutionEventToMap((ExecutionEvent) payload));
            } else if (payload != null) {
                eventMap.put("payload", payload);
            }
            
            return mapper.writeValueAsBytes(eventMap);
        } catch (Exception e) {
            throw new SerializationException("Failed to serialize event", e);
        }
    }
    
    @Override
    public byte[] serializeOrderEvent(OrderEvent orderEvent) throws SerializationException {
        try {
            return mapper.writeValueAsBytes(serializeOrderEventToMap(orderEvent));
        } catch (Exception e) {
            throw new SerializationException("Failed to serialize OrderEvent", e);
        }
    }
    
    @Override
    public byte[] serializeExecutionEvent(ExecutionEvent executionEvent) throws SerializationException {
        try {
            return mapper.writeValueAsBytes(serializeExecutionEventToMap(executionEvent));
        } catch (Exception e) {
            throw new SerializationException("Failed to serialize ExecutionEvent", e);
        }
    }
    
    private Map<String, Object> serializeOrderEventToMap(OrderEvent order) {
        Map<String, Object> map = new HashMap<>();
        map.put("orderId", order.orderId());
        map.put("symbol", order.symbol());
        map.put("side", order.side());
        map.put("orderType", order.orderType());
        map.put("quantity", order.quantity());
        map.put("price", order.price());
        map.put("timestamp", order.timestamp());
        map.put("status", order.status());
        map.put("account", order.account());
        map.put("exchange", order.exchange());
        return map;
    }
    
    private Map<String, Object> serializeExecutionEventToMap(ExecutionEvent exec) {
        Map<String, Object> map = new HashMap<>();
        map.put("executionId", exec.executionId());
        map.put("orderId", exec.orderId());
        map.put("symbol", exec.symbol());
        map.put("side", exec.side());
        map.put("executionType", exec.executionType());
        map.put("orderStatus", exec.orderStatus());
        map.put("lastQuantity", exec.lastQuantity());
        map.put("lastPrice", exec.lastPrice());
        map.put("cumulativeQty", exec.cumulativeQty());
        map.put("leavesQuantity", exec.leavesQuantity());
        map.put("timestamp", exec.timestamp());
        map.put("account", exec.account());
        map.put("exchange", exec.exchange());
        map.put("rejectReason", exec.rejectReason());
        return map;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public WebSocketMessage deserialize(byte[] data) throws SerializationException {
        try {
            Map<String, Object> messageMap = mapper.readValue(data, Map.class);
            String typeStr = (String) messageMap.get("type");
            Object payload = messageMap.get("payload");
            
            WebSocketMessage.MessageType type = WebSocketMessage.MessageType.valueOf(typeStr);
            return new WebSocketMessage(type, payload);
        } catch (Exception e) {
            throw new SerializationException("Failed to deserialize message", e);
        }
    }
    
    @Override
    public TransportFormat getFormat() {
        return TransportFormat.JSON;
    }
}
