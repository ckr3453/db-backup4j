package io.backup4j.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 백업 설정의 유효성을 검증하는 클래스입니다.
 * 데이터베이스, 로컬 백업, 알림 설정, S3 백업, 스케줄 설정의 유효성을 검사합니다.
 */
public class ConfigValidator {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    
    
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
        validateNotification(config.getNotification(), errors);
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
        
        if (db.getType() == null) {
            errors.add("Database type is required");
        }
        
        if (isEmpty(db.getHost())) {
            errors.add("Database host is required");
        }
        
        if (db.getPort() < ConfigDefaults.MIN_PORT || db.getPort() > ConfigDefaults.MAX_PORT) {
            errors.add("Database port must be between " + ConfigDefaults.MIN_PORT + " and " + ConfigDefaults.MAX_PORT);
        }
        
        if (isEmpty(db.getName())) {
            errors.add("Required property 'database.name' is missing");
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
     * 알림 설정을 검증합니다.
     * 
     * @param notification 알림 설정
     * @param errors 오류 메시지 목록
     */
    private static void validateNotification(NotificationConfig notification, List<String> errors) {
        if (notification == null || !notification.isEnabled()) {
            return;
        }
        
        // 이메일 알림 검증
        validateEmailNotification(notification.getEmail(), errors);
        
        // 웹훅 알림 검증
        validateWebhookNotification(notification.getWebhook(), errors);
        
        // 최소 하나의 알림 방법이 활성화되어야 함
        if (!notification.hasEnabledNotifiers()) {
            errors.add("At least one notification method (email or webhook) must be enabled when notifications are enabled");
        }
    }
    
    /**
     * 이메일 알림 설정을 검증합니다.
     * 
     * @param email 이메일 알림 설정
     * @param errors 오류 메시지 목록
     */
    private static void validateEmailNotification(NotificationConfig.EmailConfig email, List<String> errors) {
        if (email == null || !email.isEnabled()) {
            return;
        }
        
        if (email.getSmtp() == null) {
            errors.add("SMTP configuration is required when email notifications are enabled");
        } else {
            if (isEmpty(email.getSmtp().getHost())) {
                errors.add("Required property 'notification.email.smtp.host' is missing when email notifications are enabled");
            }
            
            if (email.getSmtp().getPort() < ConfigDefaults.MIN_PORT || email.getSmtp().getPort() > ConfigDefaults.MAX_PORT) {
                errors.add("SMTP port must be between " + ConfigDefaults.MIN_PORT + " and " + ConfigDefaults.MAX_PORT);
            }
        }
        
        if (isEmpty(email.getUsername())) {
            errors.add("Required property 'notification.email.username' is missing when email notifications are enabled");
        }
        
        if (isEmpty(email.getPassword())) {
            errors.add("Required property 'notification.email.password' is missing when email notifications are enabled");
        }
        
        if (email.getRecipients() == null || email.getRecipients().isEmpty()) {
            errors.add("Required property 'notification.email.recipients' is missing when email notifications are enabled");
        } else {
            for (String recipient : email.getRecipients()) {
                if (!EMAIL_PATTERN.matcher(recipient.trim()).matches()) {
                    errors.add("Invalid email format: " + recipient);
                }
            }
        }
    }
    
    /**
     * 웹훅 알림 설정을 검증합니다.
     * 
     * @param webhook 웹훅 알림 설정
     * @param errors 오류 메시지 목록
     */
    private static void validateWebhookNotification(NotificationConfig.WebhookConfig webhook, List<String> errors) {
        if (webhook == null || !webhook.isEnabled()) {
            return;
        }
        
        if (isEmpty(webhook.getUrl())) {
            errors.add("Required property 'notification.webhook.url' is missing when webhook notifications are enabled");
        } else {
            if (!isValidUrl(webhook.getUrl())) {
                errors.add("Invalid webhook URL format: " + webhook.getUrl());
            }
        }
        
        if (webhook.getTimeout() <= 0) {
            errors.add("Webhook timeout must be greater than 0 seconds");
        }
        
        if (webhook.getRetryCount() < 0) {
            errors.add("Webhook retry count must be 0 or greater");
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
     * URL 형식이 유효한지 검사합니다.
     * 
     * @param url 검사할 URL
     * @return 유효한 URL 형식이면 true, 그렇지 않으면 false
     */
    private static boolean isValidUrl(String url) {
        try {
            java.net.URL urlObj = new java.net.URL(url);
            String protocol = urlObj.getProtocol();
            return "http".equals(protocol) || "https".equals(protocol);
        } catch (Exception e) {
            return false;
        }
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