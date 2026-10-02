package ru.growerhub.backend.weather;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.automation.contract.WeatherForecastData;
import ru.growerhub.backend.common.config.automation.WeatherSettings;

class MetWeatherForecastGatewayTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalDateTime now = LocalDateTime.parse("2026-10-03T07:00:00");
    private WeatherSettings settings(String url) {
        return new WeatherSettings(url, "GrowerHub/1.0 (https://growerhub.ru)", 3, 2000000, 4, 1500, 3600, 12, 6, 2, 24, 72, 2, 240, 1, false, "roof", "pause", "sunny");
    }
    private String fixture() {
        return """
          {"properties":{"meta":{"updated_at":"2026-10-03T06:00:00Z"},"timeseries":[
          {"time":"2026-10-03T07:00:00Z","data":{
            "next_1_hours":{"details":{"precipitation_amount":1},"summary":{"symbol_code":"rain"}},
            "next_6_hours":{"details":{"precipitation_amount":9},"summary":{"symbol_code":"rain"}}}},
          {"time":"2026-10-03T08:00:00Z","data":{"next_6_hours":{"details":{"precipitation_amount":2,"probability_of_precipitation":60},"summary":{"symbol_code":"rain"}}}},
          {"time":"2026-10-03T09:00:00Z","data":{"next_6_hours":{"details":{"precipitation_amount":4},"summary":{"symbol_code":"rain"}}}},
          {"time":"2026-10-03T14:00:00Z","data":{"next_6_hours":{"details":{"precipitation_amount":0},"summary":{"symbol_code":"clearsky_day"}}}}
          ]}}
          """;
    }

    @Test
    void periodsDoNotOverlapAndMissingGlobalProbabilityStaysUnknown() throws Exception {
        var gateway = new MetWeatherForecastGateway(settings("https://example.test"), mapper, Clock.systemUTC());
        var forecast = gateway.parse(mapper.readTree(fixture()), now);
        assertThat(forecast.periods()).hasSize(3);
        assertThat(forecast.periods().getFirst().probability()).isNull();
        assertThat(forecast.periods().getFirst().precipitationMm()).isEqualTo(1);
        assertThat(forecast.periods().get(1).probability()).isEqualTo(60);
        assertThat(forecast.periods().get(1).to()).isEqualTo(now.plusHours(7));
        assertThat(forecast.updatedAt()).isEqualTo(now.minusHours(1));
    }

    @Test
    void expiresConditional304AndRetryAfterControlRequestsWithoutRefreshingModelAge() throws Exception {
        AtomicReference<Instant> time = new AtomicReference<>(now.toInstant(ZoneOffset.UTC));
        Clock clock = mock(Clock.class); when(clock.instant()).thenAnswer(invocation -> time.get());
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> conditional = new AtomicReference<>(), userAgent = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forecast", exchange -> {
            int count = requests.incrementAndGet();
            conditional.set(exchange.getRequestHeaders().getFirst("If-Modified-Since"));
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            if (count == 1) {
                exchange.getResponseHeaders().set("Last-Modified", "Sat, 03 Oct 2026 06:00:00 GMT");
                exchange.getResponseHeaders().set("Expires", "Sat, 03 Oct 2026 07:10:00 GMT");
                byte[] body = fixture().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            } else if (count == 2) {
                exchange.getResponseHeaders().set("Expires", "Sat, 03 Oct 2026 07:20:00 GMT");
                exchange.sendResponseHeaders(304, -1);
            } else {
                exchange.getResponseHeaders().set("Retry-After", "12000"); exchange.sendResponseHeaders(429, -1);
            }
            exchange.close();
        });
        server.start();
        try {
            var gateway = new MetWeatherForecastGateway(settings("http://127.0.0.1:" + server.getAddress().getPort() + "/forecast"), mapper, clock);
            var point = new WeatherForecastData.Location(51.5074, -0.1278, "Test");
            assertThat(gateway.forecast(null, now).issue()).contains("место");
            gateway.forecast(point, now);
            await().atMost(Duration.ofSeconds(3)).until(() -> gateway.forecast(point, now).updatedAt() != null);
            for (int i = 0; i < 10; i++) gateway.forecast(point, now.plusMinutes(9));
            assertThat(requests.get()).isEqualTo(1); assertThat(userAgent.get()).contains("growerhub.ru");
            time.set(now.plusMinutes(11).toInstant(ZoneOffset.UTC));
            gateway.forecast(point, now.plusMinutes(11));
            await().atMost(Duration.ofSeconds(3)).until(() -> now.plusMinutes(11).equals(gateway.forecast(point, now.plusMinutes(11)).retrievedAt()));
            assertThat(requests.get()).isEqualTo(2);
            assertThat(conditional.get()).isEqualTo("Sat, 03 Oct 2026 06:00:00 GMT");
            assertThat(gateway.forecast(point, now.plusMinutes(11)).updatedAt()).isEqualTo(now.minusHours(1));
            time.set(now.plusMinutes(21).toInstant(ZoneOffset.UTC));
            gateway.forecast(point, now.plusMinutes(21));
            await().atMost(Duration.ofSeconds(3)).until(() -> gateway.forecast(point, now.plusMinutes(21)).issue() != null);
            assertThat(requests.get()).isEqualTo(3);
            gateway.forecast(point, now.plusHours(2)); assertThat(requests.get()).isEqualTo(3);
        } finally { server.stop(0); }
    }
}
