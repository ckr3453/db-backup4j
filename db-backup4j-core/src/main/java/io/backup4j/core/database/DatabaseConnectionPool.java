package io.backup4j.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.backup4j.core.config.DatabaseConfig;
import io.backup4j.core.util.Constants;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * HikariCP를 사용한 데이터베이스 연결 풀 관리 클래스
 * 각 데이터베이스 설정에 대해 별도의 연결 풀을 생성하고 관리합니다.
 */
public class DatabaseConnectionPool {
    private static final Logger logger = Logger.getLogger(DatabaseConnectionPool.class.getName());
    
    private static final ConcurrentHashMap<String, HikariDataSource> poolCache = new ConcurrentHashMap<>();
    
    private DatabaseConnectionPool() {
    }
    
    /**
     * 데이터베이스 설정을 기반으로 연결 풀에서 연결을 가져옵니다.
     * 
     * @param config 데이터베이스 연결 설정
     * @return 데이터베이스 연결 객체
     * @throws SQLException 연결 실패 시
     */
    public static Connection getConnection(DatabaseConfig config) throws SQLException {
        String poolKey = generatePoolKey(config);
        HikariDataSource dataSource = poolCache.computeIfAbsent(poolKey, key -> createDataSource(config));
        
        logger.info("Getting connection from pool for: " + config.getType() + " at " + config.getHost() + ":" + config.getPort());
        return dataSource.getConnection();
    }
    
    /**
     * HikariCP 데이터소스를 생성합니다.
     * 
     * @param config 데이터베이스 설정
     * @return HikariDataSource 인스턴스
     */
    private static HikariDataSource createDataSource(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        
        // 기본 연결 설정
        hikariConfig.setJdbcUrl(buildConnectionUrl(config));
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        
        // 연결 풀 설정
        hikariConfig.setMinimumIdle(Constants.HIKARI_MINIMUM_IDLE);
        hikariConfig.setMaximumPoolSize(Constants.HIKARI_MAXIMUM_POOL_SIZE);
        hikariConfig.setConnectionTimeout(Constants.HIKARI_CONNECTION_TIMEOUT_MS);
        hikariConfig.setIdleTimeout(Constants.HIKARI_IDLE_TIMEOUT_MS);
        hikariConfig.setMaxLifetime(Constants.HIKARI_MAX_LIFETIME_MS);
        hikariConfig.setLeakDetectionThreshold(Constants.HIKARI_LEAK_DETECTION_THRESHOLD_MS);
        
        // 연결 풀 이름 설정
        String poolName = "db-backup4j-" + config.getType().toString().toLowerCase() + "-" + config.getHost() + "-" + config.getPort();
        hikariConfig.setPoolName(poolName);
        
        // 데이터베이스별 설정
        Properties props = new Properties();
        switch (config.getType()) {
            case MYSQL:
                props.setProperty("cachePrepStmts", "true");
                props.setProperty("prepStmtCacheSize", "250");
                props.setProperty("prepStmtCacheSqlLimit", "2048");
                break;
            case POSTGRESQL:
                props.setProperty("prepareThreshold", "3");
                props.setProperty("preparedStatementCacheQueries", "256");
                props.setProperty("preparedStatementCacheSizeMiB", "5");
                break;
        }
        
        if (!props.isEmpty()) {
            hikariConfig.setDataSourceProperties(props);
        }
        
        // 연결 테스트 쿼리 설정
        switch (config.getType()) {
            case MYSQL:
                hikariConfig.setConnectionTestQuery("SELECT 1");
                break;
            case POSTGRESQL:
                hikariConfig.setConnectionTestQuery("SELECT 1");
                break;
        }
        
        logger.info("Creating new connection pool: " + poolName);
        return new HikariDataSource(hikariConfig);
    }
    
    /**
     * 데이터베이스 타입에 따라 적절한 JDBC URL을 생성합니다.
     * 
     * @param config 데이터베이스 설정
     * @return JDBC 연결 URL
     * @throws IllegalArgumentException 지원하지 않는 데이터베이스 타입인 경우
     */
    private static String buildConnectionUrl(DatabaseConfig config) {
        switch (config.getType()) {
            case MYSQL:
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8", 
                    config.getHost(), config.getPort(), config.getName());
            case POSTGRESQL:
                return String.format("jdbc:postgresql://%s:%d/%s", 
                    config.getHost(), config.getPort(), config.getName());
            default:
                throw new IllegalArgumentException("Unsupported database type: " + config.getType());
        }
    }
    
    /**
     * 연결 풀 캐시 키를 생성합니다.
     * 
     * @param config 데이터베이스 설정
     * @return 캐시 키
     */
    private static String generatePoolKey(DatabaseConfig config) {
        return config.getType() + ":" + config.getHost() + ":" + config.getPort() + ":" + config.getName() + ":" + config.getUsername();
    }
    
    /**
     * 모든 연결 풀을 종료합니다.
     * 애플리케이션 종료 시 호출되어야 합니다.
     */
    public static void closeAllPools() {
        logger.info("Closing all database connection pools");
        poolCache.values().forEach(dataSource -> {
            try {
                dataSource.close();
                logger.info("Closed connection pool: " + dataSource.getPoolName());
            } catch (Exception e) {
                logger.warning("Error closing connection pool " + dataSource.getPoolName() + ": " + e.getMessage());
            }
        });
        poolCache.clear();
    }
    
    /**
     * 특정 설정에 대한 연결 풀을 종료합니다.
     * 
     * @param config 데이터베이스 설정
     */
    public static void closePool(DatabaseConfig config) {
        String poolKey = generatePoolKey(config);
        HikariDataSource dataSource = poolCache.remove(poolKey);
        if (dataSource != null) {
            try {
                dataSource.close();
                logger.info("Closed connection pool for: " + poolKey);
            } catch (Exception e) {
                logger.warning("Error closing connection pool for " + poolKey + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * 연결 풀 상태 정보를 가져옵니다.
     * 
     * @param config 데이터베이스 설정
     * @return 연결 풀 상태 정보
     */
    public static PoolStatus getPoolStatus(DatabaseConfig config) {
        String poolKey = generatePoolKey(config);
        HikariDataSource dataSource = poolCache.get(poolKey);
        
        if (dataSource == null) {
            return new PoolStatus(poolKey, false, 0, 0, 0);
        }
        
        return new PoolStatus(
            poolKey,
            !dataSource.isClosed(),
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections()
        );
    }
    
    /**
     * 연결 풀 상태 정보를 담는 클래스
     */
    public static class PoolStatus {
        private final String poolKey;
        private final boolean active;
        private final int activeConnections;
        private final int idleConnections;
        private final int totalConnections;
        
        public PoolStatus(String poolKey, boolean active, int activeConnections, int idleConnections, int totalConnections) {
            this.poolKey = poolKey;
            this.active = active;
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.totalConnections = totalConnections;
        }
        
        public String getPoolKey() { return poolKey; }
        public boolean isActive() { return active; }
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        public int getTotalConnections() { return totalConnections; }
        
        @Override
        public String toString() {
            return String.format("PoolStatus{poolKey='%s', active=%s, active=%d, idle=%d, total=%d}", 
                poolKey, active, activeConnections, idleConnections, totalConnections);
        }
    }
}