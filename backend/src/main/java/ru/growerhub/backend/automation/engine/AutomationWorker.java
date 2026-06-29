package ru.growerhub.backend.automation.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.automation.AutomationFacade;

@Component
public class AutomationWorker {
    private static final Logger log = LoggerFactory.getLogger(AutomationWorker.class);

    private final AutomationFacade automationFacade;

    public AutomationWorker(AutomationFacade automationFacade) {
        this.automationFacade = automationFacade;
    }

    @Scheduled(fixedDelayString = "${automation.workerPeriodMs:30000}")
    public void tick() {
        try {
            automationFacade.evaluateAll();
        } catch (RuntimeException ex) {
            log.warn("Automation worker tick failed: {}", ex.getMessage(), ex);
        }
    }
}
