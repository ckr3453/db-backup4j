package io.backup4j.core.database;

import io.backup4j.core.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * 데이터베이스 연결 관리 유틸리티 클래스
 * HikariCP 연결 풀을 통해 데이터베이스 연결을 관리합니다.
 */
public class DatabaseConnection {
    private DatabaseConnection() {
    }

    private static final Logger logger = Logger.getLogger(DatabaseConnection.class.getName());
    
    /**
     * 데이터베이스 설정을 기반으로 연결 풀에서 연결을 가져옵니다.
     * 
     * @param config 데이터베이스 연결 설정
     * @return 데이터베이스 연결 객체
     * @throws SQLException 연결 실패 시
     */
    public static Connection getConnection(DatabaseConfig config) throws SQLException {
        return DatabaseConnectionPool.getConnection(config);
    }
    
    /**
     * 연결 풀 상태 정보를 가져옵니다.
     * 
     * @param config 데이터베이스 설정
     * @return 연결 풀 상태 정보
     */
    public static DatabaseConnectionPool.PoolStatus getPoolStatus(DatabaseConfig config) {
        return DatabaseConnectionPool.getPoolStatus(config);
    }
    
    /**
     * 특정 설정에 대한 연결 풀을 종료합니다.
     * 
     * @param config 데이터베이스 설정
     */
    public static void closePool(DatabaseConfig config) {
        DatabaseConnectionPool.closePool(config);
    }
    
    /**
     * 모든 연결 풀을 종료합니다.
     * 애플리케이션 종료 시 호출되어야 합니다.
     */
    public static void closeAllPools() {
        DatabaseConnectionPool.closeAllPools();
    }
    
    /**
     * 데이터베이스 연결을 안전하게 종료합니다.
     * 
     * @param connection 종료할 데이터베이스 연결
     */
    public static void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed successfully");
            } catch (SQLException e) {
                logger.warning("Error closing database connection: " + e.getMessage());
            }
        }
    }
}