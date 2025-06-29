package io.backup4j.core.config;

public class BackupConfig {
    private DatabaseConfig database;
    private LocalBackupConfig local;
    private EmailBackupConfig email;
    private S3BackupConfig s3;
    private ScheduleConfig schedule;

    public DatabaseConfig getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfig database) {
        this.database = database;
    }

    public LocalBackupConfig getLocal() {
        return local;
    }

    public void setLocal(LocalBackupConfig local) {
        this.local = local;
    }

    public EmailBackupConfig getEmail() {
        return email;
    }

    public void setEmail(EmailBackupConfig email) {
        this.email = email;
    }

    public S3BackupConfig getS3() {
        return s3;
    }

    public void setS3(S3BackupConfig s3) {
        this.s3 = s3;
    }

    public ScheduleConfig getSchedule() {
        return schedule;
    }

    public void setSchedule(ScheduleConfig schedule) {
        this.schedule = schedule;
    }
}