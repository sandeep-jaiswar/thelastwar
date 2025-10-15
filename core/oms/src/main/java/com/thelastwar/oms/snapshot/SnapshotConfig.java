package com.thelastwar.oms.snapshot;

/**
 * Configuration for snapshot management.
 */
public record SnapshotConfig(
    int snapshotIntervalMinutes,
    int maxSnapshotsToRetain
) {
    public SnapshotConfig {
        if (maxSnapshotsToRetain < 0) {
            throw new IllegalArgumentException("maxSnapshotsToRetain cannot be negative");
        }
    }
    
    /**
     * Creates a default configuration.
     * - Snapshots every 60 minutes
     * - Retains last 24 snapshots (24 hours worth)
     */
    public static SnapshotConfig defaultConfig() {
        return new SnapshotConfig(60, 24);
    }
    
    /**
     * Creates a configuration for testing with frequent snapshots.
     */
    public static SnapshotConfig testConfig() {
        return new SnapshotConfig(1, 5);
    }
    
    /**
     * Creates a configuration with no periodic snapshots.
     */
    public static SnapshotConfig manualOnly() {
        return new SnapshotConfig(0, 10);
    }
}
