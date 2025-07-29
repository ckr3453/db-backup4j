package io.backup4j.core.config;

/**
 * 데이터베이스 백업 전체 설정을 관리하는 최상위 설정 클래스입니다.
 * 데이터베이스 연결, 로컬 백업, S3 백업, 스케줄 설정을 포함합니다.
 */
public class BackupConfig {
    private final DatabaseConfig database;
    private final LocalBackupConfig local;
    private final S3BackupConfig s3;
    private final ScheduleConfig schedule;

    private BackupConfig(Builder builder) {
        this.database = builder.database;
        this.local = builder.local;
        this.s3 = builder.s3;
        this.schedule = builder.schedule;
    }

    public static Builder builder() {
        return new Builder();
    }

    public DatabaseConfig getDatabase() {
        return database;
    }

    public LocalBackupConfig getLocal() {
        return local;
    }

    public S3BackupConfig getS3() {
        return s3;
    }

    public ScheduleConfig getSchedule() {
        return schedule;
    }

    public static class Builder {
        private DatabaseConfig database;
        private LocalBackupConfig local;
        private S3BackupConfig s3;
        private ScheduleConfig schedule;

        public Builder database(DatabaseConfig database) {
            this.database = database;
            return this;
        }

        public Builder local(LocalBackupConfig local) {
            this.local = local;
            return this;
        }

        public Builder s3(S3BackupConfig s3) {
            this.s3 = s3;
            return this;
        }

        public Builder schedule(ScheduleConfig schedule) {
            this.schedule = schedule;
            return this;
        }

        public BackupConfig build() {
            return new BackupConfig(this);
        }
    }
}