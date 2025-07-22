package io.backup4j.core.validation;

import io.backup4j.core.config.BackupConfig;
import io.backup4j.core.config.DatabaseConfig;
import io.backup4j.core.config.LocalBackupConfig;
import io.backup4j.core.config.NotificationConfig;
import io.backup4j.core.config.S3BackupConfig;
import io.backup4j.core.database.DatabaseBackupExecutor;
import io.backup4j.core.database.DatabaseType;
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
 * 체크섬 검증이 통합된 백업 시스템의 통합 테스트
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
        DatabaseConfig databaseConfig = DatabaseConfig.builder()
                .type(DatabaseType.MYSQL)
                .host(mysqlContainer.getHost())
                .port(mysqlContainer.getFirstMappedPort())
                .name(mysqlContainer.getDatabaseName())
                .username(mysqlContainer.getUsername())
                .password(mysqlContainer.getPassword())
                .build();

        LocalBackupConfig localConfig = LocalBackupConfig.builder()
                .enabled(true)
                .path(tempDir.toString())
                .retention("7")
                .compress(false)
                .enableChecksum(true)
                .checksumAlgorithm("SHA256")
                .build();

        config = BackupConfig.builder()
                .database(databaseConfig)
                .local(localConfig)
                .notification(NotificationConfig.builder().enabled(false).build())
                .s3(S3BackupConfig.builder().enabled(false).build())
                .build();

        executor = new DatabaseBackupExecutor();
        
        // 테스트 데이터 설정
        setupTestData(databaseConfig);
    }
    
    private void setupTestData(DatabaseConfig config) throws SQLException {
        try (Connection conn = io.backup4j.core.database.DatabaseConnection.getConnection(config);
             Statement stmt = conn.createStatement()) {
            
            // 테이블 생성
            stmt.execute("CREATE TABLE IF NOT EXISTS test_users (" +
                "id INT PRIMARY KEY, " +
                "name VARCHAR(50), " +
                "email VARCHAR(100)" +
                ")");
            
            // 테스트 데이터 삽입
            stmt.execute("INSERT INTO test_users VALUES (1, 'John Doe', 'john@example.com')");
            stmt.execute("INSERT INTO test_users VALUES (2, 'Jane Smith', 'jane@example.com')");
        }
    }

    @Test
    void executeBackup_체크섬활성화_성공적인백업결과() {
        // When
        BackupResult result = executor.executeBackup(config);

        // Then
        assertNotNull(result);
        assertNotNull(result.getBackupId());
        assertNotNull(result.getStartTime());
        assertNotNull(result.getEndTime());
        assertNotNull(result.getDuration());
        assertTrue(result.getDuration().compareTo(Duration.ZERO) > 0);

        // 백업 파일 확인
        assertFalse(result.getFiles().isEmpty());
        BackupResult.BackupFile backupFile = result.getFiles().get(0);
        assertNotNull(backupFile.getFilePath());
        assertTrue(backupFile.getFileSize() > 0);
        assertEquals("local", backupFile.getDestination());

        // 체크섬 확인
        if (config.getLocal().isEnableChecksum()) {
            assertNotNull(backupFile.getChecksum());
            assertEquals("SHA256", backupFile.getChecksum().getAlgorithm().toString());
            assertNotNull(backupFile.getChecksum().getChecksum());
            assertEquals(64, backupFile.getChecksum().getChecksum().length()); // SHA256 = 64 hex chars
        }

        // 검증 결과 확인
        if (config.getLocal().isEnableChecksum()) {
            assertFalse(result.getValidationResults().isEmpty());
            ChecksumValidator.ValidationResult validation = result.getValidationResults().get(0);
            assertTrue(validation.isValid());
            assertEquals(ChecksumValidator.ValidationStatus.VALID, validation.getStatus());
        }

        // 메타데이터 확인
        assertNotNull(result.getMetadata());
        assertEquals("MYSQL", result.getMetadata().getDatabaseType());
        assertEquals("testdb", result.getMetadata().getDatabaseName());
        assertEquals(mysqlContainer.getHost(), result.getMetadata().getDatabaseHost());
        assertEquals("SQL", result.getMetadata().getBackupFormat());
    }

    @Test
    void executeBackup_체크섬비활성화_체크섬없는백업() {
        // Given - 체크섬 비활성화
        LocalBackupConfig localConfigNoChecksum = LocalBackupConfig.builder()
                .enabled(true)
                .path(tempDir.toString())
                .retention("7")
                .compress(false)
                .enableChecksum(false)
                .build();

        BackupConfig configNoChecksum = BackupConfig.builder()
                .database(config.getDatabase())
                .local(localConfigNoChecksum)
                .notification(config.getNotification())
                .s3(config.getS3())
                .build();

        // When
        BackupResult result = executor.executeBackup(configNoChecksum);

        // Then
        assertNotNull(result);
        
        // 백업 파일은 있지만 체크섬은 없어야 함
        assertFalse(result.getFiles().isEmpty());
        BackupResult.BackupFile backupFile = result.getFiles().get(0);
        assertNull(backupFile.getChecksum());

        // 검증 결과도 없어야 함
        assertTrue(result.getValidationResults().isEmpty());
    }

    @Test
    void executeBackup_MD5알고리즘_MD5체크섬생성() {
        // Given - MD5 알고리즘 설정
        LocalBackupConfig localConfigMD5 = LocalBackupConfig.builder()
                .enabled(true)
                .path(tempDir.toString())
                .retention("7")
                .compress(false)
                .enableChecksum(true)
                .checksumAlgorithm("MD5")
                .build();

        BackupConfig configMD5 = BackupConfig.builder()
                .database(config.getDatabase())
                .local(localConfigMD5)
                .notification(config.getNotification())
                .s3(config.getS3())
                .build();

        // When
        BackupResult result = executor.executeBackup(configMD5);

        // Then
        assertNotNull(result);
        
        // MD5 체크섬 확인
        assertFalse(result.getFiles().isEmpty());
        BackupResult.BackupFile backupFile = result.getFiles().get(0);
        assertNotNull(backupFile.getChecksum());
        assertEquals("MD5", backupFile.getChecksum().getAlgorithm().toString());
        assertEquals(32, backupFile.getChecksum().getChecksum().length()); // MD5 = 32 hex chars
    }

    @Test
    void backupResult_toString_정상출력() {
        // When
        BackupResult result = executor.executeBackup(config);

        // Then
        String toString = result.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("BackupResult"));
        assertTrue(toString.contains(result.getBackupId()));
        assertTrue(toString.contains(result.getStatus().toString()));
    }

    @Test
    void backupResult_다양한상태메서드_정상동작() {
        // When
        BackupResult result = executor.executeBackup(config);

        // Then
        // 성공적인 백업인 경우
        if (result.getStatus() == BackupResult.Status.SUCCESS) {
            assertTrue(result.isSuccess());
            assertFalse(result.hasErrors());
        }

        // 검증 실패가 없는 경우
        if (result.getValidationResults().stream().allMatch(ChecksumValidator.ValidationResult::isValid)) {
            assertFalse(result.hasValidationFailures());
        }
    }

    @Test
    void backupFile_toString_정상출력() {
        // When
        BackupResult result = executor.executeBackup(config);

        // Then
        assertFalse(result.getFiles().isEmpty());
        BackupResult.BackupFile backupFile = result.getFiles().get(0);
        String toString = backupFile.toString();
        
        assertNotNull(toString);
        assertTrue(toString.contains("BackupFile"));
        assertTrue(toString.contains("local"));
        assertTrue(toString.contains(String.valueOf(backupFile.getFileSize())));
    }

    @Test
    void backupMetadata_압축률계산_정상동작() {
        // When
        BackupResult result = executor.executeBackup(config);

        // Then
        BackupResult.BackupMetadata metadata = result.getMetadata();
        assertNotNull(metadata);
        
        // 압축하지 않은 경우 압축률은 1.0
        if (!metadata.isCompressed()) {
            assertEquals(1.0, metadata.getCompressionRatio(), 0.01);
        }
        
        String toString = metadata.toString();
        assertTrue(toString.contains("BackupMetadata"));
        assertTrue(toString.contains("MYSQL"));
        assertTrue(toString.contains("test_db"));
    }

    @Test
    void 체크섬계산_성능측정_시간기록() {
        // When
        BackupResult result = executor.executeBackup(config);

        // Then
        if (config.getLocal().isEnableChecksum()) {
            assertFalse(result.getValidationResults().isEmpty());
            ChecksumValidator.ValidationResult validation = result.getValidationResults().get(0);
            
            assertTrue(validation.getValidationTimeMs() >= 0);
            assertNotNull(validation.getValidatedAt());
        }
    }
}