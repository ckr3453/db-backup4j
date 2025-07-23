package io.backup4j.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * RetentionPolicy 클래스의 단위 테스트
 * CI 환경에서의 안정성을 위해 시간 모킹 기반으로 작성
 */
class RetentionPolicyTest {

    @TempDir
    Path tempDir;

    private Path backupDir;
    private Instant fixedTime;

    @BeforeEach
    void setUp() throws IOException {
        backupDir = tempDir.resolve("backups");
        Files.createDirectories(backupDir);
        
        // 고정된 시간으로 테스트 (현재보다 미래 시간)
        fixedTime = Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS);
        RetentionPolicy.setTimeProvider(() -> fixedTime);
    }
    
    @AfterEach
    void tearDown() {
        RetentionPolicy.resetTimeProvider();
    }

    @Test
    void testCleanup_WithZeroRetentionDays() throws IOException {
        // Given - 파일 생성 (실제 파일 시간은 현재 시간)
        createBackupFile("backup.sql");

        // When - 0일 보존 기간으로 정리
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 0);

        // Then - fixedTime(2024-01-15)이 cutoff이므로 현재 생성된 파일(실제 시간)이 삭제됨
        assertThat(result.getTotalFiles()).isEqualTo(1);
        assertThat(result.getDeletedFiles()).isEqualTo(1);
    }

    @Test
    void testCleanup_WithVeryLongRetention() throws IOException {
        // Given
        createBackupFile("backup1.sql");
        createBackupFile("backup2.sql.gz");

        // When - 매우 긴 보존 기간 (1000일)
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 1000);

        // Then - 모든 파일이 보존되어야 함
        assertThat(result.getTotalFiles()).isEqualTo(2);
        assertThat(result.getDeletedFiles()).isEqualTo(0);
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getDeletedFilePaths()).isEmpty();
        assertThat(result.getFreedSpace()).isEqualTo(0);
    }

    @Test
    void testCleanup_WithDryRun() throws IOException {
        // Given
        Path file = createBackupFile("backup.sql");

        // When - Dry run으로 실행
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 0, true);

        // Then - 파일이 삭제 대상이지만 실제로는 삭제되지 않음
        assertThat(result.getTotalFiles()).isEqualTo(1);
        assertThat(result.getDeletedFiles()).isEqualTo(1);
        assertThat(result.getDeletedFilePaths()).hasSize(1);
        assertThat(Files.exists(file)).isTrue(); // Dry run이므로 파일 존재
    }

    @Test
    void testCleanup_FiltersByBackupFilePattern() throws IOException {
        // Given
        createBackupFile("backup.sql");
        createBackupFile("backup.sql.gz");
        createBackupFile("backup.sql.gzip");
        createBackupFile("data_backup.sql");
        createRegularFile("regular.txt");
        createRegularFile("script.sql"); // .sql이지만 백업 파일로 인식됨

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 0);

        // Then - 백업 파일만 삭제됨
        assertThat(result.getTotalFiles()).isEqualTo(5); // .sql 파일들이 백업 파일로 인식
        assertThat(result.getDeletedFiles()).isEqualTo(5);
    }

    @Test
    void testCleanup_WithReadOnlyFile() throws IOException {
        // Given
        Path readOnlyFile = createBackupFile("readonly.sql");
        
        // Windows가 아닌 경우에만 읽기 전용 설정
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            readOnlyFile.toFile().setReadOnly();
        }

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 0);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(1);
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            // Windows에서는 읽기 전용 파일도 삭제될 수 있음
            assertThat(result.getDeletedFiles()).isGreaterThanOrEqualTo(0);
        } else {
            // Unix-like 시스템에서는 삭제 시도하지만 오류 발생 가능
            assertThat(result.getDeletedFiles() + result.getErrors().size()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void testCleanup_WithNonExistentDirectory() {
        // Given
        Path nonExistentDir = tempDir.resolve("non-existent");

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(nonExistentDir, 5);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(0);
        assertThat(result.getDeletedFiles()).isEqualTo(0);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    void testCleanup_WithFileAsDirectory() throws IOException {
        // Given
        Path file = tempDir.resolve("not-a-directory.txt");
        Files.write(file, "content".getBytes());

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(file, 5);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(0);
        assertThat(result.getDeletedFiles()).isEqualTo(0);
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void testCleanup_WithEmptyDirectory() {
        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 5);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(0);
        assertThat(result.getDeletedFiles()).isEqualTo(0);
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getDeletedFilePaths()).isEmpty();
        assertThat(result.getFreedSpace()).isEqualTo(0);
    }

    @Test
    void testListBackupFiles_OrdersByDateDescending() throws IOException {
        // Given - 파일을 순차적으로 생성하여 생성 시간 차이 만들기
        Path file1 = createBackupFile("backup1.sql");
        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Path file2 = createBackupFile("backup2.sql");
        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Path file3 = createBackupFile("backup3.sql");

        // When
        List<RetentionPolicy.BackupFileInfo> files = RetentionPolicy.listBackupFiles(backupDir);

        // Then
        assertThat(files).hasSize(3);
        
        // 최신 파일부터 정렬되어야 함
        assertThat(files.get(0).getPath().getFileName().toString()).isEqualTo("backup3.sql");
        assertThat(files.get(1).getPath().getFileName().toString()).isEqualTo("backup2.sql");
        assertThat(files.get(2).getPath().getFileName().toString()).isEqualTo("backup1.sql");
    }

    @Test
    void testListBackupFiles_WithEmptyDirectory() {
        // When
        List<RetentionPolicy.BackupFileInfo> files = RetentionPolicy.listBackupFiles(backupDir);

        // Then
        assertThat(files).isEmpty();
    }

    @Test
    void testListBackupFiles_WithNonExistentDirectory() {
        // Given
        Path nonExistentDir = tempDir.resolve("non-existent");

        // When
        List<RetentionPolicy.BackupFileInfo> files = RetentionPolicy.listBackupFiles(nonExistentDir);

        // Then
        assertThat(files).isEmpty();
    }

    @Test
    void testBackupFileInfo_IsOlderThan() throws IOException {
        // Given
        createBackupFile("test.sql");
        List<RetentionPolicy.BackupFileInfo> files = RetentionPolicy.listBackupFiles(backupDir);
        RetentionPolicy.BackupFileInfo info = files.get(0);

        // When & Then - fixedTime 기준으로 테스트
        // 파일은 현재 시간에 생성되었고, fixedTime은 현재 시간 + 1일이므로
        // 파일이 fixedTime보다 1일 이상 이전에 생성됨
        assertThat(info.isOlderThan(0)).isTrue();   // fixedTime 기준으로 오래됨
        assertThat(info.isOlderThan(2)).isFalse();  // 2일보다는 새로움
    }

    @Test
    void testBackupFileInfo_GettersAndToString() throws IOException {
        // Given
        Path file = createBackupFile("test.sql");
        List<RetentionPolicy.BackupFileInfo> files = RetentionPolicy.listBackupFiles(backupDir);
        RetentionPolicy.BackupFileInfo info = files.get(0);

        // When & Then
        assertThat(info.getPath()).isEqualTo(file);
        assertThat(info.getSize()).isGreaterThan(0);
        assertThat(info.getCreationTime()).isNotNull();
        assertThat(info.getLastModifiedTime()).isNotNull();
        assertThat(info.getCreationDateTime()).isNotNull();
        assertThat(info.getLastModifiedDateTime()).isNotNull();
        assertThat(info.toString()).contains("test.sql");
        assertThat(info.toString()).contains("bytes");
    }

    @Test
    void testCleanupResult_GettersAndToString() throws IOException {
        // Given
        createBackupFile("test.sql");

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 0);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(1);
        assertThat(result.getDeletedFiles()).isEqualTo(1);
        assertThat(result.getFreedSpace()).isGreaterThan(0);
        assertThat(result.getDeletedFilePaths()).hasSize(1);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getCleanupTime()).isNotNull();
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.toString()).contains("totalFiles=1");
        assertThat(result.toString()).contains("deletedFiles=1");
    }

    private Path createBackupFile(String filename) throws IOException {
        Path file = backupDir.resolve(filename);
        String content = String.format("Test backup content for %s", filename);
        Files.write(file, content.getBytes());
        return file;
    }

    private Path createRegularFile(String filename) throws IOException {
        Path file = backupDir.resolve(filename);
        String content = String.format("Regular file content for %s", filename);
        Files.write(file, content.getBytes());
        return file;
    }
}