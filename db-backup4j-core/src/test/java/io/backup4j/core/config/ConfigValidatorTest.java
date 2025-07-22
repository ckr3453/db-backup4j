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
        BackupConfig config = BackupConfig.builder()
            .database(null)
            .local(LocalBackupConfig.builder().build())
            .notification(NotificationConfig.builder().build())
            .s3(S3BackupConfig.builder().build())
            .schedule(ScheduleConfig.builder().build())
            .build();
        
        // when
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        
        // then
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Database configuration is required"));
    }

    @Test
    void validate_데이터베이스호스트비어있음으로_예외발생() {
        // given
        BackupConfig config = BackupConfig.builder()
            .database(DatabaseConfig.builder()
                .type(DatabaseType.MYSQL)
                .host("")  // Empty host
                .port(3306)
                .name("testdb")
                .username("user")
                .password("pass")
                .build())
            .local(LocalBackupConfig.builder().enabled(false).build())
            .notification(NotificationConfig.builder().enabled(false).build())
            .s3(S3BackupConfig.builder().enabled(false).build())
            .schedule(ScheduleConfig.builder().enabled(false).build())
            .build();
        
        // when
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        
        // then
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Database host is required"));
    }

    @Test
    void validate_데이터베이스포트범위초과로_예외발생() {
        // given
        BackupConfig config = BackupConfig.builder()
            .database(DatabaseConfig.builder()
                .type(DatabaseType.MYSQL)
                .host("localhost")
                .port(0)  // Invalid port
                .name("testdb")
                .username("user")
                .password("pass")
                .build())
            .local(LocalBackupConfig.builder().enabled(false).build())
            .notification(NotificationConfig.builder().enabled(false).build())
            .s3(S3BackupConfig.builder().enabled(false).build())
            .schedule(ScheduleConfig.builder().enabled(false).build())
            .build();
        
        // when
        ConfigValidator.ValidationResult result = ConfigValidator.validate(config);
        
        // then
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Database port must be between " + ConfigDefaults.MIN_PORT + " and " + ConfigDefaults.MAX_PORT));
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
        return BackupConfig.builder()
            .database(DatabaseConfig.builder()
                .type(DatabaseType.MYSQL)
                .host("localhost")
                .port(3306)
                .name("testdb")
                .username("user")
                .password("pass")
                .build())
            .local(LocalBackupConfig.builder()
                .enabled(false)
                .build())
            .notification(NotificationConfig.builder()
                .enabled(false)
                .build())
            .s3(S3BackupConfig.builder()
                .enabled(false)
                .build())
            .schedule(ScheduleConfig.builder()
                .enabled(false)
                .build())
            .build();
    }
}