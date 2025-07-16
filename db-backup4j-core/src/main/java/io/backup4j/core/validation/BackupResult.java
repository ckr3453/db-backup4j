package io.backup4j.core.validation;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 백업 실행 결과를 나타내는 모델
 */
public class BackupResult {
    
    public enum Status {
        SUCCESS,        // 백업 성공
        PARTIAL_SUCCESS, // 부분적 성공 (일부 저장소 실패)
        FAILED,         // 백업 실패
        VALIDATION_FAILED // 검증 실패
    }
    
    private final String backupId;
    private final Status status;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Duration duration;
    private final BackupMetadata metadata;
    private final List<BackupFile> files;
    private final List<BackupError> errors;
    private final List<ChecksumValidator.ValidationResult> validationResults;
    
    private BackupResult(Builder builder) {
        this.backupId = builder.backupId;
        this.status = builder.status;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.duration = builder.duration;
        this.metadata = builder.metadata;
        this.files = Collections.unmodifiableList(new ArrayList<>(builder.files));
        this.errors = Collections.unmodifiableList(new ArrayList<>(builder.errors));
        this.validationResults = Collections.unmodifiableList(new ArrayList<>(builder.validationResults));
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getBackupId() { return backupId; }
    public Status getStatus() { return status; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Duration getDuration() { return duration; }
    public BackupMetadata getMetadata() { return metadata; }
    public List<BackupFile> getFiles() { return files; }
    public List<BackupError> getErrors() { return errors; }
    public List<ChecksumValidator.ValidationResult> getValidationResults() { return validationResults; }
    
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    public boolean hasValidationFailures() {
        return validationResults.stream().anyMatch(v -> !v.isValid());
    }
    
    /**
     * 백업 파일 정보
     */
    public static class BackupFile {
        private final Path filePath;
        private final long fileSize;
        private final String destination; // local, email, s3
        private final ChecksumValidator.StoredChecksum checksum;
        private final LocalDateTime createdAt;
        
        public BackupFile(Path filePath, long fileSize, String destination, 
                         ChecksumValidator.StoredChecksum checksum, LocalDateTime createdAt) {
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.destination = destination;
            this.checksum = checksum;
            this.createdAt = createdAt;
        }
        
        public Path getFilePath() { return filePath; }
        public long getFileSize() { return fileSize; }
        public String getDestination() { return destination; }
        public ChecksumValidator.StoredChecksum getChecksum() { return checksum; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        
        @Override
        public String toString() {
            return String.format("BackupFile{filePath=%s, fileSize=%d, destination='%s'}", 
                filePath, fileSize, destination);
        }
    }
    
    /**
     * 백업 오류 정보
     */
    public static class BackupError {
        private final String destination;
        private final String message;
        private final Exception exception;
        private final LocalDateTime occurredAt;
        
        public BackupError(String destination, String message, Exception exception, LocalDateTime occurredAt) {
            this.destination = destination;
            this.message = message;
            this.exception = exception;
            this.occurredAt = occurredAt;
        }
        
        public String getDestination() { return destination; }
        public String getMessage() { return message; }
        public Exception getException() { return exception; }
        public LocalDateTime getOccurredAt() { return occurredAt; }
        
        @Override
        public String toString() {
            return String.format("BackupError{destination='%s', message='%s', occurredAt=%s}", 
                destination, message, occurredAt);
        }
    }
    
    /**
     * 백업 메타데이터
     */
    public static class BackupMetadata {
        private final String databaseType;
        private final String databaseHost;
        private final String databaseName;
        private final boolean compressed;
        private final long originalSize;
        private final long compressedSize;
        private final String backupFormat; // SQL, DUMP 등
        
        public BackupMetadata(String databaseType, String databaseHost, String databaseName, 
                            boolean compressed, long originalSize, long compressedSize, String backupFormat) {
            this.databaseType = databaseType;
            this.databaseHost = databaseHost;
            this.databaseName = databaseName;
            this.compressed = compressed;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.backupFormat = backupFormat;
        }
        
        public String getDatabaseType() { return databaseType; }
        public String getDatabaseHost() { return databaseHost; }
        public String getDatabaseName() { return databaseName; }
        public boolean isCompressed() { return compressed; }
        public long getOriginalSize() { return originalSize; }
        public long getCompressedSize() { return compressedSize; }
        public String getBackupFormat() { return backupFormat; }
        
        public double getCompressionRatio() {
            return compressed && originalSize > 0 ? 
                (double) compressedSize / originalSize : 1.0;
        }
        
        @Override
        public String toString() {
            return String.format("BackupMetadata{databaseType='%s', databaseName='%s', compressed=%s, originalSize=%d, compressedSize=%d}", 
                databaseType, databaseName, compressed, originalSize, compressedSize);
        }
    }
    
    /**
     * Builder 패턴
     */
    public static class Builder {
        private String backupId;
        private Status status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Duration duration;
        private BackupMetadata metadata;
        private List<BackupFile> files = new ArrayList<>();
        private List<BackupError> errors = new ArrayList<>();
        private List<ChecksumValidator.ValidationResult> validationResults = new ArrayList<>();
        
        public Builder backupId(String backupId) { this.backupId = backupId; return this; }
        public Builder status(Status status) { this.status = status; return this; }
        public Builder startTime(LocalDateTime startTime) { this.startTime = startTime; return this; }
        public Builder endTime(LocalDateTime endTime) { this.endTime = endTime; return this; }
        public Builder duration(Duration duration) { this.duration = duration; return this; }
        public Builder metadata(BackupMetadata metadata) { this.metadata = metadata; return this; }
        
        public Builder addFile(BackupFile file) { 
            this.files.add(file); 
            return this; 
        }
        
        public Builder addError(BackupError error) { 
            this.errors.add(error); 
            return this; 
        }
        
        public Builder addValidationResult(ChecksumValidator.ValidationResult result) {
            this.validationResults.add(result);
            return this;
        }
        
        public BackupResult build() {
            // 자동 계산
            if (duration == null && startTime != null && endTime != null) {
                duration = Duration.between(startTime, endTime);
            }
            
            // 상태 자동 결정
            if (status == null) {
                if (!errors.isEmpty()) {
                    status = Status.FAILED;
                } else if (validationResults.stream().anyMatch(v -> !v.isValid())) {
                    status = Status.VALIDATION_FAILED;
                } else {
                    status = Status.SUCCESS;
                }
            }
            
            return new BackupResult(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("BackupResult{backupId='%s', status=%s, duration=%s, files=%d, errors=%d}", 
            backupId, status, duration, files.size(), errors.size());
    }
}