package io.backup4j.core.config;

public class ScheduleConfig {
    private boolean enabled;
    private String daily;
    private String weekly;
    private String monthly;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDaily() {
        return daily;
    }

    public void setDaily(String daily) {
        this.daily = daily;
    }

    public String getWeekly() {
        return weekly;
    }

    public void setWeekly(String weekly) {
        this.weekly = weekly;
    }

    public String getMonthly() {
        return monthly;
    }

    public void setMonthly(String monthly) {
        this.monthly = monthly;
    }
}