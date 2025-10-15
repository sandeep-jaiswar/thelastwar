package com.thelastwar.oms.eventsourcing;

import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.concurrent.Future;

/**
 * Interface for publishing order events to event log.
 */
public interface EventPublisher extends AutoCloseable {
    
    /**
     * Publishes an order event.
     * 
     * @param event the event to publish
     * @return future for the publish result
     */
    Future<RecordMetadata> publishEvent(OrderEvent event);
    
    /**
     * Flushes any buffered events.
     */
    void flush();
    
    /**
     * Closes the publisher and releases resources.
     */
    @Override
    void close();
}
