package io.backup4j.core.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ZeroCopyChecksumCalculatorTest {

    @TempDir
    Path tempDir;

    private Path testFile;
    private final String testContent = "Hello, World! This is a test file for checksum calculation.";

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test.txt");
        Files.write(testFile, testContent.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void calculateMD5_정상적인파일_체크섬반환() throws IOException {
        // When
        String md5 = ZeroCopyChecksumCalculator.calculateMD5(testFile);

        // Then
        assertNotNull(md5);
        assertEquals(32, md5.length()); // MD5는 32자리 hex
        assertTrue(md5.matches("[a-f0-9]+"));
    }

    @Test
    void calculateSHA256_정상적인파일_체크섬반환() throws IOException {
        // When
        String sha256 = ZeroCopyChecksumCalculator.calculateSHA256(testFile);

        // Then
        assertNotNull(sha256);
        assertEquals(64, sha256.length()); // SHA256은 64자리 hex
        assertTrue(sha256.matches("[a-f0-9]+"));
    }

    @Test
    void calculate_동일한파일_동일한체크섬() throws IOException {
        // When
        String md5First = ZeroCopyChecksumCalculator.calculateMD5(testFile);
        String md5Second = ZeroCopyChecksumCalculator.calculateMD5(testFile);

        // Then
        assertEquals(md5First, md5Second);
    }

    @Test
    void calculate_빈파일_체크섬계산성공() throws IOException {
        // Given
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.createFile(emptyFile);

        // When
        String md5 = ZeroCopyChecksumCalculator.calculateMD5(emptyFile);

        // Then
        assertNotNull(md5);
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5); // 빈 파일의 MD5
    }

    @Test
    void calculate_대용량파일_성공적으로처리() throws IOException {
        // Given
        Path largeFile = tempDir.resolve("large.txt");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            content.append("This is line ").append(i).append("\n");
        }
        Files.write(largeFile, content.toString().getBytes(StandardCharsets.UTF_8));

        // When
        String md5 = ZeroCopyChecksumCalculator.calculateMD5(largeFile);
        String sha256 = ZeroCopyChecksumCalculator.calculateSHA256(largeFile);

        // Then
        assertNotNull(md5);
        assertNotNull(sha256);
        assertEquals(32, md5.length());
        assertEquals(64, sha256.length());
    }

    @Test
    void calculateOptimalChunkSize_파일크기별_적절한청크크기반환() {
        // When & Then
        assertEquals(4 * 1024 * 1024, ZeroCopyChecksumCalculator.calculateOptimalChunkSize(50 * 1024 * 1024)); // 50MB
        assertEquals(16 * 1024 * 1024, ZeroCopyChecksumCalculator.calculateOptimalChunkSize(500 * 1024 * 1024)); // 500MB
        assertEquals(32 * 1024 * 1024, ZeroCopyChecksumCalculator.calculateOptimalChunkSize(2L * 1024 * 1024 * 1024)); // 2GB
    }

    @Test
    void calculateOptimized_최적화된계산_정상동작() throws IOException {
        // When
        String md5Optimized = ZeroCopyChecksumCalculator.calculateOptimized(testFile, ZeroCopyChecksumCalculator.Algorithm.MD5);
        String md5Standard = ZeroCopyChecksumCalculator.calculateMD5(testFile);

        // Then
        assertEquals(md5Standard, md5Optimized);
    }

    @Test
    void calculateWithMetrics_성능메트릭포함_정상동작() throws IOException {
        // When
        ZeroCopyChecksumCalculator.ChecksumResult result = 
            ZeroCopyChecksumCalculator.calculateWithMetrics(testFile, ZeroCopyChecksumCalculator.Algorithm.MD5);

        // Then
        assertNotNull(result);
        assertNotNull(result.getChecksum());
        assertEquals(ZeroCopyChecksumCalculator.Algorithm.MD5, result.getAlgorithm());
        assertTrue(result.getFileSize() > 0);
        assertTrue(result.getCalculationTimeMs() >= 0);
    }

    @Test
    void calculate_존재하지않는파일_예외발생() {
        // Given
        Path nonExistentFile = tempDir.resolve("non-existent.txt");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            ZeroCopyChecksumCalculator.calculateMD5(nonExistentFile);
        });
    }

    @Test
    void calculate_null경로_예외발생() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            ZeroCopyChecksumCalculator.calculateMD5(null);
        });
    }

    @Test
    void calculate_디렉토리경로_예외발생() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            ZeroCopyChecksumCalculator.calculateMD5(tempDir);
        });
    }

    @Test
    void calculate_유효하지않은청크크기_예외발생() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            ZeroCopyChecksumCalculator.calculate(testFile, ZeroCopyChecksumCalculator.Algorithm.MD5, 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            ZeroCopyChecksumCalculator.calculate(testFile, ZeroCopyChecksumCalculator.Algorithm.MD5, 128 * 1024 * 1024); // MAX_CHUNK_SIZE 초과
        });
    }

    @Test
    void checksumResult_toString_정상출력() throws IOException {
        // When
        ZeroCopyChecksumCalculator.ChecksumResult result = 
            ZeroCopyChecksumCalculator.calculateWithMetrics(testFile, ZeroCopyChecksumCalculator.Algorithm.SHA256);

        // Then
        String toString = result.toString();
        assertTrue(toString.contains("ChecksumResult"));
        assertTrue(toString.contains("SHA256"));
        assertTrue(toString.contains("checksum="));
        assertTrue(toString.contains("fileSize="));
    }
}