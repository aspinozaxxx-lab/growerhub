package ru.growerhub.backend.automation.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData.*;
import ru.growerhub.backend.automation.contract.ControlData.*;
import ru.growerhub.backend.automation.jpa.*;
import ru.growerhub.backend.common.config.automation.ControlSettings;
import ru.growerhub.backend.common.contract.*;
import ru.growerhub.backend.user.UserFacade;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeControlObservation;

@Service
public class ControlService {
    private final ControlDemandRepository demands;
    private final ControlChannelRepository channels;
    private final AutomationScenarioConfigRepository configs;
    private final AutomationScenarioStateRepository scenarioStates;
    private final RuleRepository rules;
    private final AutomationActionLogRepository actionLogs;
    private final AutomationResourceBindingRepository bindings;
    private final ControlSettings settings;
    private final UserFacade users;
    private final ZigbeeFacade zigbee;
    private final ObjectProvider<AutomationFacade> automation;
    private final ObjectMapper json;
    private final Clock clock;

    public ControlService(ControlDemandRepository demands, ControlChannelRepository channels,
            AutomationScenarioConfigRepository configs, AutomationScenarioStateRepository scenarioStates,
            RuleRepository rules, AutomationActionLogRepository actionLogs, AutomationResourceBindingRepository bindings,
            ControlSettings settings, UserFacade users, ZigbeeFacade zigbee,
            ObjectProvider<AutomationFacade> automation, ObjectMapper json, Clock clock) {
        this.demands = demands; this.channels = channels; this.configs = configs; this.scenarioStates = scenarioStates;
        this.rules = rules; this.actionLogs = actionLogs; this.bindings = bindings;
        this.settings = settings; this.users = users; this.zigbee = zigbee;
        this.automation = automation; this.json = json; this.clock = clock;
    }
    private record Snapshot(Map<Integer, ResourceBinding> resources, Map<Integer, Boolean> enabled) {}
    private static final Set<String> LIVE = Set.of("pending", "active", "blocked");
    private static final Comparator<ControlDemandEntity> ORDER = Comparator.<ControlDemandEntity>comparingInt(d -> d.priority)
            .reversed().thenComparing(d -> d.createdAt).thenComparing(d -> d.sourceKey).thenComparing(d -> d.id);

    public List<Status> list(AuthenticatedUser user) {
        var snapshot = snapshot(user);
        return snapshot.resources.values().stream().filter(this::switchChannel)
                .sorted(Comparator.comparing(ResourceBinding::id)).map(r -> status(r, user)).toList();
    }
    public Status get(Integer resourceId, AuthenticatedUser user) { return status(resource(resourceId, user), user); }
    public Status manual(Integer resourceId, Manual request, AuthenticatedUser user) {
        lock(user); var r = resource(resourceId, user);
        if (request == null || request.requestId() == null || request.ttlSeconds() == null) throw bad("Укажите команду, срок и идентификатор запроса");
        ttl(request.ttlSeconds()); String value = value(r, request.value());
        String sourceKey = "manual:" + request.requestId();
        var repeated = demands.findByUserIdAndSourceKey(user.id(), sourceKey).orElse(null);
        if (repeated != null) {
            if (!Objects.equals(repeated.resourceId, resourceId) || !same(read(repeated.valueJson), value)
                    || !Objects.equals(repeated.durationSeconds, request.ttlSeconds())) throw conflict();
            return status(r, user);
        }
        if (!r.ready()) throw bad("Устройство сейчас недоступно");
        for (var old : demands.findByUserIdAndTargetKeyAndStatusIn(user.id(), target(r), LIVE))
            if ("manual".equals(old.sourceType)) release(old, "Заменено новой ручной командой");
        add(r, user, "manual", request.requestId().toString(), sourceKey, "Ручное управление",
                "Временная команда пользователя", settings.manualPriority(), value, request.ttlSeconds(), "manual", null, null);
        reconcile(user); return status(r, user);
    }
    public Status releaseManual(Integer resourceId, UUID demandId, int revision, AuthenticatedUser user) {
        lock(user); var r = resource(resourceId, user);
        var demand = demands.findById(demandId).filter(d -> user.id().equals(d.userId) && r.id().equals(d.resourceId)
                && "manual".equals(d.sourceType)).orElseThrow(() -> new DomainException("not_found", "Ручная команда не найдена"));
        if (demand.revision != revision) throw conflict();
        if (LIVE.contains(demand.status) || "error".equals(demand.status)) release(demand, "Ручное управление отменено");
        channels.findByUserIdAndTargetKey(user.id(), target(r)).filter(c -> Objects.equals(c.winnerId, demand.id)).ifPresent(this::idle);
        reconcile(user); return status(r, user);
    }
    public UUID ruleHold(ResourceBinding r, AuthenticatedUser user, UUID ruleId, String name,
            String executionKey, Object requested, int duration, int priority, String fingerprint) {
        lock(user); ttl(duration); rulePriority(priority); String value = value(r, requested);
        var existing = demands.findByUserIdAndSourceKey(user.id(), executionKey).orElse(null);
        if (existing != null) return existing.id;
        return add(r, user, "rule", ruleId.toString(), executionKey, name, "Временное удержание по правилу",
                priority, value, duration, "hold", fingerprint, now().plusSeconds(settings.pendingTtlSeconds())).id;
    }
    public UUID legacy(ResourceBinding r, AuthenticatedUser user, UUID ruleId, String name, String executionKey, Object requested, String fingerprint) {
        lock(user); String value = value(r, requested);
        var old = demands.findByUserIdAndSourceKey(user.id(), executionKey).orElse(null);
        if (old != null) return old.id;
        for (var demand : demands.findByUserIdAndTargetKeyAndStatusIn(user.id(), target(r), LIVE))
            if ("legacy".equals(demand.kind)) release(demand, "Следующая команда правила");
        return add(r, user, "rule", ruleId.toString(), executionKey, name, "Команда пользовательского правила",
                settings.legacyPriority(), value, null, "legacy", fingerprint, null).id;
    }
    public void scenario(Integer resourceId, AuthenticatedUser user, String sourceId, String name,
            String reason, Object requested, String fingerprint) {
        lock(user); var r = resource(resourceId, user); String value = value(r, requested);
        String key = "scenario:" + sourceId + ":" + target(r);
        var demand = demands.findByUserIdAndSourceKey(user.id(), key).orElse(null);
        if (demand != null && LIVE.contains(demand.status) && same(read(demand.valueJson), value)
                && Objects.equals(demand.bindingFingerprint, binding(r)) && Objects.equals(demand.sourceFingerprint, fingerprint)) {
            demand.validUntil = now().plusSeconds(settings.baselineTtlSeconds()); demands.save(demand); return;
        }
        if (demand != null && "error".equals(demand.status) && same(read(demand.valueJson), value)
                && Objects.equals(demand.bindingFingerprint, binding(r)) && Objects.equals(demand.sourceFingerprint, fingerprint)) return;
        if (demand != null) {
            demand.revision++; demand.valueJson = write(value); demand.status = "pending"; demand.error = null;
            demand.resourceId = r.id(); demand.targetKey = target(r); demand.bindingFingerprint = binding(r);
            demand.sourceFingerprint = fingerprint; demand.reason = reason; demand.requestedAt = now();
            demand.confirmedAt = null; demand.expiresAt = null;
            demand.validUntil = now().plusSeconds(settings.baselineTtlSeconds());
            demand.ackDeadlineAt = now().plusSeconds(settings.ackSeconds()); demands.save(demand);
        } else add(r, user, "scenario", sourceId, key, name, reason, settings.scenarioPriority(), value,
                null, "baseline", fingerprint, now().plusSeconds(settings.baselineTtlSeconds()));
    }
    public void releaseRule(Integer userId, UUID ruleId, String reason) {
        for (var d : demands.findByUserIdAndStatusIn(userId, LIVE)) if ("rule".equals(d.sourceType)
                && ruleId.toString().equals(d.sourceId)) release(d, reason);
    }
    public Demand demand(UUID id, AuthenticatedUser user) {
        var d = demands.findById(id).filter(v -> user.id().equals(v.userId)).orElseThrow(() -> new DomainException("not_found", "Требование не найдено"));
        return view(d, null);
    }
    public Demand findExecution(String key, AuthenticatedUser user) { return demands.findByUserIdAndSourceKey(user.id(), key).map(d -> view(d, null)).orElse(null); }
    public List<Integer> ownersDue() {
        return Stream.concat(demands.findByStatusIn(LIVE).stream().map(d -> d.userId),
                channels.findByStatusIn(Set.of("pending", "dispatching", "awaiting", "confirmed")).stream().map(c -> c.userId)).distinct().sorted().toList();
    }
    public void reconcile(Integer owner) {
        var user = principal(owner);
        if (user != null) { lock(user); reconcile(user); }
        else if (users.getAuthUser(owner) == null && !users.isDemoOwner(owner)) { channels.deleteByUserId(owner); demands.deleteByUserId(owner); }
    }
    public void maintain() {
        var cutoff = now().minusDays(settings.retentionDays());
        var candidates = demands.cleanupCandidates(LIVE, cutoff,
                org.springframework.data.domain.PageRequest.of(0, settings.cleanupBatch()));
        for (var owner : candidates.stream().map(ControlDemandRepository.CleanupCandidate::getUserId).distinct().toList()) {
            if (users.isDemoOwner(owner)) users.lockDemoOwner(owner);
            else if (users.getAuthUser(owner) != null && users.getAuthUser(owner).active()) users.lockAccount(owner);
            else continue;
            for (var candidate : candidates) if (owner.equals(candidate.getUserId())) {
                var demand = demands.findById(candidate.getId()).orElse(null);
                if (demand == null || LIVE.contains(demand.status) || !demand.requestedAt.isBefore(cutoff)) continue;
                for (var channel : channels.findByUserId(owner)) if (Objects.equals(channel.winnerId, demand.id)) idle(channel);
                demands.delete(demand);
            }
            // Odna arenda akkaunta za tranzakciyu ne sozdajot obratnyj porjadok blokirovok worker.
            break;
        }
    }
    private void reconcile(AuthenticatedUser user) {
        var snapshot = snapshot(user); var all = demands.findByUserIdAndStatusIn(user.id(), LIVE);
        for (var d : all) {
            var r = snapshot.resources.get(d.resourceId);
            String issue = r == null || !switchChannel(r) || !Objects.equals(d.bindingFingerprint, binding(r)) ? "Назначение оборудования изменилось"
                    : !"manual".equals(d.sourceType) && !snapshot.enabled.getOrDefault(d.resourceId, false) ? "Ферма или теплица выключена"
                    : d.expiresAt != null && !d.expiresAt.isAfter(now()) ? d.interrupted ? "Срок закончился: удержание было прервано другим управлением" : "Срок удержания закончился"
                    : d.validUntil != null && !d.validUntil.isAfter(now()) ? "Требование больше не подтверждено"
                    : !sourceValid(d) ? "Сценарий изменён или отключён" : null;
            if (issue != null) release(d, issue);
        }
        var targets = new LinkedHashMap<String, ResourceBinding>();
        snapshot.resources.values().stream().filter(this::switchChannel).forEach(r -> targets.put(target(r), r));
        for (var entry : targets.entrySet()) {
            var r = entry.getValue();
            var live = all.stream().filter(d -> LIVE.contains(d.status) && entry.getKey().equals(d.targetKey)).sorted(ORDER).toList();
            var channel = channels.findByUserIdAndTargetKey(user.id(), entry.getKey()).orElse(null);
            if (live.isEmpty()) { if (channel != null && !"error".equals(channel.status)) idle(channel); continue; }
            var winner = live.getFirst();
            for (var d : live) {
                if (!d.id.equals(winner.id) && d.confirmedAt != null && "hold".equals(d.kind) && !same(read(d.valueJson), read(winner.valueJson))) {
                    d.interrupted = true; d.error = "Удержание прерывалось более приоритетным управлением; полный цикл не подтверждён";
                }
                d.status = d.id.equals(winner.id) ? d.confirmedAt == null ? "pending" : "active" : "blocked"; demands.save(d);
            }
            if (channel == null) {
                channel = new ControlChannelEntity(); channel.id = UUID.randomUUID(); channel.userId = user.id();
                channel.resourceId = r.id(); channel.targetKey = entry.getKey();
            }
            boolean changed = !Objects.equals(channel.winnerId, winner.id) || !Objects.equals(channel.valueJson, winner.valueJson)
                    || !Objects.equals(channel.bindingFingerprint, binding(r)) || channel.revision == 0;
            if (changed) prepare(channel, winner, r);
            var observation = observation(r, user);
            boolean matching = fresh(observation) && same(observation.value(), read(winner.valueJson));
            if (matching && ("pending".equals(channel.status)
                    || channel.dispatchAt != null && !observation.observedAt().isBefore(channel.dispatchAt))) {
                boolean firstConfirmation = !"confirmed".equals(channel.status);
                channel.status = "confirmed"; channel.confirmedAt = channel.dispatchAt == null ? now() : observation.observedAt(); channel.error = null;
                if (firstConfirmation && channel.dispatchAt != null) recordScenarioConfirmation(winner, r, channel.confirmedAt);
                if (winner.confirmedAt == null) {
                    winner.confirmedAt = channel.confirmedAt; winner.status = "active";
                    if (!"baseline".equals(winner.kind)) winner.validUntil = null;
                    if (winner.durationSeconds != null) winner.expiresAt = winner.confirmedAt.plusSeconds(winner.durationSeconds);
                    if ("legacy".equals(winner.kind)) { winner.status = "released"; winner.reason = "Разовая команда подтверждена"; }
                    demands.save(winner);
                }
            } else if ("confirmed".equals(channel.status) && fresh(observation) && !matching) {
                if ("hold".equals(winner.kind)) {
                    winner.interrupted = true; winner.error = "Фактическое состояние изменилось во время удержания; полный цикл не подтверждён"; demands.save(winner);
                }
                // Novoe zhivoe protivopolozhnoe sostoyanie razreshaet novoe soglasovanie, no ne prodlevaet TTL.
                prepare(channel, winner, r);
            } else if (!"confirmed".equals(channel.status) && channel.ackDeadlineAt != null && !channel.ackDeadlineAt.isAfter(now())) {
                channel.status = "error"; channel.error = "Устройство не подтвердило команду вовремя. Команда не повторена";
                winner.status = "error"; winner.error = channel.error; demands.save(winner);
            }
            channels.save(channel);
        }
        for (var channel : channels.findByUserId(user.id())) if (!targets.containsKey(channel.targetKey)) idle(channel);
    }
    private void recordScenarioConfirmation(ControlDemandEntity d, ResourceBinding r, LocalDateTime at) {
        if (!"scenario".equals(d.sourceType)) return;
        String[] source = d.sourceId.split(":");
        String prefix = switch (r.role()) { case "LIGHT_SWITCH" -> "LIGHT"; case "EXHAUST_SWITCH" -> "EXHAUST"; default -> "AC"; };
        String action = prefix + (same(read(d.valueJson), Objects.requireNonNullElse(r.onValue(), "ON")) ? "_ON" : "_OFF");
        actionLogs.save(AutomationActionLogEntity.create(source[0], Integer.valueOf(source[1]), source[2],
                bindings.getReferenceById(r.id()), action, d.reason, "confirmed", null, at));
        scenarioStates.findByScopeTypeAndScopeIdAndScenarioType(source[0], Integer.valueOf(source[1]), source[2]).ifPresent(state -> {
            if (state.getLastActionAt() == null || state.getLastActionAt().isBefore(at)) state.setLastActionAt(at);
            state.setUpdatedAt(now()); scenarioStates.save(state);
        });
    }
    public Step claim(Integer owner) {
        var user = principal(owner); if (user == null) return null; lock(user); reconcile(user);
        for (var channel : channels.findByUserId(owner).stream().sorted(Comparator.comparing(c -> c.targetKey)).toList()) {
            if (!"pending".equals(channel.status)) continue;
            var d = demands.findById(channel.winnerId).orElse(null);
            if (d == null || !LIVE.contains(d.status)) continue;
            channel.status = "dispatching"; channel.dispatchAt = now(); channel.ackDeadlineAt = now().plusSeconds(settings.ackSeconds());
            channels.saveAndFlush(channel);
            return new Step(channel.id, channel.revision, channel.commandId, user, channel.coordinatorId,
                    channel.ieeeAddress, channel.property, read(channel.valueJson));
        }
        return null;
    }
    public void execute(Step step) {
        lock(step.user()); var channel = channel(step);
        var r = resource(channel.resourceId, step.user());
        var d = demands.findById(channel.winnerId).orElseThrow(ControlService::conflict);
        if (!LIVE.contains(d.status) || !Objects.equals(channel.bindingFingerprint, binding(r)) || !sourceValid(d)
                || !r.ready() || !"manual".equals(d.sourceType) && !snapshot(step.user()).enabled.getOrDefault(r.id(), false)
                || d.validUntil != null && !d.validUntil.isAfter(now())
                || d.expiresAt != null && !d.expiresAt.isAfter(now()) || !"dispatching".equals(channel.status)) throw conflict();
        zigbee.setDeviceProperty(step.user(), step.coordinatorId(), step.ieeeAddress(), step.property(), step.value());
    }
    public void finish(Step step, boolean success) {
        lock(step.user()); var channel = channel(step);
        if (!"dispatching".equals(channel.status)) return;
        channel.status = "awaiting";
        if (!success) channel.error = "Результат отправки неизвестен. Ожидает фактическое состояние без повторной команды";
        channels.save(channel);
    }
    private void prepare(ControlChannelEntity c, ControlDemandEntity d, ResourceBinding r) {
        c.resourceId = r.id(); c.bindingFingerprint = binding(r); c.coordinatorId = r.zigbeeCoordinatorId();
        c.ieeeAddress = r.zigbeeIeeeAddress(); c.property = r.commandProperty(); c.winnerId = d.id;
        c.revision++; c.commandId = UUID.randomUUID(); c.valueJson = d.valueJson; c.status = "pending";
        c.requestedAt = now(); c.ackDeadlineAt = now().plusSeconds(settings.ackSeconds()); c.dispatchAt = null; c.confirmedAt = null; c.error = null;
    }
    private void idle(ControlChannelEntity c) { c.status = "idle"; c.winnerId = null; c.commandId = null; c.error = null; channels.save(c); }
    private ControlChannelEntity channel(Step s) {
        return channels.findById(s.channelId()).filter(c -> c.userId.equals(s.user().id()) && c.revision == s.revision()
                && Objects.equals(c.commandId, s.commandId())).orElseThrow(ControlService::conflict);
    }
    private ControlDemandEntity add(ResourceBinding r, AuthenticatedUser user, String type, String sourceId, String key,
            String name, String reason, int priority, Object value, Integer duration, String kind, String fingerprint, LocalDateTime validUntil) {
        var d = new ControlDemandEntity(); d.id = UUID.randomUUID(); d.userId = user.id(); d.resourceId = r.id();
        d.targetKey = target(r); d.bindingFingerprint = binding(r); d.sourceType = type; d.sourceId = sourceId;
        d.sourceKey = key; d.sourceName = name; d.reason = reason; d.revision = 1; d.priority = priority;
        d.valueJson = write(value); d.kind = kind; d.status = "pending"; d.sourceFingerprint = fingerprint;
        d.requestedAt = now(); d.createdAt = now(); d.durationSeconds = duration; d.validUntil = validUntil;
        d.ackDeadlineAt = now().plusSeconds(settings.ackSeconds()); return demands.saveAndFlush(d);
    }
    private void release(ControlDemandEntity d, String reason) { d.status = "released"; d.reason = reason; demands.save(d); }
    private boolean sourceValid(ControlDemandEntity d) {
        if ("rule".equals(d.sourceType)) {
            try {
                var row = rules.findById(UUID.fromString(d.sourceId)).orElse(null);
                if (row == null || !row.enabled || d.sourceFingerprint != null && !Integer.toString(row.revision).equals(d.sourceFingerprint)) return false;
                return !"hold".equals(d.kind) || automation.getObject().ruleControlConditionAllowed(row.id, row.revision, principal(d.userId), d.confirmedAt != null);
            }
            catch (RuntimeException ex) { return false; }
        }
        if (!"scenario".equals(d.sourceType)) return true;
        String[] key = d.sourceId.split(":"); if (key.length != 3) return false;
        try {
            var c = configs.findByScopeTypeAndScopeIdAndScenarioType(key[0], Integer.valueOf(key[1]), key[2]).orElse(null);
            var state = scenarioStates.findByScopeTypeAndScopeIdAndScenarioType(key[0], Integer.valueOf(key[1]), key[2]).orElse(null);
            return c != null && c.isEnabled() && scenarioFingerprint(c).equals(d.sourceFingerprint)
                    && (state == null || !Set.of("disabled", "unready", "stale").contains(state.getStatus()));
        } catch (RuntimeException ex) { return false; }
    }
    public String scenarioFingerprint(AutomationScenarioConfigEntity c) { return digest(c.getId() + ":" + c.isEnabled() + ":" + c.getConfigJson() + ":" + c.getUpdatedAt()); }
    private Status status(ResourceBinding r, AuthenticatedUser user) {
        var all = demands.findByUserIdAndTargetKeyAndStatusIn(user.id(), target(r), LIVE).stream().filter(d ->
                (d.expiresAt == null || d.expiresAt.isAfter(now())) && (d.validUntil == null || d.validUntil.isAfter(now())))
                .sorted(ORDER).toList();
        var channel = channels.findByUserIdAndTargetKey(user.id(), target(r)).orElse(null);
        var winner = all.isEmpty() ? channel != null && "error".equals(channel.status) && channel.winnerId != null
                ? demands.findById(channel.winnerId).filter(d -> user.id().equals(d.userId) && "error".equals(d.status)).orElse(null)
                : null : all.getFirst(); var observation = observation(r, user);
        boolean confirmed = winner != null && winner.confirmedAt != null && fresh(observation) && same(observation.value(), read(winner.valueJson));
        var error = channel == null ? null : channel.error;
        return new Status(r.id(), winner == null ? null : winner.sourceType, winner == null ? null : winner.sourceId,
                winner == null ? null : winner.sourceName, winner == null ? null : winner.reason,
                winner == null ? null : winner.priority, winner == null ? null : read(winner.valueJson),
                winner == null ? null : winner.requestedAt, winner == null ? null : winner.confirmedAt,
                winner == null ? null : winner.expiresAt, winner == null ? 0 : remaining(winner),
                winner == null ? error == null ? "idle" : "error" : "error".equals(winner.status) ? "error" : confirmed ? "active" : "pending",
                observation == null ? null : observation.value(), observation == null ? null : observation.observedAt(), confirmed, error,
                (all.isEmpty() && winner != null ? List.of(winner) : all).stream().map(d -> view(d, winner)).toList());
    }
    private Demand view(ControlDemandEntity d, ControlDemandEntity winner) {
        String status = winner != null && !winner.id.equals(d.id) ? "blocked" : d.status;
        return new Demand(d.id, d.revision, d.sourceType, d.sourceId, d.sourceName, d.reason, d.priority, read(d.valueJson),
                d.requestedAt, d.confirmedAt, d.expiresAt, remaining(d), status, d.error);
    }
    private long remaining(ControlDemandEntity d) { return d.expiresAt == null ? 0 : Math.max(0, Duration.between(now(), d.expiresAt).getSeconds()); }
    private ZigbeeControlObservation observation(ResourceBinding r, AuthenticatedUser user) {
        try { return zigbee.controlObservation(user, r.zigbeeCoordinatorId(), r.zigbeeIeeeAddress(), r.commandProperty()); }
        catch (DomainException ex) { return null; }
    }
    private boolean fresh(ZigbeeControlObservation o) { return o != null && o.online() && o.value() != null && o.observedAt() != null
            && !o.observedAt().isAfter(now()) && !o.observedAt().isBefore(now().minusSeconds(settings.freshnessSeconds())); }
    public ResourceBinding resource(Integer id, AuthenticatedUser user) {
        var r = snapshot(user).resources.get(id);
        if (r == null || !switchChannel(r)) throw new DomainException("not_found", "Канал управления не найден"); return r;
    }
    private Snapshot snapshot(AuthenticatedUser user) {
        require(user); var result = new HashMap<Integer, ResourceBinding>(); var enabled = new HashMap<Integer, Boolean>();
        for (var f : automation.getObject().getFarmsOverview(user).farms()) {
            for (var r : f.slots()) { result.put(r.id(), r); enabled.put(r.id(), f.enabled()); }
            for (var g : f.greenhouses()) for (var r : g.slots()) { result.put(r.id(), r); enabled.put(r.id(), f.enabled() && g.enabled()); }
        }
        return new Snapshot(result, enabled);
    }
    public boolean switchChannel(ResourceBinding r) { return r != null && Set.of("LIGHT_SWITCH", "EXHAUST_SWITCH", "AC_SWITCH").contains(r.role())
            && r.zigbeeCoordinatorId() != null && r.zigbeeIeeeAddress() != null && r.commandProperty() != null; }
    private String value(ResourceBinding r, Object value) {
        if (value == null) throw bad("Выберите включение или выключение");
        String v = String.valueOf(value), on = Objects.requireNonNullElse(r.onValue(), "ON"), off = Objects.requireNonNullElse(r.offValue(), "OFF");
        if (v.equalsIgnoreCase(on) || v.equalsIgnoreCase("ON")) return on;
        if (v.equalsIgnoreCase(off) || v.equalsIgnoreCase("OFF")) return off;
        throw bad("Значение не поддерживается этим каналом");
    }
    public void ttl(int seconds) { if (seconds < settings.minTtlSeconds() || seconds > settings.maxTtlSeconds()) throw bad("Срок удержания: от " + settings.minTtlSeconds() + " до " + settings.maxTtlSeconds() + " секунд"); }
    public void rulePriority(int priority) { if (priority < settings.minRulePriority() || priority > settings.maxRulePriority()) throw bad("Приоритет: от " + settings.minRulePriority() + " до " + settings.maxRulePriority()); }
    public int defaultManualTtlSeconds() { return settings.defaultManualTtlSeconds(); }
    public Object normalizeValue(ResourceBinding r, Object requested) { return value(r, requested); }
    public int scenarioPriority() { return settings.scenarioPriority(); }
    private String target(ResourceBinding r) { return digest(r.zigbeeCoordinatorId() + ":" + r.zigbeeIeeeAddress() + ":" + r.commandProperty()); }
    private String binding(ResourceBinding r) { return digest(r.id() + ":" + r.scopeType() + ":" + r.scopeId() + ":" + r.role() + ":" + target(r) + ":" + r.onValue() + ":" + r.offValue()); }
    private String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception ex) { throw bad("Неверная команда"); } }
    private Object read(String value) { try { return json.readValue(value, Object.class); } catch (Exception ex) { throw bad("Неверная команда"); } }
    private boolean same(Object a, Object b) { return a != null && b != null && String.valueOf(a).equalsIgnoreCase(String.valueOf(b)); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private AuthenticatedUser principal(Integer owner) { if (users.isDemoOwner(owner)) return new AuthenticatedUser(owner, "demo"); var u = users.getAuthUser(owner); return u == null || !u.active() ? null : new AuthenticatedUser(owner, u.role()); }
    private void require(AuthenticatedUser user) { if (user == null || user.id() == null) throw new DomainException("unauthorized", "Войдите в аккаунт"); }
    private void lock(AuthenticatedUser user) { require(user); if (user.isDemo()) users.lockDemoOwner(user.id()); else users.lockAccount(user.id()); }
    private static DomainException bad(String message) { return new DomainException("unprocessable", message); }
    private static DomainException conflict() { return new DomainException("conflict", "Команда изменилась. Обновите страницу"); }
}
