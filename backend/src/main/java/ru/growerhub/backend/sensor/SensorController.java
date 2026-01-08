package ru.growerhub.backend.sensor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.ApiException;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.api.dto.HistoryDtos;
import ru.growerhub.backend.api.dto.SensorDtos;
import ru.growerhub.backend.user.UserEntity;

@RestController
public class SensorController {
    private static final int MAX_HISTORY_POINTS = 200;

    private final SensorRepository sensorRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final SensorBindingService sensorBindingService;

    public SensorController(
            SensorRepository sensorRepository,
            SensorReadingRepository sensorReadingRepository,
            SensorBindingService sensorBindingService
    ) {
        this.sensorRepository = sensorRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.sensorBindingService = sensorBindingService;
    }

    @PutMapping("/api/sensors/{sensor_id}/bindings")
    @Transactional
    public CommonDtos.OkResponse updateBindings(
            @PathVariable("sensor_id") Integer sensorId,
            @RequestBody SensorDtos.SensorBindingUpdateRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        sensorBindingService.updateBindings(sensorId, request.plantIds(), user);
        return new CommonDtos.OkResponse(true);
    }

    @GetMapping("/api/sensors/{sensor_id}/history")
    public List<HistoryDtos.SensorHistoryPointResponse> getSensorHistory(
            @PathVariable("sensor_id") Integer sensorId,
            @RequestParam(value = "hours", defaultValue = "24") Integer hours,
            @AuthenticationPrincipal UserEntity user
    ) {
        SensorEntity sensor = requireSensorAccess(sensorId, user);
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours);
        List<SensorReadingEntity> rows = sensorReadingRepository
                .findAllBySensor_IdAndTsGreaterThanEqualOrderByTs(sensor.getId(), since);
        List<SensorReadingEntity> sampled = downsample(rows, MAX_HISTORY_POINTS);
        List<HistoryDtos.SensorHistoryPointResponse> payload = new ArrayList<>();
        for (SensorReadingEntity row : sampled) {
            payload.add(new HistoryDtos.SensorHistoryPointResponse(row.getTs(), row.getValueNumeric()));
        }
        return payload;
    }

    private SensorEntity requireSensorAccess(Integer sensorId, UserEntity user) {
        SensorEntity sensor = sensorRepository.findById(sensorId).orElse(null);
        if (sensor == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "sensor ne naiden");
        }
        if (!"admin".equals(user.getRole())) {
            if (sensor.getDevice() == null || sensor.getDevice().getUser() == null) {
                throw new ApiException(HttpStatus.FORBIDDEN, "nedostatochno prav dlya etogo sensora");
            }
            if (!sensor.getDevice().getUser().getId().equals(user.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "nedostatochno prav dlya etogo sensora");
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
