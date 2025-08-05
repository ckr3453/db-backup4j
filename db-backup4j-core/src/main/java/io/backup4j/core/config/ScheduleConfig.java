package io.backup4j.core.config;

/**
 * 백업 스케줄링 설정을 관리하는 클래스입니다.
 * 주기적인 백업 실행을 위한 cron 표현식을 설정합니다.
 */
public class ScheduleConfig {
    public static final boolean DEFAULT_SCHEDULE_ENABLED = false;

    private final boolean enabled;
    private final String cron;

    private ScheduleConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.cron = builder.cron;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCron() {
        return cron;
    }

    public static class Builder {
        private boolean enabled = DEFAULT_SCHEDULE_ENABLED;
        private String cron;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder cron(String cron) {
            this.cron = cron;
            return this;
        }

        public ScheduleConfig build() {
            return new ScheduleConfig(this);
        }
    }
}