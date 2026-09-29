package ru.growerhub.backend.pushok;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ru.growerhub.backend.common.config.zigbee.PushokSettings;

@EnabledIfEnvironmentVariable(named = "PUSHOK_LIVE_READONLY", matches = "true")
class PushokCloudLiveTest {
    @Test void verifiesHubSignatureReadsMetadataAndReconnectsWithoutRegistration() throws Exception {
        var mapper = new ObjectMapper();
        var settings = settings();
        var secrets = mapper.readValue(Base64.getDecoder().decode(System.getenv("PUSHOK_LIVE_CREDENTIALS")), PushokCredentials.Secrets.class);
        String hub = System.getenv("PUSHOK_LIVE_HUB");
        String key = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try (var client = new PushokCloudClient(mapper, settings)) {
                client.connect(hub, secrets, key, false);
                key = client.hubPublicKey();
                assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))))
                        .isEqualTo(System.getenv("PUSHOK_LIVE_KEY_SHA256"));
                var list = client.request("listObjects", Map.of("type", "zigbee"));
                assertThat(list.isArray()).isTrue();
                assertThat(list.size()).isGreaterThan(0);
                for (var description : list) {
                    var params = Map.of("id", description.path("id").asText(), "type", "zigbee");
                    var attributes = client.request("getAttributes", params);
                    var raw = client.request("getAdapter", Map.of("drv", description.path("drv").asText()));
                    var adapter = raw.path("content").isTextual() ? mapper.readTree(raw.path("content").asText()) : raw;
                    var device = new PushokDevice(description, PushokDevice.name(attributes, description.path("id").asText(), mapper), adapter);
                    device.update(client.request("getState", params));
                    assertThat(mapper.valueToTree(device.metadata(java.util.List.of("state"))).path("definition").path("exposes").isArray()).isTrue();
                    assertThat(device.state()).isNotEmpty();
                }
            }
        }
        try (var client = new PushokCloudClient(mapper, settings)) {
            assertThatThrownBy(() -> client.connect(hub, secrets, "unexpected-key", false))
                    .isInstanceOf(PushokCloudClient.Failure.class).hasMessage("HUB_IDENTITY_CHANGED");
        }
        var store = new PushokCredentials(settings, mapper);
        var id = UUID.randomUUID();
        var fresh = store.decrypt(id, store.createEncryptedCredentials(id, "unused"));
        try (var client = new PushokCloudClient(mapper, settings)) {
            assertThatThrownBy(() -> client.connect(hub, fresh, null, false))
                    .isInstanceOf(PushokCloudClient.Failure.class).hasMessage("PAIRING_REQUIRED");
        }
    }
    private PushokSettings settings() {
        var settings = new PushokSettings();
        settings.setCloudUrl("wss://iotgate.pushok.net");
        settings.setHubIdPattern("^pushok-[A-F0-9]{6}-[A-Za-z0-9]{4,12}$");
        settings.setRequestTimeoutSeconds(8);
        settings.setMaxMessageBytes(1048576);
        settings.setEncryptionKey("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        return settings;
    }
}
