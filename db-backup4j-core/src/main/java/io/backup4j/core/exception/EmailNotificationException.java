package io.backup4j.core.exception;

/**
 * 이메일 알림 전송 중 발생하는 예외를 나타냅니다.
 */
public class EmailNotificationException extends Exception {
    
    public EmailNotificationException(String message) {
        super(message);
    }
    
    public EmailNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public EmailNotificationException(Throwable cause) {
        super(cause);
    }
}