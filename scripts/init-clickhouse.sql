-- ClickHouse Initialization Script for Trading Platform
-- This script creates tables optimized for time-series tick data and analytics

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS ticks;

-- Use the ticks database
-- Note: ClickHouse doesn't support USE statement in init scripts, tables will be created with full path

-- Market ticks table - stores all market data ticks
CREATE TABLE IF NOT EXISTS ticks.market_ticks (
    timestamp DateTime64(6) CODEC(Delta, ZSTD),
    symbol String CODEC(ZSTD),
    bid_price Decimal64(8) CODEC(ZSTD),
    ask_price Decimal64(8) CODEC(ZSTD),
    bid_size Decimal64(8) CODEC(ZSTD),
    ask_size Decimal64(8) CODEC(ZSTD),
    last_price Decimal64(8) CODEC(ZSTD),
    last_size Decimal64(8) CODEC(ZSTD),
    volume Decimal64(8) CODEC(ZSTD),
    venue String CODEC(ZSTD)
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(timestamp)
ORDER BY (symbol, timestamp)
TTL timestamp + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;

-- Order events table - stores all order lifecycle events
CREATE TABLE IF NOT EXISTS ticks.order_events (
    event_id String CODEC(ZSTD),
    event_timestamp DateTime64(6) CODEC(Delta, ZSTD),
    order_id String CODEC(ZSTD),
    client_order_id String CODEC(ZSTD),
    event_type String CODEC(ZSTD),
    symbol String CODEC(ZSTD),
    side Enum8('BUY' = 1, 'SELL' = 2) CODEC(ZSTD),
    quantity Decimal64(8) CODEC(ZSTD),
    price Decimal64(8) CODEC(ZSTD),
    status String CODEC(ZSTD),
    trader_id String CODEC(ZSTD),
    venue String CODEC(ZSTD),
    metadata String CODEC(ZSTD)
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(event_timestamp)
ORDER BY (symbol, event_timestamp, order_id)
TTL event_timestamp + INTERVAL 365 DAY
SETTINGS index_granularity = 8192;

-- Execution events table - stores trade executions
CREATE TABLE IF NOT EXISTS ticks.execution_events (
    execution_id String CODEC(ZSTD),
    execution_timestamp DateTime64(6) CODEC(Delta, ZSTD),
    order_id String CODEC(ZSTD),
    symbol String CODEC(ZSTD),
    side Enum8('BUY' = 1, 'SELL' = 2) CODEC(ZSTD),
    quantity Decimal64(8) CODEC(ZSTD),
    price Decimal64(8) CODEC(ZSTD),
    venue String CODEC(ZSTD),
    trader_id String CODEC(ZSTD),
    commission Decimal64(8) CODEC(ZSTD),
    latency_micros UInt64 CODEC(ZSTD)
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(execution_timestamp)
ORDER BY (symbol, execution_timestamp, execution_id)
TTL execution_timestamp + INTERVAL 365 DAY
SETTINGS index_granularity = 8192;

-- Risk events table - stores risk check events
CREATE TABLE IF NOT EXISTS ticks.risk_events (
    event_id String CODEC(ZSTD),
    event_timestamp DateTime64(6) CODEC(Delta, ZSTD),
    order_id String CODEC(ZSTD),
    trader_id String CODEC(ZSTD),
    account_id String CODEC(ZSTD),
    risk_check_type String CODEC(ZSTD),
    check_result Enum8('PASS' = 1, 'FAIL' = 2, 'WARNING' = 3) CODEC(ZSTD),
    check_details String CODEC(ZSTD),
    latency_micros UInt64 CODEC(ZSTD)
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(event_timestamp)
ORDER BY (account_id, event_timestamp)
TTL event_timestamp + INTERVAL 180 DAY
SETTINGS index_granularity = 8192;

-- Performance metrics table - stores system performance metrics
CREATE TABLE IF NOT EXISTS ticks.performance_metrics (
    metric_timestamp DateTime64(6) CODEC(Delta, ZSTD),
    component String CODEC(ZSTD),
    metric_name String CODEC(ZSTD),
    metric_value Float64 CODEC(ZSTD),
    unit String CODEC(ZSTD),
    tags Map(String, String) CODEC(ZSTD)
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(metric_timestamp)
ORDER BY (component, metric_name, metric_timestamp)
TTL metric_timestamp + INTERVAL 30 DAY
SETTINGS index_granularity = 8192;

-- Materialized view for order book snapshots
CREATE MATERIALIZED VIEW IF NOT EXISTS ticks.orderbook_snapshots_mv
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMMDD(timestamp)
ORDER BY (symbol, timestamp)
AS SELECT
    symbol,
    toStartOfMinute(timestamp) as timestamp,
    argMax(bid_price, timestamp) as best_bid,
    argMax(ask_price, timestamp) as best_ask,
    argMax(bid_size, timestamp) as bid_size,
    argMax(ask_size, timestamp) as ask_size,
    sum(volume) as total_volume
FROM ticks.market_ticks
GROUP BY symbol, toStartOfMinute(timestamp);

-- Materialized view for trade statistics by symbol
CREATE MATERIALIZED VIEW IF NOT EXISTS ticks.trade_stats_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMMDD(execution_timestamp)
ORDER BY (symbol, execution_timestamp)
AS SELECT
    symbol,
    toStartOfMinute(execution_timestamp) as execution_timestamp,
    count() as trade_count,
    sum(quantity) as total_quantity,
    avg(price) as average_price,
    min(price) as min_price,
    max(price) as max_price,
    avg(latency_micros) as avg_latency_micros
FROM ticks.execution_events
GROUP BY symbol, toStartOfMinute(execution_timestamp);

-- Materialized view for risk check statistics
CREATE MATERIALIZED VIEW IF NOT EXISTS ticks.risk_stats_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMMDD(event_timestamp)
ORDER BY (account_id, risk_check_type, event_timestamp)
AS SELECT
    account_id,
    risk_check_type,
    toStartOfMinute(event_timestamp) as event_timestamp,
    countIf(check_result = 'PASS') as passed_checks,
    countIf(check_result = 'FAIL') as failed_checks,
    countIf(check_result = 'WARNING') as warning_checks,
    avg(latency_micros) as avg_latency_micros
FROM ticks.risk_events
GROUP BY account_id, risk_check_type, toStartOfMinute(event_timestamp);

-- Insert sample data for testing
INSERT INTO ticks.market_ticks (timestamp, symbol, bid_price, ask_price, bid_size, ask_size, last_price, last_size, volume, venue) VALUES
    (now(), 'AAPL', 150.25, 150.30, 1000, 1500, 150.28, 500, 10000, 'NASDAQ'),
    (now(), 'MSFT', 380.50, 380.55, 800, 1200, 380.52, 300, 8000, 'NASDAQ'),
    (now(), 'GOOGL', 140.75, 140.80, 600, 900, 140.77, 200, 5000, 'NASDAQ');

-- Success message (ClickHouse uses different syntax)
SELECT 'ClickHouse initialization completed successfully' as message;
