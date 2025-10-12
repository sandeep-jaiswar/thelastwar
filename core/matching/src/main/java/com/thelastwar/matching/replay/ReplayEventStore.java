package com.thelastwar.matching.replay;

import com.thelastwar.eventbus.Event;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Interface for persistent event storage to support deterministic replay.
 * 
 * Implementations can use Chronicle Queue, Aeron Archive, or other persistent stores.
 * Events are stored with sequence numbers for ordered replay.
 */
public interface ReplayEventStore extends AutoCloseable {
    
    /**
     * Appends an event to the persistent store.
     * 
     * @param event Event to store
     * @throws IOException if storage fails
     */
    void append(Event event) throws IOException;
    
    /**
     * Replays events from a specific sequence range.
     * 
     * @param fromSequence Starting sequence (inclusive)
     * @param toSequence Ending sequence (inclusive)
     * @param consumer Consumer to process each event
     * @return Number of events replayed
     * @throws IOException if replay fails
     */
    long replay(long fromSequence, long toSequence, Consumer<Event> consumer) throws IOException;
    
    /**
     * Gets the total number of events stored.
     * 
     * @return Event count
     */
    long getEventCount();
    
    /**
     * Gets the current sequence number (highest stored).
     * 
     * @return Current sequence
     */
    long getCurrentSequence();
    
    /**
     * Clears all stored events (for testing).
     */
    void clear() throws IOException;
    
    @Override
    void close() throws IOException;
}
