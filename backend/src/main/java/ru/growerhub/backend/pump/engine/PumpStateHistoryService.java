package ru.growerhub.backend.pump.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.common.config.pump.PumpHistorySettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.pump.contract.PumpHistoryPoint;
import ru.growerhub.backend.pump.jpa.PumpEntity;
import ru.growerhub.backend.pump.jpa.PumpRepository;
import ru.growerhub.backend.pump.jpa.PumpStateReadingEntity;
import ru.growerhub.backend.pump.jpa.PumpStateReadingRepository;

@Service
public class PumpStateHistoryService {
    private final PumpService pumpService;
    private final PumpRepository pumpRepository;
    private final PumpStateReadingRepository readingRepository;
    private final PumpHistorySettings historySettings;
    private final ObjectMapper objectMapper;
    private final DeviceFacade deviceFacade;

    public PumpStateHistoryService(
            PumpService pumpService,
            PumpRepository pumpRepository,
            PumpStateReadingRepository readingRepository,
            PumpHistorySettings historySettings,
            ObjectMapper objectMapper,
            @Lazy DeviceFacade deviceFacade
    ) {
        this.pumpService = pumpService;
        this.pumpRepository = pumpRepository;
        this.readingRepository = readingRepository;
        this.historySettings = historySettings;
        this.objectMapper = objectMapper;
        this.deviceFacade = deviceFacade;
    }

    public void record(Integer devicePk, DeviceShadowState state, LocalDateTime ts) {
        if (devicePk == null || state == null) {
            return;
        }
        RawPumpState raw = resolveRawState(state);
        if (raw.status() == null) {
            return;
        }
        PumpEntity pump = pumpService.ensureDefaultPump(devicePk);
        if (pump == null) {
            return;
        }
        PumpStateReadingEntity previous = readingRepository
                .findTopByPump_IdOrderByTsDesc(pump.getId())
                .orElse(null);
        if (previous != null
                && Objects.equals(previous.getIsRunning(), raw.isRunning())
                && Objects.equals(previous.getRawStatus(), raw.status())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PumpStateReadingEntity reading = PumpStateReadingEntity.create();
        reading.setPump(pump);
        reading.setTs(ts != null ? ts : now);
        reading.setIsRunning(raw.isRunning());
        reading.setRawStatus(raw.status());
        reading.setRawStateJson(toJson(state));
        reading.setCreatedAt(now);
        readingRepository.save(reading);
    }

    public List<PumpHistoryPoint> getHistory(Integer pumpId, Integer hours, AuthenticatedUser user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        int resolvedHours = hours != null ? hours : historySettings.getDefaultHours();
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(resolvedHours);
        List<PumpStateReadingEntity> rows = readingRepository
                .findStateTransitions(pump.getId(), since);
        PumpStateReadingEntity anchor = readingRepository
                .findTopByPump_IdAndTsLessThanOrderByTsDesc(pump.getId(), since)
                .orElse(null);
        int maxPoints = Math.max(1, historySettings.getMaxPoints());
        int rowLimit = maxPoints - (anchor != null ? 1 : 0);
        List<PumpStateReadingEntity> sampled = rowLimit > 0 ? downsample(rows, rowLimit) : List.of();
        List<PumpHistoryPoint> payload = new ArrayList<>();
        if (anchor != null) {
            payload.add(toHistoryPoint(anchor, since));
        }
        for (PumpStateReadingEntity row : sampled) {
            payload.add(toHistoryPoint(row, row.getTs()));
        }
        return payload;
    }

    public LocalDateTime getOldestTimestamp() {
        return readingRepository.findOldestTimestamp();
    }

    public int compactDay(LocalDateTime fromTs, LocalDateTime toTs) {
        return readingRepository.compactDay(fromTs, toTs);
    }

    private PumpEntity requirePumpAccess(Integer pumpId, AuthenticatedUser user) {
        PumpEntity pump = pumpRepository.findById(pumpId).orElse(null);
        if (pump == null) {
            throw new DomainException("not_found", "nasos ne naiden");
        }
        if (user == null) {
            throw new DomainException("forbidden", "nedostatochno prav dlya etogo nasosa");
        }
        if (!user.isAdmin()) {
            DeviceSummary summary = pump.getDeviceId() != null ? deviceFacade.getDeviceSummary(pump.getDeviceId()) : null;
            Integer ownerId = summary != null ? summary.userId() : null;
            if (ownerId == null || !ownerId.equals(user.id())) {
                throw new DomainException("forbidden", "nedostatochno prav dlya etogo nasosa");
            }
        }
        return pump;
    }

    private RawPumpState resolveRawState(DeviceShadowState state) {
        DeviceShadowState.ManualWateringState manual = state.manualWatering();
        if (manual != null && manual.status() != null) {
            String status = blankToNull(manual.status());
            return new RawPumpState(status, status != null ? "running".equals(status) : null);
        }
        DeviceShadowState.RelayState relay = state.pump();
        if (relay != null && relay.status() != null) {
            String status = blankToNull(relay.status());
            return new RawPumpState(status, status != null ? "on".equalsIgnoreCase(status) : null);
        }
        return new RawPumpState(null, null);
    }

    private String toJson(DeviceShadowState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception ex) {
            return String.valueOf(state);
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private PumpHistoryPoint toHistoryPoint(PumpStateReadingEntity row, LocalDateTime ts) {
        Boolean running = row.getIsRunning();
        return new PumpHistoryPoint(
                ts,
                running != null ? (running ? 1.0 : 0.0) : null,
                running,
                row.getRawStatus()
        );
    }

    private List<PumpStateReadingEntity> downsample(List<PumpStateReadingEntity> points, int maxPoints) {
        if (points.size() <= maxPoints) {
            return points;
        }
        int step = (int) Math.ceil(points.size() / (double) maxPoints);
        List<PumpStateReadingEntity> sampled = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            sampled.add(points.get(i));
        }
        return sampled;
    }

    private record RawPumpState(String status, Boolean isRunning) {
    }
}
