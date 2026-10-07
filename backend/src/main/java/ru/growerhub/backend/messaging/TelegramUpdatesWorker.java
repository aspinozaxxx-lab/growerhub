package ru.growerhub.backend.messaging;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.common.config.TelegramSettings;
import ru.growerhub.backend.notification.NotificationFacade;
import ru.growerhub.backend.notification.contract.TelegramData;

@Component
@ConditionalOnProperty(prefix = "telegram", name = {"enabled", "updates-enabled"}, havingValue = "true")
public class TelegramUpdatesWorker {
    private static final Logger log = LoggerFactory.getLogger(TelegramUpdatesWorker.class);
    private final NotificationFacade notifications;
    private final TelegramHttpGateway gateway;
    private final TelegramSettings settings;
    private final Clock clock;
    private long offset;
    private Instant retryAt = Instant.MIN;

    public TelegramUpdatesWorker(NotificationFacade notifications, TelegramHttpGateway gateway, TelegramSettings settings, Clock clock) {
        this.notifications = notifications; this.gateway = gateway; this.settings = settings; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${telegram.updates-poll-ms}", scheduler = "telegramUpdatesTaskScheduler")
    public void tick() {
        if (clock.instant().isBefore(retryAt)) return;
        try {
            for (var body : gateway.updates(offset)) {
                if (!body.path("update_id").canConvertToLong()) throw new IOException("Missing Telegram update ID");
                long id = body.path("update_id").asLong();
                if (id < offset) continue;
                var update = TelegramUpdateMapper.parse(body);
                String reply = update == null ? null : notifications.receive(update);
                // Sleduyushchij zapros podtverdit sobytie tolko posle kommita Facade.
                offset = id + 1;
                if (reply != null) gateway.send(new TelegramData.Delivery(null, update.chatId(), reply, null, null));
                if (update != null && update.callbackId() != null) gateway.answerCallback(update.callbackId());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            int retry = ex instanceof TelegramHttpGateway.UpdatesRetryException limited ? limited.retrySeconds() : settings.updatesRetrySeconds();
            retryAt = clock.instant().plusSeconds(retry);
            // Ne logiruem URL, tekst soobshcheniya ili prichinu s tokenom bota.
            log.warn("Telegram updates paused; retry in {} seconds", retry);
        }
    }
}
