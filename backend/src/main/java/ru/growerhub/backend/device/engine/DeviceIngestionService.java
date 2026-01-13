package ru.growerhub.backend.device.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.device.jpa.DeviceStateLastEntity;
import ru.growerhub.backend.device.jpa.DeviceStateLastRepository;
import ru.growerhub.backend.sensor.SensorMeasurement;
import ru.growerhub.backend.sensor.SensorType;

@Service
public class DeviceIngestionService {
    private final DeviceRepository deviceRepository;
    private final DeviceStateLastRepository deviceStateLastRepository;
    private final DeviceShadowStore shadowStore;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public DeviceIngestionService(
            DeviceRepository deviceRepository,
            DeviceStateLastRepository deviceStateLastRepository,
            DeviceShadowStore shadowStore,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceStateLastRepository = deviceStateLastRepository;
        this.shadowStore = shadowStore;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public List<SensorMeasurement> handleState(String deviceId, DeviceShadowState state, LocalDateTime now) {
        DeviceEntity device = ensureDeviceExists(deviceId, now);
        if (device != null) {
            applyDefaults(device, deviceId);
            device.setLastSeen(now);
            deviceRepository.save(device);
        }
        shadowStore.updateFromState(deviceId, state, now);
        upsertDeviceState(deviceId, state, now);
        return extractMeasurements(state);
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
            device.setTargetMoisture(DeviceDefaults.TARGET_MOISTURE);
        }
        if (device.getWateringDuration() == null) {
            device.setWateringDuration(DeviceDefaults.WATERING_DURATION);
        }
        if (device.getWateringTimeout() == null) {
            device.setWateringTimeout(DeviceDefaults.WATERING_TIMEOUT);
        }
        if (device.getLightOnHour() == null) {
            device.setLightOnHour(DeviceDefaults.LIGHT_ON_HOUR);
        }
        if (device.getLightOffHour() == null) {
            device.setLightOffHour(DeviceDefaults.LIGHT_OFF_HOUR);
        }
        if (device.getLightDuration() == null) {
            device.setLightDuration(DeviceDefaults.LIGHT_DURATION);
        }
        if (device.getCurrentVersion() == null) {
            device.setCurrentVersion(DeviceDefaults.CURRENT_VERSION);
        }
        if (device.getLatestVersion() == null) {
            device.setLatestVersion(DeviceDefaults.LATEST_VERSION);
        }
        if (device.getUpdateAvailable() == null) {
            device.setUpdateAvailable(DeviceDefaults.UPDATE_AVAILABLE);
        }
    }

    public String defaultName(String deviceId) {
        return DeviceDefaults.defaultName(deviceId);
    }

    public double defaultTargetMoisture() {
        return DeviceDefaults.TARGET_MOISTURE;
    }

    public int defaultWateringDuration() {
        return DeviceDefaults.WATERING_DURATION;
    }

    public int defaultWateringTimeout() {
        return DeviceDefaults.WATERING_TIMEOUT;
    }

    public int defaultLightOnHour() {
        return DeviceDefaults.LIGHT_ON_HOUR;
    }

    public int defaultLightOffHour() {
        return DeviceDefaults.LIGHT_OFF_HOUR;
    }

    public int defaultLightDuration() {
        return DeviceDefaults.LIGHT_DURATION;
    }

    public String defaultCurrentVersion() {
        return DeviceDefaults.CURRENT_VERSION;
    }

    public boolean defaultUpdateAvailable() {
        return DeviceDefaults.UPDATE_AVAILABLE;
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

    private List<SensorMeasurement> extractMeasurements(DeviceShadowState state) {
        List<SensorMeasurement> measurements = new ArrayList<>();
        if (state == null) {
            return measurements;
        }
        if (state.soilMoisture() != null) {
            measurements.add(new SensorMeasurement(SensorType.SOIL_MOISTURE, 0, state.soilMoisture(), true));
        }
        if (state.airTemperature() != null) {
            measurements.add(new SensorMeasurement(SensorType.AIR_TEMPERATURE, 0, state.airTemperature(), true));
        }
        if (state.airHumidity() != null) {
            measurements.add(new SensorMeasurement(SensorType.AIR_HUMIDITY, 0, state.airHumidity(), true));
        }
        DeviceShadowState.AirState air = state.air();
        if (air != null) {
            boolean available = Boolean.TRUE.equals(air.available());
            if (air.temperature() != null && available) {
                measurements.add(new SensorMeasurement(SensorType.AIR_TEMPERATURE, 0, air.temperature(), true));
            }
            if (air.humidity() != null && available) {
                measurements.add(new SensorMeasurement(SensorType.AIR_HUMIDITY, 0, air.humidity(), true));
            }
        }
        DeviceShadowState.SoilState soil = state.soil();
        if (soil != null && soil.ports() != null) {
            for (DeviceShadowState.SoilPort port : soil.ports()) {
                if (port == null || port.port() == null) {
                    continue;
                }
                boolean detected = Boolean.TRUE.equals(port.detected());
                Double value = port.percent() != null ? port.percent().doubleValue() : null;
                if (value != null || detected) {
                    measurements.add(new SensorMeasurement(SensorType.SOIL_MOISTURE, port.port(), value, detected));
                }
            }
        }
        return measurements;
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
