# Matching Engine Sharding & Load Balancer

## Overview

The sharding system enables horizontal scaling of matching engines by partitioning trading instruments across multiple engine instances. Each shard runs on a dedicated CPU core for optimal cache locality and lock-free operation.

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────┐
│                        ShardManager                          │
│  - Routes orders to appropriate shards                       │
│  - Manages shard lifecycle (add, remove, recover)           │
│  - Aggregates metrics across shards                          │
└───────────────┬─────────────────────────────────────────────┘
                │
    ┌───────────┴───────────┐
    │                       │
┌───▼────────┐      ┌───────▼──────┐
│ShardRouter │      │ShardRegistry │
│(Consistent │      │(Cluster      │
│ Hashing)   │      │ Coordination)│
└───┬────────┘      └───────┬──────┘
    │                       │
    └───────────┬───────────┘
                │
    ┌───────────┴───────────────────────────────┐
    │           │           │           │       │
┌───▼───┐  ┌───▼───┐  ┌───▼───┐  ┌───▼───┐ ┌──▼──┐
│Shard-0│  │Shard-1│  │Shard-2│  │Shard-3│ │ ... │
│CPU 0  │  │CPU 1  │  │CPU 2  │  │CPU 3  │ │     │
└───────┘  └───────┘  └───────┘  └───────┘ └─────┘
```

### Key Classes

- **`ShardManager`**: Orchestrates multiple shards, routes orders, manages lifecycle
- **`Shard`**: Wrapper around a MatchingEngine instance with metrics tracking
- **`ShardRouter`**: Routes instruments to shards using consistent hashing
- **`ShardRegistry`**: Manages shard membership and topology changes
- **`ConsistentHashRouter`**: Implements consistent hashing for even distribution
- **`InMemoryShardRegistry`**: In-memory registry for testing (etcd/ZooKeeper for production)

## Features

### 1. Consistent Hashing

Instruments are distributed across shards using consistent hashing:

- **Even Distribution**: Each shard handles approximately 1/N of the instruments
- **Minimal Reassignment**: When adding/removing shards, only ~1/N instruments need to move
- **Deterministic**: Same instrument always routes to the same shard

### 2. Dynamic Scaling

Shards can be added or removed without stopping the system:

```java
// Add a new shard
ShardId newShard = new ShardId(4);
manager.addShard(newShard, 4, Set.of());

// Remove a shard
manager.removeShard(existingShardId);
```

### 3. Load Distribution Metrics

Track how evenly orders are distributed:

```java
LoadDistribution dist = manager.getLoadDistribution();
System.out.println("Total orders: " + dist.totalOrders());
System.out.println("Balance factor: " + dist.balanceFactor()); // 1.0 = perfect
```

### 4. Shard Recovery

Shards can recover from failures by replaying events:

```java
shard.recover(fromSequence, eventBus);
```

### 5. Per-Shard Metrics

Each shard tracks its own performance:

```java
ShardMetrics metrics = shard.getMetrics();
System.out.println("Orders processed: " + metrics.getOrdersProcessed());
System.out.println("Trades generated: " + metrics.getTradesGenerated());
System.out.println("Recovery time: " + metrics.getRecoveryTimeMs() + "ms");
```

## Usage

### Basic Setup

```java
// Create event bus
EventBus eventBus = new AeronEventBus();
eventBus.start();

// Create registry and router
ShardRegistry registry = new InMemoryShardRegistry();
ShardRouter router = new ConsistentHashRouter();

// Create shard manager
ShardManager manager = new ShardManager(eventBus, registry, router);
manager.start();

// Add shards
for (int i = 0; i < 4; i++) {
    manager.addShard(new ShardId(i), i, Set.of());
}
```

### Processing Orders

Orders are automatically routed to the appropriate shard:

```java
// Submit order - ShardManager routes to correct shard
OrderEvent order = OrderEvent.newOrder(
    orderId, "AAPL", OrderEvent.SIDE_BUY, OrderEvent.TYPE_LIMIT,
    100L, 15000L, accountId, exchangeId
);

Event event = Event.create(
    System.nanoTime(), orderId, SourceId.REST_GATEWAY,
    EventType.ORDER_SUBMITTED, 0L, order
);

eventBus.publish(event);
```

### Monitoring

```java
// Get per-shard metrics
Map<ShardId, ShardMetrics> metrics = manager.getMetrics();
for (Map.Entry<ShardId, ShardMetrics> entry : metrics.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}

// Get load distribution
LoadDistribution dist = manager.getLoadDistribution();
System.out.printf("Load balance: %.2f%% (1.0 = perfect)%n", 
    dist.balanceFactor() * 100);
```

### CPU Affinity (Linux)

Pin shards to CPU cores for optimal performance:

```java
// Shard pinning is specified during creation
manager.addShard(new ShardId(0), 0, Set.of()); // Pin to CPU 0
manager.addShard(new ShardId(1), 1, Set.of()); // Pin to CPU 1
```

For system-level CPU isolation:

```bash
# Isolate CPUs 4-7 for matching engines
echo "isolcpus=4,5,6,7" >> /etc/default/grub
update-grub && reboot

# Run with taskset
taskset -c 4-7 java -jar matching-engine.jar
```

## Performance Targets

### Throughput Scaling

Linear scaling with number of shards:

- 1 shard: 5M orders/sec
- 2 shards: 10M orders/sec
- 4 shards: 20M orders/sec
- 8 shards: 40M orders/sec

### Recovery Time

- **Target**: < 1 second from shard failure to full recovery
- **Achieved**: Recovery time depends on replay log size

### Load Balance

- **Target**: Balance factor > 0.85 (85% of perfect distribution)
- **Achieved**: Consistent hashing provides > 0.90 with 1000+ instruments

## Testing

### Unit Tests

```bash
# Run all shard tests
./gradlew :core:matching:test --tests "com.thelastwar.matching.shard.*"
```

Tests cover:
- Consistent hashing distribution
- Registry operations (add, remove, update)
- Shard lifecycle management
- Metrics aggregation
- Dynamic scaling
- Concurrent access

### Benchmarks

```bash
# Run scaling benchmarks
./gradlew :core:matching:jmh -Pargs="ShardScalingBenchmark"
```

This benchmark tests throughput with 1, 2, 4, and 8 shards to verify linear scaling.

## Production Deployment

### Registry Backend

For production multi-node deployments, implement `ShardRegistry` with:

- **etcd**: Distributed key-value store with strong consistency
- **ZooKeeper**: Coordination service with watches and locks
- **Consul**: Service mesh with health checking

Example etcd implementation:

```java
public class EtcdShardRegistry implements ShardRegistry {
    private final Client etcdClient;
    private final String keyPrefix = "/matching-engine/shards/";
    
    @Override
    public void registerShard(ShardInfo info) {
        String key = keyPrefix + info.shardId().id();
        String value = serializeShardInfo(info);
        etcdClient.getKVClient().put(
            ByteSequence.from(key, StandardCharsets.UTF_8),
            ByteSequence.from(value, StandardCharsets.UTF_8)
        ).join();
    }
    
    // ... other methods
}
```

### High Availability

For HA deployments:

1. **Shard Replication**: Each shard has a hot standby
2. **Leader Election**: Use registry for leader election per shard
3. **Failover**: Standby takes over within 1 second using replay
4. **Split Brain Prevention**: Use quorum-based consensus

### Monitoring

Integrate with your monitoring stack:

```java
// Prometheus metrics
MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

// Expose shard metrics
for (Map.Entry<ShardId, ShardMetrics> entry : manager.getMetrics().entrySet()) {
    Gauge.builder("shard.orders.processed", 
        () -> entry.getValue().getOrdersProcessed())
        .tag("shard", entry.getKey().toString())
        .register(registry);
}
```

## Troubleshooting

### Uneven Load Distribution

**Symptom**: Some shards process significantly more orders than others

**Solutions**:
1. Increase number of virtual nodes in consistent hashing (default: 150)
2. Check if certain instruments have abnormally high volume
3. Manually rebalance by redistributing hot instruments

### Slow Recovery

**Symptom**: Shard recovery takes > 1 second

**Solutions**:
1. Implement incremental snapshots to reduce replay window
2. Use faster storage for replay logs (NVMe SSD)
3. Parallelize replay across multiple threads
4. Tune replay batch size

### High CPU Utilization

**Symptom**: CPUs running at > 90% utilization

**Solutions**:
1. Add more shards to distribute load
2. Check for inefficient order processing patterns
3. Verify CPU affinity is properly configured
4. Profile hot paths for optimization opportunities

## Future Enhancements

- [ ] Automatic rebalancing based on load metrics
- [ ] Cross-shard atomic operations
- [ ] Shard splitting for hot instruments
- [ ] Geographic distribution with latency-aware routing
- [ ] Real-time shard health monitoring
- [ ] Predictive scaling based on historical patterns

## References

- [Consistent Hashing](https://en.wikipedia.org/wiki/Consistent_hashing)
- [CPU Affinity in Linux](https://www.kernel.org/doc/html/latest/admin-guide/cpu-load.html)
- [etcd Documentation](https://etcd.io/docs/)
- [Matching Engine Core Implementation](../MATCHING_ENGINE_CORE_IMPLEMENTATION.md)
