package io.backup4j.core.validation;

import java.io.*;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;

/**
 * 백업 파일의 기본적인 유효성을 검증하는 클래스입니다.
 * 체크섬 대신 실용적이고 빠른 검증 방법들을 제공합니다.
 */
public class BackupValidator {

    private BackupValidator() {
    }

    private static final int SAMPLE_SIZE = 1024; // 첫 1KB만 읽어서 검증
    private static final int MIN_FILE_SIZE_BYTES = 100; // 최소 파일 크기
    private static final String GZIP_EXTENSION = ".gz";
    private static final String GZIP_EXTENSION_ALT = ".gzip";
    private static final String SQL_EXTENSION = ".sql";
    private static final String SQL_COMMENT_PREFIX = "--";
    private static final String CREATE_TABLE_KEYWORD = "CREATE TABLE";
    
    /**
     * 백업 파일의 기본적인 유효성을 검증합니다.
     * 
     * @param backupFile 검증할 백업 파일
     * @return 검증 결과
     */
    public static ValidationResult validateBackupFile(File backupFile) {
        ValidationResult.Builder result = ValidationResult.builder()
            .filePath(backupFile.getAbsolutePath());
        
        try {
            // 1. 파일 존재 및 기본 속성 검증
            if (!validateFileExists(backupFile, result)) {
                return result.build();
            }
            
            // 2. 파일 크기 검증
            if (!validateFileSize(backupFile, result)) {
                return result.build();
            }
            
            // 3. 파일 권한 검증
            if (!validateFilePermissions(backupFile, result)) {
                return result.build();
            }
            
            // 4. 파일 내용 검증
            if (backupFile.getName().endsWith(GZIP_EXTENSION) || backupFile.getName().endsWith(GZIP_EXTENSION_ALT)) {
                validateCompressedFile(backupFile, result);
            } else if (backupFile.getName().endsWith(SQL_EXTENSION)) {
                validateSQLFile(backupFile, result);
            }
            
            return result.build();
            
        } catch (Exception e) {
            return result
                .valid(false)
                .addError("Unexpected error during validation: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 파일 존재 여부를 검증합니다.
     */
    private static boolean validateFileExists(File file, ValidationResult.Builder result) {
        if (!file.exists()) {
            result.addError("Backup file does not exist: " + file.getAbsolutePath());
            return false;
        }
        
        if (!file.isFile()) {
            result.addError("Path is not a file: " + file.getAbsolutePath());
            return false;
        }
        
        return true;
    }
    
    /**
     * 파일 크기를 검증합니다.
     */
    private static boolean validateFileSize(File file, ValidationResult.Builder result) {
        long fileSize = file.length();
        
        if (fileSize == 0) {
            result.addError("Backup file is empty: " + file.getName());
            return false;
        }
        
        if (fileSize < MIN_FILE_SIZE_BYTES) {
            result.addWarning("Backup file is very small (" + fileSize + " bytes): " + file.getName());
        }
        
        return true;
    }
    
    /**
     * 파일 권한을 검증합니다.
     */
    private static boolean validateFilePermissions(File file, ValidationResult.Builder result) {
        if (!file.canRead()) {
            result.addError("Cannot read backup file: " + file.getName());
            return false;
        }
        
        return true;
    }
    
    /**
     * SQL 백업 파일의 내용을 검증합니다.
     */
    private static void validateSQLFile(File sqlFile, ValidationResult.Builder result) {
        try {
            String sample = readFileSample(sqlFile);
            
            // SQL 백업 파일에 포함되어야 할 기본 요소들 검증
            if (!sample.contains(SQL_COMMENT_PREFIX)) {
                result.addWarning("SQL file may not contain proper comments");
            }
            
            boolean hasCreateTable = sample.toUpperCase().contains(CREATE_TABLE_KEYWORD);
            boolean hasInsert = sample.toUpperCase().contains("INSERT");
            boolean hasDrop = sample.toUpperCase().contains("DROP");
            
            if (!hasCreateTable && !hasDrop) {
                result.addWarning("SQL file does not contain CREATE TABLE or DROP statements - could be empty database");
            }
            
            if (!hasInsert) {
                result.addWarning("SQL file may not contain INSERT statements (could be empty database)");
            }
            
            // 기본 SQL 구문 오류 검증
            if (sample.contains("ERROR") || sample.contains("FAILED")) {
                result.addError("SQL file appears to contain error messages");
            }
            
        } catch (IOException e) {
            result.addError("Failed to read SQL file content: " + e.getMessage());
        }
    }
    
    /**
     * 압축된 백업 파일을 검증합니다.
     */
    private static void validateCompressedFile(File compressedFile, ValidationResult.Builder result) {
        try {
            // GZIP 파일 압축 해제 가능 여부 확인
            try (GZIPInputStream gzis = new GZIPInputStream(Files.newInputStream(compressedFile.toPath()))) {
                byte[] buffer = new byte[SAMPLE_SIZE];
                int bytesRead = gzis.read(buffer);
                
                if (bytesRead <= 0) {
                    result.addError("Compressed file appears to be empty after decompression");
                    return;
                }
                
                // 압축 해제된 내용이 SQL인지 확인
                String decompressedSample = new String(buffer, 0, bytesRead);
                if (!decompressedSample.toUpperCase().contains("CREATE") && 
                    !decompressedSample.toUpperCase().contains("INSERT") &&
                    !decompressedSample.toUpperCase().contains("DROP")) {
                    result.addWarning("Compressed file may not contain valid SQL content");
                }
                
            }
        } catch (IOException e) {
            result.addError("Failed to decompress file or file is corrupted: " + e.getMessage());
        }
    }
    
    /**
     * 파일의 첫 부분을 샘플로 읽어옵니다.
     */
    private static String readFileSample(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[SAMPLE_SIZE];
            int bytesRead = fis.read(buffer);
            return new String(buffer, 0, bytesRead);
        }
    }
    
    /**
     * 백업 검증 결과를 나타내는 클래스입니다.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String filePath;
        private final java.util.List<String> errors;
        private final java.util.List<String> warnings;
        
        private ValidationResult(Builder builder) {
            this.valid = builder.valid;
            this.filePath = builder.filePath;
            this.errors = java.util.Collections.unmodifiableList(builder.errors);
            this.warnings = java.util.Collections.unmodifiableList(builder.warnings);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public java.util.List<String> getErrors() {
            return errors;
        }
        
        public java.util.List<String> getWarnings() {
            return warnings;
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        @Override
        public String toString() {
            return "ValidationResult{" +
                "valid=" + valid +
                ", filePath='" + filePath + '\'' +
                ", errors=" + errors.size() +
                ", warnings=" + warnings.size() +
                '}';
        }
        
        public static class Builder {
            private boolean valid = true;
            private String filePath;
            private final java.util.List<String> errors = new java.util.ArrayList<>();
            private final java.util.List<String> warnings = new java.util.ArrayList<>();
            
            public Builder valid(boolean valid) {
                this.valid = valid;
                return this;
            }
            
            public Builder filePath(String filePath) {
                this.filePath = filePath;
                return this;
            }
            
            public Builder addError(String error) {
                this.errors.add(error);
                this.valid = false;
                return this;
            }
            
            public Builder addWarning(String warning) {
                this.warnings.add(warning);
                return this;
            }
            
            public java.util.List<String> getErrors() {
                return errors;
            }
            
            public ValidationResult build() {
                return new ValidationResult(this);
            }
        }
    }
}