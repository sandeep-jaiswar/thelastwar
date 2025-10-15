package com.thelastwar.oms.snapshot;

import com.thelastwar.oms.persistence.OrderStateRecord;

import java.util.List;

/**
 * Container for snapshot data including metadata and order records.
 */
public record SnapshotData(
    SnapshotMetadata metadata,
    List<OrderStateRecord> orders
) {
    public boolean hasData() {
        return metadata != null && !orders.isEmpty();
    }
}
