package ru.growerhub.backend.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.advisor.contract.AssistantData.*;
import ru.growerhub.backend.advisor.contract.AssistantGateway;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;
import ru.growerhub.backend.common.contract.DomainException;

@Component
public class AssistantHttpGateway implements AssistantGateway {
    private final AssistantSettings settings;
    private final ObjectMapper json;
    private final HttpClient client;
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private record Active(CompletableFuture<?> future, AtomicReference<InputStream> input) {}
    public AssistantHttpGateway(AssistantSettings settings, ObjectMapper json) {
        this.settings = settings; this.json = json;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(settings.limits().queueSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Override public List<Model> models() {
        try {
            var response = client.send(request("codex", "/v1/models").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IOException();
            return json.readerForListOf(Model.class).readValue(json.readTree(response.body()).path("models"));
        } catch (Exception ex) { throw new DomainException("unavailable", "Не удалось получить список моделей. Попробуйте позже"); }
    }
    @Override public RuntimeStatus runtimeStatus() {
        try {
            var response = client.send(request("codex", "/v1/runtime").timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IOException();
            return json.readValue(response.body(), RuntimeStatus.class);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            return new RuntimeStatus(null, null, java.time.Instant.now(), false, "runtime_unavailable");
        }
    }
    @Override public RuntimeUpdate runtimeUpdate(RuntimeUpdateRequest value) {
        try {
            var config = settings.maintenance();
            if (config == null || !config.enabled()) throw new IOException();
            URI base = URI.create(config.url());
            if (!Set.of("127.0.0.1", "localhost", "::1", "[::1]").contains(base.getHost()) || !"http".equals(base.getScheme())) throw new IOException();
            var request = HttpRequest.newBuilder(base.resolve("/v1/update")).timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Authorization", "Bearer " + config.token()).header("Content-Type", "application/json");
            if (value == null) request.GET(); else request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(value)));
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 409) throw new DomainException("conflict", "Обновление не принято: проверьте версию и состояние предыдущей операции");
            if (response.statusCode() != 200 && response.statusCode() != 202) throw new IOException();
            var operation = json.readTree(response.body()).get("operation");
            return operation == null || operation.isNull() ? null : json.treeToValue(operation, RuntimeUpdate.class);
        } catch (DomainException ex) { throw ex; }
        catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new DomainException("unavailable", "Не удалось проверить обновление Codex. Операция могла начаться; дождитесь обновления её состояния");
        }
    }
    @Override public Outcome run(Work work, Consumer<String> onText, Function<ToolCall, JsonNode> onTool, BooleanSupplier cancelled) {
        boolean dispatched = false; StringBuilder text = new StringBuilder();
        AtomicReference<InputStream> input = new AtomicReference<>();
        try {
            var request = request(work.provider(), "/v1/jobs").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(work))).build();
            if (cancelled.getAsBoolean()) return new Outcome("cancelled", "", 0, 0, true, "cancelled");
            var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            active.put(work.id(), new Active(future, input)); dispatched = true;
            var response = future.get(); input.set(response.body());
            if (cancelled.getAsBoolean()) { response.body().close(); throw new IOException(); }
            if (response.statusCode() != 200) {
                response.body().close();
                return new Outcome("failed", "", 0, 0, response.statusCode() < 500 && response.statusCode() != 409, "runtime_unavailable");
            }
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelled.getAsBoolean()) throw new IOException();
                    if (line.length() > settings.maxToolResultChars() * settings.limits().maxToolCalls()) throw new IOException();
                    var event = json.readTree(line);
                    switch (event.path("type").asText()) {
                        case "delta" -> {
                            text.append(event.path("text").asText());
                            if (text.length() > settings.limits().maxResponseChars()) throw new IOException();
                            onText.accept(text.toString());
                        }
                        case "tool" -> {
                            String id = event.path("id").asText();
                            JsonNode result = onTool.apply(new ToolCall(id, event.path("name").asText(), event.path("arguments")));
                            var answer = client.send(request(work.provider(), "/v1/jobs/" + work.id() + "/tools/" + URLEncoder.encode(id, StandardCharsets.UTF_8))
                                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(result))).build(), HttpResponse.BodyHandlers.discarding());
                            if (answer.statusCode() != 200) throw new IOException();
                        }
                        case "completed" -> { return new Outcome(event.path("status").asText("failed"), event.path("text").asText(text.toString()),
                                event.path("inputTokens").asLong(), event.path("outputTokens").asLong(), event.path("usageKnown").asBoolean(false),
                                event.path("code").isTextual() ? event.get("code").asText() : null); }
                        default -> { }
                    }
                }
            }
        } catch (Exception ex) { if (ex instanceof InterruptedException) Thread.currentThread().interrupt(); }
        finally { active.remove(work.id()); }
        return new Outcome("failed", text.toString(), 0, 0, !dispatched, "result_unknown");
    }
    @Override public void cancel(String provider, UUID id) {
        var running = active.get(id);
        if (running != null) {
            running.future.cancel(true);
            try { if (running.input.get() != null) running.input.get().close(); } catch (IOException ignored) { }
        }
        try { client.sendAsync(request(provider, "/v1/jobs/" + id).DELETE().build(), HttpResponse.BodyHandlers.discarding()); }
        catch (RuntimeException ignored) { }
    }
    private HttpRequest.Builder request(String provider, String path) {
        boolean owner = "codex".equals(provider);
        String token = owner ? settings.ownerToken() : settings.publicToken();
        if (token == null || token.isBlank()) throw new IllegalStateException("Runtime not configured");
        URI base = URI.create(owner ? settings.ownerUrl() : settings.publicUrl());
        if (!Set.of("127.0.0.1", "localhost", "::1", "[::1]").contains(base.getHost()) || !"http".equals(base.getScheme()))
            throw new IllegalStateException("Runtime must use the private tunnel");
        return HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(owner ? settings.limits().ownerLeaseSeconds() : settings.limits().leaseSeconds()))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
    }
}
