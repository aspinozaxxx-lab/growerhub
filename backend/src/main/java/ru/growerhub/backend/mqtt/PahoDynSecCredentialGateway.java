package ru.growerhub.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.common.config.mqtt.MqttProvisioningSettings;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.zigbee.contract.ZigbeeBrokerCredentialGateway;

@Component
public class PahoDynSecCredentialGateway implements ZigbeeBrokerCredentialGateway {
    private static final String COMMAND_TOPIC = "$CONTROL/dynamic-security/v1";
    private static final String RESPONSE_TOPIC = "$CONTROL/dynamic-security/v1/response";

    private final MqttProvisioningSettings settings;
    private final ObjectMapper objectMapper;

    public PahoDynSecCredentialGateway(MqttProvisioningSettings settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    @Override
    public void provision(String username, String password, String clientId, String roleName) {
        execute(Map.of(
                "command", "createClient",
                "username", username,
                "password", password,
                "clientid", clientId,
                "roles", List.of(Map.of("rolename", roleName, "priority", -1))
        ));
    }

    @Override
    public void rotate(String username, String password) {
        execute(Map.of(
                "command", "setClientPassword",
                "username", username,
                "password", password
        ));
    }

    @Override
    public void revoke(String username) {
        execute(Map.of(
                "command", "deleteClient",
                "username", username
        ));
    }

    private synchronized void execute(Map<String, Object> command) {
        validateSettings();
        String correlationData = UUID.randomUUID().toString();
        Map<String, Object> request = Map.of(
                "commands", List.of(command),
                "correlationData", correlationData
        );
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(request);
        } catch (Exception ex) {
            throw new DomainException("internal_error", "Ne udalos podgotovit MQTT provisioning command");
        }

        AtomicReference<JsonNode> responseRef = new AtomicReference<>();
        CountDownLatch responseLatch = new CountDownLatch(1);
        String brokerUri = (settings.isTls() ? "ssl" : "tcp") + "://" + settings.getHost() + ":" + settings.getPort();
        String operationClientId = "gh-provisioning-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        MqttClient client = null;
        try {
            client = new MqttClient(brokerUri, operationClientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            options.setConnectionTimeout(Math.max(1, settings.getResponseTimeoutSeconds()));
            options.setUserName(settings.getUsername());
            options.setPassword(settings.getPassword().toCharArray());
            client.connect(options);
            client.subscribe(RESPONSE_TOPIC, 1, (topic, message) -> {
                try {
                    JsonNode candidate = objectMapper.readTree(message.getPayload());
                    if (correlationData.equals(candidate.path("correlationData").asText())) {
                        responseRef.set(candidate);
                        responseLatch.countDown();
                    }
                } catch (Exception ignored) {
                    // Nekorrektnyj otvet ne dolzhen raskryvat payload ili credentials v logah.
                }
            });
            MqttMessage mqttMessage = new MqttMessage(payload);
            mqttMessage.setQos(1);
            mqttMessage.setRetained(false);
            client.publish(COMMAND_TOPIC, mqttMessage);

            Duration timeout = Duration.ofSeconds(Math.max(1, settings.getResponseTimeoutSeconds()));
            if (!responseLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new DomainException("bad_gateway", "Mosquitto Dynamic Security ne otvetil vovremya");
            }
            validateResponse(responseRef.get());
        } catch (DomainException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DomainException("bad_gateway", "MQTT provisioning prervan");
        } catch (Exception ex) {
            throw new DomainException("bad_gateway", "MQTT provisioning nedostupen");
        } finally {
            closeQuietly(client);
        }
    }

    private void validateSettings() {
        if (!settings.isEnabled()) {
            throw new DomainException("unavailable", "MQTT provisioning vyklyuchen");
        }
        if (settings.getHost() == null || settings.getHost().isBlank()
                || settings.getUsername() == null || settings.getPassword() == null) {
            throw new DomainException("unavailable", "MQTT provisioning ne nastroen");
        }
    }

    private void validateResponse(JsonNode response) {
        JsonNode responses = response != null ? response.path("responses") : null;
        if (responses == null || !responses.isArray() || responses.isEmpty()) {
            throw new DomainException("bad_gateway", "Mosquitto Dynamic Security vernul nekorrektnyj otvet");
        }
        JsonNode first = responses.get(0);
        String error = first.path("error").asText(null);
        if (error != null && !error.isBlank()) {
            throw new DomainException("bad_gateway", "Mosquitto Dynamic Security otklonil operaciyu");
        }
    }

    private void closeQuietly(MqttClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnectForcibly(1_000, 1_000, false);
            }
        } catch (Exception ignored) {
            // Osvobozhdenie resursov ne menjaet rezultat provisioning operacii.
        }
        try {
            client.close();
        } catch (Exception ignored) {
            // Osvobozhdenie resursov ne menjaet rezultat provisioning operacii.
        }
    }
}
