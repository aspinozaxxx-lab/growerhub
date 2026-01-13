package ru.growerhub.backend.sensor.engine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.device.DeviceAccessService;
import ru.growerhub.backend.sensor.SensorMeasurement;
import ru.growerhub.backend.sensor.SensorReadingSummary;
import ru.growerhub.backend.sensor.jpa.SensorEntity;
import ru.growerhub.backend.sensor.jpa.SensorReadingEntity;
import ru.growerhub.backend.sensor.jpa.SensorReadingRepository;
import ru.growerhub.backend.sensor.jpa.SensorRepository;

@Service
public class SensorHistoryService {
    private final SensorRepository sensorRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final DeviceAccessService deviceAccessService;

    public SensorHistoryService(
            SensorRepository sensorRepository,
            SensorReadingRepository sensorReadingRepository,
            DeviceAccessService deviceAccessService
    ) {
        this.sensorRepository = sensorRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.deviceAccessService = deviceAccessService;
    }

    @Transactional
    public List<SensorReadingSummary> record(String deviceId, List<SensorMeasurement> measurements, LocalDateTime ts) {
        if (measurements == null || measurements.isEmpty()) {
            return List.of();
        }
        Integer devicePk = deviceAccessService.findDeviceId(deviceId);
        if (devicePk == null) {
            return List.of();
        }
        List<SensorReadingSummary> summaries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (SensorMeasurement measurement : measurements) {
            if (measurement == null) {
                continue;
            }
            SensorEntity sensor = sensorRepository.findByDeviceIdAndTypeAndChannel(
                    devicePk,
                    measurement.type(),
                    measurement.channel()
            ).orElse(null);
            if (sensor == null) {
                sensor = SensorEntity.create();
                sensor.setDeviceId(devicePk);
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
}


