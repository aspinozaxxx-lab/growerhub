package ru.growerhub.backend.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.common.config.TelegramSettings;
import ru.growerhub.backend.notification.contract.TelegramData;

class TelegramHttpGatewayTest {
    private final ObjectMapper json = new ObjectMapper();

    private TelegramSettings settings(String apiUrl, String proxyHost, int proxyPort) {
        var settings = mock(TelegramSettings.class);
        when(settings.apiUrl()).thenReturn(apiUrl);
        when(settings.botToken()).thenReturn("test-token");
        when(settings.proxyHost()).thenReturn(proxyHost);
        when(settings.proxyPort()).thenReturn(proxyPort);
        when(settings.timeoutSeconds()).thenReturn(3);
        return settings;
    }

    @Test
    void sendsThroughConfiguredProxyWithoutChangingOtherHttpClients() throws Exception {
        var originalSelector = ProxySelector.getDefault();
        var uri = new AtomicReference<URI>();
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            uri.set(exchange.getRequestURI());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new TelegramHttpGateway(settings("http://telegram.invalid", "127.0.0.1", server.getAddress().getPort()), json);
            var delivery = new TelegramData.Delivery(1L, 42L, "Проверить растение", null, "lease");
            assertThat(gateway.send(delivery).status()).isEqualTo("accepted");
            assertThat(uri.get()).isEqualTo(URI.create("http://telegram.invalid/bottest-token/sendMessage"));
            assertThat(json.readTree(body.get()).path("chat_id").asLong()).isEqualTo(42L);
            assertThat(json.readTree(body.get()).path("text").asText()).isEqualTo("Проверить растение");
            assertThat(ProxySelector.getDefault()).isSameAs(originalSelector);
        } finally { server.stop(0); }
    }

    @Test
    void keepsDirectTransportAndDoesNotRepeatAmbiguousDelivery() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bottest-token/sendMessage", exchange -> {
            requests.incrementAndGet();
            byte[] response = "{\"ok\":false,\"error_code\":502}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(502, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new TelegramHttpGateway(settings("http://127.0.0.1:" + server.getAddress().getPort(), "", 0), json);
            assertThat(gateway.send(new TelegramData.Delivery(1L, 42L, "Test", null, "lease")).status()).isEqualTo("uncertain");
            assertThat(requests.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test
    void rejectsInvalidProxyPortBeforeSending() {
        assertThatThrownBy(() -> new TelegramHttpGateway(settings("https://telegram.invalid", "127.0.0.1", 0), json))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
