package com.thelastwar.oms.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thelastwar.oms.persistence.OrderStateRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Manages periodic snapshots of OMS order state for fast recovery.
 * 
 * Features:
 * - Periodic snapshot generation
 * - Compressed JSON format
 * - Automatic cleanup based on retention policy
 * - Fast restoration from snapshots
 * 
 * Performance targets:
 * - Snapshot creation: < 30s for 1M orders
 * - Snapshot restoration: < 15s for 1M orders
 */
public class SnapshotManager implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(SnapshotManager.class);
    
    private final Path snapshotDirectory;
    private final SnapshotConfig config;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    
    public SnapshotManager(Path snapshotDirectory, SnapshotConfig config) throws IOException {
        this.snapshotDirectory = snapshotDirectory;
        this.config = config;
        this.objectMapper = createObjectMapper();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SnapshotScheduler");
            t.setDaemon(true);
            return t;
        });
        
        // Create snapshot directory if it doesn't exist
        Files.createDirectories(snapshotDirectory);
        
        logger.info("SnapshotManager initialized with directory: {}", snapshotDirectory);
    }
    
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // Don't use INDENT_OUTPUT for JSONL format
        return mapper;
    }
    
    /**
     * Starts periodic snapshot generation.
     */
    public void startPeriodicSnapshots(SnapshotProvider provider) {
        if (config.snapshotIntervalMinutes() <= 0) {
            logger.info("Periodic snapshots disabled (interval <= 0)");
            return;
        }
        
        scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    createSnapshot(provider);
                } catch (Exception e) {
                    logger.error("Failed to create scheduled snapshot", e);
                }
            },
            config.snapshotIntervalMinutes(),
            config.snapshotIntervalMinutes(),
            TimeUnit.MINUTES
        );
        
        logger.info("Started periodic snapshots every {} minutes", config.snapshotIntervalMinutes());
    }
    
    /**
     * Creates a snapshot of current order state.
     * 
     * @param provider provides the current state to snapshot
     * @return metadata about the created snapshot
     */
    public SnapshotMetadata createSnapshot(SnapshotProvider provider) throws IOException {
        long startTime = System.nanoTime();
        
        // Get current state
        Map<Long, OrderStateRecord> activeOrders = provider.getActiveOrders();
        long snapshotTimestamp = System.currentTimeMillis();
        long lastEventOffset = provider.getLastEventOffset();
        
        // Create snapshot metadata
        SnapshotMetadata metadata = new SnapshotMetadata(
            generateSnapshotId(snapshotTimestamp),
            snapshotTimestamp,
            Instant.now(),
            activeOrders.size(),
            lastEventOffset,
            0 // Will be set after writing
        );
        
        // Write snapshot
        Path snapshotPath = getSnapshotPath(metadata.snapshotId());
        Path tempPath = snapshotPath.resolveSibling(snapshotPath.getFileName() + ".tmp");
        
        try (OutputStream fileOut = Files.newOutputStream(tempPath);
             GZIPOutputStream gzipOut = new GZIPOutputStream(fileOut, 65536);
             BufferedOutputStream bufferedOut = new BufferedOutputStream(gzipOut, 65536)) {
            
            // Write metadata header
            String metadataJson = objectMapper.writeValueAsString(metadata);
            bufferedOut.write(metadataJson.getBytes());
            bufferedOut.write('\n');
            
            // Write order records
            for (OrderStateRecord record : activeOrders.values()) {
                String recordJson = objectMapper.writeValueAsString(record);
                bufferedOut.write(recordJson.getBytes());
                bufferedOut.write('\n');
            }
            
            bufferedOut.flush();
            gzipOut.finish();
        }
        
        // Get file size
        long fileSize = Files.size(tempPath);
        SnapshotMetadata finalMetadata = new SnapshotMetadata(
            metadata.snapshotId(),
            metadata.timestamp(),
            metadata.createdAt(),
            metadata.orderCount(),
            metadata.lastEventOffset(),
            fileSize
        );
        
        // Atomic move to final location
        Files.move(tempPath, snapshotPath, StandardCopyOption.ATOMIC_MOVE);
        
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        logger.info("Created snapshot {} with {} orders in {}ms (size: {} bytes)",
                    metadata.snapshotId(), activeOrders.size(), durationMs, fileSize);
        
        // Cleanup old snapshots
        cleanupOldSnapshots();
        
        return finalMetadata;
    }
    
    /**
     * Restores state from the latest snapshot.
     * 
     * @return snapshot data with metadata and orders
     */
    public SnapshotData restoreLatestSnapshot() throws IOException {
        SnapshotMetadata latestSnapshot = findLatestSnapshot();
        if (latestSnapshot == null) {
            logger.info("No snapshots found");
            return new SnapshotData(null, new ArrayList<>());
        }
        
        return restoreSnapshot(latestSnapshot.snapshotId());
    }
    
    /**
     * Restores state from a specific snapshot.
     * 
     * @param snapshotId the snapshot ID to restore
     * @return snapshot data with metadata and orders
     */
    public SnapshotData restoreSnapshot(String snapshotId) throws IOException {
        long startTime = System.nanoTime();
        
        Path snapshotPath = getSnapshotPath(snapshotId);
        if (!Files.exists(snapshotPath)) {
            throw new IOException("Snapshot not found: " + snapshotId);
        }
        
        SnapshotMetadata metadata = null;
        List<OrderStateRecord> orders = new ArrayList<>();
        
        try (InputStream fileIn = Files.newInputStream(snapshotPath);
             GZIPInputStream gzipIn = new GZIPInputStream(fileIn, 65536);
             BufferedInputStream bufferedIn = new BufferedInputStream(gzipIn, 65536);
             BufferedReader reader = new BufferedReader(new InputStreamReader(bufferedIn))) {
            
            // Read metadata header
            String metadataLine = reader.readLine();
            if (metadataLine != null) {
                metadata = objectMapper.readValue(metadataLine, SnapshotMetadata.class);
            }
            
            // Read order records
            String line;
            while ((line = reader.readLine()) != null) {
                OrderStateRecord record = objectMapper.readValue(line, OrderStateRecord.class);
                orders.add(record);
            }
        }
        
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        logger.info("Restored snapshot {} with {} orders in {}ms",
                    snapshotId, orders.size(), durationMs);
        
        return new SnapshotData(metadata, orders);
    }
    
    /**
     * Finds the latest snapshot metadata.
     */
    public SnapshotMetadata findLatestSnapshot() throws IOException {
        try (Stream<Path> files = Files.list(snapshotDirectory)) {
            return files
                .filter(p -> p.getFileName().toString().endsWith(".snapshot.gz"))
                .map(this::readSnapshotMetadata)
                .filter(m -> m != null)
                .max(Comparator.comparing(SnapshotMetadata::timestamp))
                .orElse(null);
        }
    }
    
    /**
     * Lists all available snapshots.
     */
    public List<SnapshotMetadata> listSnapshots() throws IOException {
        try (Stream<Path> files = Files.list(snapshotDirectory)) {
            return files
                .filter(p -> p.getFileName().toString().endsWith(".snapshot.gz"))
                .map(this::readSnapshotMetadata)
                .filter(m -> m != null)
                .sorted(Comparator.comparing(SnapshotMetadata::timestamp).reversed())
                .toList();
        }
    }
    
    private SnapshotMetadata readSnapshotMetadata(Path snapshotPath) {
        try (InputStream fileIn = Files.newInputStream(snapshotPath);
             GZIPInputStream gzipIn = new GZIPInputStream(fileIn);
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzipIn))) {
            
            String metadataLine = reader.readLine();
            if (metadataLine != null) {
                return objectMapper.readValue(metadataLine, SnapshotMetadata.class);
            }
        } catch (IOException e) {
            logger.warn("Failed to read snapshot metadata from {}", snapshotPath, e);
        }
        return null;
    }
    
    private void cleanupOldSnapshots() throws IOException {
        if (config.maxSnapshotsToRetain() <= 0) {
            return; // No limit
        }
        
        List<SnapshotMetadata> snapshots = listSnapshots();
        if (snapshots.size() <= config.maxSnapshotsToRetain()) {
            return; // Within limit
        }
        
        // Delete oldest snapshots
        int toDelete = snapshots.size() - config.maxSnapshotsToRetain();
        for (int i = snapshots.size() - 1; i >= snapshots.size() - toDelete; i--) {
            SnapshotMetadata snapshot = snapshots.get(i);
            Path snapshotPath = getSnapshotPath(snapshot.snapshotId());
            try {
                Files.deleteIfExists(snapshotPath);
                logger.info("Deleted old snapshot: {}", snapshot.snapshotId());
            } catch (IOException e) {
                logger.warn("Failed to delete old snapshot: {}", snapshot.snapshotId(), e);
            }
        }
    }
    
    private String generateSnapshotId(long timestamp) {
        return String.format("snapshot-%d", timestamp);
    }
    
    private Path getSnapshotPath(String snapshotId) {
        return snapshotDirectory.resolve(snapshotId + ".snapshot.gz");
    }
    
    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("SnapshotManager closed");
    }
    
    /**
     * Provider interface for getting current state to snapshot.
     */
    public interface SnapshotProvider {
        Map<Long, OrderStateRecord> getActiveOrders();
        long getLastEventOffset();
    }
}
