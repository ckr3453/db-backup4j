package io.backup4j.core.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 백업 파일 정리 정책을 관리하는 클래스
 * 설정된 보존 기간보다 오래된 백업 파일들을 자동으로 삭제하고 관리함
 */
public class RetentionPolicy {
    /**
     * 시간 공급자 인터페이스
     */
    public interface TimeProvider {
        Instant now();
    }
    
    /**
     * 기본 시간 공급자 - 시스템 현재 시간 반환
     */
    public static class SystemTimeProvider implements TimeProvider {
        @Override
        public Instant now() {
            return Instant.now();
        }
    }
    
    private final TimeProvider timeProvider;
    
    /**
     * 기본 시간 공급자를 사용하는 생성자
     */
    public RetentionPolicy() {
        this.timeProvider = new SystemTimeProvider();
    }
    
    /**
     * 커스텀 시간 공급자를 사용하는 생성자
     * @param timeProvider 시간 공급자
     */
    public RetentionPolicy(TimeProvider timeProvider) {
        this.timeProvider = timeProvider != null ? timeProvider : new SystemTimeProvider();
    }
    
    /**
     * 백업 파일 정리 결과를 담는 클래스
     */
    public static class CleanupResult {
        private final int totalFiles;
        private final int deletedFiles;
        private final long freedSpace;
        private final List<String> deletedFilePaths;
        private final List<String> errors;
        private final LocalDateTime cleanupTime;
        
        public CleanupResult(int totalFiles, int deletedFiles, long freedSpace, 
                           List<String> deletedFilePaths, List<String> errors) {
            this.totalFiles = totalFiles;
            this.deletedFiles = deletedFiles;
            this.freedSpace = freedSpace;
            this.deletedFilePaths = deletedFilePaths;
            this.errors = errors;
            this.cleanupTime = LocalDateTime.now();
        }
        
        public int getTotalFiles() {
            return totalFiles;
        }
        
        public int getDeletedFiles() {
            return deletedFiles;
        }
        
        public long getFreedSpace() {
            return freedSpace;
        }
        
        public List<String> getDeletedFilePaths() {
            return deletedFilePaths;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public LocalDateTime getCleanupTime() {
            return cleanupTime;
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        @Override
        public String toString() {
            return String.format("CleanupResult{totalFiles=%d, deletedFiles=%d, " +
                    "freedSpace=%d bytes, hasErrors=%b, cleanupTime=%s}",
                    totalFiles, deletedFiles, freedSpace, hasErrors(), 
                    cleanupTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
    }
    
    /**
     * 지정된 디렉토리에서 보존 기간을 초과한 백업 파일들을 삭제
     * 
     * @param backupDirectory 백업 파일들이 저장된 디렉토리
     * @param retentionDays 보존 기간 (일)
     * @return 정리 결과
     */
    public CleanupResult cleanup(Path backupDirectory, int retentionDays) {
        return cleanup(backupDirectory, retentionDays, false);
    }
    
    /**
     * 지정된 디렉토리에서 보존 기간을 초과한 백업 파일들을 삭제
     * 
     * @param backupDirectory 백업 파일들이 저장된 디렉토리
     * @param retentionDays 보존 기간 (일)
     * @param dryRun true면 실제 삭제하지 않고 시뮬레이션만 수행
     * @return 정리 결과
     */
    public CleanupResult cleanup(Path backupDirectory, int retentionDays, boolean dryRun) {
        // 1. 디렉터리 검증 및 초기화
        CleanupContext context = validateAndInitialize(backupDirectory, retentionDays);
        if (context.hasInitialErrors()) {
            return context.buildResult();
        }
        
        // 2. 백업 파일 스캔
        List<Path> candidateFiles = scanBackupFiles(backupDirectory, context);
        if (context.hasErrors()) {
            return context.buildResult();
        }
        
        // 3. 파일 삭제 처리
        processFileDeletion(candidateFiles, context, dryRun);
        
        // 4. 결과 반환
        return context.buildResult();
    }
    
    /**
     * 디렉터리 검증 및 정리 컨텍스트 초기화
     * 
     * @param backupDirectory 백업 디렉터리
     * @param retentionDays 보존 기간 (일)
     * @return 정리 작업 컨텍스트
     */
    private CleanupContext validateAndInitialize(Path backupDirectory, int retentionDays) {
        CleanupContext context = new CleanupContext(retentionDays, this.timeProvider);
        
        if (!Files.exists(backupDirectory) || !Files.isDirectory(backupDirectory)) {
            context.addError("Backup directory does not exist or is not a directory: " + backupDirectory);
        }
        
        return context;
    }
    
    /**
     * 백업 파일들을 스캔하여 정리 대상 파일들을 찾습니다.
     * 
     * @param backupDirectory 백업 디렉터리
     * @param context 정리 작업 컨텍스트
     * @return 정리 대상 파일 목록
     */
    private static List<Path> scanBackupFiles(Path backupDirectory, CleanupContext context) {
        List<Path> candidateFiles = new ArrayList<>();
        
        try (Stream<Path> files = Files.walk(backupDirectory, 1)) {
            List<Path> backupFiles = files
                .filter(Files::isRegularFile)
                .filter(RetentionPolicy::isBackupFile)
                .collect(Collectors.toList());
            
            context.setTotalFiles(backupFiles.size());
            
            // 파일 생성 시간을 기준으로 정리 대상 파일 선별
            for (Path file : backupFiles) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    Instant fileTime = attrs.creationTime().toInstant();
                    
                    if (context.shouldDelete(fileTime)) {
                        candidateFiles.add(file);
                    }
                } catch (IOException e) {
                    context.addError("Failed to read file attributes: " + file + " - " + e.getMessage());
                }
            }
            
        } catch (IOException e) {
            context.addError("Failed to scan backup directory: " + e.getMessage());
        }
        
        return candidateFiles;
    }
    
    /**
     * 파일 삭제를 처리합니다.
     * 
     * @param candidateFiles 삭제 대상 파일들
     * @param context 정리 작업 컨텍스트
     * @param dryRun 시뮬레이션 모드 여부
     */
    private static void processFileDeletion(List<Path> candidateFiles, CleanupContext context, boolean dryRun) {
        for (Path file : candidateFiles) {
            deleteFile(file, context, dryRun);
        }
    }
    
    /**
     * 단일 파일을 삭제합니다.
     * 
     * @param file 삭제할 파일
     * @param context 정리 작업 컨텍스트
     * @param dryRun 시뮬레이션 모드 여부
     */
    private static void deleteFile(Path file, CleanupContext context, boolean dryRun) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            long fileSize = attrs.size();
            
            if (dryRun) {
                context.recordDeletion(file.toString(), fileSize);
            } else {
                try {
                    Files.delete(file);
                    context.recordDeletion(file.toString(), fileSize);
                } catch (IOException e) {
                    context.addError("Failed to delete file: " + file + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            context.addError("Failed to read file attributes for deletion: " + file + " - " + e.getMessage());
        }
    }
    
    /**
     * 정리 작업의 상태와 결과를 관리하는 내부 클래스
     */
    private static class CleanupContext {
        private final Instant cutoffTime;
        private final List<String> deletedFilePaths = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private int totalFiles = 0;
        private int deletedFiles = 0;
        private long freedSpace = 0;
        
        public CleanupContext(int retentionDays, TimeProvider timeProvider) {
            this.cutoffTime = timeProvider.now().minusSeconds(retentionDays * 24 * 60 * 60L);
        }
        
        public boolean shouldDelete(Instant fileTime) {
            return fileTime.isBefore(cutoffTime) || fileTime.equals(cutoffTime);
        }
        
        public void setTotalFiles(int totalFiles) {
            this.totalFiles = totalFiles;
        }
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void recordDeletion(String filePath, long fileSize) {
            deletedFilePaths.add(filePath);
            deletedFiles++;
            freedSpace += fileSize;
        }
        
        public boolean hasInitialErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public CleanupResult buildResult() {
            return new CleanupResult(totalFiles, deletedFiles, freedSpace, deletedFilePaths, errors);
        }
    }
    
    /**
     * 파일이 백업 파일인지 확인
     * 
     * @param path 파일 경로
     * @return 백업 파일이면 true
     */
    private static boolean isBackupFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".sql") || 
               fileName.endsWith(".sql.gz") || 
               fileName.endsWith(".sql.gzip") ||
               (fileName.contains("backup") && (fileName.endsWith(".sql") || fileName.endsWith(".gz")));
    }
    
    /**
     * 백업 파일 정보를 담는 클래스
     */
    public static class BackupFileInfo {
        private final Path path;
        private final long size;
        private final Instant creationTime;
        private final Instant lastModifiedTime;
        
        public BackupFileInfo(Path path, long size, Instant creationTime, Instant lastModifiedTime) {
            this.path = path;
            this.size = size;
            this.creationTime = creationTime;
            this.lastModifiedTime = lastModifiedTime;
        }
        
        public Path getPath() {
            return path;
        }
        
        public long getSize() {
            return size;
        }
        
        public Instant getCreationTime() {
            return creationTime;
        }
        
        public Instant getLastModifiedTime() {
            return lastModifiedTime;
        }
        
        public LocalDateTime getCreationDateTime() {
            return LocalDateTime.ofInstant(creationTime, ZoneId.systemDefault());
        }
        
        public LocalDateTime getLastModifiedDateTime() {
            return LocalDateTime.ofInstant(lastModifiedTime, ZoneId.systemDefault());
        }
        
        public boolean isOlderThan(int days) {
            return isOlderThan(days, new SystemTimeProvider());
        }
        
        public boolean isOlderThan(int days, TimeProvider timeProvider) {
            Instant cutoff = timeProvider.now().minusSeconds(days * 24 * 60 * 60L);
            return creationTime.isBefore(cutoff);
        }
        
        @Override
        public String toString() {
            return String.format("BackupFileInfo{path=%s, size=%d bytes, created=%s}", 
                    path.getFileName(), size, 
                    getCreationDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
    }
}