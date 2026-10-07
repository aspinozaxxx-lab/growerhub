package ru.growerhub.backend.messaging;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.common.config.TelegramSettings;
import ru.growerhub.backend.notification.contract.*;

@Component
public class TelegramHttpGateway implements TelegramGateway {
    private final TelegramSettings settings;
    private final ObjectMapper json;
    private final HttpClient client;
    public TelegramHttpGateway(TelegramSettings settings, ObjectMapper json) {
        this.settings = settings; this.json = json;
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()));
        if (settings.proxyHost() != null && !settings.proxyHost().isBlank()) {
            if (settings.proxyPort() < 1 || settings.proxyPort() > 65535) {
                throw new IllegalArgumentException("Invalid Telegram proxy port");
            }
            builder.proxy(ProxySelector.of(new InetSocketAddress(settings.proxyHost(), settings.proxyPort())));
        }
        client = builder.build();
    }
    public TelegramData.Result send(TelegramData.Delivery delivery) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", delivery.chatId()); body.put("text", delivery.text());
            body.put("link_preview_options", Map.of("is_disabled", true));
            if (delivery.keyboard() != null) body.put("reply_markup", json.readTree(delivery.keyboard()));
            var request = HttpRequest.newBuilder(URI.create(settings.apiUrl() + "/bot" + settings.botToken() + "/sendMessage"))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds())).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var data = json.readTree(response.body());
            if (response.statusCode() == 200 && data.path("ok").asBoolean()) return new TelegramData.Result("accepted", null);
            int code = data.path("error_code").asInt(response.statusCode());
            if (code == 403) return new TelegramData.Result("blocked", null);
            if (code == 429) return new TelegramData.Result("retry", data.path("parameters").path("retry_after").asInt(settings.retrySeconds()));
            return new TelegramData.Result(code >= 500 ? "uncertain" : "failed", null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); return new TelegramData.Result("uncertain", null);
        } catch (Exception ex) {
            // URL i isklyuchenie mogut soderzhat token; ne pishem ih v log.
            return new TelegramData.Result("uncertain", null);
        }
    }
}
