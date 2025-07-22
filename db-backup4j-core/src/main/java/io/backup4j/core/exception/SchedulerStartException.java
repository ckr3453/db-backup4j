package io.backup4j.core.exception;

/**
 * 스케줄러 시작 중 발생하는 예외를 나타냅니다.
 */
public class SchedulerStartException extends Exception {
    
    public SchedulerStartException(String message) {
        super(message);
    }
    
    public SchedulerStartException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public SchedulerStartException(Throwable cause) {
        super(cause);
    }
}