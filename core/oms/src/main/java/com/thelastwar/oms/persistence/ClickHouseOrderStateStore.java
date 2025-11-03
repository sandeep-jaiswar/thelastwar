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
 * ClickHouse-backed order state store providing durable persistence for OMS.
 * 
 * ClickHouse is an OLAP database optimized for high-speed data ingestion and analytics,
 * making it ideal for trading systems that need fast writes and efficient querying.
 * 
 * Features:
 * - Connection pooling via HikariCP
 * - Batch operations for performance
 * - Automatic schema initialization
 * - Thread-safe operations
 * 
 * Performance targets:
 * - Single insert: < 1ms
 * - Batch insert (1000 orders): < 50ms (faster than PostgreSQL)
 * - Query by ID: < 300µs (optimized with proper indexing)
 */
public class ClickHouseOrderStateStore implements OrderStateStore {
    
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseOrderStateStore.class);
    
    private final HikariDataSource dataSource;
    private final ConcurrentHashMap<Long, OrderStateRecord> cache;
    
    public ClickHouseOrderStateStore(String jdbcUrl, String username, String password) throws SQLException {
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
        
        // ClickHouse-specific settings for optimal performance
        config.addDataSourceProperty("socket_timeout", "30000");
        config.addDataSourceProperty("connect_timeout", "10000");
        config.addDataSourceProperty("max_execution_time", "30");
        
        return new HikariDataSource(config);
    }
    
    private void initializeSchema() throws SQLException {
        // ClickHouse uses ReplacingMergeTree for UPSERT-like behavior
        // The version column helps ClickHouse determine which row is the latest
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS oms_order_state (
                internal_order_id Int64,
                client_order_id String,
                symbol String,
                side Int16,
                order_type Int16,
                quantity Int64,
                price Int64,
                account Int64,
                current_state String,
                filled_quantity Int64,
                remaining_quantity Int64,
                created_at DateTime DEFAULT now(),
                updated_at DateTime DEFAULT now(),
                version Int64
            ) ENGINE = ReplacingMergeTree(version)
            ORDER BY internal_order_id
            """;
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            logger.info("ClickHouse schema initialized successfully");
        }
    }
    
    @Override
    public void saveOrderState(OrderStateRecord orderState) throws SQLException {
        // In ClickHouse, we insert a new row and let ReplacingMergeTree handle deduplication
        String insertSql = """
            INSERT INTO oms_order_state 
            (internal_order_id, client_order_id, symbol, side, order_type, quantity, price, 
             account, current_state, filled_quantity, remaining_quantity, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            stmt.setLong(1, orderState.internalOrderId());
            stmt.setString(2, orderState.clientOrderId());
            stmt.setString(3, orderState.symbol());
            stmt.setShort(4, orderState.side());
            stmt.setShort(5, orderState.orderType());
            stmt.setLong(6, orderState.quantity());
            stmt.setLong(7, orderState.price());
            stmt.setLong(8, orderState.account());
            stmt.setString(9, orderState.currentState().name());
            stmt.setLong(10, orderState.filledQuantity());
            stmt.setLong(11, orderState.remainingQuantity());
            stmt.setLong(12, orderState.version());
            
            stmt.executeUpdate();
            cache.put(orderState.internalOrderId(), orderState);
            
        } catch (SQLException e) {
            String errorMsg = String.format("Failed to save order state for orderId=%d", orderState.internalOrderId());
            logger.error(errorMsg, e);
            throw new SQLException(errorMsg, e);
        }
    }
    
    @Override
    public void saveOrderStateBatch(Iterable<OrderStateRecord> records) throws SQLException {
        String insertSql = """
            INSERT INTO oms_order_state 
            (internal_order_id, client_order_id, symbol, side, order_type, quantity, price, 
             account, current_state, filled_quantity, remaining_quantity, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            
            int batchCount = 0;
            for (OrderStateRecord orderState : records) {
                stmt.setLong(1, orderState.internalOrderId());
                stmt.setString(2, orderState.clientOrderId());
                stmt.setString(3, orderState.symbol());
                stmt.setShort(4, orderState.side());
                stmt.setShort(5, orderState.orderType());
                stmt.setLong(6, orderState.quantity());
                stmt.setLong(7, orderState.price());
                stmt.setLong(8, orderState.account());
                stmt.setString(9, orderState.currentState().name());
                stmt.setLong(10, orderState.filledQuantity());
                stmt.setLong(11, orderState.remainingQuantity());
                stmt.setLong(12, orderState.version());
                
                stmt.addBatch();
                batchCount++;
                
                // ClickHouse handles larger batches efficiently
                if (batchCount % 5000 == 0) {
                    stmt.executeBatch();
                }
                
                cache.put(orderState.internalOrderId(), orderState);
            }
            
            // Execute remaining batch
            if (batchCount % 5000 != 0) {
                stmt.executeBatch();
            }
            
            logger.info("Batch saved {} order state records to ClickHouse", batchCount);
            
        } catch (SQLException e) {
            logger.error("Failed to save order state batch", e);
            throw new SQLException("Failed to save order state batch to ClickHouse", e);
        }
    }
    
    @Override
    public OrderStateRecord getOrderState(long internalOrderId) throws SQLException {
        // Check cache first
        OrderStateRecord cached = cache.get(internalOrderId);
        if (cached != null) {
            return cached;
        }
        
        // Use FINAL to get the latest version after ReplacingMergeTree deduplication
        String selectSql = """
            SELECT internal_order_id, client_order_id, symbol, side, order_type, quantity, 
                   price, account, current_state, filled_quantity, remaining_quantity, version
            FROM oms_order_state FINAL
            WHERE internal_order_id = ?
            LIMIT 1
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            
            stmt.setLong(1, internalOrderId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    OrderStateRecord orderState = mapResultSetToRecord(rs);
                    cache.put(internalOrderId, orderState);
                    return orderState;
                }
            }
            
            return null;
            
        } catch (SQLException e) {
            String errorMsg = String.format("Failed to get order state for orderId=%d", internalOrderId);
            logger.error(errorMsg, e);
            throw new SQLException(errorMsg, e);
        }
    }
    
    @Override
    public Map<Long, OrderStateRecord> getAllActiveOrders() throws SQLException {
        Map<Long, OrderStateRecord> result = new HashMap<>();
        
        // Use FINAL to get deduplicated results
        String selectSql = """
            SELECT internal_order_id, client_order_id, symbol, side, order_type, quantity, 
                   price, account, current_state, filled_quantity, remaining_quantity, version
            FROM oms_order_state FINAL
            WHERE current_state IN ('NEW', 'ACCEPTED', 'WORKING', 'PARTIAL_FILL')
            ORDER BY internal_order_id
            """;
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql)) {
            
            while (rs.next()) {
                OrderStateRecord orderState = mapResultSetToRecord(rs);
                result.put(orderState.internalOrderId(), orderState);
                cache.put(orderState.internalOrderId(), orderState);
            }
            
            logger.info("Loaded {} active orders from ClickHouse", result.size());
            return result;
            
        } catch (SQLException e) {
            logger.error("Failed to load active orders from ClickHouse", e);
            throw new SQLException("Failed to load active orders from ClickHouse", e);
        }
    }
    
    @Override
    public long countActiveOrders() throws SQLException {
        String countSql = """
            SELECT COUNT(*) as count
            FROM (
                SELECT internal_order_id
                FROM oms_order_state FINAL
                WHERE current_state IN ('NEW', 'ACCEPTED', 'WORKING', 'PARTIAL_FILL')
            )
            """;
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            
            if (rs.next()) {
                return rs.getLong("count");
            }
            return 0;
            
        } catch (SQLException e) {
            logger.error("Failed to count active orders in ClickHouse", e);
            throw new SQLException("Failed to count active orders in ClickHouse", e);
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
            rs.getLong("version")
        );
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
            logger.info("ClickHouse connection pool closed");
        }
    }
}
