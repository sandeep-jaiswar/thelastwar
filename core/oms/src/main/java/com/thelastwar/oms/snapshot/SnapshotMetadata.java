package com.thelastwar.oms.snapshot;

import java.time.Instant;

/**
 * Metadata describing a snapshot.
 */
public record SnapshotMetadata(
    String snapshotId,
    long timestamp,
    Instant createdAt,
    long orderCount,
    long lastEventOffset,
    long fileSizeBytes
) {}
