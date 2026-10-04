package ru.growerhub.backend.journal;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.common.contract.*;
import ru.growerhub.backend.common.util.SafeImage;
import ru.growerhub.backend.journal.contract.CareData;
import ru.growerhub.backend.notification.NotificationFacade;
import ru.growerhub.backend.notification.contract.TelegramData;
import ru.growerhub.backend.notification.engine.TelegramService;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.user.UserFacade;
import ru.growerhub.backend.user.jpa.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"telegram.enabled=true", "telegram.bot-token=test", "telegram.poll-ms=3600000", "telegram.webhook-secret=test-webhook"})
class CareJournalIntegrationTest extends IntegrationTestBase {
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired JournalFacade journal;
    @Autowired PlantFacade plants;
    @Autowired UserRepository users;
    @Autowired UserFacade userFacade;
    @Autowired NotificationFacade notifications;
    @Autowired ru.growerhub.backend.notification.jpa.TelegramDeliveryRepository deliveries;
    @Autowired ru.growerhub.backend.api.NotificationController webhook;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    AuthenticatedUser account() {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var user = users.save(UserEntity.create(UUID.randomUUID() + "@example.test", "Care", "user", true, "Europe/Moscow", now, now));
        return new AuthenticatedUser(user.getId(), "user");
    }
    Integer plant(AuthenticatedUser owner) {
        return plants.createPlant(new PlantFacade.PlantCreateCommand("Монстера", null, null, null, null, "У окна", "Гостиная", true), owner).id();
    }
    @Test void journalWorksWithoutEquipmentAndKeepsOwnershipAndIdempotency() throws Exception {
        var owner = account(); var other = account(); var id = plant(owner);
        assertNull(plants.requireOwnedPlantInfo(id, owner).plantedAt());
        var command = new CareData.EntryCommand("repotting", "Новый горшок", LocalDateTime.of(2026, 10, 1, 12, 0), "same-request");
        var created = journal.createCareEntry(id, owner, command);
        assertEquals(created.entry().id(), journal.createCareEntry(id, owner, command).entry().id());
        assertThrows(DomainException.class, () -> journal.createCareEntry(id, other, command));
        assertEquals(1, journal.searchCare(owner, null, "горшок", "", null, null, 0).items().size());
        assertEquals(1, journal.searchCare(owner, null, "Гостиная", "repotting", null, null, 0).items().size());
        assertTrue(journal.searchCare(other, null, "", "", null, null, 0).items().isEmpty());
        assertTrue(journal.searchCare(owner, null, "%", "", null, null, 0).items().isEmpty());
        var image = new BufferedImage(10, 20, BufferedImage.TYPE_INT_RGB); var out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out);
        byte[] jpeg = SafeImage.jpeg(out.toByteArray(), 6000000, 24000000, 1600);
        var photo = journal.addCarePhoto(created.entry().id(), owner, jpeg);
        assertEquals(photo.id(), journal.addCarePhoto(created.entry().id(), owner, jpeg).id());
        assertNotNull(journal.getPhoto(photo.id(), owner));
        assertThrows(DomainException.class, () -> journal.getPhoto(photo.id(), other));
        assertEquals(photo.id(), journal.careSummaries(owner).get(0).coverPhotoId());
        assertEquals(1, journal.searchCare(owner, id, "", "", null, null, 0).items().get(0).entry().photos().size());
        var date = command.eventAt().minusDays(1);
        assertEquals(date, journal.updateCareEntry(created.entry().id(), owner, new CareData.EntryCommand("note", "Исправлено", date, null)).entry().eventAt());
        journal.deleteCareEntry(created.entry().id(), owner);
        assertTrue(journal.searchCare(owner, id, "", "", null, null, 0).items().isEmpty());
        journal.createEntry(id, owner, "photo", "До обновления", date, List.of());
        assertEquals(1, journal.searchCare(owner, id, "", "photo", null, null, 0).items().size());
        var harvest = journal.createCareEntry(id, owner, new CareData.EntryCommand("harvest", "Первый сбор", date, "harvest-request"));
        assertEquals("harvest", harvest.entry().type());
    }
    @Test void remindersCompleteOnceAndSnoozeIsNotCare() {
        var owner = account(); var id = plant(owner);
        var reminder = journal.saveCareReminder(null, owner, new CareData.ReminderCommand(id, "watering", "Проверить грунт", LocalDateTime.now().minusDays(1), 7, "calendar"));
        var action = new CareData.ReminderAction(reminder.occurrenceKey(), null);
        journal.actCareReminder(reminder.id(), owner, "done", action);
        journal.actCareReminder(reminder.id(), owner, "done", action);
        assertEquals(1, journal.searchCare(owner, id, "", "", null, null, 0).items().size());
        var next = journal.careReminders(owner).get(0);
        assertNotEquals(reminder.occurrenceKey(), next.occurrenceKey());
        assertTrue(next.dueAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)));
        journal.actCareReminder(next.id(), owner, "snooze", new CareData.ReminderAction(next.occurrenceKey(), LocalDateTime.now(ZoneOffset.UTC).plusDays(2)));
        assertEquals(1, journal.searchCare(owner, id, "", "", null, null, 0).items().size());
        assertThrows(DomainException.class, () -> journal.actCareReminder(next.id(), account(), "done", new CareData.ReminderAction(next.occurrenceKey(), null)));
    }
    @Test void telegramRequiresWebsiteConfirmationAndCannotBeReassignedOrUsedByDemo() {
        var owner = account(); var other = account(); long chat = 8000000L + owner.id();
        var link = notifications.link(owner); String token = link.url().substring(link.url().indexOf("start=") + 6);
        notifications.receive(new TelegramData.Update(chat, chat, true, "Test", "/start " + token, null, null));
        notifications.receive(new TelegramData.Update(chat, chat, true, "Test", "/start " + token, null, null));
        var pending = notifications.status(owner);
        assertFalse(pending.connected()); assertNotNull(pending.confirmation());
        assertThrows(DomainException.class, () -> notifications.confirm(owner, "old-confirmation"));
        assertTrue(notifications.confirm(owner, pending.confirmation()).connected());
        assertThrows(DomainException.class, () -> notifications.confirm(owner, pending.confirmation()));
        var otherLink = notifications.link(other); String otherToken = otherLink.url().split("start=")[1];
        notifications.receive(new TelegramData.Update(chat + 1, chat, true, "Test", "/start " + otherToken, null, null));
        assertNull(notifications.status(other).pendingName());
        assertThrows(DomainException.class, () -> notifications.link(new AuthenticatedUser(owner.id(), "demo")));
        notifications.disconnect(owner);
        assertFalse(notifications.status(owner).connected());
        assertTrue(notifications.claim().stream().noneMatch(d -> d.chatId() == chat));
    }
    @Test void careStartDoesNotPretendToConnectEquipmentAndQuietHoursWrapMidnight() {
        var owner = account(); userFacade.startCare(owner.id());
        assertNotNull(userFacade.getUser(owner.id()).careStartedAt());
        assertNull(userFacade.getUser(owner.id()).onboardingCompletedAt());
        assertTrue(TelegramService.quiet(23, 22, 8)); assertTrue(TelegramService.quiet(7, 22, 8));
        assertEquals(LocalDate.of(2026, 10, 3), TelegramService.digestDate(ZonedDateTime.of(2026, 10, 4, 8, 0, 0, 0, ZoneId.of("Europe/Moscow")), 23, 22, 8));
        assertNull(TelegramService.digestDate(ZonedDateTime.of(2026, 10, 4, 7, 0, 0, 0, ZoneId.of("Europe/Moscow")), 23, 22, 8));
        assertFalse(TelegramService.quiet(8, 22, 8)); assertFalse(TelegramService.quiet(22, 0, 0));
        assertThrows(DomainException.class, () -> SafeImage.jpeg(new byte[]{1, 2, 3}, 6000000, 24000000, 1600));
    }

    @Test void deliveryRetriesOnlyExplicitFailureAndDropsObsoleteTasks() {
        var owner = account(); var id = plant(owner); long chat = 9000000L + owner.id();
        var link = notifications.link(owner);
        notifications.receive(new TelegramData.Update(chat, chat, true, "Test", "/start " + link.url().split("start=")[1], null, null));
        notifications.confirm(owner, notifications.status(owner).confirmation());
        notifications.preferences(owner, new TelegramData.Preferences(true, 0, 0, 0));
        for (var delivery : notifications.claim()) notifications.finish(delivery, new TelegramData.Result("accepted", null));
        var reminder = journal.saveCareReminder(null, owner, new CareData.ReminderCommand(id, "watering", "Проверить грунт", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), null, "completion"));
        notifications.prepare();
        journal.deleteCareReminder(reminder.id(), owner);
        assertTrue(notifications.claim().stream().noneMatch(d -> d.chatId() == chat));
        notifications.test(owner);
        var delivery = notifications.claim().stream().filter(d -> d.chatId() == chat).findFirst().orElseThrow();
        notifications.finish(delivery, new TelegramData.Result("retry", 120));
        var queued = deliveries.findById(delivery.id()).orElseThrow();
        assertEquals("queued", queued.status);
        assertTrue(queued.availableAt.isAfter(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(100)));
        queued.availableAt = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1); deliveries.save(queued);
        var retry = notifications.claim().stream().filter(d -> d.id().equals(delivery.id())).findFirst().orElseThrow();
        notifications.finish(retry, new TelegramData.Result("uncertain", null));
        notifications.prepare();
        assertTrue(notifications.claim().stream().noneMatch(d -> d.id().equals(delivery.id())));
        notifications.receive(new TelegramData.Update(chat + 1, chat, true, "Test", "/help", null, null));
        var blocked = notifications.claim().stream().filter(d -> d.chatId() == chat).findFirst().orElseThrow();
        notifications.finish(blocked, new TelegramData.Result("blocked", null));
        assertFalse(notifications.status(owner).enabled());
        notifications.disconnect(owner);
    }
    @Test void webhookRejectsMissingSecretAndGreetsUnlinkedPrivateUser() throws Exception {
        String url = "http://127.0.0.1:" + port + "/api/notifications/telegram";
        io.restassured.RestAssured.given().contentType("application/json").body("{}").post(url + "/webhook").then().statusCode(403);
        io.restassured.RestAssured.given().header("X-Telegram-Bot-Api-Secret-Token", "test-webhook").contentType("application/json").body("{}").post(url + "/webhook").then().statusCode(200);
        io.restassured.RestAssured.given().get(url).then().statusCode(401);
        var body = json.readTree("{\"update_id\":777777,\"message\":{\"chat\":{\"id\":777777,\"type\":\"private\"},\"from\":{\"id\":777777,\"first_name\":\"Test\"},\"text\":\"/start\"}}");
        assertThrows(ru.growerhub.backend.api.ApiException.class, () -> webhook.webhook("", body));
        var reply = webhook.webhook("test-webhook", body);
        assertEquals("sendMessage", reply.get("method"));
        assertTrue(reply.get("text").toString().contains("/app/settings/notifications/"));
        var group = json.readTree(body.toString().replace("private", "group"));
        assertTrue(webhook.webhook("test-webhook", group).isEmpty());
    }
}
