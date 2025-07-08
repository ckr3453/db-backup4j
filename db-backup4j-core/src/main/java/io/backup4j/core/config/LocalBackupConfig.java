package io.backup4j.core.config;

public class LocalBackupConfig {
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
        private boolean enabled = ConfigDefaults.DEFAULT_LOCAL_BACKUP_ENABLED;
        private String path = ConfigDefaults.DEFAULT_LOCAL_BACKUP_PATH;
        private String retention = ConfigDefaults.DEFAULT_LOCAL_BACKUP_RETENTION_DAYS;
        private boolean compress = ConfigDefaults.DEFAULT_LOCAL_BACKUP_COMPRESS;

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