package io.backup4j.core.config;

import io.backup4j.core.database.DatabaseType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigValidatorTest {

    @Test
    void validate_null설정으로_예외발생() {
        // given
        BackupConfig nullConfig = null;
        
        // when
        ConfigValidator.ValidationResult result = ConfigValidator.validate(nullConfig);
        
        // then
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Configuration cannot be null"));
    }

    @Test
    void validate_데이터베이스설정null로_예외발생() {
        // given
        BackupConfig config = new BackupConfig();
        config.setDatabase(null);
        
        // when
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        
        // then
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Database configuration is required"));
    }

    @Test
    void validate_데이터베이스호스트비어있음으로_예외발생() {
        // given
        BackupConfig config = createValidConfig();
        config.getDatabase().setHost("");
        
        // when
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        
        // then
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Database host is required"));
    }

    @Test
    void validate_데이터베이스포트범위초과로_예외발생() {
        // given
        BackupConfig config = createValidConfig();
        config.getDatabase().setPort(0);
        
        // when
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        
        // then
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Database port must be between 1 and 65535"));
    }

    @Test
    void validate_유효한설정으로_검증통과() {
        // given
        BackupConfig config = createValidConfig();
        
        // when
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        
        // then
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    private BackupConfig createValidConfig() {
        BackupConfig config = new BackupConfig();
        
        // Database config
        DatabaseConfig database = new DatabaseConfig();
        database.setType(DatabaseType.MYSQL);
        database.setHost("localhost");
        database.setPort(3306);
        database.setName("testdb");
        database.setUsername("user");
        database.setPassword("pass");
        config.setDatabase(database);
        
        // Other configs (not enabled, so minimal setup)
        LocalBackupConfig local = new LocalBackupConfig();
        local.setEnabled(false);
        config.setLocal(local);
        
        EmailBackupConfig email = new EmailBackupConfig();
        email.setEnabled(false);
        config.setEmail(email);
        
        S3BackupConfig s3 = new S3BackupConfig();
        s3.setEnabled(false);
        config.setS3(s3);
        
        ScheduleConfig schedule = new ScheduleConfig();
        schedule.setEnabled(false);
        config.setSchedule(schedule);
        
        return config;
    }
}