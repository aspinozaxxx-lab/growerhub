package ru.growerhub.backend.maintenance;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.config.maintenance.HistoryRetentionSettings;
import ru.growerhub.backend.maintenance.contract.HistoryRetentionResult;
import ru.growerhub.backend.maintenance.jpa.HistoryRetentionStateEntity;
import ru.growerhub.backend.maintenance.jpa.HistoryRetentionStateRepository;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.pump.PumpFacade;
import ru.growerhub.backend.sensor.SensorFacade;
import ru.growerhub.backend.zigbee.ZigbeeFacade;

@Service
public class MaintenanceFacade {
    private static final int RETENTION_STATE_ID = 1;

    private final HistoryRetentionStateRepository stateRepository;
    private final SensorFacade sensorFacade;
    private final PlantFacade plantFacade;
    private final PumpFacade pumpFacade;
    private final ZigbeeFacade zigbeeFacade;
    private final HistoryRetentionSettings settings;
    private final Clock clock;

    public MaintenanceFacade(
            HistoryRetentionStateRepository stateRepository,
            SensorFacade sensorFacade,
            PlantFacade plantFacade,
            PumpFacade pumpFacade,
            ZigbeeFacade zigbeeFacade,
            HistoryRetentionSettings settings,
            Clock clock
    ) {
        this.stateRepository = stateRepository;
        this.sensorFacade = sensorFacade;
        this.plantFacade = plantFacade;
        this.pumpFacade = pumpFacade;
        this.zigbeeFacade = zigbeeFacade;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public HistoryRetentionResult compactNextDay() {
        if (!settings.isEnabled()) {
            return HistoryRetentionResult.noWork(true);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate cutoffDay = LocalDate.now(clock).minusDays(Math.max(1, settings.getRawDays()));
        HistoryRetentionStateEntity state = stateRepository.findLockedById(RETENTION_STATE_ID)
                .orElseGet(() -> stateRepository.saveAndFlush(
                        HistoryRetentionStateEntity.create(RETENTION_STATE_ID, now)
                ));
        if (state.getNextDay() == null) {
            state.setNextDay(resolveFirstHistoryDay(cutoffDay));
            state.setUpdatedAt(now);
            stateRepository.save(state);
        }
        LocalDate day = state.getNextDay();
        if (day == null || !day.isBefore(cutoffDay)) {
            return HistoryRetentionResult.noWork(true);
        }

        LocalDateTime fromTs = day.atStartOfDay();
        LocalDateTime toTs = day.plusDays(1).atStartOfDay();
        int sensorDeleted = sensorFacade.compactHistoryDay(fromTs, toTs);
        int plantDeleted = plantFacade.compactHistoryDay(fromTs, toTs);
        int pumpDeleted = pumpFacade.compactHistoryDay(fromTs, toTs);
        int zigbeeDeleted = zigbeeFacade.compactHistoryDay(fromTs, toTs);

        LocalDate nextDay = day.plusDays(1);
        state.setNextDay(nextDay);
        state.setUpdatedAt(now);
        stateRepository.save(state);
        return new HistoryRetentionResult(
                day,
                sensorDeleted,
                plantDeleted,
                pumpDeleted,
                zigbeeDeleted,
                !nextDay.isBefore(cutoffDay)
        );
    }

    private LocalDate resolveFirstHistoryDay(LocalDate cutoffDay) {
        return Stream.of(
                        sensorFacade.getOldestHistoryTimestamp(),
                        plantFacade.getOldestHistoryTimestamp(),
                        pumpFacade.getOldestHistoryTimestamp(),
                        zigbeeFacade.getOldestHistoryTimestamp()
                )
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .min(Comparator.naturalOrder())
                .map(day -> day.isBefore(cutoffDay) ? day : cutoffDay)
                .orElse(cutoffDay);
    }
}
