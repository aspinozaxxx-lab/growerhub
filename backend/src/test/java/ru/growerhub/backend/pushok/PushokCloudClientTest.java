package ru.growerhub.backend.pushok;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.growerhub.backend.common.config.zigbee.PushokSettings;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PushokCloudClientTest {
    @ParameterizedTest
    @CsvSource({"getAttributes,HUB_COMMAND_REJECTED", "listObjects,HUB_COMMAND_REJECTED", "setState,HUB_COMMAND_REJECTED",
            "challenge,PAIRING_REQUIRED", "authenticate,PAIRING_REQUIRED", "addUser,PAIRING_REQUIRED"})
    @SuppressWarnings("unchecked")
    void rejectedRpcKeepsTheMethodWithoutLeakingResponseOrParametersAndDoesNotRetry(String method, String code) throws Exception {
        var mapper = new ObjectMapper();
        var settings = new PushokSettings();
        settings.setRequestTimeoutSeconds(1);
        try (var client = new PushokCloudClient(mapper, settings)) {
            var socket = mock(WebSocket.class);
            ReflectionTestUtils.setField(client, "open", true);
            ReflectionTestUtils.setField(client, "socket", socket);
            var pending = (Map<Integer, CompletableFuture<JsonNode>>) ReflectionTestUtils.getField(client, "pending");
            when(socket.sendText(any(), eq(true))).thenAnswer(invocation -> {
                var request = mapper.readTree(invocation.getArgument(0).toString());
                pending.get(request.path("id").asInt()).complete(mapper.readTree("{\"error\":\"not_found\",\"msg\":\"private response\"}"));
                return CompletableFuture.completedFuture(socket);
            });

            assertThatThrownBy(() -> client.request(method, Map.of("id", "private-id")))
                    .isInstanceOfSatisfying(PushokCloudClient.Failure.class, failure -> {
                        assertThat(failure.code()).isEqualTo(code);
                        assertThat(failure.method()).isEqualTo(method);
                        assertThat(failure.toString()).doesNotContain("private-id", "private response", "not_found");
                    });
            verify(socket, times(1)).sendText(any(), eq(true));
            assertThat(pending).isEmpty();
        }
    }
}
