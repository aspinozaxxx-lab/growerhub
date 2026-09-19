package ru.growerhub.backend.sensor.engine;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.sensor.contract.SensorMeasurement;
import ru.growerhub.backend.sensor.contract.SensorReadingSummary;
import ru.growerhub.backend.sensor.contract.SensorStatus;
import ru.growerhub.backend.sensor.jpa.SensorEntity;
import ru.growerhub.backend.sensor.jpa.SensorReadingEntity;
import ru.growerhub.backend.sensor.jpa.SensorReadingRepository;
import ru.growerhub.backend.sensor.jpa.SensorRepository;

@Service
public class SensorHistoryService {
    private final SensorRepository sensorRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final DeviceFacade deviceFacade;

    public SensorHistoryService(
            SensorRepository sensorRepository,
            SensorReadingRepository sensorReadingRepository,
            @Lazy DeviceFacade deviceFacade
    ) {
        this.sensorRepository = sensorRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.deviceFacade = deviceFacade;
    }

    public List<SensorReadingSummary> record(String deviceId, List<SensorMeasurement> measurements, LocalDateTime ts) {
        if (measurements == null || measurements.isEmpty()) {
            return List.of();
        }
        Integer devicePk = deviceFacade.findDeviceId(deviceId);
        if (devicePk == null) {
            return List.of();
        }
        List<SensorReadingSummary> summaries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
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
                sensor.setStatus(measurement.status());
                sensor.setStatusChangedAt(measurement.statusObservedAt() != null ? measurement.statusObservedAt() : ts);
                if (measurement.status() == SensorStatus.ERROR) {
                    sensor.setLastErrorAt(measurement.statusObservedAt() != null ? measurement.statusObservedAt() : ts);
                }
                sensor.setCreatedAt(now);
            }
            if (measurement.detected() != sensor.isDetected()) {
                sensor.setDetected(measurement.detected());
            }
            SensorStatus nextStatus = measurement.status();
            if (nextStatus != sensor.getStatus()) {
                sensor.setStatus(nextStatus);
                sensor.setStatusChangedAt(measurement.statusObservedAt() != null ? measurement.statusObservedAt() : ts);
            }
            if (nextStatus == SensorStatus.ERROR) {
                sensor.setLastErrorAt(measurement.statusObservedAt() != null ? measurement.statusObservedAt() : ts);
            }
            sensor.setUpdatedAt(now);
            sensorRepository.save(sensor);

            if (measurement.value() != null) {
                summaries.add(recordReading(sensor, measurement.value(), ts, now));
            }
        }
        return summaries;
    }

    public List<SensorReadingSummary> seedHistory(String deviceId, Map<LocalDateTime, List<SensorMeasurement>> history) {
        Integer devicePk = deviceFacade.findDeviceId(deviceId);
        List<SensorEntity> sensors = sensorRepository.findAllByDeviceId(devicePk);
        List<SensorReadingSummary> summaries = new ArrayList<>();
        LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        for (var entry : history.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            for (SensorMeasurement measurement : entry.getValue()) {
                if (measurement == null || measurement.value() == null) continue;
                SensorEntity sensor = sensors.stream().filter(item -> item.getType() == measurement.type()
                        && Objects.equals(item.getChannel(), measurement.channel())).findFirst()
                        .orElseThrow(() -> new DomainException("not_found", "Demo sensor ne najden"));
                summaries.add(recordReading(sensor, measurement.value(), entry.getKey(), createdAt));
            }
        }
        return summaries;
    }

    private SensorReadingSummary recordReading(SensorEntity sensor, Double value, LocalDateTime ts, LocalDateTime createdAt) {
        SensorReadingEntity reading = SensorReadingEntity.create();
        reading.setSensor(sensor);
        reading.setTs(ts);
        reading.setValueNumeric(value);
        reading.setCreatedAt(createdAt);
        sensorReadingRepository.save(reading);
        return new SensorReadingSummary(sensor.getId(), sensor.getType(), ts, value);
    }
}
