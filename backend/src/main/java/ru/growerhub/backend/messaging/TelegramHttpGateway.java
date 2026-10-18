package ru.growerhub.backend.messaging;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.io.IOException;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
            var response = request("sendMessage", body, settings.timeoutSeconds());
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

    public JsonNode updates(long offset) throws IOException, InterruptedException {
        var response = request("getUpdates", Map.of("offset", offset, "timeout", settings.updatesWaitSeconds(),
                "limit", settings.updatesBatchSize(), "allowed_updates", List.of("message", "callback_query")),
                settings.timeoutSeconds() + settings.updatesWaitSeconds());
        var body = json.readTree(response.body());
        if (body.path("error_code").asInt(response.statusCode()) == 429) {
            throw new UpdatesRetryException(Math.max(settings.updatesRetrySeconds(), body.path("parameters").path("retry_after").asInt()));
        }
        if (response.statusCode() != 200 || !body.path("ok").asBoolean() || !body.path("result").isArray()) {
            throw new IOException("Telegram updates unavailable");
        }
        return body.path("result");
    }

    public void answerCallback(String id) {
        try { request("answerCallbackQuery", Map.of("callback_query_id", id), settings.timeoutSeconds()); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        catch (Exception ignored) { /* Podtverzhdenie knopki ne povtoryaet uzhe sohranennuyu komandu. */ }
    }

    @Override public byte[] photo(String fileId, int maximumBytes) {
        if (fileId == null || !fileId.matches("[A-Za-z0-9_-]{1,255}") || maximumBytes <= 0) throw new IllegalArgumentException("Invalid photo");
        try {
            var response = request("getFile", Map.of("file_id", fileId), settings.timeoutSeconds());
            var body = json.readTree(response.body()); var file = body.path("result");
            String path = file.path("file_path").asText();
            if (response.statusCode() != 200 || !body.path("ok").asBoolean() || file.path("file_size").asLong() > maximumBytes
                    || !path.matches("photos/[A-Za-z0-9_-]+\\.(jpg|jpeg|png)")) throw new IOException();
            var req = HttpRequest.newBuilder(URI.create(settings.apiUrl() + "/file/bot" + settings.botToken() + "/" + path))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds())).GET().build();
            var image = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = image.body()) {
                if (image.statusCode() != 200) throw new IOException();
                byte[] bytes = stream.readNBytes(maximumBytes + 1);
                if (bytes.length > maximumBytes) throw new IOException(); return bytes;
            }
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Photo unavailable"); }
        catch (Exception ex) { throw new IllegalStateException("Photo unavailable"); }
    }

    private HttpResponse<String> request(String method, Map<String, Object> body, int timeoutSeconds) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(settings.apiUrl() + "/bot" + settings.botToken() + "/" + method))
                .timeout(Duration.ofSeconds(timeoutSeconds)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static final class UpdatesRetryException extends IOException {
        private final int retrySeconds;
        public UpdatesRetryException(int retrySeconds) { super("Telegram updates rate limited"); this.retrySeconds = retrySeconds; }
        public int retrySeconds() { return retrySeconds; }
    }
}
