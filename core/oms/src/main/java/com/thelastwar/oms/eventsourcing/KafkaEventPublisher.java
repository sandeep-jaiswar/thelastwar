package com.thelastwar.oms.eventsourcing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.Future;

/**
 * Kafka-based event publisher for OMS events.
 * 
 * Features:
 * - Publishes order events to compacted Kafka topic
 * - Async publishing with futures
 * - JSON serialization
 * - Configurable compression and batching
 * 
 * Topic configuration:
 * - cleanup.policy=compact
 * - retention.ms=infinite (or very long)
 * - compression.type=snappy
 */
public class KafkaEventPublisher implements EventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaEventPublisher.class);
    
    private final String topic;
    private final KafkaProducer<Long, String> producer;
    private final ObjectMapper objectMapper;
    
    public KafkaEventPublisher(String bootstrapServers, String topic) {
        this.topic = topic;
        this.producer = createProducer(bootstrapServers);
        this.objectMapper = createObjectMapper();
        
        logger.info("KafkaEventPublisher initialized for topic: {}", topic);
    }
    
    private KafkaProducer<Long, String> createProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        // Performance tuning
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // Ensure durability
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1); // Small batching window
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768); // 32KB batches
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864); // 64MB buffer
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Retry configuration
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        
        return new KafkaProducer<>(props);
    }
    
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
    
    @Override
    public Future<RecordMetadata> publishEvent(OrderEvent event) {
        try {
            String jsonValue = objectMapper.writeValueAsString(event);
            ProducerRecord<Long, String> record = new ProducerRecord<>(
                topic,
                event.internalOrderId(),
                jsonValue
            );
            
            return producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to publish event for orderId={}", 
                                event.internalOrderId(), exception);
                } else {
                    logger.debug("Published event for orderId={} to partition={} offset={}",
                                event.internalOrderId(), metadata.partition(), metadata.offset());
                }
            });
            
        } catch (Exception e) {
            logger.error("Failed to serialize event for orderId={}", event.internalOrderId(), e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
    
    @Override
    public void flush() {
        producer.flush();
    }
    
    @Override
    public void close() {
        producer.close(Duration.ofSeconds(5));
        logger.info("KafkaEventPublisher closed");
    }
}
