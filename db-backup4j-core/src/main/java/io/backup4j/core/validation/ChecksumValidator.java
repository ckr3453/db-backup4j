package io.backup4j.core.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 백업 파일의 체크섬 검증을 담당하는 클래스
 */
public class ChecksumValidator {
    
    public enum ValidationStatus {
        VALID,      // 체크섬 일치
        INVALID,    // 체크섬 불일치  
        ERROR       // 검증 중 오류 발생
    }
    
    /**
     * 체크섬 검증 결과
     */
    public static class ValidationResult {
        private final ValidationStatus status;
        private final String expectedChecksum;
        private final String actualChecksum;
        private final ZeroCopyChecksumCalculator.Algorithm algorithm;
        private final String message;
        private final LocalDateTime validatedAt;
        private final long validationTimeMs;
        private final Exception error;
        
        private ValidationResult(Builder builder) {
            this.status = builder.status;
            this.expectedChecksum = builder.expectedChecksum;
            this.actualChecksum = builder.actualChecksum;
            this.algorithm = builder.algorithm;
            this.message = builder.message;
            this.validatedAt = builder.validatedAt;
            this.validationTimeMs = builder.validationTimeMs;
            this.error = builder.error;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public ValidationStatus getStatus() { return status; }
        public String getExpectedChecksum() { return expectedChecksum; }
        public String getActualChecksum() { return actualChecksum; }
        public ZeroCopyChecksumCalculator.Algorithm getAlgorithm() { return algorithm; }
        public String getMessage() { return message; }
        public LocalDateTime getValidatedAt() { return validatedAt; }
        public long getValidationTimeMs() { return validationTimeMs; }
        public Exception getError() { return error; }
        
        public boolean isValid() {
            return status == ValidationStatus.VALID;
        }
        
        @Override
        public String toString() {
            return String.format("ValidationResult{status=%s, algorithm=%s, expectedChecksum='%s', actualChecksum='%s', message='%s'}", 
                status, algorithm, expectedChecksum, actualChecksum, message);
        }
        
        public static class Builder {
            private ValidationStatus status;
            private String expectedChecksum;
            private String actualChecksum;
            private ZeroCopyChecksumCalculator.Algorithm algorithm;
            private String message;
            private LocalDateTime validatedAt = LocalDateTime.now();
            private long validationTimeMs;
            private Exception error;
            
            public Builder status(ValidationStatus status) { this.status = status; return this; }
            public Builder expectedChecksum(String expected) { this.expectedChecksum = expected; return this; }
            public Builder actualChecksum(String actual) { this.actualChecksum = actual; return this; }
            public Builder algorithm(ZeroCopyChecksumCalculator.Algorithm algorithm) { this.algorithm = algorithm; return this; }
            public Builder message(String message) { this.message = message; return this; }
            public Builder validatedAt(LocalDateTime validatedAt) { this.validatedAt = validatedAt; return this; }
            public Builder validationTimeMs(long timeMs) { this.validationTimeMs = timeMs; return this; }
            public Builder error(Exception error) { this.error = error; return this; }
            
            public ValidationResult build() {
                return new ValidationResult(this);
            }
        }
    }
    
    /**
     * 파일의 체크섬을 계산하고 예상 값과 비교하여 검증
     */
    public static ValidationResult validate(Path filePath, String expectedChecksum, 
                                          ZeroCopyChecksumCalculator.Algorithm algorithm) {
        long startTime = System.currentTimeMillis();
        
        try {
            String actualChecksum = ZeroCopyChecksumCalculator.calculateOptimized(filePath, algorithm);
            long validationTime = System.currentTimeMillis() - startTime;
            
            boolean isValid = Objects.equals(expectedChecksum, actualChecksum);
            
            return ValidationResult.builder()
                .status(isValid ? ValidationStatus.VALID : ValidationStatus.INVALID)
                .expectedChecksum(expectedChecksum)
                .actualChecksum(actualChecksum)
                .algorithm(algorithm)
                .validationTimeMs(validationTime)
                .message(createValidationMessage(isValid, filePath, expectedChecksum, actualChecksum))
                .build();
                
        } catch (Exception e) {
            long validationTime = System.currentTimeMillis() - startTime;
            
            return ValidationResult.builder()
                .status(ValidationStatus.ERROR)
                .expectedChecksum(expectedChecksum)
                .algorithm(algorithm)
                .validationTimeMs(validationTime)
                .error(e)
                .message("체크섬 검증 중 오류 발생: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * MD5 체크섬으로 검증
     */
    public static ValidationResult validateMD5(Path filePath, String expectedMD5) {
        return validate(filePath, expectedMD5, ZeroCopyChecksumCalculator.Algorithm.MD5);
    }
    
    /**
     * SHA256 체크섬으로 검증
     */
    public static ValidationResult validateSHA256(Path filePath, String expectedSHA256) {
        return validate(filePath, expectedSHA256, ZeroCopyChecksumCalculator.Algorithm.SHA256);
    }
    
    /**
     * 파일의 체크섬을 계산하고 저장 (검증용)
     */
    public static StoredChecksum calculateAndStore(Path filePath, ZeroCopyChecksumCalculator.Algorithm algorithm) throws IOException {
        ZeroCopyChecksumCalculator.ChecksumResult result = 
            ZeroCopyChecksumCalculator.calculateWithMetrics(filePath, algorithm);
        
        return new StoredChecksum(
            result.getChecksum(),
            algorithm,
            filePath.toString(),
            LocalDateTime.now(),
            result.getFileSize(),
            result.getCalculationTimeMs()
        );
    }
    
    /**
     * 저장된 체크섬 정보
     */
    public static class StoredChecksum {
        private final String checksum;
        private final ZeroCopyChecksumCalculator.Algorithm algorithm;
        private final String filePath;
        private final LocalDateTime calculatedAt;
        private final long fileSize;
        private final long calculationTimeMs;
        
        public StoredChecksum(String checksum, ZeroCopyChecksumCalculator.Algorithm algorithm, 
                            String filePath, LocalDateTime calculatedAt, long fileSize, long calculationTimeMs) {
            this.checksum = checksum;
            this.algorithm = algorithm;
            this.filePath = filePath;
            this.calculatedAt = calculatedAt;
            this.fileSize = fileSize;
            this.calculationTimeMs = calculationTimeMs;
        }
        
        public String getChecksum() { return checksum; }
        public ZeroCopyChecksumCalculator.Algorithm getAlgorithm() { return algorithm; }
        public String getFilePath() { return filePath; }
        public LocalDateTime getCalculatedAt() { return calculatedAt; }
        public long getFileSize() { return fileSize; }
        public long getCalculationTimeMs() { return calculationTimeMs; }
        
        /**
         * 현재 파일과 체크섬 검증
         */
        public ValidationResult validateAgainstFile(Path currentFilePath) {
            return ChecksumValidator.validate(currentFilePath, this.checksum, this.algorithm);
        }
        
        @Override
        public String toString() {
            return String.format("StoredChecksum{checksum='%s', algorithm=%s, filePath='%s', fileSize=%d}", 
                checksum, algorithm, filePath, fileSize);
        }
    }
    
    private static String createValidationMessage(boolean isValid, Path filePath, 
                                                String expected, String actual) {
        if (isValid) {
            return String.format("체크섬 검증 성공: %s", filePath.getFileName());
        } else {
            return String.format("체크섬 불일치 - 파일: %s, 예상: %s, 실제: %s", 
                filePath.getFileName(), expected, actual);
        }
    }
}