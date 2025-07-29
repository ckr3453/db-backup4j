package io.backup4j.core.config;

/**
 * Amazon S3로의 백업 설정을 관리하는 클래스입니다.
 * S3 버킷, 리전, 인증 정보, 백업 파일 경로 등을 설정합니다.
 */
public class S3BackupConfig {
    private final boolean enabled;
    private final String bucket;
    private final String prefix;
    private final String region;
    private final String accessKey;
    private final String secretKey;

    private S3BackupConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.bucket = builder.bucket;
        this.prefix = builder.prefix;
        this.region = builder.region;
        this.accessKey = builder.accessKey;
        this.secretKey = builder.secretKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBucket() {
        return bucket;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getRegion() {
        return region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }


    public static class Builder {
        private boolean enabled = ConfigDefaults.DEFAULT_S3_BACKUP_ENABLED;
        private String bucket;
        private String prefix = ConfigDefaults.DEFAULT_S3_PREFIX;
        private String region = ConfigDefaults.DEFAULT_S3_REGION;
        private String accessKey;
        private String secretKey;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public Builder accessKey(String accessKey) {
            this.accessKey = accessKey;
            return this;
        }

        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }


        public S3BackupConfig build() {
            return new S3BackupConfig(this);
        }
    }
}