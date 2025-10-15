package com.thelastwar.oms;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * File-based implementation of CommandLog using sequential I/O for optimal performance.
 * 
 * Storage format (per command):
 * - 8 bytes: commandId
 * - 8 bytes: timestamp
 * - 4 bytes: commandType (ordinal)
 * - 4 bytes: clientOrderId length
 * - N bytes: clientOrderId (UTF-8)
 * - 8 bytes: internalOrderId (or -1 if null)
 * - 4 bytes: symbol length
 * - N bytes: symbol (UTF-8, or empty if null)
 * - 1 byte: side
 * - 1 byte: orderType
 * - 8 bytes: quantity
 * - 8 bytes: price
 * - 8 bytes: account
 */
public class FileBasedCommandLog implements CommandLog {
    
    private final Path logPath;
    private final AtomicLong commandCount;
    private final AtomicLong latestCommandId;
    private FileChannel writeChannel;
    private final Object writeLock = new Object();
    
    public FileBasedCommandLog(Path logPath) throws IOException {
        this.logPath = logPath;
        this.commandCount = new AtomicLong(0);
        this.latestCommandId = new AtomicLong(-1);
        
        // Create parent directories if needed
        if (logPath.getParent() != null) {
            Files.createDirectories(logPath.getParent());
        }
        
        // Open file for writing
        this.writeChannel = FileChannel.open(logPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND);
        
        // If file exists, restore counters
        if (Files.exists(logPath) && Files.size(logPath) > 0) {
            restoreCounters();
        }
    }
    
    private void restoreCounters() throws IOException {
        long count = 0;
        long maxId = -1;
        
        try (FileChannel channel = FileChannel.open(logPath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            
            while (channel.position() < channel.size()) {
                // Read command header
                buffer.clear();
                buffer.limit(20); // commandId + timestamp + commandType
                
                int bytesRead = channel.read(buffer);
                if (bytesRead < 20) break;
                
                buffer.flip();
                long cmdId = buffer.getLong();
                maxId = Math.max(maxId, cmdId);
                buffer.getLong(); // timestamp
                buffer.getInt(); // commandType
                
                // Read clientOrderId length and skip
                buffer.clear();
                buffer.limit(4);
                if (channel.read(buffer) < 4) break;
                buffer.flip();
                int clientOrderIdLen = buffer.getInt();
                channel.position(channel.position() + clientOrderIdLen);
                
                // Read internalOrderId
                buffer.clear();
                buffer.limit(8);
                if (channel.read(buffer) < 8) break;
                
                // Read symbol length and skip
                buffer.clear();
                buffer.limit(4);
                if (channel.read(buffer) < 4) break;
                buffer.flip();
                int symbolLen = buffer.getInt();
                channel.position(channel.position() + symbolLen);
                
                // Skip remaining fixed fields: side, orderType, quantity, price, account
                channel.position(channel.position() + 26); // 1 + 1 + 8 + 8 + 8
                
                count++;
            }
        }
        
        commandCount.set(count);
        latestCommandId.set(maxId);
    }
    
    @Override
    public void append(OrderCommand command) throws IOException {
        synchronized (writeLock) {
            ByteBuffer buffer = serializeCommand(command);
            writeChannel.write(buffer);
            writeChannel.force(false); // Sync to disk
            
            commandCount.incrementAndGet();
            latestCommandId.set(Math.max(latestCommandId.get(), command.commandId()));
        }
    }
    
    private ByteBuffer serializeCommand(OrderCommand cmd) {
        byte[] clientOrderIdBytes = cmd.clientOrderId().getBytes(StandardCharsets.UTF_8);
        byte[] symbolBytes = (cmd.symbol() != null) ? cmd.symbol().getBytes(StandardCharsets.UTF_8) : new byte[0];
        
        int totalSize = 20 + // commandId + timestamp + commandType
                        4 + clientOrderIdBytes.length +
                        8 + // internalOrderId
                        4 + symbolBytes.length +
                        26; // side + orderType + quantity + price + account
        
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        
        // Write command header
        buffer.putLong(cmd.commandId());
        buffer.putLong(cmd.timestamp());
        buffer.putInt(cmd.commandType().ordinal());
        
        // Write clientOrderId
        buffer.putInt(clientOrderIdBytes.length);
        buffer.put(clientOrderIdBytes);
        
        // Write internalOrderId
        buffer.putLong(cmd.internalOrderId() != null ? cmd.internalOrderId() : -1L);
        
        // Write symbol
        buffer.putInt(symbolBytes.length);
        buffer.put(symbolBytes);
        
        // Write order details
        buffer.put(cmd.side());
        buffer.put(cmd.orderType());
        buffer.putLong(cmd.quantity());
        buffer.putLong(cmd.price());
        buffer.putLong(cmd.account());
        
        buffer.flip();
        return buffer;
    }
    
    @Override
    public long replay(long fromCommandId, long toCommandId, Consumer<OrderCommand> consumer) throws IOException {
        long replayedCount = 0;
        
        if (!Files.exists(logPath) || Files.size(logPath) == 0) {
            return 0;
        }
        
        try (FileChannel channel = FileChannel.open(logPath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            
            while (channel.position() < channel.size()) {
                OrderCommand cmd = deserializeCommand(channel, buffer);
                if (cmd == null) break;
                
                if (cmd.commandId() >= fromCommandId && cmd.commandId() <= toCommandId) {
                    consumer.accept(cmd);
                    replayedCount++;
                }
                
                if (cmd.commandId() > toCommandId) {
                    break;
                }
            }
        }
        
        return replayedCount;
    }
    
    private OrderCommand deserializeCommand(FileChannel channel, ByteBuffer buffer) throws IOException {
        // Read command header
        buffer.clear();
        buffer.limit(20);
        
        int bytesRead = channel.read(buffer);
        if (bytesRead < 20) return null;
        
        buffer.flip();
        long commandId = buffer.getLong();
        long timestamp = buffer.getLong();
        int commandTypeOrdinal = buffer.getInt();
        
        // Read clientOrderId
        buffer.clear();
        buffer.limit(4);
        if (channel.read(buffer) < 4) return null;
        buffer.flip();
        int clientOrderIdLen = buffer.getInt();
        
        buffer.clear();
        buffer.limit(clientOrderIdLen);
        if (channel.read(buffer) < clientOrderIdLen) return null;
        buffer.flip();
        byte[] clientOrderIdBytes = new byte[clientOrderIdLen];
        buffer.get(clientOrderIdBytes);
        String clientOrderId = new String(clientOrderIdBytes, StandardCharsets.UTF_8);
        
        // Read internalOrderId
        buffer.clear();
        buffer.limit(8);
        if (channel.read(buffer) < 8) return null;
        buffer.flip();
        long internalOrderIdLong = buffer.getLong();
        Long internalOrderId = (internalOrderIdLong == -1) ? null : internalOrderIdLong;
        
        // Read symbol
        buffer.clear();
        buffer.limit(4);
        if (channel.read(buffer) < 4) return null;
        buffer.flip();
        int symbolLen = buffer.getInt();
        
        String symbol = null;
        if (symbolLen > 0) {
            buffer.clear();
            buffer.limit(symbolLen);
            if (channel.read(buffer) < symbolLen) return null;
            buffer.flip();
            byte[] symbolBytes = new byte[symbolLen];
            buffer.get(symbolBytes);
            symbol = new String(symbolBytes, StandardCharsets.UTF_8);
        }
        
        // Read order details
        buffer.clear();
        buffer.limit(26);
        if (channel.read(buffer) < 26) return null;
        buffer.flip();
        byte side = buffer.get();
        byte orderType = buffer.get();
        long quantity = buffer.getLong();
        long price = buffer.getLong();
        long account = buffer.getLong();
        
        return new OrderCommand(
            commandId,
            timestamp,
            OrderCommand.CommandType.values()[commandTypeOrdinal],
            clientOrderId,
            internalOrderId,
            symbol,
            side,
            orderType,
            quantity,
            price,
            account
        );
    }
    
    @Override
    public long getCommandCount() {
        return commandCount.get();
    }
    
    @Override
    public long getLatestCommandId() {
        return latestCommandId.get();
    }
    
    @Override
    public void flush() throws IOException {
        synchronized (writeLock) {
            writeChannel.force(true);
        }
    }
    
    @Override
    public void close() throws IOException {
        synchronized (writeLock) {
            if (writeChannel != null && writeChannel.isOpen()) {
                writeChannel.force(true);
                writeChannel.close();
            }
        }
    }
}
