package io.backup4j.core.config;

/**
 * Configuration default values
 */
public final class ConfigDefaults {
    
    // Database defaults
    public static final String DEFAULT_DATABASE_HOST = "localhost";
    public static final int DEFAULT_MYSQL_PORT = 3306;
    public static final int DEFAULT_POSTGRESQL_PORT = 5432;
    
    // Local backup defaults
    public static final boolean DEFAULT_LOCAL_BACKUP_ENABLED = true;
    public static final String DEFAULT_LOCAL_BACKUP_PATH = "./db-backup4j";
    public static final String DEFAULT_LOCAL_BACKUP_RETENTION_DAYS = "30";
    public static final boolean DEFAULT_LOCAL_BACKUP_COMPRESS = true;
    
    // Email backup defaults
    public static final boolean DEFAULT_EMAIL_BACKUP_ENABLED = false;
    public static final int DEFAULT_SMTP_PORT = 587;
    
    // S3 backup defaults
    public static final boolean DEFAULT_S3_BACKUP_ENABLED = false;
    public static final String DEFAULT_S3_PREFIX = "db-backup4j";
    public static final String DEFAULT_S3_REGION = "ap-northeast-2";
    
    // Schedule defaults
    public static final boolean DEFAULT_SCHEDULE_ENABLED = false;
    
    // Port ranges
    public static final int MIN_PORT = 1;
    public static final int MAX_PORT = 65535;
    
    private ConfigDefaults() {
    }
}