package io.backup4j.core.config;

/**
 * 로컬 파일 시스템으로의 백업 설정을 관리하는 클래스입니다.
 * 백업 파일 저장 경로, 압축 여부, 보관 기간 등을 설정합니다.
 */
public class LocalBackupConfig {
    public static final boolean DEFAULT_LOCAL_BACKUP_ENABLED = true;
    public static final String DEFAULT_LOCAL_BACKUP_PATH = "./db-backup4j";
    public static final String DEFAULT_LOCAL_BACKUP_RETENTION_DAYS = "30";
    public static final boolean DEFAULT_LOCAL_BACKUP_COMPRESS = true;

    private final boolean enabled;
    private final String path;
    private final String retention;
    private final boolean compress;

    private LocalBackupConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.path = builder.path;
        this.retention = builder.retention;
        this.compress = builder.compress;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPath() {
        return path;
    }

    public String getRetention() {
        return retention;
    }

    public boolean isCompress() {
        return compress;
    }


    public static class Builder {
        private boolean enabled = DEFAULT_LOCAL_BACKUP_ENABLED;
        private String path = DEFAULT_LOCAL_BACKUP_PATH;
        private String retention = DEFAULT_LOCAL_BACKUP_RETENTION_DAYS;
        private boolean compress = DEFAULT_LOCAL_BACKUP_COMPRESS;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder retention(String retention) {
            this.retention = retention;
            return this;
        }

        public Builder compress(boolean compress) {
            this.compress = compress;
            return this;
        }


        public LocalBackupConfig build() {
            return new LocalBackupConfig(this);
        }
    }
}