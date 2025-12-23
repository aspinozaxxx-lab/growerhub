package ru.growerhub.backend.api;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.HistoryDtos;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.db.PlantDeviceEntity;
import ru.growerhub.backend.db.PlantDeviceRepository;
import ru.growerhub.backend.db.PlantJournalEntryEntity;
import ru.growerhub.backend.db.PlantJournalEntryRepository;
import ru.growerhub.backend.db.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.db.SensorDataEntity;
import ru.growerhub.backend.db.SensorDataRepository;

@RestController
public class HistoryController {
    private static final int MAX_HISTORY_POINTS = 200;
    private static final double OUTLIER_SIGMA = 4.0;
    private static final double AIR_TEMP_MIN = -20.0;
    private static final double AIR_TEMP_MAX = 60.0;
    private static final double AIR_HUM_MIN = 0.0;
    private static final double AIR_HUM_MAX = 100.0;
    private static final double SOIL_MIN = 0.0;
    private static final double SOIL_MAX = 100.0;

    private final DeviceRepository deviceRepository;
    private final PlantDeviceRepository plantDeviceRepository;
    private final PlantJournalEntryRepository plantJournalEntryRepository;
    private final SensorDataRepository sensorDataRepository;

    public HistoryController(
            DeviceRepository deviceRepository,
            PlantDeviceRepository plantDeviceRepository,
            PlantJournalEntryRepository plantJournalEntryRepository,
            SensorDataRepository sensorDataRepository
    ) {
        this.deviceRepository = deviceRepository;
        this.plantDeviceRepository = plantDeviceRepository;
        this.plantJournalEntryRepository = plantJournalEntryRepository;
        this.sensorDataRepository = sensorDataRepository;
    }

    @GetMapping("/api/device/{device_id}/sensor-history")
    public List<HistoryDtos.SensorDataPointResponse> getSensorHistory(
            @PathVariable("device_id") String deviceId,
            @RequestParam(value = "hours", defaultValue = "24") Integer hours
    ) {
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours);
        List<SensorDataEntity> rows =
                sensorDataRepository.findAllByDeviceIdAndTimestampGreaterThanEqualOrderByTimestamp(deviceId, since);
        List<HistoryPoint> raw = new ArrayList<>();
        for (SensorDataEntity row : rows) {
            raw.add(new HistoryPoint(
                    row.getTimestamp(),
                    row.getSoilMoisture(),
                    row.getAirTemperature(),
                    row.getAirHumidity()
            ));
        }

        List<HistoryPoint> filtered = filterOutliers(raw);
        List<HistoryPoint> sampled = downsample(filtered, MAX_HISTORY_POINTS);
        List<HistoryDtos.SensorDataPointResponse> payload = new ArrayList<>();
        for (HistoryPoint point : sampled) {
            payload.add(new HistoryDtos.SensorDataPointResponse(
                    point.timestamp(),
                    point.soilMoisture(),
                    point.airTemperature(),
                    point.airHumidity()
            ));
        }
        return payload;
    }

    @GetMapping("/api/device/{device_id}/watering-logs")
    @Transactional
    public List<HistoryDtos.WateringLogResponse> getWateringLogs(
            @PathVariable("device_id") String deviceId,
            @RequestParam(value = "days", defaultValue = "7") Integer days
    ) {
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusDays(days);
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            return List.of();
        }

        List<PlantDeviceEntity> links = plantDeviceRepository.findAllByDevice_Id(device.getId());
        if (links.isEmpty()) {
            return List.of();
        }

        List<Integer> plantIds = new ArrayList<>();
        for (PlantDeviceEntity link : links) {
            if (link.getPlant() != null) {
                plantIds.add(link.getPlant().getId());
            }
        }
        if (plantIds.isEmpty()) {
            return List.of();
        }

        List<Object[]> rows = plantJournalEntryRepository.findWateringEntries(plantIds, since);
        List<HistoryDtos.WateringLogResponse> payload = new ArrayList<>();
        for (Object[] row : rows) {
            PlantJournalEntryEntity entry = (PlantJournalEntryEntity) row[0];
            PlantJournalWateringDetailsEntity details = (PlantJournalWateringDetailsEntity) row[1];
            Integer plantId = entry.getPlant() != null ? entry.getPlant().getId() : null;
            payload.add(new HistoryDtos.WateringLogResponse(
                    entry.getEventAt(),
                    details.getDurationS(),
                    details.getWaterVolumeL(),
                    plantId,
                    details.getPh(),
                    details.getFertilizersPerLiter()
            ));
        }
        return payload;
    }

    private List<HistoryPoint> filterOutliers(List<HistoryPoint> points) {
        List<HistoryPoint> physicallyOk = new ArrayList<>();
        for (HistoryPoint point : points) {
            if (isOutsideBounds(point)) {
                continue;
            }
            physicallyOk.add(point);
        }

        List<Double> tempVals = new ArrayList<>();
        List<Double> humVals = new ArrayList<>();
        List<Double> soilVals = new ArrayList<>();
        for (HistoryPoint point : physicallyOk) {
            if (point.airTemperature() != null) {
                tempVals.add(point.airTemperature());
            }
            if (point.airHumidity() != null) {
                humVals.add(point.airHumidity());
            }
            if (point.soilMoisture() != null) {
                soilVals.add(point.soilMoisture());
            }
        }

        MeanStd tempStats = meanStd(tempVals);
        MeanStd humStats = meanStd(humVals);
        MeanStd soilStats = meanStd(soilVals);

        List<HistoryPoint> filtered = new ArrayList<>();
        for (HistoryPoint point : physicallyOk) {
            if (!withinBand(point.airTemperature(), tempStats)) {
                continue;
            }
            if (!withinBand(point.airHumidity(), humStats)) {
                continue;
            }
            if (!withinBand(point.soilMoisture(), soilStats)) {
                continue;
            }
            filtered.add(point);
        }
        return filtered;
    }

    private boolean isOutsideBounds(HistoryPoint point) {
        if (point.airTemperature() != null
                && (point.airTemperature() < AIR_TEMP_MIN || point.airTemperature() > AIR_TEMP_MAX)) {
            return true;
        }
        if (point.airHumidity() != null
                && (point.airHumidity() < AIR_HUM_MIN || point.airHumidity() > AIR_HUM_MAX)) {
            return true;
        }
        if (point.soilMoisture() != null
                && (point.soilMoisture() < SOIL_MIN || point.soilMoisture() > SOIL_MAX)) {
            return true;
        }
        return false;
    }

    private boolean withinBand(Double value, MeanStd stats) {
        if (value == null || stats.mean() == null || stats.std() == null || stats.std() == 0.0) {
            return true;
        }
        return Math.abs(value - stats.mean()) <= OUTLIER_SIGMA * stats.std();
    }

    private MeanStd meanStd(List<Double> values) {
        if (values.isEmpty()) {
            return new MeanStd(null, null);
        }
        double sum = 0.0;
        for (Double value : values) {
            sum += value;
        }
        double mean = sum / values.size();
        double varianceSum = 0.0;
        for (Double value : values) {
            double diff = value - mean;
            varianceSum += diff * diff;
        }
        double variance = varianceSum / values.size();
        return new MeanStd(mean, Math.sqrt(variance));
    }

    private List<HistoryPoint> downsample(List<HistoryPoint> points, int maxPoints) {
        if (points.size() <= maxPoints) {
            return points;
        }
        int step = (int) Math.ceil(points.size() / (double) maxPoints);
        List<HistoryPoint> sampled = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            sampled.add(points.get(i));
        }
        return sampled;
    }

    private record HistoryPoint(
            LocalDateTime timestamp,
            Double soilMoisture,
            Double airTemperature,
            Double airHumidity
    ) {
    }

    private record MeanStd(Double mean, Double std) {
    }
}
