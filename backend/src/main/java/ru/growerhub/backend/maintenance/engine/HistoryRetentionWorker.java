package ru.growerhub.backend.maintenance.engine;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.common.config.maintenance.HistoryRetentionSettings;
import ru.growerhub.backend.maintenance.MaintenanceFacade;
import ru.growerhub.backend.maintenance.contract.HistoryRetentionResult;

@Component
public class HistoryRetentionWorker {
    private static final Logger log = LoggerFactory.getLogger(HistoryRetentionWorker.class);

    private final MaintenanceFacade maintenanceFacade;
    private final HistoryRetentionSettings settings;
    private final AtomicBoolean running = new AtomicBoolean();

    public HistoryRetentionWorker(
            MaintenanceFacade maintenanceFacade,
            HistoryRetentionSettings settings
    ) {
        this.maintenanceFacade = maintenanceFacade;
        this.settings = settings;
    }

    @Scheduled(
            cron = "${history.retention.cron:0 0 3 * * *}",
            zone = "${history.retention.timezone:Europe/Istanbul}"
    )
    public void scheduledRun() {
        runCatchUp();
    }

    @Scheduled(
            initialDelayString = "${history.retention.startupDelayMs:60000}",
            fixedDelayString = "${history.retention.catchUpPeriodMs:86400000}"
    )
    public void startupCatchUp() {
        runCatchUp();
    }

    private void runCatchUp() {
        if (!settings.isEnabled() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            int maxDays = Math.max(1, settings.getMaxDaysPerRun());
            for (int index = 0; index < maxDays; index++) {
                HistoryRetentionResult result = maintenanceFacade.compactNextDay();
                if (result.day() == null) {
                    return;
                }
                log.info(
                        "Prorezhivanie istorii za {}: udaleno {} strok "
                                + "(sensor={}, plant={}, pump={}, zigbee={})",
                        result.day(),
                        result.totalRowsDeleted(),
                        result.sensorRowsDeleted(),
                        result.plantRowsDeleted(),
                        result.pumpRowsDeleted(),
                        result.zigbeeRowsDeleted()
                );
                if (result.caughtUp()) {
                    return;
                }
            }
            log.info("Prorezhivanie istorii ostavilo backlog posle limita za odin zapusk");
        } catch (RuntimeException ex) {
            log.warn("Prorezhivanie istorii zavershilos oshibkoj: {}", ex.getMessage(), ex);
        } finally {
            running.set(false);
        }
    }
}
