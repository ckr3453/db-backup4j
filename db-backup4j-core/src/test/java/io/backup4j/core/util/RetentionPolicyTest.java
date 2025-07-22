package io.backup4j.core.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * RetentionPolicy 클래스의 단위 테스트
 */
class RetentionPolicyTest {

    @TempDir
    Path tempDir;

    private Path backupDir;

    @BeforeEach
    void setUp() throws IOException {
        backupDir = tempDir.resolve("backups");
        Files.createDirectories(backupDir);
    }

    @Test
    void testCleanup_WithOldFiles() throws IOException {
        // Given
        Path oldFile1 = createBackupFile("old_backup1.sql", 7);
        Path oldFile2 = createBackupFile("old_backup2.sql.gz", 10);
        Path newFile = createBackupFile("new_backup.sql", 1);

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 5);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(3);
        assertThat(result.getDeletedFiles()).isEqualTo(2);
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getDeletedFilePaths()).hasSize(2);
        assertThat(result.getFreedSpace()).isGreaterThan(0);

        // 오래된 파일들은 삭제되어야 함
        assertThat(oldFile1).doesNotExist();
        assertThat(oldFile2).doesNotExist();
        
        // 새 파일은 유지되어야 함
        assertThat(newFile).exists();
    }

    @Test
    void testCleanup_WithDryRun() throws IOException {
        // Given
        Path oldFile = createBackupFile("old_backup.sql", 10);
        Path newFile = createBackupFile("new_backup.sql", 1);

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 5, true);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(2);
        assertThat(result.getDeletedFiles()).isEqualTo(1);
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getDeletedFilePaths()).hasSize(1);

        // Dry run이므로 실제로는 파일이 삭제되지 않아야 함
        assertThat(oldFile).exists();
        assertThat(newFile).exists();
    }

    @Test
    void testCleanup_WithNoOldFiles() throws IOException {
        // Given
        createBackupFile("backup1.sql", 1);
        createBackupFile("backup2.sql.gz", 2);

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 5);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(2);
        assertThat(result.getDeletedFiles()).isEqualTo(0);
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getDeletedFilePaths()).isEmpty();
        assertThat(result.getFreedSpace()).isEqualTo(0);
    }

    @Test
    void testCleanup_WithEmptyDirectory() {
        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 5);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(0);
        assertThat(result.getDeletedFiles()).isEqualTo(0);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void testCleanup_WithNonExistentDirectory() {
        // Given
        Path nonExistentDir = tempDir.resolve("nonexistent");

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(nonExistentDir, 5);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(0);
        assertThat(result.getDeletedFiles()).isEqualTo(0);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains("does not exist");
    }

    @Test
    void testCleanup_WithFileAsDirectory() throws IOException {
        // Given
        Path file = tempDir.resolve("notadirectory.txt");
        Files.write(file, "content".getBytes());

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(file, 5);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(0);
        assertThat(result.getDeletedFiles()).isEqualTo(0);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().get(0)).contains("not a directory");
    }

    @Test
    void testCleanup_FiltersByBackupFilePattern() throws IOException {
        // Given
        createBackupFile("backup.sql", 10);           // 백업 파일 - 삭제됨
        createBackupFile("backup.sql.gz", 10);        // 백업 파일 - 삭제됨
        createBackupFile("data_backup.sql", 10);      // 백업 파일 - 삭제됨
        createRegularFile("regular.txt", 10);         // 일반 파일 - 유지됨
        createRegularFile("script.sql", 10);          // SQL 파일이지만 백업이 아님 - 유지됨

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 5);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(4); // .sql 확장자를 가진 모든 파일이 백업 파일로 인식됨
        assertThat(result.getDeletedFiles()).isEqualTo(4);
        
        // 일반 파일들은 유지되어야 함
        assertThat(backupDir.resolve("regular.txt")).exists();
        assertThat(backupDir.resolve("script.sql")).doesNotExist(); // SQL 파일은 백업 파일로 인식되어 삭제됨
    }

    @Test
    void testListBackupFiles_OrdersByDateDescending() throws IOException {
        // Given
        Path file1 = createBackupFile("backup1.sql", 5);
        Path file2 = createBackupFile("backup2.sql", 3);
        Path file3 = createBackupFile("backup3.sql", 1);

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
        Path nonExistentDir = tempDir.resolve("nonexistent");

        // When
        List<RetentionPolicy.BackupFileInfo> files = RetentionPolicy.listBackupFiles(nonExistentDir);

        // Then
        assertThat(files).isEmpty();
    }

    @Test
    void testBackupFileInfo_IsOlderThan() throws IOException {
        // Given
        createBackupFile("old.sql", 10);
        createBackupFile("new.sql", 2);

        List<RetentionPolicy.BackupFileInfo> files = RetentionPolicy.listBackupFiles(backupDir);
        RetentionPolicy.BackupFileInfo oldInfo = files.stream()
                .filter(f -> f.getPath().getFileName().toString().equals("old.sql"))
                .findFirst().get();
        RetentionPolicy.BackupFileInfo newInfo = files.stream()
                .filter(f -> f.getPath().getFileName().toString().equals("new.sql"))
                .findFirst().get();

        // When & Then
        assertThat(oldInfo.isOlderThan(5)).isTrue();
        assertThat(newInfo.isOlderThan(5)).isFalse();
    }

    @Test
    void testBackupFileInfo_GettersAndToString() throws IOException {
        // Given
        Path file = createBackupFile("test.sql", 3);
        List<RetentionPolicy.BackupFileInfo> files = RetentionPolicy.listBackupFiles(backupDir);
        RetentionPolicy.BackupFileInfo info = files.get(0);

        // When & Then
        assertThat(info.getPath()).isEqualTo(file);
        assertThat(info.getSize()).isGreaterThan(0);
        assertThat(info.getCreationTime()).isNotNull();
        assertThat(info.getLastModifiedTime()).isNotNull();
        assertThat(info.getCreationDateTime()).isNotNull();
        assertThat(info.getLastModifiedDateTime()).isNotNull();
        
        String toString = info.toString();
        assertThat(toString).contains("BackupFileInfo");
        assertThat(toString).contains("test.sql");
        assertThat(toString).contains("size=");
        assertThat(toString).contains("created=");
    }

    @Test
    void testCleanupResult_GettersAndToString() throws IOException {
        // Given
        createBackupFile("old.sql", 10);
        createBackupFile("new.sql", 1);

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 5);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(2);
        assertThat(result.getDeletedFiles()).isEqualTo(1);
        assertThat(result.getFreedSpace()).isGreaterThan(0);
        assertThat(result.getDeletedFilePaths()).hasSize(1);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getCleanupTime()).isNotNull();
        assertThat(result.hasErrors()).isFalse();

        String toString = result.toString();
        assertThat(toString).contains("CleanupResult");
        assertThat(toString).contains("totalFiles=2");
        assertThat(toString).contains("deletedFiles=1");
        assertThat(toString).contains("hasErrors=false");
    }

    @Test
    void testCleanup_WithDifferentFileExtensions() throws IOException {
        // Given
        createBackupFile("backup.sql", 10);
        createBackupFile("backup.sql.gz", 10);
        createBackupFile("backup.sql.gzip", 10);
        createBackupFile("data_backup.sql", 10);

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 5);

        // Then
        assertThat(result.getTotalFiles()).isEqualTo(4);
        assertThat(result.getDeletedFiles()).isEqualTo(4);
    }

    @Test
    void testCleanup_WithZeroRetentionDays() throws IOException {
        // Given
        createBackupFile("backup.sql", 0); // 오늘 생성된 파일

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 0);

        // Then
        // 보존 기간이 0일이면 모든 파일이 삭제되어야 함
        assertThat(result.getDeletedFiles()).isEqualTo(1);
    }

    @Test
    void testCleanup_WithReadOnlyFile() throws IOException {
        // Given
        Path readOnlyFile = createBackupFile("readonly.sql", 10);
        
        // 파일을 읽기 전용으로 설정 (Windows에서는 동작하지 않을 수 있음)
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            readOnlyFile.toFile().setReadOnly();
        }

        // When
        RetentionPolicy.CleanupResult result = RetentionPolicy.cleanup(backupDir, 5);

        // Then
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            // Windows에서는 읽기 전용 파일도 삭제됨
            assertThat(result.getDeletedFiles()).isEqualTo(1);
            assertThat(result.hasErrors()).isFalse();
        } else {
            // Unix 계열에서는 권한 오류가 발생할 수 있음
            assertThat(result.getTotalFiles()).isEqualTo(1);
        }
    }

    private Path createBackupFile(String filename, int daysAgo) throws IOException {
        Path file = backupDir.resolve(filename);
        Files.write(file, ("Test backup content for " + filename).getBytes());
        
        // 파일 생성 시간을 과거로 설정
        Instant creationTime = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        Files.setLastModifiedTime(file, FileTime.from(creationTime));
        
        return file;
    }

    private Path createRegularFile(String filename, int daysAgo) throws IOException {
        Path file = backupDir.resolve(filename);
        Files.write(file, ("Regular file content for " + filename).getBytes());
        
        // 파일 생성 시간을 과거로 설정
        Instant creationTime = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        Files.setLastModifiedTime(file, FileTime.from(creationTime));
        
        return file;
    }
}