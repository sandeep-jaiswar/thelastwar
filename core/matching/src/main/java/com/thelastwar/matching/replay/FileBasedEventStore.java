package com.thelastwar.matching.replay;

import com.thelastwar.eventbus.Event;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * File-based implementation of ReplayEventStore.
 * 
 * Stores events in a binary format for efficient replay.
 * Format per event:
 * - 8 bytes: timestamp
 * - 8 bytes: sequence
 * - 4 bytes: sourceId
 * - 4 bytes: eventType
 * - 8 bytes: header
 * - 4 bytes: payload length
 * - N bytes: serialized payload
 */
public class FileBasedEventStore implements ReplayEventStore {
    
    private final Path storePath;
    private final AtomicLong eventCount;
    private final AtomicLong currentSequence;
    private FileChannel writeChannel;
    private final Object writeLock = new Object();
    
    public FileBasedEventStore(Path storePath) throws IOException {
        this.storePath = storePath;
        this.eventCount = new AtomicLong(0);
        this.currentSequence = new AtomicLong(0);
        
        // Create parent directories if needed
        if (storePath.getParent() != null) {
            Files.createDirectories(storePath.getParent());
        }
        
        // Open file for writing
        this.writeChannel = FileChannel.open(storePath, 
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND);
        
        // If file exists, count events to restore counters
        if (Files.exists(storePath) && Files.size(storePath) > 0) {
            restoreCounters();
        }
    }
    
    private void restoreCounters() throws IOException {
        long count = 0;
        long maxSeq = 0;
        
        try (FileChannel channel = FileChannel.open(storePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            
            while (channel.position() < channel.size()) {
                buffer.clear();
                buffer.limit(36); // Read header (timestamp + sequence + sourceId + eventType + header)
                
                int bytesRead = channel.read(buffer);
                if (bytesRead < 36) break;
                
                buffer.flip();
                buffer.getLong(); // timestamp
                long seq = buffer.getLong(); // sequence
                maxSeq = Math.max(maxSeq, seq);
                buffer.getInt(); // sourceId
                buffer.getInt(); // eventType
                buffer.getLong(); // header
                
                // Read payload length
                buffer.clear();
                buffer.limit(4);
                if (channel.read(buffer) < 4) break;
                buffer.flip();
                int payloadLen = buffer.getInt();
                
                // Skip payload
                channel.position(channel.position() + payloadLen);
                count++;
            }
        }
        
        eventCount.set(count);
        currentSequence.set(maxSeq);
    }
    
    @Override
    public void append(Event event) throws IOException {
        synchronized (writeLock) {
            ByteBuffer buffer = serializeEvent(event);
            writeChannel.write(buffer);
            writeChannel.force(false); // Sync to disk
            
            eventCount.incrementAndGet();
            currentSequence.set(Math.max(currentSequence.get(), event.sequence()));
        }
    }
    
    @Override
    public long replay(long fromSequence, long toSequence, Consumer<Event> consumer) throws IOException {
        long replayedCount = 0;
        
        if (!Files.exists(storePath) || Files.size(storePath) == 0) {
            return 0;
        }
        
        try (FileChannel channel = FileChannel.open(storePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            
            while (true) {
                // Check if we're at end of file
                if (channel.position() >= channel.size()) {
                    break;
                }
                
                // Read event header
                buffer.clear();
                buffer.limit(36);
                
                int bytesRead = 0;
                while (buffer.hasRemaining() && bytesRead >= 0) {
                    int read = channel.read(buffer);
                    if (read < 0) break;
                    bytesRead += read;
                }
                if (bytesRead < 36) break;
                
                buffer.flip();
                long timestamp = buffer.getLong();
                long sequence = buffer.getLong();
                int sourceId = buffer.getInt();
                int eventType = buffer.getInt();
                long header = buffer.getLong();
                
                // Read payload length
                buffer.clear();
                buffer.limit(4);
                bytesRead = 0;
                while (buffer.hasRemaining() && bytesRead >= 0) {
                    int read = channel.read(buffer);
                    if (read < 0) break;
                    bytesRead += read;
                }
                if (bytesRead < 4) break;
                
                buffer.flip();
                int payloadLen = buffer.getInt();
                
                // Read payload
                byte[] payloadBytes = new byte[payloadLen];
                ByteBuffer payloadBuffer = ByteBuffer.wrap(payloadBytes);
                bytesRead = 0;
                while (payloadBuffer.hasRemaining() && bytesRead >= 0) {
                    int read = channel.read(payloadBuffer);
                    if (read < 0) break;
                    bytesRead += read;
                }
                if (bytesRead < payloadLen) break;
                
                // Process event if in range
                if (sequence >= fromSequence && sequence <= toSequence) {
                    Object payload = deserializePayload(payloadBytes);
                    Event event = Event.create(timestamp, sequence, sourceId, eventType, header, payload);
                    consumer.accept(event);
                    replayedCount++;
                }
            }
        }
        
        return replayedCount;
    }
    
    @Override
    public long getEventCount() {
        return eventCount.get();
    }
    
    @Override
    public long getCurrentSequence() {
        return currentSequence.get();
    }
    
    @Override
    public void clear() throws IOException {
        synchronized (writeLock) {
            writeChannel.close();
            Files.deleteIfExists(storePath);
            
            writeChannel = FileChannel.open(storePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
            
            eventCount.set(0);
            currentSequence.set(0);
        }
    }
    
    @Override
    public void close() throws IOException {
        synchronized (writeLock) {
            if (writeChannel != null && writeChannel.isOpen()) {
                writeChannel.close();
            }
        }
    }
    
    private ByteBuffer serializeEvent(Event event) throws IOException {
        // Serialize payload
        byte[] payloadBytes = serializePayload(event.payload());
        
        // Allocate buffer for entire event
        int totalSize = 36 + 4 + payloadBytes.length; // header + payload length + payload
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        
        buffer.putLong(event.timestamp());
        buffer.putLong(event.sequence());
        buffer.putInt(event.sourceId());
        buffer.putInt(event.eventType());
        buffer.putLong(event.header());
        buffer.putInt(payloadBytes.length);
        buffer.put(payloadBytes);
        
        buffer.flip();
        return buffer;
    }
    
    private byte[] serializePayload(Object payload) throws IOException {
        if (payload == null) {
            return new byte[0];
        }
        
        // For OrderEvent, use custom serialization
        if (payload instanceof com.thelastwar.eventbus.model.OrderEvent) {
            return serializeOrderEvent((com.thelastwar.eventbus.model.OrderEvent) payload);
        }
        
        // For other types, use toString() as simple serialization
        String str = payload.toString();
        return str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    
    private byte[] serializeOrderEvent(com.thelastwar.eventbus.model.OrderEvent order) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            
            dos.writeLong(order.orderId());
            
            // Write symbol length and bytes manually
            byte[] symbolBytes = order.symbol().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            dos.writeInt(symbolBytes.length);
            dos.write(symbolBytes);
            
            dos.writeByte(order.side());
            dos.writeByte(order.orderType());
            dos.writeLong(order.quantity());
            dos.writeLong(order.price());
            dos.writeLong(order.timestamp());
            dos.writeByte(order.status());
            dos.writeLong(order.account());
            dos.writeByte(order.exchange());
            dos.writeByte(order.timeInForce());
            dos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private Object deserializePayload(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        
        // Deserialize as OrderEvent
        return deserializeOrderEvent(bytes);
    }
    
    private com.thelastwar.eventbus.model.OrderEvent deserializeOrderEvent(byte[] bytes) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(bis);
        
        long orderId = dis.readLong();
        
        // Read symbol length and bytes manually
        int symbolLen = dis.readInt();
        byte[] symbolBytes = new byte[symbolLen];
        dis.readFully(symbolBytes);
        String symbol = new String(symbolBytes, java.nio.charset.StandardCharsets.UTF_8);
        
        byte side = dis.readByte();
        byte orderType = dis.readByte();
        long quantity = dis.readLong();
        long price = dis.readLong();
        long timestamp = dis.readLong();
        byte status = dis.readByte();
        long account = dis.readLong();
        byte exchange = dis.readByte();
        byte timeInForce = dis.readByte();
        
        return new com.thelastwar.eventbus.model.OrderEvent(
            orderId, symbol, side, orderType, quantity, price,
            timestamp, status, account, exchange, timeInForce
        );
    }
    
    /**
     * Gets the file path of this store.
     * 
     * @return Store path
     */
    public Path getStorePath() {
        return storePath;
    }
}
