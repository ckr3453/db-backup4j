package io.backup4j.core.database;

import io.backup4j.core.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 데이터베이스 연결 관리 유틸리티 클래스
 * 데이터베이스 타입별로 적절한 JDBC 연결을 생성하고 관리합니다.
 */
public class DatabaseConnection {
    private DatabaseConnection() {
    }

    private static final Logger logger = Logger.getLogger(DatabaseConnection.class.getName());
    
    /**
     * 데이터베이스 설정을 기반으로 JDBC 연결을 생성합니다.
     * 
     * @param config 데이터베이스 연결 설정
     * @return 데이터베이스 연결 객체
     * @throws SQLException 연결 실패 시
     */
    public static Connection getConnection(DatabaseConfig config) throws SQLException {
        String url = buildConnectionUrl(config);
        Properties props = new Properties();
        props.setProperty("user", config.getUsername());
        props.setProperty("password", config.getPassword());
        
        // 연결 타임아웃 설정
        props.setProperty("connectTimeout", "30000");
        props.setProperty("socketTimeout", "30000");
        
        logger.info("Connecting to database: " + config.getType() + " at " + config.getHost() + ":" + config.getPort());
        
        return DriverManager.getConnection(url, props);
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
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", 
                    config.getHost(), config.getPort(), config.getName());
            case POSTGRESQL:
                return String.format("jdbc:postgresql://%s:%d/%s", 
                    config.getHost(), config.getPort(), config.getName());
            default:
                throw new IllegalArgumentException("Unsupported database type: " + config.getType());
        }
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