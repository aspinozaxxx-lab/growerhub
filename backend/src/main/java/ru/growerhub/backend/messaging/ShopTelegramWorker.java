package ru.growerhub.backend.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.notification.contract.TelegramGateway;
import ru.growerhub.backend.shop.ShopFacade;

@Component
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class ShopTelegramWorker {
    private final ShopFacade shop;
    private final TelegramGateway gateway;
    public ShopTelegramWorker(ShopFacade shop, TelegramGateway gateway) { this.shop = shop; this.gateway = gateway; }
    @Scheduled(fixedDelayString = "${shop.poll-ms}", scheduler = "shopTaskScheduler")
    public void tick() {
        for (var delivery : shop.claimNotifications()) shop.finishNotification(delivery, gateway.send(delivery));
    }
}
