package io.backup4j.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CronValidatorTest {

    @Test
    void isValid_유효한cron식_true반환() {
        // given
        String[] validCronExpressions = {
            "0 2 * * *",        // 매일 2시
            "30 14 * * 1",      // 매주 월요일 14시 30분
            "0 0 1 * *",        // 매월 1일 자정
            "*/15 * * * *",     // 15분마다
            "0 9-17 * * 1-5",   // 평일 9-17시
            "0 0 * * 0"         // 매주 일요일 자정
        };
        
        // when & then
        for (String cron : validCronExpressions) {
            assertTrue(CronValidator.isValid(cron), "Should be valid: " + cron);
        }
    }

    @Test
    void isValid_무효한cron식_false반환() {
        // given
        String[] invalidCronExpressions = {
            "",                 // 빈 문자열
            "invalid",          // 잘못된 형식
            "60 * * * *",       // 분이 범위 초과 (0-59)
            "* 25 * * *",       // 시가 범위 초과 (0-23)
            "* * 32 * *",       // 일이 범위 초과 (1-31)
            "* * * 13 *",       // 월이 범위 초과 (1-12)
            "* * * * 8"         // 요일이 범위 초과 (0-7)
        };
        
        // when & then
        for (String cron : invalidCronExpressions) {
            assertFalse(CronValidator.isValid(cron), "Should be invalid: " + cron);
        }
    }

    @Test
    void isValid_null과공백_false반환() {
        // when & then
        assertFalse(CronValidator.isValid(null));
        assertFalse(CronValidator.isValid(""));
        assertFalse(CronValidator.isValid("   "));
    }

    @Test
    void validate_유효한cron식_성공결과반환() {
        // given
        String validCron = "0 2 * * *";
        
        // when
        CronValidator.ValidationResult result = CronValidator.validate(validCron);
        
        // then
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
    }

    @Test
    void validate_무효한cron식_실패결과와메시지반환() {
        // given
        String invalidCron = "invalid cron";
        
        // when
        CronValidator.ValidationResult result = CronValidator.validate(invalidCron);
        
        // then
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Invalid cron expression"));
        assertTrue(result.getErrorMessage().contains("invalid cron"));
    }

    @Test
    void validate_null값_실패결과와메시지반환() {
        // when
        CronValidator.ValidationResult result = CronValidator.validate(null);
        
        // then
        assertFalse(result.isValid());
        assertEquals("Cron expression is required when schedule is enabled", result.getErrorMessage());
    }

    @Test
    void validate_빈문자열_실패결과와메시지반환() {
        // when
        CronValidator.ValidationResult result = CronValidator.validate("");
        
        // then
        assertFalse(result.isValid());
        assertEquals("Cron expression is required when schedule is enabled", result.getErrorMessage());
    }
}