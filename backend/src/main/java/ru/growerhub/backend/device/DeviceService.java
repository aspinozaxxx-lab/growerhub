package ru.growerhub.backend.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.db.DeviceStateLastEntity;
import ru.growerhub.backend.db.DeviceStateLastRepository;
import ru.growerhub.backend.db.SensorDataEntity;
import ru.growerhub.backend.db.SensorDataRepository;
import ru.growerhub.backend.mqtt.model.DeviceState;

@Service
public class DeviceService {
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
            device.setLastSeen(now);
            deviceRepository.save(device);
        }
        shadowStore.updateFromState(deviceId, state);
        upsertDeviceState(deviceId, state, now);
        persistSensorHistoryIfPresent(deviceId, state, now);
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
        Double airTemperature = state.airTemperature();
        Double airHumidity = state.airHumidity();
        if (soilMoisture == null && airTemperature == null && airHumidity == null) {
            return;
        }
        SensorDataEntity sensorData = SensorDataEntity.create();
        sensorData.setDeviceId(deviceId);
        sensorData.setSoilMoisture(soilMoisture);
        sensorData.setAirTemperature(airTemperature);
        sensorData.setAirHumidity(airHumidity);
        sensorData.setTimestamp(timestamp);
        sensorDataRepository.save(sensorData);
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
