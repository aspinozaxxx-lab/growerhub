package ru.growerhub.backend.automation.engine;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.common.config.automation.ControlSettings;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "spring.task.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ControlWorker {
    private final AutomationFacade facade;
    private final ControlSettings settings;
    public ControlWorker(AutomationFacade facade, ControlSettings settings) { this.facade = facade; this.settings = settings; }
    @Scheduled(fixedDelayString = "${automation.controls.worker-ms}")
    public void tick() {
        for (var owner : facade.controlOwnersDue()) try {
            for (int i = 0; i < settings.commandsPerTick(); i++) {
                var step = facade.claimControlCommand(owner); if (step == null) break;
                boolean success = false;
                try { facade.executeControlCommand(step); success = true; }
                catch (RuntimeException ignored) { /* Neizvestnyj rezultat ne povtoryaetsya. */ }
                facade.finishControlCommand(step, success);
            }
        } catch (RuntimeException ignored) { /* Sledujushchij tik sverit ustojchivoe sostoyanie. */ }
    }
    @Scheduled(fixedDelayString = "${automation.controls.cleanup-ms}")
    public void cleanup() { facade.maintainControls(); }
}
