package io.backup4j.core.exception;

/**
 * 웹훅 알림 전송 중 발생하는 예외를 나타냅니다.
 */
public class WebhookNotificationException extends Exception {
    
    public WebhookNotificationException(String message) {
        super(message);
    }
    
    public WebhookNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public WebhookNotificationException(Throwable cause) {
        super(cause);
    }
}