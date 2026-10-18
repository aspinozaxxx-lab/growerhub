package ru.growerhub.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.growerhub.backend.advisor.AdvisorFacade;
import ru.growerhub.backend.advisor.contract.AssistantLimitException;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;

@Component
public class AssistantStreams {
    private final AdvisorFacade advisor;
    private final AssistantSettings settings;
    private final ObjectMapper json;
    private final Map<Integer, Subscription> streams = new ConcurrentHashMap<>();
    private static class Subscription {
        final AuthenticatedUser user; final UUID message; final SseEmitter emitter; String last;
        Subscription(AuthenticatedUser user, UUID message, SseEmitter emitter) { this.user = user; this.message = message; this.emitter = emitter; }
    }
    public AssistantStreams(AdvisorFacade advisor, AssistantSettings settings, ObjectMapper json) { this.advisor = advisor; this.settings = settings; this.json = json; }
    public synchronized SseEmitter open(UUID message, AuthenticatedUser user) {
        advisor.assistantMessage(message, user);
        if (!streams.containsKey(user.id()) && streams.size() >= settings.limits().streams()) throw new AssistantLimitException("Подключений много. Повторите через минуту", 60);
        var old = streams.remove(user.id()); if (old != null) old.emitter.complete();
        var emitter = new SseEmitter(settings.limits().streamSeconds() * 1000L);
        var subscription = new Subscription(user, message, emitter); streams.put(user.id(), subscription);
        Runnable remove = () -> streams.remove(user.id(), subscription);
        emitter.onCompletion(remove); emitter.onTimeout(remove); emitter.onError(error -> remove.run());
        return emitter;
    }
    @Scheduled(fixedDelayString = "${assistant.worker-delay-ms}", scheduler = "assistantTaskScheduler")
    public void emit() {
        for (var subscription : streams.values()) {
            try {
                var message = advisor.assistantMessage(subscription.message, subscription.user);
                String current = json.writeValueAsString(message);
                if (!current.equals(subscription.last)) { subscription.emitter.send(SseEmitter.event().name("message").data(message)); subscription.last = current; }
                if (!Set.of("queued", "running").contains(message.status())) { streams.remove(subscription.user.id(), subscription); subscription.emitter.complete(); }
            } catch (Exception ex) { streams.remove(subscription.user.id(), subscription); subscription.emitter.complete(); }
        }
    }
}
