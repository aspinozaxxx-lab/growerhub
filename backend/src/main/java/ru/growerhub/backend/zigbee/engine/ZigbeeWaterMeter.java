package ru.growerhub.backend.zigbee.engine;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import ru.growerhub.backend.zigbee.contract.ZigbeeWaterMeterData;

public final class ZigbeeWaterMeter {
    private ZigbeeWaterMeter() {}

    public static boolean supports(JsonNode definition, boolean simulated) {
        // Semantika vremeni operacii podtverzhdena tolko dlya etogo profilya, ne po imenam svojstv.
        boolean profile = "SWV".equals(definition.path("model").asText())
                && "SONOFF".equalsIgnoreCase(definition.path("vendor").asText());
        profile |= simulated && "Demo valve".equals(definition.path("model").asText());
        return profile && readable(definition, "real_time_irrigation_volume", "L")
                && readable(definition, "irrigation_start_time", null)
                && readable(definition, "irrigation_end_time", null);
    }

    public static ZigbeeWaterMeterData.Observation normalize(JsonNode definition, JsonNode state, LocalDateTime receivedAt) {
        Double volume = nonNegative(state.path("real_time_irrigation_volume"));
        LocalDateTime start = epoch(state.path("irrigation_start_time"));
        LocalDateTime end = epoch(state.path("irrigation_end_time"));
        String issue = null;
        if (start == null || end == null || !end.isAfter(start) || receivedAt == null || end.isAfter(receivedAt)) {
            issue = "invalid_operation_time";
        } else if (!"OFF".equals(state.path("state").asText())) {
            issue = "closure_not_reported";
        } else if (volume == null) {
            issue = "missing_volume";
        }
        Double flow = nonNegative(state.path("flow"));
        JsonNode feature = feature(definition.path("exposes"), "flow");
        if (flow != null) {
            String unit = feature == null ? "" : feature.path("unit").asText();
            flow = switch (unit) {
                case "m³/h", "m3/h" -> flow * 1000.0 / 60.0;
                case "L/min", "l/min" -> flow;
                default -> null;
            };
        }
        if ("OFF".equals(state.path("state").asText()) && flow != null && flow > 0) issue = "flow_after_closed_state";
        Double daily = readable(definition, "daily_irrigation_volume", "L")
                ? nonNegative(state.path("daily_irrigation_volume")) : null;
        return new ZigbeeWaterMeterData.Observation(receivedAt, start, end, volume, flow, daily, issue == null, issue);
    }

    private static Double nonNegative(JsonNode node) {
        if (!node.isNumber()) return null;
        double value = node.asDouble();
        return Double.isFinite(value) && value >= 0 ? value : null;
    }

    private static LocalDateTime epoch(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() <= 0) return null;
        try { return LocalDateTime.ofInstant(Instant.ofEpochSecond(node.asLong()), ZoneOffset.UTC); }
        catch (DateTimeException ex) { return null; }
    }

    private static boolean readable(JsonNode definition, String property, String unit) {
        JsonNode node = feature(definition.path("exposes"), property);
        return node != null && "numeric".equals(node.path("type").asText())
                && (node.path("access").asInt() & 1) != 0
                && (unit == null || unit.equals(node.path("unit").asText()));
    }

    private static JsonNode feature(JsonNode nodes, String property) {
        if (nodes.isArray()) for (JsonNode node : nodes) {
            if (property.equals(node.path("property").asText())) return node;
            JsonNode found = feature(node.path("features"), property);
            if (found != null) return found;
        }
        return null;
    }
}
