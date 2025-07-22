package io.backup4j.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 백업 파일 정리 정책을 관리하는 클래스
 * 설정된 보존 기간보다 오래된 백업 파일들을 자동으로 삭제하고 관리함
 */
public class RetentionPolicy {
    
    private static final Logger logger = Logger.getLogger(RetentionPolicy.class.getName());
    
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
    public static CleanupResult cleanup(Path backupDirectory, int retentionDays) {
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
    public static CleanupResult cleanup(Path backupDirectory, int retentionDays, boolean dryRun) {
        logger.info("Starting backup cleanup in directory: " + backupDirectory + 
                   ", retention: " + retentionDays + " days, dryRun: " + dryRun);
        
        List<String> deletedFilePaths = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int totalFiles = 0;
        int deletedFiles = 0;
        long freedSpace = 0;
        
        if (!Files.exists(backupDirectory) || !Files.isDirectory(backupDirectory)) {
            String error = "Backup directory does not exist or is not a directory: " + backupDirectory;
            logger.warning(error);
            errors.add(error);
            return new CleanupResult(0, 0, 0, deletedFilePaths, errors);
        }
        
        try {
            // 현재 시간에서 보존 기간을 뺀 임계점 계산
            Instant cutoffTime = Instant.now().minusSeconds(retentionDays * 24 * 60 * 60L);
            logger.info("Cutoff time for cleanup: " + cutoffTime);
            
            // 백업 파일 패턴으로 필터링 (*.sql, *.sql.gz)
            try (Stream<Path> files = Files.walk(backupDirectory, 1)) {
                List<Path> backupFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> isBackupFile(path))
                    .collect(Collectors.toList());
                
                totalFiles = backupFiles.size();
                logger.info("Found " + totalFiles + " backup files");
                
                // 파일 생성 시간을 기준으로 정리 대상 파일 선별
                for (Path file : backupFiles) {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                        Instant fileTime = attrs.creationTime().toInstant();
                        
                        if (fileTime.isBefore(cutoffTime)) {
                            long fileSize = attrs.size();
                            
                            if (dryRun) {
                                logger.info("[DRY RUN] Would delete: " + file + " (size: " + fileSize + " bytes)");
                                deletedFilePaths.add(file.toString());
                                deletedFiles++;
                                freedSpace += fileSize;
                            } else {
                                try {
                                    Files.delete(file);
                                    logger.info("Deleted backup file: " + file + " (size: " + fileSize + " bytes)");
                                    deletedFilePaths.add(file.toString());
                                    deletedFiles++;
                                    freedSpace += fileSize;
                                } catch (IOException e) {
                                    String error = "Failed to delete file: " + file + " - " + e.getMessage();
                                    logger.warning(error);
                                    errors.add(error);
                                }
                            }
                        }
                    } catch (IOException e) {
                        String error = "Failed to read file attributes: " + file + " - " + e.getMessage();
                        logger.warning(error);
                        errors.add(error);
                    }
                }
            }
            
        } catch (IOException e) {
            String error = "Failed to scan backup directory: " + e.getMessage();
            logger.severe(error);
            errors.add(error);
        }
        
        CleanupResult result = new CleanupResult(totalFiles, deletedFiles, freedSpace, deletedFilePaths, errors);
        logger.info("Cleanup completed: " + result);
        
        return result;
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
     * 디렉토리의 모든 백업 파일 목록을 최신순으로 조회
     * 
     * @param backupDirectory 백업 디렉토리
     * @return 백업 파일 목록 (최신순)
     */
    public static List<BackupFileInfo> listBackupFiles(Path backupDirectory) {
        List<BackupFileInfo> backupFiles = new ArrayList<>();
        
        if (!Files.exists(backupDirectory) || !Files.isDirectory(backupDirectory)) {
            logger.warning("Backup directory does not exist: " + backupDirectory);
            return backupFiles;
        }
        
        try (Stream<Path> files = Files.walk(backupDirectory, 1)) {
            backupFiles = files
                .filter(Files::isRegularFile)
                .filter(RetentionPolicy::isBackupFile)
                .map(path -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        return new BackupFileInfo(
                            path,
                            attrs.size(),
                            attrs.creationTime().toInstant(),
                            attrs.lastModifiedTime().toInstant()
                        );
                    } catch (IOException e) {
                        logger.warning("Failed to read file attributes: " + path + " - " + e.getMessage());
                        return null;
                    }
                })
                .filter(info -> info != null)
                .sorted(Comparator.comparing(BackupFileInfo::getCreationTime).reversed())
                .collect(Collectors.toList());
                
        } catch (IOException e) {
            logger.severe("Failed to list backup files: " + e.getMessage());
        }
        
        return backupFiles;
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
            Instant cutoff = Instant.now().minusSeconds(days * 24 * 60 * 60L);
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