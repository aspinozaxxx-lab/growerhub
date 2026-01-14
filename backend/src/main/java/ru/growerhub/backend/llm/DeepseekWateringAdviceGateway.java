package ru.growerhub.backend.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.advisor.contract.WateringAdviceGateway;
import ru.growerhub.backend.common.config.advisor.AdvisorDeepseekSettings;

@Service
public class DeepseekWateringAdviceGateway implements WateringAdviceGateway {
    private final AdvisorDeepseekSettings settings;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepseekWateringAdviceGateway(AdvisorDeepseekSettings settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.getRequestTimeoutSeconds()))
                .build();
    }

    @Override
    public String requestWateringAdvice(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        if (settings.getBaseUrl() == null || settings.getBaseUrl().isBlank()) {
            return null;
        }
        if (settings.getApiKey() == null || settings.getApiKey().isBlank()) {
            return null;
        }
        ChatRequest requestPayload = new ChatRequest(
                settings.getModel(),
                List.of(new ChatMessage("user", prompt)),
                0.2
        );
        String body;
        try {
            body = objectMapper.writeValueAsString(requestPayload);
        } catch (JsonProcessingException ex) {
            return null;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.getBaseUrl()))
                .timeout(Duration.ofSeconds(settings.getRequestTimeoutSeconds()))
                .header("Authorization", "Bearer " + settings.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            return extractContent(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException ex) {
            return null;
        }
    }

    private String extractContent(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            ChatResponse response = objectMapper.readValue(body, ChatResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return null;
            }
            ChatChoice choice = response.choices().get(0);
            if (choice == null || choice.message() == null) {
                return null;
            }
            return choice.message().content();
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private record ChatRequest(
            @JsonProperty("model") String model,
            @JsonProperty("messages") List<ChatMessage> messages,
            @JsonProperty("temperature") Double temperature
    ) {
    }

    private record ChatMessage(
            @JsonProperty("role") String role,
            @JsonProperty("content") String content
    ) {
    }

    private record ChatResponse(
            @JsonProperty("choices") List<ChatChoice> choices
    ) {
    }

    private record ChatChoice(
            @JsonProperty("message") ChatMessage message
    ) {
    }
}
