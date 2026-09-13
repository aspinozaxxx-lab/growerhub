package ru.growerhub.backend.demo.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.demo.DemoFacade;
import ru.growerhub.backend.automation.AutomationFacade;

@Component
public class DemoWorker {
    private static final Logger log = LoggerFactory.getLogger(DemoWorker.class);
    private final DemoFacade demo;
    private final AutomationFacade automation;
    public DemoWorker(DemoFacade demo, AutomationFacade automation) { this.demo = demo; this.automation = automation; }

    @Scheduled(fixedDelayString = "${demo.worker-period-ms}", scheduler = "demoTaskScheduler")
    public void tick() {
        for (var id : demo.activeIds()) {
            try {
                var tick = demo.tick(id);
                if (tick == null) continue;
                if (tick.telemetry()) automation.evaluateDemoOwner(tick.ownerId());
                automation.evaluateDemoWatering(tick.ownerId());
            } catch (RuntimeException ex) { log.warn("Oshibka tika demo {}: {}", id, ex.getMessage()); }
        }
    }

    @Scheduled(fixedDelayString = "${demo.cleanup-period-ms}", scheduler = "demoTaskScheduler")
    public void cleanup() {
        try { demo.cleanup(); }
        catch (RuntimeException ex) { log.warn("Oshibka ochistki demo: {}", ex.getMessage()); }
    }
}
