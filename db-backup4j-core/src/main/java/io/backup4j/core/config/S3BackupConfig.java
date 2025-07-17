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
    private final boolean enableChecksum;
    private final String checksumAlgorithm;

    private S3BackupConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.bucket = builder.bucket;
        this.prefix = builder.prefix;
        this.region = builder.region;
        this.accessKey = builder.accessKey;
        this.secretKey = builder.secretKey;
        this.enableChecksum = builder.enableChecksum;
        this.checksumAlgorithm = builder.checksumAlgorithm;
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

    public boolean isEnableChecksum() {
        return enableChecksum;
    }

    public String getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    public static class Builder {
        private boolean enabled = ConfigDefaults.DEFAULT_S3_BACKUP_ENABLED;
        private String bucket;
        private String prefix = ConfigDefaults.DEFAULT_S3_PREFIX;
        private String region = ConfigDefaults.DEFAULT_S3_REGION;
        private String accessKey;
        private String secretKey;
        private boolean enableChecksum = ConfigDefaults.DEFAULT_S3_BACKUP_ENABLE_CHECKSUM;
        private String checksumAlgorithm = ConfigDefaults.DEFAULT_S3_BACKUP_CHECKSUM_ALGORITHM;

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

        public Builder enableChecksum(boolean enableChecksum) {
            this.enableChecksum = enableChecksum;
            return this;
        }

        public Builder checksumAlgorithm(String checksumAlgorithm) {
            this.checksumAlgorithm = checksumAlgorithm;
            return this;
        }

        public S3BackupConfig build() {
            return new S3BackupConfig(this);
        }
    }
}