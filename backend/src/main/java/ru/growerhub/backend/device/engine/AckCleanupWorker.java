package ru.growerhub.backend.device.engine;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.device.DeviceFacade;

@Component
public class AckCleanupWorker {
    // Translitem: period ochistki ACK v ms, istochnik - ACK_CLEANUP_PERIOD_SECONDS (sekundy).
    private static final String CLEANUP_DELAY_MS = "${ACK_CLEANUP_PERIOD_SECONDS:60}000";

    private final DeviceFacade deviceFacade;

    public AckCleanupWorker(DeviceFacade deviceFacade) {
        this.deviceFacade = deviceFacade;
    }

    // Translitem: scheduled-vyzov delaet cleanup cherez facade, gde est tranzakciya.
    @Scheduled(fixedDelayString = CLEANUP_DELAY_MS)
    public void cleanupExpired() {
        deviceFacade.cleanupExpiredAcks();
    }
}
