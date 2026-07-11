package ru.growerhub.backend.pump.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PumpSessionData {
    public static final String SOURCE_ADMIN_MANUAL = "admin_manual";
    public static final String SOURCE_AUTOMATION = "automation";
    public static final String SOURCE_USER_MANUAL = "user_manual";

    public static final String MODE_TIMED = "timed";
    public static final String MODE_UNTIL_LEAK = "until_leak";

    public static final String PHASE_RUNNING = "running";
    public static final String PHASE_PAUSE = "pause";
    public static final String PHASE_STOPPING = "stopping";
    public static final String PHASE_COMPLETED = "completed";
    public static final String PHASE_FAILED = "failed";

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_TERMINAL = "terminal";

    public static final String REASON_DURATION = "duration";
    public static final String REASON_LEAK = "leak";
    public static final String REASON_LIMIT = "limit";
    public static final String REASON_MANUAL = "manual";
    public static final String REASON_COMMAND_ERROR = "command_error";
    public static final String REASON_DEVICE_OFFLINE = "device_offline";
    public static final String REASON_SENSOR_UNAVAILABLE = "sensor_unavailable";
    public static final String REASON_RECOVERY = "recovery";

    private PumpSessionData() {
    }

    public record Defaults(
            @JsonProperty("timed_duration_s") int timedDurationS,
            @JsonProperty("until_leak_max_active_duration_s") int untilLeakMaxActiveDurationS,
            @JsonProperty("pulse_run_s") int pulseRunS,
            @JsonProperty("pulse_pause_s") int pulsePauseS
    ) {
    }

    public record Start(
            @JsonProperty("pump_id") Integer pumpId,
            @JsonProperty("source") String source,
            @JsonProperty("mode") String mode,
            @JsonProperty("duration_s") Integer durationS,
            @JsonProperty("max_active_duration_s") Integer maxActiveDurationS,
            @JsonProperty("pulse_enabled") Boolean pulseEnabled,
            @JsonProperty("pulse_run_s") Integer pulseRunS,
            @JsonProperty("pulse_pause_s") Integer pulsePauseS,
            @JsonProperty("boxes") List<BoxTarget> boxes,
            @JsonProperty("water_volume_l") Double waterVolumeL,
            @JsonProperty("ph") Double ph,
            @JsonProperty("fertilizers_per_liter") String fertilizersPerLiter
    ) {
        public Start(
                Integer pumpId,
                String source,
                String mode,
                Integer durationS,
                Integer maxActiveDurationS,
                Boolean pulseEnabled,
                Integer pulseRunS,
                Integer pulsePauseS,
                List<BoxTarget> boxes,
                Double ph,
                String fertilizersPerLiter
        ) {
            this(
                    pumpId,
                    source,
                    mode,
                    durationS,
                    maxActiveDurationS,
                    pulseEnabled,
                    pulseRunS,
                    pulsePauseS,
                    boxes,
                    null,
                    ph,
                    fertilizersPerLiter
            );
        }
    }

    public record BoxTarget(
            @JsonProperty("box_id") Integer boxId,
            @JsonProperty("box_name") String boxName,
            @JsonProperty("room_id") Integer roomId,
            @JsonProperty("room_name") String roomName,
            @JsonProperty("plants") List<PlantTarget> plants,
            @JsonProperty("leak_sensors") List<LeakTarget> leakSensors
    ) {
    }

    public record PlantTarget(
            @JsonProperty("plant_id") Integer plantId,
            @JsonProperty("plant_name") String plantName,
            @JsonProperty("rate_ml_per_hour") Integer rateMlPerHour,
            @JsonProperty("owner_id") Integer ownerId
    ) {
    }

    public record LeakTarget(
            @JsonProperty("reference") String reference,
            @JsonProperty("resource_binding_id") Integer resourceBindingId,
            @JsonProperty("source_type") String sourceType,
            @JsonProperty("external_id") String externalId,
            @JsonProperty("property") String property,
            @JsonProperty("label") String label,
            @JsonProperty("available") Boolean available,
            @JsonProperty("triggered") Boolean triggered
    ) {
    }

    public record LeakState(
            @JsonProperty("reference") String reference,
            @JsonProperty("available") boolean available,
            @JsonProperty("triggered") boolean triggered
    ) {
    }

    public record LeakProbe(
            @JsonProperty("device_online") Boolean deviceOnline,
            @JsonProperty("pump_running") Boolean pumpRunning,
            @JsonProperty("pump_observed_at") LocalDateTime pumpObservedAt,
            @JsonProperty("leak_states") List<LeakState> leakStates
    ) {
        public LeakProbe(Boolean deviceOnline, Boolean pumpRunning, List<LeakState> leakStates) {
            this(deviceOnline, pumpRunning, null, leakStates);
        }
    }

    public record Probe(
            @JsonProperty("session_id") Long sessionId,
            @JsonProperty("pump_id") Integer pumpId,
            @JsonProperty("device_key") String deviceKey,
            @JsonProperty("mode") String mode,
            @JsonProperty("phase") String phase,
            @JsonProperty("leak_sensors") List<LeakTarget> leakSensors
    ) {
    }

    public record PlantSnapshot(
            @JsonProperty("plant_id") Integer plantId,
            @JsonProperty("plant_name") String plantName,
            @JsonProperty("rate_ml_per_hour") Integer rateMlPerHour,
            @JsonProperty("owner_id") Integer ownerId,
            @JsonProperty("duration_s") Integer durationS,
            @JsonProperty("water_volume_l") Double waterVolumeL
    ) {
    }

    public record BoxSnapshot(
            @JsonProperty("box_id") Integer boxId,
            @JsonProperty("box_name") String boxName,
            @JsonProperty("room_id") Integer roomId,
            @JsonProperty("room_name") String roomName,
            @JsonProperty("plants") List<PlantSnapshot> plants,
            @JsonProperty("leak_sensors") List<LeakTarget> leakSensors
    ) {
    }

    public record View(
            @JsonProperty("id") Long id,
            @JsonProperty("pump_id") Integer pumpId,
            @JsonProperty("device_id") Integer deviceId,
            @JsonProperty("device_key") String deviceKey,
            @JsonProperty("channel") Integer channel,
            @JsonProperty("pump_label") String pumpLabel,
            @JsonProperty("user_id") Integer userId,
            @JsonProperty("source") String source,
            @JsonProperty("mode") String mode,
            @JsonProperty("status") String status,
            @JsonProperty("phase") String phase,
            @JsonProperty("planned_duration_s") Integer plannedDurationS,
            @JsonProperty("max_active_duration_s") Integer maxActiveDurationS,
            @JsonProperty("pulse_enabled") boolean pulseEnabled,
            @JsonProperty("pulse_run_s") Integer pulseRunS,
            @JsonProperty("pulse_pause_s") Integer pulsePauseS,
            @JsonProperty("active_duration_s") int activeDurationS,
            @JsonProperty("remaining_active_s") Integer remainingActiveS,
            @JsonProperty("phase_remaining_s") Integer phaseRemainingS,
            @JsonProperty("known_volume_l") Double knownVolumeL,
            @JsonProperty("partial_volume") boolean partialVolume,
            @JsonProperty("volume_complete") boolean volumeComplete,
            @JsonProperty("started_at") LocalDateTime startedAt,
            @JsonProperty("phase_started_at") LocalDateTime phaseStartedAt,
            @JsonProperty("finished_at") LocalDateTime finishedAt,
            @JsonProperty("updated_at") LocalDateTime updatedAt,
            @JsonProperty("correlation_id") String correlationId,
            @JsonProperty("completion_reason") String completionReason,
            @JsonProperty("error_message") String errorMessage,
            @JsonProperty("boxes") List<BoxSnapshot> boxes
    ) {
    }

    public record Page(
            @JsonProperty("items") List<View> items,
            @JsonProperty("next_before_id") Long nextBeforeId
    ) {
    }

    public record BoxStatistics(
            @JsonProperty("box_id") Integer boxId,
            @JsonProperty("range") String range,
            @JsonProperty("from") LocalDateTime from,
            @JsonProperty("to") LocalDateTime to,
            @JsonProperty("session_count") long sessionCount,
            @JsonProperty("active_duration_s") long activeDurationS,
            @JsonProperty("known_volume_l") Double knownVolumeL,
            @JsonProperty("partial_volume") boolean partialVolume,
            @JsonProperty("mode_counts") Map<String, Long> modeCounts,
            @JsonProperty("reason_counts") Map<String, Long> reasonCounts,
            @JsonProperty("sessions") List<View> sessions,
            @JsonProperty("next_before_id") Long nextBeforeId,
            @JsonProperty("active_session") View activeSession
    ) {
    }
}
