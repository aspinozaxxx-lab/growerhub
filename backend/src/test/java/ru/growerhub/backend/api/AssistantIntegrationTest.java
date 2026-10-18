package ru.growerhub.backend.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.advisor.AdvisorFacade;
import ru.growerhub.backend.advisor.contract.AssistantData.*;
import ru.growerhub.backend.advisor.contract.AssistantGateway;
import ru.growerhub.backend.advisor.contract.AssistantLimitException;
import ru.growerhub.backend.advisor.jpa.*;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;
import ru.growerhub.backend.llm.AssistantWorker;
import ru.growerhub.backend.user.jpa.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:assistant;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "assistant.enabled=true", "assistant.public-enabled=true", "assistant.hmac-secret=assistant-test-signing-secret-at-least-32-characters",
    "assistant.trusted-proxies=127.0.0.2", "telegram.enabled=true", "telegram.bot-token=test-only"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AssistantIntegrationTest extends IntegrationTestBase {
    @LocalServerPort int port;
    @Autowired AdvisorFacade advisor;
    @Autowired AssistantUsageRepository ledger;
    @Autowired AssistantJobRepository jobs;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired ru.growerhub.backend.plant.PlantFacade plants;
    @Autowired ru.growerhub.backend.journal.JournalFacade journal;
    @Autowired ru.growerhub.backend.automation.AutomationFacade automation;
    @Autowired ru.growerhub.backend.notification.NotificationFacade notifications;
    @Autowired ru.growerhub.backend.notification.jpa.TelegramInboxRepository inbox;
    @MockBean ru.growerhub.backend.messaging.TelegramWorker telegramWorker;
    @MockBean ru.growerhub.backend.messaging.ShopTelegramWorker shopWorker;
    @MockBean ru.growerhub.backend.notification.contract.TelegramGateway telegramGateway;
    @SpyBean AssistantSettings settings;
    @MockBean AssistantWorker worker;
    @MockBean ru.growerhub.backend.messaging.AssistantChannelWorker channelsWorker;
    @MockBean AssistantGateway gateway;
    UserEntity owner, first, other;
    @BeforeAll void migration() {
        for (String table : List.of("assistant_preferences", "assistant_proposals", "assistant_photos", "assistant_jobs", "assistant_conversations", "assistant_usage", "assistant_guard"))
            jdbc.execute("drop table " + table);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V34__farm_assistant.sql"))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
    }
    @BeforeEach void prepare() {
        for (String table : List.of("assistant_preferences", "assistant_proposals", "assistant_photos", "assistant_jobs", "assistant_conversations", "assistant_usage")) jdbc.execute("delete from " + table);
        jdbc.update("update assistant_guard set public_enabled=true, rejected=0");
        users.deleteAll(); owner = user("owner", "admin"); first = user("first", "user"); other = user("other", "user");
        doReturn(owner.getId()).when(settings).ownerUserId();
        when(gateway.models()).thenReturn(List.of(new Model("test-model", "Test", List.of("medium", "high"), "medium", true, true)));
        advisor.assistantModels(principal(owner));
    }
    @Test void guestsNeedDemoAuthenticationAndOwnersDoNotExposeModelSelection() {
        request(null).get("/api/advisor/conversations").then().statusCode(401);
        request(first).get("/api/advisor/models").then().statusCode(403);
        request(first).body(Map.of("model", "test-model")).post("/api/advisor/conversations").then().statusCode(403);
        request(owner).get("/api/advisor/models").then().statusCode(200).body("[0].id", equalTo("test-model"));
        request(owner).body(Map.of("effort", "invented")).post("/api/advisor/conversations").then().statusCode(422);
    }
    @Test void messageRetryCannotDoubleReserveOrCrossConversationAndAccount() {
        UUID chat = create(first), foreign = create(other), key = UUID.randomUUID();
        var body = Map.of("clientKey", key, "text", "Как растения?");
        String id = request(first).body(body).post(path(chat)).then().statusCode(200).extract().path("id");
        request(first).body(body).post(path(chat)).then().statusCode(200).body("id", equalTo(id));
        request(first).body(Map.of("clientKey", key, "text", "Другой текст")).post(path(chat)).then().statusCode(409);
        request(first).body(body).post(path(foreign)).then().statusCode(404);
        request(other).get("/api/advisor/messages/" + id).then().statusCode(404);
        assertThat(ledger.count()).isEqualTo(1); assertThat(ledger.findAll().getFirst().reservedMicros).isEqualTo(15000);
        assertThatThrownBy(() -> advisor.sendAssistantMessage(chat, new Send(UUID.randomUUID(), "Из Telegram", List.of()), principal(first), new Origin(null, null, "telegram")))
                .isInstanceOf(AssistantLimitException.class);
        request(first).delete("/api/advisor/messages/" + id).then().statusCode(200);
        assertThat(ledger.findAll().getFirst().reservedMicros).isZero(); assertThat(ledger.findAll().getFirst().chargedMicros).isZero();
    }
    @Test void concurrentAccountsCannotOversubscribeGlobalHourReservation() {
        var participants = new ArrayList<UserEntity>(); participants.add(first); participants.add(other);
        for (int i = 0; i < 8; i++) participants.add(user("parallel" + i, "user"));
        var chats = participants.stream().map(this::create).toList();
        var futures = new ArrayList<CompletableFuture<Integer>>();
        for (int i = 0; i < participants.size(); i++) {
            var account = participants.get(i); var chat = chats.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> request(account).body(Map.of("clientKey", UUID.randomUUID(), "text", "Отчёт"))
                    .post(path(chat)).statusCode()));
        }
        var statuses = futures.stream().map(CompletableFuture::join).toList();
        assertThat(statuses).containsOnly(200, 429); assertThat(statuses.stream().filter(v -> v == 200).count()).isEqualTo(2);
        assertThat(ledger.findAll().stream().mapToLong(u -> u.reservedMicros).sum()).isEqualTo(30000);
        assertThat(jdbc.queryForObject("select rejected from assistant_guard where id=1", Long.class)).isEqualTo(8);
        verify(gateway, never()).run(any(), any(), any(), any());
    }
    @Test void unknownProviderResultAndRestartKeepFundsAndNeverReplayWork() {
        var chat = create(first); send(first, chat);
        var work = advisor.claimAssistantWork("deepseek"); assertThat(work).isNotNull();
        jdbc.update("update assistant_jobs set leased_until=?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        assertThat(advisor.claimAssistantWork("deepseek")).isNull();
        var row = ledger.findById(work.id()).orElseThrow();
        assertThat(row.chargedMicros).isEqualTo(15000); assertThat(row.reservedMicros).isZero();
        assertThat(jobs.findById(work.id()).orElseThrow().status).isEqualTo("uncertain");
        advisor.finishAssistantWork(work.id(), work.lease(), new Outcome("completed", "Готово", 1000, 1000, true, null));
        assertThat(ledger.findById(work.id()).orElseThrow().chargedMicros).isEqualTo(1500);
        advisor.finishAssistantWork(work.id(), work.lease(), new Outcome("completed", "Повтор", 0, 0, true, null));
        assertThat(ledger.findById(work.id()).orElseThrow().chargedMicros).isEqualTo(1500);
        users.delete(first); assertThat(ledger.existsById(work.id())).isTrue();
    }
    @Test void twoDollarMonthIncludesReservationsAndStopsAtBoundary() {
        assertThat(settings.budget().monthMicros()).isEqualTo(2000000);
        var entry = new AssistantUsageEntity(); entry.id = UUID.randomUUID(); entry.billingKey = "previous-account";
        entry.paid = true; entry.status = "completed"; entry.chargedMicros = 1985000;
        var b = settings.budget();
        doReturn(new AssistantSettings.Budget(b.monthMicros(), 3000000, 3000000, b.turnMicros(), b.userDayMicros(), b.guestsDayMicros(), b.inputNanoUsd(), b.outputNanoUsd())).when(settings).budget();
        entry.createdAt = LocalDateTime.now(ZoneOffset.UTC).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0); entry.finishedAt = entry.createdAt;
        ledger.save(entry);
        UUID chat = create(first), secondChat = create(other);
        send(first, chat);
        assertThat(advisor.assistantUsage(principal(owner)).monthMicros()).isEqualTo(2000000);
        request(other).body(Map.of("clientKey", UUID.randomUUID(), "text", "Отчёт")).post(path(secondChat))
                .then().statusCode(429).header("Retry-After", notNullValue());
        assertThat(ledger.count()).isEqualTo(2);
    }
    @Test void ownerUsesOwnQuotaAndPublicKillSwitchDoesNotSpendOrRouteToCodex() {
        var ownChat = create(owner); send(owner, ownChat);
        var ownWork = advisor.claimAssistantWork("codex"); assertThat(ownWork.provider()).isEqualTo("codex");
        assertThat(ledger.findById(ownWork.id()).orElseThrow().reservedMicros).isZero();
        advisor.switchPublicAssistant(false, principal(owner));
        request(first).body(Map.of("clientKey", UUID.randomUUID(), "text", "Отчёт")).post(path(create(first))).then().statusCode(429).header("Retry-After", notNullValue());
        assertThat(advisor.assistantWorkAlive(ownWork.id(), ownWork.lease())).isTrue();
    }
    @Test void forwardedIpCannotBeForgedAndIpv6IsGroupedBy64() {
        var a = create(first); var b = create(other);
        request(first).header("X-Real-IP", "1.1.1.1").body(Map.of("clientKey", UUID.randomUUID(), "text", "Вопрос")).post(path(a)).then().statusCode(200);
        request(other).header("X-Real-IP", "8.8.8.8").body(Map.of("clientKey", UUID.randomUUID(), "text", "Вопрос")).post(path(b)).then().statusCode(200);
        assertThat(ledger.findAll().stream().map(u -> u.ipKey).distinct().count()).isEqualTo(1);
        assertThat(AssistantOrigin.normalize("2001:db8:12:34::1")).isEqualTo(AssistantOrigin.normalize("2001:db8:12:34:ffff::9"));
        assertThat(AssistantOrigin.normalize("attacker.example")).isEqualTo("unknown");
    }
    @Test void largeInputsAndForeignAttachmentsAreRejectedBeforeGeneration() {
        UUID chat = create(first);
        request(first).body(Map.of("clientKey", UUID.randomUUID(), "text", "x".repeat(6001))).post(path(chat)).then().statusCode(422);
        request(first).body(Map.of("clientKey", UUID.randomUUID(), "text", "Фото", "attachments", List.of(UUID.randomUUID()))).post(path(chat)).then().statusCode(404);
        assertThat(ledger.count()).isZero();
    }
    @Test void careProposalNeedsExactVersionAndIsAppliedOnceWithoutDeviceCommands() throws Exception {
        var user = principal(owner);
        int plant = plants.createPlant(new ru.growerhub.backend.plant.PlantFacade.PlantCreateCommand("Пеларгония", null, null, null, null, "", "Окно", true), user).id();
        var chat = create(owner); send(owner, chat); var work = advisor.claimAssistantWork("codex");
        var arguments = json.valueToTree(Map.of("kind", "care", "payload", Map.of("plantId", plant, "action", "inspection", "text", "Проверены листья", "eventAt", Instant.now().toString())));
        var call = new ToolCall("care-1", "gh_prepare_change", arguments);
        assertThat(advisor.beginAssistantTool(work.id(), work.lease(), call).execute()).isTrue();
        var result = advisor.executeAssistantTool(work.id(), work.lease(), call);
        advisor.finishAssistantTool(work.id(), work.lease(), call.id(), result);
        assertThat(advisor.beginAssistantTool(work.id(), work.lease(), call).execute()).isFalse();
        UUID proposal = UUID.fromString(result.path("id").asText()); String revision = result.path("revision").asText();
        assertThat(journal.searchCare(user, plant, null, null, null, null, 0).items()).isEmpty();
        assertThatThrownBy(() -> advisor.confirmAssistantProposal(proposal, new Confirm("wrong"), user, false)).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        assertThatThrownBy(() -> advisor.confirmAssistantProposal(proposal, new Confirm(revision), principal(other), false)).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        advisor.confirmAssistantProposal(proposal, new Confirm(revision), user, false);
        advisor.confirmAssistantProposal(proposal, new Confirm(revision), user, false);
        assertThat(advisor.claimAssistantApplication().id()).isEqualTo(proposal);
        advisor.applyAssistantProposal(proposal); advisor.applyAssistantProposal(proposal);
        assertThat(advisor.claimAssistantApplication()).isNull();
        assertThat(journal.searchCare(user, plant, null, null, null, null, 0).items()).hasSize(1);
    }
    @Test void photosAreSanitizedPrivateAndDoNotReservePaidGenerationOnUpload() throws Exception {
        var chat = create(first); var image = new java.awt.image.BufferedImage(12, 18, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "png", bytes);
        var saved = advisor.uploadAssistantPhoto(chat, bytes.toByteArray(), principal(first), new Origin(null, null, "app"));
        assertThat(saved.mime()).isEqualTo("image/jpeg");
        assertThat(advisor.assistantImage(saved.id(), principal(first)).bytes()).startsWith((byte) 0xff, (byte) 0xd8);
        assertThatThrownBy(() -> advisor.assistantImage(saved.id(), principal(other))).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        assertThatThrownBy(() -> advisor.uploadAssistantPhoto(chat, new byte[]{1, 2, 3}, principal(first), new Origin(null, null, "app"))).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        assertThat(ledger.count()).isZero();
    }
    @Test void telegramIsOptInSharesQuotaAndDeduplicatesUpdatesAndReports() {
        var user = principal(first); long update = 1000000L + user.id() * 100L, address = update + 50;
        var link = notifications.link(user); String token = link.url().split("start=")[1];
        notifications.receive(new ru.growerhub.backend.notification.contract.TelegramData.Update(update, address, true, "Test", "/start " + token, null, null));
        notifications.confirm(user, notifications.status(user).confirmation());
        notifications.preferences(user, new ru.growerhub.backend.notification.contract.TelegramData.Preferences(true, 9, 0, 0));
        notifications.receive(new ru.growerhub.backend.notification.contract.TelegramData.Update(update + 1, address, true, "Test", "До включения", null, null));
        assertThat(inbox.existsById(update + 1)).isFalse();
        var chat = create(first);
        advisor.saveAssistantPreferences(new Preferences(true, "daily", 9, 1, chat.toString()), user);
        var event = new ru.growerhub.backend.notification.contract.TelegramData.Update(update + 2, address, true, "Test", "Покажи состояние", null, null);
        notifications.receive(event); notifications.receive(event); advisor.processAssistantInbox();
        assertThat(jobs.findAll()).hasSize(1); assertThat(jobs.findAll().getFirst().conversationId).isEqualTo(chat);
        assertThatThrownBy(() -> advisor.sendAssistantMessage(chat, new Send(UUID.randomUUID(), "Ещё", List.of()), user, new Origin(null, null, "app"))).isInstanceOf(AssistantLimitException.class);
        advisor.switchPublicAssistant(false, principal(owner));
        var due = new ReportDue(user.id(), "test-day"); advisor.prepareAssistantReport(due); advisor.prepareAssistantReport(due);
        assertThat(jobs.findAll().stream().filter(j -> "summary".equals(j.provider))).hasSize(1);
        notifications.disconnect(user);
        notifications.receive(new ru.growerhub.backend.notification.contract.TelegramData.Update(update + 3, address, true, "Test", "После отключения", null, null));
        assertThat(inbox.existsById(update + 3)).isFalse();
    }
    @Test void telegramCreatesNotificationOnlyRuleInSharedHistoryAfterConfirmationOnce() {
        var user = principal(owner);
        int farm = automation.createUserFarm(user, new ru.growerhub.backend.automation.contract.AutomationData.SaveRoomRequest("Проверка Telegram", true)).farms().getFirst().id();
        long update = 2000000L + user.id() * 100L, address = update + 50;
        var link = notifications.link(user);
        notifications.receive(new ru.growerhub.backend.notification.contract.TelegramData.Update(update, address, true, "Test", "/start " + link.url().split("start=")[1], null, null));
        notifications.confirm(user, notifications.status(user).confirmation());
        var chat = create(owner);
        advisor.saveAssistantPreferences(new Preferences(true, "off", 9, 1, chat.toString()), user);
        notifications.receive(new ru.growerhub.backend.notification.contract.TelegramData.Update(update + 1, address, true, "Test", "Создай выключенное напоминание об осмотре", null, null));
        advisor.processAssistantInbox();
        var work = advisor.claimAssistantWork("codex");
        assertThat(work.conversationId()).isEqualTo(chat);
        var arguments = json.valueToTree(Map.of("kind", "rule", "payload", Map.of("enabled", false, "definition", Map.of(
                "version", 1, "name", "Осмотр растений", "scopeType", "ROOM", "scopeId", farm,
                "trigger", Map.of("type", "schedule", "at", "09:20"),
                "actions", List.of(Map.of("type", "notify", "text", "Осмотреть листья")), "cooldownSeconds", 60))));
        var call = new ToolCall("telegram-rule", "gh_prepare_change", arguments);
        advisor.beginAssistantTool(work.id(), work.lease(), call);
        var proposal = advisor.executeAssistantTool(work.id(), work.lease(), call);
        advisor.finishAssistantTool(work.id(), work.lease(), call.id(), proposal);
        advisor.finishAssistantWork(work.id(), work.lease(), new Outcome("completed", "Подтвердите карточку", 1000, 100, true, null));
        assertThat(automation.listRules(user)).isEmpty();
        UUID proposalId = UUID.fromString(proposal.path("id").asText());
        var callback = new ru.growerhub.backend.notification.contract.TelegramData.Update(update + 2, address, true, "Test", null, null,
                "gha:" + proposalId.toString().replace("-", "") + ":" + proposal.path("revision").asText());
        notifications.receive(callback); notifications.receive(callback); advisor.processAssistantInbox();
        assertThat(advisor.claimAssistantApplication().id()).isEqualTo(proposalId);
        advisor.applyAssistantProposal(proposalId); advisor.applyAssistantProposal(proposalId);
        assertThat(automation.listRules(user)).singleElement().satisfies(rule -> {
            assertThat(rule.enabled()).isFalse(); assertThat(rule.revision()).isEqualTo(1);
            assertThat(rule.definition().actions()).singleElement().satisfies(action -> assertThat(action.path("type").asText()).isEqualTo("notify"));
            assertThat(automation.ruleHistory(rule.id(), user)).isEmpty();
        });
        assertThat(advisor.assistantMessages(chat, user)).singleElement().satisfies(message ->
                assertThat(message.proposals()).singleElement().satisfies(item -> assertThat(item.status()).isEqualTo("applied")));
        assertThat(advisor.claimAssistantApplication()).isNull();
    }
    private UUID create(UserEntity user) { return UUID.fromString(request(user).body(Map.of()).post("/api/advisor/conversations").then().statusCode(200).extract().path("id")); }
    private void send(UserEntity user, UUID chat) { request(user).body(Map.of("clientKey", UUID.randomUUID(), "text", "Покажи состояние")).post(path(chat)).then().statusCode(200); }
    private String path(UUID id) { return "/api/advisor/conversations/" + id + "/messages"; }
    private AuthenticatedUser principal(UserEntity user) { return new AuthenticatedUser(user.getId(), user.getRole()); }
    private UserEntity user(String name, String role) { var now = LocalDateTime.now(ZoneOffset.UTC); return users.save(UserEntity.create(name + "@assistant.test", name, role, true, now, now)); }
    private RequestSpecification request(UserEntity user) {
        var request = given().baseUri("http://localhost").port(port).contentType("application/json");
        if (user == null) return request;
        String token = Jwts.builder().claim("user_id", user.getId()).setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        return request.header("Authorization", "Bearer " + token);
    }
}
