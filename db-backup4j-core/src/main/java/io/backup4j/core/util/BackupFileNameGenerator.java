package io.backup4j.core.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 백업 파일명 생성 규칙을 관리하는 클래스
 * 일관된 네이밍 컨벤션을 적용하여 백업 파일명을 생성하고 검증함
 */
public class BackupFileNameGenerator {
    
    // 파일명 패턴: {database}_{timestamp}_{type}.sql[.gz]
    private static final String FILENAME_PATTERN = "%s_%s_%s.sql";
    private static final String COMPRESSED_SUFFIX = ".gz";
    
    // 타임스탬프 포맷
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    
    // 백업 타입
    public enum BackupType {
        FULL("full"),
        INCREMENTAL("incr"),
        DIFFERENTIAL("diff"),
        SCHEDULED("sched"),
        MANUAL("manual");
        
        private final String code;
        
        BackupType(String code) {
            this.code = code;
        }
        
        public String getCode() {
            return code;
        }
    }
    
    // 백업 파일명 패턴을 검증하는 정규식
    private static final Pattern BACKUP_FILE_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_]+_\\d{8}_\\d{6}_(full|incr|diff|sched|manual)\\.sql(\\.gz)?$"
    );
    
    /**
     * 백업 파일명 생성 (기본: MANUAL 타입)
     * 
     * @param databaseName 데이터베이스명
     * @param compressed 압축 여부
     * @return 생성된 파일명
     */
    public static String generateFileName(String databaseName, boolean compressed) {
        return generateFileName(databaseName, BackupType.MANUAL, compressed);
    }
    
    /**
     * 백업 파일명 생성
     * 
     * @param databaseName 데이터베이스명
     * @param backupType 백업 타입
     * @param compressed 압축 여부
     * @return 생성된 파일명
     */
    public static String generateFileName(String databaseName, BackupType backupType, boolean compressed) {
        return generateFileName(databaseName, backupType, LocalDateTime.now(), compressed);
    }
    
    /**
     * 백업 파일명 생성 (시간 지정)
     * 
     * @param databaseName 데이터베이스명
     * @param backupType 백업 타입
     * @param timestamp 타임스탬프
     * @param compressed 압축 여부
     * @return 생성된 파일명
     */
    public static String generateFileName(String databaseName, BackupType backupType, 
                                        LocalDateTime timestamp, boolean compressed) {
        // 데이터베이스명 정제 (특수문자 제거)
        String sanitizedDbName = sanitizeDatabaseName(databaseName);
        
        // 타임스탬프 포맷팅
        String timestampStr = timestamp.format(TIMESTAMP_FORMAT);
        
        // 기본 파일명 생성
        String fileName = String.format(FILENAME_PATTERN, 
            sanitizedDbName, timestampStr, backupType.getCode());
        
        // 압축 파일인 경우 .gz 확장자 추가
        if (compressed) {
            fileName += COMPRESSED_SUFFIX;
        }
        
        return fileName;
    }
    
    /**
     * 일일 백업 파일명 생성 (날짜만 포함)
     * 
     * @param databaseName 데이터베이스명
     * @param backupType 백업 타입
     * @param compressed 압축 여부
     * @return 생성된 파일명
     */
    public static String generateDailyFileName(String databaseName, BackupType backupType, boolean compressed) {
        String sanitizedDbName = sanitizeDatabaseName(databaseName);
        String dateStr = LocalDateTime.now().format(DATE_FORMAT);
        
        String fileName = String.format("%s_%s_daily_%s.sql", 
            sanitizedDbName, dateStr, backupType.getCode());
        
        if (compressed) {
            fileName += COMPRESSED_SUFFIX;
        }
        
        return fileName;
    }
    
    /**
     * 데이터베이스명 정제 (파일명에 사용할 수 없는 문자 제거)
     * 
     * @param databaseName 원본 데이터베이스명
     * @return 정제된 데이터베이스명
     */
    public static String sanitizeDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            return "unknown";
        }
        
        // 파일명에 사용할 수 없는 문자들을 언더스코어로 치환
        String sanitized = databaseName.trim()
            .replaceAll("[^a-zA-Z0-9_-]", "_")
            .replaceAll("_{2,}", "_")  // 연속된 언더스코어 제거
            .replaceAll("^_+|_+$", ""); // 시작/끝 언더스코어 제거
        
        // 빈 문자열인 경우 기본값 반환
        if (sanitized.isEmpty()) {
            return "unknown";
        }
        
        return sanitized;
    }
    
    /**
     * 파일명이 백업 파일 패턴과 일치하는지 확인
     * 
     * @param fileName 파일명
     * @return 백업 파일이면 true
     */
    public static boolean isValidBackupFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        return BACKUP_FILE_PATTERN.matcher(fileName.trim()).matches();
    }
    
    /**
     * 파일명에서 데이터베이스명 추출
     * 
     * @param fileName 백업 파일명
     * @return 데이터베이스명 (추출 실패시 null)
     */
    public static String extractDatabaseName(String fileName) {
        if (!isValidBackupFileName(fileName)) {
            return null;
        }
        
        String[] parts = fileName.split("_");
        if (parts.length >= 3) {
            return parts[0];
        }
        
        return null;
    }
    
    /**
     * 파일명에서 백업 타입 추출
     * 
     * @param fileName 백업 파일명
     * @return 백업 타입 (추출 실패시 null)
     */
    public static BackupType extractBackupType(String fileName) {
        if (!isValidBackupFileName(fileName)) {
            return null;
        }
        
        String[] parts = fileName.replace(".sql.gz", "").replace(".sql", "").split("_");
        if (parts.length >= 4) {
            String typeCode = parts[3];
            for (BackupType type : BackupType.values()) {
                if (type.getCode().equals(typeCode)) {
                    return type;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 파일명에서 타임스탬프 추출
     * 
     * @param fileName 백업 파일명
     * @return 타임스탬프 (추출 실패시 null)
     */
    public static LocalDateTime extractTimestamp(String fileName) {
        if (!isValidBackupFileName(fileName)) {
            return null;
        }
        
        String[] parts = fileName.split("_");
        if (parts.length >= 3) {
            try {
                String timestampStr = parts[1] + "_" + parts[2];
                return LocalDateTime.parse(timestampStr, TIMESTAMP_FORMAT);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * 파일명이 압축 파일인지 확인
     * 
     * @param fileName 파일명
     * @return 압축 파일이면 true
     */
    public static boolean isCompressedFile(String fileName) {
        return fileName != null && fileName.endsWith(COMPRESSED_SUFFIX);
    }
    
    /**
     * 백업 파일명 예시 생성
     * 
     * @return 파일명 예시 목록
     */
    public static String[] getExampleFileNames() {
        return new String[] {
            "myapp_20240116_143020_manual.sql",
            "myapp_20240116_143020_manual.sql.gz",
            "userdb_20240116_090000_sched.sql.gz",
            "inventory_20240116_daily_full.sql.gz",
            "analytics_20240116_120000_incr.sql"
        };
    }
    
    /**
     * 백업 파일명 정보를 요약하는 클래스
     */
    public static class BackupFileInfo {
        private final String fileName;
        private final String databaseName;
        private final BackupType backupType;
        private final LocalDateTime timestamp;
        private final boolean compressed;
        
        public BackupFileInfo(String fileName) {
            this.fileName = fileName;
            this.databaseName = extractDatabaseName(fileName);
            this.backupType = extractBackupType(fileName);
            this.timestamp = extractTimestamp(fileName);
            this.compressed = isCompressedFile(fileName);
        }
        
        public String getFileName() { return fileName; }
        public String getDatabaseName() { return databaseName; }
        public BackupType getBackupType() { return backupType; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public boolean isCompressed() { return compressed; }
        public boolean isValid() { return isValidBackupFileName(fileName); }
        
        @Override
        public String toString() {
            return String.format("BackupFileInfo{fileName='%s', database='%s', type=%s, timestamp=%s, compressed=%s}", 
                fileName, databaseName, backupType, timestamp, compressed);
        }
    }
}