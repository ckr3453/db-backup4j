package io.backup4j.core.validation;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;

/**
 * Cron 표현식 검증기
 * cron-utils 라이브러리를 사용하여 Unix cron 형식을 지원
 * 
 * 지원 형식: 분(0-59) 시(0-23) 일(1-31) 월(1-12) 요일(0-7)
 */
public class CronValidator {
    
    private CronValidator() {
    }
    
    private static final CronDefinition CRON_DEFINITION = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
    private static final CronParser CRON_PARSER = new CronParser(CRON_DEFINITION);
    
    /**
     * Cron 표현식 검증
     * 
     * @param cronExpression 검증할 cron 표현식
     * @return 유효하면 true
     */
    public static boolean isValid(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return false;
        }
        
        try {
            CRON_PARSER.parse(cronExpression.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 상세한 검증 결과 반환
     * 
     * @param cronExpression 검증할 cron 표현식
     * @return 검증 결과와 오류 메시지
     */
    public static ValidationResult validate(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return new ValidationResult(false, "Cron expression is required when schedule is enabled");
        }
        
        try {
            CRON_PARSER.parse(cronExpression.trim());
            return new ValidationResult(true, null);
        } catch (Exception e) {
            return new ValidationResult(false, 
                String.format("Invalid cron expression '%s': %s. Example: '0 2 * * *' (daily at 2 AM)", 
                    cronExpression.trim(), e.getMessage()));
        }
    }
    
    
    /**
     * 검증 결과를 담는 클래스
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        @Override
        public String toString() {
            return String.format("ValidationResult{valid=%s, errorMessage='%s'}", valid, errorMessage);
        }
    }
}