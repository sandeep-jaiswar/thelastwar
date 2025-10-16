-- PostgreSQL Initialization Script for Trading Platform
-- This script creates the necessary tables for trade ledger and order management

-- Create schema for trading operations
CREATE SCHEMA IF NOT EXISTS trading;

-- Set search path
SET search_path TO trading, public;

-- Orders table - stores all order information
CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(50) PRIMARY KEY,
    client_order_id VARCHAR(50) UNIQUE NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_type VARCHAR(20) NOT NULL,
    quantity DECIMAL(20, 8) NOT NULL,
    price DECIMAL(20, 8),
    status VARCHAR(20) NOT NULL,
    trader_id VARCHAR(50) NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    filled_quantity DECIMAL(20, 8) DEFAULT 0,
    average_price DECIMAL(20, 8),
    leaves_quantity DECIMAL(20, 8),
    metadata JSONB
);

-- Executions table - stores trade executions
CREATE TABLE IF NOT EXISTS executions (
    execution_id VARCHAR(50) PRIMARY KEY,
    order_id VARCHAR(50) NOT NULL REFERENCES orders(order_id),
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity DECIMAL(20, 8) NOT NULL,
    price DECIMAL(20, 8) NOT NULL,
    execution_time TIMESTAMP NOT NULL DEFAULT NOW(),
    venue VARCHAR(50),
    trader_id VARCHAR(50) NOT NULL,
    commission DECIMAL(20, 8),
    metadata JSONB
);

-- Positions table - tracks current positions
CREATE TABLE IF NOT EXISTS positions (
    position_id SERIAL PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    quantity DECIMAL(20, 8) NOT NULL DEFAULT 0,
    average_price DECIMAL(20, 8),
    realized_pnl DECIMAL(20, 8) DEFAULT 0,
    unrealized_pnl DECIMAL(20, 8) DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(account_id, symbol)
);

-- Order state snapshots - for event sourcing and recovery
CREATE TABLE IF NOT EXISTS order_snapshots (
    snapshot_id SERIAL PRIMARY KEY,
    order_id VARCHAR(50) NOT NULL,
    snapshot_data JSONB NOT NULL,
    snapshot_time TIMESTAMP NOT NULL DEFAULT NOW(),
    sequence_number BIGINT NOT NULL
);

-- Risk limits table
CREATE TABLE IF NOT EXISTS risk_limits (
    limit_id SERIAL PRIMARY KEY,
    account_id VARCHAR(50) NOT NULL,
    limit_type VARCHAR(50) NOT NULL,
    limit_value DECIMAL(20, 8) NOT NULL,
    current_value DECIMAL(20, 8) DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(account_id, limit_type)
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_orders_symbol ON orders(symbol);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_trader ON orders(trader_id);
CREATE INDEX IF NOT EXISTS idx_orders_account ON orders(account_id);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_executions_order ON executions(order_id);
CREATE INDEX IF NOT EXISTS idx_executions_symbol ON executions(symbol);
CREATE INDEX IF NOT EXISTS idx_executions_time ON executions(execution_time DESC);

CREATE INDEX IF NOT EXISTS idx_positions_account ON positions(account_id);
CREATE INDEX IF NOT EXISTS idx_positions_symbol ON positions(symbol);

CREATE INDEX IF NOT EXISTS idx_snapshots_order ON order_snapshots(order_id);
CREATE INDEX IF NOT EXISTS idx_snapshots_time ON order_snapshots(snapshot_time DESC);

-- Create a function to automatically update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for automatic timestamp updates
CREATE TRIGGER update_orders_updated_at BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_positions_updated_at BEFORE UPDATE ON positions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_risk_limits_updated_at BEFORE UPDATE ON risk_limits
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert some sample data for testing
INSERT INTO risk_limits (account_id, limit_type, limit_value) VALUES
    ('ACC12345', 'MAX_ORDER_SIZE', 10000.00),
    ('ACC12345', 'DAILY_LOSS_LIMIT', 50000.00),
    ('ACC12345', 'POSITION_LIMIT', 100000.00)
ON CONFLICT (account_id, limit_type) DO NOTHING;

-- Grant permissions
GRANT ALL PRIVILEGES ON SCHEMA trading TO trading_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA trading TO trading_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA trading TO trading_user;

-- Success message
DO $$
BEGIN
    RAISE NOTICE 'PostgreSQL initialization completed successfully';
END $$;
