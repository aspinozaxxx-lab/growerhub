package ru.growerhub.backend.device.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.growerhub.backend.common.config.device.DeviceDefaultsSettings;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.device.jpa.DeviceStateLastEntity;
import ru.growerhub.backend.device.jpa.DeviceStateLastRepository;
import ru.growerhub.backend.sensor.contract.SensorMeasurement;
import ru.growerhub.backend.sensor.contract.SensorStatus;
import ru.growerhub.backend.sensor.contract.SensorType;

@Service
public class DeviceIngestionService {
    private final DeviceRepository deviceRepository;
    private final DeviceStateLastRepository deviceStateLastRepository;
    private final DeviceShadowStore shadowStore;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final DeviceDefaultsSettings defaultsSettings;

    public DeviceIngestionService(
            DeviceRepository deviceRepository,
            DeviceStateLastRepository deviceStateLastRepository,
            DeviceShadowStore shadowStore,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            DeviceDefaultsSettings defaultsSettings
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceStateLastRepository = deviceStateLastRepository;
        this.shadowStore = shadowStore;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.defaultsSettings = defaultsSettings;
    }

    public List<SensorMeasurement> handleState(String deviceId, DeviceShadowState state, LocalDateTime now) {
        DeviceEntity device = ensureDeviceExists(deviceId, now);
        if (device != null) {
            applyDefaults(device, deviceId);
            device.setLastSeen(now);
            deviceRepository.save(device);
        }
        shadowStore.updateFromState(deviceId, state, now);
        DeviceShadowState merged = shadowStore.getLastState(deviceId);
        upsertDeviceState(deviceId, merged != null ? merged : state, now);
        return extractMeasurements(state, now);
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
            device.setName(DeviceDefaults.defaultName(deviceId));
        }
        if (device.getTargetMoisture() == null) {
            device.setTargetMoisture(defaultsSettings.getTargetMoisture());
        }
        if (device.getWateringDuration() == null) {
            device.setWateringDuration(defaultsSettings.getWateringDurationSeconds());
        }
        if (device.getWateringTimeout() == null) {
            device.setWateringTimeout(defaultsSettings.getWateringTimeoutSeconds());
        }
        if (device.getLightOnHour() == null) {
            device.setLightOnHour(defaultsSettings.getLightOnHour());
        }
        if (device.getLightOffHour() == null) {
            device.setLightOffHour(defaultsSettings.getLightOffHour());
        }
        if (device.getLightDuration() == null) {
            device.setLightDuration(defaultsSettings.getLightDurationHours());
        }
        if (device.getCurrentVersion() == null) {
            device.setCurrentVersion(defaultsSettings.getCurrentVersion());
        }
        if (device.getLatestVersion() == null) {
            device.setLatestVersion(defaultsSettings.getLatestVersion());
        }
        if (device.getUpdateAvailable() == null) {
            device.setUpdateAvailable(defaultsSettings.isUpdateAvailable());
        }
    }

    public String defaultName(String deviceId) {
        return DeviceDefaults.defaultName(deviceId);
    }

    public double defaultTargetMoisture() {
        return defaultsSettings.getTargetMoisture();
    }

    public int defaultWateringDuration() {
        return defaultsSettings.getWateringDurationSeconds();
    }

    public int defaultWateringTimeout() {
        return defaultsSettings.getWateringTimeoutSeconds();
    }

    public int defaultLightOnHour() {
        return defaultsSettings.getLightOnHour();
    }

    public int defaultLightOffHour() {
        return defaultsSettings.getLightOffHour();
    }

    public int defaultLightDuration() {
        return defaultsSettings.getLightDurationHours();
    }

    public String defaultCurrentVersion() {
        return defaultsSettings.getCurrentVersion();
    }

    public boolean defaultUpdateAvailable() {
        return defaultsSettings.isUpdateAvailable();
    }

    private void upsertDeviceState(String deviceId, DeviceShadowState state, LocalDateTime updatedAt) {
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

    private List<SensorMeasurement> extractMeasurements(DeviceShadowState state, LocalDateTime observedAt) {
        List<SensorMeasurement> measurements = new ArrayList<>();
        if (state == null) {
            return measurements;
        }
        if (state.soilMoisture() != null) {
            measurements.add(new SensorMeasurement(SensorType.SOIL_MOISTURE, 0, state.soilMoisture(), true, SensorStatus.OK, observedAt));
        }
        if (state.airTemperature() != null) {
            measurements.add(new SensorMeasurement(SensorType.AIR_TEMPERATURE, 0, state.airTemperature(), true, SensorStatus.OK, observedAt));
        }
        if (state.airHumidity() != null) {
            measurements.add(new SensorMeasurement(SensorType.AIR_HUMIDITY, 0, state.airHumidity(), true, SensorStatus.OK, observedAt));
        }
        DeviceShadowState.AirState air = state.air();
        if (air != null) {
            SensorStatus status = resolveAirStatus(air);
            boolean detected = status != SensorStatus.DISCONNECTED;
            measurements.add(new SensorMeasurement(
                    SensorType.AIR_TEMPERATURE,
                    0,
                    air.temperature(),
                    detected,
                    status,
                    observedAt
            ));
            measurements.add(new SensorMeasurement(
                    SensorType.AIR_HUMIDITY,
                    0,
                    air.humidity(),
                    detected,
                    status,
                    observedAt
            ));
        }
        DeviceShadowState.SoilState soil = state.soil();
        if (soil != null && soil.ports() != null) {
            for (DeviceShadowState.SoilPort port : soil.ports()) {
                if (port == null || port.port() == null) {
                    continue;
                }
                boolean detected = Boolean.TRUE.equals(port.detected());
                Double value = port.percent() != null ? port.percent().doubleValue() : null;
                SensorStatus status = resolveSoilStatus(port);
                measurements.add(new SensorMeasurement(SensorType.SOIL_MOISTURE, port.port(), value, detected, status, observedAt));
            }
        }
        return measurements;
    }

    private SensorStatus resolveAirStatus(DeviceShadowState.AirState air) {
        if (air == null) {
            return SensorStatus.DISCONNECTED;
        }
        if (air.status() != null) {
            return air.status();
        }
        return Boolean.TRUE.equals(air.available()) ? SensorStatus.OK : SensorStatus.DISCONNECTED;
    }

    private SensorStatus resolveSoilStatus(DeviceShadowState.SoilPort port) {
        if (port == null) {
            return SensorStatus.DISCONNECTED;
        }
        if (port.status() != null) {
            return port.status();
        }
        return Boolean.TRUE.equals(port.detected()) ? SensorStatus.OK : SensorStatus.DISCONNECTED;
    }

    private void createIfMissing(String deviceId, LocalDateTime now) {
        transactionTemplate.executeWithoutResult(status -> {
            if (deviceRepository.findByDeviceId(deviceId).isPresent()) {
                return;
            }
            DeviceEntity device = DeviceEntity.create();
            device.setDeviceId(deviceId);
            device.setName(DeviceDefaults.defaultName(deviceId));
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
