package ru.growerhub.backend.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.automation.contract.WeatherForecastData;
import ru.growerhub.backend.automation.contract.WeatherForecastGateway;
import ru.growerhub.backend.common.config.automation.WeatherSettings;

@Component
public class MetWeatherForecastGateway implements WeatherForecastGateway {
    private final WeatherSettings settings;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final HttpClient client;
    private final Map<String, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private LocalDateTime nextRequest = LocalDateTime.MIN;

    @org.springframework.beans.factory.annotation.Autowired
    public MetWeatherForecastGateway(WeatherSettings settings, ObjectMapper mapper, Clock clock) {
        this(settings, mapper, clock, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds())).build());
    }

    MetWeatherForecastGateway(WeatherSettings settings, ObjectMapper mapper, Clock clock, HttpClient client) {
        this.settings = settings; this.mapper = mapper; this.clock = clock; this.client = client;
    }

    @Override
    public synchronized WeatherForecastData.Forecast forecast(WeatherForecastData.Location location, LocalDateTime now) {
        if (location == null || location.latitude() == null || location.longitude() == null)
            return unavailable("Укажите место фермы для прогноза");
        double scale = Math.pow(10, settings.coordinateDecimals());
        String point = "lat=" + (Math.rint(location.latitude() * scale) / scale)
                + "&lon=" + (Math.rint(location.longitude() * scale) / scale);
        Entry entry = cache.get(point);
        if (entry == null) {
            if (cache.size() >= settings.maximumLocations()) {
                var evict = cache.entrySet().stream().filter(e -> !e.getValue().loading).findFirst();
                if (evict.isEmpty()) return unavailable("Очередь прогноза занята; повторим позже");
                cache.remove(evict.get().getKey());
            }
            entry = new Entry(); cache.put(point, entry);
        }
        if (!entry.loading && !now.isBefore(entry.nextRefresh) && !now.isBefore(nextRequest)) {
            entry.loading = true;
            nextRequest = now.plusNanos(settings.minimumRequestMillis() * 1000000L);
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(settings.url() + "?" + point))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds())).header("User-Agent", settings.userAgent())
                    .header("Accept", "application/json").header("Accept-Encoding", "gzip");
            if (entry.lastModified != null) request.header("If-Modified-Since", entry.lastModified);
            Entry target = entry;
            client.sendAsync(request.GET().build(), info -> new LimitedBody(settings.maximumBodyBytes()))
                    .orTimeout(settings.timeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                    .thenAcceptAsync(response -> receive(target, response))
                    .exceptionally(error -> { fail(target); return null; });
        }
        return entry.forecast == null ? unavailable("Прогноз загружается; план будет пересчитан") : entry.forecast;
    }

    private void receive(Entry entry, HttpResponse<byte[]> response) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        try (InputStream raw = new java.io.ByteArrayInputStream(response.body())) {
            WeatherForecastData.Forecast parsed = null;
            if (response.statusCode() == 200) {
                InputStream data = "gzip".equalsIgnoreCase(response.headers().firstValue("Content-Encoding").orElse(""))
                        ? new GZIPInputStream(raw) : raw;
                byte[] bytes = data.readNBytes(settings.maximumBodyBytes() + 1);
                if (bytes.length > settings.maximumBodyBytes()) throw new IllegalArgumentException("Forecast body too large");
                parsed = parse(mapper.readTree(bytes), now);
            }
            synchronized (this) {
                if (response.statusCode() == 200) {
                    entry.forecast = parsed;
                    entry.lastModified = response.headers().firstValue("Last-Modified").orElse(null);
                } else if (response.statusCode() == 304 && entry.forecast != null) {
                    var old = entry.forecast;
                    entry.forecast = new WeatherForecastData.Forecast(old.source(), old.updatedAt(), now, old.periods(), null);
                } else {
                    entry.forecast = unavailable(entry, "Сервис прогноза временно недоступен");
                }
                LocalDateTime retry = now.plusSeconds(settings.retrySeconds());
                entry.nextRefresh = response.statusCode() == 200 || response.statusCode() == 304
                        ? httpDate(response.headers().firstValue("Expires").orElse(null), retry)
                        : retryAfter(response.headers().firstValue("Retry-After").orElse(null), retry, now);
                // Nikogda ne povtoryaem zapros ran'she Expires ili Retry-After.
                if (!entry.nextRefresh.isAfter(now)) entry.nextRefresh = retry;
                if (response.statusCode() != 200 && response.statusCode() != 304 && entry.nextRefresh.isAfter(nextRequest))
                    nextRequest = entry.nextRefresh;
                entry.loading = false;
            }
        } catch (Exception error) { fail(entry); }
    }

    private synchronized void fail(Entry entry) {
        entry.forecast = unavailable(entry, "Не удалось получить прогноз; применяется выбранное правило");
        entry.nextRefresh = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusSeconds(settings.retrySeconds());
        entry.loading = false;
    }

    WeatherForecastData.Forecast parse(JsonNode root, LocalDateTime retrievedAt) {
        LocalDateTime updated = utc(root.path("properties").path("meta").path("updated_at").asText());
        List<WeatherForecastData.Period> periods = new ArrayList<>();
        LocalDateTime coveredUntil = LocalDateTime.MIN;
        for (JsonNode row : root.path("properties").path("timeseries")) {
            LocalDateTime from = utc(row.path("time").asText());
            if (from.isBefore(coveredUntil)) continue;
            JsonNode data = row.path("data");
            for (int hours : List.of(1, 6, 12)) {
                JsonNode period = data.path("next_" + hours + "_hours");
                JsonNode amount = period.path("details").path("precipitation_amount");
                if (!amount.isNumber() || !Double.isFinite(amount.doubleValue()) || amount.doubleValue() < 0) continue;
                JsonNode probability = period.path("details").path("probability_of_precipitation");
                Double chance = probability.isNumber() && Double.isFinite(probability.doubleValue())
                        && probability.doubleValue() >= 0 && probability.doubleValue() <= 100 ? probability.doubleValue() : null;
                coveredUntil = from.plusHours(hours);
                periods.add(new WeatherForecastData.Period(from, coveredUntil, amount.doubleValue(), chance,
                        period.path("summary").path("symbol_code").asText("")));
                break;
            }
        }
        if (periods.isEmpty()) throw new IllegalArgumentException("Forecast has no precipitation periods");
        return new WeatherForecastData.Forecast("MET Norway", updated, retrievedAt, List.copyOf(periods), null);
    }

    private WeatherForecastData.Forecast unavailable(String issue) {
        return new WeatherForecastData.Forecast("MET Norway", null, null, List.of(), issue);
    }
    private WeatherForecastData.Forecast unavailable(Entry entry, String issue) {
        var old = entry.forecast;
        return old == null ? unavailable(issue) : new WeatherForecastData.Forecast(old.source(), old.updatedAt(),
                old.retrievedAt(), old.periods(), issue);
    }
    private LocalDateTime utc(String value) {
        return LocalDateTime.ofInstant(java.time.Instant.parse(value), ZoneOffset.UTC);
    }
    private LocalDateTime httpDate(String value, LocalDateTime fallback) {
        try { return LocalDateTime.ofInstant(ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(), ZoneOffset.UTC); }
        catch (Exception ex) { return fallback; }
    }
    private LocalDateTime retryAfter(String value, LocalDateTime fallback, LocalDateTime now) {
        try { return now.plusSeconds(Math.max(settings.retrySeconds(), Long.parseLong(value))); }
        catch (Exception ex) {
            LocalDateTime date = httpDate(value, fallback); return date.isAfter(fallback) ? date : fallback;
        }
    }
    private static class Entry {
        WeatherForecastData.Forecast forecast;
        LocalDateTime nextRefresh = LocalDateTime.MIN;
        String lastModified;
        boolean loading;
    }

    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private final java.util.concurrent.CompletableFuture<byte[]> result = new java.util.concurrent.CompletableFuture<>();
        private java.util.concurrent.Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit = limit; }
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        public void onNext(List<java.nio.ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IllegalArgumentException("Forecast body too large")); return;
                }
                byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
