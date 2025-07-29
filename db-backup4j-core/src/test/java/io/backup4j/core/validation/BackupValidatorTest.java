package io.backup4j.core.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.io.FileOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BackupValidatorTest {

    @TempDir
    Path tempDir;

    private File validSqlFile;
    private File validGzFile;
    private File emptySqlFile;
    private File invalidGzFile;

    @BeforeEach
    void setUp() throws IOException {
        // 유효한 SQL 파일 생성
        validSqlFile = tempDir.resolve("valid_backup.sql").toFile();
        try (FileWriter writer = new FileWriter(validSqlFile)) {
            writer.write("-- MySQL Database Backup by db-backup4j\n");
            writer.write("-- Generated: 2025-01-28T10:00:00\n");
            writer.write("-- Database: testdb\n\n");
            writer.write("DROP TABLE IF EXISTS `users`;\n");
            writer.write("CREATE TABLE `users` (\n");
            writer.write("  `id` int(11) NOT NULL AUTO_INCREMENT,\n");
            writer.write("  `name` varchar(255) DEFAULT NULL,\n");
            writer.write("  `email` varchar(255) DEFAULT NULL,\n");
            writer.write("  PRIMARY KEY (`id`)\n");
            writer.write(") ENGINE=InnoDB DEFAULT CHARSET=utf8;\n\n");
            writer.write("INSERT INTO `users` VALUES (1,'John','john@example.com');\n");
            writer.write("INSERT INTO `users` VALUES (2,'Jane','jane@example.com');\n");
        }

        // 유효한 압축 파일 생성
        validGzFile = tempDir.resolve("valid_backup.sql.gz").toFile();
        try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(validGzFile))) {
            String content = "-- PostgreSQL Database Backup\nCREATE TABLE test_table (id INTEGER);\nINSERT INTO test_table VALUES (1);";
            gzos.write(content.getBytes());
        }

        // 빈 SQL 파일 생성
        emptySqlFile = tempDir.resolve("empty_backup.sql").toFile();
        assertTrue(emptySqlFile.createNewFile());

        // 잘못된 압축 파일 생성 (실제로는 텍스트 파일)
        invalidGzFile = tempDir.resolve("invalid_backup.sql.gz").toFile();
        try (FileWriter writer = new FileWriter(invalidGzFile)) {
            writer.write("This is not a compressed file");
        }
    }

    @Test
    void validateBackupFile_유효한SQL파일_성공() {
        // When
        BackupValidator.ValidationResult result = BackupValidator.validateBackupFile(validSqlFile);

        // Then
        assertTrue(result.isValid());
        assertEquals(validSqlFile.getAbsolutePath(), result.getFilePath());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validateBackupFile_유효한압축파일_성공() {
        // When
        BackupValidator.ValidationResult result = BackupValidator.validateBackupFile(validGzFile);

        // Then
        assertTrue(result.isValid());
        assertEquals(validGzFile.getAbsolutePath(), result.getFilePath());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validateBackupFile_빈파일_실패() {
        // When
        BackupValidator.ValidationResult result = BackupValidator.validateBackupFile(emptySqlFile);

        // Then
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream()
            .anyMatch(error -> error.contains("empty")));
    }

    @Test
    void validateBackupFile_존재하지않는파일_실패() {
        // Given
        File nonExistentFile = new File("non_existent_file.sql");

        // When
        BackupValidator.ValidationResult result = BackupValidator.validateBackupFile(nonExistentFile);

        // Then
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream()
            .anyMatch(error -> error.contains("does not exist")));
    }

    @Test
    void validateBackupFile_잘못된압축파일_실패() {
        // When
        BackupValidator.ValidationResult result = BackupValidator.validateBackupFile(invalidGzFile);

        // Then
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream()
            .anyMatch(error -> error.toLowerCase().contains("decompress") || 
                              error.toLowerCase().contains("corrupted")));
    }

    @Test
    void validateBackupFile_SQL내용없는파일_경고포함() throws IOException {
        // Given
        File noSqlFile = tempDir.resolve("no_sql_content.sql").toFile();
        try (FileWriter writer = new FileWriter(noSqlFile)) {
            writer.write("This file has no SQL content\n");
            writer.write("Just some text without CREATE TABLE or INSERT\n");
            writer.write("No actual database statements here\n");
        }

        // When
        BackupValidator.ValidationResult result = BackupValidator.validateBackupFile(noSqlFile);

        // Then
        assertTrue(result.isValid()); // 경고만 있고 에러는 없으므로 유효함
        assertTrue(result.getErrors().isEmpty()); // 에러는 없음
        assertFalse(result.getWarnings().isEmpty()); // 경고는 있음
        assertTrue(result.getWarnings().stream()
            .anyMatch(warning -> warning.contains("CREATE TABLE") || warning.contains("DROP")));
    }

    @Test
    void validateBackupFile_ERROR포함파일_실패() throws IOException {
        // Given
        File errorFile = tempDir.resolve("error_backup.sql").toFile();
        try (FileWriter writer = new FileWriter(errorFile)) {
            writer.write("-- Database Backup\n");
            writer.write("CREATE TABLE users (id INT);\n");
            writer.write("ERROR 1062: Duplicate entry\n");
            writer.write("INSERT INTO users VALUES (1);\n");
        }

        // When
        BackupValidator.ValidationResult result = BackupValidator.validateBackupFile(errorFile);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(error -> error.contains("error messages")));
    }

    @Test
    void validateBackupFile_매우작은파일_경고() throws IOException {
        // Given
        File tinyFile = tempDir.resolve("tiny_backup.sql").toFile();
        try (FileWriter writer = new FileWriter(tinyFile)) {
            writer.write("CREATE TABLE t (id INT); INSERT INTO t VALUES (1);"); // 50바이트 정도
        }

        // When
        BackupValidator.ValidationResult result = BackupValidator.validateBackupFile(tinyFile);

        // Then
        // 작은 파일이지만 필수 SQL 구문이 있으므로 유효함
        assertTrue(result.isValid());
        // 하지만 경고가 있을 수 있음
        // (크기에 대한 경고는 100바이트 미만에서 발생)
    }

    @Test
    void validationResult_toString_정상출력() {
        // When
        BackupValidator.ValidationResult result = BackupValidator.validateBackupFile(validSqlFile);

        // Then
        String toString = result.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("ValidationResult"));
        assertTrue(toString.contains("valid=" + result.isValid()));
        assertTrue(toString.contains("errors=" + result.getErrors().size()));
        assertTrue(toString.contains("warnings=" + result.getWarnings().size()));
    }

    @Test
    void validationResult_builder_정상동작() {
        // Given & When
        BackupValidator.ValidationResult result = BackupValidator.ValidationResult.builder()
            .filePath("/test/path")
            .valid(false)
            .addError("Test error")
            .addWarning("Test warning")
            .build();

        // Then
        assertFalse(result.isValid());
        assertEquals("/test/path", result.getFilePath());
        assertEquals(1, result.getErrors().size());
        assertEquals("Test error", result.getErrors().get(0));
        assertEquals(1, result.getWarnings().size());
        assertEquals("Test warning", result.getWarnings().get(0));
        assertTrue(result.hasWarnings());
    }

    @Test
    void validateBackupFile_압축해제후SQL검증_성공() {
        // Given - validGzFile은 PostgreSQL SQL 내용을 포함

        // When
        BackupValidator.ValidationResult result = BackupValidator.validateBackupFile(validGzFile);

        // Then
        assertTrue(result.isValid());
        // 압축 파일 내부의 SQL 내용도 검증되어야 함
    }

    @Test
    void validateBackupFile_디렉토리경로_실패() {
        // Given
        File directory = tempDir.toFile();

        // When
        BackupValidator.ValidationResult result = BackupValidator.validateBackupFile(directory);

        // Then
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(error -> error.contains("not a file")));
    }
}