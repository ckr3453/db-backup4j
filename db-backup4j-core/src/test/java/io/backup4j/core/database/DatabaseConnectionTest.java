package io.backup4j.core.database;

import io.backup4j.core.config.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class DatabaseConnectionTest {

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Test
    void getConnection_MySQL_실제연결성공() throws SQLException {
        // given
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                mysqlContainer.getHost(),
                mysqlContainer.getFirstMappedPort(),
                mysqlContainer.getDatabaseName());
        DatabaseConfig config = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username(mysqlContainer.getUsername())
            .password(mysqlContainer.getPassword())
            .build();

        // when
        try (Connection connection = DatabaseConnection.getConnection(config)) {
            // then
            assertNotNull(connection);
            assertFalse(connection.isClosed());
            assertTrue(connection.isValid(5)); // 5초 타임아웃으로 연결 유효성 검사
            
            // 실제 쿼리 실행 테스트
            connection.createStatement().executeQuery("SELECT 1");
        }
    }

    @Test
    void getConnection_PostgreSQL_실제연결성공() throws SQLException {
        // given
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                postgresContainer.getHost(),
                postgresContainer.getFirstMappedPort(),
                postgresContainer.getDatabaseName());
        DatabaseConfig config = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username(postgresContainer.getUsername())
            .password(postgresContainer.getPassword())
            .build();

        // when
        try (Connection connection = DatabaseConnection.getConnection(config)) {
            // then
            assertNotNull(connection);
            assertFalse(connection.isClosed());
            assertTrue(connection.isValid(5));
            
            // 실제 쿼리 실행 테스트
            connection.createStatement().executeQuery("SELECT 1");
        }
    }

    @Test
    void getConnection_MySQL_잘못된자격증명_연결실패() {
        // given
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                mysqlContainer.getHost(),
                mysqlContainer.getFirstMappedPort(),
                mysqlContainer.getDatabaseName());
        DatabaseConfig config = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username("wronguser")
            .password("wrongpass")
            .build();

        // when & then - SQL 예외 발생 확인
        SQLException exception = assertThrows(SQLException.class, () -> {
            DatabaseConnection.getConnection(config);
        });
        
        // MySQL 인증 실패 관련 예외가 발생하는 것만 확인
        assertNotNull(exception);
        assertNotNull(exception.getMessage());
    }

    @Test
    void getConnection_PostgreSQL_잘못된자격증명_연결실패() {
        // given
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                postgresContainer.getHost(),
                postgresContainer.getFirstMappedPort(),
                postgresContainer.getDatabaseName());
        DatabaseConfig config = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username("wronguser")
            .password("wrongpass")
            .build();

        // when & then - SQL 예외 발생 확인
        SQLException exception = assertThrows(SQLException.class, () -> {
            DatabaseConnection.getConnection(config);
        });
        
        // PostgreSQL 인증 실패 관련 예외가 발생하는 것만 확인
        assertNotNull(exception);
        String message = exception.getMessage();
        if (message != null) {
            assertTrue(message.contains("authentication failed") ||
                      message.contains("password authentication failed") ||
                      message.contains("Access denied"));
        }
    }

    @Test
    void getConnection_MySQL_잘못된데이터베이스_연결실패() {
        // given
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                mysqlContainer.getHost(),
                mysqlContainer.getFirstMappedPort(),
                "nonexistentdb");
        DatabaseConfig config = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username(mysqlContainer.getUsername())
            .password(mysqlContainer.getPassword())
            .build();

        // when & then - 존재하지 않는 DB 연결 시 예외 발생
        SQLException exception = assertThrows(SQLException.class, () -> {
            DatabaseConnection.getConnection(config);
        });
        
        // DB 접근 오류 관련 예외가 발생하는 것만 확인
        assertNotNull(exception);
        assertNotNull(exception.getMessage());
    }

    @Test
    void getConnection_PostgreSQL_잘못된데이터베이스_연결실패() {
        // given
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                postgresContainer.getHost(),
                postgresContainer.getFirstMappedPort(),
                "nonexistentdb");
        DatabaseConfig config = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username(postgresContainer.getUsername())
            .password(postgresContainer.getPassword())
            .build();

        // when & then - SQL 예외 발생 확인
        SQLException exception = assertThrows(SQLException.class, () -> {
            DatabaseConnection.getConnection(config);
        });
        
        // DB 존재하지 않음 관련 예외가 발생하는 것만 확인
        assertNotNull(exception);
        String message = exception.getMessage();
        if (message != null) {
            assertTrue(message.contains("does not exist") ||
                      message.contains("database") && message.contains("exist") ||
                      message.contains("Access denied"));
        }
    }

    @Test
    void getConnection_MySQL_연결속성검증() throws SQLException {
        // given
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                mysqlContainer.getHost(),
                mysqlContainer.getFirstMappedPort(),
                mysqlContainer.getDatabaseName());
        DatabaseConfig config = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username(mysqlContainer.getUsername())
            .password(mysqlContainer.getPassword())
            .build();

        // when
        try (Connection connection = DatabaseConnection.getConnection(config)) {
            // then - MySQL 연결 URL 확인
            String url = connection.getMetaData().getURL();
            assertTrue(url.contains("jdbc:mysql://"));
            assertTrue(url.contains(mysqlContainer.getHost()));
            assertTrue(url.contains(String.valueOf(mysqlContainer.getFirstMappedPort())));
            assertTrue(url.contains(mysqlContainer.getDatabaseName()));
            assertTrue(url.contains("useSSL=false"));
            assertTrue(url.contains("serverTimezone=UTC"));
        }
    }

    @Test
    void getConnection_PostgreSQL_연결속성검증() throws SQLException {
        // given
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                postgresContainer.getHost(),
                postgresContainer.getFirstMappedPort(),
                postgresContainer.getDatabaseName());
        DatabaseConfig config = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username(postgresContainer.getUsername())
            .password(postgresContainer.getPassword())
            .build();

        // when
        try (Connection connection = DatabaseConnection.getConnection(config)) {
            // then - PostgreSQL 연결 URL 확인
            String url = connection.getMetaData().getURL();
            assertTrue(url.contains("jdbc:postgresql://"));
            assertTrue(url.contains(postgresContainer.getHost()));
            assertTrue(url.contains(String.valueOf(postgresContainer.getFirstMappedPort())));
            assertTrue(url.contains(postgresContainer.getDatabaseName()));
        }
    }

    @Test
    void getConnection_MySQL_복수연결처리() throws SQLException {
        // given
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                mysqlContainer.getHost(),
                mysqlContainer.getFirstMappedPort(),
                mysqlContainer.getDatabaseName());
        DatabaseConfig config = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username(mysqlContainer.getUsername())
            .password(mysqlContainer.getPassword())
            .build();

        // when - 여러 연결 동시 생성
        try (Connection conn1 = DatabaseConnection.getConnection(config);
             Connection conn2 = DatabaseConnection.getConnection(config);
             Connection conn3 = DatabaseConnection.getConnection(config)) {
            
            // then
            assertNotNull(conn1);
            assertNotNull(conn2);
            assertNotNull(conn3);
            assertTrue(conn1.isValid(5));
            assertTrue(conn2.isValid(5));
            assertTrue(conn3.isValid(5));
            
            // 각각 독립적인 연결인지 확인
            assertNotSame(conn1, conn2);
            assertNotSame(conn2, conn3);
        }
    }
}