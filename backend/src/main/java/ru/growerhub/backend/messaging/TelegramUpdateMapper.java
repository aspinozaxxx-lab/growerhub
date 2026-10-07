package ru.growerhub.backend.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import ru.growerhub.backend.notification.contract.TelegramData;

public final class TelegramUpdateMapper {
    private TelegramUpdateMapper() { }

    public static TelegramData.Update parse(JsonNode body) {
        JsonNode callback = body.path("callback_query");
        JsonNode message = callback.isMissingNode() ? body.path("message") : callback.path("message");
        JsonNode sender = callback.isMissingNode() ? message.path("from") : callback.path("from");
        if (!body.has("update_id") || !message.path("chat").has("id") || sender.path("is_bot").asBoolean()
                || sender.path("id").asLong() != message.path("chat").path("id").asLong()
                || !"private".equals(message.path("chat").path("type").asText())) return null;
        String name = (sender.path("first_name").asText("") + " " + sender.path("last_name").asText("")).trim();
        if (sender.has("username")) name += " (@" + sender.path("username").asText() + ")";
        return new TelegramData.Update(body.path("update_id").asLong(), message.path("chat").path("id").asLong(),
                true, name, message.path("text").asText(""), callback.path("id").asText(null), callback.path("data").asText(null));
    }
}
