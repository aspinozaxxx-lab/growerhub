package ru.growerhub.backend.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop")
public record ShopSettings(boolean acceptingRequests, String catalogPath, String telegramChatId,
        String adminUrl, String rateSecret, List<String> trustedProxyAddresses, int rateWindowMinutes,
        int maxRequestsPerAddress, int maxRequestsPerPhone, int maxItems, int maxQuantity,
        int maxNameLength, int maxPhoneLength, int minPhoneDigits, int maxPhoneDigits,
        int maxTelegramLength, int maxCommentLength, int maxCityLength, int maxPickupCodeLength,
        int maxPickupAddressLength, int maxCatalogVersionLength, int maxPageSize,
        int notificationBatchSize, int notificationLeaseSeconds, int notificationMaxAttempts,
        int notificationRetrySeconds) { }
