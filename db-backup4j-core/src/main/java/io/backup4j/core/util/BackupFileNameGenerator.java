package io.backup4j.core.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 백업 파일명 생성 규칙을 관리하는 클래스
 * 일관된 네이밍 컨벤션을 적용하여 백업 파일명을 생성하고 검증함
 */
public class BackupFileNameGenerator {

    private BackupFileNameGenerator() {
    }
    
    // 파일명 패턴: {database}_{timestamp}.sql[.gz]
    private static final String FILENAME_PATTERN = "%s_%s.sql";
    private static final String COMPRESSED_SUFFIX = ".gz";
    
    // 타임스탬프 포맷
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * 백업 파일명 생성
     * 
     * @param databaseName 데이터베이스명
     * @param compressed 압축 여부
     * @return 생성된 파일명
     */
    public static String generateFileName(String databaseName, boolean compressed) {
        return generateFileName(databaseName, LocalDateTime.now(), compressed);
    }
    
    /**
     * 백업 파일명 생성 (시간 지정)
     * 
     * @param databaseName 데이터베이스명
     * @param timestamp 타임스탬프
     * @param compressed 압축 여부
     * @return 생성된 파일명
     */
    public static String generateFileName(String databaseName, LocalDateTime timestamp, boolean compressed) {
        // 데이터베이스명 정제 (특수문자 제거)
        String sanitizedDbName = sanitizeDatabaseName(databaseName);
        
        // 타임스탬프 포맷팅
        String timestampStr = timestamp.format(TIMESTAMP_FORMAT);
        
        // 기본 파일명 생성
        String fileName = String.format(FILENAME_PATTERN, sanitizedDbName, timestampStr);
        
        // 압축 파일인 경우 .gz 확장자 추가
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
}