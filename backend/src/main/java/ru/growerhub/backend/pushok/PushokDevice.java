package ru.growerhub.backend.pushok;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PushokDevice {
    record Field(int address, String property, String type, boolean writable, String unit,
                 Double minimum, Double maximum, Map<String, Object> labels, double scale) { }
    private final String id;
    private final String friendlyName;
    private final JsonNode description;
    private final Map<String, Field> fields = new LinkedHashMap<>();
    private final Map<String, Object> state = new LinkedHashMap<>();
    private final Map<Integer, Long> timestamps = new LinkedHashMap<>();
    private boolean offline = true;

    PushokDevice(JsonNode description, String name, JsonNode adapter) {
        this.description = description;
        id = description.path("id").asText();
        if (!id.matches("[a-fA-F0-9]{16}")) throw new IllegalArgumentException("Invalid Zigbee identity");
        friendlyName = name;
        for (JsonNode param : adapter.path("params")) {
            String property = param.path("viewParams").path("name").asText().toLowerCase(Locale.ROOT).replace(' ', '_');
            if ("lqi".equals(property)) property = "linkquality";
            if (!property.matches("[a-z][a-z0-9_]{0,63}") || (param.path("hidden").asBoolean() && !"linkquality".equals(property))) continue;
            String type = param.path("type").asText();
            if (!List.of("bool", "int", "float", "number").contains(type) || !param.path("access").asText().contains("r")) continue;
            if (!param.path("address").canConvertToInt()) continue;
            int address = param.path("address").asInt();
            if (address < 0 || address > 255 || fields.containsKey(property)) continue;
            if (param.has("__convert") || param.path("viewParams").has("__convert")) continue;
            String rawUnit = param.path("viewParams").path("unit").asText();
            double scale = "unit_mA".equals(rawUnit) ? 0.001 : 1;
            Map<String, Object> labels = new LinkedHashMap<>();
            if ("dropdown".equals(param.path("viewParams").path("type").asText())) {
                param.path("labels").fields().forEachRemaining(entry -> {
                    if (entry.getValue().isNumber()) labels.put(entry.getKey(), entry.getValue().numberValue());
                });
            }
            fields.put(property, new Field(address, property, type, param.path("access").asText().contains("w"),
                    unit(rawUnit), number(param.get("min"), scale), number(param.get("max"), scale), labels, scale));
        }
    }
    String id() { return id; }
    String friendlyName() { return friendlyName; }
    boolean offline() { return offline; }
    Map<String, Object> state() { return Map.copyOf(state); }
    Map<Integer, Long> timestamps() { return Map.copyOf(timestamps); }

    boolean update(JsonNode props) {
        boolean changed = false;
        JsonNode offlineValue = props.path("251");
        if (confirmed(251, offlineValue) && offlineValue.path("value").isBoolean()) offline = offlineValue.path("value").asBoolean();
        for (Field field : fields.values()) {
            JsonNode raw = props.path(Integer.toString(field.address()));
            Long previousTime = timestamps.get(field.address());
            if (!confirmed(field.address(), raw)) continue;
            JsonNode value = raw.get("value");
            Object converted;
            if ("bool".equals(field.type())) {
                if (!value.isBoolean()) continue;
                converted = "state".equals(field.property()) ? (value.asBoolean() ? "ON" : "OFF") : value.asBoolean();
            } else {
                if (!value.isNumber() || !Double.isFinite(value.asDouble())) continue;
                converted = value.asDouble() * field.scale();
                if (!field.labels().isEmpty()) {
                    var matched = field.labels().entrySet().stream()
                            .filter(entry -> ((Number) entry.getValue()).doubleValue() == value.asDouble()).findFirst();
                    if (matched.isEmpty()) continue;
                    converted = matched.get().getKey();
                }
            }
            Object previous = state.put(field.property(), converted);
            changed |= !java.util.Objects.equals(previous, converted);
            changed |= !java.util.Objects.equals(previousTime, timestamps.get(field.address()));
        }
        return changed;
    }

    private boolean confirmed(int address, JsonNode raw) {
        if (!raw.path("ack").asBoolean(false) || !raw.has("value") || raw.path("history").asBoolean()) return false;
        long time = raw.path("time").asLong(0);
        if (time < timestamps.getOrDefault(address, 0L)) return false;
        timestamps.put(address, time);
        return true;
    }

    Map<String, Object> metadata(List<String> writableProperties) {
        List<Map<String, Object>> exposes = new ArrayList<>();
        for (Field field : fields.values()) {
            var item = new LinkedHashMap<String, Object>();
            item.put("property", field.property()); item.put("name", field.property());
            item.put("access", field.writable() && writableProperties.contains(field.property()) ? 3 : 1);
            if ("bool".equals(field.type())) {
                item.put("type", "binary");
                item.put("value_on", "state".equals(field.property()) ? "ON" : true);
                item.put("value_off", "state".equals(field.property()) ? "OFF" : false);
            } else if (!field.labels().isEmpty()) {
                item.put("type", "enum"); item.put("values", List.copyOf(field.labels().keySet()));
            } else {
                item.put("type", "numeric");
                if (field.unit() != null) item.put("unit", field.unit());
                if (field.minimum() != null) item.put("value_min", field.minimum());
                if (field.maximum() != null) item.put("value_max", field.maximum());
            }
            exposes.add(item);
        }
        return Map.of("ieee_address", ieee(), "friendly_name", friendlyName, "type", description.path("rtr").asBoolean() ? "Router" : "EndDevice",
                "supported", !fields.isEmpty(), "disabled", false,
                "definition", Map.of("model", description.path("mdl").asText(), "vendor", description.path("mnf").asText(),
                        "description", friendlyName, "exposes", exposes));
    }
    String ieee() { return "0x" + id.toLowerCase(Locale.ROOT); }

    Map<String, Object> command(String property, JsonNode value) {
        Field field = fields.get(property);
        if (offline || field == null || !field.writable()) throw new IllegalArgumentException("Property is not available");
        Object raw;
        if ("bool".equals(field.type())) {
            if (value.isBoolean()) raw = value.asBoolean();
            else if ("state".equals(property) && List.of("ON", "OFF").contains(value.asText())) raw = "ON".equals(value.asText());
            else throw new IllegalArgumentException("Invalid binary value");
        } else if (!field.labels().isEmpty()) {
            raw = field.labels().get(value.asText());
            if (raw == null) throw new IllegalArgumentException("Invalid enum value");
        } else {
            if (!value.isNumber() || !Double.isFinite(value.asDouble())) throw new IllegalArgumentException("Invalid number");
            double number = value.asDouble();
            if ((field.minimum() != null && number < field.minimum()) || (field.maximum() != null && number > field.maximum()))
                throw new IllegalArgumentException("Number outside range");
            number /= field.scale();
            if ("int".equals(field.type()) && number != Math.rint(number)) throw new IllegalArgumentException("Integer required");
            raw = "int".equals(field.type()) ? (Object) (long) number : number;
        }
        return Map.of("id", id, "type", "zigbee", "field", field.address(), "value", raw);
    }

    static String name(JsonNode attributes, String id, ObjectMapper mapper) {
        try {
            JsonNode parsed = attributes.isTextual() ? mapper.readTree(attributes.asText()) : attributes;
            String name = parsed.path("name").asText().replaceAll("[/+#\\x00-\\x1f]", " ").strip();
            return name.isEmpty() ? "0x" + id.toLowerCase(Locale.ROOT) : name.substring(0, Math.min(100, name.length()));
        } catch (Exception ex) { return "0x" + id.toLowerCase(Locale.ROOT); }
    }
    private static Double number(JsonNode node, double scale) { return node != null && node.isNumber() ? node.asDouble() * scale : null; }
    private static String unit(String raw) {
        return switch (raw) {
            case "unit_C", "unit_degrees" -> "°C";
            case "unit_%" -> "%";
            case "unit_voltage" -> "V";
            case "unit_A", "unit_mA" -> "A";
            case "unit_power", "unit_watt" -> "W";
            case "unit_kW", "kW" -> "kW";
            case "unit_energy" -> "kWh";
            case "unit_lux", "unit_lx" -> "lx";
            case "unit_hpa" -> "hPa";
            case "unit_bar" -> "bar";
            case "unit_s", "unit_seconds" -> "s";
            case "unit_min", "unit_minutes" -> "min";
            case "unit_cm" -> "cm";
            case "unit_mm" -> "mm";
            case "unit_l", "unit_liter" -> "l";
            case "unit_ppm" -> "ppm";
            default -> null;
        };
    }
}
