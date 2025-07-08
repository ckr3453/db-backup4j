package io.backup4j.core.config;

public class ScheduleConfig {
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
        private boolean enabled = ConfigDefaults.DEFAULT_SCHEDULE_ENABLED;
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