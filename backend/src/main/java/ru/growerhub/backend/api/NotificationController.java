package ru.growerhub.backend.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.growerhub.backend.common.config.TelegramSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.notification.NotificationFacade;
import ru.growerhub.backend.notification.contract.TelegramData;
import ru.growerhub.backend.messaging.TelegramUpdateMapper;

@RestController
@RequestMapping("/api/notifications/telegram")
public class NotificationController {
    private final NotificationFacade notifications;
    private final TelegramSettings settings;
    public NotificationController(NotificationFacade notifications, TelegramSettings settings) { this.notifications = notifications; this.settings = settings; }
    @GetMapping
    public TelegramData.Status status(@AuthenticationPrincipal AuthenticatedUser user) { return notifications.status(user); }
    @PostMapping("/link")
    public TelegramData.Link link(@AuthenticationPrincipal AuthenticatedUser user) { return notifications.link(user); }
    @PostMapping("/confirm")
    public TelegramData.Status confirm(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody Map<String, String> body) { return notifications.confirm(user, body.get("confirmation")); }
    @PutMapping
    public TelegramData.Status preferences(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody TelegramData.Preferences body) { return notifications.preferences(user, body); }
    @DeleteMapping
    public void disconnect(@AuthenticationPrincipal AuthenticatedUser user) { notifications.disconnect(user); }
    @PostMapping("/test")
    public void test(@AuthenticationPrincipal AuthenticatedUser user) { notifications.test(user); }
    @PostMapping("/webhook")
    public Map<String, Object> webhook(@RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", defaultValue = "") String secret, @RequestBody JsonNode body) {
        if (!settings.enabled() || settings.webhookSecret() == null || settings.webhookSecret().isBlank()
                || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), settings.webhookSecret().getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Webhook rejected");
        }
        var update = TelegramUpdateMapper.parse(body);
        if (update == null) return Map.of();
        String reply = notifications.receive(update);
        if (update.callbackId() != null) return Map.of("method", "answerCallbackQuery", "callback_query_id", update.callbackId());
        if (reply != null) return Map.of("method", "sendMessage", "chat_id", update.chatId(), "text", reply);
        return Map.of();
    }
}
