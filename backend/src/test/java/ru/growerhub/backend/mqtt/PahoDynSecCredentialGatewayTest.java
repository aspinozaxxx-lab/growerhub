package ru.growerhub.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.common.config.mqtt.MqttProvisioningSettings;
import ru.growerhub.backend.common.config.mqtt.MqttTopicSettings;

class PahoDynSecCredentialGatewayTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PahoDynSecCredentialGateway gateway = new PahoDynSecCredentialGateway(
            new MqttProvisioningSettings(),
            new MqttTopicSettings(),
            objectMapper
    );

    @Test
    void acceptsCorrelationDataWhenBrokerReturnsIt() throws Exception {
        List<Map<String, Object>> commands = List.of(Map.of("command", "setClientPassword"));
        JsonNode response = objectMapper.readTree("""
                {"correlationData":"request-1","responses":[{"command":"otherCommand"}]}
                """);

        Assertions.assertTrue(gateway.matchesResponse(response, "request-1", commands));
        Assertions.assertFalse(gateway.matchesResponse(response, "request-2", commands));
    }

    @Test
    void fallsBackToExactCommandSequenceForMosquittoTwoZero() throws Exception {
        List<Map<String, Object>> commands = List.of(
                Map.of("command", "createRole"),
                Map.of("command", "addRoleACL"),
                Map.of("command", "createClient")
        );
        JsonNode response = objectMapper.readTree("""
                {"responses":[
                  {"command":"createRole"},
                  {"command":"addRoleACL"},
                  {"command":"createClient"}
                ]}
                """);

        Assertions.assertTrue(gateway.matchesResponse(response, "not-returned", commands));
    }

    @Test
    void rejectsUnrelatedResponseWithoutCorrelationData() throws Exception {
        List<Map<String, Object>> commands = List.of(Map.of("command", "deleteClient"));
        JsonNode unrelated = objectMapper.readTree("""
                {"responses":[{"command":"listClients"}]}
                """);

        Assertions.assertFalse(gateway.matchesResponse(unrelated, "not-returned", commands));
    }
}
