package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.device.contract.DeviceBrokerCredentialGateway;
import ru.growerhub.backend.pushok.PushokCloudBridge;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeBrokerCredentialGateway;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttMessageType;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttSnapshotMessage;
import ru.growerhub.backend.zigbee.jpa.PushokConnectionRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCoordinatorRepository;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:pushok_cloud;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "zigbee.self-service.enabled=true", "zigbee.self-service.credential-cooldown-seconds=0",
    "zigbee.pushok.enabled=true", "zigbee.pushok.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class PushokConnectionIntegrationTest extends IntegrationTestBase {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PushokConnectionRepository connections;
    @Autowired ZigbeeCoordinatorRepository coordinators;
    @Autowired ZigbeeFacade facade;
    @MockBean PushokCloudBridge bridge;
    @MockBean ZigbeeBrokerCredentialGateway broker;
    @MockBean DeviceBrokerCredentialGateway deviceBroker;
    UserEntity owner;
    UserEntity other;
    String hub;
    @BeforeEach void setup() {
        owner = user(); other = user();
        hub = "pushok-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-1234";
    }
    @Test void createsAnIsolatedCloudCoordinatorWithoutReturningSecretsAndIsIdempotent() {
        String id = create();
        request(owner).body(input()).post("/api/zigbee/pushok").then().statusCode(202).body("id", equalTo(id));
        var response = request(owner).get("/api/zigbee/coordinators/" + id).then().statusCode(200)
                .body("transport", equalTo("PUSHOK_CLOUD")).body("hub_id", equalTo(hub))
                .body("connection_status", equalTo("PAIRING")).extract().asString();
        assertThat(response).doesNotContain("password", "private_key", "encrypted_credentials");
        var stored = connections.findByHubId(hub).orElseThrow();
        assertThat(stored.getEncryptedCredentials()).isNotBlank().doesNotContain("privateKey", "mqttPassword");
        verify(broker, times(1)).provision(anyString(), anyString(), anyString(), anyString());
        request(other).get("/api/zigbee/coordinators/" + id).then().statusCode(404);
        request(other).post("/api/zigbee/pushok/" + id + "/pair").then().statusCode(404);
        request(other).body(input()).post("/api/zigbee/pushok").then().statusCode(409);
    }
    @Test void rejectsLocalAddressesUrlsAndMissingConsentBeforeProvisioning() {
        for (String invalid : new String[] {"192.168.10.22", "https://localhost/x", "pushok-A1B2C3-1234/../x", "pushok-A1B2C3-1234?x", ""})
            request(owner).body(Map.of("name", "Test", "hub_id", invalid, "confirm_access", true)).post("/api/zigbee/pushok").then().statusCode(400);
        request(owner).body(Map.of("name", "Test", "hub_id", hub)).post("/api/zigbee/pushok").then().statusCode(422);
        request(owner).body(Map.of("name", "Test", "hub_id", hub, "confirm_access", false)).post("/api/zigbee/pushok").then().statusCode(422);
        request(null).body(input()).post("/api/zigbee/pushok").then().statusCode(401);
        verifyNoInteractions(broker);
    }
    @Test void retryKeepsTheSameCredentialsAndLateResultsCannotOverwriteNewAttempt() {
        String id = create();
        var initial = connections.findByHubId(hub).orElseThrow();
        String encrypted = initial.getEncryptedCredentials();
        facade.reportPushokPairing(initial.getCoordinatorId(), initial.getAttemptAt(), null, "PAIRING_REQUIRED");
        request(owner).get("/api/zigbee/coordinators/" + id).then().body("connection_error", equalTo("PAIRING_REQUIRED"));
        request(owner).post("/api/zigbee/pushok/" + id + "/pair").then().statusCode(202).body("connection_status", equalTo("PAIRING"));
        var retry = connections.findByHubId(hub).orElseThrow();
        assertThat(retry.getEncryptedCredentials()).isEqualTo(encrypted);
        facade.reportPushokPairing(initial.getCoordinatorId(), initial.getAttemptAt(), "old-key", null);
        assertThat(facade.reportPushokPairing(initial.getCoordinatorId(), initial.getAttemptAt(), null, "CLOUD_UNAVAILABLE")).isFalse();
        assertThat(connections.findByHubId(hub).orElseThrow().getStatus()).isEqualTo("PAIRING");
        facade.reportPushokPairing(retry.getCoordinatorId(), retry.getAttemptAt(), "pinned-key", null);
        request(owner).get("/api/zigbee/coordinators/" + id).then().body("connection_status", equalTo("ACTIVE")).body("connection_error", nullValue());
        verify(broker, times(1)).provision(anyString(), anyString(), anyString(), anyString());
    }
    @Test void temporaryDisconnectKeepsTheBindingAndRestoresOnlyAfterLiveTraffic() {
        String id = create();
        var connection = connections.findByHubId(hub).orElseThrow();
        var coordinator = coordinators.findByPublicIdAndArchivedAtIsNull(UUID.fromString(id)).orElseThrow();
        String encrypted = connection.getEncryptedCredentials();
        LocalDateTime seenAt = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).truncatedTo(ChronoUnit.MICROS);
        var online = new ZigbeeMqttSnapshotMessage(coordinator.getMqttUsername(), coordinator.getBaseTopic(),
                ZigbeeMqttMessageType.BRIDGE_STATE, coordinator.getBaseTopic() + "/bridge/state", "bridge/state", null,
                "{\"state\":\"online\"}", Map.of("state", "online"), seenAt, false);
        facade.reportPushokPairing(connection.getCoordinatorId(), connection.getAttemptAt(), "pinned-key", null);
        facade.handleMqttSnapshot(online);
        request(owner).get("/api/zigbee/coordinators/" + id).then().body("status", equalTo("ONLINE"));

        facade.reportPushokPairing(connection.getCoordinatorId(), connection.getAttemptAt(), null, "CLOUD_UNAVAILABLE");
        request(owner).get("/api/zigbee/coordinators/" + id).then()
                .body("status", equalTo("OFFLINE")).body("connection_status", equalTo("ACTIVE"))
                .body("connection_error", equalTo("CLOUD_UNAVAILABLE"));
        var offline = coordinators.findByPublicIdAndArchivedAtIsNull(UUID.fromString(id)).orElseThrow();
        assertThat(offline.getLastSeenAt()).isEqualTo(seenAt);
        var preserved = connections.findByHubId(hub).orElseThrow();
        assertThat(preserved.getEncryptedCredentials()).isEqualTo(encrypted);
        assertThat(preserved.getHubPublicKey()).isEqualTo("pinned-key");
        assertThat(preserved.getAttemptAt()).isEqualTo(connection.getAttemptAt());
        assertThat(facade.getPushokConnections()).anyMatch(item -> item.publicId().toString().equals(id) && item.status().equals("ACTIVE"));
        assertThat(facade.getOverview(new ru.growerhub.backend.common.contract.AuthenticatedUser(owner.getId(), "user"), UUID.fromString(id))
                .bridge().state()).isEqualTo("offline");

        facade.reportPushokPairing(connection.getCoordinatorId(), connection.getAttemptAt(), "pinned-key", null);
        request(owner).get("/api/zigbee/coordinators/" + id).then()
                .body("status", equalTo("OFFLINE")).body("connection_error", nullValue());
        facade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(online.mqttUsername(), online.baseTopic(), online.type(),
                online.topic(), online.relativeTopic(), null, online.rawPayload(), online.payload(), seenAt.plusSeconds(1), true));
        request(owner).get("/api/zigbee/coordinators/" + id).then().body("status", equalTo("OFFLINE"));
        facade.handleMqttSnapshot(new ZigbeeMqttSnapshotMessage(online.mqttUsername(), online.baseTopic(), online.type(),
                online.topic(), online.relativeTopic(), null, online.rawPayload(), online.payload(), seenAt.plusSeconds(2), false));
        request(owner).get("/api/zigbee/coordinators/" + id).then()
                .body("status", equalTo("ONLINE")).body("connection_status", equalTo("ACTIVE"));
        verify(broker, times(1)).provision(anyString(), anyString(), anyString(), anyString());
        verify(broker, never()).revoke(anyString(), anyString());
    }
    @Test void firstConnectionFailureAndChangedHubIdentityStillRequireExplicitRetry() {
        String id = create();
        var initial = connections.findByHubId(hub).orElseThrow();
        facade.reportPushokPairing(initial.getCoordinatorId(), initial.getAttemptAt(), null, "CLOUD_UNAVAILABLE");
        request(owner).get("/api/zigbee/coordinators/" + id).then()
                .body("status", equalTo("ERROR")).body("connection_status", equalTo("ERROR"));
        assertThat(facade.getPushokConnections()).noneMatch(item -> item.publicId().toString().equals(id));
        request(owner).post("/api/zigbee/pushok/" + id + "/pair").then().statusCode(202);
        var retry = connections.findByHubId(hub).orElseThrow();
        facade.reportPushokPairing(retry.getCoordinatorId(), retry.getAttemptAt(), "pinned-key", null);
        facade.reportPushokPairing(retry.getCoordinatorId(), retry.getAttemptAt(), null, "HUB_IDENTITY_CHANGED");
        request(owner).get("/api/zigbee/coordinators/" + id).then()
                .body("status", equalTo("ERROR")).body("connection_status", equalTo("ERROR"))
                .body("connection_error", equalTo("HUB_IDENTITY_CHANGED"));
        assertThat(facade.getPushokConnections()).noneMatch(item -> item.publicId().toString().equals(id));
        assertThat(connections.findByHubId(hub).orElseThrow().getHubPublicKey()).isEqualTo("pinned-key");
    }
    @Test void unverifiedFailedAttemptCannotReserveSomeoneElsesHubForever() {
        String abandoned = create();
        var stored = connections.findByHubId(hub).orElseThrow();
        facade.reportPushokPairing(stored.getCoordinatorId(), stored.getAttemptAt(), null, "PAIRING_REQUIRED");
        request(other).body(input()).post("/api/zigbee/pushok").then().statusCode(202).body("id", not(equalTo(abandoned)));
        request(owner).get("/api/zigbee/coordinators/" + abandoned).then().statusCode(404);
        verify(broker).revoke(anyString(), anyString());
        var newConnection = connections.findByHubId(hub).orElseThrow();
        facade.reportPushokPairing(newConnection.getCoordinatorId(), newConnection.getAttemptAt(), "verified-hub-key", null);
        facade.reportPushokPairing(newConnection.getCoordinatorId(), newConnection.getAttemptAt(), null, "CLOUD_UNAVAILABLE");
        request(owner).body(input()).post("/api/zigbee/pushok").then().statusCode(409);
    }
    @Test void archiveRevokesBrokerAccessAndDeletesServerCredentials() {
        String id = create();
        var entity = coordinators.findByPublicIdAndArchivedAtIsNull(UUID.fromString(id)).orElseThrow();
        request(owner).post("/api/zigbee/coordinators/" + id + "/credentials/rotate").then().statusCode(400);
        request(other).delete("/api/zigbee/coordinators/" + id).then().statusCode(404);
        request(owner).delete("/api/zigbee/coordinators/" + id).then().statusCode(204);
        verify(broker).revoke(eq(entity.getMqttUsername()), anyString());
        assertThat(connections.findByHubId(hub)).isEmpty();
        assertThat(facade.getPushokConnections()).noneMatch(connection -> connection.publicId().equals(UUID.fromString(id)));
        request(owner).get("/api/zigbee/coordinators/" + id).then().statusCode(404);
        request(other).body(input()).post("/api/zigbee/pushok").then().statusCode(202).body("id", not(equalTo(id)));
    }
    @Test void unsupportedManagementDoesNotPretendToSucceed() {
        String id = create();
        request(owner).body(Map.of("seconds", 60)).post("/api/zigbee/coordinators/" + id + "/permit-join").then().statusCode(400);
        request(owner).body(Map.of("friendly_name", "name")).post("/api/zigbee/coordinators/" + id + "/devices/0x12345678/rename").then().statusCode(400);
    }
    @Test void demoCannotCreateCloudAccessOrControlAnOwnersHub() {
        var demo = users.save(UserEntity.createDemo("Demo PushOk", "UTC", LocalDateTime.now(ZoneOffset.UTC)));
        var principal = new ru.growerhub.backend.common.contract.AuthenticatedUser(demo.getId(), "demo");
        assertThat(facade.isPushokAvailable(principal)).isFalse();
        assertThatThrownBy(() -> facade.createPushokCoordinator(principal, "Demo", hub))
                .isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        request(demo).body(input()).post("/api/zigbee/pushok").then().statusCode(401);
        verifyNoInteractions(broker);
        String id = create();
        assertThatThrownBy(() -> facade.retryPushokPairing(principal, UUID.fromString(id)))
                .isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
    }
    private String create() { return request(owner).body(input()).post("/api/zigbee/pushok").then().statusCode(202).extract().path("id"); }
    private Map<String,Object> input() { return Map.of("name", "ПушОк тест", "hub_id", hub, "confirm_access", true); }
    private UserEntity user() {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        return users.save(UserEntity.create(UUID.randomUUID() + "@example.test", "PushOk QA", "user", true, now, now));
    }
    private RequestSpecification request(UserEntity user) {
        var request = given().baseUri("http://localhost").port(port).contentType("application/json");
        if (user == null) return request;
        String token = Jwts.builder().claim("user_id", user.getId()).setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        return request.header("Authorization", "Bearer " + token);
    }
}
