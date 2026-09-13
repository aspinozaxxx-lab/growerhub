package ru.growerhub.backend.demo.engine;

import java.util.List;
import java.util.Map;

public record DemoTemplate(int version, Map<String, String> farmName, List<Profile> profiles,
        List<Greenhouse> greenhouses, Map<String, Map<String, Object>> scenarios, Physics physics) {
    public record Physics(double dailyTemperatureDelta, double temperatureResponsePerMinute, double lightHeat,
            double fanCooling, double acCooling, double pumpMoisturePerSecond, double dryingPerHour,
            int wateringRateMlPerHour, int historyWateringSeconds) {}
    public record Profile(String key, String kind, Map<String, String> name, Map<String, String> description,
            double powerWatts, double temperature, double humidity, double moisture) {}
    public record Environment(double temperature, double humidity, double moisture, double dailyTemperatureDelta,
            double dailyHumidityDelta, double temperaturePeakHour, double dryingPerHour,
            String historyWateringTime, int historyWateringSeconds) {}
    public record Greenhouse(Map<String, String> name, List<Map<String, String>> plants, Environment environment) {}
}
