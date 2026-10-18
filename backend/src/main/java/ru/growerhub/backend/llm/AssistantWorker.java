package ru.growerhub.backend.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.advisor.AdvisorFacade;
import ru.growerhub.backend.advisor.contract.AssistantData.*;
import ru.growerhub.backend.advisor.contract.AssistantGateway;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;
import ru.growerhub.backend.common.contract.DomainException;

@Component
public class AssistantWorker {
    private final AdvisorFacade advisor;
    private final AssistantGateway gateway;
    private final AssistantSettings settings;
    private final ObjectMapper json;
    private final ExecutorService executor;
    private final Map<UUID, Work> active = new ConcurrentHashMap<>();
    public AssistantWorker(AdvisorFacade advisor, AssistantGateway gateway, AssistantSettings settings, ObjectMapper json) {
        this.advisor = advisor; this.gateway = gateway; this.settings = settings; this.json = json;
        executor = Executors.newFixedThreadPool(settings.limits().publicConcurrency() + settings.limits().ownerConcurrency(),
                Thread.ofPlatform().daemon().name("assistant-", 0).factory());
    }
    @Scheduled(fixedDelayString = "${assistant.worker-delay-ms}", scheduler = "assistantTaskScheduler")
    public void tick() {
        for (var work : active.values()) if (!advisor.assistantWorkAlive(work.id(), work.lease())) gateway.cancel(work.provider(), work.id());
        if (!settings.enabled()) return;
        for (String provider : List.of("codex", "deepseek")) {
            int available = ("codex".equals(provider) ? settings.limits().ownerConcurrency() : settings.limits().publicConcurrency())
                    - (int) active.values().stream().filter(w -> w.provider().equals(provider)).count();
            for (int i = 0; i < available; i++) {
                Work work = advisor.claimAssistantWork(provider);
                if (work == null) break;
                active.put(work.id(), work); executor.submit(() -> process(work));
            }
        }
        var application = advisor.claimAssistantApplication();
        if (application != null) {
            try { advisor.applyAssistantProposal(application.id()); }
            catch (RuntimeException ex) { advisor.failAssistantApplication(application.id(), ex instanceof DomainException d ? d.getCode() : "result_unknown"); }
        }
    }
    private void process(Work work) {
        AtomicLong lastAppend = new AtomicLong();
        try {
            Outcome result = gateway.run(work, text -> {
                long now = System.currentTimeMillis();
                if (now - lastAppend.get() >= settings.workerDelayMs()) { advisor.appendAssistantText(work.id(), work.lease(), text); lastAppend.set(now); }
            }, call -> {
                var permit = advisor.beginAssistantTool(work.id(), work.lease(), call);
                if (!permit.execute()) return permit.result();
                com.fasterxml.jackson.databind.JsonNode resultValue;
                try { resultValue = advisor.executeAssistantTool(work.id(), work.lease(), call); }
                catch (DomainException ex) { resultValue = json.valueToTree(Map.of("error", ex.getCode(), "message", ex.getMessage())); }
                catch (RuntimeException ex) { resultValue = json.valueToTree(Map.of("error", "tool_unavailable")); }
                return advisor.finishAssistantTool(work.id(), work.lease(), call.id(), resultValue);
            }, () -> !advisor.assistantWorkAlive(work.id(), work.lease()));
            advisor.finishAssistantWork(work.id(), work.lease(), result);
        } catch (RuntimeException ex) {
            advisor.finishAssistantWork(work.id(), work.lease(), new Outcome("failed", null, 0, 0, false, "worker_failure"));
        } finally { active.remove(work.id()); }
    }
    @Scheduled(fixedDelayString = "${assistant.codex-check-ms}", initialDelayString = "${assistant.codex-check-start-ms}", scheduler = "assistantTaskScheduler")
    public void checkCodex() {
        if (settings.enabled() && settings.ownerUserId() != null && settings.ownerUserId() > 0)
            advisor.recordAssistantRuntimeStatus(gateway.runtimeStatus());
    }
    @PreDestroy public void stop() {
        active.values().forEach(work -> gateway.cancel(work.provider(), work.id())); executor.shutdownNow();
    }
}
