package io.backup4j.core.database;

import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.config.DatabaseConfig;
import io.backup4j.core.config.LocalBackupConfig;
import io.backup4j.core.config.S3BackupConfig;
import io.backup4j.core.validation.BackupResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class DatabaseBackupExecutorTest {

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

    @TempDir
    Path tempDir;

    private DatabaseBackupExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DatabaseBackupExecutor();
    }

    @Test
    void executeBackup_MySQL_실제데이터베이스백업() throws Exception {
        // given - MySQL 컨테이너 설정
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            mysqlContainer.getHost(), mysqlContainer.getFirstMappedPort(), mysqlContainer.getDatabaseName());

        DatabaseConfig databaseConfig = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username(mysqlContainer.getUsername())
            .password(mysqlContainer.getPassword())
            .build();

        LocalBackupConfig localConfig = LocalBackupConfig.builder()
            .enabled(true)
            .path(tempDir.toString())
            .retention("30")
            .compress(false)
            .build();

        BackupConfig config = BackupConfig.builder()
            .database(databaseConfig)
            .local(localConfig)
            .s3(S3BackupConfig.builder().enabled(false).build())
            .build();

        // 테스트 데이터 생성
        setupMySQLTestData(databaseConfig);

        // when
        executor.executeBackup(config);

        // then
        File[] backupFiles = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".sql"));
        assertNotNull(backupFiles);
        assertEquals(1, backupFiles.length);

        String content = readFileContent(backupFiles[0].toPath());
        assertTrue(content.contains("-- MySQL Database Backup by db-backup4j"));
        assertTrue(content.contains("-- Database: testdb"));
        assertTrue(content.contains("SET FOREIGN_KEY_CHECKS=0;"));
        assertTrue(content.contains("SET FOREIGN_KEY_CHECKS=1;"));
        assertTrue(content.contains("CREATE TABLE"));
        assertTrue(content.contains("INSERT INTO"));
        assertTrue(content.contains("John"));
        assertTrue(content.contains("Jane"));
    }

    @Test
    void executeBackup_PostgreSQL_실제데이터베이스백업() throws Exception {
        // given - PostgreSQL 컨테이너 설정
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
            postgresContainer.getHost(), postgresContainer.getFirstMappedPort(), postgresContainer.getDatabaseName());

        DatabaseConfig databaseConfig = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username(postgresContainer.getUsername())
            .password(postgresContainer.getPassword())
            .build();

        LocalBackupConfig localConfig = LocalBackupConfig.builder()
            .enabled(true)
            .path(tempDir.toString())
            .retention("30")
            .compress(false)
            .build();

        BackupConfig config = BackupConfig.builder()
            .database(databaseConfig)
            .local(localConfig)
            .s3(S3BackupConfig.builder().enabled(false).build())
            .build();

        // 테스트 데이터 생성
        setupPostgreSQLTestData(databaseConfig);

        // when
        executor.executeBackup(config);

        // then
        File[] backupFiles = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".sql"));
        assertNotNull(backupFiles);
        assertEquals(1, backupFiles.length);

        String content = readFileContent(backupFiles[0].toPath());
        assertTrue(content.contains("-- PostgreSQL Database Backup by db-backup4j"));
        assertTrue(content.contains("-- Schema: public"));
        assertTrue(content.contains("DROP TABLE IF EXISTS"));
        assertTrue(content.contains("INSERT INTO"));
        assertTrue(content.contains("Alice"));
        assertTrue(content.contains("Bob"));
    }

    @Test
    void executeBackup_MySQL_NULL값과특수문자처리() throws Exception {
        // given
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            mysqlContainer.getHost(), mysqlContainer.getFirstMappedPort(), mysqlContainer.getDatabaseName());

        DatabaseConfig databaseConfig = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username(mysqlContainer.getUsername())
            .password(mysqlContainer.getPassword())
            .build();

        LocalBackupConfig localConfig = LocalBackupConfig.builder()
            .enabled(true)
            .path(tempDir.toString())
            .retention("30")
            .compress(false)
            .build();

        BackupConfig config = BackupConfig.builder()
            .database(databaseConfig)
            .local(localConfig)
            .s3(S3BackupConfig.builder().enabled(false).build())
            .build();

        // 특수 데이터 생성
        setupMySQLSpecialData(databaseConfig);

        // when
        executor.executeBackup(config);

        // then
        File[] backupFiles = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".sql"));
        String content = readFileContent(backupFiles[0].toPath());

        // NULL 값 처리 확인
        assertTrue(content.contains("NULL"));
        // 특수문자 이스케이프 확인 (single quote)
        assertTrue(content.contains("John''s"));
    }

    @Test
    void executeBackup_디렉토리생성_성공() throws Exception {
        // given - 존재하지 않는 디렉토리 경로
        Path newDir = tempDir.resolve("new-backup-directory");

        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            mysqlContainer.getHost(), mysqlContainer.getFirstMappedPort(), mysqlContainer.getDatabaseName());

        DatabaseConfig databaseConfig = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username(mysqlContainer.getUsername())
            .password(mysqlContainer.getPassword())
            .build();

        LocalBackupConfig localConfig = LocalBackupConfig.builder()
            .enabled(true)
            .path(newDir.toString())
            .retention("30")
            .compress(false)
            .build();

        BackupConfig config = BackupConfig.builder()
            .database(databaseConfig)
            .local(localConfig)
            .s3(S3BackupConfig.builder().enabled(false).build())
            .build();

        setupMySQLTestData(databaseConfig);

        // when
        executor.executeBackup(config);

        // then
        assertTrue(Files.exists(newDir));
        assertTrue(Files.isDirectory(newDir));

        File[] backupFiles = newDir.toFile().listFiles((dir, name) -> name.endsWith(".sql"));
        assertNotNull(backupFiles);
        assertEquals(1, backupFiles.length);
    }

    @Test
    void executeBackup_잘못된연결정보_예외발생() {
        // given - 잘못된 연결 정보
        DatabaseConfig databaseConfig = DatabaseConfig.builder()
            .url("jdbc:mysql://invalid-host:9999/invalid-db")
            .username("invalid-user")
            .password("invalid-pass")
            .build();

        LocalBackupConfig localConfig = LocalBackupConfig.builder()
            .enabled(true)
            .path(tempDir.toString())
            .retention("30")
            .compress(false)
            .build();

        BackupConfig config = BackupConfig.builder()
            .database(databaseConfig)
            .local(localConfig)
            .s3(S3BackupConfig.builder().enabled(false).build())
            .build();

        // when
        BackupResult result = executor.executeBackup(config);

        // then
        assertNotNull(result);
        assertEquals(BackupResult.Status.FAILED, result.getStatus());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream()
            .anyMatch(error -> error.getMessage().toLowerCase().contains("connection") ||
                              error.getMessage().toLowerCase().contains("database") ||
                              error.getMessage().toLowerCase().contains("host")));
    }

    @Test
    void executeBackup_읽기전용디렉토리_예외발생() throws Exception {
        // given - 읽기 전용 디렉토리 (시뮬레이션을 위해 불가능한 경로 사용)
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            mysqlContainer.getHost(), mysqlContainer.getFirstMappedPort(), mysqlContainer.getDatabaseName());

        DatabaseConfig databaseConfig = DatabaseConfig.builder()
            .url(jdbcUrl)
            .username(mysqlContainer.getUsername())
            .password(mysqlContainer.getPassword())
            .build();

        LocalBackupConfig localConfig = LocalBackupConfig.builder()
            .enabled(true)
            .path("/invalid/path/that/cannot/be/created") // 생성 불가능한 경로
            .retention("30")
            .compress(false)
            .build();

        BackupConfig config = BackupConfig.builder()
            .database(databaseConfig)
            .local(localConfig)
            .s3(S3BackupConfig.builder().enabled(false).build())
            .build();

        // when
        BackupResult result = executor.executeBackup(config);

        // then
        assertNotNull(result);
        assertEquals(BackupResult.Status.FAILED, result.getStatus());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream()
            .anyMatch(error -> error.getMessage().toLowerCase().contains("directory") ||
                              error.getMessage().toLowerCase().contains("permission") ||
                              error.getMessage().toLowerCase().contains("access") ||
                              error.getMessage().toLowerCase().contains("failed to create") ||
                              error.getMessage().toLowerCase().contains("invalid")));
    }

    private void setupMySQLTestData(DatabaseConfig config) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection(config);
             Statement stmt = conn.createStatement()) {

            // 테이블 생성
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                "id INT PRIMARY KEY, " +
                "name VARCHAR(50), " +
                "email VARCHAR(100)" +
                ")");

            // 기존 데이터 삭제
            stmt.execute("DELETE FROM users");

            // 테스트 데이터 삽입
            stmt.execute("INSERT INTO users (id, name, email) VALUES " +
                "(1, 'John', 'john@example.com'), " +
                "(2, 'Jane', 'jane@example.com')");
        }
    }

    private void setupPostgreSQLTestData(DatabaseConfig config) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection(config);
             Statement stmt = conn.createStatement()) {

            // 테이블 생성
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY, " +
                "name VARCHAR(50), " +
                "email VARCHAR(100)" +
                ")");

            // 기존 데이터 삭제
            stmt.execute("DELETE FROM users");

            // 테스트 데이터 삽입
            stmt.execute("INSERT INTO users (id, name, email) VALUES " +
                "(1, 'Alice', 'alice@example.com'), " +
                "(2, 'Bob', 'bob@example.com')");
        }
    }

    private void setupMySQLSpecialData(DatabaseConfig config) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection(config);
             Statement stmt = conn.createStatement()) {

            // 테이블 생성
            stmt.execute("CREATE TABLE IF NOT EXISTS special_data (" +
                "id INT PRIMARY KEY, " +
                "name VARCHAR(50), " +
                "comment TEXT" +
                ")");

            // 기존 데이터 삭제
            stmt.execute("DELETE FROM special_data");

            // 특수 데이터 삽입 (NULL, 특수문자 포함)
            stmt.execute("INSERT INTO special_data (id, name, comment) VALUES " +
                "(1, 'John', 'John''s comment'), " +
                "(2, NULL, 'User with no name'), " +
                "(3, 'Special', 'Text with \"quotes\" and ''apostrophes''')");
        }
    }

    // JDK 8 호환을 위한 파일 읽기 메서드
    private String readFileContent(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath);
        StringBuilder content = new StringBuilder();
        for (String line : lines) {
            content.append(line).append("\n");
        }
        return content.toString();
    }
}