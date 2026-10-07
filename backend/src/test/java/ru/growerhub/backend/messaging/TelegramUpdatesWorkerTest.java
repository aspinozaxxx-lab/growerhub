package ru.growerhub.backend.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.common.config.TelegramSettings;
import ru.growerhub.backend.notification.NotificationFacade;

class TelegramUpdatesWorkerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final NotificationFacade notifications = mock(NotificationFacade.class);
    private final TelegramHttpGateway gateway = mock(TelegramHttpGateway.class);
    private final Clock clock = mock(Clock.class);
    private final TelegramSettings settings = mock(TelegramSettings.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-07T08:00:00Z"));

    private TelegramUpdatesWorker worker() {
        when(clock.instant()).thenAnswer(invocation -> now.get());
        when(settings.updatesRetrySeconds()).thenReturn(60);
        return new TelegramUpdatesWorker(notifications, gateway, settings, clock);
    }

    private JsonNode message(long id) throws Exception {
        return json.readTree("{\"update_id\":" + id + ",\"message\":{\"chat\":{\"id\":42,\"type\":\"private\"},\"from\":{\"id\":42},\"text\":\"/today\"}}");
    }

    @Test
    void acknowledgesOnlyCommittedCommandsAndWaitsAfterFailure() throws Exception {
        when(gateway.updates(0)).thenReturn(json.createArrayNode().add(message(11)).add(message(12)));
        when(gateway.updates(12)).thenReturn(json.createArrayNode().add(message(12)));
        when(gateway.updates(13)).thenReturn(json.createArrayNode());
        when(notifications.receive(argThat(update -> update.updateId() == 12))).thenThrow(new IllegalStateException("Database unavailable")).thenReturn(null);
        var worker = worker();
        worker.tick(); worker.tick();
        verify(gateway, times(1)).updates(anyLong());
        now.set(now.get().plusSeconds(60));
        worker.tick(); worker.tick();
        verify(gateway).updates(12);
        verify(gateway).updates(13);
        verify(notifications, times(1)).receive(argThat(update -> update.updateId() == 11));
        verify(notifications, times(2)).receive(argThat(update -> update.updateId() == 12));
    }

    @Test
    void ignoresGroupsAndForeignCallbacksButProcessesOwnCallbackAndReplies() throws Exception {
        var group = message(20).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) group.path("message").path("chat")).put("type", "group");
        var foreign = json.readTree("{\"update_id\":21,\"callback_query\":{\"id\":\"foreign\",\"from\":{\"id\":99},\"message\":{\"chat\":{\"id\":42,\"type\":\"private\"}},\"data\":\"done:1:occurrence\"}}");
        var own = json.readTree("{\"update_id\":22,\"callback_query\":{\"id\":\"own\",\"from\":{\"id\":42},\"message\":{\"chat\":{\"id\":42,\"type\":\"private\"}},\"data\":\"done:1:occurrence\"}}");
        assertThat(TelegramUpdateMapper.parse(group)).isNull();
        assertThat(TelegramUpdateMapper.parse(foreign)).isNull();
        when(gateway.updates(0)).thenReturn(json.createArrayNode().add(group).add(foreign).add(own));
        when(notifications.receive(any())).thenReturn("Saved");
        worker().tick();
        verify(notifications, times(1)).receive(argThat(update -> update.updateId() == 22 && update.chatId() == 42 && update.callbackData().equals("done:1:occurrence")));
        verify(gateway).answerCallback("own");
        verify(gateway, never()).answerCallback("foreign");
        verify(gateway).send(argThat(delivery -> delivery.chatId() == 42 && delivery.text().equals("Saved")));
    }

    @Test
    void respectsRetryAfterWithoutPollingOrAcknowledgingEarly() throws Exception {
        when(gateway.updates(0)).thenThrow(new TelegramHttpGateway.UpdatesRetryException(120)).thenReturn(json.createArrayNode());
        var worker = worker(); worker.tick();
        now.set(now.get().plusSeconds(119)); worker.tick();
        verify(gateway, times(1)).updates(0);
        now.set(now.get().plusSeconds(1)); worker.tick();
        verify(gateway, times(2)).updates(0);
        verifyNoInteractions(notifications);
    }
}
