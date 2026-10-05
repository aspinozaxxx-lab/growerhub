package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.doReturn;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.config.ShopSettings;
import ru.growerhub.backend.messaging.ShopTelegramWorker;
import ru.growerhub.backend.messaging.TelegramWorker;
import ru.growerhub.backend.notification.contract.TelegramData;
import ru.growerhub.backend.notification.contract.TelegramGateway;
import ru.growerhub.backend.shop.ShopFacade;
import ru.growerhub.backend.shop.contract.ShopData;
import ru.growerhub.backend.shop.jpa.ShopNotificationRepository;
import ru.growerhub.backend.shop.jpa.ShopRequestRepository;
import ru.growerhub.backend.shop.jpa.ShopSubmissionGuardEntity;
import ru.growerhub.backend.shop.jpa.ShopSubmissionGuardRepository;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:shop;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "shop.accepting-requests=true", "shop.rate-secret=shop-test-secret", "shop.telegram-chat-id=123456",
        "telegram.enabled=true", "telegram.bot-token=test-token-never-sent",
        "shop.trusted-proxy-addresses=127.0.0.2"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShopIntegrationTest extends IntegrationTestBase {
    @LocalServerPort int port;
    @Autowired ShopFacade shop;
    @Autowired ShopRequestRepository requests;
    @Autowired ShopNotificationRepository notifications;
    @Autowired ShopSubmissionGuardRepository guard;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @SpyBean ShopSettings settings;
    @MockBean ShopTelegramWorker shopWorker;
    @MockBean TelegramWorker careWorker;
    @MockBean TelegramGateway gateway;
    UserEntity admin;
    UserEntity ordinary;

    @BeforeAll
    void useActualShopMigration() {
        jdbc.execute("drop table shop_notifications");
        jdbc.execute("drop table shop_requests");
        jdbc.execute("drop table shop_submission_guard");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V33__shop_requests.sql"))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
    }

    @BeforeEach
    void setup() {
        notifications.deleteAll(); requests.deleteAll();
        var mutex = new ShopSubmissionGuardEntity(); mutex.id = 1; guard.save(mutex);
        users.deleteAll(); admin = user("admin@shop.test", "admin"); ordinary = user("visitor@shop.test", "user");
    }

    @Test
    void guestCanReadCatalogAndSubmitWithServerPriceAndManualPickup() {
        request(null).get("/api/shop/catalog").then().statusCode(200)
                .body("version", equalTo("2026-10-05.1"), "currency", equalTo("RUB"), "acceptingRequests", equalTo(true))
                .body("offers.find { it.id == 'light-mini' }.verification", equalTo("PILOT"));
        var body = order();
        body.put("items", List.of(Map.of("offerId", "light-white", "quantity", 2, "priceMinor", 1)));
        body.put("totalMinor", 1);
        long devices = jdbc.queryForObject("select count(*) from devices", Long.class);
        long coordinators = jdbc.queryForObject("select count(*) from zigbee_coordinators", Long.class);
        var receipt = request(null).body(body).post("/api/shop/requests").then().statusCode(200)
                .body("totalMinor", equalTo(898000), "currency", equalTo("RUB"), "status", equalTo("NEW"))
                .body("customer", nullValue(), "pickup", nullValue()).extract().asString();
        assertThat(receipt).doesNotContain("Test Buyer", "79990000000", "TST-10");
        assertThat(requests.count()).isEqualTo(1); assertThat(notifications.count()).isEqualTo(1);
        long id = requests.findAll().getFirst().id;
        request(admin).get("/api/admin/shop/requests/" + id).then().statusCode(200)
                .body("customer.name", equalTo("Test Buyer"), "customer.phone", equalTo("+79990000000"))
                .body("pickup.code", equalTo("TST-10"), "pickup.city", equalTo("Москва"))
                .body("items[0].unitPriceMinor", equalTo(449000), "items[0].hubModel", equalTo("POK100"));
        assertThat(jdbc.queryForObject("select count(*) from devices", Long.class)).isEqualTo(devices);
        assertThat(jdbc.queryForObject("select count(*) from zigbee_coordinators", Long.class)).isEqualTo(coordinators);
    }

    @Test
    void concurrentIdenticalRetriesCreateOneRequestAndOneNotification() {
        var body = order();
        var first = CompletableFuture.supplyAsync(() -> request(null).body(body).post("/api/shop/requests").then().statusCode(200).extract().asString());
        var second = CompletableFuture.supplyAsync(() -> request(null).body(body).post("/api/shop/requests").then().statusCode(200).extract().asString());
        assertThat(first.join()).isEqualTo(second.join());
        assertThat(requests.count()).isEqualTo(1); assertThat(notifications.count()).isEqualTo(1);
        var id = requests.findAll().getFirst().id;
        request(admin).body(Map.of("status", "PROCESSING")).patch("/api/admin/shop/requests/" + id).then().statusCode(200);
        assertThat(request(null).body(body).post("/api/shop/requests").asString()).isEqualTo(first.join());
        body.put("comment", "Another order");
        request(null).body(body).post("/api/shop/requests").then().statusCode(409).body("code", equalTo("IDEMPOTENCY_CONFLICT"));
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void rejectsStaleCatalogInvalidItemsAndIncompleteContactWithoutSaving() {
        var stale = order(); stale.put("catalogVersion", "old");
        request(null).body(stale).post("/api/shop/requests").then().statusCode(409).body("code", equalTo("CATALOG_CHANGED"));
        for (var bad : List.of(
                Map.of("items", List.of(Map.of("offerId", "unknown", "quantity", 1))),
                Map.of("items", List.of(Map.of("offerId", "light-mini", "quantity", 0))),
                Map.of("items", List.of(Map.of("offerId", "light-mini", "quantity", 11))),
                Map.of("items", List.of(Map.of("offerId", "light-mini", "quantity", 1), Map.of("offerId", "light-mini", "quantity", 1))),
                Map.of("pickup", Map.of("city", "Москва", "code", "", "address", "Street")),
                Map.of("customer", Map.of("name", "Name", "phone", "12345")),
                Map.of("customer", Map.of("name", "Name\nInjected", "phone", "+79990000000")),
                Map.of("comment", "x".repeat(2001)), Map.of("consent", false), Map.of("website", "spam"))) {
            var input = order(); input.putAll(bad);
            request(null).body(input).post("/api/shop/requests").then().statusCode(422).body("code", equalTo("INVALID_REQUEST"));
        }
        assertThat(requests.count()).isZero(); assertThat(notifications.count()).isZero();
    }

    @Test
    void consultationRequiresOnlyContactAndIsListedWithStatusFilter() {
        var body = order(); body.put("kind", "CONSULTATION"); body.put("items", List.of()); body.remove("pickup");
        request(null).body(body).post("/api/shop/requests").then().statusCode(200).body("totalMinor", equalTo(0));
        long id = requests.findAll().getFirst().id;
        request(admin).body(Map.of("status", "CONFIRMED")).patch("/api/admin/shop/requests/" + id).then().statusCode(200)
                .body("status", equalTo("CONFIRMED"), "kind", equalTo("CONSULTATION"), "pickup", nullValue());
        request(admin).get("/api/admin/shop/requests?status=CONFIRMED&page=0&size=20").then().statusCode(200)
                .body("totalElements", equalTo(1), "requests[0].id", equalTo((int) id));
        request(admin).get("/api/admin/shop/requests?status=NEW").then().body("totalElements", equalTo(0));
        request(admin).get("/api/admin/shop/requests?size=101").then().statusCode(422);
    }

    @Test
    void personalDataAndNotificationControlsRequireAdmin() {
        request(null).body(order()).post("/api/shop/requests").then().statusCode(200);
        long id = requests.findAll().getFirst().id;
        for (String path : List.of("/api/admin/shop/requests", "/api/admin/shop/requests/" + id)) {
            request(null).get(path).then().statusCode(401);
            request(ordinary).get(path).then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        }
        request(ordinary).body(Map.of("status", "CLOSED")).patch("/api/admin/shop/requests/" + id).then().statusCode(403);
        request(ordinary).post("/api/admin/shop/requests/" + id + "/retry-notification").then().statusCode(403);
        request(null).get("/api/shop/requests/" + id).then().statusCode(401);
    }

    @Test
    void rateLimitsArePersistentAndIdempotentRetriesAreExempt() {
        var first = order(); request(null).body(first).post("/api/shop/requests").then().statusCode(200);
        request(null).body(order()).post("/api/shop/requests").then().statusCode(200);
        request(null).body(order()).post("/api/shop/requests").then().statusCode(200);
        request(null).body(order()).post("/api/shop/requests").then().statusCode(429).header("Retry-After", equalTo("3600"));
        request(null).body(first).post("/api/shop/requests").then().statusCode(200);
        for (int i = 1; i <= 2; i++) {
            var body = order(); body.put("customer", Map.of("name", "Test", "phone", "+7999000000" + i));
            request(null).body(body).post("/api/shop/requests").then().statusCode(200);
        }
        var spoof = order(); spoof.put("customer", Map.of("name", "Test", "phone", "+79990000009"));
        request(null).header("X-Real-IP", "203.0.113.100").body(spoof).post("/api/shop/requests").then().statusCode(429);
        assertThat(requests.count()).isEqualTo(5);
    }

    @Test
    void closedShopStillReturnsReceiptForAlreadySavedUuid() {
        var body = order();
        String receipt = request(null).body(body).post("/api/shop/requests").then().statusCode(200).extract().asString();
        doReturn(false).when(settings).acceptingRequests();
        request(null).get("/api/shop/catalog").then().statusCode(200).body("acceptingRequests", equalTo(false));
        request(null).body(order()).post("/api/shop/requests").then().statusCode(503).body("code", equalTo("SHOP_UNAVAILABLE"));
        assertThat(request(null).body(body).post("/api/shop/requests").then().statusCode(200).extract().asString()).isEqualTo(receipt);
        assertThat(requests.count()).isEqualTo(1); assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void missingTelegramConfigurationCannotLoseOrderAndIsVisibleToAdmin() {
        doReturn("").when(settings).telegramChatId();
        request(null).body(order()).post("/api/shop/requests").then().statusCode(200);
        long id = requests.findAll().getFirst().id;
        request(admin).get("/api/admin/shop/requests/" + id).then().statusCode(200)
                .body("notification.status", equalTo("blocked"), "notification.lastError", equalTo("NOT_CONFIGURED"));
        assertThat(shop.claimNotifications()).isEmpty();
        request(admin).post("/api/admin/shop/requests/" + id + "/retry-notification").then().statusCode(503);
        assertThat(requests.count()).isEqualTo(1);
        doReturn("123456").when(settings).telegramChatId();
        request(admin).post("/api/admin/shop/requests/" + id + "/retry-notification").then().statusCode(200)
                .body("notification.status", equalTo("queued"));
        assertThat(shop.claimNotifications()).hasSize(1);
    }

    @Test
    void notificationLeasePreventsDuplicatesAndContainsNoCustomerData() {
        request(null).body(order()).post("/api/shop/requests").then().statusCode(200);
        var delivery = shop.claimNotifications().getFirst();
        assertThat(delivery.chatId()).isEqualTo(123456);
        assertThat(delivery.text()).contains("GH-", "/app/admin/shop/requests/?request=")
                .doesNotContain("Test Buyer", "79990000000", "TST-10", "Москва");
        assertThat(shop.claimNotifications()).isEmpty();
        shop.finishNotification(delivery, new TelegramData.Result("accepted", null));
        shop.finishNotification(delivery, new TelegramData.Result("failed", null));
        assertThat(notifications.findById(delivery.id()).orElseThrow().status).isEqualTo("accepted");
        long id = requests.findAll().getFirst().id;
        request(admin).post("/api/admin/shop/requests/" + id + "/retry-notification").then().statusCode(409);
    }

    @Test
    void timeoutAndExpiredLeaseWaitForExplicitAdminRetry() {
        request(null).body(order()).post("/api/shop/requests").then().statusCode(200);
        var first = shop.claimNotifications().getFirst();
        shop.finishNotification(first, new TelegramData.Result("uncertain", null));
        assertThat(shop.claimNotifications()).isEmpty();
        long id = requests.findAll().getFirst().id;
        request(admin).get("/api/admin/shop/requests/" + id).then().body("notification.status", equalTo("uncertain"));
        request(admin).post("/api/admin/shop/requests/" + id + "/retry-notification").then().statusCode(200)
                .body("notification.status", equalTo("queued"));
        var retry = shop.claimNotifications().getFirst();
        assertThat(retry.leaseKey()).isNotEqualTo(first.leaseKey());
        shop.finishNotification(first, new TelegramData.Result("accepted", null));
        assertThat(notifications.findById(first.id()).orElseThrow().status).isEqualTo("sending");
        jdbc.update("update shop_notifications set leased_until = ? where id = ?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5), retry.id());
        assertThat(shop.claimNotifications()).isEmpty();
        request(admin).get("/api/admin/shop/requests/" + id).then()
                .body("notification.status", equalTo("uncertain"), "notification.lastError", equalTo("LEASE_EXPIRED"));
        assertThat(requests.count()).isEqualTo(1);
    }

    @Test
    void explicit429RespectsDelayAndStopsAfterConfiguredAttempts() {
        request(null).body(order()).post("/api/shop/requests").then().statusCode(200);
        for (int attempt = 1; attempt <= 3; attempt++) {
            var delivery = shop.claimNotifications().getFirst();
            shop.finishNotification(delivery, new TelegramData.Result("retry", 120));
            var state = notifications.findById(delivery.id()).orElseThrow();
            assertThat(state.attempts).isEqualTo(attempt);
            assertThat(state.availableAt).isAfter(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(110));
            assertThat(shop.claimNotifications()).isEmpty();
            if (attempt < 3) {
                assertThat(state.status).isEqualTo("queued");
                jdbc.update("update shop_notifications set available_at = ? where id = ?", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), delivery.id());
            } else assertThat(state.status).isEqualTo("failed");
        }
        assertThat(requests.count()).isEqualTo(1);
    }

    private Map<String, Object> order() {
        var body = new LinkedHashMap<String, Object>();
        body.put("kind", "ORDER"); body.put("idempotencyKey", UUID.randomUUID().toString()); body.put("catalogVersion", "2026-10-05.1");
        body.put("items", List.of(Map.of("offerId", "light-mini", "quantity", 1)));
        body.put("customer", Map.of("name", " Test Buyer ", "phone", "+7 (999) 000-00-00", "telegram", "@qa_test"));
        body.put("pickup", Map.of("city", "Москва", "code", "TST-10", "address", "Test pickup address"));
        body.put("comment", "QA only"); body.put("consent", true); body.put("website", ""); return body;
    }
    private UserEntity user(String email, String role) {
        var now = LocalDateTime.now(ZoneOffset.UTC); return users.save(UserEntity.create(email, "Shop QA", role, true, now, now));
    }
    private RequestSpecification request(UserEntity user) {
        var request = given().baseUri("http://localhost").port(port).contentType("application/json");
        if (user == null) return request;
        String token = Jwts.builder().claim("user_id", user.getId()).setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        return request.header("Authorization", "Bearer " + token);
    }
}
