package ru.growerhub.backend.advisor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.ObjectProvider;
import ru.growerhub.backend.advisor.contract.AssistantData;
import ru.growerhub.backend.advisor.contract.AssistantGateway;
import ru.growerhub.backend.advisor.contract.AssistantLimitException;
import ru.growerhub.backend.advisor.engine.AssistantService;
import ru.growerhub.backend.advisor.engine.AssistantChannels;
import ru.growerhub.backend.notification.NotificationFacade;
import ru.growerhub.backend.notification.contract.TelegramGateway;
import ru.growerhub.backend.notification.contract.TelegramData;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.common.util.SafeImage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.advisor.contract.WateringAdvice;
import ru.growerhub.backend.advisor.contract.WateringAdviceBundle;
import ru.growerhub.backend.advisor.contract.WateringPrevious;
import ru.growerhub.backend.advisor.engine.WateringAdviceContext;
import ru.growerhub.backend.advisor.engine.WateringAdviceEngine;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.config.advisor.AdvisorSettings;
import ru.growerhub.backend.diagnostics.PlantTiming;
import ru.growerhub.backend.journal.JournalFacade;
import ru.growerhub.backend.journal.contract.JournalWateringInfo;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.plant.contract.PlantInfo;
import ru.growerhub.backend.plant.contract.PlantMetricBucketPoint;
import ru.growerhub.backend.plant.contract.PlantMetricType;

@Service
public class AdvisorFacade {
    private static final Duration WATERING_HISTORY_BUCKET = Duration.ofHours(2);
    private static final int WATERING_HISTORY_HOURS = 72;
    private static final String FERTILIZERS_NAME = "Terra Aquatica TriPart";
    private static final String FERTILIZERS_FORMAT = "gX-mY-bZ drops per liter";

    private final PlantFacade plantFacade;
    private final JournalFacade journalFacade;
    private final WateringAdviceEngine adviceEngine;
    private final AdvisorSettings advisorSettings;
    private final AssistantService assistant;
    private final AssistantGateway assistantGateway;
    private final ObjectProvider<AdvisorFacade> self;
    private final AssistantChannels channels;
    private final NotificationFacade notifications;
    private final TelegramGateway telegramGateway;
    private final AssistantSettings assistantSettings;

    public AdvisorFacade(
            PlantFacade plantFacade,
            JournalFacade journalFacade,
            WateringAdviceEngine adviceEngine,
            AdvisorSettings advisorSettings, AssistantService assistant, AssistantGateway assistantGateway,
            ObjectProvider<AdvisorFacade> self, AssistantChannels channels, NotificationFacade notifications,
            TelegramGateway telegramGateway, AssistantSettings assistantSettings
    ) {
        this.plantFacade = plantFacade;
        this.journalFacade = journalFacade;
        this.adviceEngine = adviceEngine;
        this.advisorSettings = advisorSettings;
        this.assistant = assistant; this.assistantGateway = assistantGateway; this.self = self;
        this.channels = channels; this.notifications = notifications; this.telegramGateway = telegramGateway; this.assistantSettings = assistantSettings;
    }

    @Transactional(readOnly = true)
    public AssistantData.Capabilities assistantCapabilities(AuthenticatedUser user, AssistantData.Origin origin) { return assistant.capabilities(user, origin); }
    public List<AssistantData.Model> assistantModels(AuthenticatedUser user) {
        assistant.requireModelAccess(user); return assistant.cacheModels(assistantGateway.models());
    }
    @Transactional(readOnly = true)
    public List<AssistantData.Conversation> assistantConversations(AuthenticatedUser user) { return assistant.conversations(user); }
    @Transactional(noRollbackFor = AssistantLimitException.class)
    public AssistantData.Conversation createAssistantConversation(AuthenticatedUser user, AssistantData.Origin origin, AssistantData.Options options) { return assistant.create(user, origin, options); }
    @Transactional
    public AssistantData.Conversation configureAssistantConversation(UUID id, AssistantData.Options options, AuthenticatedUser user, AssistantData.Origin origin) { return assistant.configure(id, options, user, origin); }
    @Transactional(readOnly = true)
    public List<AssistantData.Message> assistantMessages(UUID id, AuthenticatedUser user) { return assistant.messages(id, user); }
    @Transactional(readOnly = true)
    public AssistantData.Message assistantMessage(UUID id, AuthenticatedUser user) { return assistant.message(id, user); }
    @Transactional(noRollbackFor = AssistantLimitException.class)
    public AssistantData.Message sendAssistantMessage(UUID id, AssistantData.Send request, AuthenticatedUser user, AssistantData.Origin origin) { return assistant.send(id, request, user, origin); }
    @Transactional
    public AssistantData.Message cancelAssistantMessage(UUID id, AuthenticatedUser user) { return assistant.cancel(id, user); }
    @Transactional(noRollbackFor = AssistantLimitException.class)
    public AssistantData.PhotoPermit prepareAssistantPhoto(UUID id, AuthenticatedUser user, AssistantData.Origin origin) { return assistant.preparePhoto(id, user, origin); }
    @Transactional
    public AssistantData.Attachment finishAssistantPhoto(UUID id, byte[] jpeg, AuthenticatedUser user) { return assistant.finishPhoto(id, jpeg, user); }
    public AssistantData.Attachment uploadAssistantPhoto(UUID conversation, byte[] source, AuthenticatedUser user, AssistantData.Origin origin) {
        var permit = self.getObject().prepareAssistantPhoto(conversation, user, origin);
        try { return self.getObject().finishAssistantPhoto(permit.id(), SafeImage.jpeg(source, permit.maxBytes(), permit.maxPixels(), permit.edge()), user); }
        catch (RuntimeException ex) { self.getObject().finishAssistantPhoto(permit.id(), null, user); throw ex; }
    }
    @Transactional(readOnly = true)
    public AssistantData.Image assistantImage(UUID id, AuthenticatedUser user) { return assistant.image(id, user); }
    @Transactional
    public AssistantData.Work claimAssistantWork(String provider) { return assistant.claim(provider); }
    @Transactional(readOnly = true)
    public boolean assistantWorkAlive(UUID id, String lease) { return assistant.alive(id, lease); }
    @Transactional
    public void appendAssistantText(UUID id, String lease, String text) { assistant.append(id, lease, text); }
    @Transactional
    public AssistantData.ToolPermit beginAssistantTool(UUID id, String lease, AssistantData.ToolCall call) { return assistant.beginTool(id, lease, call); }
    @Transactional
    public JsonNode executeAssistantTool(UUID id, String lease, AssistantData.ToolCall call) { return assistant.executeTool(id, lease, call); }
    @Transactional
    public JsonNode finishAssistantTool(UUID id, String lease, String toolId, JsonNode result) { return assistant.finishTool(id, lease, toolId, result); }
    @Transactional
    public void finishAssistantWork(UUID id, String lease, AssistantData.Outcome result) { assistant.finish(id, lease, result); }
    @Transactional
    public AssistantData.Proposal confirmAssistantProposal(UUID id, AssistantData.Confirm request, AuthenticatedUser user, boolean reject) { return assistant.confirm(id, request, user, reject); }
    @Transactional
    public AssistantData.Application claimAssistantApplication() { return assistant.claimApplication(); }
    @Transactional
    public AssistantData.Proposal applyAssistantProposal(UUID id) { return assistant.apply(id); }
    @Transactional
    public void failAssistantApplication(UUID id, String code) { assistant.applicationFailed(id, code); }
    @Transactional(readOnly = true)
    public AssistantData.Usage assistantUsage(AuthenticatedUser user) { return assistant.usage(user); }
    @Transactional
    public AssistantData.Usage switchPublicAssistant(boolean enabled, AuthenticatedUser user) { return assistant.publicSwitch(enabled, user); }
    @Transactional
    public AssistantData.Preferences saveAssistantPreferences(AssistantData.Preferences value, AuthenticatedUser user) { return channels.save(value, user); }
    @Transactional(noRollbackFor = AssistantLimitException.class)
    public AssistantData.Incoming beginAssistantInbox(TelegramData.Inbox item) { return channels.begin(item); }
    @Transactional(noRollbackFor = AssistantLimitException.class)
    public void completeAssistantInbox(AssistantData.Incoming work, UUID photo) { channels.complete(work, photo); }
    @Transactional
    public void rejectAssistantInbox(TelegramData.Inbox item, String message) { channels.reject(item, message); }
    public void processAssistantInbox() {
        if (!assistantSettings.enabled()) return;
        var item = notifications.claimAssistantInbox(); if (item == null) return;
        AssistantData.Incoming work = null;
        try {
            if (item.userId().equals(assistantSettings.ownerUserId())) assistantModels(new AuthenticatedUser(item.userId(), "user"));
            work = self.getObject().beginAssistantInbox(item);
            if (work != null) {
                UUID photo = null;
                if (work.photo() != null) {
                    var permit = work.photo();
                    var bytes = telegramGateway.photo(item.photoId(), permit.maxBytes());
                    photo = self.getObject().finishAssistantPhoto(permit.id(), SafeImage.jpeg(bytes, permit.maxBytes(), permit.maxPixels(), permit.edge()), work.user()).id();
                }
                self.getObject().completeAssistantInbox(work, photo);
            }
            notifications.finishAssistantInbox(item, "completed");
        } catch (RuntimeException ex) {
            if (work != null && work.photo() != null) try { self.getObject().finishAssistantPhoto(work.photo().id(), null, work.user()); } catch (RuntimeException ignored) { }
            self.getObject().rejectAssistantInbox(item, ex instanceof DomainException d ? d.getMessage() : "Не удалось обработать сообщение. Попробуйте позже в GrowerHub.");
            notifications.finishAssistantInbox(item, "failed");
        }
    }
    @Transactional(readOnly = true)
    public List<AssistantData.ReportDue> assistantReportsDue() { return channels.due(); }
    @Transactional(noRollbackFor = AssistantLimitException.class)
    public void prepareAssistantReport(AssistantData.ReportDue due) { channels.report(due); }
    @Transactional
    public void deliverAssistantResponses() { channels.deliver(); }
    @Transactional
    public void maintainAssistant() { assistant.maintain(); }

    /**
     * Poluchaet rekomendacii poliva i predydushchii poliv dlya rastenija.
     */
    @Transactional
    public WateringAdviceBundle getWateringAdvice(Integer plantId, AuthenticatedUser user) {
        if (!advisorSettings.isEnabled()) {
            return null;
        }
        long adviceStart = PlantTiming.startTimer();
        try {
            PlantInfo plant = plantFacade.getPlant(plantId, user);
            long journalStart = PlantTiming.startTimer();
            JournalWateringInfo lastWatering = journalFacade.getLastWatering(plantId, user);
            PlantTiming.recordJournal(journalStart);
            WateringPrevious previous = lastWatering != null
                    ? new WateringPrevious(
                    lastWatering.waterVolumeL(),
                    lastWatering.ph(),
                    lastWatering.fertilizersPerLiter(),
                    lastWatering.eventAt()
            )
                    : null;

            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime since = now.minusHours(WATERING_HISTORY_HOURS);
            List<PlantMetricType> metrics = List.of(
                    PlantMetricType.SOIL_MOISTURE,
                    PlantMetricType.AIR_TEMPERATURE,
                    PlantMetricType.AIR_HUMIDITY
            );
            long historyStart = PlantTiming.startTimer();
            List<PlantMetricBucketPoint> history = plantFacade.getBucketedHistory(
                    plantId,
                    user,
                    metrics,
                    since,
                    WATERING_HISTORY_BUCKET
            );
            PlantTiming.recordHistory(historyStart);
            Integer ageDays = resolveAgeDays(plant != null ? plant.plantedAt() : null);
            WateringAdviceContext context = new WateringAdviceContext(
                    plantId,
                    plant,
                    ageDays,
                    previous,
                    history,
                    FERTILIZERS_NAME,
                    FERTILIZERS_FORMAT
            );
            WateringAdvice advice = adviceEngine.resolveAdvice(
                    context,
                    lastWatering != null ? lastWatering.eventAt() : null
            );
            return new WateringAdviceBundle(previous, advice);
        } finally {
            PlantTiming.recordAdvice(plantId, adviceStart);
        }
    }

    private Integer resolveAgeDays(LocalDateTime plantedAt) {
        if (plantedAt == null) {
            return null;
        }
        LocalDate plantedDate = plantedAt.toLocalDate();
        int ageDays = (int) ChronoUnit.DAYS.between(plantedDate, LocalDate.now(ZoneOffset.UTC));
        return Math.max(ageDays, 0);
    }
}
