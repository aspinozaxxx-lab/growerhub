package ru.growerhub.backend.messaging;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.advisor.AdvisorFacade;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;
@Component
public class AssistantChannelWorker {
    private final AdvisorFacade advisor;
    private final AssistantSettings settings;
    public AssistantChannelWorker(AdvisorFacade advisor, AssistantSettings settings) { this.advisor = advisor; this.settings = settings; }
    @Scheduled(fixedDelayString = "${assistant.worker-delay-ms}", scheduler = "assistantTaskScheduler")
    public void tick() {
        if (!settings.enabled()) return;
        try { advisor.processAssistantInbox(); advisor.deliverAssistantResponses(); }
        catch (RuntimeException ignored) { /* Ochered i rezervy ostayutsya v baze; sekrety ne logiruyutsya. */ }
    }
    @Scheduled(fixedDelayString = "${assistant.reports-poll-ms}", scheduler = "assistantTaskScheduler")
    public void reports() {
        if (!settings.enabled()) return;
        for (var due : advisor.assistantReportsDue()) try { advisor.prepareAssistantReport(due); } catch (RuntimeException ignored) { }
        advisor.maintainAssistant();
    }
}
