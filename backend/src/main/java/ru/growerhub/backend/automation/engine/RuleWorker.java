package ru.growerhub.backend.automation.engine;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.common.config.automation.RuleSettings;
@Component
public class RuleWorker {
    private final AutomationFacade facade;
    private final RuleSettings settings;
    public RuleWorker(AutomationFacade facade, RuleSettings settings) { this.facade = facade; this.settings = settings; }
    @Scheduled(fixedDelayString = "${automation.rules.worker-ms}")
    public void tick() {
        for (var owner : facade.ruleOwnersDue()) {
            try {
                facade.evaluateRules(owner);
                for (int n = 0; n < settings.actions(); n++) {
                    var step = facade.claimRuleStep(owner); if (step == null) break;
                    try { var result = facade.executeRuleStep(step); facade.finishRuleStep(step, result, true); }
                    catch (RuntimeException ex) { facade.finishRuleStep(step, JsonNodeFactory.instance.objectNode().put("error", "command_result_unknown"), false); }
                }
            } catch (RuntimeException ignored) { /* Lease vosstanovit ustojchivoe sostoyanie bez povtornoj komandy. */ }
        }
    }
}
