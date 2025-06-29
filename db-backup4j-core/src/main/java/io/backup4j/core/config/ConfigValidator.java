package io.backup4j.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ConfigValidator {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    
    private static final Pattern HHMM_TIME_PATTERN = Pattern.compile("^([01]?[0-9]|2[0-3]):[0-5][0-9]$");
    
    public static ValidationResult validate(BackupConfig config) {
        List<String> errors = new ArrayList<>();
        
        validateDatabase(config.getDatabase(), errors);
        validateLocalBackup(config.getLocal(), errors);
        validateEmail(config.getEmail(), errors);
        validateS3(config.getS3(), errors);
        validateSchedule(config.getSchedule(), errors);
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    private static void validateDatabase(DatabaseConfig db, List<String> errors) {
        if (db == null) {
            errors.add("Database configuration is required");
            return;
        }
        
        if (db.getType() == null) {
            errors.add("Required property 'database.type' is missing or invalid. Supported types: MYSQL, POSTGRESQL");
        }
        
        if (isEmpty(db.getHost())) {
            errors.add("Database host is required");
        }
        
        if (db.getPort() <= 0 || db.getPort() > 65535) {
            errors.add("Database port must be between 1 and 65535");
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
    
    private static void validateEmail(EmailBackupConfig email, List<String> errors) {
        if (email == null || !email.isEnabled()) {
            return;
        }
        
        if (email.getSmtp() == null) {
            errors.add("SMTP configuration is required when email backup is enabled");
        } else {
            if (isEmpty(email.getSmtp().getHost())) {
                errors.add("Required property 'backup.email.smtp.host' is missing when email is enabled");
            }
            
            if (email.getSmtp().getPort() <= 0 || email.getSmtp().getPort() > 65535) {
                errors.add("SMTP port must be between 1 and 65535");
            }
        }
        
        if (isEmpty(email.getUsername())) {
            errors.add("Required property 'backup.email.username' is missing when email is enabled");
        }
        
        if (isEmpty(email.getPassword())) {
            errors.add("Required property 'backup.email.password' is missing when email is enabled");
        }
        
        if (email.getRecipients() == null || email.getRecipients().isEmpty()) {
            errors.add("Required property 'backup.email.recipients' is missing when email is enabled");
        } else {
            for (String recipient : email.getRecipients()) {
                if (!EMAIL_PATTERN.matcher(recipient.trim()).matches()) {
                    errors.add("Invalid email format: " + recipient);
                }
            }
        }
    }
    
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
    
    private static void validateSchedule(ScheduleConfig schedule, List<String> errors) {
        if (schedule == null || !schedule.isEnabled()) {
            return;
        }
        
        boolean hasSchedule = false;
        
        if (!isEmpty(schedule.getDaily())) {
            hasSchedule = true;
            if (!HHMM_TIME_PATTERN.matcher(schedule.getDaily()).matches()) {
                errors.add("Invalid daily schedule format: " + schedule.getDaily() + ". Use HH:MM format");
            }
        }
        
        if (!isEmpty(schedule.getWeekly())) {
            hasSchedule = true;            
        }
        
        if (!isEmpty(schedule.getMonthly())) {
            hasSchedule = true;            
        }
        
        if (!hasSchedule) {
            errors.add("At least one schedule type (daily, weekly, or monthly) is required when scheduling is enabled");
        }
    }
    
    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    
    private static boolean isValidRetention(String retention) {
        return retention.matches("\\d+");
    }
    
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