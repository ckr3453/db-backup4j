package io.backup4j.core.config;

/**
 * 설정 기본값들을 정의하는 클래스
 * 모든 백업 관련 설정의 기본값을 상수로 제공함
 */
public final class ConfigDefaults {
    
    // 데이터베이스 기본값
    public static final String DEFAULT_DATABASE_HOST = "localhost";
    public static final int DEFAULT_MYSQL_PORT = 3306;
    public static final int DEFAULT_POSTGRESQL_PORT = 5432;
    
    // 로컬 백업 기본값
    public static final boolean DEFAULT_LOCAL_BACKUP_ENABLED = true;
    public static final String DEFAULT_LOCAL_BACKUP_PATH = "./db-backup4j";
    public static final String DEFAULT_LOCAL_BACKUP_RETENTION_DAYS = "30";
    public static final boolean DEFAULT_LOCAL_BACKUP_COMPRESS = true;
    
    // S3 백업 기본값
    public static final boolean DEFAULT_S3_BACKUP_ENABLED = false;
    public static final String DEFAULT_S3_PREFIX = "db-backup4j";
    public static final String DEFAULT_S3_REGION = "ap-northeast-2";
    
    // 스케줄 기본값
    public static final boolean DEFAULT_SCHEDULE_ENABLED = false;
    
    // 알림 기본값
    public static final boolean DEFAULT_NOTIFICATION_ENABLED = true;
    public static final boolean DEFAULT_EMAIL_ENABLED = false;
    public static final boolean DEFAULT_WEBHOOK_ENABLED = false;
    public static final String DEFAULT_EMAIL_SUBJECT = "DB Backup4j Backup Completed";
    public static final String DEFAULT_EMAIL_TEMPLATE = "simple";
    public static final boolean DEFAULT_SMTP_TLS = true;
    public static final boolean DEFAULT_SMTP_AUTH = true;
    public static final int DEFAULT_WEBHOOK_TIMEOUT = 30;
    public static final int DEFAULT_WEBHOOK_RETRY_COUNT = 3;
    public static final boolean DEFAULT_WEBHOOK_RICH_FORMAT = true;
    
    // 알림 설정 별칭
    public static final boolean DEFAULT_EMAIL_NOTIFICATION_ENABLED = DEFAULT_EMAIL_ENABLED;
    public static final boolean DEFAULT_WEBHOOK_NOTIFICATION_ENABLED = DEFAULT_WEBHOOK_ENABLED;
    public static final boolean DEFAULT_WEBHOOK_USE_RICH_FORMAT = DEFAULT_WEBHOOK_RICH_FORMAT;
    public static final boolean DEFAULT_SMTP_USE_TLS = DEFAULT_SMTP_TLS;
    public static final boolean DEFAULT_SMTP_USE_AUTH = DEFAULT_SMTP_AUTH;
    
    // 포트 범위
    public static final int MIN_PORT = 1;
    public static final int MAX_PORT = 65535;
    
    private ConfigDefaults() {
    }
}