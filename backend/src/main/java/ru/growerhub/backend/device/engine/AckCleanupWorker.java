package ru.growerhub.backend.device.engine;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AckCleanupWorker {
    // Translitem: period ochistki ACK v ms, istochnik - ACK_CLEANUP_PERIOD_SECONDS (sekundy).
    private static final String CLEANUP_DELAY_MS = "${ACK_CLEANUP_PERIOD_SECONDS:60}000";

    private final AckCleanupService cleanupService;

    public AckCleanupWorker(AckCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    // Translitem: scheduled-vyzov prosto delegiruet ochistku v servis s tranzakciej.
    @Scheduled(fixedDelayString = CLEANUP_DELAY_MS)
    public void cleanupExpired() {
        cleanupService.cleanupExpired();
    }
}
