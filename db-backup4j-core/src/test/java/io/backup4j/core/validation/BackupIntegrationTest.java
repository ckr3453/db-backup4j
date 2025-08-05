package io.backup4j.core.validation;

import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.config.DatabaseConfig;
import io.backup4j.core.config.LocalBackupConfig;
import io.backup4j.core.config.S3BackupConfig;
import io.backup4j.core.database.DatabaseBackupExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 백업 파일 검증이 통합된 백업 시스템의 통합 테스트
 */
@Testcontainers
class BackupIntegrationTest {

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @TempDir
    Path tempDir;

    private BackupConfig config;
    private DatabaseBackupExecutor executor;

    @BeforeEach
    void setUp() throws SQLException {
        // 테스트용 백업 설정
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                mysqlContainer.getHost(),
                mysqlContainer.getFirstMappedPort(),
                mysqlContainer.getDatabaseName());
        
        DatabaseConfig databaseConfig = DatabaseConfig.builder()
                .url(jdbcUrl)
                .username(mysqlContainer.getUsername())
                .password(mysqlContainer.getPassword())
                .build();

        LocalBackupConfig localConfig = LocalBackupConfig.builder()
                .enabled(true)
                .path(tempDir.toString())
                .retention("7")
                .compress(false)
                .build();

        config = BackupConfig.builder()
                .database(databaseConfig)
                .local(localConfig)
                .s3(S3BackupConfig.builder().enabled(false).build())
                .build();

        executor = new DatabaseBackupExecutor();
        
        // 테스트 데이터 준비
        setupTestData();
    }

    private void setupTestData() throws SQLException {
        try (Connection conn = io.backup4j.core.database.DatabaseConnection.getConnection(config.getDatabase());
             Statement stmt = conn.createStatement()) {
            
            // 기존 테이블 삭제
            stmt.execute("DROP TABLE IF EXISTS users");
            
            // 테스트 테이블 생성
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100))");
            
            // 테스트 데이터 삽입
            stmt.execute("INSERT INTO users VALUES (1, 'John Doe', 'john@example.com')");
            stmt.execute("INSERT INTO users VALUES (2, 'Jane Smith', 'jane@example.com')");
        }
    }

    @Test
    void executeBackup_정상실행_검증성공() {
        // When
        BackupResult result = executor.executeBackup(config);

        // Then
        assertEquals(BackupResult.Status.SUCCESS, result.getStatus());
        assertFalse(result.hasErrors());
        assertEquals(1, result.getFiles().size());
        assertTrue(result.getDuration().compareTo(Duration.ZERO) > 0);

        // 백업 파일 검증
        BackupResult.BackupFile backupFile = result.getFiles().get(0);
        assertNotNull(backupFile.getFilePath());
        assertTrue(backupFile.getFileSize() > 0);
        assertEquals("local", backupFile.getDestination());
        
        // BackupValidator를 통한 검증 결과 확인
        assertNotNull(backupFile.getValidation());
        assertTrue(backupFile.getValidation().isValid());
        
        // 검증 결과 확인
        if (!result.getValidationResults().isEmpty()) {
            BackupValidator.ValidationResult validation = result.getValidationResults().get(0);
            assertTrue(validation.isValid());
            assertTrue(validation.getErrors().isEmpty());
        }
    }

    @Test
    void executeBackup_압축비활성화_정상백업() {
        // Given
        LocalBackupConfig localConfig = LocalBackupConfig.builder()
                .enabled(true)
                .path(tempDir.toString())
                .retention("7")
                .compress(false)
                .build();

        BackupConfig configWithoutCompression = BackupConfig.builder()
                .database(config.getDatabase())
                .local(localConfig)
                .s3(S3BackupConfig.builder().enabled(false).build())
                .build();

        // When
        BackupResult result = executor.executeBackup(configWithoutCompression);

        // Then
        assertEquals(BackupResult.Status.SUCCESS, result.getStatus());
        assertFalse(result.hasErrors());
        
        BackupResult.BackupFile backupFile = result.getFiles().get(0);
        assertNotNull(backupFile.getValidation());
        assertTrue(backupFile.getValidation().isValid());
        
        // 파일명이 .sql로 끝나는지 확인 (압축되지 않음)
        assertTrue(backupFile.getFilePath().toString().endsWith(".sql"));
    }

    @Test
    void executeBackup_압축활성화_정상백업() {
        // Given
        LocalBackupConfig localConfig = LocalBackupConfig.builder()
                .enabled(true)
                .path(tempDir.toString())
                .retention("7")
                .compress(true)
                .build();

        BackupConfig configWithCompression = BackupConfig.builder()
                .database(config.getDatabase())
                .local(localConfig)
                .s3(S3BackupConfig.builder().enabled(false).build())
                .build();

        // When
        BackupResult result = executor.executeBackup(configWithCompression);

        // Then
        assertEquals(BackupResult.Status.SUCCESS, result.getStatus());
        assertFalse(result.hasErrors());
        
        BackupResult.BackupFile backupFile = result.getFiles().get(0);
        assertNotNull(backupFile.getValidation());
        assertTrue(backupFile.getValidation().isValid());
        
        // 파일명이 .gz로 끝나는지 확인 (압축됨)
        assertTrue(backupFile.getFilePath().toString().endsWith(".gz"));
    }

    @Test
    void executeBackup_백업메타데이터_정상생성() {
        // When
        BackupResult result = executor.executeBackup(config);

        // Then
        assertEquals(BackupResult.Status.SUCCESS, result.getStatus());
        
        BackupResult.BackupMetadata metadata = result.getMetadata();
        assertNotNull(metadata);
        assertEquals("MYSQL", metadata.getDatabaseType());
        assertEquals("testdb", metadata.getDatabaseName());
        assertFalse(metadata.isCompressed());
        assertTrue(metadata.getOriginalSize() > 0);
        assertEquals("SQL", metadata.getBackupFormat());
    }

    @Test
    void executeBackup_검증결과포함_확인() {
        // When
        BackupResult result = executor.executeBackup(config);

        // Then
        assertEquals(BackupResult.Status.SUCCESS, result.getStatus());
        assertFalse(result.hasValidationFailures());
        
        // 모든 검증 결과가 유효한지 확인
        if (!result.getValidationResults().isEmpty()) {
            assertTrue(result.getValidationResults().stream().allMatch(BackupValidator.ValidationResult::isValid));
        }
    }

    @Test
    void executeBackup_파일크기확인() {
        // When
        BackupResult result = executor.executeBackup(config);

        // Then
        assertEquals(BackupResult.Status.SUCCESS, result.getStatus());
        
        // 메타데이터에서 파일 크기 확인
        BackupResult.BackupMetadata metadata = result.getMetadata();
        assertTrue(metadata.getOriginalSize() > 0);
        
        // 백업 파일에서 크기 확인
        BackupResult.BackupFile backupFile = result.getFiles().get(0);
        assertTrue(backupFile.getFileSize() > 0);
        
        // 검증 결과 확인
        assertNotNull(backupFile.getValidation());
        assertTrue(backupFile.getValidation().isValid());
    }
}