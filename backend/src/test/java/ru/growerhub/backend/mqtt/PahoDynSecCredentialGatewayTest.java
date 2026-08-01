package ru.growerhub.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    @Test
    void nativeDeviceCommandsUseOnlyOwnNamespaceAndFixedClientId() {
        List<Map<String, Object>> commands = gateway.buildProvisionCommands(
                "GROVIKA_040AB1",
                "secret",
                "GROVIKA_040AB1",
                "native-device--GROVIKA_040AB1",
                "gh/dev/GROVIKA_040AB1/#"
        );

        List<Map<String, Object>> aclCommands = commands.stream()
                .filter(command -> "addRoleACL".equals(command.get("command")))
                .toList();
        Assertions.assertEquals(4, aclCommands.size());
        Assertions.assertEquals(
                Set.of(
                        "publishClientSend",
                        "publishClientReceive",
                        "subscribePattern",
                        "unsubscribePattern"
                ),
                aclCommands.stream().map(command -> command.get("acltype").toString()).collect(Collectors.toSet())
        );
        Assertions.assertTrue(aclCommands.stream().allMatch(command ->
                "gh/dev/GROVIKA_040AB1/#".equals(command.get("topic"))
        ));

        Map<String, Object> createClient = commands.get(commands.size() - 1);
        Assertions.assertEquals("GROVIKA_040AB1", createClient.get("username"));
        Assertions.assertEquals("GROVIKA_040AB1", createClient.get("clientid"));
        Assertions.assertFalse(commands.toString().contains("GROVIKA_OTHER"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeDeviceRotationRepairsWildcardSubscriptionAclBeforePasswordChange() {
        List<Map<String, Object>> commands = gateway.buildDeviceRotationCommands(
                "GROVIKA_040AB1",
                "secret",
                "native-device",
                "gh/dev/GROVIKA_040AB1/#"
        );

        Assertions.assertEquals(2, commands.size());
        Map<String, Object> modifyRole = commands.get(0);
        Assertions.assertEquals("modifyRole", modifyRole.get("command"));
        Assertions.assertEquals("native-device--GROVIKA_040AB1", modifyRole.get("rolename"));
        List<Map<String, Object>> acls = (List<Map<String, Object>>) modifyRole.get("acls");
        Assertions.assertEquals(
                Set.of(
                        "publishClientSend",
                        "publishClientReceive",
                        "subscribePattern",
                        "unsubscribePattern"
                ),
                acls.stream().map(acl -> acl.get("acltype").toString()).collect(Collectors.toSet())
        );
        Assertions.assertTrue(acls.stream().allMatch(acl ->
                "gh/dev/GROVIKA_040AB1/#".equals(acl.get("topic"))
        ));
        Assertions.assertEquals("setClientPassword", commands.get(1).get("command"));
        Assertions.assertEquals("GROVIKA_040AB1", commands.get(1).get("username"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeDeviceAccessReconcileDoesNotChangePassword() {
        List<Map<String, Object>> commands = gateway.buildDeviceAccessCommands(
                "GROVIKA_040AB1",
                "native-device",
                "gh/dev/GROVIKA_040AB1/#"
        );

        Assertions.assertEquals(1, commands.size());
        Map<String, Object> modifyRole = commands.get(0);
        Assertions.assertEquals("modifyRole", modifyRole.get("command"));
        Assertions.assertEquals("native-device--GROVIKA_040AB1", modifyRole.get("rolename"));
        List<Map<String, Object>> acls = (List<Map<String, Object>>) modifyRole.get("acls");
        Assertions.assertEquals(
                Set.of(
                        "publishClientSend",
                        "publishClientReceive",
                        "subscribePattern",
                        "unsubscribePattern"
                ),
                acls.stream().map(acl -> acl.get("acltype").toString()).collect(Collectors.toSet())
        );
        Assertions.assertFalse(commands.toString().contains("password"));
    }
}
