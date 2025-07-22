package io.backup4j.core.exception;

/**
 * 백업 실행 중 발생하는 예외를 나타냅니다.
 */
public class BackupExecutionException extends Exception {
    
    public BackupExecutionException(String message) {
        super(message);
    }
    
    public BackupExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public BackupExecutionException(Throwable cause) {
        super(cause);
    }
}