package io.backup4j.core.database;

import io.backup4j.core.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 데이터베이스 연결 관리 유틸리티 클래스
 * 직접 JDBC 연결을 통해 데이터베이스 연결을 관리합니다.
 */
public class DatabaseConnection {
    private DatabaseConnection() {
    }

    /**
     * 데이터베이스 설정을 기반으로 직접 연결을 생성합니다.
     * 
     * @param config 데이터베이스 연결 설정 (JDBC URL 포함)
     * @return 데이터베이스 연결 객체
     * @throws SQLException 연결 실패 시
     */
    public static Connection getConnection(DatabaseConfig config) throws SQLException {
        return DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword());
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
            } catch (SQLException e) {
                // 로그 출력 생략
            }
        }
    }
}