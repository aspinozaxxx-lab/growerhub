package ru.growerhub.backend.sensor.internal;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.device.DeviceEntity;
import ru.growerhub.backend.sensor.SensorMeasurement;
import ru.growerhub.backend.sensor.SensorEntity;
import ru.growerhub.backend.sensor.SensorReadingSummary;
import ru.growerhub.backend.sensor.SensorReadingEntity;

@Service
public class SensorHistoryService {
    private final EntityManager entityManager;
    private final SensorRepository sensorRepository;
    private final SensorReadingRepository sensorReadingRepository;

    public SensorHistoryService(
            EntityManager entityManager,
            SensorRepository sensorRepository,
            SensorReadingRepository sensorReadingRepository
    ) {
        this.entityManager = entityManager;
        this.sensorRepository = sensorRepository;
        this.sensorReadingRepository = sensorReadingRepository;
    }

    @Transactional
    public List<SensorReadingSummary> record(String deviceId, List<SensorMeasurement> measurements, LocalDateTime ts) {
        if (measurements == null || measurements.isEmpty()) {
            return List.of();
        }
        DeviceEntity device = findDeviceByDeviceId(deviceId);
        if (device == null) {
            return List.of();
        }
        List<SensorReadingSummary> summaries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (SensorMeasurement measurement : measurements) {
            if (measurement == null) {
                continue;
            }
            SensorEntity sensor = sensorRepository.findByDevice_IdAndTypeAndChannel(
                    device.getId(),
                    measurement.type(),
                    measurement.channel()
            ).orElse(null);
            if (sensor == null) {
                sensor = SensorEntity.create();
                sensor.setDevice(device);
                sensor.setType(measurement.type());
                sensor.setChannel(measurement.channel());
                sensor.setDetected(measurement.detected());
                sensor.setCreatedAt(now);
            }
            if (measurement.detected() != sensor.isDetected()) {
                sensor.setDetected(measurement.detected());
            }
            sensor.setUpdatedAt(now);
            sensorRepository.save(sensor);

            if (measurement.value() != null) {
                SensorReadingEntity reading = SensorReadingEntity.create();
                reading.setSensor(sensor);
                reading.setTs(ts);
                reading.setValueNumeric(measurement.value());
                reading.setCreatedAt(now);
                sensorReadingRepository.save(reading);
                summaries.add(new SensorReadingSummary(sensor.getId(), sensor.getType(), ts, measurement.value()));
            }
        }
        return summaries;
    }

    private DeviceEntity findDeviceByDeviceId(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        List<DeviceEntity> result = entityManager
                .createQuery("select d from DeviceEntity d where d.deviceId = :deviceId", DeviceEntity.class)
                .setParameter("deviceId", deviceId)
                .setMaxResults(1)
                .getResultList();
        return result.isEmpty() ? null : result.get(0);
    }
}
