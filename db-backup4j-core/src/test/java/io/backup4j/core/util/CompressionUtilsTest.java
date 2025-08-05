package io.backup4j.core.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * CompressionUtils 클래스의 단위 테스트
 */
class CompressionUtilsTest {

    @TempDir
    Path tempDir;

    private Path sourceFile;
    private Path compressedFile;
    private Path decompressedFile;

    @BeforeEach
    void setUp() throws IOException {
        sourceFile = tempDir.resolve("test.sql");
        compressedFile = tempDir.resolve("test.sql.gz");
        decompressedFile = tempDir.resolve("test_decompressed.sql");
    }

    @Test
    void testCompressFile_WithTextContent() throws IOException {
        // Given
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            contentBuilder.append("CREATE TABLE users (id INT, name VARCHAR(50));\n");
        }
        String content = contentBuilder.toString();
        Files.write(sourceFile, content.getBytes());

        // When
        CompressionUtils.compressFile(sourceFile, compressedFile);

        // Then
        assertThat(compressedFile).exists();
        assertThat(Files.size(compressedFile)).isLessThan(Files.size(sourceFile));
        assertThat(CompressionUtils.isCompressed(compressedFile)).isTrue();
    }

    @Test
    void testCompressFile_WithEmptyFile() throws IOException {
        // Given
        Files.write(sourceFile, new byte[0]);

        // When
        CompressionUtils.compressFile(sourceFile, compressedFile);

        // Then
        assertThat(compressedFile).exists();
        assertThat(Files.size(compressedFile)).isGreaterThan(0); // GZIP header exists
    }

    @Test
    void testCompressFile_WithLargeFile() throws IOException {
        // Given
        byte[] data = new byte[1024 * 1024]; // 1MB
        Arrays.fill(data, (byte) 'A');
        Files.write(sourceFile, data);

        // When
        CompressionUtils.compressFile(sourceFile, compressedFile);

        // Then
        assertThat(compressedFile).exists();
        assertThat(Files.size(compressedFile)).isLessThan(Files.size(sourceFile));
        
        // 동일한 데이터이므로 압축률이 매우 좋아야 함
        double ratio = CompressionUtils.calculateCompressionRatio(sourceFile, compressedFile);
        assertThat(ratio).isLessThan(0.01); // 1% 미만으로 압축되어야 함
    }

    @Test
    void testDecompressFile_ValidGzipFile() throws IOException {
        // Given
        StringBuilder originalContentBuilder = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            originalContentBuilder.append("SELECT * FROM users WHERE name = 'test';\n");
        }
        String originalContent = originalContentBuilder.toString();
        Files.write(sourceFile, originalContent.getBytes());
        CompressionUtils.compressFile(sourceFile, compressedFile);

        // When
        CompressionUtils.decompressFile(compressedFile, decompressedFile);

        // Then
        assertThat(decompressedFile).exists();
        String decompressedContent = new String(Files.readAllBytes(decompressedFile));
        assertThat(decompressedContent).isEqualTo(originalContent);
        assertThat(Files.size(decompressedFile)).isEqualTo(Files.size(sourceFile));
    }

    @Test
    void testDecompressFile_InvalidGzipFile() throws IOException {
        // Given - 유효하지 않은 GZIP 파일
        Files.write(compressedFile, "This is not a gzip file".getBytes());

        // When & Then
        assertThatThrownBy(() -> CompressionUtils.decompressFile(compressedFile, decompressedFile))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testCalculateCompressionRatio_NormalFile() throws IOException {
        // Given
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            contentBuilder.append("INSERT INTO table VALUES (1, 'data');\n");
        }
        String content = contentBuilder.toString();
        Files.write(sourceFile, content.getBytes());
        CompressionUtils.compressFile(sourceFile, compressedFile);

        // When
        double ratio = CompressionUtils.calculateCompressionRatio(sourceFile, compressedFile);

        // Then
        assertThat(ratio).isBetween(0.0, 1.0);
        assertThat(ratio).isLessThan(1.0); // 압축이 되어야 함
    }

    @Test
    void testCalculateCompressionRatio_EmptyFile() throws IOException {
        // Given
        Files.write(sourceFile, new byte[0]);
        CompressionUtils.compressFile(sourceFile, compressedFile);

        // When
        double ratio = CompressionUtils.calculateCompressionRatio(sourceFile, compressedFile);

        // Then
        assertThat(ratio).isEqualTo(1.0); // 빈 파일은 압축률 1.0
    }

    @Test
    void testIsCompressed_WithValidExtensions() {
        // Given
        Path gzFile = tempDir.resolve("test.gz");
        Path gzipFile = tempDir.resolve("test.gzip");
        Path sqlFile = tempDir.resolve("test.sql");

        // When & Then
        assertThat(CompressionUtils.isCompressed(gzFile)).isTrue();
        assertThat(CompressionUtils.isCompressed(gzipFile)).isTrue();
        assertThat(CompressionUtils.isCompressed(sqlFile)).isFalse();
    }

    @Test
    void testIsCompressed_CaseInsensitive() {
        // Given
        Path upperCaseFile = tempDir.resolve("test.GZ");
        Path mixedCaseFile = tempDir.resolve("test.GZip");

        // When & Then
        assertThat(CompressionUtils.isCompressed(upperCaseFile)).isTrue();
        assertThat(CompressionUtils.isCompressed(mixedCaseFile)).isTrue();
    }

    @Test
    void testGetCompressedFileName() {
        // Given
        Path originalFile = tempDir.resolve("backup.sql");

        // When
        Path compressedName = CompressionUtils.getCompressedFileName(originalFile);

        // Then
        assertThat(compressedName.getFileName().toString()).isEqualTo("backup.sql.gz");
        assertThat(compressedName.getParent()).isEqualTo(tempDir);
    }

    @Test
    void testGetDecompressedFileName_WithGzExtension() {
        // Given
        Path compressedFile = tempDir.resolve("backup.sql.gz");

        // When
        Path decompressedName = CompressionUtils.getDecompressedFileName(compressedFile);

        // Then
        assertThat(decompressedName.getFileName().toString()).isEqualTo("backup.sql");
        assertThat(decompressedName.getParent()).isEqualTo(tempDir);
    }

    @Test
    void testGetDecompressedFileName_WithGzipExtension() {
        // Given
        Path compressedFile = tempDir.resolve("backup.sql.gzip");

        // When
        Path decompressedName = CompressionUtils.getDecompressedFileName(compressedFile);

        // Then
        assertThat(decompressedName.getFileName().toString()).isEqualTo("backup.sql");
        assertThat(decompressedName.getParent()).isEqualTo(tempDir);
    }

    @Test
    void testGetDecompressedFileName_WithoutCompressionExtension() {
        // Given
        Path nonCompressedFile = tempDir.resolve("backup.sql");

        // When
        Path decompressedName = CompressionUtils.getDecompressedFileName(nonCompressedFile);

        // Then
        assertThat(decompressedName.getFileName().toString()).isEqualTo("backup.sql");
    }

    @Test
    void testCompressWithMetrics() throws IOException {
        // Given
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            contentBuilder.append("CREATE TABLE test (id INT, data TEXT);\n");
        }
        String content = contentBuilder.toString();
        Files.write(sourceFile, content.getBytes());

        // When
        CompressionUtils.CompressionResult result = CompressionUtils.compressWithMetrics(sourceFile, compressedFile);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOriginalFile()).isEqualTo(sourceFile);
        assertThat(result.getCompressedFile()).isEqualTo(compressedFile);
        assertThat(result.getOriginalSize()).isEqualTo(Files.size(sourceFile));
        assertThat(result.getCompressedSize()).isEqualTo(Files.size(compressedFile));
        assertThat(result.getCompressionRatio()).isBetween(0.0, 1.0);
        assertThat(result.getCompressionTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.getSavedBytes()).isGreaterThan(0);
        assertThat(result.getSavedPercentage()).isGreaterThan(0.0);
    }

    @Test
    void testCompressionResult_ToString() throws IOException {
        // Given
        String content = "TEST DATA";
        Files.write(sourceFile, content.getBytes());
        CompressionUtils.CompressionResult result = CompressionUtils.compressWithMetrics(sourceFile, compressedFile);

        // When
        String resultString = result.toString();

        // Then
        assertThat(resultString).contains("CompressionResult");
        assertThat(resultString).contains("originalSize");
        assertThat(resultString).contains("compressedSize");
        assertThat(resultString).contains("compressionRatio");
        assertThat(resultString).contains("savedPercentage");
        assertThat(resultString).contains("timeMs");
    }

    @Test
    void testCompressFile_NonExistentSourceFile() {
        // Given
        Path nonExistentFile = tempDir.resolve("nonexistent.sql");

        // When & Then
        assertThatThrownBy(() -> CompressionUtils.compressFile(nonExistentFile, compressedFile))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testDecompressFile_NonExistentCompressedFile() {
        // Given
        Path nonExistentFile = tempDir.resolve("nonexistent.gz");

        // When & Then
        assertThatThrownBy(() -> CompressionUtils.decompressFile(nonExistentFile, decompressedFile))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testRoundTripCompression_PreservesData() throws IOException {
        // Given
        String originalContent = generateTestSqlContent();
        Files.write(sourceFile, originalContent.getBytes());

        // When
        CompressionUtils.compressFile(sourceFile, compressedFile);
        CompressionUtils.decompressFile(compressedFile, decompressedFile);

        // Then
        String restoredContent = new String(Files.readAllBytes(decompressedFile));
        assertThat(restoredContent).isEqualTo(originalContent);
        assertThat(Files.size(decompressedFile)).isEqualTo(Files.size(sourceFile));
    }

    @Test
    void testCompression_WithSpecialCharacters() throws IOException {
        // Given
        String content = "INSERT INTO users VALUES (1, '한글 데이터');\n" +
                        "INSERT INTO users VALUES (2, 'Special chars: àáâãäåæçèéêë');\n" +
                        "INSERT INTO users VALUES (3, 'Symbols: !@#$%^&*()_+-={}[]|\\:;\"<>?,./');";
        Files.write(sourceFile, content.getBytes());

        // When
        CompressionUtils.compressFile(sourceFile, compressedFile);
        CompressionUtils.decompressFile(compressedFile, decompressedFile);

        // Then
        String restoredContent = new String(Files.readAllBytes(decompressedFile));
        assertThat(restoredContent).isEqualTo(content);
    }

    private String generateTestSqlContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Database backup generated by db-backup4j\n");
        sb.append("SET FOREIGN_KEY_CHECKS=0;\n\n");
        
        for (int i = 1; i <= 1000; i++) {
            sb.append("INSERT INTO users VALUES (")
              .append(i)
              .append(", 'user")
              .append(i)
              .append("', 'user")
              .append(i)
              .append("@example.com');\n");
        }
        
        sb.append("\nSET FOREIGN_KEY_CHECKS=1;\n");
        return sb.toString();
    }
}