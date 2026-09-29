package ru.growerhub.backend.pushok;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.common.config.zigbee.PushokSettings;
import static org.assertj.core.api.Assertions.*;

class PushokCredentialsTest {
    @Test void credentialsAreBoundToCoordinatorAndCannotBeTamperedWith() {
        var settings = new PushokSettings();
        settings.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        var cipher = new PushokCredentials(settings, new ObjectMapper());
        UUID id = UUID.randomUUID();
        String stored = cipher.createEncryptedCredentials(id, "test-mqtt-secret");
        var secrets = cipher.decrypt(id, stored);
        assertThat(secrets.mqttPassword()).isEqualTo("test-mqtt-secret");
        assertThat(stored).doesNotContain(secrets.privateKey(), secrets.userId(), secrets.mqttPassword());
        assertThat(secrets.toString()).doesNotContain(secrets.privateKey(), secrets.mqttPassword());
        assertThat(Base64.getDecoder().decode(secrets.publicKey())).hasSize(65);
        assertThatThrownBy(() -> cipher.decrypt(UUID.randomUUID(), stored)).isInstanceOf(IllegalStateException.class);
        byte[] bytes = Base64.getDecoder().decode(stored); bytes[bytes.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(id, Base64.getEncoder().encodeToString(bytes))).isInstanceOf(IllegalStateException.class);
        settings.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[16]));
        assertThatThrownBy(() -> cipher.createEncryptedCredentials(id, "secret")).isInstanceOf(IllegalStateException.class);
    }
}
