package io.backup4j.core.util;

import io.backup4j.core.config.S3BackupConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3Utils 테스트 클래스
 * 실제 S3 연결 없이 로직을 검증합니다.
 */
class S3UtilsTest {

    @TempDir
    Path tempDir;
    
    private S3BackupConfig s3Config;
    private File testFile;

    @BeforeEach
    void setUp() throws IOException {
        // 테스트용 S3 설정 생성
        s3Config = S3BackupConfig.builder()
            .enabled(true)
            .bucket("test-bucket")
            .prefix("backup")
            .region("us-east-1")
            .accessKey("test-access-key")
            .secretKey("test-secret-key")
            .build();
        
        // 테스트용 파일 생성
        testFile = tempDir.resolve("test-backup.sql").toFile();
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write("-- Test backup file\nCREATE TABLE test (id INT);\n");
        }
    }

    @Test
    void uploadFile_존재하지않는파일_예외발생() {
        // Given
        File nonExistentFile = new File("non-existent-file.sql");
        
        // When & Then
        IOException exception = assertThrows(IOException.class, 
            () -> S3Utils.uploadFile(nonExistentFile, s3Config, "test-key"));
        
        assertTrue(exception.getMessage().contains("Backup file not found"));
    }

    @Test
    void uploadFile_유효한파일_네트워크에러() {
        // Given
        String objectKey = "backup/test-backup.sql";
        
        // When & Then
        // 실제 S3가 없으므로 연결 에러가 발생할 것임
        assertThrows(IOException.class, 
            () -> S3Utils.uploadFile(testFile, s3Config, objectKey));
    }

    @Test
    void uploadFile_잘못된인증정보_에러테스트() {
        // Given
        S3BackupConfig invalidConfig = S3BackupConfig.builder()
            .enabled(true)
            .bucket("invalid-bucket")
            .region("invalid-region")
            .accessKey("invalid-key")
            .secretKey("invalid-secret")
            .build();
        
        // When & Then
        assertThrows(IOException.class, 
            () -> S3Utils.uploadFile(testFile, invalidConfig, "test-key"));
    }

    @Test
    void uploadFile_파일크기확인() {
        // Given
        assertTrue(testFile.exists());
        assertTrue(testFile.length() > 0);
        
        // When
        long fileSize = testFile.length();
        
        // Then
        assertTrue(fileSize > 0, "Test file should have content");
    }

    @Test
    void s3Config_설정값검증() {
        // Given & When & Then
        assertTrue(s3Config.isEnabled());
        assertEquals("test-bucket", s3Config.getBucket());
        assertEquals("backup", s3Config.getPrefix());
        assertEquals("us-east-1", s3Config.getRegion());
        assertEquals("test-access-key", s3Config.getAccessKey());
        assertEquals("test-secret-key", s3Config.getSecretKey());
    }
}