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
    @Autowired AssistantRequestRepository featureRequests;
    @Autowired ru.growerhub.backend.device.jpa.DeviceRepository devices;
    @Autowired ru.growerhub.backend.sensor.jpa.SensorRepository sensors;
    @Autowired ru.growerhub.backend.sensor.jpa.SensorReadingRepository readings;
    @Autowired ru.growerhub.backend.automation.jpa.AutomationResourceBindingRepository bindings;
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
        for (String table : List.of("assistant_feature_requests", "assistant_preferences", "assistant_proposals", "assistant_photos", "assistant_jobs", "assistant_conversations", "assistant_usage", "assistant_guard"))
            jdbc.execute("drop table " + table);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V34__farm_assistant.sql"), new ClassPathResource("db/migration/V37__assistant_codex_status.sql"), new ClassPathResource("db/migration/V38__codex_maintenance.sql"), new ClassPathResource("db/migration/V39__assistant_feature_requests.sql"))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
    }
    @BeforeEach void prepare() {
        for (String table : List.of("assistant_feature_requests", "assistant_preferences", "assistant_proposals", "assistant_photos", "assistant_jobs", "assistant_conversations", "assistant_usage")) jdbc.execute("delete from " + table);
        jdbc.update("update assistant_guard set public_enabled=true, rejected=0, codex_status_json=null, codex_notified_version=null, codex_update_json=null, codex_update_notified_id=null");
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
        var stranger = principal(other); var foreignLink = notifications.link(stranger);
        notifications.receive(new ru.growerhub.backend.notification.contract.TelegramData.Update(update + 10, address + 1, true, "Other", "/start " + foreignLink.url().split("start=")[1], null, null));
        notifications.confirm(stranger, notifications.status(stranger).confirmation());
        advisor.saveAssistantPreferences(new Preferences(true, "off", 9, 1, create(other).toString()), stranger);
        notifications.receive(new ru.growerhub.backend.notification.contract.TelegramData.Update(update + 11, address + 1, true, "Other", null, null,
                "gha:" + proposalId.toString().replace("-", "") + ":" + proposal.path("revision").asText()));
        advisor.processAssistantInbox();
        assertThat(advisor.claimAssistantApplication()).isNull();
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

    @Test void interfaceHelpFindsOnlyOwnedRulesAndNeverCreatesAProposal() {
        var user = principal(first); var stranger = principal(other);
        int ownFarm = automation.createUserFarm(user, new ru.growerhub.backend.automation.contract.AutomationData.SaveRoomRequest("Моя ферма", true)).farms().getFirst().id();
        int foreignFarm = automation.createUserFarm(stranger, new ru.growerhub.backend.automation.contract.AutomationData.SaveRoomRequest("Чужая ферма", true)).farms().getFirst().id();
        var ownDefinition = json.convertValue(Map.of("version", 2, "name", "Утренний осмотр", "scopeType", "ROOM", "scopeId", ownFarm,
                "trigger", Map.of("type", "schedule", "at", "09:00"), "actions", List.of(Map.of("type", "notify", "text", "Осмотр")), "cooldownSeconds", 60), ru.growerhub.backend.automation.contract.RuleData.Definition.class);
        var foreignDefinition = new ru.growerhub.backend.automation.contract.RuleData.Definition(2, "Чужой осмотр", "ROOM", foreignFarm,
                ownDefinition.trigger(), null, ownDefinition.actions(), 60);
        var mine = automation.saveRule(null, new ru.growerhub.backend.automation.contract.RuleData.Save(null, false, ownDefinition), user);
        var theirs = automation.saveRule(null, new ru.growerhub.backend.automation.contract.RuleData.Save(null, false, foreignDefinition), stranger);
        var chat = create(first); send(first, chat); var work = advisor.claimAssistantWork("deepseek");
        assertThat(work.tools().stream().map(t -> t.get("name"))).contains("gh_app_help");
        var help = new ToolCall("help", "gh_app_help", json.valueToTree(Map.of("section", "scenarios")));
        advisor.beginAssistantTool(work.id(), work.lease(), help);
        var result = advisor.executeAssistantTool(work.id(), work.lease(), help);
        advisor.finishAssistantTool(work.id(), work.lease(), help.id(), result);
        assertThat(result.path("sections").get(0).path("path").asText()).isEqualTo("/app/automations/");
        assertThat(result.path("rules")).hasSize(1);
        assertThat(result.path("rules").get(0).path("url").asText()).endsWith("/app/automations/?rule=" + mine.id());
        assertThat(result.toString()).contains("Моя ферма", "Свои сценарии").doesNotContain("Чужая ферма", "Чужой осмотр");
        var foreign = new ToolCall("foreign-help", "gh_app_help", json.valueToTree(Map.of("ruleId", theirs.id().toString())));
        advisor.beginAssistantTool(work.id(), work.lease(), foreign);
        assertThatThrownBy(() -> advisor.executeAssistantTool(work.id(), work.lease(), foreign)).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        assertThat(jdbc.queryForObject("select count(*) from assistant_proposals", Integer.class)).isZero();
        assertThat(automation.getRule(mine.id(), user).revision()).isEqualTo(1);
        assertThat(automation.getRule(mine.id(), user).enabled()).isFalse();
    }

    @Test void foreignFarmPlantAndSystemCommandsAreRejectedRegardlessOfPromptOrArguments() {
        var foreign = principal(other);
        int plant = plants.createPlant(new ru.growerhub.backend.plant.PlantFacade.PlantCreateCommand("Private plant", null, null, null, null, "", "", true), foreign).id();
        int farm = automation.createUserFarm(foreign, new ru.growerhub.backend.automation.contract.AutomationData.SaveRoomRequest("Private farm", true)).farms().getFirst().id();
        var chat = create(first); send(first, chat); var work = advisor.claimAssistantWork("deepseek");
        assertThat(work.tools().stream().map(t -> t.get("name").toString())).noneMatch(n -> n.startsWith("gh_admin"));
        var attempts = List.of(
                new ToolCall("foreign-plant", "gh_plant_details", json.valueToTree(Map.of("plantId", plant, "userId", other.getId()))),
                new ToolCall("foreign-care", "gh_prepare_change", json.valueToTree(Map.of("kind", "care", "payload", Map.of("plantId", plant, "action", "inspection", "eventAt", Instant.now().toString())))),
                new ToolCall("foreign-scenario", "gh_rule_details", json.valueToTree(Map.of("scopeType", "ROOM", "scopeId", farm))),
                new ToolCall("fake-admin", "gh_admin_user_report", json.valueToTree(Map.of("userId", other.getId()))),
                new ToolCall("shell", "exec_command", json.valueToTree(Map.of("cmd", "read secrets"))),
                new ToolCall("confirm", "gh_confirm_change", json.valueToTree(Map.of("id", UUID.randomUUID()))));
        for (var call : attempts) {
            advisor.beginAssistantTool(work.id(), work.lease(), call);
            assertThatThrownBy(() -> advisor.executeAssistantTool(work.id(), work.lease(), call)).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        }
        assertThat(journal.searchCare(foreign, plant, null, null, null, null, 0).items()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from assistant_proposals", Integer.class)).isZero();
        request(first).get("/api/advisor/runtime").then().statusCode(403);
    }

    @Test void adminReadRequiresConfiguredOwnerAndCurrentRoleAndNeverAuthorizesForeignChanges() {
        var foreign = principal(other);
        int farm = automation.createUserFarm(foreign, new ru.growerhub.backend.automation.contract.AutomationData.SaveRoomRequest("Private farm", true)).farms().getFirst().id();
        int plant = plants.createPlant(new ru.growerhub.backend.plant.PlantFacade.PlantCreateCommand("Private plant", null, null, null, null, "", "", true), foreign).id();
        var chat = create(owner); send(owner, chat); var work = advisor.claimAssistantWork("codex");
        assertThat(work.tools().stream().map(t -> t.get("name").toString())).contains("gh_admin_user_report", "gh_admin_users", "gh_admin_system");
        var read = new ToolCall("admin-read", "gh_admin_user_report", json.valueToTree(Map.of("userId", other.getId())));
        advisor.beginAssistantTool(work.id(), work.lease(), read);
        var data = advisor.executeAssistantTool(work.id(), work.lease(), read);
        assertThat(data.path("profile").path("id").asInt()).isEqualTo(other.getId());
        assertThat(data.path("farm").path("plants").get(0).path("id").asInt()).isEqualTo(plant);
        var change = new ToolCall("foreign-change", "gh_prepare_change", json.valueToTree(Map.of("kind", "care", "payload", Map.of("plantId", plant, "action", "inspection", "eventAt", Instant.now().toString()))));
        advisor.beginAssistantTool(work.id(), work.lease(), change);
        assertThatThrownBy(() -> advisor.executeAssistantTool(work.id(), work.lease(), change)).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        var definition = Map.of("version", 1, "name", "Foreign rule", "scopeType", "ROOM", "scopeId", farm,
                "trigger", Map.of("type", "schedule", "at", "09:20"), "actions", List.of(Map.of("type", "notify", "text", "No access")), "cooldownSeconds", 60);
        var rule = new ToolCall("foreign-rule", "gh_prepare_change", json.valueToTree(Map.of("kind", "rule", "payload", Map.of("enabled", false, "definition", definition))));
        advisor.beginAssistantTool(work.id(), work.lease(), rule);
        assertThatThrownBy(() -> advisor.executeAssistantTool(work.id(), work.lease(), rule)).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        assertThat(automation.listRules(foreign)).isEmpty();
        jdbc.update("update users set role='user' where id=?", owner.getId());
        var revoked = new ToolCall("revoked", "gh_admin_users", json.createObjectNode());
        advisor.beginAssistantTool(work.id(), work.lease(), revoked);
        assertThatThrownBy(() -> advisor.executeAssistantTool(work.id(), work.lease(), revoked)).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        var anotherAdmin = user("another-admin", "admin");
        request(anotherAdmin).get("/api/advisor/models").then().statusCode(403);
        request(anotherAdmin).get("/api/advisor/runtime").then().statusCode(403);
    }

    @Test void codexUpdateNotificationsAreOwnerOnlyAndDeduplicatedPerRelease() {
        var user = principal(owner); long update = 3000000L + user.id() * 100L, address = update + 50;
        var link = notifications.link(user);
        notifications.receive(new ru.growerhub.backend.notification.contract.TelegramData.Update(update, address, true, "Test", "/start " + link.url().split("start=")[1], null, null));
        notifications.confirm(user, notifications.status(user).confirmation());
        advisor.saveAssistantPreferences(new Preferences(true, "off", 9, 1, create(owner).toString()), user);
        var state = new RuntimeStatus("0.163.0", "0.164.0", Instant.now(), true, null);
        advisor.recordAssistantRuntimeStatus(state); advisor.recordAssistantRuntimeStatus(state);
        request(owner).get("/api/advisor/runtime").then().statusCode(200).body("latest", equalTo("0.164.0"));
        assertThat(jdbc.queryForObject("select count(*) from telegram_deliveries where dedupe_key like '%:codex-release:0.164.0'", Integer.class)).isEqualTo(1);
        advisor.recordAssistantRuntimeStatus(new RuntimeStatus("0.163.0", null, Instant.now(), false, "check_unavailable"));
        assertThat(advisor.assistantRuntimeStatus(user).error()).isEqualTo("check_unavailable");
        assertThat(jdbc.queryForObject("select count(*) from telegram_deliveries where dedupe_key like '%:codex-release:%'", Integer.class)).isEqualTo(1);
        jdbc.update("delete from telegram_deliveries where dedupe_key like '%:codex-release:%'");
        advisor.recordAssistantRuntimeStatus(state);
        assertThat(jdbc.queryForObject("select count(*) from telegram_deliveries where dedupe_key like '%:codex-release:%'", Integer.class)).isZero();
    }

    @Test void currentModelCatalogAndEffortAreSavedAndUsedByTelegram() {
        when(gateway.models()).thenReturn(List.of(new Model("gpt-6.1-sol", "GPT-6.1-Sol", List.of("low", "medium", "max", "ultra"), "low", true, true)));
        advisor.assistantModels(principal(owner)); var chat = create(owner);
        request(owner).body(Map.of("model", "gpt-6.1-sol", "effort", "max")).put("/api/advisor/conversations/" + chat).then().statusCode(200);
        advisor.sendAssistantMessage(chat, new Send(UUID.randomUUID(), "Отчёт", List.of()), principal(owner), new Origin(null, null, "telegram"));
        var work = advisor.claimAssistantWork("codex");
        assertThat(work.model()).isEqualTo("gpt-6.1-sol"); assertThat(work.effort()).isEqualTo("max");
        request(first).body(Map.of("model", "gpt-6.1-sol", "effort", "max")).put("/api/advisor/conversations/" + chat).then().statusCode(404);
    }
    @Test void codexMaintenanceRequiresCurrentOwnerAdminAndRunsOutsideTransaction() {
        doReturn(new AssistantSettings.Maintenance("http://127.0.0.1:18789", "test-token-with-at-least-32-characters", 20)).when(settings).maintenance();
        var command = new RuntimeUpdateRequest(UUID.randomUUID(), "0.164.0", "0.163.0");
        var update = new RuntimeUpdate(command.id(), command.target(), command.expectedInstalled(), "0.163.0", "queued", Instant.now(), Instant.now(), null, null);
        when(gateway.runtimeUpdate(command)).thenAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return update;
        });
        var admin = user("second-admin", "admin");
        for (var denied : List.of(first, other, admin)) {
            request(denied).body(command).post("/api/advisor/runtime/update").then().statusCode(403);
            request(denied).get("/api/advisor/runtime/update").then().statusCode(403);
            request(denied).post("/api/advisor/runtime/check").then().statusCode(403);
        }
        verify(gateway, never()).runtimeUpdate(any());
        assertThatThrownBy(() -> advisor.startAssistantRuntimeUpdate(command, new AuthenticatedUser(owner.getId(), "demo")))
                .isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        request(owner).body(command).post("/api/advisor/runtime/update").then().statusCode(200).body("phase", equalTo("queued"));
        request(owner).get("/api/advisor/runtime/update").then().statusCode(200).body("operation.id", equalTo(command.id().toString()));
        request(owner).body(new RuntimeUpdateRequest(UUID.randomUUID(), ";touch /tmp/x", "0.163.0")).post("/api/advisor/runtime/update").then().statusCode(422);
        jdbc.update("update users set role='user' where id=?", owner.getId());
        request(owner).body(command).post("/api/advisor/runtime/update").then().statusCode(403);
        verify(gateway, times(1)).runtimeUpdate(any());
    }

    @Test void codexMaintenancePausesOnlyOwnerAndKeepsTheLatestOperationResult() {
        var chat = create(owner); send(owner, chat);
        Instant at = Instant.now(); UUID id = UUID.randomUUID();
        var running = new RuntimeUpdate(id, "0.164.0", "0.163.0", "0.163.0", "verifying", at, at, null, null);
        advisor.recordAssistantRuntimeUpdate(running);
        assertThat(advisor.claimAssistantWork("codex")).isNull();
        request(owner).body(Map.of("clientKey", UUID.randomUUID(), "text", "Новый вопрос")).post(path(chat)).then().statusCode(429);
        var ordinary = create(first); send(first, ordinary);
        assertThat(advisor.claimAssistantWork("deepseek")).isNotNull();
        var restored = new RuntimeUpdate(id, "0.164.0", "0.163.0", "0.163.0", "rolled_back", at, at.plusSeconds(2), at.plusSeconds(2), "health_failed");
        assertThat(advisor.recordAssistantRuntimeUpdate(restored)).isTrue();
        assertThat(advisor.recordAssistantRuntimeUpdate(running)).isFalse();
        assertThat(advisor.assistantRuntimeMaintenance(principal(owner)).operation().phase()).isEqualTo("rolled_back");
        assertThat(advisor.claimAssistantWork("codex")).isNotNull();
    }

    @Test void featureRequestIsConfirmedOncePrivateAndKeepsOwnerNotificationPending() {
        var chat = create(first); send(first, chat); var work = advisor.claimAssistantWork("deepseek");
        var payload = Map.of("title", "Расход удобрения", "description", "Хочу видеть расход удобрения по растениям за месяц", "benefit", "Легче планировать покупки");
        var call = new ToolCall("request-1", "gh_prepare_change", json.valueToTree(Map.of("kind", "feature_request", "payload", payload)));
        advisor.beginAssistantTool(work.id(), work.lease(), call);
        var card = advisor.executeAssistantTool(work.id(), work.lease(), call);
        advisor.finishAssistantTool(work.id(), work.lease(), call.id(), card);
        assertThat(featureRequests.count()).isZero();
        UUID id = UUID.fromString(card.path("id").asText()); String revision = card.path("revision").asText();
        request(other).body(new Confirm(revision)).post("/api/advisor/proposals/" + id + "/confirm").then().statusCode(404);
        request(first).body(new Confirm("wrong")).post("/api/advisor/proposals/" + id + "/confirm").then().statusCode(404);
        request(first).body(new Confirm(revision)).post("/api/advisor/proposals/" + id + "/confirm").then().statusCode(200);
        assertThat(advisor.claimAssistantApplication().id()).isEqualTo(id);
        advisor.applyAssistantProposal(id); advisor.applyAssistantProposal(id);
        assertThat(featureRequests.count()).isEqualTo(1);
        advisor.deliverAssistantResponses();
        assertThat(featureRequests.findById(id).orElseThrow().notificationQueued).isFalse();
        request(other).get("/api/advisor/requests").then().statusCode(200).body("size()", equalTo(0));
        request(other).get("/api/advisor/requests/admin").then().statusCode(403);
        request(first).body(new FeatureDecision("done", "", 1)).put("/api/advisor/requests/admin/" + id).then().statusCode(403);
        request(owner).get("/api/advisor/requests/admin").then().statusCode(200).body("[0].description", equalTo(payload.get("description")));
        request(owner).body(new FeatureDecision("planned", "Добавим месячный отчёт", 1)).put("/api/advisor/requests/admin/" + id).then().statusCode(200);
        request(owner).body(new FeatureDecision("done", "", 1)).put("/api/advisor/requests/admin/" + id).then().statusCode(409);
        request(first).get("/api/advisor/requests").then().statusCode(200).body("[0].status", equalTo("planned")).body("[0].note", equalTo("Добавим месячный отчёт"));
        var user = principal(owner); long update = 4000000L + user.id() * 100L;
        var link = notifications.link(user);
        notifications.receive(new ru.growerhub.backend.notification.contract.TelegramData.Update(update, update + 50, true, "Owner", "/start " + link.url().split("start=")[1], null, null));
        notifications.confirm(user, notifications.status(user).confirmation());
        advisor.deliverAssistantResponses(); advisor.deliverAssistantResponses();
        assertThat(featureRequests.findById(id).orElseThrow().notificationQueued).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from telegram_deliveries where dedupe_key like '%:assistant-request:" + id + "'", Integer.class)).isEqualTo(1);
        jdbc.update("update users set role='user' where id=?", owner.getId());
        request(owner).get("/api/advisor/requests/admin").then().statusCode(403);
    }

    @Test void sensorHistoryUsesSavedReadingsAndCannotCrossFarmOwnershipEvenForAdmin() {
        var user = principal(first); var now = LocalDateTime.now(ZoneOffset.UTC);
        int farm = automation.createUserFarm(user, new ru.growerhub.backend.automation.contract.AutomationData.SaveRoomRequest("История", true)).farms().getFirst().id();
        var device = ru.growerhub.backend.device.jpa.DeviceEntity.create(); device.setUserId(first.getId()); device.setDeviceId("assistant-history-" + first.getId()); device.setName("Датчик"); devices.save(device);
        var sensor = ru.growerhub.backend.sensor.jpa.SensorEntity.create(); sensor.setDeviceId(device.getId());
        sensor.setType(ru.growerhub.backend.sensor.contract.SensorType.AIR_TEMPERATURE); sensor.setChannel(0); sensor.setDetected(true); sensors.save(sensor);
        var binding = ru.growerhub.backend.automation.jpa.AutomationResourceBindingEntity.create("ROOM", farm, "AIR_TEMPERATURE_SENSOR", now);
        binding.setSourceType("NATIVE_SENSOR"); binding.setNativeSensorId(sensor.getId()); bindings.save(binding);
        for (int i = 0; i < 6; i++) {
            var reading = ru.growerhub.backend.sensor.jpa.SensorReadingEntity.create();
            reading.setSensor(sensor); reading.setTs(now.minusHours(6 - i)); reading.setValueNumeric(20d + i); readings.save(reading);
        }
        var chat = create(first); send(first, chat); var work = advisor.claimAssistantWork("deepseek");
        var call = new ToolCall("history", "gh_sensor_history", json.valueToTree(Map.of("resourceId", binding.getId(), "hours", 24)));
        advisor.beginAssistantTool(work.id(), work.lease(), call);
        var result = advisor.executeAssistantTool(work.id(), work.lease(), call);
        advisor.finishAssistantTool(work.id(), work.lease(), call.id(), result);
        assertThat(result.path("savedPoints").asInt()).isEqualTo(6);
        assertThat(result.path("unit").asText()).isEqualTo("°C");
        assertThat(result.path("minimum").path("value").asDouble()).isEqualTo(20d);
        assertThat(result.path("maximum").path("value").asDouble()).isEqualTo(25d);
        assertThat(result.path("change").asDouble()).isEqualTo(5d);
        assertThat(result.path("largestGapSeconds").asLong()).isEqualTo(3600L);
        for (var foreign : List.of(principal(other), principal(owner))) {
            assertThatThrownBy(() -> automation.getResourceSensorHistory(foreign, binding.getId(), 24))
                    .isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        }
        var tooLong = new ToolCall("too-long", "gh_sensor_history", json.valueToTree(Map.of("resourceId", binding.getId(), "hours", 100000)));
        advisor.beginAssistantTool(work.id(), work.lease(), tooLong);
        assertThatThrownBy(() -> advisor.executeAssistantTool(work.id(), work.lease(), tooLong)).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        readings.deleteAll(); bindings.delete(binding); sensors.delete(sensor); devices.delete(device);
    }
    @Test void assistantCreatesMovesAndFillsPlantsOnlyAfterConfirmation() {
        var user = principal(first); int a = plantZone(user, "Теплица 1"), b = plantZone(user, "Теплица 2");
        UUID chat = create(first); send(first, chat); var work = advisor.claimAssistantWork("deepseek");
        var proposal = plantChange(work, "create-plant", Map.of("operation", "create", "fields", Map.of("name", "Розмарин", "plantType", "Пряные травы"), "zoneId", a));
        assertThat(plants.listPlants(user)).isEmpty();
        assertThat(proposal.path("payload").path("zoneName").asText()).isEqualTo("Теплица 1");
        assertThat(proposal.path("payload").path("fields").path("dateUnknown").asBoolean()).isTrue();
        var saved = applyChange(proposal, user); int id = saved.result().path("id").asInt();
        assertThat(plants.getPlant(id, user).plantedAt()).isNull();
        assertThat(automation.getPlantZone(user, id).id()).isEqualTo(a);
        advisor.applyAssistantProposal(saved.id()); assertThat(plants.listPlants(user)).hasSize(1);
        var moved = plantChange(work, "move-plant", Map.of("operation", "update", "plantId", id, "fields", Map.of("description", "Черенок от мамы", "strain", "Арп"), "zoneId", b));
        assertThat(automation.getPlantZone(user, id).id()).isEqualTo(a);
        applyChange(moved, user);
        assertThat(automation.getPlantZone(user, id).id()).isEqualTo(b);
        assertThat(plants.getPlant(id, user).description()).isEqualTo("Черенок от мамы");
        assertThat(plants.getPlant(id, user).strain()).isEqualTo("Арп");
        var detach = json.createObjectNode().put("operation", "update").put("plantId", id); detach.set("fields", json.createObjectNode()); detach.putNull("zoneId");
        var unplace = assistantTool(work, "unplace", "gh_prepare_change", Map.of("kind", "plant", "payload", detach));
        applyChange(unplace, user); assertThat(automation.getPlantZone(user, id)).isNull();
        assertThat(plants.getPlant(id, user).description()).isEqualTo("Черенок от мамы");
    }
    @Test void manualPlantChangesInvalidateTheExactAssistantProposal() {
        var user = principal(first); int zone = plantZone(user, "Розмарин");
        int id = plants.createPlant(new ru.growerhub.backend.plant.PlantFacade.PlantCreateCommand("Куст", null, null, null, null, "", "", true), user).id();
        UUID chat = create(first); send(first, chat); var work = advisor.claimAssistantWork("deepseek");
        var proposal = plantChange(work, "stale-move", Map.of("operation", "update", "plantId", id, "fields", Map.of(), "zoneId", zone));
        plants.updatePlant(id, new ru.growerhub.backend.plant.PlantFacade.PlantUpdateCommand("Новое имя", null, null, null, null), user);
        var confirmed = advisor.confirmAssistantProposal(UUID.fromString(proposal.path("id").asText()), new Confirm(proposal.path("revision").asText()), user, false);
        assertThat(confirmed.status()).isEqualTo("stale"); assertThat(advisor.claimAssistantApplication()).isNull();
        assertThat(automation.getPlantZone(user, id)).isNull(); assertThat(plants.getPlant(id, user).name()).isEqualTo("Новое имя");
    }
    @Test void ownerPhotoKeepsRoomForTheImageAndTrimsOldConversationBeforeCodex() throws Exception {
        var user = principal(owner); var chat = create(owner);
        for (int i = 0; i < 3; i++) {
            advisor.sendAssistantMessage(chat, new Send(UUID.randomUUID(), "Старая переписка " + "растение ".repeat(90), List.of()), user, new Origin(null, null, "app"));
            var previous = advisor.claimAssistantWork("codex");
            advisor.finishAssistantWork(previous.id(), previous.lease(), new Outcome("completed", "Описание ухода ".repeat(90), 100, 100, true, null));
        }
        var image = advisor.uploadAssistantPhoto(chat, journalTestPhoto(), user, new Origin(null, null, "app"));
        advisor.sendAssistantMessage(chat, new Send(UUID.randomUUID(), "Сохрани приложенную фотографию в журнал моего розмарина. Текст: новый прирост после пересадки. Подготовь карточку и покажи растение, которому будет добавлена фотография.", List.of(image.id())), user, new Origin(null, null, "app"));
        var work = advisor.claimAssistantWork("codex");
        var schemas = json.valueToTree(work.tools());
        schemas.forEach(schema -> ((com.fasterxml.jackson.databind.node.ObjectNode) schema).put("strict", false));
        int estimate = json.writeValueAsBytes(schemas).length;
        for (var message : work.messages()) for (Object item : (List<?>) message.get("content")) {
            var part = (Map<?, ?>) item;
            estimate += "image".equals(part.get("type")) ? 6144 : part.get("text").toString().getBytes(StandardCharsets.UTF_8).length;
        }
        assertThat(estimate).as("Codex preflight including image and complete owner tool catalogue").isLessThanOrEqualTo(work.maxInputTokens());
        assertThat(work.messages().toString()).contains(image.id().toString()).doesNotContain("Старая переписка");
        assertThat(work.tools().stream().map(t -> t.get("name"))).contains("gh_journal", "gh_prepare_change", "gh_admin_users");
    }
    @Test void telegramPhotoIsCopiedToTheChosenJournalOnceAndSharedWithTheApp() throws Exception {
        var user = principal(first); int id = plants.createPlant(new ru.growerhub.backend.plant.PlantFacade.PlantCreateCommand("Мой розмарин", null, null, null, null), user).id();
        long update = 3000000L + user.id() * 100L, address = update + 50;
        var link = notifications.link(user);
        notifications.receive(new ru.growerhub.backend.notification.contract.TelegramData.Update(update, address, true, "Test", "/start " + link.url().split("start=")[1], null, null));
        notifications.confirm(user, notifications.status(user).confirmation());
        var chat = create(first); advisor.saveAssistantPreferences(new Preferences(true, "off", 9, 1, chat.toString()), user);
        byte[] source = journalTestPhoto(); when(telegramGateway.photo(eq("rosemary-file"), anyInt())).thenReturn(source);
        var event = new ru.growerhub.backend.notification.contract.TelegramData.Update(update + 1, address, true, "Test", "Добавь фото в журнал розмарина", null, null, "rosemary-file", source.length);
        notifications.receive(event); notifications.receive(event); advisor.processAssistantInbox();
        var work = advisor.claimAssistantWork("deepseek");
        UUID image = jdbc.queryForObject("select id from assistant_photos where user_id=?", UUID.class, user.id());
        assertThat(work.messages().toString()).contains(image.toString());
        var proposal = assistantTool(work, "journal-photo", "gh_prepare_change", Map.of("kind", "care", "payload", Map.of("plantId", id, "action", "photo", "text", "Новый прирост", "photoIds", List.of(image.toString()))));
        assertThat(journal.listEntries(id, user)).isEmpty();
        assertThat(proposal.path("payload").path("plantName").asText()).isEqualTo("Мой розмарин");
        advisor.finishAssistantWork(work.id(), work.lease(), new Outcome("completed", "Проверьте растение и фото", 500, 100, true, null));
        advisor.deliverAssistantResponses();
        assertThat(jdbc.queryForObject("select count(*) from telegram_deliveries where text like '%Фото в журнал: 1%'", Integer.class)).isEqualTo(1);
        UUID proposalId = UUID.fromString(proposal.path("id").asText());
        var callback = new ru.growerhub.backend.notification.contract.TelegramData.Update(update + 2, address, true, "Test", null, null, "gha:" + proposalId.toString().replace("-", "") + ":" + proposal.path("revision").asText());
        notifications.receive(callback); notifications.receive(callback); advisor.processAssistantInbox();
        assertThat(advisor.claimAssistantApplication().id()).isEqualTo(proposalId);
        advisor.applyAssistantProposal(proposalId); advisor.applyAssistantProposal(proposalId);
        var entry = journal.listEntries(id, user).getFirst(); assertThat(entry.photos()).hasSize(1);
        assertThat(journal.listEntries(id, user)).hasSize(1);
        assertThat(advisor.assistantMessages(chat, user)).singleElement().satisfies(message ->
                assertThat(message.proposals().getFirst().result().path("entry").path("photos")).hasSize(1));
        int photoId = entry.photos().getFirst().id();
        assertThatThrownBy(() -> journal.getPhoto(photoId, principal(other))).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        jdbc.update("delete from assistant_photos where id=?", image);
        assertThat(journal.getPhoto(photoId, user).data()).isNotEmpty();
        advisor.deliverAssistantResponses();
        assertThat(jdbc.queryForObject("select count(*) from telegram_deliveries where text like '%Журнал растения:%'", Integer.class)).isEqualTo(1);
    }
    @Test void assistantEditsAndSearchesManualCareWithoutChangingItsDate() {
        var user = principal(first); int id = plants.createPlant(new ru.growerhub.backend.plant.PlantFacade.PlantCreateCommand("Розмарин", null, null, null, null), user).id();
        var time = LocalDateTime.of(2026, 10, 1, 10, 0);
        var entry = journal.createCareEntry(id, user, new ru.growerhub.backend.journal.contract.CareData.EntryCommand("pruning", "Срезала сухую веточку", time, "initial"));
        var chat = create(first); send(first, chat); var work = advisor.claimAssistantWork("deepseek");
        var read = assistantTool(work, "read-journal", "gh_journal", Map.of("plantId", id, "query", "веточку", "action", "pruning", "page", 0));
        assertThat(read.path("page").path("items")).hasSize(1);
        var proposal = assistantTool(work, "edit-journal", "gh_prepare_change", Map.of("kind", "care", "payload", Map.of("plantId", id, "entryId", entry.entry().id(), "text", "Обрезаны две сухие веточки")));
        applyChange(proposal, user);
        var updated = journal.getCareEntry(entry.entry().id(), user);
        assertThat(updated.action()).isEqualTo("pruning"); assertThat(updated.entry().eventAt()).isEqualTo(time);
        assertThat(updated.entry().text()).isEqualTo("Обрезаны две сухие веточки"); assertThat(journal.listEntries(id, user)).hasSize(1);
    }
    @Test void evenTheOwnerCannotChangeForeignPlantsZonesEntriesOrInventFields() {
        var stranger = principal(other); int foreignZone = plantZone(stranger, "Чужая теплица");
        int plantId = plants.createPlant(new ru.growerhub.backend.plant.PlantFacade.PlantCreateCommand("Чужой куст", null, null, null, null), stranger).id();
        var entry = journal.createCareEntry(plantId, stranger, new ru.growerhub.backend.journal.contract.CareData.EntryCommand("note", "Личные записи", null, "private"));
        var chat = create(owner); send(owner, chat); var work = advisor.claimAssistantWork("codex");
        for (var args : List.of(Map.of("kind", "plant", "payload", Map.of("operation", "update", "plantId", plantId, "fields", Map.of("name", "Взлом"))),
                Map.of("kind", "plant", "payload", Map.of("operation", "create", "zoneId", foreignZone, "fields", Map.of("name", "Новый"))),
                Map.of("kind", "plant", "payload", Map.of("operation", "create", "fields", Map.of("name", "Новый", "userId", other.getId()))))) {
            assertThatThrownBy(() -> assistantTool(work, UUID.randomUUID().toString(), "gh_prepare_change", args)).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        }
        assertThatThrownBy(() -> assistantTool(work, "private-journal", "gh_journal", Map.of("entryId", entry.entry().id()))).isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        assertThat(plants.listPlants(principal(owner))).isEmpty(); assertThat(plants.getPlant(plantId, stranger).name()).isEqualTo("Чужой куст");
        assertThat(jdbc.queryForObject("select count(*) from assistant_proposals", Integer.class)).isZero();
    }
    @Test void aPhotoCannotBeTakenFromAnotherChatOrAnotherAccount() throws Exception {
        var user = principal(first); UUID ownChat = create(first), otherChat = create(first), foreignChat = create(other);
        var ownImage = advisor.uploadAssistantPhoto(otherChat, journalTestPhoto(), user, new Origin(null, null, "app"));
        var foreignImage = advisor.uploadAssistantPhoto(foreignChat, journalTestPhoto(), principal(other), new Origin(null, null, "app"));
        int id = plants.createPlant(new ru.growerhub.backend.plant.PlantFacade.PlantCreateCommand("Розмарин", null, null, null, null), user).id();
        send(first, ownChat); var work = advisor.claimAssistantWork("deepseek");
        for (var image : List.of(ownImage, foreignImage)) assertThatThrownBy(() -> assistantTool(work, image.id().toString(), "gh_prepare_change",
                Map.of("kind", "care", "payload", Map.of("plantId", id, "action", "photo", "photoIds", List.of(image.id().toString())))))
                .isInstanceOf(ru.growerhub.backend.common.contract.DomainException.class);
        assertThat(journal.listEntries(id, user)).isEmpty(); assertThat(jdbc.queryForObject("select count(*) from assistant_proposals", Integer.class)).isZero();
    }
    private int plantZone(AuthenticatedUser user, String name) {
        int farm = automation.createUserFarm(user, new ru.growerhub.backend.automation.contract.AutomationData.SaveRoomRequest(name + " ферма", true)).farms().stream().filter(f -> f.name().equals(name + " ферма")).findFirst().orElseThrow().id();
        return automation.createGreenhouse(user, farm, new ru.growerhub.backend.automation.contract.AutomationData.SaveBoxRequest(name, true)).farms().stream()
                .flatMap(f -> f.greenhouses().stream()).filter(g -> g.name().equals(name)).findFirst().orElseThrow().id();
    }
    private com.fasterxml.jackson.databind.JsonNode plantChange(Work work, String key, Map<String, ?> payload) { return assistantTool(work, key, "gh_prepare_change", Map.of("kind", "plant", "payload", payload)); }
    private com.fasterxml.jackson.databind.JsonNode assistantTool(Work work, String key, String name, Map<String, ?> arguments) {
        var call = new ToolCall(key, name, json.valueToTree(arguments)); advisor.beginAssistantTool(work.id(), work.lease(), call);
        var result = advisor.executeAssistantTool(work.id(), work.lease(), call); return advisor.finishAssistantTool(work.id(), work.lease(), key, result);
    }
    private Proposal applyChange(com.fasterxml.jackson.databind.JsonNode value, AuthenticatedUser user) {
        UUID id = UUID.fromString(value.path("id").asText());
        assertThat(advisor.confirmAssistantProposal(id, new Confirm(value.path("revision").asText()), user, false).status()).isEqualTo("confirmed");
        assertThat(advisor.claimAssistantApplication().id()).isEqualTo(id); return advisor.applyAssistantProposal(id);
    }
    private byte[] journalTestPhoto() throws Exception {
        var image = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "jpeg", bytes); return bytes.toByteArray();
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
