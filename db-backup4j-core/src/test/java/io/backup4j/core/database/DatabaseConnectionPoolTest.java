package io.backup4j.core.database;

import io.backup4j.core.config.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatabaseConnectionPool 테스트
 */
@Testcontainers
class DatabaseConnectionPoolTest {

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    private DatabaseConfig config;

    @BeforeEach
    void setUp() {
        config = DatabaseConfig.builder()
                .type(DatabaseType.MYSQL)
                .host(mysqlContainer.getHost())
                .port(mysqlContainer.getFirstMappedPort())
                .name(mysqlContainer.getDatabaseName())
                .username(mysqlContainer.getUsername())
                .password(mysqlContainer.getPassword())
                .build();
    }

    @AfterEach
    void tearDown() {
        DatabaseConnectionPool.closeAllPools();
    }

    @Test
    void getConnection_정상연결_성공() throws SQLException {
        // When
        Connection connection = DatabaseConnectionPool.getConnection(config);

        // Then
        assertNotNull(connection);
        assertFalse(connection.isClosed());
        
        connection.close();
    }

    @Test
    void getConnection_동일설정_동일풀사용() throws SQLException {
        // When
        Connection connection1 = DatabaseConnectionPool.getConnection(config);
        Connection connection2 = DatabaseConnectionPool.getConnection(config);

        // Then
        assertNotNull(connection1);
        assertNotNull(connection2);
        assertNotEquals(connection1, connection2); // 다른 연결 객체여야 함
        
        // 풀 상태 확인
        DatabaseConnectionPool.PoolStatus status = DatabaseConnectionPool.getPoolStatus(config);
        assertTrue(status.isActive());
        assertEquals(2, status.getActiveConnections());
        
        connection1.close();
        connection2.close();
    }

    @Test
    void getPoolStatus_풀상태조회_정확한정보반환() throws SQLException {
        // Given
        Connection connection = DatabaseConnectionPool.getConnection(config);

        // When
        DatabaseConnectionPool.PoolStatus status = DatabaseConnectionPool.getPoolStatus(config);

        // Then
        assertNotNull(status);
        assertTrue(status.isActive());
        assertEquals(1, status.getActiveConnections());
        assertTrue(status.getTotalConnections() >= 1);
        
        connection.close();
    }

    @Test
    void closePool_특정풀종료_풀비활성화() throws SQLException {
        // Given
        Connection connection = DatabaseConnectionPool.getConnection(config);
        assertTrue(DatabaseConnectionPool.getPoolStatus(config).isActive());
        
        connection.close();

        // When
        DatabaseConnectionPool.closePool(config);

        // Then
        DatabaseConnectionPool.PoolStatus status = DatabaseConnectionPool.getPoolStatus(config);
        assertFalse(status.isActive());
    }

    @Test
    void closeAllPools_모든풀종료_모든풀비활성화() throws SQLException {
        // Given
        Connection connection = DatabaseConnectionPool.getConnection(config);
        assertTrue(DatabaseConnectionPool.getPoolStatus(config).isActive());
        
        connection.close();

        // When
        DatabaseConnectionPool.closeAllPools();

        // Then
        DatabaseConnectionPool.PoolStatus status = DatabaseConnectionPool.getPoolStatus(config);
        assertFalse(status.isActive());
    }

    @Test
    void connectionPool_동시접근_성능테스트() throws InterruptedException {
        // Given
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int numberOfTasks = 50;

        // When
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numberOfTasks; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try (Connection connection = DatabaseConnectionPool.getConnection(config)) {
                    // 간단한 쿼리 실행
                    connection.createStatement().executeQuery("SELECT 1").close();
                } catch (SQLException e) {
                    fail("Connection failed: " + e.getMessage());
                }
            }, executor);
            futures.add(future);
        }

        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        // Then
        assertTrue(executionTime < 5000); // 5초 이내 완료
        
        // 풀 상태 확인
        DatabaseConnectionPool.PoolStatus status = DatabaseConnectionPool.getPoolStatus(config);
        assertTrue(status.isActive());
        assertEquals(0, status.getActiveConnections()); // 모든 연결이 반환되었어야 함
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void poolStatus_toString_정상출력() throws SQLException {
        // Given
        Connection connection = DatabaseConnectionPool.getConnection(config);

        // When
        DatabaseConnectionPool.PoolStatus status = DatabaseConnectionPool.getPoolStatus(config);
        String toString = status.toString();

        // Then
        assertNotNull(toString);
        assertTrue(toString.contains("PoolStatus"));
        assertTrue(toString.contains("active=true"));
        assertTrue(toString.contains("active=1")); // 활성 연결 1개
        
        connection.close();
    }

    @Test
    void connectionPool_다른설정_다른풀키생성() throws SQLException {
        // Given - 다른 사용자 이름으로 설정된 두 번째 설정
        DatabaseConfig config2 = DatabaseConfig.builder()
                .type(DatabaseType.MYSQL)
                .host(config.getHost())
                .port(config.getPort())
                .name(config.getName())
                .username("different_user") // 다른 사용자 이름
                .password(config.getPassword())
                .build();

        // When
        Connection connection1 = DatabaseConnectionPool.getConnection(config);

        // Then
        DatabaseConnectionPool.PoolStatus status1 = DatabaseConnectionPool.getPoolStatus(config);
        DatabaseConnectionPool.PoolStatus status2 = DatabaseConnectionPool.getPoolStatus(config2);
        
        assertTrue(status1.isActive());
        assertFalse(status2.isActive()); // 아직 연결되지 않았으므로 비활성
        
        // 다른 풀 키를 가져야 함
        assertNotEquals(status1.getPoolKey(), status2.getPoolKey());
        
        connection1.close();
    }
}