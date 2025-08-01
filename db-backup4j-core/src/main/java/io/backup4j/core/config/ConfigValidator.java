package io.backup4j.core.util;

import io.backup4j.core.config.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 백업 설정의 유효성을 검증하는 클래스입니다.
 * 데이터베이스, 로컬 백업, S3 백업, 스케줄 설정의 유효성을 검사합니다.
 */
public class ConfigValidator {

    private ConfigValidator(){
    }

    /**
     * 백업 설정 전체의 유효성을 검증합니다.
     * 
     * @param config 검증할 백업 설정
     * @return 검증 결과와 오류 메시지 목록
     */
    public static ValidationResult validate(BackupConfig config) {
        List<String> errors = new ArrayList<>();
        
        if (config == null) {
            errors.add("Configuration cannot be null");
            return new ValidationResult(false, errors);
        }
        
        validateDatabase(config.getDatabase(), errors);
        validateLocalBackup(config.getLocal(), errors);
        validateS3(config.getS3(), errors);
        validateSchedule(config.getSchedule(), errors);
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 데이터베이스 연결 설정을 검증합니다.
     * 
     * @param db 데이터베이스 설정
     * @param errors 오류 메시지 목록
     */
    private static void validateDatabase(DatabaseConfig db, List<String> errors) {
        if (db == null) {
            errors.add("Database configuration is required");
            return;
        }
        
        if (isEmpty(db.getUrl())) {
            errors.add("Required property 'database.url' is missing");
            return;
        }
        
        // JDBC URL 형식 검증
        try {
            String url = db.getUrl();
            if (!url.startsWith("jdbc:")) {
                errors.add("Database URL must start with 'jdbc:'");
            }
            
            // URL 자체의 유효성 검증은 DatabaseConfig 생성 시 이미 수행됨
            // 여기서는 추가 검증만 수행
            
        } catch (Exception e) {
            errors.add("Invalid database URL format: " + e.getMessage());
        }
        
        if (isEmpty(db.getUsername())) {
            errors.add("Required property 'database.username' is missing");
        }
        
        if (isEmpty(db.getPassword())) {
            errors.add("Required property 'database.password' is missing");
        }
    }
    
    /**
     * 로컬 백업 설정을 검증합니다.
     * 
     * @param local 로컬 백업 설정
     * @param errors 오류 메시지 목록
     */
    private static void validateLocalBackup(LocalBackupConfig local, List<String> errors) {
        if (local == null || !local.isEnabled()) {
            return;
        }
        
        if (isEmpty(local.getPath())) {
            errors.add("Local backup path is required when local backup is enabled");
        }
        
        if (!isEmpty(local.getRetention()) && !isValidRetention(local.getRetention())) {
            errors.add("Invalid retention format: " + local.getRetention() + ". Use number format like '30' (days)");
        }
    }
    
    /**
     * S3 백업 설정을 검증합니다.
     * 
     * @param s3 S3 백업 설정
     * @param errors 오류 메시지 목록
     */
    private static void validateS3(S3BackupConfig s3, List<String> errors) {
        if (s3 == null || !s3.isEnabled()) {
            return;
        }
        
        if (isEmpty(s3.getBucket())) {
            errors.add("Required property 'backup.s3.bucket' is missing when S3 is enabled");
        }
        
        if (isEmpty(s3.getRegion())) {
            errors.add("S3 region is required when S3 backup is enabled");
        }
        
        if (isEmpty(s3.getAccessKey())) {
            errors.add("Required property 'backup.s3.access-key' is missing when S3 is enabled");
        }
        
        if (isEmpty(s3.getSecretKey())) {
            errors.add("Required property 'backup.s3.secret-key' is missing when S3 is enabled");
        }
    }
    
    /**
     * 스케줄 설정을 검증합니다.
     * 
     * @param schedule 스케줄 설정
     * @param errors 오류 메시지 목록
     */
    private static void validateSchedule(ScheduleConfig schedule, List<String> errors) {
        if (schedule == null || !schedule.isEnabled()) {
            return;
        }
        
        if (isEmpty(schedule.getCron())) {
            errors.add("Cron expression is required when schedule is enabled");
            return;
        }
        
        CronValidator.ValidationResult cronResult = CronValidator.validate(schedule.getCron());
        if (!cronResult.isValid()) {
            errors.add(cronResult.getErrorMessage());
        }
    }
    
    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    
    /**
     * 백업 보관 기간 형식이 유효한지 검사합니다.
     * 
     * @param retention 보관 기간 문자열
     * @return 숫자 형식이면 true, 그렇지 않으면 false
     */
    private static boolean isValidRetention(String retention) {
        return retention.matches("\\d+");
    }
    
    /**
     * 설정 검증 결과를 담는 클래스입니다.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        
        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
    }
}