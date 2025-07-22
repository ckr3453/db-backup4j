package io.backup4j.core.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ChecksumValidatorTest {

    @TempDir
    Path tempDir;

    private Path testFile;
    private final String testContent = "Hello, World! Test content for checksum validation.";
    private String expectedMD5;
    private String expectedSHA256;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test.txt");
        Files.write(testFile, testContent.getBytes(StandardCharsets.UTF_8));
        
        // 예상 체크섬 미리 계산
        expectedMD5 = ZeroCopyChecksumCalculator.calculateMD5(testFile);
        expectedSHA256 = ZeroCopyChecksumCalculator.calculateSHA256(testFile);
    }

    @Test
    void validate_올바른MD5체크섬_검증성공() {
        // When
        ChecksumValidator.ValidationResult result = ChecksumValidator.validateMD5(testFile, expectedMD5);

        // Then
        assertTrue(result.isValid());
        assertEquals(ChecksumValidator.ValidationStatus.VALID, result.getStatus());
        assertEquals(expectedMD5, result.getExpectedChecksum());
        assertEquals(expectedMD5, result.getActualChecksum());
        assertEquals(ZeroCopyChecksumCalculator.Algorithm.MD5, result.getAlgorithm());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("검증 성공"));
    }

    @Test
    void validate_올바른SHA256체크섬_검증성공() {
        // When
        ChecksumValidator.ValidationResult result = ChecksumValidator.validateSHA256(testFile, expectedSHA256);

        // Then
        assertTrue(result.isValid());
        assertEquals(ChecksumValidator.ValidationStatus.VALID, result.getStatus());
        assertEquals(expectedSHA256, result.getExpectedChecksum());
        assertEquals(expectedSHA256, result.getActualChecksum());
        assertEquals(ZeroCopyChecksumCalculator.Algorithm.SHA256, result.getAlgorithm());
    }

    @Test
    void validate_잘못된체크섬_검증실패() {
        // Given
        String wrongChecksum = "wrongchecksum1234567890abcdef1234567890abcdef";

        // When
        ChecksumValidator.ValidationResult result = ChecksumValidator.validateMD5(testFile, wrongChecksum);

        // Then
        assertFalse(result.isValid());
        assertEquals(ChecksumValidator.ValidationStatus.INVALID, result.getStatus());
        assertEquals(wrongChecksum, result.getExpectedChecksum());
        assertEquals(expectedMD5, result.getActualChecksum());
        assertNotEquals(result.getExpectedChecksum(), result.getActualChecksum());
        assertTrue(result.getMessage().contains("불일치"));
    }

    @Test
    void validate_존재하지않는파일_오류상태() {
        // Given
        Path nonExistentFile = tempDir.resolve("non-existent.txt");

        // When
        ChecksumValidator.ValidationResult result = ChecksumValidator.validateMD5(nonExistentFile, expectedMD5);

        // Then
        assertEquals(ChecksumValidator.ValidationStatus.ERROR, result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getMessage().contains("오류 발생"));
        assertFalse(result.isValid());
    }

    @Test
    void calculateAndStore_정상파일_저장된체크섬반환() throws IOException {
        // When
        ChecksumValidator.StoredChecksum stored = ChecksumValidator.calculateAndStore(
            testFile, ZeroCopyChecksumCalculator.Algorithm.MD5);

        // Then
        assertNotNull(stored);
        assertEquals(expectedMD5, stored.getChecksum());
        assertEquals(ZeroCopyChecksumCalculator.Algorithm.MD5, stored.getAlgorithm());
        assertEquals(testFile.toString(), stored.getFilePath());
        assertTrue(stored.getFileSize() > 0);
        assertTrue(stored.getCalculationTimeMs() >= 0);
        assertNotNull(stored.getCalculatedAt());
    }

    @Test
    void storedChecksum_validateAgainstFile_정상검증() throws IOException {
        // Given
        ChecksumValidator.StoredChecksum stored = ChecksumValidator.calculateAndStore(
            testFile, ZeroCopyChecksumCalculator.Algorithm.MD5);

        // When
        ChecksumValidator.ValidationResult result = stored.validateAgainstFile(testFile);

        // Then
        assertTrue(result.isValid());
        assertEquals(stored.getChecksum(), result.getExpectedChecksum());
        assertEquals(stored.getChecksum(), result.getActualChecksum());
    }

    @Test
    void storedChecksum_validateAgainstModifiedFile_검증실패() throws IOException {
        // Given
        ChecksumValidator.StoredChecksum stored = ChecksumValidator.calculateAndStore(
            testFile, ZeroCopyChecksumCalculator.Algorithm.MD5);
        
        // 파일 내용 변경
        Files.write(testFile, "Modified content".getBytes());

        // When
        ChecksumValidator.ValidationResult result = stored.validateAgainstFile(testFile);

        // Then
        assertFalse(result.isValid());
        assertEquals(ChecksumValidator.ValidationStatus.INVALID, result.getStatus());
        assertEquals(stored.getChecksum(), result.getExpectedChecksum());
        assertNotEquals(stored.getChecksum(), result.getActualChecksum());
    }

    @Test
    void validationResult_builder_정상동작() {
        // When
        ChecksumValidator.ValidationResult result = ChecksumValidator.ValidationResult.builder()
            .status(ChecksumValidator.ValidationStatus.VALID)
            .expectedChecksum("expected")
            .actualChecksum("actual")
            .algorithm(ZeroCopyChecksumCalculator.Algorithm.MD5)
            .message("test message")
            .validationTimeMs(100L)
            .build();

        // Then
        assertEquals(ChecksumValidator.ValidationStatus.VALID, result.getStatus());
        assertEquals("expected", result.getExpectedChecksum());
        assertEquals("actual", result.getActualChecksum());
        assertEquals(ZeroCopyChecksumCalculator.Algorithm.MD5, result.getAlgorithm());
        assertEquals("test message", result.getMessage());
        assertEquals(100L, result.getValidationTimeMs());
        assertNotNull(result.getValidatedAt());
    }

    @Test
    void validationResult_toString_정상출력() {
        // Given
        ChecksumValidator.ValidationResult result = ChecksumValidator.validateMD5(testFile, expectedMD5);

        // When
        String toString = result.toString();

        // Then
        assertTrue(toString.contains("ValidationResult"));
        assertTrue(toString.contains("VALID"));
        assertTrue(toString.contains("MD5"));
        assertTrue(toString.contains(expectedMD5));
    }

    @Test
    void storedChecksum_toString_정상출력() throws IOException {
        // Given
        ChecksumValidator.StoredChecksum stored = ChecksumValidator.calculateAndStore(
            testFile, ZeroCopyChecksumCalculator.Algorithm.SHA256);

        // When
        String toString = stored.toString();

        // Then
        assertTrue(toString.contains("StoredChecksum"));
        assertTrue(toString.contains("SHA256"));
        assertTrue(toString.contains(stored.getChecksum()));
        assertTrue(toString.contains(testFile.toString()));
    }

    @Test
    void validate_성능측정_시간기록() {
        // When
        ChecksumValidator.ValidationResult result = ChecksumValidator.validateMD5(testFile, expectedMD5);

        // Then
        assertTrue(result.getValidationTimeMs() >= 0);
        assertNotNull(result.getValidatedAt());
    }
}