package io.backup4j.core.database;

import io.backup4j.core.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

public class DatabaseConnection {
    private DatabaseConnection() {
    }

    private static final Logger logger = Logger.getLogger(DatabaseConnection.class.getName());
    
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