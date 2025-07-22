package io.backup4j.core.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 시스템 전반에서 사용되는 상수들을 정의하는 클래스
 */
public final class Constants {
    
    private Constants() {
    }
    
    // Database Connection Constants
    public static final int DEFAULT_CONNECTION_TIMEOUT_MS = 30000; // 30 seconds
    public static final int DEFAULT_SOCKET_TIMEOUT_MS = 30000;     // 30 seconds
    
    // Connection Pool Constants
    public static final int HIKARI_MINIMUM_IDLE = 2;
    public static final int HIKARI_MAXIMUM_POOL_SIZE = 5;
    public static final long HIKARI_CONNECTION_TIMEOUT_MS = 30000;
    public static final long HIKARI_IDLE_TIMEOUT_MS = 600000;      // 10 minutes
    public static final long HIKARI_MAX_LIFETIME_MS = 1800000;     // 30 minutes
    public static final long HIKARI_LEAK_DETECTION_THRESHOLD_MS = 60000; // 1 minute
    
    // Scheduler Constants  
    public static final long SCHEDULER_CHECK_INTERVAL_MS = 1000;   // 1 second
    public static final long SCHEDULER_SHUTDOWN_TIMEOUT_SEC = 5;   // 5 seconds
    public static final TimeUnit SCHEDULER_SHUTDOWN_TIMEOUT_UNIT = TimeUnit.SECONDS;
    
    // Thread Pool Constants
    public static final int DEFAULT_NOTIFICATION_THREAD_POOL_SIZE = 2;
    public static final long NOTIFICATION_TIMEOUT_SEC = 30;        // 30 seconds
    public static final TimeUnit NOTIFICATION_TIMEOUT_UNIT = TimeUnit.SECONDS;
    
    // Async Backup Thread Pool Constants
    public static final int DEFAULT_ASYNC_CORE_POOL_SIZE = 2;
    public static final int DEFAULT_ASYNC_MAX_POOL_SIZE = 4;
    public static final long DEFAULT_ASYNC_KEEP_ALIVE_TIME_SEC = 60;
    public static final int DEFAULT_ASYNC_QUEUE_CAPACITY = 100;
    
    // Retry Constants
    public static final int DEFAULT_WEBHOOK_RETRY_DELAY_MS = 1000; // 1 second base delay
    
    // File and Response Size Limits
    public static final int MAX_ERROR_RESPONSE_SIZE = 1000;        // characters
    public static final int MAX_SUCCESS_RESPONSE_SIZE = 500;       // characters
    
    // Backup File Extensions
    public static final String SQL_FILE_EXTENSION = ".sql";
    public static final String GZIP_FILE_EXTENSION = ".gz";
    
    // Date/Time Format Patterns
    public static final String BACKUP_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss";
    public static final String LOG_TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss";
    
    // Network Constants
    public static final String USER_AGENT = "db-backup4j/1.0";
    
    // SQL Constants
    public static final String SQL_NULL = "NULL";
    public static final String MYSQL_QUOTE_CHAR = "`";
    public static final String POSTGRESQL_QUOTE_CHAR = "\"";
    
    // Backup Destination Constants
    public static final String BACKUP_DESTINATION_RETENTION = "retention";
    
    // Encryption Constants
    public static final String ENCRYPTED_VALUE_PREFIX = "ENC:";
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    public static final String DEFAULT_KEY_FILE_NAME = ".db-backup4j.key";
    public static final String DEFAULT_KEY_ENV_VAR = "DB_BACKUP4J_ENCRYPTION_KEY";
}