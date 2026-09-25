package ru.growerhub.backend.zigbee.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.common.config.zigbee.ZigbeeWateringSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandGateway;
import ru.growerhub.backend.zigbee.contract.ZigbeeWateringData;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCoordinatorEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCoordinatorRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotRepository;

@Service
public class ZigbeeWateringService {
    private final ZigbeeCoordinatorRepository coordinators;
    private final ZigbeeDeviceSnapshotRepository devices;
    private final ZigbeeCommandGateway commands;
    private final ZigbeeWateringSettings settings;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ZigbeeWateringService(ZigbeeCoordinatorRepository coordinators, ZigbeeDeviceSnapshotRepository devices,
            ZigbeeCommandGateway commands, ZigbeeWateringSettings settings, ObjectMapper mapper, Clock clock) {
        this.coordinators = coordinators;
        this.devices = devices;
        this.commands = commands;
        this.settings = settings;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<ZigbeeWateringData.Capability> capabilities(Integer coordinatorId, String ieee) {
        if (coordinatorId == null || ieee == null) return List.of();
        ZigbeeCoordinatorEntity coordinator = coordinators.findByIdAndArchivedAtIsNull(coordinatorId).orElse(null);
        if (coordinator == null) return List.of();
        ZigbeeDeviceSnapshotEntity device = device(new ZigbeeWateringData.Target(coordinatorId, ieee, null), false);
        List<ZigbeeWateringData.Capability> result = new ArrayList<>();
        for (JsonNode feature : features(definition(device).path("exposes"))) {
            if (!"binary".equals(feature.path("type").asText()) || (feature.path("access").asInt() & 3) != 3) continue;
            String property = feature.path("property").asText();
            if (!property.isBlank()) result.add(capability(coordinator, device, property));
        }
        return List.copyOf(result);
    }

    public ZigbeeWateringData.Executor resolve(ZigbeeWateringData.Target target, AuthenticatedUser user, boolean lock) {
        if (target == null || target.coordinatorId() == null || target.property() == null || target.property().isBlank()) throw missing();
        ZigbeeCoordinatorEntity coordinator = lock ? coordinators.lockActiveById(target.coordinatorId()).orElseThrow(this::missing)
                : coordinator(target.coordinatorId());
        requireOwner(coordinator, user);
        ZigbeeDeviceSnapshotEntity device = device(target, lock);
        var profile = profile(coordinator, device, target.property());
        return new ZigbeeWateringData.Executor(target, coordinator.getUserId(), coordinator.isSimulated(),
                device.getFriendlyName(), profile == null ? null : profile.onValue(),
                profile == null ? null : profile.offValue(), capability(coordinator, device, target.property()));
    }

    public ZigbeeWateringData.State state(ZigbeeWateringData.Target target, String onValue, String offValue) {
        coordinator(target.coordinatorId());
        ZigbeeDeviceSnapshotEntity device = device(target, false);
        LocalDateTime observedAt = device.getLastLiveStateAt();
        LocalDateTime now = LocalDateTime.now(clock);
        boolean fresh = observedAt != null && !observedAt.isAfter(now)
                && !observedAt.isBefore(now.minusSeconds(settings.stateFreshnessSeconds()));
        JsonNode value = json(device.getLiveStateJson()).path(target.property());
        Boolean running = value.isValueNode() && Objects.equals(value.asText(), onValue) ? Boolean.TRUE
                : value.isValueNode() && Objects.equals(value.asText(), offValue) ? Boolean.FALSE : null;
        return new ZigbeeWateringData.State(fresh && !Boolean.TRUE.equals(device.getDisabled())
                && !"offline".equalsIgnoreCase(device.getAvailability()), running, observedAt);
    }

    public void start(ZigbeeWateringData.Target target, int durationSeconds, AuthenticatedUser user) {
        var executor = resolve(target, user, true);
        if (!executor.capability().ready()) throw new DomainException("bad_request", executor.capability().reason());
        ZigbeeCoordinatorEntity coordinator = coordinator(target.coordinatorId());
        ZigbeeDeviceSnapshotEntity device = device(target, false);
        var profile = profile(coordinator, device, target.property());
        if (durationSeconds < 1 || durationSeconds > profile.maxDurationSeconds()
                || durationSeconds % profile.durationUnitSeconds() != 0) {
            throw new DomainException("bad_request", "Длительность не поддерживается локальным таймером клапана");
        }
        var state = state(target, executor.onValue(), executor.offValue());
        if (!state.online() || !Boolean.FALSE.equals(state.running())) {
            throw new DomainException("conflict", "Нужно свежее подтверждение закрытого клапана");
        }
        JsonNode timer = feature(definition(device), profile.durationProperty());
        double value = (double) durationSeconds / profile.durationUnitSeconds();
        if (!coordinator.isSimulated() && (value < timer.path("value_min").asDouble()
                || (timer.path("value_step").isNumber() && timer.path("value_step").asDouble() > 0
                    && Math.abs((value - timer.path("value_min").asDouble()) / timer.path("value_step").asDouble()
                        - Math.rint((value - timer.path("value_min").asDouble()) / timer.path("value_step").asDouble())) > 0.000001))) {
            throw new DomainException("bad_request", "Длительность не соответствует шагу таймера клапана");
        }
        commands.publishWateringStart(coordinator.getBaseTopic(), device.getFriendlyName(), Map.of(
                profile.durationProperty(), durationSeconds / profile.durationUnitSeconds(),
                target.property(), profile.onValue()));
    }

    public void stop(ZigbeeWateringData.Target target, String offValue, AuthenticatedUser user) {
        ZigbeeCoordinatorEntity coordinator = coordinator(target.coordinatorId());
        requireOwner(coordinator, user);
        ZigbeeDeviceSnapshotEntity device = device(target, true);
        // Ostanovka aktivnoj sessii razreshena i posle vyklyucheniya flaga ili otzyva dopuska.
        if (offValue == null || offValue.isBlank()) throw new DomainException("bad_request", "Нет команды закрытия клапана");
        commands.publishSet(coordinator.getBaseTopic(), device.getFriendlyName(), Map.of(target.property(), offValue));
    }

    public boolean simulated(Integer coordinatorId) { return coordinator(coordinatorId).isSimulated(); }

    public void requireGenericCommandAllowed(Integer coordinatorId, ZigbeeDeviceSnapshotEntity device, String property, Object value) {
        ZigbeeCoordinatorEntity coordinator = coordinators.findByIdAndArchivedAtIsNull(coordinatorId).orElse(null);
        if (coordinator == null) return;
        for (var capability : capabilities(coordinatorId, device.getIeeeAddress())) {
            var profile = profile(coordinator, device, capability.property());
            if (profile != null && (Objects.equals(property, profile.durationProperty())
                    || (Objects.equals(property, profile.stateProperty()) && !Objects.equals(String.valueOf(value), profile.offValue())))) {
                throw new DomainException("bad_request", "Запускайте клапан через полив с ограничением времени");
            }
        }
    }

    private ZigbeeWateringData.Capability capability(ZigbeeCoordinatorEntity coordinator,
            ZigbeeDeviceSnapshotEntity device, String property) {
        var profile = profile(coordinator, device, property);
        String reason = !settings.enabled() ? "Полив Zigbee пока выключен"
                : profile == null ? "Для этого клапана не проверено автономное закрытие по таймеру"
                : Boolean.TRUE.equals(device.getDisabled()) ? "Устройство выключено в Zigbee2MQTT" : null;
        return new ZigbeeWateringData.Capability(property, reason == null, reason,
                profile != null ? profile.maxDurationSeconds() : null, false, false);
    }

    private ZigbeeWateringSettings.VerifiedDevice profile(ZigbeeCoordinatorEntity coordinator,
            ZigbeeDeviceSnapshotEntity device, String property) {
        JsonNode definition = definition(device);
        if (coordinator.isSimulated() && "Demo valve".equals(definition.path("model").asText())
                && "state".equals(property)) {
            return new ZigbeeWateringSettings.VerifiedDevice(coordinator.getPublicId(), device.getIeeeAddress(),
                    null, "state", "watering_duration", "ON", "OFF", settings.simulatedMaxDurationSeconds(), 1,
                    null, "SIMULATED", null, true);
        }
        if (coordinator.isSimulated()) return null;
        for (var profile : settings.verifiedDevices()) {
            if (!Objects.equals(profile.coordinatorId(), coordinator.getPublicId())
                    || !Objects.equals(profile.ieeeAddress(), device.getIeeeAddress())
                    || !Objects.equals(profile.stateProperty(), property)
                    || profile.maxDurationSeconds() < 1 || profile.durationUnitSeconds() < 1
                    || profile.evidenceReference() == null || profile.evidenceReference().isBlank()
                    || profile.onValue() == null || profile.offValue() == null
                    || profile.onValue().equals(profile.offValue()) || property.equals(profile.durationProperty())
                    || !profile.duplicateCommandDoesNotExtendTimer()
                    || profile.softwareBuildId() == null || profile.softwareBuildId().isBlank()
                    || !profile.softwareBuildId().equals(json(device.getBridgeDeviceJson()).path("software_build_id").asText())) continue;
            try {
                if (OffsetDateTime.parse(profile.verifiedAt()).toInstant().isAfter(clock.instant())) continue;
                String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(definition.toString().getBytes(StandardCharsets.UTF_8)));
                if (!fingerprint.equals(profile.definitionSha256())) continue;
            } catch (Exception ex) { continue; }
            JsonNode state = feature(definition, property);
            JsonNode timer = feature(definition, profile.durationProperty());
            if (state == null || timer == null || !"binary".equals(state.path("type").asText())
                    || (state.path("access").asInt() & 3) != 3 || (timer.path("access").asInt() & 2) == 0
                    || !"numeric".equals(timer.path("type").asText())
                    || !state.path("value_on").isTextual() || !state.path("value_off").isTextual()
                    || !profile.onValue().equals(state.path("value_on").asText())
                    || !profile.offValue().equals(state.path("value_off").asText())) continue;
            double max = (double) profile.maxDurationSeconds() / profile.durationUnitSeconds();
            if (!timer.path("value_max").isNumber() || max > timer.path("value_max").asDouble()
                    || timer.path("value_min").asDouble() > 1) continue;
            return profile;
        }
        return null;
    }

    private ZigbeeCoordinatorEntity coordinator(Integer id) {
        if (id == null) throw missing();
        return coordinators.findByIdAndArchivedAtIsNull(id).orElseThrow(this::missing);
    }

    private ZigbeeDeviceSnapshotEntity device(ZigbeeWateringData.Target target, boolean lock) {
        if (target.ieeeAddress() == null || target.ieeeAddress().isBlank()) throw missing();
        var device = (lock ? devices.lockWateringDevice(target.coordinatorId(), target.ieeeAddress())
                : devices.findByCoordinatorIdAndIeeeAddress(target.coordinatorId(), target.ieeeAddress())).orElseThrow(this::missing);
        if (device.isCoordinator()) throw missing();
        return device;
    }

    private void requireOwner(ZigbeeCoordinatorEntity coordinator, AuthenticatedUser user) {
        if (user == null || (!user.isAdmin() && !Objects.equals(user.id(), coordinator.getUserId()))
                || (user.isDemo() && !coordinator.isSimulated())) throw missing();
    }

    private DomainException missing() { return new DomainException("not_found", "Исполнитель полива не найден"); }
    private JsonNode definition(ZigbeeDeviceSnapshotEntity device) { return json(device.getBridgeDeviceJson()).path("definition"); }
    private JsonNode json(String value) {
        try { return value == null ? mapper.createObjectNode() : mapper.readTree(value); }
        catch (Exception ex) { return mapper.createObjectNode(); }
    }
    private JsonNode feature(JsonNode definition, String property) {
        return features(definition.path("exposes")).stream()
                .filter(node -> Objects.equals(property, node.path("property").asText())).findFirst().orElse(null);
    }
    private List<JsonNode> features(JsonNode nodes) {
        List<JsonNode> result = new ArrayList<>();
        if (nodes.isArray()) for (JsonNode node : nodes) {
            if (node.has("property")) result.add(node);
            result.addAll(features(node.path("features")));
        }
        return result;
    }
}
