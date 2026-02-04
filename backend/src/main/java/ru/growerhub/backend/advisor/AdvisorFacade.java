package ru.growerhub.backend.advisor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.advisor.contract.WateringAdvice;
import ru.growerhub.backend.advisor.contract.WateringAdviceBundle;
import ru.growerhub.backend.advisor.contract.WateringPrevious;
import ru.growerhub.backend.advisor.engine.WateringAdviceContext;
import ru.growerhub.backend.advisor.engine.WateringAdviceEngine;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.config.advisor.AdvisorSettings;
import ru.growerhub.backend.diagnostics.PlantTiming;
import ru.growerhub.backend.journal.JournalFacade;
import ru.growerhub.backend.journal.contract.JournalWateringInfo;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.plant.contract.PlantInfo;
import ru.growerhub.backend.plant.contract.PlantMetricBucketPoint;
import ru.growerhub.backend.plant.contract.PlantMetricType;

@Service
public class AdvisorFacade {
    private static final Duration WATERING_HISTORY_BUCKET = Duration.ofHours(2);
    private static final int WATERING_HISTORY_HOURS = 72;
    private static final String FERTILIZERS_NAME = "Terra Aquatica TriPart";
    private static final String FERTILIZERS_FORMAT = "gX-mY-bZ drops per liter";

    private final PlantFacade plantFacade;
    private final JournalFacade journalFacade;
    private final WateringAdviceEngine adviceEngine;
    private final AdvisorSettings advisorSettings;

    public AdvisorFacade(
            PlantFacade plantFacade,
            JournalFacade journalFacade,
            WateringAdviceEngine adviceEngine,
            AdvisorSettings advisorSettings
    ) {
        this.plantFacade = plantFacade;
        this.journalFacade = journalFacade;
        this.adviceEngine = adviceEngine;
        this.advisorSettings = advisorSettings;
    }

    /**
     * Poluchaet rekomendacii poliva i predydushchii poliv dlya rastenija.
     */
    @Transactional
    public WateringAdviceBundle getWateringAdvice(Integer plantId, AuthenticatedUser user) {
        if (!advisorSettings.isEnabled()) {
            return null;
        }
        long adviceStart = PlantTiming.startTimer();
        try {
            PlantInfo plant = plantFacade.getPlant(plantId, user);
            long journalStart = PlantTiming.startTimer();
            JournalWateringInfo lastWatering = journalFacade.getLastWatering(plantId, user);
            PlantTiming.recordJournal(journalStart);
            WateringPrevious previous = lastWatering != null
                    ? new WateringPrevious(
                    lastWatering.waterVolumeL(),
                    lastWatering.ph(),
                    lastWatering.fertilizersPerLiter(),
                    lastWatering.eventAt()
            )
                    : null;

            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime since = now.minusHours(WATERING_HISTORY_HOURS);
            List<PlantMetricType> metrics = List.of(
                    PlantMetricType.SOIL_MOISTURE,
                    PlantMetricType.AIR_TEMPERATURE,
                    PlantMetricType.AIR_HUMIDITY
            );
            long historyStart = PlantTiming.startTimer();
            List<PlantMetricBucketPoint> history = plantFacade.getBucketedHistory(
                    plantId,
                    user,
                    metrics,
                    since,
                    WATERING_HISTORY_BUCKET
            );
            PlantTiming.recordHistory(historyStart);
            Integer ageDays = resolveAgeDays(plant != null ? plant.plantedAt() : null);
            WateringAdviceContext context = new WateringAdviceContext(
                    plantId,
                    plant,
                    ageDays,
                    previous,
                    history,
                    FERTILIZERS_NAME,
                    FERTILIZERS_FORMAT
            );
            WateringAdvice advice = adviceEngine.resolveAdvice(
                    context,
                    lastWatering != null ? lastWatering.eventAt() : null
            );
            return new WateringAdviceBundle(previous, advice);
        } finally {
            PlantTiming.recordAdvice(plantId, adviceStart);
        }
    }

    private Integer resolveAgeDays(LocalDateTime plantedAt) {
        if (plantedAt == null) {
            return null;
        }
        LocalDate plantedDate = plantedAt.toLocalDate();
        int ageDays = (int) ChronoUnit.DAYS.between(plantedDate, LocalDate.now(ZoneOffset.UTC));
        return Math.max(ageDays, 0);
    }
}
