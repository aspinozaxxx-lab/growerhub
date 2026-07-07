package ru.growerhub.backend.automation.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AutomationData {
    private AutomationData() {
    }

    public static final String SCOPE_ROOM = "ROOM";
    public static final String SCOPE_BOX = "BOX";

    public static final String SOURCE_NATIVE_SENSOR = "NATIVE_SENSOR";
    public static final String SOURCE_NATIVE_PUMP = "NATIVE_PUMP";
    public static final String SOURCE_ZIGBEE_DEVICE = "ZIGBEE_DEVICE";

    public static final String ROLE_AC_SWITCH = "AC_SWITCH";
    public static final String ROLE_AIR_TEMPERATURE_SENSOR = "AIR_TEMPERATURE_SENSOR";
    public static final String ROLE_EXHAUST_SWITCH = "EXHAUST_SWITCH";
    public static final String ROLE_LIGHT_SWITCH = "LIGHT_SWITCH";
    public static final String ROLE_LEAK_SENSOR = "LEAK_SENSOR";
    public static final String ROLE_SOIL_MOISTURE_SENSOR = "SOIL_MOISTURE_SENSOR";
    public static final String ROLE_WATER_PUMP = "WATER_PUMP";

    public static final String SCENARIO_ROOM_CLIMATE = "ROOM_CLIMATE";
    public static final String SCENARIO_BOX_CLIMATE = "BOX_CLIMATE";
    public static final String SCENARIO_LIGHT_SCHEDULE = "LIGHT_SCHEDULE";
    public static final String SCENARIO_WATERING = "WATERING";

    public record Overview(
            @JsonProperty("rooms") List<Room> rooms,
            @JsonProperty("resource_catalog") ResourceCatalog resourceCatalog,
            @JsonProperty("last_actions") List<ActionLog> lastActions,
            @JsonProperty("settings") Settings settings
    ) {
    }

    public record Settings(
            @JsonProperty("timezone") String timezone,
            @JsonProperty("stale_sensor_minutes") int staleSensorMinutes,
            @JsonProperty("manual_override_minutes") int manualOverrideMinutes,
            @JsonProperty("resource_offline_minutes") int resourceOfflineMinutes
    ) {
    }

    public record Room(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") String name,
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("resources") List<ResourceBinding> resources,
            @JsonProperty("scenarios") List<ScenarioConfig> scenarios,
            @JsonProperty("states") List<ScenarioState> states,
            @JsonProperty("boxes") List<Box> boxes,
            @JsonProperty("last_actions") List<ActionLog> lastActions,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("updated_at") LocalDateTime updatedAt
    ) {
    }

    public record Box(
            @JsonProperty("id") Integer id,
            @JsonProperty("room_id") Integer roomId,
            @JsonProperty("name") String name,
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("plants") List<Plant> plants,
            @JsonProperty("resources") List<ResourceBinding> resources,
            @JsonProperty("scenarios") List<ScenarioConfig> scenarios,
            @JsonProperty("states") List<ScenarioState> states,
            @JsonProperty("last_actions") List<ActionLog> lastActions,
            @JsonProperty("readiness") Map<String, Readiness> readiness,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("updated_at") LocalDateTime updatedAt
    ) {
    }

    public record Plant(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") String name,
            @JsonProperty("owner_email") String ownerEmail,
            @JsonProperty("owner_username") String ownerUsername,
            @JsonProperty("owner_id") Integer ownerId,
            @JsonProperty("group_name") String groupName
    ) {
    }

    public record ResourceBinding(
            @JsonProperty("id") Integer id,
            @JsonProperty("scope_type") String scopeType,
            @JsonProperty("scope_id") Integer scopeId,
            @JsonProperty("role") String role,
            @JsonProperty("source_type") String sourceType,
            @JsonProperty("native_sensor_id") Integer nativeSensorId,
            @JsonProperty("native_pump_id") Integer nativePumpId,
            @JsonProperty("zigbee_ieee_address") String zigbeeIeeeAddress,
            @JsonProperty("zigbee_property") String zigbeeProperty,
            @JsonProperty("command_property") String commandProperty,
            @JsonProperty("on_value") String onValue,
            @JsonProperty("off_value") String offValue,
            @JsonProperty("current_value") Object currentValue,
            @JsonProperty("last_seen_at") LocalDateTime lastSeenAt,
            @JsonProperty("connection_status") String connectionStatus,
            @JsonProperty("connection_message") String connectionMessage,
            @JsonProperty("label") String label,
            @JsonProperty("ready") boolean ready,
            @JsonProperty("reason") String reason
    ) {
    }

    public record ScenarioConfig(
            @JsonProperty("id") Integer id,
            @JsonProperty("scope_type") String scopeType,
            @JsonProperty("scope_id") Integer scopeId,
            @JsonProperty("scenario_type") String scenarioType,
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("config") Map<String, Object> config,
            @JsonProperty("readiness") Readiness readiness,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("updated_at") LocalDateTime updatedAt
    ) {
    }

    public record ScenarioState(
            @JsonProperty("id") Integer id,
            @JsonProperty("scope_type") String scopeType,
            @JsonProperty("scope_id") Integer scopeId,
            @JsonProperty("scenario_type") String scenarioType,
            @JsonProperty("status") String status,
            @JsonProperty("unavailable_reason") String unavailableReason,
            @JsonProperty("last_evaluated_at") LocalDateTime lastEvaluatedAt,
            @JsonProperty("last_action_at") LocalDateTime lastActionAt,
            @JsonProperty("ac_request_active") boolean acRequestActive,
            @JsonProperty("manual_pause_until") LocalDateTime manualPauseUntil,
            @JsonProperty("runtime") Map<String, Object> runtime,
            @JsonProperty("updated_at") LocalDateTime updatedAt
    ) {
    }

    public record Readiness(
            @JsonProperty("ready") boolean ready,
            @JsonProperty("reason") String reason,
            @JsonProperty("required_roles") List<String> requiredRoles
    ) {
    }

    public record ActionLog(
            @JsonProperty("id") Integer id,
            @JsonProperty("scope_type") String scopeType,
            @JsonProperty("scope_id") Integer scopeId,
            @JsonProperty("scenario_type") String scenarioType,
            @JsonProperty("resource_binding_id") Integer resourceBindingId,
            @JsonProperty("action") String action,
            @JsonProperty("reason") String reason,
            @JsonProperty("result") String result,
            @JsonProperty("duration_s") Integer durationS,
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {
    }

    public record ResourceCatalog(
            @JsonProperty("plants") List<Plant> plants,
            @JsonProperty("native_devices") List<NativeDevice> nativeDevices,
            @JsonProperty("zigbee_devices") List<ZigbeeDevice> zigbeeDevices
    ) {
    }

    public record NativeDevice(
            @JsonProperty("id") Integer id,
            @JsonProperty("device_id") String deviceId,
            @JsonProperty("name") String name,
            @JsonProperty("is_online") Boolean isOnline,
            @JsonProperty("last_seen_at") LocalDateTime lastSeenAt,
            @JsonProperty("sensors") List<NativeSensor> sensors,
            @JsonProperty("pumps") List<NativePump> pumps
    ) {
    }

    public record NativeSensor(
            @JsonProperty("id") Integer id,
            @JsonProperty("device_id") Integer deviceId,
            @JsonProperty("type") String type,
            @JsonProperty("channel") Integer channel,
            @JsonProperty("label") String label,
            @JsonProperty("status") String status,
            @JsonProperty("last_value") Double lastValue,
            @JsonProperty("last_ts") LocalDateTime lastTs,
            @JsonProperty("last_seen_at") LocalDateTime lastSeenAt
    ) {
    }

    public record NativePump(
            @JsonProperty("id") Integer id,
            @JsonProperty("device_id") Integer deviceId,
            @JsonProperty("channel") Integer channel,
            @JsonProperty("label") String label,
            @JsonProperty("is_running") Boolean isRunning,
            @JsonProperty("is_online") Boolean isOnline,
            @JsonProperty("last_seen_at") LocalDateTime lastSeenAt
    ) {
    }

    public record ZigbeeDevice(
            @JsonProperty("ieee_address") String ieeeAddress,
            @JsonProperty("friendly_name") String friendlyName,
            @JsonProperty("type") String type,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("definition") Object definition,
            @JsonProperty("metrics") List<ZigbeeFeature> metrics,
            @JsonProperty("controls") List<ZigbeeFeature> controls,
            @JsonProperty("availability") String availability,
            @JsonProperty("last_state_at") LocalDateTime lastStateAt
    ) {
    }

    public record ZigbeeFeature(
            @JsonProperty("type") String type,
            @JsonProperty("property") String property,
            @JsonProperty("label") String label,
            @JsonProperty("unit") String unit,
            @JsonProperty("access") Integer access,
            @JsonProperty("value") Object value,
            @JsonProperty("value_on") Object valueOn,
            @JsonProperty("value_off") Object valueOff
    ) {
    }

    public record SaveRoomRequest(
            @JsonProperty("name") String name,
            @JsonProperty("enabled") Boolean enabled
    ) {
    }

    public record SaveBoxRequest(
            @JsonProperty("name") String name,
            @JsonProperty("enabled") Boolean enabled
    ) {
    }

    public record SavePlantsRequest(@JsonProperty("plant_ids") List<Integer> plantIds) {
    }

    public record SaveResourcesRequest(@JsonProperty("resources") List<ResourceBindingRequest> resources) {
    }

    public record ResourceBindingRequest(
            @JsonProperty("role") String role,
            @JsonProperty("source_type") String sourceType,
            @JsonProperty("native_sensor_id") Integer nativeSensorId,
            @JsonProperty("native_pump_id") Integer nativePumpId,
            @JsonProperty("zigbee_ieee_address") String zigbeeIeeeAddress,
            @JsonProperty("zigbee_property") String zigbeeProperty,
            @JsonProperty("command_property") String commandProperty,
            @JsonProperty("on_value") String onValue,
            @JsonProperty("off_value") String offValue
    ) {
    }

    public record SaveScenariosRequest(@JsonProperty("scenarios") List<ScenarioConfigRequest> scenarios) {
    }

    public record ScenarioConfigRequest(
            @JsonProperty("scenario_type") String scenarioType,
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("config") Map<String, Object> config
    ) {
    }
}
