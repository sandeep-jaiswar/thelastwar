package com.thelastwar.oms.recovery;

/**
 * Result of a recovery operation.
 */
public record RecoveryResult(
    boolean success,
    long ordersFromSnapshot,
    long eventsReplayed,
    long activeOrderCount,
    long snapshotRestoreTimeMs,
    long eventReplayTimeMs,
    long totalRecoveryTimeMs
) {
    /**
     * Checks if recovery meets the performance target of < 15s for 1M orders.
     */
    public boolean meetsPerformanceTarget() {
        // Target: < 15s for 1M orders
        double targetTimePerMillionOrders = 15000.0; // ms
        double actualTimePerMillionOrders = (double) totalRecoveryTimeMs / (activeOrderCount / 1_000_000.0);
        return actualTimePerMillionOrders <= targetTimePerMillionOrders;
    }
    
    @Override
    public String toString() {
        return String.format(
            "RecoveryResult[success=%s, snapshot=%d orders, replayed=%d events, " +
            "active=%d orders, times=(snapshot:%dms, replay:%dms, total:%dms), " +
            "performanceTarget=%s]",
            success, ordersFromSnapshot, eventsReplayed, activeOrderCount,
            snapshotRestoreTimeMs, eventReplayTimeMs, totalRecoveryTimeMs,
            meetsPerformanceTarget() ? "MET" : "NOT MET"
        );
    }
}
