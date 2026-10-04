package ru.growerhub.backend.notification.contract;

public interface TelegramGateway {
    TelegramData.Result send(TelegramData.Delivery delivery);
}
