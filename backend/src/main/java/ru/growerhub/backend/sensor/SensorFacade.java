package ru.growerhub.backend.sensor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.sensor.contract.SensorView;
import ru.growerhub.backend.sensor.internal.SensorBindingService;
import ru.growerhub.backend.sensor.internal.SensorHistoryService;
import ru.growerhub.backend.sensor.internal.SensorQueryService;
import ru.growerhub.backend.sensor.internal.SensorReadingRepository;
import ru.growerhub.backend.sensor.internal.SensorRepository;

@Service
public class SensorFacade {
    private static final int MAX_HISTORY_POINTS = 200;

    private final SensorBindingService bindingService;
    private final SensorHistoryService historyService;
    private final SensorQueryService queryService;
    private final SensorRepository sensorRepository;
    private final SensorReadingRepository sensorReadingRepository;

    public SensorFacade(
            SensorBindingService bindingService,
            SensorHistoryService historyService,
            SensorQueryService queryService,
            SensorRepository sensorRepository,
            SensorReadingRepository sensorReadingRepository
    ) {
        this.bindingService = bindingService;
        this.historyService = historyService;
        this.queryService = queryService;
        this.sensorRepository = sensorRepository;
        this.sensorReadingRepository = sensorReadingRepository;
    }

    @Transactional
    public void updateBindings(Integer sensorId, List<Integer> plantIds, AuthenticatedUser user) {
        bindingService.updateBindings(sensorId, plantIds, user);
    }

    @Transactional(readOnly = true)
    public List<SensorHistoryPoint> getHistory(Integer sensorId, Integer hours, AuthenticatedUser user) {
        SensorEntity sensor = requireSensorAccess(sensorId, user);
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours != null ? hours : 24);
        List<SensorReadingEntity> rows = sensorReadingRepository
                .findAllBySensor_IdAndTsGreaterThanEqualOrderByTs(sensor.getId(), since);
        List<SensorReadingEntity> sampled = downsample(rows, MAX_HISTORY_POINTS);
        List<SensorHistoryPoint> payload = new ArrayList<>();
        for (SensorReadingEntity row : sampled) {
            payload.add(new SensorHistoryPoint(row.getTs(), row.getValueNumeric()));
        }
        return payload;
    }

    @Transactional(readOnly = true)
    public List<SensorView> listByDeviceId(Integer deviceId) {
        return queryService.listByDeviceId(deviceId);
    }

    @Transactional(readOnly = true)
    public List<SensorView> listByPlantId(Integer plantId) {
        return queryService.listByPlantId(plantId);
    }

    @Transactional
    public List<SensorReadingSummary> recordMeasurements(
            String deviceId,
            List<SensorMeasurement> measurements,
            LocalDateTime ts
    ) {
        return historyService.record(deviceId, measurements, ts);
    }

    private SensorEntity requireSensorAccess(Integer sensorId, AuthenticatedUser user) {
        SensorEntity sensor = sensorRepository.findById(sensorId).orElse(null);
        if (sensor == null) {
            throw new DomainException("not_found", "sensor ne naiden");
        }
        if (user == null) {
            throw new DomainException("forbidden", "nedostatochno prav dlya etogo sensora");
        }
        if (!user.isAdmin()) {
            if (sensor.getDevice() == null || sensor.getDevice().getUser() == null) {
                throw new DomainException("forbidden", "nedostatochno prav dlya etogo sensora");
            }
            if (!sensor.getDevice().getUser().getId().equals(user.id())) {
                throw new DomainException("forbidden", "nedostatochno prav dlya etogo sensora");
            }
        }
        return sensor;
    }

    private List<SensorReadingEntity> downsample(List<SensorReadingEntity> points, int maxPoints) {
        if (points.size() <= maxPoints) {
            return points;
        }
        int step = (int) Math.ceil(points.size() / (double) maxPoints);
        List<SensorReadingEntity> sampled = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            sampled.add(points.get(i));
        }
        return sampled;
    }
}
