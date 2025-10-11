package com.thelastwar.wsgateway;

import com.thelastwar.eventbus.Event;
import com.thelastwar.eventbus.model.ExecutionEvent;
import com.thelastwar.eventbus.model.OrderEvent;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

/**
 * MessagePack binary serializer for WebSocket messages.
 * 
 * Provides compact binary format with:
 * - Smaller message size vs JSON (30-50% reduction)
 * - Faster serialization/deserialization
 * - Lower bandwidth usage for high-throughput scenarios
 */
public class MessagePackWebSocketSerializer implements WebSocketMessageSerializer {
    
    @Override
    public byte[] serialize(Event event) throws SerializationException {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packer.packMapHeader(6);
            
            packer.packString("timestamp");
            packer.packLong(event.timestamp());
            
            packer.packString("sequence");
            packer.packLong(event.sequence());
            
            packer.packString("sourceId");
            packer.packInt(event.sourceId());
            
            packer.packString("eventType");
            packer.packInt(event.eventType());
            
            packer.packString("header");
            packer.packLong(event.header());
            
            packer.packString("payload");
            Object payload = event.payload();
            if (payload instanceof OrderEvent) {
                packOrderEvent(packer, (OrderEvent) payload);
            } else if (payload instanceof ExecutionEvent) {
                packExecutionEvent(packer, (ExecutionEvent) payload);
            } else {
                packer.packNil();
            }
            
            return packer.toByteArray();
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize event", e);
        }
    }
    
    @Override
    public byte[] serializeOrderEvent(OrderEvent orderEvent) throws SerializationException {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packOrderEvent(packer, orderEvent);
            return packer.toByteArray();
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize OrderEvent", e);
        }
    }
    
    @Override
    public byte[] serializeExecutionEvent(ExecutionEvent executionEvent) throws SerializationException {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packExecutionEvent(packer, executionEvent);
            return packer.toByteArray();
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize ExecutionEvent", e);
        }
    }
    
    private void packOrderEvent(MessageBufferPacker packer, OrderEvent order) throws IOException {
        packer.packMapHeader(10);
        
        packer.packString("orderId");
        packer.packLong(order.orderId());
        
        packer.packString("symbol");
        packer.packString(order.symbol());
        
        packer.packString("side");
        packer.packByte(order.side());
        
        packer.packString("orderType");
        packer.packByte(order.orderType());
        
        packer.packString("quantity");
        packer.packLong(order.quantity());
        
        packer.packString("price");
        packer.packLong(order.price());
        
        packer.packString("timestamp");
        packer.packLong(order.timestamp());
        
        packer.packString("status");
        packer.packByte(order.status());
        
        packer.packString("account");
        packer.packLong(order.account());
        
        packer.packString("exchange");
        packer.packInt(order.exchange());
    }
    
    private void packExecutionEvent(MessageBufferPacker packer, ExecutionEvent exec) throws IOException {
        packer.packMapHeader(14);
        
        packer.packString("executionId");
        packer.packLong(exec.executionId());
        
        packer.packString("orderId");
        packer.packLong(exec.orderId());
        
        packer.packString("symbol");
        packer.packString(exec.symbol());
        
        packer.packString("side");
        packer.packByte(exec.side());
        
        packer.packString("executionType");
        packer.packByte(exec.executionType());
        
        packer.packString("orderStatus");
        packer.packByte(exec.orderStatus());
        
        packer.packString("lastQuantity");
        packer.packLong(exec.lastQuantity());
        
        packer.packString("lastPrice");
        packer.packLong(exec.lastPrice());
        
        packer.packString("cumulativeQty");
        packer.packLong(exec.cumulativeQty());
        
        packer.packString("leavesQuantity");
        packer.packLong(exec.leavesQuantity());
        
        packer.packString("timestamp");
        packer.packLong(exec.timestamp());
        
        packer.packString("account");
        packer.packLong(exec.account());
        
        packer.packString("exchange");
        packer.packInt(exec.exchange());
        
        packer.packString("rejectReason");
        packer.packInt(exec.rejectReason());
    }
    
    @Override
    public WebSocketMessage deserialize(byte[] data) throws SerializationException {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            int mapSize = unpacker.unpackMapHeader();
            
            String type = null;
            Object payload = null;
            
            for (int i = 0; i < mapSize; i++) {
                String key = unpacker.unpackString();
                if ("type".equals(key)) {
                    type = unpacker.unpackString();
                } else if ("payload".equals(key)) {
                    // Simplified payload handling
                    if (unpacker.tryUnpackNil()) {
                        payload = null;
                    } else {
                        payload = unpacker.unpackValue().toString();
                    }
                } else {
                    unpacker.skipValue();
                }
            }
            
            if (type == null) {
                throw new SerializationException("Missing 'type' field in message");
            }
            
            WebSocketMessage.MessageType messageType = WebSocketMessage.MessageType.valueOf(type);
            return new WebSocketMessage(messageType, payload);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize message", e);
        }
    }
    
    @Override
    public TransportFormat getFormat() {
        return TransportFormat.MESSAGEPACK;
    }
}
