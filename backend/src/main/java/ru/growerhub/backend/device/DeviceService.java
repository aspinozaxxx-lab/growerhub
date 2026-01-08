package ru.growerhub.backend.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.growerhub.backend.api.dto.DeviceDtos;
import ru.growerhub.backend.api.dto.ManualWateringDtos;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.db.DeviceStateLastEntity;
import ru.growerhub.backend.db.DeviceStateLastRepository;
import ru.growerhub.backend.db.SensorDataEntity;
import ru.growerhub.backend.db.SensorDataRepository;
import ru.growerhub.backend.mqtt.model.DeviceState;

@Service
public class DeviceService {
    // Translitem: format vremeni dlya manual watering status.
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final double DEFAULT_SOIL_MOISTURE = 0.0;
    private static final double DEFAULT_AIR_TEMPERATURE = 0.0;
    private static final double DEFAULT_AIR_HUMIDITY = 0.0;
    private static final boolean DEFAULT_IS_WATERING = false;
    private static final boolean DEFAULT_IS_LIGHT_ON = false;
    private static final double DEFAULT_TARGET_MOISTURE = 40.0;
    private static final int DEFAULT_WATERING_DURATION = 30;
    private static final int DEFAULT_WATERING_TIMEOUT = 300;
    private static final int DEFAULT_LIGHT_ON_HOUR = 6;
    private static final int DEFAULT_LIGHT_OFF_HOUR = 22;
    private static final int DEFAULT_LIGHT_DURATION = 16;
    private static final String DEFAULT_CURRENT_VERSION = "1.0.0";
    private static final String DEFAULT_LATEST_VERSION = "1.0.0";
    private static final boolean DEFAULT_UPDATE_AVAILABLE = false;
    // Translitem: fallback dlya firmware_version v otvete.
    private static final String DEFAULT_FIRMWARE_VERSION = "old";

    private final DeviceRepository deviceRepository;
    private final DeviceShadowStore shadowStore;
    private final DeviceStateLastRepository deviceStateLastRepository;
    private final SensorDataRepository sensorDataRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public DeviceService(
            DeviceRepository deviceRepository,
            DeviceShadowStore shadowStore,
            DeviceStateLastRepository deviceStateLastRepository,
            SensorDataRepository sensorDataRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.deviceRepository = deviceRepository;
        this.shadowStore = shadowStore;
        this.deviceStateLastRepository = deviceStateLastRepository;
        this.sensorDataRepository = sensorDataRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public void handleState(String deviceId, DeviceState state, LocalDateTime now) {
        DeviceEntity device = ensureDeviceExists(deviceId, now);
        if (device != null) {
            applyDefaults(device, deviceId);
            applyStateToDevice(device, state);
            device.setLastSeen(now);
            deviceRepository.save(device);
        }
        shadowStore.updateFromState(deviceId, state, now);
        upsertDeviceState(deviceId, state, now);
        persistSensorHistoryIfPresent(deviceId, state, now);
    }

    // Translitem: spisok ustrojstv dlya polzovatelya cherez shadow.
    public List<DeviceDtos.DeviceResponse> listMyDevices(Integer userId) {
        List<DeviceEntity> devices = deviceRepository.findAllByUser_Id(userId);
        List<DeviceDtos.DeviceResponse> responses = new ArrayList<>();
        for (DeviceEntity device : devices) {
            DeviceDtos.DeviceResponse response = buildDeviceResponse(device.getDeviceId(), userId);
            if (response != null) {
                responses.add(response);
            }
        }
        return responses;
    }

    // Translitem: sobiraem response dlya deviceId s access-check po userId.
    public DeviceDtos.DeviceResponse buildDeviceResponse(String deviceId, Integer userId) {
        DeviceShadowStore.DeviceSnapshot snapshot = shadowStore.getSnapshotOrLoad(deviceId, userId);
        if (snapshot == null) {
            return null;
        }
        return buildDeviceResponse(snapshot);
    }

    // Translitem: status poliva dlya UI (shadow + DB cold start).
    public ManualWateringDtos.ManualWateringStatusResponse buildManualWateringStatus(String deviceId) {
        Map<String, Object> view = shadowStore.getManualWateringView(deviceId);
        if (view != null) {
            return buildManualWateringStatusFromView(view, "calculated");
        }

        DeviceShadowStore.DeviceSnapshot snapshot = shadowStore.getSnapshotOrLoad(deviceId, null);
        if (snapshot == null) {
            return buildManualWateringFallback(null, false, "no_state_yet");
        }
        if (snapshot.source() == DeviceShadowStore.SnapshotSource.DB_STATE) {
            Map<String, Object> dbView = shadowStore.buildManualWateringView(
                    snapshot.state(),
                    snapshot.updatedAt(),
                    "db_state"
            );
            if (dbView != null) {
                return buildManualWateringStatusFromView(dbView, "db_state");
            }
            return buildManualWateringFallback(snapshot.updatedAt(), snapshot.isOnline(), "db_state");
        }
        return buildManualWateringFallback(snapshot.updatedAt(), snapshot.isOnline(), "db_fallback");
    }

    // Translitem: vydaem manual_watering view tol'ko iz shadow.
    public Map<String, Object> getManualWateringView(String deviceId) {
        return shadowStore.getManualWateringView(deviceId);
    }

    // Translitem: debug-damp shadow dlya admin/diagnostiki.
    public Map<String, Object> debugShadowDump(String deviceId) {
        return shadowStore.debugDump(deviceId);
    }

    // Translitem: debug-view manual_watering (bez DB fallback).
    public Map<String, Object> debugManualWateringView(String deviceId) {
        return shadowStore.getManualWateringView(deviceId);
    }

    public DeviceEntity ensureDeviceExists(String deviceId, LocalDateTime now) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device != null) {
            return device;
        }
        createIfMissing(deviceId, now);
        return deviceRepository.findByDeviceId(deviceId).orElse(null);
    }

    public void applyDefaults(DeviceEntity device, String deviceId) {
        if (device.getName() == null) {
            device.setName(defaultName(deviceId));
        }
        if (device.getSoilMoisture() == null) {
            device.setSoilMoisture(DEFAULT_SOIL_MOISTURE);
        }
        if (device.getAirTemperature() == null) {
            device.setAirTemperature(DEFAULT_AIR_TEMPERATURE);
        }
        if (device.getAirHumidity() == null) {
            device.setAirHumidity(DEFAULT_AIR_HUMIDITY);
        }
        if (device.getIsWatering() == null) {
            device.setIsWatering(DEFAULT_IS_WATERING);
        }
        if (device.getIsLightOn() == null) {
            device.setIsLightOn(DEFAULT_IS_LIGHT_ON);
        }
        if (device.getTargetMoisture() == null) {
            device.setTargetMoisture(DEFAULT_TARGET_MOISTURE);
        }
        if (device.getWateringDuration() == null) {
            device.setWateringDuration(DEFAULT_WATERING_DURATION);
        }
        if (device.getWateringTimeout() == null) {
            device.setWateringTimeout(DEFAULT_WATERING_TIMEOUT);
        }
        if (device.getLightOnHour() == null) {
            device.setLightOnHour(DEFAULT_LIGHT_ON_HOUR);
        }
        if (device.getLightOffHour() == null) {
            device.setLightOffHour(DEFAULT_LIGHT_OFF_HOUR);
        }
        if (device.getLightDuration() == null) {
            device.setLightDuration(DEFAULT_LIGHT_DURATION);
        }
        if (device.getCurrentVersion() == null) {
            device.setCurrentVersion(DEFAULT_CURRENT_VERSION);
        }
        if (device.getLatestVersion() == null) {
            device.setLatestVersion(DEFAULT_LATEST_VERSION);
        }
        if (device.getUpdateAvailable() == null) {
            device.setUpdateAvailable(DEFAULT_UPDATE_AVAILABLE);
        }
    }

    public String defaultName(String deviceId) {
        return "Watering Device " + deviceId;
    }

    public double defaultSoilMoisture() {
        return DEFAULT_SOIL_MOISTURE;
    }

    public double defaultAirTemperature() {
        return DEFAULT_AIR_TEMPERATURE;
    }

    public double defaultAirHumidity() {
        return DEFAULT_AIR_HUMIDITY;
    }

    public boolean defaultIsWatering() {
        return DEFAULT_IS_WATERING;
    }

    public boolean defaultIsLightOn() {
        return DEFAULT_IS_LIGHT_ON;
    }

    public double defaultTargetMoisture() {
        return DEFAULT_TARGET_MOISTURE;
    }

    public int defaultWateringDuration() {
        return DEFAULT_WATERING_DURATION;
    }

    public int defaultWateringTimeout() {
        return DEFAULT_WATERING_TIMEOUT;
    }

    public int defaultLightOnHour() {
        return DEFAULT_LIGHT_ON_HOUR;
    }

    public int defaultLightOffHour() {
        return DEFAULT_LIGHT_OFF_HOUR;
    }

    public int defaultLightDuration() {
        return DEFAULT_LIGHT_DURATION;
    }

    public String defaultCurrentVersion() {
        return DEFAULT_CURRENT_VERSION;
    }

    public String defaultLatestVersion() {
        return DEFAULT_LATEST_VERSION;
    }

    public boolean defaultUpdateAvailable() {
        return DEFAULT_UPDATE_AVAILABLE;
    }

    private DeviceDtos.DeviceResponse buildDeviceResponse(DeviceShadowStore.DeviceSnapshot snapshot) {
        DeviceShadowStore.DeviceMeta meta = snapshot.meta();
        DeviceState state = snapshot.state();
        Double airTemperature = resolveAirTemperature(state);
        Double airHumidity = resolveAirHumidity(state);
        Double soilMoisture = state != null ? state.soilMoisture() : null;
        Boolean isWatering = resolveIsWateringValue(state);
        Boolean isLightOn = resolveIsLightOnValue(state);

        return new DeviceDtos.DeviceResponse(
                meta.id(),
                meta.deviceId(),
                defaultString(meta.name(), defaultName(meta.deviceId())),
                snapshot.isOnline(),
                defaultDouble(soilMoisture, DEFAULT_SOIL_MOISTURE),
                defaultDouble(airTemperature, DEFAULT_AIR_TEMPERATURE),
                defaultDouble(airHumidity, DEFAULT_AIR_HUMIDITY),
                defaultBoolean(isWatering, DEFAULT_IS_WATERING),
                defaultBoolean(isLightOn, DEFAULT_IS_LIGHT_ON),
                meta.lastWatering(),
                snapshot.updatedAt(),
                defaultDouble(meta.targetMoisture(), DEFAULT_TARGET_MOISTURE),
                defaultInteger(meta.wateringDuration(), DEFAULT_WATERING_DURATION),
                defaultInteger(meta.wateringTimeout(), DEFAULT_WATERING_TIMEOUT),
                meta.wateringSpeedLph(),
                defaultInteger(meta.lightOnHour(), DEFAULT_LIGHT_ON_HOUR),
                defaultInteger(meta.lightOffHour(), DEFAULT_LIGHT_OFF_HOUR),
                defaultInteger(meta.lightDuration(), DEFAULT_LIGHT_DURATION),
                defaultString(meta.currentVersion(), DEFAULT_CURRENT_VERSION),
                defaultBoolean(meta.updateAvailable(), DEFAULT_UPDATE_AVAILABLE),
                defaultString(resolveFirmwareVersion(state), DEFAULT_FIRMWARE_VERSION),
                meta.userId(),
                meta.plantIds()
        );
    }

    // Translitem: nalivaem tekushchij state v device (dlya denormalizacii).
    private void applyStateToDevice(DeviceEntity device, DeviceState state) {
        if (device == null || state == null) {
            return;
        }
        Double soilMoisture = state.soilMoisture();
        Double airTemperature = resolveAirTemperature(state);
        Double airHumidity = resolveAirHumidity(state);
        Boolean isWatering = resolveIsWateringValue(state);
        Boolean isLightOn = resolveIsLightOnValue(state);

        if (soilMoisture != null) {
            device.setSoilMoisture(soilMoisture);
        }
        if (airTemperature != null) {
            device.setAirTemperature(airTemperature);
        }
        if (airHumidity != null) {
            device.setAirHumidity(airHumidity);
        }
        if (isWatering != null) {
            device.setIsWatering(isWatering);
        }
        if (isLightOn != null) {
            device.setIsLightOn(isLightOn);
        }
    }

    // Translitem: vychislenie statusa poliva iz state (manual ili pump).
    private Boolean resolveIsWateringValue(DeviceState state) {
        if (state == null) {
            return null;
        }
        if (state.manualWatering() != null && state.manualWatering().status() != null) {
            return "running".equals(state.manualWatering().status());
        }
        DeviceState.RelayState pump = state.pump();
        if (pump != null && pump.status() != null) {
            return "on".equalsIgnoreCase(pump.status());
        }
        return null;
    }

    // Translitem: vychislenie statusa sveta iz state.
    private Boolean resolveIsLightOnValue(DeviceState state) {
        if (state == null) {
            return null;
        }
        DeviceState.RelayState light = state.light();
        if (light != null && light.status() != null) {
            return "on".equalsIgnoreCase(light.status());
        }
        return null;
    }

    // Translitem: tekushchaya versiya proshivki iz state.
    private String resolveFirmwareVersion(DeviceState state) {
        if (state == null || state.fwVer() == null) {
            return DEFAULT_FIRMWARE_VERSION;
        }
        String value = state.fwVer();
        return value != null && !value.isBlank() ? value : DEFAULT_FIRMWARE_VERSION;
    }

    private ManualWateringDtos.ManualWateringStatusResponse buildManualWateringStatusFromView(
            Map<String, Object> view,
            String defaultSource
    ) {
        Map<String, Object> enriched = new HashMap<>(view);
        enriched.putIfAbsent("updated_at", enriched.get("last_seen_at"));
        enriched.putIfAbsent("last_seen_at", enriched.get("updated_at"));
        enriched.putIfAbsent("start_time", enriched.get("started_at"));
        enriched.putIfAbsent("duration", enriched.get("duration_s"));
        Boolean isOnline = asBoolean(enriched.get("is_online"));
        enriched.put("offline_reason", Boolean.TRUE.equals(isOnline) ? null : "device_offline");
        if (enriched.get("source") == null) {
            enriched.put("source", defaultSource);
        }
        return new ManualWateringDtos.ManualWateringStatusResponse(
                asString(enriched.get("status")),
                asInteger(enriched.get("duration_s")),
                asInteger(enriched.get("duration")),
                asString(enriched.get("started_at")),
                asString(enriched.get("start_time")),
                asInteger(enriched.get("remaining_s")),
                asString(enriched.get("correlation_id")),
                asString(enriched.get("updated_at")),
                asString(enriched.get("last_seen_at")),
                isOnline,
                asString(enriched.get("offline_reason")),
                asString(enriched.get("source"))
        );
    }

    private ManualWateringDtos.ManualWateringStatusResponse buildManualWateringFallback(
            LocalDateTime updatedAt,
            boolean isOnline,
            String source
    ) {
        String lastSeenIso = formatIsoUtc(updatedAt);
        String offlineReason = isOnline ? null : "device_offline";
        return new ManualWateringDtos.ManualWateringStatusResponse(
                "idle",
                null,
                0,
                null,
                null,
                null,
                null,
                lastSeenIso,
                lastSeenIso,
                isOnline,
                offlineReason,
                source
        );
    }

    private String formatIsoUtc(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC).withNano(0).format(ISO_UTC);
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    // Translitem: helpery dlya default-znachenij v response.
    private Double defaultDouble(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    private Integer defaultInteger(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private Boolean defaultBoolean(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private String defaultString(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private void upsertDeviceState(String deviceId, DeviceState state, LocalDateTime updatedAt) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(state);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        DeviceStateLastEntity record = deviceStateLastRepository.findByDeviceId(deviceId).orElse(null);
        if (record == null) {
            record = DeviceStateLastEntity.create();
            record.setDeviceId(deviceId);
        }
        record.setStateJson(payload);
        record.setUpdatedAt(updatedAt);
        deviceStateLastRepository.save(record);
    }

    private void persistSensorHistoryIfPresent(String deviceId, DeviceState state, LocalDateTime timestamp) {
        if (state == null) {
            return;
        }
        Double soilMoisture = state.soilMoisture();
        Double airTemperature = resolveAirTemperature(state);
        Double airHumidity = resolveAirHumidity(state);
        Double soilMoisture1 = resolveSoilPortPercent(state, 0);
        Double soilMoisture2 = resolveSoilPortPercent(state, 1);
        Boolean pumpRelayOn = resolveRelayState(state.pump());
        Boolean lightRelayOn = resolveRelayState(state.light());
        if (soilMoisture == null
                && airTemperature == null
                && airHumidity == null
                && soilMoisture1 == null
                && soilMoisture2 == null
                && pumpRelayOn == null
                && lightRelayOn == null) {
            return;
        }
        SensorDataEntity sensorData = SensorDataEntity.create();
        sensorData.setDeviceId(deviceId);
        sensorData.setSoilMoisture(soilMoisture);
        sensorData.setSoilMoisture1(soilMoisture1);
        sensorData.setSoilMoisture2(soilMoisture2);
        sensorData.setAirTemperature(airTemperature);
        sensorData.setAirHumidity(airHumidity);
        sensorData.setPumpRelayOn(pumpRelayOn);
        sensorData.setLightRelayOn(lightRelayOn);
        sensorData.setTimestamp(timestamp);
        sensorDataRepository.save(sensorData);
    }

    // Vybor temperatury vozduha s uchetom novogo payload.
    private Double resolveAirTemperature(DeviceState state) {
        if (state == null) {
            return null;
        }
        DeviceState.AirState air = state.air();
        if (air != null) {
            return Boolean.TRUE.equals(air.available()) ? air.temperature() : null;
        }
        return state.airTemperature();
    }

    // Vybor vlazhnosti vozduha s uchetom novogo payload.
    private Double resolveAirHumidity(DeviceState state) {
        if (state == null) {
            return null;
        }
        DeviceState.AirState air = state.air();
        if (air != null) {
            return Boolean.TRUE.equals(air.available()) ? air.humidity() : null;
        }
        return state.airHumidity();
    }

    // Vybor procenta pochvy po portu s uchetom detected.
    private Double resolveSoilPortPercent(DeviceState state, int port) {
        DeviceState.SoilState soil = state.soil();
        if (soil == null || soil.ports() == null) {
            return null;
        }
        for (DeviceState.SoilPort entry : soil.ports()) {
            if (entry == null || entry.port() == null) {
                continue;
            }
            if (entry.port() == port) {
                if (!Boolean.TRUE.equals(entry.detected()) || entry.percent() == null) {
                    return null;
                }
                return entry.percent().doubleValue();
            }
        }
        return null;
    }

    // Konvertaciya statusa rele v Boolean.
    private Boolean resolveRelayState(DeviceState.RelayState relay) {
        if (relay == null || relay.status() == null) {
            return null;
        }
        return "on".equalsIgnoreCase(relay.status());
    }

    private void createIfMissing(String deviceId, LocalDateTime now) {
        transactionTemplate.executeWithoutResult(status -> {
            if (deviceRepository.findByDeviceId(deviceId).isPresent()) {
                return;
            }
            DeviceEntity device = DeviceEntity.create();
            device.setDeviceId(deviceId);
            device.setName(defaultName(deviceId));
            device.setLastSeen(now);
            applyDefaults(device, deviceId);
            try {
                deviceRepository.saveAndFlush(device);
            } catch (DataIntegrityViolationException ex) {
                status.setRollbackOnly();
            }
        });
    }
}
