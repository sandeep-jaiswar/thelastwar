# OMS Persistent Storage & Snapshotting

## Overview

This document describes the persistent storage and snapshotting implementation for the Order Management System (OMS). The system provides reliable persistence with fast recovery capabilities through a combination of PostgreSQL, Kafka event sourcing, and periodic snapshots.

## Architecture

### Components

1. **PostgreSQL Store** - Primary persistence layer for order state
2. **Kafka Event Sourcing** - Event log with compacted topics
3. **Snapshot Manager** - Periodic snapshot creation and management
4. **Recovery Service** - Fast state restoration from snapshot + events

### Data Flow

```
Order Submit → OMS Service → PostgreSQL (immediate)
                           ↓
                           → Kafka Events (async)
                           
Periodic Timer → Snapshot Manager → Compressed Snapshot File
                                  ↓
                                  → Cleanup Old Snapshots
                                  
Recovery → Load Snapshot → Restore to PostgreSQL → Replay Kafka Events → Ready
```

## Snapshot Format

### File Structure

Snapshots are stored as gzip-compressed JSON files with the following structure:

#### Filename Format
```
snapshot-<timestamp>.snapshot.gz
```

Example: `snapshot-1729012345678.snapshot.gz`

#### Content Format

Each snapshot file contains:
1. **Metadata Header** (first line): JSON object with snapshot metadata
2. **Order Records** (subsequent lines): One JSON object per line (JSONL format)

#### Metadata Schema

```json
{
  "snapshotId": "snapshot-1729012345678",
  "timestamp": 1729012345678,
  "createdAt": "2024-10-15T17:25:45.678Z",
  "orderCount": 1000000,
  "lastEventOffset": 5234567,
  "fileSizeBytes": 123456789
}
```

Fields:
- `snapshotId`: Unique identifier for the snapshot
- `timestamp`: Epoch milliseconds when snapshot was created
- `createdAt`: ISO 8601 timestamp (human-readable)
- `orderCount`: Number of active orders in snapshot
- `lastEventOffset`: Last Kafka offset included in snapshot
- `fileSizeBytes`: Size of compressed snapshot file

#### Order Record Schema

```json
{
  "internalOrderId": 12345,
  "clientOrderId": "ORDER-ABC-001",
  "symbol": "AAPL",
  "side": 1,
  "orderType": 2,
  "quantity": 1000,
  "price": 150000,
  "account": 9876543,
  "currentState": "WORKING",
  "filledQuantity": 250,
  "remainingQuantity": 750,
  "version": 3
}
```

Fields:
- `internalOrderId`: System-assigned order ID (long)
- `clientOrderId`: Client-assigned order ID (string)
- `symbol`: Trading symbol
- `side`: Order side (1=Buy, 2=Sell)
- `orderType`: Order type (1=Market, 2=Limit, etc.)
- `quantity`: Original order quantity (in lots)
- `price`: Order price (scaled, e.g., cents)
- `account`: Account identifier
- `currentState`: Current order state (NEW, ACCEPTED, WORKING, PARTIAL_FILL, FILLED, CANCELLED, REJECTED, EXPIRED)
- `filledQuantity`: Quantity already filled
- `remainingQuantity`: Quantity remaining to be filled
- `version`: Optimistic locking version

### Compression

Snapshots use GZIP compression with buffer size of 64KB for optimal performance. Typical compression ratio: 5:1 to 10:1 for JSON data.

### Storage Requirements

Approximate storage per 1M orders:
- Uncompressed: ~500 MB
- Compressed: ~50-100 MB (depending on data)

## Retention Policy

### Default Configuration

```java
SnapshotConfig.defaultConfig()
  - Snapshot Interval: 60 minutes
  - Max Snapshots: 24 (24 hours of history)
```

### Retention Rules

1. **Frequency**: Snapshots are created every N minutes (configurable)
2. **Retention Count**: Keep last M snapshots (configurable)
3. **Automatic Cleanup**: Old snapshots are automatically deleted when count exceeds retention limit
4. **Manual Snapshots**: Can be created on-demand without affecting scheduled snapshots

### Configuration Options

```java
// Default: hourly snapshots, keep 24
SnapshotConfig.defaultConfig();

// Testing: frequent snapshots
SnapshotConfig.testConfig(); // 1 minute, keep 5

// Manual only: no periodic snapshots
SnapshotConfig.manualOnly(); // interval=0, keep 10

// Custom configuration
new SnapshotConfig(
  30,  // snapshot every 30 minutes
  48   // keep 48 snapshots (24 hours)
);
```

## Recovery Procedure

### Overview

The recovery process combines snapshot restoration with Kafka event replay to rebuild the complete OMS state.

### Recovery Steps

1. **Load Latest Snapshot**
   - Identify most recent snapshot file
   - Decompress and parse snapshot data
   - Extract last event offset from metadata

2. **Restore to PostgreSQL**
   - Batch insert all order records from snapshot
   - Use batch size of 1000 for optimal performance
   - Clear any existing in-memory caches

3. **Replay Kafka Events**
   - Connect to Kafka with recovery consumer group
   - Seek to offset: `snapshotOffset + 1`
   - Poll and apply all subsequent events
   - Update order states in PostgreSQL

4. **Rebuild In-Memory State**
   - Load active orders into memory
   - Initialize order state machines
   - Restore idempotency indexes

### Recovery API

```java
// Initialize recovery service
RecoveryService recovery = new RecoveryService(
    snapshotManager,
    orderStateStore,
    "localhost:9092",
    "oms-events"
);

// Perform recovery
RecoveryResult result = recovery.recover();

// Check results
System.out.println(result);
// Output: RecoveryResult[success=true, snapshot=1000000 orders, 
//         replayed=5000 events, active=995000 orders, 
//         times=(snapshot:5000ms, replay:3000ms, total:8000ms), 
//         performanceTarget=MET]
```

### Performance Targets

| Operation | Target | Notes |
|-----------|--------|-------|
| Snapshot Creation | < 30s for 1M orders | Includes compression |
| Snapshot Restoration | < 15s for 1M orders | Total recovery time |
| Event Replay | < 100µs per event | Kafka consumer throughput |
| Total Recovery | < 15s for 1M orders | Snapshot + replay combined |

### Error Handling

The recovery process handles errors gracefully:

1. **Missing Snapshot**: Recovery starts from beginning (offset 0)
2. **Corrupt Snapshot**: Skips to previous snapshot
3. **Kafka Unavailable**: Waits and retries with exponential backoff
4. **Database Errors**: Rolls back transaction and retries

### Verification

After recovery, the system verifies:
- Order count matches expectations
- All active orders have valid state
- No data loss compared to last checkpoint
- State machine consistency

## PostgreSQL Schema

### Table: `oms_order_state`

```sql
CREATE TABLE oms_order_state (
    internal_order_id BIGINT PRIMARY KEY,
    client_order_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side SMALLINT NOT NULL,
    order_type SMALLINT NOT NULL,
    quantity BIGINT NOT NULL,
    price BIGINT NOT NULL,
    account BIGINT NOT NULL,
    current_state VARCHAR(32) NOT NULL,
    filled_quantity BIGINT NOT NULL DEFAULT 0,
    remaining_quantity BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 1
);

CREATE INDEX idx_client_order_id ON oms_order_state(client_order_id);
CREATE INDEX idx_current_state ON oms_order_state(current_state);
CREATE INDEX idx_updated_at ON oms_order_state(updated_at);
```

### Connection Pooling

Uses HikariCP with optimized settings:
- Max pool size: 20 connections
- Min idle: 5 connections
- Connection timeout: 30s
- Idle timeout: 10 minutes
- Max lifetime: 30 minutes

## Kafka Configuration

### Topic: `oms-events`

```properties
# Compaction for event sourcing
cleanup.policy=compact

# Retention
retention.ms=-1  # Infinite retention

# Performance
compression.type=snappy
min.compaction.lag.ms=60000
segment.ms=3600000

# Replication
replication.factor=3
min.insync.replicas=2
```

### Producer Settings

```java
acks=all                    // Full durability
compression.type=snappy     // Compression
linger.ms=1                 // Small batching window
batch.size=32768            // 32KB batches
enable.idempotence=true     // Exactly-once semantics
```

## Backup Scheduling

### Automated Backups

1. **Snapshot Backups**: Automatically created every 60 minutes
2. **PostgreSQL Backups**: Use `pg_dump` or continuous archiving
3. **Kafka Backups**: Topic snapshots using MirrorMaker or Kafka Connect

### Backup Verification

Automated tests verify:
- Snapshots are created on schedule
- Snapshots are valid and can be restored
- Recovery produces consistent state
- No data loss under graceful shutdown

### Example Backup Script

```bash
#!/bin/bash
# Backup OMS snapshots to S3

SNAPSHOT_DIR=/var/lib/oms/snapshots
S3_BUCKET=s3://my-bucket/oms-backups
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Upload latest snapshot to S3
aws s3 sync $SNAPSHOT_DIR $S3_BUCKET/snapshots-$TIMESTAMP/

# Backup PostgreSQL
pg_dump -h localhost -U oms_user -d oms_db | \
    gzip > /tmp/oms_db_$TIMESTAMP.sql.gz

aws s3 cp /tmp/oms_db_$TIMESTAMP.sql.gz $S3_BUCKET/db-backups/

echo "Backup completed: $TIMESTAMP"
```

## Testing

### Integration Tests

```java
@Test
void testSnapshotAndRecovery() {
    // Create orders
    for (int i = 0; i < 100000; i++) {
        omsService.submitOrder(...);
    }
    
    // Create snapshot
    SnapshotMetadata snapshot = snapshotManager.createSnapshot(...);
    
    // Simulate restart
    omsService.close();
    orderStateStore.clearCache();
    
    // Recover
    RecoveryResult result = recoveryService.recover();
    
    // Verify
    assertEquals(100000, result.activeOrderCount());
    assertTrue(result.meetsPerformanceTarget());
}
```

### Performance Tests

```java
@Test
void testRecoveryPerformance() {
    // Create 1M orders
    createOrders(1_000_000);
    
    // Create snapshot
    SnapshotMetadata snapshot = snapshotManager.createSnapshot(...);
    
    // Measure recovery time
    long startTime = System.currentTimeMillis();
    RecoveryResult result = recoveryService.recover();
    long duration = System.currentTimeMillis() - startTime;
    
    // Verify target: < 15s for 1M orders
    assertTrue(duration < 15_000, 
        "Recovery took " + duration + "ms, expected < 15000ms");
}
```

## Monitoring

### Metrics

Track the following metrics:
- Snapshot creation time (ms)
- Snapshot size (bytes)
- Recovery time (ms)
- Event replay rate (events/sec)
- PostgreSQL connection pool utilization
- Kafka consumer lag

### Alerts

Configure alerts for:
- Snapshot creation failures
- Recovery time exceeds target
- Database connection pool exhaustion
- Kafka consumer lag > threshold
- Disk space for snapshots < 10%

## Operational Procedures

### Manual Snapshot Creation

```java
SnapshotMetadata snapshot = snapshotManager.createSnapshot(
    new SnapshotProvider() {
        public Map<Long, OrderStateRecord> getActiveOrders() {
            return orderStateStore.getAllActiveOrders();
        }
        
        public long getLastEventOffset() {
            return kafkaOffsetTracker.getLastOffset();
        }
    }
);
```

### Manual Recovery

```bash
# Stop OMS service
systemctl stop oms-service

# Run recovery
java -cp oms.jar com.thelastwar.oms.recovery.RecoveryTool \
    --snapshot-dir /var/lib/oms/snapshots \
    --db-url jdbc:postgresql://localhost:5432/oms \
    --kafka-brokers localhost:9092

# Start OMS service
systemctl start oms-service
```

### Snapshot Cleanup

```bash
# List snapshots
java -cp oms.jar com.thelastwar.oms.snapshot.SnapshotTool \
    --list --snapshot-dir /var/lib/oms/snapshots

# Delete old snapshots
java -cp oms.jar com.thelastwar.oms.snapshot.SnapshotTool \
    --cleanup --keep 24 --snapshot-dir /var/lib/oms/snapshots
```

## Acceptance Criteria Status

✅ **Snapshot restore time < 15s for 1M active orders**
- Implementation uses batch operations and optimized I/O
- Performance tests validate target is met

✅ **No data loss under graceful shutdown**
- PostgreSQL provides ACID guarantees
- Kafka ensures durable event log
- Graceful shutdown flushes all buffers

✅ **Integration tests for snapshot + replay restore produce consistent state**
- Tests verify order counts match
- Tests verify state machine consistency
- Tests verify idempotency after recovery

✅ **Backups scheduled and verified**
- Automated snapshot creation every 60 minutes
- Retention policy maintains 24 snapshots
- Tests verify backup and restore functionality

## References

- [OMS Core Domain & State Machine](../OMS_IMPLEMENTATION_SUMMARY.md)
- [Event Sourcing Pattern](https://martinfowler.com/eaaDev/EventSourcing.html)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [HikariCP Documentation](https://github.com/brettwooldridge/HikariCP)

---

**Document Version:** 1.0  
**Last Updated:** 2024-10-15  
**Author:** OMS Team
