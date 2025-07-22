package io.backup4j.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * 디스크 공간을 체크하는 유틸리티 클래스
 * 백업 실행 전 충분한 디스크 공간이 있는지 확인하고 관리함
 */
public class DiskSpaceChecker {

    private DiskSpaceChecker() {}
    
    private static final Logger logger = Logger.getLogger(DiskSpaceChecker.class.getName());
    
    // 기본 안전 마진 (10%)
    private static final double DEFAULT_SAFETY_MARGIN_PERCENT = 0.1;
    
    // 최소 여유 공간 (100MB)
    private static final long MIN_FREE_SPACE_BYTES = 100 * 1024 * 1024L;
    
    /**
     * 디스크 공간 체크 결과를 담는 클래스
     */
    public static class SpaceCheckResult {
        private final boolean hasEnoughSpace;
        private final long availableSpace;
        private final long totalSpace;
        private final long usedSpace;
        private final long requiredSpace;
        private final double usagePercentage;
        private final String reason;
        
        public SpaceCheckResult(boolean hasEnoughSpace, long availableSpace, long totalSpace, 
                              long usedSpace, long requiredSpace, String reason) {
            this.hasEnoughSpace = hasEnoughSpace;
            this.availableSpace = availableSpace;
            this.totalSpace = totalSpace;
            this.usedSpace = usedSpace;
            this.requiredSpace = requiredSpace;
            this.usagePercentage = totalSpace > 0 ? (double) usedSpace / totalSpace * 100.0 : 0.0;
            this.reason = reason;
        }
        
        public boolean hasEnoughSpace() { return hasEnoughSpace; }
        public long getAvailableSpace() { return availableSpace; }
        public long getTotalSpace() { return totalSpace; }
        public long getUsedSpace() { return usedSpace; }
        public long getRequiredSpace() { return requiredSpace; }
        public double getUsagePercentage() { return usagePercentage; }
        public String getReason() { return reason; }
        
        public String getAvailableSpaceFormatted() {
            return formatBytes(availableSpace);
        }
        
        public String getTotalSpaceFormatted() {
            return formatBytes(totalSpace);
        }
        
        public String getUsedSpaceFormatted() {
            return formatBytes(usedSpace);
        }
        
        public String getRequiredSpaceFormatted() {
            return formatBytes(requiredSpace);
        }
        
        @Override
        public String toString() {
            return String.format("SpaceCheckResult{hasEnoughSpace=%s, available=%s, total=%s, " +
                    "used=%s (%.1f%%), required=%s, reason='%s'}",
                    hasEnoughSpace, getAvailableSpaceFormatted(), getTotalSpaceFormatted(),
                    getUsedSpaceFormatted(), usagePercentage, getRequiredSpaceFormatted(), reason);
        }
    }
    
    /**
     * 기본 디스크 공간 체크 (예상 백업 파일 크기 기반)
     * 
     * @param backupDirectory 백업 디렉토리
     * @param estimatedBackupSize 예상 백업 파일 크기 (바이트)
     * @return 디스크 공간 체크 결과
     */
    public static SpaceCheckResult checkDiskSpace(Path backupDirectory, long estimatedBackupSize) {
        return checkDiskSpace(backupDirectory, estimatedBackupSize, DEFAULT_SAFETY_MARGIN_PERCENT);
    }
    
    /**
     * 디스크 공간 체크 (안전 마진 설정 가능)
     * 
     * @param backupDirectory 백업 디렉토리
     * @param estimatedBackupSize 예상 백업 파일 크기 (바이트)
     * @param safetyMarginPercent 안전 마진 비율 (0.1 = 10%)
     * @return 디스크 공간 체크 결과
     */
    public static SpaceCheckResult checkDiskSpace(Path backupDirectory, long estimatedBackupSize, 
                                                double safetyMarginPercent) {
        try {
            // 디렉토리가 존재하지 않으면 생성
            if (!Files.exists(backupDirectory)) {
                Files.createDirectories(backupDirectory);
                logger.info("Created backup directory: " + backupDirectory);
            }
            
            // 파일 시스템 정보 조회
            FileStore fileStore = Files.getFileStore(backupDirectory);
            long totalSpace = fileStore.getTotalSpace();
            long availableSpace = fileStore.getUsableSpace();
            long usedSpace = totalSpace - availableSpace;
            
            // 안전 마진을 포함한 필요 공간 계산
            long safetyMargin = (long) (estimatedBackupSize * safetyMarginPercent);
            long requiredSpace = estimatedBackupSize + safetyMargin + MIN_FREE_SPACE_BYTES;
            
            logger.info(String.format("Disk space check for %s: available=%s, required=%s", 
                backupDirectory, formatBytes(availableSpace), formatBytes(requiredSpace)));
            
            // 공간 충분성 검사
            if (availableSpace >= requiredSpace) {
                return new SpaceCheckResult(true, availableSpace, totalSpace, usedSpace, 
                    requiredSpace, "충분한 디스크 공간이 있습니다.");
            } else {
                String reason = String.format("디스크 공간이 부족합니다. 필요: %s, 사용가능: %s, 부족: %s",
                    formatBytes(requiredSpace), formatBytes(availableSpace), 
                    formatBytes(requiredSpace - availableSpace));
                return new SpaceCheckResult(false, availableSpace, totalSpace, usedSpace, 
                    requiredSpace, reason);
            }
            
        } catch (IOException e) {
            logger.severe("디스크 공간 체크 중 오류 발생: " + e.getMessage());
            return new SpaceCheckResult(false, 0, 0, 0, estimatedBackupSize, 
                "디스크 공간 체크 실패: " + e.getMessage());
        }
    }
    
    /**
     * 현재 디스크 사용량 조회
     * 
     * @param path 조회할 경로
     * @return 디스크 공간 정보
     */
    public static SpaceCheckResult getCurrentDiskUsage(Path path) {
        try {
            // 경로가 존재하지 않는 경우 부모 디렉토리로 체크
            Path checkPath = path;
            while (checkPath != null && !Files.exists(checkPath)) {
                checkPath = checkPath.getParent();
            }
            
            // 부모 디렉토리도 없으면 현재 작업 디렉토리 사용
            if (checkPath == null) {
                checkPath = new File(".").toPath().toAbsolutePath();
            }
            
            FileStore fileStore = Files.getFileStore(checkPath);
            long totalSpace = fileStore.getTotalSpace();
            long availableSpace = fileStore.getUsableSpace();
            long usedSpace = totalSpace - availableSpace;
            
            String reason = checkPath.equals(path) ? "현재 디스크 사용량" : 
                "부모 디렉토리 디스크 사용량 (" + checkPath + ")";
            
            return new SpaceCheckResult(true, availableSpace, totalSpace, usedSpace, 0, reason);
                
        } catch (IOException e) {
            logger.severe("디스크 사용량 조회 중 오류 발생: " + path);
            return new SpaceCheckResult(false, 0, 0, 0, 0, 
                "디스크 사용량 조회 실패: " + path);
        }
    }
    
    /**
     * 데이터베이스 크기 추정 기반 백업 파일 크기 예상
     * 
     * @param databaseSizeBytes 데이터베이스 크기 (바이트)
     * @param compressionEnabled 압축 활성화 여부
     * @return 예상 백업 파일 크기 (바이트)
     */
    public static long estimateBackupSize(long databaseSizeBytes, boolean compressionEnabled) {
        if (databaseSizeBytes <= 0) {
            // 기본 최소 크기 (1MB)
            return 1024 * 1024;
        }
        
        if (compressionEnabled) {
            // 압축 시 대략 30-50% 크기 감소 예상 (보수적으로 70% 적용)
            return (long) (databaseSizeBytes * 0.7);
        } else {
            // 압축 미사용 시 원본 크기와 비슷하거나 약간 클 수 있음
            return (long) (databaseSizeBytes * 1.1);
        }
    }
    
    /**
     * 백업 디렉토리의 기존 파일들 크기 합계 계산
     * 
     * @param backupDirectory 백업 디렉토리
     * @return 기존 백업 파일들의 총 크기 (바이트)
     */
    public static long calculateExistingBackupSize(Path backupDirectory) {
        if (!Files.exists(backupDirectory) || !Files.isDirectory(backupDirectory)) {
            return 0;
        }
        
        try {
            return Files.walk(backupDirectory, 1)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.getFileName().toString().toLowerCase();
                    return fileName.endsWith(".sql") || fileName.endsWith(".sql.gz") || 
                           fileName.endsWith(".sql.gzip");
                })
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        logger.warning("파일 크기 조회 실패: " + path + " - " + e.getMessage());
                        return 0;
                    }
                })
                .sum();
                
        } catch (IOException e) {
            logger.warning("백업 디렉토리 크기 계산 실패: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * 디스크 공간 부족 경고 임계값 체크
     * 
     * @param path 체크할 경로
     * @param warningThresholdPercent 경고 임계값 (0.8 = 80%)
     * @return 경고가 필요하면 true
     */
    public static boolean shouldWarnDiskSpace(Path path, double warningThresholdPercent) {
        try {
            FileStore fileStore = Files.getFileStore(path);
            long totalSpace = fileStore.getTotalSpace();
            long usedSpace = totalSpace - fileStore.getUsableSpace();
            double usagePercent = (double) usedSpace / totalSpace;
            
            return usagePercent >= warningThresholdPercent;
            
        } catch (IOException e) {
            logger.warning("디스크 공간 경고 체크 실패: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 바이트 크기를 사람이 읽기 쉬운 형태로 변환
     * 
     * @param bytes 바이트 크기
     * @return 포맷된 문자열 (예: "1.5 GB")
     */
    public static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        if (unitIndex == 0) {
            return String.format("%d %s", (long) size, units[unitIndex]);
        } else {
            return String.format("%.1f %s", size, units[unitIndex]);
        }
    }
    
    /**
     * 백업 전 종합 디스크 공간 체크
     * 
     * @param backupDirectory 백업 디렉토리
     * @param estimatedBackupSize 예상 백업 파일 크기
     * @param compressionEnabled 압축 활성화 여부
     * @return 종합 체크 결과
     */
    public static SpaceCheckResult comprehensiveSpaceCheck(Path backupDirectory, 
                                                         long estimatedBackupSize, 
                                                         boolean compressionEnabled) {
        logger.info("백업 전 종합 디스크 공간 체크 시작...");
        
        // 현재 디스크 사용량 조회
        SpaceCheckResult currentUsage = getCurrentDiskUsage(backupDirectory);
        if (!currentUsage.hasEnoughSpace()) {
            return currentUsage;
        }
        
        // 기존 백업 파일 크기 계산
        long existingBackupSize = calculateExistingBackupSize(backupDirectory);
        
        // 압축 고려한 예상 크기 재계산
        long adjustedEstimatedSize = estimateBackupSize(estimatedBackupSize, compressionEnabled);
        
        // 디스크 공간 체크 수행
        SpaceCheckResult spaceCheck = checkDiskSpace(backupDirectory, adjustedEstimatedSize);
        
        // 디스크 공간 부족 경고 체크 (80% 이상 사용시)
        if (spaceCheck.hasEnoughSpace() && shouldWarnDiskSpace(backupDirectory, 0.8)) {
            logger.warning(String.format("디스크 사용량이 높습니다: %.1f%% 사용 중", 
                spaceCheck.getUsagePercentage()));
        }
        
        logger.info(String.format("종합 디스크 공간 체크 완료: %s, 기존 백업 크기: %s", 
            spaceCheck, formatBytes(existingBackupSize)));
        
        return spaceCheck;
    }
    
    /**
     * 백업 디렉토리를 위한 파일 시스템 정보 조회
     * 
     * @param backupDirectory 백업 디렉토리
     * @return 파일 시스템 정보 문자열
     */
    public static String getFileSystemInfo(Path backupDirectory) {
        try {
            FileStore fileStore = Files.getFileStore(backupDirectory);
            return String.format("FileSystem: %s, Type: %s, ReadOnly: %s",
                fileStore.name(), fileStore.type(), fileStore.isReadOnly());
        } catch (IOException e) {
            return "파일 시스템 정보 조회 실패: " + e.getMessage();
        }
    }
}