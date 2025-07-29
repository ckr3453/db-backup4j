package io.backup4j.core.config;

import java.util.regex.Pattern;

/**
 * Cron 표현식의 유효성을 검증하는 클래스입니다.
 * Unix 형식의 5필드 cron 표현식을 지원합니다.
 */
public class CronValidator {
    private CronValidator() {
    }

    // 각 필드에 대한 정규식 패턴
    private static final String MINUTE_PATTERN = "([0-5]?\\d|\\*|\\*/[1-9]\\d*|[0-5]?\\d-[0-5]?\\d|[0-5]?\\d(?:,[0-5]?\\d)*)";
    private static final String HOUR_PATTERN = "(1?\\d|2[0-3]|\\*|\\*/[1-9]\\d*|(?:1?\\d|2[0-3])-(?:1?\\d|2[0-3])|(?:1?\\d|2[0-3])(?:,(?:1?\\d|2[0-3]))*)";
    private static final String DAY_PATTERN = "([1-9]|[12]\\d|3[01]|\\*|\\*/[1-9]\\d*|[1-9]-(?:[12]\\d|3[01])|[1-9](?:,[1-9]|,[12]\\d|,3[01])*)";
    private static final String MONTH_PATTERN = "([1-9]|1[0-2]|\\*|\\*/[1-9]\\d*|[1-9]-(?:[1-9]|1[0-2])|[1-9](?:,[1-9]|,1[0-2])*)";
    private static final String WEEKDAY_PATTERN = "([0-7]|\\*|\\*/[1-7]|[0-7]-[0-7]|[0-7](?:,[0-7])*)";
    
    // 전체 cron 표현식 패턴 (5필드)
    private static final Pattern CRON_PATTERN = Pattern.compile(
        "^" + MINUTE_PATTERN + "\\s+" + HOUR_PATTERN + "\\s+" + DAY_PATTERN + "\\s+" + MONTH_PATTERN + "\\s+" + WEEKDAY_PATTERN + "$"
    );
    
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
        
        String trimmed = cronExpression.trim();
        
        // 기본적인 구조 검증 (5개 필드)
        String[] fields = trimmed.split("\\s+");
        if (fields.length != 5) {
            return false;
        }
        
        // 정규식으로 각 필드 검증
        if (!CRON_PATTERN.matcher(trimmed).matches()) {
            return false;
        }
        
        // 추가 비즈니스 로직 검증
        return validateFieldValues(fields);
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
        
        String trimmed = cronExpression.trim();
        
        // 기본적인 구조 검증
        String[] fields = trimmed.split("\\s+");
        if (fields.length != 5) {
            return new ValidationResult(false, 
                String.format("Invalid cron expression '%s': Expected 5 fields (minute hour day month dayOfWeek), got %d. Example: '0 2 * * *'", 
                    cronExpression, fields.length));
        }
        
        // 정규식 검증
        if (!CRON_PATTERN.matcher(trimmed).matches()) {
            return new ValidationResult(false, 
                String.format("Invalid cron expression '%s': Invalid field format. Expected format: 'minute hour day month dayOfWeek' (e.g., '0 2 * * *' for daily at 2 AM)", 
                    cronExpression));
        }
        
        // 추가 비즈니스 로직 검증
        if (!validateFieldValues(fields)) {
            return new ValidationResult(false, 
                String.format("Invalid cron expression '%s': Field values out of range. Valid ranges: minute(0-59), hour(0-23), day(1-31), month(1-12), weekday(0-7)", 
                    cronExpression));
        }
        
        return new ValidationResult(true, null);
    }
    
    /**
     * 각 필드의 값이 유효한 범위 내에 있는지 검증합니다.
     * 
     * @param fields cron 표현식의 각 필드 배열
     * @return 모든 필드가 유효하면 true
     */
    private static boolean validateFieldValues(String[] fields) {
        try {
            // 각 필드에서 숫자 값들을 검증
            if (!validateNumericField(fields[0], 0, 59)) return false; // minute
            if (!validateNumericField(fields[1], 0, 23)) return false; // hour  
            if (!validateNumericField(fields[2], 1, 31)) return false; // day
            if (!validateNumericField(fields[3], 1, 12)) return false; // month
            if (!validateNumericField(fields[4], 0, 7)) return false;  // weekday (0과 7 모두 일요일)
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 숫자 필드의 유효성을 검증합니다.
     * 
     * @param field 검증할 필드 문자열
     * @param min 최소값
     * @param max 최대값
     * @return 유효하면 true
     */
    private static boolean validateNumericField(String field, int min, int max) {
        if ("*".equals(field)) {
            return true;
        }
        
        // */n 형태 처리
        if (field.startsWith("*/")) {
            try {
                int step = Integer.parseInt(field.substring(2));
                return step > 0 && step <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        // 범위나 리스트가 포함된 복잡한 경우는 정규식에서 이미 검증됨
        // 여기서는 단순 숫자만 추가 검증
        if (field.matches("\\d+")) {
            try {
                int value = Integer.parseInt(field);
                return value >= min && value <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        // 복잡한 패턴은 정규식에서 검증되었으므로 통과
        return true;
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