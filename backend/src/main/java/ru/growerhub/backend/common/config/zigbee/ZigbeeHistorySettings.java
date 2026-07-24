package ru.growerhub.backend.common.config.zigbee;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki istorii Zigbee-svojstv.
@ConfigurationProperties(prefix = "zigbee.history")
public class ZigbeeHistorySettings {
    private int maxPoints = 200;
    private int maxDiscretePoints = 5000;
    private int defaultHours = 24;
    private long numericIntervalSeconds = 300;
    private long numericChangeMinIntervalSeconds = 60;
    private double numericChangeThreshold = 0.5;
    private Map<String, Long> numericIntervals = new LinkedHashMap<>();
    private Map<String, Double> numericChangeThresholds = new LinkedHashMap<>();
    private Set<String> eventProperties = new LinkedHashSet<>();
    private Set<String> ignoredProperties = new LinkedHashSet<>();

    public int getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public int getMaxDiscretePoints() {
        return maxDiscretePoints;
    }

    public void setMaxDiscretePoints(int maxDiscretePoints) {
        this.maxDiscretePoints = maxDiscretePoints;
    }

    public int getDefaultHours() {
        return defaultHours;
    }

    public void setDefaultHours(int defaultHours) {
        this.defaultHours = defaultHours;
    }

    public long getNumericIntervalSeconds() {
        return numericIntervalSeconds;
    }

    public void setNumericIntervalSeconds(long numericIntervalSeconds) {
        this.numericIntervalSeconds = numericIntervalSeconds;
    }

    public double getNumericChangeThreshold() {
        return numericChangeThreshold;
    }

    public void setNumericChangeThreshold(double numericChangeThreshold) {
        this.numericChangeThreshold = numericChangeThreshold;
    }

    public long getNumericChangeMinIntervalSeconds() {
        return numericChangeMinIntervalSeconds;
    }

    public void setNumericChangeMinIntervalSeconds(long numericChangeMinIntervalSeconds) {
        this.numericChangeMinIntervalSeconds = numericChangeMinIntervalSeconds;
    }

    public Map<String, Long> getNumericIntervals() {
        return numericIntervals;
    }

    public void setNumericIntervals(Map<String, Long> numericIntervals) {
        this.numericIntervals = numericIntervals != null ? numericIntervals : new LinkedHashMap<>();
    }

    public Map<String, Double> getNumericChangeThresholds() {
        return numericChangeThresholds;
    }

    public void setNumericChangeThresholds(Map<String, Double> numericChangeThresholds) {
        this.numericChangeThresholds = numericChangeThresholds != null
                ? numericChangeThresholds
                : new LinkedHashMap<>();
    }

    public Set<String> getEventProperties() {
        return eventProperties;
    }

    public void setEventProperties(Set<String> eventProperties) {
        this.eventProperties = eventProperties != null ? eventProperties : new LinkedHashSet<>();
    }

    public Set<String> getIgnoredProperties() {
        return ignoredProperties;
    }

    public void setIgnoredProperties(Set<String> ignoredProperties) {
        this.ignoredProperties = ignoredProperties != null ? ignoredProperties : new LinkedHashSet<>();
    }

    public long numericIntervalSeconds(String property) {
        return Math.max(1, numericIntervals.getOrDefault(property, numericIntervalSeconds));
    }

    public double numericChangeThreshold(String property) {
        return Math.max(0.0, numericChangeThresholds.getOrDefault(property, numericChangeThreshold));
    }
}
