package com.thelastwar.oms.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thelastwar.oms.eventsourcing.OrderEvent;
import com.thelastwar.oms.persistence.OrderStateRecord;
import com.thelastwar.oms.persistence.OrderStateStore;
import com.thelastwar.oms.snapshot.SnapshotData;
import com.thelastwar.oms.snapshot.SnapshotManager;
import com.thelastwar.oms.snapshot.SnapshotMetadata;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

/**
 * Service for recovering OMS state from snapshots and event replay.
 * 
 * Recovery process:
 * 1. Load latest snapshot from SnapshotManager
 * 2. Restore snapshot state to OrderStateStore
 * 3. Replay events from Kafka since snapshot
 * 4. Rebuild in-memory state
 * 
 * Performance target:
 * - Recovery time < 15s for 1M orders
 */
public class RecoveryService {
    
    private static final Logger logger = LoggerFactory.getLogger(RecoveryService.class);
    
    private final SnapshotManager snapshotManager;
    private final OrderStateStore orderStateStore;
    private final String kafkaBootstrapServers;
    private final String kafkaTopic;
    private final ObjectMapper objectMapper;
    
    public RecoveryService(SnapshotManager snapshotManager,
                          OrderStateStore orderStateStore,
                          String kafkaBootstrapServers,
                          String kafkaTopic) {
        this.snapshotManager = snapshotManager;
        this.orderStateStore = orderStateStore;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.kafkaTopic = kafkaTopic;
        this.objectMapper = createObjectMapper();
    }
    
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
    
    /**
     * Performs full recovery from snapshot + event replay.
     * 
     * @return recovery result with statistics
     */
    public RecoveryResult recover() throws Exception {
        long startTime = System.nanoTime();
        
        logger.info("Starting OMS state recovery...");
        
        // Step 1: Load snapshot
        logger.info("Loading latest snapshot...");
        long snapshotStartTime = System.nanoTime();
        SnapshotData snapshotData = snapshotManager.restoreLatestSnapshot();
        long snapshotDurationMs = (System.nanoTime() - snapshotStartTime) / 1_000_000;
        
        long lastEventOffset = 0;
        int ordersFromSnapshot = 0;
        
        if (snapshotData.hasData()) {
            SnapshotMetadata metadata = snapshotData.metadata();
            lastEventOffset = metadata.lastEventOffset();
            ordersFromSnapshot = snapshotData.orders().size();
            
            logger.info("Loaded snapshot {} with {} orders (created at: {})",
                       metadata.snapshotId(), ordersFromSnapshot, metadata.createdAt());
            
            // Step 2: Restore snapshot to database
            logger.info("Restoring snapshot to database...");
            long dbRestoreStartTime = System.nanoTime();
            orderStateStore.saveOrderStateBatch(snapshotData.orders());
            long dbRestoreDurationMs = (System.nanoTime() - dbRestoreStartTime) / 1_000_000;
            
            logger.info("Restored {} orders to database in {}ms", 
                       ordersFromSnapshot, dbRestoreDurationMs);
        } else {
            logger.info("No snapshot found, starting from beginning");
        }
        
        // Step 3: Replay events from Kafka
        logger.info("Replaying events from Kafka (offset > {})...", lastEventOffset);
        long replayStartTime = System.nanoTime();
        int eventsReplayed = replayEvents(lastEventOffset);
        long replayDurationMs = (System.nanoTime() - replayStartTime) / 1_000_000;
        
        logger.info("Replayed {} events in {}ms", eventsReplayed, replayDurationMs);
        
        // Step 4: Get final statistics
        long activeOrderCount = orderStateStore.countActiveOrders();
        
        long totalDurationMs = (System.nanoTime() - startTime) / 1_000_000;
        
        RecoveryResult result = new RecoveryResult(
            true,
            ordersFromSnapshot,
            eventsReplayed,
            activeOrderCount,
            snapshotDurationMs,
            replayDurationMs,
            totalDurationMs
        );
        
        logger.info("Recovery completed successfully in {}ms: {} active orders",
                   totalDurationMs, activeOrderCount);
        
        return result;
    }
    
    private int replayEvents(long fromOffset) {
        int eventsReplayed = 0;
        
        try (KafkaConsumer<Long, String> consumer = createConsumer()) {
            // Get all partitions for the topic
            List<TopicPartition> partitions = consumer.partitionsFor(kafkaTopic).stream()
                .map(info -> new TopicPartition(kafkaTopic, info.partition()))
                .toList();
            
            if (partitions.isEmpty()) {
                logger.warn("No partitions found for topic: {}", kafkaTopic);
                return 0;
            }
            
            // Assign partitions
            consumer.assign(partitions);
            
            // Seek to the offset after the snapshot
            for (TopicPartition partition : partitions) {
                consumer.seek(partition, fromOffset + 1);
            }
            
            // Poll for events
            boolean keepPolling = true;
            int emptyPollCount = 0;
            
            while (keepPolling && emptyPollCount < 3) {
                ConsumerRecords<Long, String> records = consumer.poll(Duration.ofSeconds(1));
                
                if (records.isEmpty()) {
                    emptyPollCount++;
                    continue;
                }
                
                emptyPollCount = 0;
                
                for (ConsumerRecord<Long, String> record : records) {
                    try {
                        OrderEvent event = objectMapper.readValue(record.value(), OrderEvent.class);
                        applyEvent(event);
                        eventsReplayed++;
                        
                        if (eventsReplayed % 10000 == 0) {
                            logger.info("Replayed {} events...", eventsReplayed);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to replay event at offset {}", record.offset(), e);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during event replay", e);
        }
        
        return eventsReplayed;
    }
    
    private void applyEvent(OrderEvent event) throws SQLException {
        // Get current state if exists
        OrderStateRecord currentState = orderStateStore.getOrderState(event.internalOrderId());
        
        if (currentState == null && event.eventType() == OrderEvent.EventType.ORDER_SUBMITTED) {
            // New order submission
            OrderStateRecord newRecord = new OrderStateRecord(
                event.internalOrderId(),
                event.clientOrderId(),
                event.symbol(),
                event.side(),
                event.orderType(),
                event.quantity(),
                event.price(),
                event.account(),
                event.newState(),
                event.filledQuantity() != null ? event.filledQuantity() : 0,
                event.remainingQuantity() != null ? event.remainingQuantity() : event.quantity(),
                1
            );
            orderStateStore.saveOrderState(newRecord);
        } else if (currentState != null) {
            // State transition
            OrderStateRecord updatedRecord = new OrderStateRecord(
                currentState.internalOrderId(),
                currentState.clientOrderId(),
                currentState.symbol(),
                currentState.side(),
                currentState.orderType(),
                currentState.quantity(),
                currentState.price(),
                currentState.account(),
                event.newState(),
                event.filledQuantity() != null ? event.filledQuantity() : currentState.filledQuantity(),
                event.remainingQuantity() != null ? event.remainingQuantity() : currentState.remainingQuantity(),
                currentState.version() + 1
            );
            orderStateStore.saveOrderState(updatedRecord);
        }
    }
    
    private KafkaConsumer<Long, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "oms-recovery-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);
        
        return new KafkaConsumer<>(props);
    }
}
