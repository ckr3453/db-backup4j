package io.backup4j.core.validation;

import io.backup4j.core.config.*;
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
    void validate_데이터베이스URL비어있음으로_예외발생() {
        // given & when & then - Empty URL should throw during DatabaseConfig creation
        assertThrows(IllegalArgumentException.class, () -> {
            DatabaseConfig.builder()
                .url("")  // Empty URL
                .username("user")
                .password("pass")
                .build();
        });
    }

    @Test
    void validate_잘못된JDBCURL로_예외발생() {
        // given & when & then - Invalid JDBC URL should throw during DatabaseConfig creation
        assertThrows(IllegalArgumentException.class, () -> {
            DatabaseConfig.builder()
                .url("invalid-url")  // Invalid JDBC URL
                .username("user")
                .password("pass")
                .build();
        });
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
                .url("jdbc:mysql://localhost:3306/testdb")
                .username("user")
                .password("pass")
                .build())
            .local(LocalBackupConfig.builder()
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