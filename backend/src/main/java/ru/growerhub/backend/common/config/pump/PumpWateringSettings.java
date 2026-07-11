package ru.growerhub.backend.common.config.pump;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki poliva nasosom.
@ConfigurationProperties(prefix = "pump.watering")
public class PumpWateringSettings {
    private int defaultRateMlPerHour = 2000;
    private int defaultTimedDurationS = 300;
    private int defaultUntilLeakMaxActiveDurationS = 1800;
    private int defaultPulseRunS = 180;
    private int defaultPulsePauseS = 300;
    private int maxActiveDurationS = 86400;
    private int sessionPageMax = 100;
    private int stoppingTimeoutS = 15;

    public int getDefaultRateMlPerHour() {
        return defaultRateMlPerHour;
    }

    public void setDefaultRateMlPerHour(int defaultRateMlPerHour) {
        this.defaultRateMlPerHour = defaultRateMlPerHour;
    }

    public int getDefaultTimedDurationS() {
        return defaultTimedDurationS;
    }

    public void setDefaultTimedDurationS(int defaultTimedDurationS) {
        this.defaultTimedDurationS = defaultTimedDurationS;
    }

    public int getDefaultUntilLeakMaxActiveDurationS() {
        return defaultUntilLeakMaxActiveDurationS;
    }

    public void setDefaultUntilLeakMaxActiveDurationS(int defaultUntilLeakMaxActiveDurationS) {
        this.defaultUntilLeakMaxActiveDurationS = defaultUntilLeakMaxActiveDurationS;
    }

    public int getDefaultPulseRunS() {
        return defaultPulseRunS;
    }

    public void setDefaultPulseRunS(int defaultPulseRunS) {
        this.defaultPulseRunS = defaultPulseRunS;
    }

    public int getDefaultPulsePauseS() {
        return defaultPulsePauseS;
    }

    public void setDefaultPulsePauseS(int defaultPulsePauseS) {
        this.defaultPulsePauseS = defaultPulsePauseS;
    }

    public int getMaxActiveDurationS() {
        return maxActiveDurationS;
    }

    public void setMaxActiveDurationS(int maxActiveDurationS) {
        this.maxActiveDurationS = maxActiveDurationS;
    }

    public int getSessionPageMax() {
        return sessionPageMax;
    }

    public void setSessionPageMax(int sessionPageMax) {
        this.sessionPageMax = sessionPageMax;
    }

    public int getStoppingTimeoutS() {
        return stoppingTimeoutS;
    }

    public void setStoppingTimeoutS(int stoppingTimeoutS) {
        this.stoppingTimeoutS = stoppingTimeoutS;
    }
}
