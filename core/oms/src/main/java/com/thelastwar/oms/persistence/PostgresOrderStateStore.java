package com.thelastwar.oms.persistence;

import com.thelastwar.oms.OrderState;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PostgreSQL-backed order state store providing durable persistence for OMS.
 * 
 * Features:
 * - Connection pooling via HikariCP
 * - Batch operations for performance
 * - Automatic schema initialization
 * - Thread-safe operations
 * 
 * Performance targets:
 * - Single insert: < 1ms
 * - Batch insert (1000 orders): < 100ms
 * - Query by ID: < 500µs
 */
public class PostgresOrderStateStore implements OrderStateStore {

    private static final Logger logger = LoggerFactory.getLogger(PostgresOrderStateStore.class);

    private final HikariDataSource dataSource;
    private final ConcurrentHashMap<Long, OrderStateRecord> cache;

    public PostgresOrderStateStore(String jdbcUrl, String username, String password) throws SQLException {
        this.cache = new ConcurrentHashMap<>();
        this.dataSource = createDataSource(jdbcUrl, username, password);
        initializeSchema();
    }

    private HikariDataSource createDataSource(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setAutoCommit(false);

        // Performance tuning
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return new HikariDataSource(config);
    }

    private void initializeSchema() throws SQLException {
        String createTableSql = """
                CREATE TABLE IF NOT EXISTS oms_order_state (
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

                CREATE INDEX IF NOT EXISTS idx_client_order_id ON oms_order_state(client_order_id);
                CREATE INDEX IF NOT EXISTS idx_current_state ON oms_order_state(current_state);
                CREATE INDEX IF NOT EXISTS idx_updated_at ON oms_order_state(updated_at);
                """;

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            conn.commit();
            logger.info("Database schema initialized successfully");
        }
    }

    @Override
    public void saveOrderState(OrderStateRecord record) throws SQLException {
        String insertSql = """
                INSERT INTO oms_order_state
                (internal_order_id, client_order_id, symbol, side, order_type, quantity, price,
                 account, current_state, filled_quantity, remaining_quantity, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (internal_order_id)
                DO UPDATE SET
                    current_state = EXCLUDED.current_state,
                    filled_quantity = EXCLUDED.filled_quantity,
                    remaining_quantity = EXCLUDED.remaining_quantity,
                    updated_at = NOW(),
                    version = oms_order_state.version + 1
                WHERE oms_order_state.version = ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(insertSql)) {

            stmt.setLong(1, record.internalOrderId());
            stmt.setString(2, record.clientOrderId());
            stmt.setString(3, record.symbol());
            stmt.setShort(4, record.side());
            stmt.setShort(5, record.orderType());
            stmt.setLong(6, record.quantity());
            stmt.setLong(7, record.price());
            stmt.setLong(8, record.account());
            stmt.setString(9, record.currentState().name());
            stmt.setLong(10, record.filledQuantity());
            stmt.setLong(11, record.remainingQuantity());
            stmt.setLong(12, record.version());
            stmt.setLong(13, record.version() - 1); // For optimistic locking

            int rowsAffected = stmt.executeUpdate();
            conn.commit();

            if (rowsAffected > 0) {
                cache.put(record.internalOrderId(), record);
            }

        } catch (SQLException e) {
            logger.error("Failed to save order state for orderId={}", record.internalOrderId(), e);
            throw e;
        }
    }

    @Override
    public void saveOrderStateBatch(Iterable<OrderStateRecord> records) throws SQLException {
        String insertSql = """
                INSERT INTO oms_order_state
                (internal_order_id, client_order_id, symbol, side, order_type, quantity, price,
                 account, current_state, filled_quantity, remaining_quantity, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (internal_order_id)
                DO UPDATE SET
                    current_state = EXCLUDED.current_state,
                    filled_quantity = EXCLUDED.filled_quantity,
                    remaining_quantity = EXCLUDED.remaining_quantity,
                    updated_at = NOW(),
                    version = oms_order_state.version + 1
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(insertSql)) {

            int batchCount = 0;
            for (OrderStateRecord record : records) {
                stmt.setLong(1, record.internalOrderId());
                stmt.setString(2, record.clientOrderId());
                stmt.setString(3, record.symbol());
                stmt.setShort(4, record.side());
                stmt.setShort(5, record.orderType());
                stmt.setLong(6, record.quantity());
                stmt.setLong(7, record.price());
                stmt.setLong(8, record.account());
                stmt.setString(9, record.currentState().name());
                stmt.setLong(10, record.filledQuantity());
                stmt.setLong(11, record.remainingQuantity());
                stmt.setLong(12, record.version());

                stmt.addBatch();
                batchCount++;

                if (batchCount % 1000 == 0) {
                    stmt.executeBatch();
                    conn.commit();
                }

                cache.put(record.internalOrderId(), record);
            }

            // Execute remaining batch
            if (batchCount % 1000 != 0) {
                stmt.executeBatch();
                conn.commit();
            }

            logger.info("Batch saved {} order state records", batchCount);

        } catch (SQLException e) {
            logger.error("Failed to save order state batch", e);
            throw e;
        }
    }

    @Override
    public OrderStateRecord getOrderState(long internalOrderId) throws SQLException {
        // Check cache first
        OrderStateRecord cached = cache.get(internalOrderId);
        if (cached != null) {
            return cached;
        }

        String selectSql = """
                SELECT internal_order_id, client_order_id, symbol, side, order_type, quantity,
                       price, account, current_state, filled_quantity, remaining_quantity, version
                FROM oms_order_state
                WHERE internal_order_id = ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(selectSql)) {

            stmt.setLong(1, internalOrderId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    OrderStateRecord record = mapResultSetToRecord(rs);
                    cache.put(internalOrderId, record);
                    return record;
                }
            }

            return null;

        } catch (SQLException e) {
            logger.error("Failed to get order state for orderId={}", internalOrderId, e);
            throw e;
        }
    }

    @Override
    public Map<Long, OrderStateRecord> getAllActiveOrders() throws SQLException {
        Map<Long, OrderStateRecord> result = new HashMap<>();

        String selectSql = """
                SELECT internal_order_id, client_order_id, symbol, side, order_type, quantity,
                       price, account, current_state, filled_quantity, remaining_quantity, version
                FROM oms_order_state
                WHERE current_state IN ('NEW', 'ACCEPTED', 'WORKING', 'PARTIAL_FILL')
                ORDER BY internal_order_id
                """;

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(selectSql)) {

            while (rs.next()) {
                OrderStateRecord record = mapResultSetToRecord(rs);
                result.put(record.internalOrderId(), record);
                cache.put(record.internalOrderId(), record);
            }

            logger.info("Loaded {} active orders from database", result.size());
            return result;

        } catch (SQLException e) {
            logger.error("Failed to load active orders", e);
            throw e;
        }
    }

    @Override
    public long countActiveOrders() throws SQLException {
        String countSql = """
                SELECT COUNT(*) as count
                FROM oms_order_state
                WHERE current_state IN ('NEW', 'ACCEPTED', 'WORKING', 'PARTIAL_FILL')
                """;

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(countSql)) {

            if (rs.next()) {
                return rs.getLong("count");
            }
            return 0;

        } catch (SQLException e) {
            logger.error("Failed to count active orders", e);
            throw e;
        }
    }

    private OrderStateRecord mapResultSetToRecord(ResultSet rs) throws SQLException {
        return new OrderStateRecord(
                rs.getLong("internal_order_id"),
                rs.getString("client_order_id"),
                rs.getString("symbol"),
                rs.getShort("side"),
                rs.getShort("order_type"),
                rs.getLong("quantity"),
                rs.getLong("price"),
                rs.getLong("account"),
                OrderState.valueOf(rs.getString("current_state")),
                rs.getLong("filled_quantity"),
                rs.getLong("remaining_quantity"),
                rs.getLong("version"));
    }

    @Override
    public void clearCache() {
        cache.clear();
        logger.info("Order state cache cleared");
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }
}
