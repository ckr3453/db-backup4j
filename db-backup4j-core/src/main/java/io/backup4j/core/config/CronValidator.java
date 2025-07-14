package io.backup4j.core.config;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;

import java.util.logging.Logger;

public class CronValidator {
    private CronValidator() {
    }

    private static final Logger logger = Logger.getLogger(CronValidator.class.getName());
    
    private static final CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
    private static final CronParser parser = new CronParser(cronDefinition);
    
    /**
     * Cron 표현식의 유효성을 검증합니다.
     * Unix 형식의 5필드 cron을 지원합니다: 분 시 일 월 요일
     * 
     * @param cronExpression 검증할 cron 표현식
     * @return 유효하면 true, 그렇지 않으면 false
     */
    public static boolean isValid(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return false;
        }
        
        try {
            parser.parse(cronExpression.trim());
            return true;
        } catch (Exception e) {
            logger.warning("Invalid cron expression: " + cronExpression + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Cron 표현식을 검증하고 상세한 오류 메시지를 반환합니다.
     * 
     * @param cronExpression 검증할 cron 표현식
     * @return 검증 결과와 오류 메시지
     */
    public static ValidationResult validate(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return new ValidationResult(false, "Cron expression is required when schedule is enabled");
        }
        
        try {
            parser.parse(cronExpression.trim());
            return new ValidationResult(true, null);
        } catch (Exception e) {
            String errorMessage = String.format(
                "Invalid cron expression '%s': %s. Expected format: 'minute hour day month dayOfWeek' (e.g., '0 2 * * *' for daily at 2 AM)", 
                cronExpression, e.getMessage()
            );
            return new ValidationResult(false, errorMessage);
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
    }
}