package ru.growerhub.backend.notification.contract;

public interface TelegramGateway {
    default byte[] photo(String fileId, int maximumBytes) { throw new IllegalStateException("Telegram photos unavailable"); }
    TelegramData.Result send(TelegramData.Delivery delivery);
}
