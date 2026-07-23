package ru.growerhub.backend.common.config.maintenance;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki hranenija i prorezhivanija istorii.
@ConfigurationProperties(prefix = "history.retention")
public class HistoryRetentionSettings {
    private boolean enabled = true;
    private int rawDays = 30;
    private int maxDaysPerRun = 365;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRawDays() {
        return rawDays;
    }

    public void setRawDays(int rawDays) {
        this.rawDays = rawDays;
    }

    public int getMaxDaysPerRun() {
        return maxDaysPerRun;
    }

    public void setMaxDaysPerRun(int maxDaysPerRun) {
        this.maxDaysPerRun = maxDaysPerRun;
    }
}
