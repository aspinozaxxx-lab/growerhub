package ru.growerhub.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
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
import ru.growerhub.backend.common.config.mqtt.MqttTopicSettings;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.zigbee.contract.ZigbeeBrokerCredentialGateway;

@Component
public class PahoDynSecCredentialGateway implements ZigbeeBrokerCredentialGateway {
    private static final String COMMAND_TOPIC = "$CONTROL/dynamic-security/v1";
    private static final String RESPONSE_TOPIC = "$CONTROL/dynamic-security/v1/response";

    private final MqttProvisioningSettings settings;
    private final MqttTopicSettings topicSettings;
    private final ObjectMapper objectMapper;

    public PahoDynSecCredentialGateway(
            MqttProvisioningSettings settings,
            MqttTopicSettings topicSettings,
            ObjectMapper objectMapper
    ) {
        this.settings = settings;
        this.topicSettings = topicSettings;
        this.objectMapper = objectMapper;
    }

    @Override
    public void provision(String username, String password, String clientId, String roleName) {
        String scopedRoleName = scopedRoleName(roleName, username);
        String topicFilter = topicSettings.getZigbeeUserPrefix() + "/" + username + "/#";
        List<Map<String, Object>> commands = new ArrayList<>();
        commands.add(Map.of("command", "createRole", "rolename", scopedRoleName));
        for (String aclType : List.of(
                "publishClientSend",
                "publishClientReceive",
                "subscribePattern",
                "unsubscribePattern"
        )) {
            commands.add(Map.of(
                    "command", "addRoleACL",
                    "rolename", scopedRoleName,
                    "acltype", aclType,
                    "topic", topicFilter,
                    "allow", true,
                    "priority", 0
            ));
        }
        commands.add(Map.of(
                "command", "createClient",
                "username", username,
                "password", password,
                "clientid", clientId,
                "roles", List.of(Map.of("rolename", scopedRoleName, "priority", -1))
        ));
        try {
            execute(commands);
        } catch (DomainException ex) {
            cleanupPartialProvisioning(username, scopedRoleName);
            throw ex;
        }
    }

    @Override
    public void rotate(String username, String password) {
        execute(List.of(Map.of(
                "command", "setClientPassword",
                "username", username,
                "password", password
        )));
    }

    @Override
    public void revoke(String username, String roleName) {
        execute(List.of(
                Map.of("command", "deleteClient", "username", username),
                Map.of("command", "deleteRole", "rolename", scopedRoleName(roleName, username))
        ));
    }

    private synchronized void execute(List<Map<String, Object>> commands) {
        validateSettings();
        String correlationData = UUID.randomUUID().toString();
        Map<String, Object> request = Map.of(
                "commands", commands,
                "correlationData", correlationData
        );
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(request);
        } catch (Exception ex) {
            throw new DomainException("internal_error", "Не удалось подготовить команду настройки MQTT");
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
                throw new DomainException("bad_gateway", "Mosquitto Dynamic Security не ответил вовремя");
            }
            validateResponse(responseRef.get(), commands.size());
        } catch (DomainException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DomainException("bad_gateway", "Настройка MQTT прервана");
        } catch (Exception ex) {
            throw new DomainException("bad_gateway", "Настройка MQTT недоступна");
        } finally {
            closeQuietly(client);
        }
    }

    private void validateSettings() {
        if (!settings.isEnabled()) {
            throw new DomainException("unavailable", "Настройка MQTT выключена");
        }
        if (settings.getHost() == null || settings.getHost().isBlank()
                || settings.getUsername() == null || settings.getPassword() == null) {
            throw new DomainException("unavailable", "Настройка MQTT не подготовлена");
        }
    }

    private void validateResponse(JsonNode response, int expectedResponses) {
        JsonNode responses = response != null ? response.path("responses") : null;
        if (responses == null || !responses.isArray() || responses.size() != expectedResponses) {
            throw new DomainException("bad_gateway", "Mosquitto Dynamic Security вернул некорректный ответ");
        }
        for (JsonNode item : responses) {
            String error = item.path("error").asText(null);
            if (error != null && !error.isBlank()) {
                throw new DomainException("bad_gateway", "Mosquitto Dynamic Security отклонил операцию");
            }
        }
    }

    private String scopedRoleName(String roleName, String username) {
        if (roleName == null || roleName.isBlank() || username == null || username.isBlank()) {
            throw new DomainException("unavailable", "Настройка MQTT не подготовлена");
        }
        return roleName + "--" + username;
    }

    private void cleanupPartialProvisioning(String username, String scopedRoleName) {
        try {
            execute(List.of(
                    Map.of("command", "deleteClient", "username", username),
                    Map.of("command", "deleteRole", "rolename", scopedRoleName)
            ));
        } catch (DomainException ignored) {
            // Oshibka ochistki ne dolzhna skryvat pervichnuju oshibku provisioning.
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
