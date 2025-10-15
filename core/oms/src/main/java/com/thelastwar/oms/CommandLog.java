package com.thelastwar.oms;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * CommandLog provides append-only write-ahead logging for order commands.
 * 
 * This ensures that all order operations are durably persisted before being processed,
 * enabling recovery and replay after failures.
 * 
 * Performance characteristics:
 * - Append operation: < 100 µs (with fsync)
 * - Sequential I/O for optimal disk performance
 * - Minimal memory overhead
 * 
 * Guarantees:
 * - Durability: Commands are synced to disk
 * - Ordering: Commands are stored in sequence order
 * - Atomicity: Each command write is atomic
 */
public interface CommandLog extends AutoCloseable {
    
    /**
     * Appends a command to the log.
     * This method blocks until the command is durably persisted to disk.
     * 
     * @param command The command to append
     * @throws IOException if an I/O error occurs
     */
    void append(OrderCommand command) throws IOException;
    
    /**
     * Replays commands from the log.
     * 
     * @param fromCommandId Starting command ID (inclusive)
     * @param toCommandId   Ending command ID (inclusive)
     * @param consumer      Consumer to receive replayed commands
     * @return number of commands replayed
     * @throws IOException if an I/O error occurs
     */
    long replay(long fromCommandId, long toCommandId, Consumer<OrderCommand> consumer) throws IOException;
    
    /**
     * Gets the total number of commands in the log.
     * 
     * @return command count
     */
    long getCommandCount();
    
    /**
     * Gets the latest command ID in the log.
     * 
     * @return latest command ID, or -1 if log is empty
     */
    long getLatestCommandId();
    
    /**
     * Flushes any buffered commands to disk.
     * 
     * @throws IOException if an I/O error occurs
     */
    void flush() throws IOException;
    
    /**
     * Closes the command log and releases resources.
     * 
     * @throws IOException if an I/O error occurs
     */
    @Override
    void close() throws IOException;
}
