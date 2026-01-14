package ru.growerhub.backend.sensor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.config.sensor.SensorHistorySettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.sensor.contract.SensorHistoryPoint;
import ru.growerhub.backend.sensor.contract.SensorMeasurement;
import ru.growerhub.backend.sensor.contract.SensorReadingSummary;
import ru.growerhub.backend.sensor.contract.SensorView;
import ru.growerhub.backend.sensor.engine.SensorBindingService;
import ru.growerhub.backend.sensor.engine.SensorHistoryService;
import ru.growerhub.backend.sensor.engine.SensorQueryService;
import ru.growerhub.backend.sensor.jpa.SensorEntity;
import ru.growerhub.backend.sensor.jpa.SensorReadingEntity;
import ru.growerhub.backend.sensor.jpa.SensorReadingRepository;
import ru.growerhub.backend.sensor.jpa.SensorRepository;

@Service
public class SensorFacade {
    private final SensorBindingService bindingService;
    private final SensorHistoryService historyService;
    private final SensorQueryService queryService;
    private final SensorRepository sensorRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final DeviceFacade deviceFacade;
    private final SensorHistorySettings historySettings;

    public SensorFacade(
            SensorBindingService bindingService,
            SensorHistoryService historyService,
            SensorQueryService queryService,
            SensorRepository sensorRepository,
            SensorReadingRepository sensorReadingRepository,
            @Lazy DeviceFacade deviceFacade,
            SensorHistorySettings historySettings
    ) {
        this.bindingService = bindingService;
        this.historyService = historyService;
        this.queryService = queryService;
        this.sensorRepository = sensorRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.deviceFacade = deviceFacade;
        this.historySettings = historySettings;
    }

    @Transactional
    public void updateBindings(Integer sensorId, List<Integer> plantIds, AuthenticatedUser user) {
        bindingService.updateBindings(sensorId, plantIds, user);
    }

    @Transactional(readOnly = true)
    public List<SensorHistoryPoint> getHistory(Integer sensorId, Integer hours, AuthenticatedUser user) {
        SensorEntity sensor = requireSensorAccess(sensorId, user);
        int defaultHours = historySettings.getDefaultHours();
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours != null ? hours : defaultHours);
        List<SensorReadingEntity> rows = sensorReadingRepository
                .findAllBySensor_IdAndTsGreaterThanEqualOrderByTs(sensor.getId(), since);
        List<SensorReadingEntity> sampled = downsample(rows, historySettings.getMaxPoints());
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
    public void deleteByDeviceId(Integer deviceId) {
        if (deviceId == null) {
            return;
        }
        sensorRepository.deleteAllByDeviceId(deviceId);
    }

    @Transactional
    public List<SensorReadingSummary> recordMeasurements(
            String deviceId,
            List<SensorMeasurement> measurements,
            LocalDateTime ts
    ) {
        return historyService.record(deviceId, measurements, ts);
    }

    @Transactional(readOnly = true)
    public Map<Integer, List<Integer>> getPlantIdsBySensorIds(List<Integer> sensorIds) {
        return bindingService.getPlantIdsBySensorIds(sensorIds);
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
            DeviceSummary summary = deviceFacade.getDeviceSummary(sensor.getDeviceId());
            Integer ownerId = summary != null ? summary.userId() : null;
            if (ownerId == null || !ownerId.equals(user.id())) {
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

