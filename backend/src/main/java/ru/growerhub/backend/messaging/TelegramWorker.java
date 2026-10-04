package ru.growerhub.backend.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.notification.NotificationFacade;
import ru.growerhub.backend.notification.contract.TelegramGateway;

@Component
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class TelegramWorker {
    private final NotificationFacade notifications;
    private final TelegramGateway gateway;
    public TelegramWorker(NotificationFacade notifications, TelegramGateway gateway) { this.notifications = notifications; this.gateway = gateway; }
    @Scheduled(fixedDelayString = "${telegram.poll-ms}", scheduler = "telegramTaskScheduler")
    public void tick() {
        notifications.prepare();
        for (var delivery : notifications.claim()) notifications.finish(delivery, gateway.send(delivery));
    }
}
