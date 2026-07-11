package ru.growerhub.backend.automation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.automation.contract.AutomationData;
import ru.growerhub.backend.automation.jpa.AutomationActionLogEntity;
import ru.growerhub.backend.automation.jpa.AutomationActionLogRepository;
import ru.growerhub.backend.automation.jpa.AutomationBoxEntity;
import ru.growerhub.backend.automation.jpa.AutomationBoxPlantEntity;
import ru.growerhub.backend.automation.jpa.AutomationBoxPlantRepository;
import ru.growerhub.backend.automation.jpa.AutomationBoxRepository;
import ru.growerhub.backend.automation.jpa.AutomationResourceBindingEntity;
import ru.growerhub.backend.automation.jpa.AutomationResourceBindingRepository;
import ru.growerhub.backend.automation.jpa.AutomationRoomEntity;
import ru.growerhub.backend.automation.jpa.AutomationRoomRepository;
import ru.growerhub.backend.automation.jpa.AutomationScenarioConfigEntity;
import ru.growerhub.backend.automation.jpa.AutomationScenarioConfigRepository;
import ru.growerhub.backend.automation.jpa.AutomationScenarioStateEntity;
import ru.growerhub.backend.automation.jpa.AutomationScenarioStateRepository;
import ru.growerhub.backend.common.config.AutomationSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.plant.contract.AdminPlantInfo;
import ru.growerhub.backend.pump.PumpFacade;
import ru.growerhub.backend.pump.contract.PumpSessionData;
import ru.growerhub.backend.pump.contract.PumpStartResult;
import ru.growerhub.backend.pump.contract.PumpView;
import ru.growerhub.backend.sensor.SensorFacade;
import ru.growerhub.backend.sensor.contract.SensorView;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeDeviceData;
import ru.growerhub.backend.zigbee.contract.ZigbeeFeatureData;

@Service
@Transactional
public class AutomationFacade {
    private static final Logger log = LoggerFactory.getLogger(AutomationFacade.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final AuthenticatedUser SYSTEM_ADMIN = new AuthenticatedUser(0, "admin");
    private static final List<String> ROOM_SCENARIOS = List.of(AutomationData.SCENARIO_ROOM_CLIMATE);
    private static final List<String> BOX_SCENARIOS = List.of(
            AutomationData.SCENARIO_BOX_CLIMATE,
            AutomationData.SCENARIO_LIGHT_SCHEDULE,
            AutomationData.SCENARIO_WATERING
    );
    private static final String STOP_MODE_FIXED_DURATION = "fixed_duration";
    private static final String STOP_MODE_UNTIL_DRAIN = "until_drain";
    private static final String RUNTIME_WATERING_ACTIVE = "watering_session_active";
    private static final List<String> LEGACY_WATERING_RUNTIME_KEYS = List.of(
            RUNTIME_WATERING_ACTIVE,
            "watering_phase",
            "session_started_at",
            "phase_started_at",
            "run_accumulated_s",
            "current_run_duration_s"
    );

    private final AutomationRoomRepository roomRepository;
    private final AutomationBoxRepository boxRepository;
    private final AutomationBoxPlantRepository boxPlantRepository;
    private final AutomationResourceBindingRepository resourceRepository;
    private final AutomationScenarioConfigRepository configRepository;
    private final AutomationScenarioStateRepository stateRepository;
    private final AutomationActionLogRepository actionLogRepository;
    private final DeviceFacade deviceFacade;
    private final SensorFacade sensorFacade;
    private final PumpFacade pumpFacade;
    private final PlantFacade plantFacade;
    private final ZigbeeFacade zigbeeFacade;
    private final AutomationSettings settings;
    private final ObjectMapper objectMapper;

    public AutomationFacade(
            AutomationRoomRepository roomRepository,
            AutomationBoxRepository boxRepository,
            AutomationBoxPlantRepository boxPlantRepository,
            AutomationResourceBindingRepository resourceRepository,
            AutomationScenarioConfigRepository configRepository,
            AutomationScenarioStateRepository stateRepository,
            AutomationActionLogRepository actionLogRepository,
            DeviceFacade deviceFacade,
            SensorFacade sensorFacade,
            PumpFacade pumpFacade,
            PlantFacade plantFacade,
            ZigbeeFacade zigbeeFacade,
            AutomationSettings settings,
            ObjectMapper objectMapper
    ) {
        this.roomRepository = roomRepository;
        this.boxRepository = boxRepository;
        this.boxPlantRepository = boxPlantRepository;
        this.resourceRepository = resourceRepository;
        this.configRepository = configRepository;
        this.stateRepository = stateRepository;
        this.actionLogRepository = actionLogRepository;
        this.deviceFacade = deviceFacade;
        this.sensorFacade = sensorFacade;
        this.pumpFacade = pumpFacade;
        this.plantFacade = plantFacade;
        this.zigbeeFacade = zigbeeFacade;
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AutomationData.Overview getOverview() {
        Catalog catalog = buildCatalog();
        List<AutomationRoomEntity> rooms = roomRepository.findAllByOrderByNameAscIdAsc();
        List<AutomationBoxEntity> boxes = boxRepository.findAllByOrderByNameAscIdAsc();
        Map<Integer, List<AutomationBoxEntity>> boxesByRoom = boxes.stream()
                .collect(Collectors.groupingBy(AutomationBoxEntity::getRoomId));
        Map<Integer, List<AutomationBoxPlantEntity>> plantsByBox = groupPlants(boxes);
        Map<String, List<AutomationResourceBindingEntity>> resources = groupResources(rooms, boxes);
        Map<String, List<AutomationScenarioConfigEntity>> configs = groupConfigs(rooms, boxes);
        Map<String, List<AutomationScenarioStateEntity>> states = groupStates(rooms, boxes);

        List<AutomationData.Room> roomData = new ArrayList<>();
        for (AutomationRoomEntity room : rooms) {
            List<AutomationData.Box> boxData = boxesByRoom.getOrDefault(room.getId(), List.of()).stream()
                    .map(box -> toBoxData(box, plantsByBox, resources, configs, states, catalog, room.getId()))
                    .toList();
            roomData.add(toRoomData(room, boxData, resources, configs, states, catalog));
        }
        return new AutomationData.Overview(
                roomData,
                catalog.toData(),
                actionLogRepository.findTop20ByOrderByCreatedAtDesc().stream().map(this::toActionLogData).toList(),
                new AutomationData.Settings(
                        settings.getTimezone(),
                        settings.getStaleSensorMinutes(),
                        settings.getManualOverrideMinutes(),
                        settings.getResourceOfflineMinutes()
                )
        );
    }

    public AutomationData.Overview createRoom(AutomationData.SaveRoomRequest request) {
        LocalDateTime now = nowUtc();
        AutomationRoomEntity room = AutomationRoomEntity.create(requiredName(request != null ? request.name() : null), now);
        if (request != null && request.enabled() != null) {
            room.setEnabled(request.enabled());
        }
        roomRepository.save(room);
        return getOverview();
    }

    public AutomationData.Overview updateRoom(Integer roomId, AutomationData.SaveRoomRequest request) {
        AutomationRoomEntity room = requireRoom(roomId);
        if (request != null && request.name() != null) {
            room.setName(requiredName(request.name()));
        }
        if (request != null && request.enabled() != null) {
            room.setEnabled(request.enabled());
        }
        room.setUpdatedAt(nowUtc());
        roomRepository.save(room);
        return getOverview();
    }

    public void deleteRoom(Integer roomId) {
        AutomationRoomEntity room = requireRoom(roomId);
        List<AutomationBoxEntity> boxes = boxRepository.findAllByRoom_IdOrderByNameAscIdAsc(room.getId());
        Set<Integer> pumpIds = boxes.stream()
                .map(AutomationBoxEntity::getId)
                .map(this::automationPumpId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (AutomationBoxEntity box : boxes) {
            deleteBoxResources(box.getId());
        }
        deleteRoomResources(room.getId());
        roomRepository.delete(room);
        roomRepository.flush();
        pumpIds.forEach(this::syncAutomationPump);
    }

    public AutomationData.Overview createBox(Integer roomId, AutomationData.SaveBoxRequest request) {
        AutomationRoomEntity room = requireRoom(roomId);
        LocalDateTime now = nowUtc();
        AutomationBoxEntity box = AutomationBoxEntity.create(room, requiredName(request != null ? request.name() : null), now);
        if (request != null && request.enabled() != null) {
            box.setEnabled(request.enabled());
        }
        boxRepository.save(box);
        return getOverview();
    }

    public AutomationData.Overview updateBox(Integer boxId, AutomationData.SaveBoxRequest request) {
        AutomationBoxEntity box = requireBox(boxId);
        if (request != null && request.name() != null) {
            box.setName(requiredName(request.name()));
        }
        if (request != null && request.enabled() != null) {
            box.setEnabled(request.enabled());
        }
        box.setUpdatedAt(nowUtc());
        boxRepository.save(box);
        return getOverview();
    }

    public void deleteBox(Integer boxId) {
        AutomationBoxEntity box = requireBox(boxId);
        Integer pumpId = automationPumpId(box.getId());
        deleteBoxResources(box.getId());
        boxRepository.delete(box);
        boxRepository.flush();
        if (pumpId != null) {
            syncAutomationPump(pumpId);
        }
    }

    public AutomationData.Overview replaceBoxPlants(Integer boxId, AutomationData.SavePlantsRequest request) {
        AutomationBoxEntity box = requireBox(boxId);
        LocalDateTime now = nowUtc();
        List<AutomationData.BoxPlantRequest> items = boxPlantRequests(request);
        List<Integer> plantIds = items.stream().map(AutomationData.BoxPlantRequest::plantId).toList();
        Set<Integer> uniquePlantIds = new HashSet<>(plantIds);
        if (uniquePlantIds.size() != plantIds.size()) {
            throw new DomainException("bad_request", "plant_ids dolzhny byt' unikalnymi");
        }
        for (AutomationData.BoxPlantRequest item : items) {
            Integer plantId = item.plantId();
            if (plantId == null) {
                throw new DomainException("bad_request", "plant_id obyazatelen");
            }
            if (item.rateMlPerHour() != null && item.rateMlPerHour() <= 0) {
                throw new DomainException("bad_request", "rate_ml_per_hour dolzhen byt' bol'she nulya");
            }
            if (plantFacade.getPlantInfoById(plantId) == null) {
                throw new DomainException("not_found", "rastenie ne naideno");
            }
        }
        boxPlantRepository.deleteAllByBox_Id(box.getId());
        List<AutomationBoxPlantEntity> rows = items.stream()
                .map(item -> AutomationBoxPlantEntity.create(box, item.plantId(), item.rateMlPerHour(), now))
                .toList();
        boxPlantRepository.saveAll(rows);
        boxPlantRepository.flush();
        Integer pumpId = automationPumpId(box.getId());
        if (pumpId != null) {
            syncAutomationPump(pumpId);
        }
        return getOverview();
    }

    private List<AutomationData.BoxPlantRequest> boxPlantRequests(AutomationData.SavePlantsRequest request) {
        if (request == null) {
            return List.of();
        }
        if (request.items() != null) {
            if (request.items().stream().anyMatch(Objects::isNull)) {
                throw new DomainException("bad_request", "items ne dolzhny soderzhat' null");
            }
            return request.items();
        }
        if (request.plantIds() == null) {
            return List.of();
        }
        return request.plantIds().stream()
                .map(plantId -> new AutomationData.BoxPlantRequest(plantId, null))
                .toList();
    }

    public AutomationData.Overview replaceRoomResources(Integer roomId, AutomationData.SaveResourcesRequest request) {
        requireRoom(roomId);
        replaceResources(AutomationData.SCOPE_ROOM, roomId, request);
        return getOverview();
    }

    public AutomationData.Overview replaceBoxResources(Integer boxId, AutomationData.SaveResourcesRequest request) {
        requireBox(boxId);
        Integer previousPumpId = automationPumpId(boxId);
        replaceResources(AutomationData.SCOPE_BOX, boxId, request);
        Integer nextPumpId = automationPumpId(boxId);
        Set<Integer> affectedPumpIds = new HashSet<>();
        if (previousPumpId != null) {
            affectedPumpIds.add(previousPumpId);
        }
        if (nextPumpId != null) {
            affectedPumpIds.add(nextPumpId);
        }
        affectedPumpIds.forEach(this::syncAutomationPump);
        return getOverview();
    }

    @Transactional(readOnly = true)
    public AutomationData.ManualWateringOverview getManualWateringOverview() {
        Catalog catalog = buildCatalog();
        WateringTopology topology = buildWateringTopology(catalog);
        Map<Integer, PumpSessionData.View> sessionsByPump = new HashMap<>();
        for (Integer pumpId : catalog.pumpsById.keySet()) {
            PumpSessionData.View session = pumpFacade.currentSession(pumpId);
            if (session != null) {
                sessionsByPump.put(pumpId, session);
            }
        }
        List<PumpSessionData.Probe> activeProbes = pumpFacade.listActiveSessionProbes();

        List<AutomationData.ManualWateringPump> pumps = new ArrayList<>();
        for (AutomationData.NativeDevice device : catalog.nativeDevices) {
            for (AutomationData.NativePump pump : device.pumps()) {
                PumpSessionData.View currentSession = sessionsByPump.get(pump.id());
                List<AutomationData.ManualWateringBox> boxes = topology.boxesByPump.getOrDefault(pump.id(), List.of());
                List<String> blockReasons = manualWateringBlockReasons(
                        pump,
                        device,
                        boxes,
                        currentSession,
                        activeProbes
                );
                boolean untilLeak = boxes.stream()
                        .flatMap(box -> box.leakSensors().stream())
                        .anyMatch(sensor -> Boolean.TRUE.equals(sensor.available()));
                pumps.add(new AutomationData.ManualWateringPump(
                        pump.id(),
                        pump.deviceId(),
                        device.deviceId(),
                        pump.channel(),
                        pump.label(),
                        pump.isOnline(),
                        pump.isRunning(),
                        new AutomationData.ManualWateringCapabilities(
                                blockReasons.isEmpty(),
                                blockReasons,
                                true,
                                untilLeak,
                                currentSession != null
                        ),
                        boxes,
                        currentSession
                ));
            }
        }
        return new AutomationData.ManualWateringOverview(pumpFacade.sessionDefaults(), pumps);
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public PumpSessionData.View startManualWatering(
            Integer pumpId,
            AutomationData.ManualWateringStartRequest request,
            AuthenticatedUser user
    ) {
        Catalog catalog = buildCatalog();
        WateringTopology topology = buildWateringTopology(catalog);
        AutomationData.ManualWateringStartRequest safeRequest = request != null
                ? request
                : new AutomationData.ManualWateringStartRequest(null, null, null, null, null, null);
        return pumpFacade.startSession(new PumpSessionData.Start(
                pumpId,
                PumpSessionData.SOURCE_ADMIN_MANUAL,
                safeRequest.mode(),
                safeRequest.durationS(),
                safeRequest.maxActiveDurationS(),
                safeRequest.pulseEnabled(),
                safeRequest.pulseRunS(),
                safeRequest.pulsePauseS(),
                topology.targetsByPump.getOrDefault(pumpId, List.of()),
                null,
                null
        ), user);
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public PumpStartResult startUserManualWatering(
            Integer pumpId,
            AutomationData.UserManualWateringStartRequest request,
            AuthenticatedUser user
    ) {
        AutomationData.UserManualWateringStartRequest safeRequest = request != null
                ? request
                : new AutomationData.UserManualWateringStartRequest(null, null, null, null);
        if (safeRequest.durationS() == null && safeRequest.waterVolumeL() == null) {
            throw new DomainException(
                    "bad_request",
                    "ukazhite water_volume_l ili duration_s dlya starta poliva"
            );
        }
        Catalog catalog = buildCatalog();
        List<PumpSessionData.BoxTarget> targets = buildWateringTopology(catalog)
                .targetsByPump
                .getOrDefault(pumpId, List.of());
        if (targets.isEmpty()) {
            return pumpFacade.start(
                    pumpId,
                    new PumpFacade.PumpWateringRequest(
                            safeRequest.durationS(),
                            safeRequest.waterVolumeL(),
                            safeRequest.ph(),
                            safeRequest.fertilizersPerLiter()
                    ),
                    user
            );
        }
        PumpSessionData.View session = pumpFacade.startSession(new PumpSessionData.Start(
                pumpId,
                PumpSessionData.SOURCE_USER_MANUAL,
                PumpSessionData.MODE_TIMED,
                safeRequest.durationS(),
                null,
                false,
                null,
                null,
                targets,
                safeRequest.waterVolumeL(),
                safeRequest.ph(),
                safeRequest.fertilizersPerLiter()
        ), user);
        return new PumpStartResult(session.correlationId());
    }

    public PumpSessionData.View stopManualWatering(Integer pumpId, AuthenticatedUser user) {
        return pumpFacade.stopSession(pumpId, user);
    }

    @Transactional(readOnly = true)
    public PumpSessionData.Page getManualWateringSessions(Integer pumpId, int limit, Long beforeId) {
        return pumpFacade.listSessions(pumpId, limit, beforeId);
    }

    @Transactional(readOnly = true)
    public PumpSessionData.BoxStatistics getManualWateringBoxStatistics(
            Integer boxId,
            String range,
            int limit,
            Long beforeId
    ) {
        requireBox(boxId);
        return pumpFacade.boxStatistics(boxId, range, limit, beforeId);
    }

    public AutomationData.Overview replaceRoomScenarios(Integer roomId, AutomationData.SaveScenariosRequest request) {
        requireRoom(roomId);
        replaceScenarios(AutomationData.SCOPE_ROOM, roomId, ROOM_SCENARIOS, request);
        return getOverview();
    }

    public AutomationData.Overview replaceBoxScenarios(Integer boxId, AutomationData.SaveScenariosRequest request) {
        requireBox(boxId);
        replaceScenarios(AutomationData.SCOPE_BOX, boxId, BOX_SCENARIOS, request);
        return getOverview();
    }

    public void evaluateAll() {
        LocalDateTime now = nowUtc();
        Catalog catalog = buildCatalog();
        List<AutomationRoomEntity> rooms = roomRepository.findAllByOrderByNameAscIdAsc();
        List<AutomationBoxEntity> boxes = boxRepository.findAllByOrderByNameAscIdAsc();
        Map<Integer, AutomationRoomEntity> roomsById = rooms.stream()
                .collect(Collectors.toMap(AutomationRoomEntity::getId, Function.identity()));

        for (AutomationBoxEntity box : boxes) {
            AutomationRoomEntity room = roomsById.get(box.getRoomId());
            evaluateBoxClimate(box, room, catalog, now);
            evaluateLightSchedule(box, catalog, now);
            evaluateWatering(box, catalog, now);
        }
        for (AutomationRoomEntity room : rooms) {
            evaluateRoomClimate(room, boxes, catalog, now);
        }
    }

    @Transactional(readOnly = true)
    public void evaluateActiveWateringSessions() {
        LocalDateTime now = nowUtc();
        List<PumpSessionData.Probe> probes = pumpFacade.listActiveSessionProbes();
        if (probes.isEmpty()) {
            return;
        }
        Catalog catalog = buildCatalog();
        Map<String, AutomationData.NativeDevice> devicesByKey = catalog.nativeDevices.stream()
                .collect(Collectors.toMap(AutomationData.NativeDevice::deviceId, Function.identity()));
        for (PumpSessionData.Probe probe : probes) {
            Long sessionId = probe != null ? probe.sessionId() : null;
            try {
                AutomationData.NativeDevice device = devicesByKey.get(probe.deviceKey());
                AutomationData.NativePump pump = catalog.pumpsById.get(probe.pumpId());
                List<PumpSessionData.LeakState> leakStates = probe.leakSensors().stream()
                        .map(sensor -> currentLeakState(sensor, catalog))
                        .toList();
                pumpFacade.advanceSession(
                        sessionId,
                        new PumpSessionData.LeakProbe(
                                device != null ? device.isOnline() : Boolean.FALSE,
                                pump != null ? pump.isRunning() : null,
                                pump != null
                                        ? pump.lastSeenAt()
                                        : device != null ? device.lastSeenAt() : null,
                                leakStates
                        ),
                        now
                );
            } catch (RuntimeException ex) {
                log.warn(
                        "Obrabotka sessii poliva {} zavershilas oshibkoj: {}",
                        sessionId,
                        ex.getMessage(),
                        ex
                );
            }
        }
    }

    private void replaceResources(String scopeType, Integer scopeId, AutomationData.SaveResourcesRequest request) {
        LocalDateTime now = nowUtc();
        Catalog catalog = buildCatalog();
        List<AutomationData.ResourceBindingRequest> resources =
                request != null && request.resources() != null ? request.resources() : List.of();
        Set<String> seenRoles = new HashSet<>();
        List<AutomationResourceBindingEntity> next = new ArrayList<>();
        for (AutomationData.ResourceBindingRequest item : resources) {
            String role = normalizeRequired(item.role(), "role");
            if (!seenRoles.add(role)) {
                throw new DomainException("bad_request", "rol' resursa dolzhna byt' unikal'noj");
            }
            validateRoleForScope(scopeType, role);
            validateResource(role, item, catalog);
            AutomationResourceBindingEntity entity = AutomationResourceBindingEntity.create(scopeType, scopeId, role, now);
            entity.setSourceType(normalizeRequired(item.sourceType(), "source_type"));
            entity.setNativeSensorId(item.nativeSensorId());
            entity.setNativePumpId(item.nativePumpId());
            entity.setZigbeeIeeeAddress(blankToNull(item.zigbeeIeeeAddress()));
            entity.setZigbeeProperty(defaultProperty(role, item.zigbeeProperty()));
            entity.setCommandProperty(defaultCommandProperty(role, item.commandProperty()));
            entity.setOnValue(defaultOnValue(item.onValue()));
            entity.setOffValue(defaultOffValue(item.offValue()));
            next.add(entity);
        }
        validateResourcesAgainstScenarios(scopeType, scopeId, next, catalog);
        resourceRepository.deleteAllByScopeTypeAndScopeId(scopeType, scopeId);
        resourceRepository.flush();
        resourceRepository.saveAll(next);
    }

    private void replaceScenarios(
            String scopeType,
            Integer scopeId,
            List<String> allowedTypes,
            AutomationData.SaveScenariosRequest request
    ) {
        LocalDateTime now = nowUtc();
        List<AutomationData.ScenarioConfigRequest> scenarios =
                request != null && request.scenarios() != null ? request.scenarios() : List.of();
        Set<String> seen = new HashSet<>();
        for (AutomationData.ScenarioConfigRequest item : scenarios) {
            String scenarioType = normalizeRequired(item.scenarioType(), "scenario_type");
            if (!allowedTypes.contains(scenarioType)) {
                throw new DomainException("bad_request", "scenario_type nedostupen dlya etogo scope");
            }
            if (!seen.add(scenarioType)) {
                throw new DomainException("bad_request", "scenario_type dolzhen byt' unikal'nym");
            }
            AutomationScenarioConfigEntity entity = configRepository
                    .findByScopeTypeAndScopeIdAndScenarioType(scopeType, scopeId, scenarioType)
                    .orElseGet(() -> AutomationScenarioConfigEntity.create(scopeType, scopeId, scenarioType, now));
            Map<String, Object> mergedConfig = mergeDefaults(scenarioType, item.config());
            validateScenarioConfig(scopeType, scopeId, scenarioType, Boolean.TRUE.equals(item.enabled()), mergedConfig);
            entity.setEnabled(Boolean.TRUE.equals(item.enabled()));
            entity.setConfigJson(writeJson(mergedConfig));
            entity.setUpdatedAt(now);
            configRepository.save(entity);
        }
    }

    private void validateScenarioConfig(
            String scopeType,
            Integer scopeId,
            String scenarioType,
            boolean enabled,
            Map<String, Object> cfg
    ) {
        if (!AutomationData.SCENARIO_WATERING.equals(scenarioType)) {
            return;
        }
        validateWateringConfig(scopeType, scopeId, enabled, cfg);
    }

    private void validateWateringConfig(
            String scopeType,
            Integer scopeId,
            boolean enabled,
            Map<String, Object> cfg
    ) {
        if (!AutomationData.SCOPE_BOX.equals(scopeType)) {
            return;
        }
        String stopMode = String.valueOf(cfg.getOrDefault("stop_mode", STOP_MODE_FIXED_DURATION));
        if (!STOP_MODE_FIXED_DURATION.equals(stopMode) && !STOP_MODE_UNTIL_DRAIN.equals(stopMode)) {
            throw new DomainException("bad_request", "nekorrektnyj stop_mode");
        }
        if (!STOP_MODE_UNTIL_DRAIN.equals(stopMode)) {
            return;
        }
        if (number(cfg.get("max_run_minutes"), 0.0) <= 0.0) {
            throw new DomainException("bad_request", "max_run_minutes obyazatelen dlya until_drain");
        }
        if (number(cfg.get("pulse_run_minutes"), 3.0) <= 0.0 || number(cfg.get("pulse_pause_minutes"), 5.0) <= 0.0) {
            throw new DomainException("bad_request", "intervaly impulsnogo poliva dolzhny byt' bol'she nulya");
        }
        Catalog catalog = buildCatalog();
        Integer pumpId = automationPumpId(scopeId);
        if (pumpId == null || !hasConfiguredLeakSensorForPump(pumpId, null, catalog)) {
            throw new DomainException("bad_request", "dlya until_drain nuzhen LEAK_SENSOR");
        }
    }

    private void validateResourcesAgainstScenarios(
            String scopeType,
            Integer scopeId,
            List<AutomationResourceBindingEntity> nextResources,
            Catalog catalog
    ) {
        if (!AutomationData.SCOPE_BOX.equals(scopeType)) {
            return;
        }
        AutomationScenarioConfigEntity config = configFor(scopeType, scopeId, AutomationData.SCENARIO_WATERING);
        if (config == null || !config.isEnabled()) {
            return;
        }
        Map<String, Object> cfg = configMap(config, AutomationData.SCENARIO_WATERING);
        if (!isUntilDrain(cfg)) {
            return;
        }
        Integer pumpId = nextResources.stream()
                .filter(resource -> AutomationData.ROLE_WATER_PUMP.equals(resource.getRole()))
                .map(AutomationResourceBindingEntity::getNativePumpId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        boolean hasLeakSensor = nextResources.stream()
                .filter(resource -> AutomationData.ROLE_LEAK_SENSOR.equals(resource.getRole()))
                .map(resource -> resolveResourceStatus(resource, catalog))
                .anyMatch(ResourceStatus::ready);
        if (!hasLeakSensor && (pumpId == null || !hasConfiguredLeakSensorForPump(pumpId, scopeId, catalog))) {
            throw new DomainException("bad_request", "LEAK_SENSOR nel'zya ubrat' pri until_drain");
        }
    }

    private void evaluateBoxClimate(
            AutomationBoxEntity box,
            AutomationRoomEntity room,
            Catalog catalog,
            LocalDateTime now
    ) {
        AutomationScenarioConfigEntity config = configFor(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_BOX_CLIMATE);
        AutomationScenarioStateEntity state = stateFor(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_BOX_CLIMATE, now);
        AutomationData.Readiness readiness = boxClimateReadiness(box.getId(), room != null ? room.getId() : null, catalog);
        if (!box.isEnabled() || room == null || !room.isEnabled()) {
            markState(state, "disabled", "box ili pomeshchenie vyklyucheny", false, now);
            return;
        }
        if (config == null || !config.isEnabled()) {
            markState(state, "disabled", "scenario disabled", false, now);
            return;
        }
        if (!readiness.ready()) {
            markState(state, "unready", readiness.reason(), state.isAcRequestActive(), now);
            return;
        }

        AutomationResourceBindingEntity tempBinding = resource(AutomationData.SCOPE_BOX, box.getId(), AutomationData.ROLE_AIR_TEMPERATURE_SENSOR);
        AutomationResourceBindingEntity exhaustBinding = resource(AutomationData.SCOPE_BOX, box.getId(), AutomationData.ROLE_EXHAUST_SWITCH);
        SensorValue temperature = readSensorValue(tempBinding, catalog);
        if (temperature == null || temperature.value() == null || isStale(temperature.ts(), now)) {
            markState(state, "stale", "net aktual'noj temperatury boksa", state.isAcRequestActive(), now);
            return;
        }

        Map<String, Object> cfg = configMap(config, AutomationData.SCENARIO_BOX_CLIMATE);
        Map<String, Object> runtime = runtimeMap(state);
        double value = temperature.value();
        double max = number(cfg.get("max_c"), 28.0);
        double exhaustOffBelow = number(cfg.get("exhaust_off_below_c"), 27.0);
        double acAbove = number(cfg.get("ac_request_above_c"), 29.0);
        double acClear = number(cfg.get("ac_clear_below_c"), 27.0);
        Double previous = asDouble(runtime.get("last_temperature"));
        LocalDateTime previousAt = parseDateTime(runtime.get("last_temperature_at"));
        boolean risingForFiveMinutes = previous != null && previousAt != null
                && !previousAt.isAfter(now.minusMinutes(5))
                && value > previous;

        boolean exhaustShouldBeOn = value > max || (previous != null && value >= max - 0.5 && value > previous);
        boolean exhaustShouldBeOff = value < exhaustOffBelow;
        if (exhaustShouldBeOn) {
            sendSwitchIfNeeded(exhaustBinding, true, catalog, AutomationData.SCENARIO_BOX_CLIMATE,
                    AutomationData.SCOPE_BOX, box.getId(), "temperatura " + value + "C", now, null);
            runtime.put("exhaust_desired_state", "ON");
        } else if (exhaustShouldBeOff) {
            sendSwitchIfNeeded(exhaustBinding, false, catalog, AutomationData.SCENARIO_BOX_CLIMATE,
                    AutomationData.SCOPE_BOX, box.getId(), "temperatura " + value + "C", now, null);
            runtime.put("exhaust_desired_state", "OFF");
        }

        boolean acRequest = state.isAcRequestActive();
        if ((Boolean.TRUE.equals(exhaustShouldBeOn) || "ON".equals(runtime.get("exhaust_desired_state")))
                && (value > acAbove || risingForFiveMinutes)) {
            acRequest = true;
        }
        if (value <= acClear) {
            acRequest = false;
        }

        runtime.put("last_temperature", value);
        runtime.put("last_temperature_at", now.toString());
        state.setRuntimeJson(writeJson(runtime));
        markState(state, "active", null, acRequest, now);
    }

    private void evaluateRoomClimate(
            AutomationRoomEntity room,
            List<AutomationBoxEntity> allBoxes,
            Catalog catalog,
            LocalDateTime now
    ) {
        AutomationScenarioConfigEntity config = configFor(AutomationData.SCOPE_ROOM, room.getId(), AutomationData.SCENARIO_ROOM_CLIMATE);
        AutomationScenarioStateEntity state = stateFor(AutomationData.SCOPE_ROOM, room.getId(), AutomationData.SCENARIO_ROOM_CLIMATE, now);
        AutomationData.Readiness readiness = roomClimateReadiness(room.getId(), allBoxes, catalog);
        if (!room.isEnabled()) {
            markState(state, "disabled", "pomeshchenie vyklyucheno", false, now);
            return;
        }
        if (config == null || !config.isEnabled()) {
            markState(state, "disabled", "scenario disabled", false, now);
            return;
        }
        if (!readiness.ready()) {
            markState(state, "unready", readiness.reason(), false, now);
            return;
        }

        boolean hasRequest = allBoxes.stream()
                .filter(box -> Objects.equals(box.getRoomId(), room.getId()))
                .map(box -> stateRepository.findByScopeTypeAndScopeIdAndScenarioType(
                        AutomationData.SCOPE_BOX,
                        box.getId(),
                        AutomationData.SCENARIO_BOX_CLIMATE
                ).orElse(null))
                .anyMatch(boxState -> boxState != null && boxState.isAcRequestActive());

        Map<String, Object> cfg = configMap(config, AutomationData.SCENARIO_ROOM_CLIMATE);
        Map<String, Object> runtime = runtimeMap(state);
        int offDelayMinutes = integer(cfg.get("off_delay_minutes"), 5);
        int minToggleMinutes = integer(cfg.get("min_toggle_minutes"), 5);
        if (hasRequest) {
            runtime.put("last_request_at", now.toString());
        }
        LocalDateTime lastAction = state.getLastActionAt();
        boolean toggleAllowed = lastAction == null || !lastAction.isAfter(now.minusMinutes(minToggleMinutes));
        LocalDateTime lastRequestAt = parseDateTime(runtime.get("last_request_at"));
        AutomationResourceBindingEntity acBinding = resource(AutomationData.SCOPE_ROOM, room.getId(), AutomationData.ROLE_AC_SWITCH);

        if (hasRequest && toggleAllowed) {
            sendSwitchIfNeeded(acBinding, true, catalog, AutomationData.SCENARIO_ROOM_CLIMATE,
                    AutomationData.SCOPE_ROOM, room.getId(), "est' zapros kondicionera ot boksa", now, null);
        } else if (!hasRequest
                && lastRequestAt != null
                && !lastRequestAt.isAfter(now.minusMinutes(offDelayMinutes))
                && toggleAllowed) {
            sendSwitchIfNeeded(acBinding, false, catalog, AutomationData.SCENARIO_ROOM_CLIMATE,
                    AutomationData.SCOPE_ROOM, room.getId(), "zaprosov kondicionera net", now, null);
        }
        state.setRuntimeJson(writeJson(runtime));
        markState(state, "active", null, false, now);
    }

    private void evaluateLightSchedule(AutomationBoxEntity box, Catalog catalog, LocalDateTime now) {
        AutomationScenarioConfigEntity config = configFor(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_LIGHT_SCHEDULE);
        AutomationScenarioStateEntity state = stateFor(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_LIGHT_SCHEDULE, now);
        AutomationData.Readiness readiness = lightReadiness(box.getId(), catalog);
        if (!box.isEnabled()) {
            markState(state, "disabled", "box vyklyuchen", false, now);
            return;
        }
        if (config == null || !config.isEnabled()) {
            markState(state, "disabled", "scenario disabled", false, now);
            return;
        }
        if (!readiness.ready()) {
            markState(state, "unready", readiness.reason(), false, now);
            return;
        }

        Map<String, Object> cfg = configMap(config, AutomationData.SCENARIO_LIGHT_SCHEDULE);
        boolean shouldBeOn = isLightScheduleActive(cfg, now);
        AutomationResourceBindingEntity lightBinding = resource(AutomationData.SCOPE_BOX, box.getId(), AutomationData.ROLE_LIGHT_SWITCH);
        sendSwitchIfNeeded(lightBinding, shouldBeOn, catalog, AutomationData.SCENARIO_LIGHT_SCHEDULE,
                AutomationData.SCOPE_BOX, box.getId(), shouldBeOn ? "raspisanie vklyucheniya" : "raspisanie vyklyucheniya", now, null);
        markState(state, "active", null, false, now);
    }

    private void evaluateWatering(AutomationBoxEntity box, Catalog catalog, LocalDateTime now) {
        AutomationScenarioConfigEntity config = configFor(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_WATERING);
        AutomationScenarioStateEntity state = stateFor(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_WATERING, now);
        AutomationData.Readiness readiness = wateringReadiness(box.getId(), catalog);
        if (hasActiveWateringSession(state)) {
            Map<String, Object> legacyRuntime = runtimeMap(state);
            clearWateringRuntime(legacyRuntime);
            state.setRuntimeJson(writeJson(legacyRuntime));
        }
        if (!box.isEnabled()) {
            markState(state, "disabled", "box vyklyuchen", false, now);
            return;
        }
        if (config == null || !config.isEnabled()) {
            markState(state, "disabled", "scenario disabled", false, now);
            return;
        }
        if (!readiness.ready()) {
            markState(state, "unready", readiness.reason(), false, now);
            return;
        }
        Map<String, Object> cfg = configMap(config, AutomationData.SCENARIO_WATERING);
        boolean untilDrain = isUntilDrain(cfg);
        AutomationResourceBindingEntity pumpBinding = resource(
                AutomationData.SCOPE_BOX,
                box.getId(),
                AutomationData.ROLE_WATER_PUMP
        );
        WateringTopology topology = buildWateringTopology(catalog);
        boolean hasAvailableLeakSensor = pumpBinding != null
                && topology.targetsByPump.getOrDefault(pumpBinding.getNativePumpId(), List.of()).stream()
                .flatMap(target -> target.leakSensors().stream())
                .anyMatch(sensor -> Boolean.TRUE.equals(sensor.available()));
        if (untilDrain && !hasAvailableLeakSensor) {
            markState(state, "unready", "nuzhen dostupnyj LEAK_SENSOR", false, now);
            return;
        }
        if (pumpBinding != null && pumpFacade.currentSession(pumpBinding.getNativePumpId()) != null) {
            markState(state, "active", null, false, now);
            return;
        }
        AutomationResourceBindingEntity soilBinding = resource(AutomationData.SCOPE_BOX, box.getId(), AutomationData.ROLE_SOIL_MOISTURE_SENSOR);
        SensorValue moisture = readSensorValue(soilBinding, catalog);
        if (moisture == null || moisture.value() == null || isStale(moisture.ts(), now)) {
            markState(state, "stale", "net aktual'noj vlazhnosti pochvy", false, now);
            return;
        }

        double threshold = number(cfg.get("soil_threshold_percent"), 40.0);
        int maxIntervalHours = integer(cfg.get("max_interval_hours"), 48);
        int runSeconds = Math.max(1, integer(cfg.get("run_seconds"), 30));
        int minIntervalHours = integer(cfg.get("min_interval_hours"), 6);
        int dailyMaxSeconds = Math.max(1, integer(cfg.get("daily_max_seconds"), 1200));
        int requestedRunSeconds = untilDrain ? untilDrainMaxRunSeconds(cfg) : runSeconds;
        PumpSessionData.View lastSession = pumpFacade.lastCompletedSessionForBox(box.getId());
        LocalDateTime lastFinishedAt = lastSession != null ? lastSession.finishedAt() : null;
        boolean tooDry = moisture.value() <= threshold;
        boolean intervalElapsed = lastFinishedAt == null || lastFinishedAt.isBefore(now.minusHours(maxIntervalHours));
        if (!tooDry && !intervalElapsed) {
            markState(state, "active", null, false, now);
            return;
        }
        if (lastFinishedAt != null && lastFinishedAt.isAfter(now.minusHours(minIntervalHours))) {
            logAction(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_WATERING, null,
                    "SKIP", "min interval mezhdu polivami", "skipped", null, now);
            markState(state, "limited", "min interval mezhdu polivami", false, now);
            return;
        }
        long usedToday = pumpFacade.boxStatistics(box.getId(), "day", 1, null).activeDurationS();
        if (usedToday + requestedRunSeconds > dailyMaxSeconds) {
            logAction(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_WATERING, null,
                    "SKIP", "dnevnoj limit poliva", "skipped", null, now);
            markState(state, "limited", "dnevnoj limit poliva", false, now);
            return;
        }

        String reason = "vlazhnost' " + moisture.value() + "%";
        startAutomationWatering(box, state, cfg, pumpBinding, reason, untilDrain, requestedRunSeconds, now, topology);
    }

    private void startAutomationWatering(
            AutomationBoxEntity box,
            AutomationScenarioStateEntity state,
            Map<String, Object> cfg,
            AutomationResourceBindingEntity pumpBinding,
            String reason,
            boolean untilDrain,
            int requestedRunSeconds,
            LocalDateTime now,
            WateringTopology topology
    ) {
        try {
            pumpFacade.startSession(new PumpSessionData.Start(
                    pumpBinding.getNativePumpId(),
                    PumpSessionData.SOURCE_AUTOMATION,
                    untilDrain ? PumpSessionData.MODE_UNTIL_LEAK : PumpSessionData.MODE_TIMED,
                    untilDrain ? null : requestedRunSeconds,
                    untilDrain ? requestedRunSeconds : null,
                    pulseEnabled(cfg),
                    pulseRunSeconds(cfg),
                    pulsePauseSeconds(cfg),
                    topology.targetsByPump.getOrDefault(pumpBinding.getNativePumpId(), List.of()),
                    null,
                    null
            ), SYSTEM_ADMIN);
            logAction(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_WATERING, pumpBinding,
                    "PUMP_START", reason, "published", requestedRunSeconds, now);
            state.setLastActionAt(now);
            markState(state, "active", null, false, now);
        } catch (RuntimeException ex) {
            logAction(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_WATERING, pumpBinding,
                    "PUMP_START", ex.getMessage(), "error", requestedRunSeconds, now);
            markState(state, "error", ex.getMessage(), false, now);
        }
    }

    private void sendSwitchIfNeeded(
            AutomationResourceBindingEntity binding,
            boolean on,
            Catalog catalog,
            String scenarioType,
            String scopeType,
            Integer scopeId,
            String reason,
            LocalDateTime now,
            Integer durationS
    ) {
        if (binding == null) {
            return;
        }
        String desired = on ? defaultOnValue(binding.getOnValue()) : defaultOffValue(binding.getOffValue());
        Object current = readSwitchValue(binding, catalog);
        if (current != null && desired.equalsIgnoreCase(String.valueOf(current))) {
            return;
        }
        String property = defaultCommandProperty(binding.getRole(), binding.getCommandProperty());
        try {
            zigbeeFacade.setDeviceProperty(binding.getZigbeeIeeeAddress(), property, desired);
            logAction(scopeType, scopeId, scenarioType, binding,
                    switchAction(binding.getRole(), on), reason, "published", durationS, now);
            AutomationScenarioStateEntity state = stateFor(scopeType, scopeId, scenarioType, now);
            state.setLastActionAt(now);
            stateRepository.save(state);
        } catch (RuntimeException ex) {
            logAction(scopeType, scopeId, scenarioType, binding,
                    switchAction(binding.getRole(), on), ex.getMessage(), "error", durationS, now);
        }
    }

    private String switchAction(String role, boolean on) {
        String prefix = switch (role) {
            case AutomationData.ROLE_AC_SWITCH -> "AC";
            case AutomationData.ROLE_EXHAUST_SWITCH -> "EXHAUST";
            case AutomationData.ROLE_LIGHT_SWITCH -> "LIGHT";
            default -> "SWITCH";
        };
        return prefix + "_" + (on ? "ON" : "OFF");
    }

    private void markState(
            AutomationScenarioStateEntity state,
            String status,
            String reason,
            boolean acRequestActive,
            LocalDateTime now
    ) {
        state.setStatus(status);
        state.setUnavailableReason(reason);
        state.setAcRequestActive(acRequestActive);
        state.setLastEvaluatedAt(now);
        state.setUpdatedAt(now);
        stateRepository.save(state);
    }

    private void logAction(
            String scopeType,
            Integer scopeId,
            String scenarioType,
            AutomationResourceBindingEntity binding,
            String action,
            String reason,
            String result,
            Integer durationS,
            LocalDateTime now
    ) {
        actionLogRepository.save(AutomationActionLogEntity.create(
                scopeType,
                scopeId,
                scenarioType,
                binding,
                action,
                reason,
                result,
                durationS,
                now
        ));
    }

    private AutomationData.Room toRoomData(
            AutomationRoomEntity room,
            List<AutomationData.Box> boxes,
            Map<String, List<AutomationResourceBindingEntity>> resources,
            Map<String, List<AutomationScenarioConfigEntity>> configs,
            Map<String, List<AutomationScenarioStateEntity>> states,
            Catalog catalog
    ) {
        return new AutomationData.Room(
                room.getId(),
                room.getName(),
                room.isEnabled(),
                resources.getOrDefault(key(AutomationData.SCOPE_ROOM, room.getId()), List.of()).stream()
                        .map(binding -> toBindingData(binding, catalog))
                        .toList(),
                scenarioData(
                        AutomationData.SCOPE_ROOM,
                        room.getId(),
                        ROOM_SCENARIOS,
                        configs.getOrDefault(key(AutomationData.SCOPE_ROOM, room.getId()), List.of()),
                        catalog,
                        room.getId()
                ),
                states.getOrDefault(key(AutomationData.SCOPE_ROOM, room.getId()), List.of()).stream()
                        .map(this::toStateData)
                        .toList(),
                boxes,
                actionLogRepository.findTop10ByScopeTypeAndScopeIdOrderByCreatedAtDesc(AutomationData.SCOPE_ROOM, room.getId())
                        .stream().map(this::toActionLogData).toList(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }

    private AutomationData.Box toBoxData(
            AutomationBoxEntity box,
            Map<Integer, List<AutomationBoxPlantEntity>> plantsByBox,
            Map<String, List<AutomationResourceBindingEntity>> resources,
            Map<String, List<AutomationScenarioConfigEntity>> configs,
            Map<String, List<AutomationScenarioStateEntity>> states,
            Catalog catalog,
            Integer roomId
    ) {
        Map<String, AutomationData.Readiness> readiness = new LinkedHashMap<>();
        readiness.put(AutomationData.SCENARIO_BOX_CLIMATE, boxClimateReadiness(box.getId(), roomId, catalog));
        readiness.put(AutomationData.SCENARIO_LIGHT_SCHEDULE, lightReadiness(box.getId(), catalog));
        readiness.put(AutomationData.SCENARIO_WATERING, wateringReadiness(box.getId(), catalog));
        return new AutomationData.Box(
                box.getId(),
                box.getRoomId(),
                box.getName(),
                box.isEnabled(),
                plantsByBox.getOrDefault(box.getId(), List.of()).stream()
                        .map(binding -> toBoxPlantData(binding, catalog.plantsById.get(binding.getPlantId())))
                        .filter(Objects::nonNull)
                        .toList(),
                resources.getOrDefault(key(AutomationData.SCOPE_BOX, box.getId()), List.of()).stream()
                        .map(binding -> toBindingData(binding, catalog))
                        .toList(),
                scenarioData(
                        AutomationData.SCOPE_BOX,
                        box.getId(),
                        BOX_SCENARIOS,
                        configs.getOrDefault(key(AutomationData.SCOPE_BOX, box.getId()), List.of()),
                        catalog,
                        roomId
                ),
                states.getOrDefault(key(AutomationData.SCOPE_BOX, box.getId()), List.of()).stream()
                        .map(this::toStateData)
                        .toList(),
                actionLogRepository.findTop10ByScopeTypeAndScopeIdOrderByCreatedAtDesc(AutomationData.SCOPE_BOX, box.getId())
                        .stream().map(this::toActionLogData).toList(),
                readiness,
                box.getCreatedAt(),
                box.getUpdatedAt()
        );
    }

    private List<AutomationData.ScenarioConfig> scenarioData(
            String scopeType,
            Integer scopeId,
            List<String> scenarioTypes,
            List<AutomationScenarioConfigEntity> configs,
            Catalog catalog,
            Integer roomId
    ) {
        Map<String, AutomationScenarioConfigEntity> byType = configs.stream()
                .collect(Collectors.toMap(AutomationScenarioConfigEntity::getScenarioType, Function.identity()));
        List<AutomationData.ScenarioConfig> result = new ArrayList<>();
        for (String scenarioType : scenarioTypes) {
            AutomationScenarioConfigEntity config = byType.get(scenarioType);
            result.add(new AutomationData.ScenarioConfig(
                    config != null ? config.getId() : null,
                    scopeType,
                    scopeId,
                    scenarioType,
                    config != null && config.isEnabled(),
                    config != null ? configMap(config, scenarioType) : defaultConfig(scenarioType),
                    readinessForScenario(scopeType, scopeId, scenarioType, roomId, catalog),
                    config != null ? config.getCreatedAt() : null,
                    config != null ? config.getUpdatedAt() : null
            ));
        }
        return result;
    }

    private AutomationData.ResourceBinding toBindingData(AutomationResourceBindingEntity binding, Catalog catalog) {
        ResourceStatus status = resolveResourceStatus(binding, catalog);
        ConnectionStatus connectionStatus = connectionStatus(binding.getRole(), status, nowUtc());
        return new AutomationData.ResourceBinding(
                binding.getId(),
                binding.getScopeType(),
                binding.getScopeId(),
                binding.getRole(),
                binding.getSourceType(),
                binding.getNativeSensorId(),
                binding.getNativePumpId(),
                binding.getZigbeeIeeeAddress(),
                binding.getZigbeeProperty(),
                binding.getCommandProperty(),
                binding.getOnValue(),
                binding.getOffValue(),
                status.value(),
                status.ts(),
                connectionStatus.status(),
                connectionStatus.message(),
                status.label(),
                status.ready(),
                status.reason()
        );
    }

    private AutomationData.ScenarioState toStateData(AutomationScenarioStateEntity state) {
        return new AutomationData.ScenarioState(
                state.getId(),
                state.getScopeType(),
                state.getScopeId(),
                state.getScenarioType(),
                state.getStatus(),
                state.getUnavailableReason(),
                state.getLastEvaluatedAt(),
                state.getLastActionAt(),
                state.isAcRequestActive(),
                state.getManualPauseUntil(),
                runtimeMap(state),
                state.getUpdatedAt()
        );
    }

    private AutomationData.ActionLog toActionLogData(AutomationActionLogEntity log) {
        return new AutomationData.ActionLog(
                log.getId(),
                log.getScopeType(),
                log.getScopeId(),
                log.getScenarioType(),
                log.getResourceBinding() != null ? log.getResourceBinding().getId() : null,
                log.getAction(),
                log.getReason(),
                log.getResult(),
                log.getDurationS(),
                log.getCreatedAt()
        );
    }

    private AutomationData.Readiness readinessForScenario(
            String scopeType,
            Integer scopeId,
            String scenarioType,
            Integer roomId,
            Catalog catalog
    ) {
        if (AutomationData.SCOPE_ROOM.equals(scopeType)) {
            return roomClimateReadiness(scopeId, boxRepository.findAllByRoom_IdOrderByNameAscIdAsc(scopeId), catalog);
        }
        return switch (scenarioType) {
            case AutomationData.SCENARIO_BOX_CLIMATE -> boxClimateReadiness(scopeId, roomId, catalog);
            case AutomationData.SCENARIO_LIGHT_SCHEDULE -> lightReadiness(scopeId, catalog);
            case AutomationData.SCENARIO_WATERING -> wateringReadiness(scopeId, catalog);
            default -> new AutomationData.Readiness(false, "neizvestnyj scenarij", List.of());
        };
    }

    private AutomationData.Readiness roomClimateReadiness(
            Integer roomId,
            List<AutomationBoxEntity> roomBoxes,
            Catalog catalog
    ) {
        List<String> roles = List.of(AutomationData.ROLE_AC_SWITCH);
        ResourceStatus ac = resolveResourceStatus(resource(AutomationData.SCOPE_ROOM, roomId, AutomationData.ROLE_AC_SWITCH), catalog);
        if (!ac.ready()) {
            return new AutomationData.Readiness(false, "nuzhen AC_SWITCH", roles);
        }
        boolean hasBoxClimate = roomBoxes.stream()
                .anyMatch(box -> {
                    AutomationScenarioConfigEntity cfg = configFor(
                            AutomationData.SCOPE_BOX,
                            box.getId(),
                            AutomationData.SCENARIO_BOX_CLIMATE
                    );
                    return cfg != null && cfg.isEnabled();
                });
        if (!hasBoxClimate) {
            return new AutomationData.Readiness(false, "net vklyuchennyh klimat-scenariev boksov", roles);
        }
        return new AutomationData.Readiness(true, null, roles);
    }

    private AutomationData.Readiness boxClimateReadiness(Integer boxId, Integer roomId, Catalog catalog) {
        List<String> roles = List.of(
                AutomationData.ROLE_AIR_TEMPERATURE_SENSOR,
                AutomationData.ROLE_EXHAUST_SWITCH,
                AutomationData.ROLE_AC_SWITCH
        );
        ResourceStatus temperature = resolveResourceStatus(
                resource(AutomationData.SCOPE_BOX, boxId, AutomationData.ROLE_AIR_TEMPERATURE_SENSOR),
                catalog
        );
        if (!temperature.ready()) {
            return new AutomationData.Readiness(false, "nuzhen AIR_TEMPERATURE_SENSOR", roles);
        }
        ResourceStatus exhaust = resolveResourceStatus(
                resource(AutomationData.SCOPE_BOX, boxId, AutomationData.ROLE_EXHAUST_SWITCH),
                catalog
        );
        if (!exhaust.ready()) {
            return new AutomationData.Readiness(false, "nuzhen EXHAUST_SWITCH", roles);
        }
        ResourceStatus ac = resolveResourceStatus(
                roomId != null ? resource(AutomationData.SCOPE_ROOM, roomId, AutomationData.ROLE_AC_SWITCH) : null,
                catalog
        );
        if (!ac.ready()) {
            return new AutomationData.Readiness(false, "v pomeshchenii nuzhen AC_SWITCH", roles);
        }
        return new AutomationData.Readiness(true, null, roles);
    }

    private AutomationData.Readiness lightReadiness(Integer boxId, Catalog catalog) {
        List<String> roles = List.of(AutomationData.ROLE_LIGHT_SWITCH);
        ResourceStatus light = resolveResourceStatus(resource(AutomationData.SCOPE_BOX, boxId, AutomationData.ROLE_LIGHT_SWITCH), catalog);
        if (!light.ready()) {
            return new AutomationData.Readiness(false, "nuzhen Zigbee LIGHT_SWITCH s writable state", roles);
        }
        return new AutomationData.Readiness(true, null, roles);
    }

    private AutomationData.Readiness wateringReadiness(Integer boxId, Catalog catalog) {
        List<String> roles = List.of(AutomationData.ROLE_SOIL_MOISTURE_SENSOR, AutomationData.ROLE_WATER_PUMP);
        ResourceStatus soil = resolveResourceStatus(
                resource(AutomationData.SCOPE_BOX, boxId, AutomationData.ROLE_SOIL_MOISTURE_SENSOR),
                catalog
        );
        if (!soil.ready()) {
            return new AutomationData.Readiness(false, "nuzhen SOIL_MOISTURE_SENSOR", roles);
        }
        ResourceStatus pump = resolveResourceStatus(resource(AutomationData.SCOPE_BOX, boxId, AutomationData.ROLE_WATER_PUMP), catalog);
        if (!pump.ready()) {
            return new AutomationData.Readiness(false, "nuzhen native WATER_PUMP", roles);
        }
        return new AutomationData.Readiness(true, null, roles);
    }

    private ResourceStatus resolveResourceStatus(AutomationResourceBindingEntity binding, Catalog catalog) {
        if (binding == null) {
            return ResourceStatus.notReady("resource ne privyazan");
        }
        if (AutomationData.SOURCE_NATIVE_SENSOR.equals(binding.getSourceType())) {
            AutomationData.NativeSensor sensor = catalog.sensorsById.get(binding.getNativeSensorId());
            if (sensor == null) {
                return ResourceStatus.notReady("native sensor ne naiden");
            }
            return new ResourceStatus(
                    true,
                    null,
                    sensor.lastValue(),
                    sensor.lastSeenAt(),
                    sensor.label(),
                    nativeSensorConnectionWarning(sensor)
            );
        }
        if (AutomationData.SOURCE_NATIVE_PUMP.equals(binding.getSourceType())) {
            AutomationData.NativePump pump = catalog.pumpsById.get(binding.getNativePumpId());
            if (pump == null) {
                return ResourceStatus.notReady("native pump ne naiden");
            }
            return new ResourceStatus(true, null, pump.isRunning(), pump.lastSeenAt(), pump.label(), false);
        }
        if (AutomationData.SOURCE_ZIGBEE_DEVICE.equals(binding.getSourceType())) {
            AutomationData.ZigbeeDevice device = catalog.zigbeeByIeee.get(binding.getZigbeeIeeeAddress());
            if (device == null) {
                return ResourceStatus.notReady("Zigbee ustrojstvo ne naideno");
            }
            Object value = readZigbeeFeatureValue(device, binding.getZigbeeProperty());
            LocalDateTime ts = device.lastStateAt();
            return new ResourceStatus(
                    true,
                    null,
                    value,
                    ts,
                    device.friendlyName(),
                    "offline".equalsIgnoreCase(device.availability())
            );
        }
        return ResourceStatus.notReady("neizvestnyj source_type");
    }

    private boolean nativeSensorConnectionWarning(AutomationData.NativeSensor sensor) {
        if (sensor == null || sensor.status() == null) {
            return false;
        }
        return "DISCONNECTED".equals(sensor.status()) || "ERROR".equals(sensor.status());
    }

    private SensorValue readSensorValue(AutomationResourceBindingEntity binding, Catalog catalog) {
        if (binding == null) {
            return null;
        }
        if (AutomationData.SOURCE_NATIVE_SENSOR.equals(binding.getSourceType())) {
            AutomationData.NativeSensor sensor = catalog.sensorsById.get(binding.getNativeSensorId());
            return sensor != null ? new SensorValue(sensor.lastValue(), sensor.lastTs()) : null;
        }
        if (AutomationData.SOURCE_ZIGBEE_DEVICE.equals(binding.getSourceType())) {
            AutomationData.ZigbeeDevice device = catalog.zigbeeByIeee.get(binding.getZigbeeIeeeAddress());
            Double value = asDouble(readZigbeeFeatureValue(device, binding.getZigbeeProperty()));
            return device != null ? new SensorValue(value, device.lastStateAt()) : null;
        }
        return null;
    }

    private Object readSwitchValue(AutomationResourceBindingEntity binding, Catalog catalog) {
        if (binding == null || !AutomationData.SOURCE_ZIGBEE_DEVICE.equals(binding.getSourceType())) {
            return null;
        }
        AutomationData.ZigbeeDevice device = catalog.zigbeeByIeee.get(binding.getZigbeeIeeeAddress());
        return readZigbeeFeatureValue(device, defaultCommandProperty(binding.getRole(), binding.getCommandProperty()));
    }

    private Object readZigbeeFeatureValue(AutomationData.ZigbeeDevice device, String property) {
        AutomationData.ZigbeeFeature feature = readZigbeeFeature(device, property);
        return feature != null ? feature.value() : null;
    }

    private AutomationData.ZigbeeFeature readZigbeeFeature(AutomationData.ZigbeeDevice device, String property) {
        if (device == null || property == null) {
            return null;
        }
        for (AutomationData.ZigbeeFeature feature : device.metrics()) {
            if (property.equals(feature.property())) {
                return feature;
            }
        }
        for (AutomationData.ZigbeeFeature feature : device.controls()) {
            if (property.equals(feature.property())) {
                return feature;
            }
        }
        return null;
    }

    private void validateResource(String role, AutomationData.ResourceBindingRequest item, Catalog catalog) {
        String sourceType = normalizeRequired(item.sourceType(), "source_type");
        if (AutomationData.ROLE_WATER_PUMP.equals(role)) {
            if (!AutomationData.SOURCE_NATIVE_PUMP.equals(sourceType) || !catalog.pumpsById.containsKey(item.nativePumpId())) {
                throw new DomainException("bad_request", "WATER_PUMP v v1 dolzhen byt' native pump");
            }
            return;
        }
        if (isSwitchRole(role)) {
            if (!AutomationData.SOURCE_ZIGBEE_DEVICE.equals(sourceType)) {
                throw new DomainException("bad_request", role + " v v1 dolzhen byt' Zigbee switch");
            }
            String property = defaultCommandProperty(role, item.commandProperty());
            if (!zigbeeHasWritableProperty(catalog, item.zigbeeIeeeAddress(), property)) {
                throw new DomainException("bad_request", role + " dolzhen imet' writable " + property);
            }
            return;
        }
        if (AutomationData.ROLE_LEAK_SENSOR.equals(role)) {
            if (!AutomationData.SOURCE_ZIGBEE_DEVICE.equals(sourceType)) {
                throw new DomainException("bad_request", "LEAK_SENSOR dolzhen byt' Zigbee sensor");
            }
            String property = defaultProperty(role, item.zigbeeProperty());
            if (!zigbeeHasReadableProperty(catalog, item.zigbeeIeeeAddress(), property)) {
                throw new DomainException("bad_request", "LEAK_SENSOR dolzhen imet' readable " + property);
            }
            return;
        }
        if (AutomationData.ROLE_AIR_TEMPERATURE_SENSOR.equals(role) || AutomationData.ROLE_SOIL_MOISTURE_SENSOR.equals(role)) {
            String expectedType = AutomationData.ROLE_AIR_TEMPERATURE_SENSOR.equals(role) ? "AIR_TEMPERATURE" : "SOIL_MOISTURE";
            if (AutomationData.SOURCE_NATIVE_SENSOR.equals(sourceType)) {
                AutomationData.NativeSensor sensor = catalog.sensorsById.get(item.nativeSensorId());
                if (sensor == null || !expectedType.equals(sensor.type())) {
                    throw new DomainException("bad_request", role + " dolzhen ssylat'sya na sensor " + expectedType);
                }
                return;
            }
            if (AutomationData.SOURCE_ZIGBEE_DEVICE.equals(sourceType)) {
                String property = defaultProperty(role, item.zigbeeProperty());
                if (!zigbeeHasReadableProperty(catalog, item.zigbeeIeeeAddress(), property)) {
                    throw new DomainException("bad_request", role + " dolzhen imet' readable " + property);
                }
                return;
            }
        }
        throw new DomainException("bad_request", "nekorrektnyj resource binding");
    }

    private boolean zigbeeHasWritableProperty(Catalog catalog, String ieeeAddress, String property) {
        AutomationData.ZigbeeDevice device = catalog.zigbeeByIeee.get(blankToNull(ieeeAddress));
        if (device == null || property == null) {
            return false;
        }
        return device.controls().stream().anyMatch(feature -> property.equals(feature.property()));
    }

    private boolean zigbeeHasReadableProperty(Catalog catalog, String ieeeAddress, String property) {
        AutomationData.ZigbeeDevice device = catalog.zigbeeByIeee.get(blankToNull(ieeeAddress));
        if (device == null || property == null) {
            return false;
        }
        return device.metrics().stream().anyMatch(feature -> property.equals(feature.property()))
                || device.controls().stream().anyMatch(feature -> property.equals(feature.property()));
    }

    private Catalog buildCatalog() {
        List<AutomationData.Plant> plants = plantFacade.listAdminPlants(SYSTEM_ADMIN).stream()
                .map(this::toPlantData)
                .toList();
        Map<Integer, AutomationData.Plant> plantsById = plants.stream()
                .collect(Collectors.toMap(AutomationData.Plant::id, Function.identity()));

        List<AutomationData.NativeDevice> nativeDevices = new ArrayList<>();
        Map<Integer, AutomationData.NativeSensor> sensorsById = new HashMap<>();
        Map<Integer, AutomationData.NativePump> pumpsById = new HashMap<>();
        for (DeviceSummary summary : deviceFacade.listAdminDevices()) {
            DeviceShadowState shadow = deviceFacade.getShadowState(summary.deviceId());
            List<AutomationData.NativeSensor> sensors = sensorFacade.listByDeviceId(summary.id()).stream()
                    .map(sensor -> toNativeSensorData(sensor, summary))
                    .toList();
            sensors.forEach(sensor -> sensorsById.put(sensor.id(), sensor));
            List<AutomationData.NativePump> pumps = pumpFacade.listByDeviceId(summary.id(), shadow).stream()
                    .map(pump -> toNativePumpData(pump, summary))
                    .toList();
            pumps.forEach(pump -> pumpsById.put(pump.id(), pump));
            nativeDevices.add(new AutomationData.NativeDevice(
                    summary.id(),
                    summary.deviceId(),
                    summary.name(),
                    summary.isOnline(),
                    summary.lastSeen(),
                    sensors,
                    pumps
            ));
        }

        List<AutomationData.ZigbeeDevice> zigbeeDevices = zigbeeFacade.getOverview().devices().stream()
                .filter(device -> !device.coordinator())
                .map(this::toZigbeeDeviceData)
                .toList();
        Map<String, AutomationData.ZigbeeDevice> zigbeeByIeee = zigbeeDevices.stream()
                .filter(device -> device.ieeeAddress() != null)
                .collect(Collectors.toMap(AutomationData.ZigbeeDevice::ieeeAddress, Function.identity(), (left, right) -> left));
        return new Catalog(plants, plantsById, nativeDevices, sensorsById, pumpsById, zigbeeDevices, zigbeeByIeee);
    }

    private AutomationData.Plant toPlantData(AdminPlantInfo plant) {
        return new AutomationData.Plant(
                plant.id(),
                plant.name(),
                plant.ownerEmail(),
                plant.ownerUsername(),
                plant.ownerId(),
                plant.groupName()
        );
    }

    private AutomationData.BoxPlant toBoxPlantData(
            AutomationBoxPlantEntity binding,
            AutomationData.Plant plant
    ) {
        if (plant == null) {
            return null;
        }
        return new AutomationData.BoxPlant(
                plant.id(),
                plant.name(),
                plant.ownerEmail(),
                plant.ownerUsername(),
                plant.ownerId(),
                plant.groupName(),
                binding.getRateMlPerHour()
        );
    }

    private AutomationData.NativeSensor toNativeSensorData(SensorView sensor, DeviceSummary summary) {
        return new AutomationData.NativeSensor(
                sensor.id(),
                sensor.deviceId(),
                sensor.type() != null ? sensor.type().name() : null,
                sensor.channel(),
                sensor.label(),
                sensor.status() != null ? sensor.status().name() : null,
                sensor.lastValue(),
                sensor.lastTs(),
                summary != null ? summary.lastSeen() : null
        );
    }

    private AutomationData.NativePump toNativePumpData(PumpView pump, DeviceSummary summary) {
        return new AutomationData.NativePump(
                pump.id(),
                pump.deviceId(),
                pump.channel(),
                pump.label(),
                pump.isRunning(),
                summary.isOnline(),
                summary.lastSeen()
        );
    }

    private AutomationData.ZigbeeDevice toZigbeeDeviceData(ZigbeeDeviceData device) {
        return new AutomationData.ZigbeeDevice(
                device.ieeeAddress(),
                device.friendlyName(),
                device.type(),
                device.imageUrl(),
                device.definition(),
                device.metrics().stream().map(this::toZigbeeFeatureData).toList(),
                device.controls().stream().map(this::toZigbeeFeatureData).toList(),
                device.availability(),
                device.lastStateAt()
        );
    }

    private AutomationData.ZigbeeFeature toZigbeeFeatureData(ZigbeeFeatureData feature) {
        return new AutomationData.ZigbeeFeature(
                feature.type(),
                feature.property(),
                feature.label() != null ? feature.label() : feature.name(),
                feature.unit(),
                feature.access(),
                feature.value(),
                feature.valueOn(),
                feature.valueOff()
        );
    }

    private void validateRoleForScope(String scopeType, String role) {
        if (AutomationData.SCOPE_ROOM.equals(scopeType)) {
            if (!AutomationData.ROLE_AC_SWITCH.equals(role)) {
                throw new DomainException("bad_request", "rol' nedostupna dlya pomeshcheniya");
            }
            return;
        }
        if (AutomationData.SCOPE_BOX.equals(scopeType)) {
            if (List.of(
                    AutomationData.ROLE_AIR_TEMPERATURE_SENSOR,
                    AutomationData.ROLE_EXHAUST_SWITCH,
                    AutomationData.ROLE_LIGHT_SWITCH,
                    AutomationData.ROLE_LEAK_SENSOR,
                    AutomationData.ROLE_SOIL_MOISTURE_SENSOR,
                    AutomationData.ROLE_WATER_PUMP
            ).contains(role)) {
                return;
            }
        }
        throw new DomainException("bad_request", "rol' nedostupna dlya scope");
    }

    private boolean isSwitchRole(String role) {
        return AutomationData.ROLE_AC_SWITCH.equals(role)
                || AutomationData.ROLE_EXHAUST_SWITCH.equals(role)
                || AutomationData.ROLE_LIGHT_SWITCH.equals(role);
    }

    private WateringTopology buildWateringTopology(Catalog catalog) {
        List<AutomationRoomEntity> rooms = roomRepository.findAllByOrderByNameAscIdAsc();
        List<AutomationBoxEntity> boxes = boxRepository.findAllByOrderByNameAscIdAsc();
        Map<Integer, AutomationRoomEntity> roomsById = rooms.stream()
                .collect(Collectors.toMap(AutomationRoomEntity::getId, Function.identity()));
        Map<Integer, List<AutomationBoxPlantEntity>> plantsByBox = groupPlants(boxes);
        Map<String, List<AutomationResourceBindingEntity>> resources = groupResources(rooms, boxes);
        Map<Integer, List<AutomationData.ManualWateringBox>> boxesByPump = new LinkedHashMap<>();
        Map<Integer, List<PumpSessionData.BoxTarget>> targetsByPump = new LinkedHashMap<>();
        LocalDateTime now = nowUtc();

        for (AutomationBoxEntity box : boxes) {
            List<AutomationResourceBindingEntity> boxResources = resources.getOrDefault(
                    key(AutomationData.SCOPE_BOX, box.getId()),
                    List.of()
            );
            AutomationResourceBindingEntity pumpBinding = boxResources.stream()
                    .filter(binding -> AutomationData.ROLE_WATER_PUMP.equals(binding.getRole()))
                    .findFirst()
                    .orElse(null);
            if (pumpBinding == null || pumpBinding.getNativePumpId() == null) {
                continue;
            }
            AutomationRoomEntity room = roomsById.get(box.getRoomId());
            List<AutomationData.BoxPlant> plants = plantsByBox.getOrDefault(box.getId(), List.of()).stream()
                    .map(binding -> toBoxPlantData(binding, catalog.plantsById.get(binding.getPlantId())))
                    .filter(Objects::nonNull)
                    .toList();
            List<PumpSessionData.LeakTarget> leakSensors = boxResources.stream()
                    .filter(binding -> AutomationData.ROLE_LEAK_SENSOR.equals(binding.getRole()))
                    .map(binding -> toLeakTarget(binding, catalog))
                    .toList();
            AutomationData.ManualWateringBox manualBox = new AutomationData.ManualWateringBox(
                    box.getId(),
                    box.getName(),
                    box.getRoomId(),
                    room != null ? room.getName() : null,
                    box.isEnabled(),
                    plants,
                    leakSensors
            );
            PumpSessionData.BoxTarget target = new PumpSessionData.BoxTarget(
                    box.getId(),
                    box.getName(),
                    box.getRoomId(),
                    room != null ? room.getName() : null,
                    plants.stream()
                            .map(plant -> new PumpSessionData.PlantTarget(
                                    plant.id(),
                                    plant.name(),
                                    plant.rateMlPerHour(),
                                    plant.ownerId()
                            ))
                            .toList(),
                    leakSensors
            );
            boxesByPump.computeIfAbsent(pumpBinding.getNativePumpId(), ignored -> new ArrayList<>()).add(manualBox);
            targetsByPump.computeIfAbsent(pumpBinding.getNativePumpId(), ignored -> new ArrayList<>()).add(target);
        }
        return new WateringTopology(boxesByPump, targetsByPump);
    }

    private boolean hasConfiguredLeakSensorForPump(
            Integer pumpId,
            Integer excludedBoxId,
            Catalog catalog
    ) {
        return buildWateringTopology(catalog).targetsByPump.getOrDefault(pumpId, List.of()).stream()
                .filter(target -> !Objects.equals(target.boxId(), excludedBoxId))
                .flatMap(target -> target.leakSensors().stream())
                .findAny()
                .isPresent();
    }

    private List<String> manualWateringBlockReasons(
            AutomationData.NativePump pump,
            AutomationData.NativeDevice device,
            List<AutomationData.ManualWateringBox> boxes,
            PumpSessionData.View currentSession,
            List<PumpSessionData.Probe> activeProbes
    ) {
        List<String> reasons = new ArrayList<>();
        if (!Boolean.TRUE.equals(device.isOnline()) || !Boolean.TRUE.equals(pump.isOnline())) {
            reasons.add("pump_offline");
        }
        if (currentSession != null) {
            reasons.add("pump_session_active");
        } else if (Boolean.TRUE.equals(pump.isRunning())) {
            reasons.add("pump_running");
        }
        boolean deviceBusy = activeProbes.stream()
                .anyMatch(probe -> Objects.equals(probe.deviceKey(), device.deviceId())
                        && !Objects.equals(probe.pumpId(), pump.id()));
        if (deviceBusy) {
            reasons.add("device_busy");
        }
        if (boxes.isEmpty()) {
            reasons.add("no_boxes");
        }
        if (boxes.stream().flatMap(box -> box.plants().stream()).findAny().isEmpty()) {
            reasons.add("no_plants");
        }
        if (boxes.stream()
                .flatMap(box -> box.leakSensors().stream())
                .anyMatch(sensor -> Boolean.TRUE.equals(sensor.triggered()))) {
            reasons.add("leak_triggered");
        }
        return List.copyOf(reasons);
    }

    private PumpSessionData.LeakTarget toLeakTarget(
            AutomationResourceBindingEntity binding,
            Catalog catalog
    ) {
        AutomationData.ZigbeeDevice device = catalog.zigbeeByIeee.get(binding.getZigbeeIeeeAddress());
        String property = defaultProperty(AutomationData.ROLE_LEAK_SENSOR, binding.getZigbeeProperty());
        return new PumpSessionData.LeakTarget(
                "automation-resource:" + binding.getId(),
                binding.getId(),
                binding.getSourceType(),
                binding.getZigbeeIeeeAddress(),
                property,
                device != null ? device.friendlyName() : null,
                isLeakAvailable(device, property),
                isLeakTriggered(device, property)
        );
    }

    private PumpSessionData.LeakState currentLeakState(
            PumpSessionData.LeakTarget target,
            Catalog catalog
    ) {
        AutomationData.ZigbeeDevice device = catalog.zigbeeByIeee.get(target.externalId());
        return new PumpSessionData.LeakState(
                target.reference(),
                isLeakAvailable(device, target.property()),
                isLeakTriggered(device, target.property())
        );
    }

    private boolean isLeakAvailable(AutomationData.ZigbeeDevice device, String property) {
        if (device == null || readZigbeeFeature(device, property) == null) {
            return false;
        }
        return "online".equalsIgnoreCase(device.availability());
    }

    private boolean isLeakTriggered(AutomationData.ZigbeeDevice device, String property) {
        AutomationData.ZigbeeFeature feature = readZigbeeFeature(device, property);
        if (feature == null) {
            return false;
        }
        Object value = feature.value();
        if (Boolean.TRUE.equals(value)) {
            return true;
        }
        String normalized = value != null ? String.valueOf(value).trim() : "";
        if ("true".equalsIgnoreCase(normalized) || "ON".equalsIgnoreCase(normalized)) {
            return true;
        }
        return feature.valueOn() != null
                && normalized.equalsIgnoreCase(String.valueOf(feature.valueOn()).trim());
    }

    private Integer automationPumpId(Integer boxId) {
        AutomationResourceBindingEntity binding = resource(
                AutomationData.SCOPE_BOX,
                boxId,
                AutomationData.ROLE_WATER_PUMP
        );
        return binding != null ? binding.getNativePumpId() : null;
    }

    private void syncAutomationPump(Integer pumpId) {
        Catalog catalog = buildCatalog();
        WateringTopology topology = buildWateringTopology(catalog);
        pumpFacade.syncAutomationBindings(pumpId, topology.targetsByPump.getOrDefault(pumpId, List.of()));
    }

    private AutomationResourceBindingEntity resource(String scopeType, Integer scopeId, String role) {
        if (scopeId == null) {
            return null;
        }
        return resourceRepository.findByScopeTypeAndScopeIdAndRole(scopeType, scopeId, role).orElse(null);
    }

    private AutomationScenarioConfigEntity configFor(String scopeType, Integer scopeId, String scenarioType) {
        return configRepository.findByScopeTypeAndScopeIdAndScenarioType(scopeType, scopeId, scenarioType).orElse(null);
    }

    private AutomationScenarioStateEntity stateFor(String scopeType, Integer scopeId, String scenarioType, LocalDateTime now) {
        return stateRepository.findByScopeTypeAndScopeIdAndScenarioType(scopeType, scopeId, scenarioType)
                .orElseGet(() -> stateRepository.save(AutomationScenarioStateEntity.create(scopeType, scopeId, scenarioType, now)));
    }

    private Map<Integer, List<AutomationBoxPlantEntity>> groupPlants(List<AutomationBoxEntity> boxes) {
        List<Integer> boxIds = boxes.stream().map(AutomationBoxEntity::getId).toList();
        if (boxIds.isEmpty()) {
            return Map.of();
        }
        return boxPlantRepository.findAllByBox_IdIn(boxIds).stream()
                .collect(Collectors.groupingBy(AutomationBoxPlantEntity::getBoxId));
    }

    private Map<String, List<AutomationResourceBindingEntity>> groupResources(
            List<AutomationRoomEntity> rooms,
            List<AutomationBoxEntity> boxes
    ) {
        Map<String, List<AutomationResourceBindingEntity>> result = new HashMap<>();
        List<Integer> roomIds = rooms.stream().map(AutomationRoomEntity::getId).toList();
        if (!roomIds.isEmpty()) {
            resourceRepository.findAllByScopeTypeAndScopeIdIn(AutomationData.SCOPE_ROOM, roomIds)
                    .forEach(item -> result.computeIfAbsent(key(item.getScopeType(), item.getScopeId()), ignored -> new ArrayList<>()).add(item));
        }
        List<Integer> boxIds = boxes.stream().map(AutomationBoxEntity::getId).toList();
        if (!boxIds.isEmpty()) {
            resourceRepository.findAllByScopeTypeAndScopeIdIn(AutomationData.SCOPE_BOX, boxIds)
                    .forEach(item -> result.computeIfAbsent(key(item.getScopeType(), item.getScopeId()), ignored -> new ArrayList<>()).add(item));
        }
        return result;
    }

    private Map<String, List<AutomationScenarioConfigEntity>> groupConfigs(
            List<AutomationRoomEntity> rooms,
            List<AutomationBoxEntity> boxes
    ) {
        Map<String, List<AutomationScenarioConfigEntity>> result = new HashMap<>();
        List<Integer> roomIds = rooms.stream().map(AutomationRoomEntity::getId).toList();
        if (!roomIds.isEmpty()) {
            configRepository.findAllByScopeTypeAndScopeIdIn(AutomationData.SCOPE_ROOM, roomIds)
                    .forEach(item -> result.computeIfAbsent(key(item.getScopeType(), item.getScopeId()), ignored -> new ArrayList<>()).add(item));
        }
        List<Integer> boxIds = boxes.stream().map(AutomationBoxEntity::getId).toList();
        if (!boxIds.isEmpty()) {
            configRepository.findAllByScopeTypeAndScopeIdIn(AutomationData.SCOPE_BOX, boxIds)
                    .forEach(item -> result.computeIfAbsent(key(item.getScopeType(), item.getScopeId()), ignored -> new ArrayList<>()).add(item));
        }
        return result;
    }

    private Map<String, List<AutomationScenarioStateEntity>> groupStates(
            List<AutomationRoomEntity> rooms,
            List<AutomationBoxEntity> boxes
    ) {
        Map<String, List<AutomationScenarioStateEntity>> result = new HashMap<>();
        List<Integer> roomIds = rooms.stream().map(AutomationRoomEntity::getId).toList();
        if (!roomIds.isEmpty()) {
            stateRepository.findAllByScopeTypeAndScopeIdIn(AutomationData.SCOPE_ROOM, roomIds)
                    .forEach(item -> result.computeIfAbsent(key(item.getScopeType(), item.getScopeId()), ignored -> new ArrayList<>()).add(item));
        }
        List<Integer> boxIds = boxes.stream().map(AutomationBoxEntity::getId).toList();
        if (!boxIds.isEmpty()) {
            stateRepository.findAllByScopeTypeAndScopeIdIn(AutomationData.SCOPE_BOX, boxIds)
                    .forEach(item -> result.computeIfAbsent(key(item.getScopeType(), item.getScopeId()), ignored -> new ArrayList<>()).add(item));
        }
        return result;
    }

    private void deleteRoomResources(Integer roomId) {
        resourceRepository.deleteAllByScopeTypeAndScopeId(AutomationData.SCOPE_ROOM, roomId);
        configRepository.deleteAllByScopeTypeAndScopeId(AutomationData.SCOPE_ROOM, roomId);
        stateRepository.deleteAllByScopeTypeAndScopeId(AutomationData.SCOPE_ROOM, roomId);
    }

    private void deleteBoxResources(Integer boxId) {
        boxPlantRepository.deleteAllByBox_Id(boxId);
        resourceRepository.deleteAllByScopeTypeAndScopeId(AutomationData.SCOPE_BOX, boxId);
        configRepository.deleteAllByScopeTypeAndScopeId(AutomationData.SCOPE_BOX, boxId);
        stateRepository.deleteAllByScopeTypeAndScopeId(AutomationData.SCOPE_BOX, boxId);
    }

    private AutomationRoomEntity requireRoom(Integer roomId) {
        if (roomId == null) {
            throw new DomainException("bad_request", "room_id obyazatelen");
        }
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new DomainException("not_found", "pomeshchenie ne naideno"));
    }

    private AutomationBoxEntity requireBox(Integer boxId) {
        if (boxId == null) {
            throw new DomainException("bad_request", "box_id obyazatelen");
        }
        return boxRepository.findById(boxId)
                .orElseThrow(() -> new DomainException("not_found", "box ne naiden"));
    }

    private Map<String, Object> defaultConfig(String scenarioType) {
        Map<String, Object> config = new LinkedHashMap<>();
        switch (scenarioType) {
            case AutomationData.SCENARIO_BOX_CLIMATE -> {
                config.put("min_c", 24.0);
                config.put("max_c", 28.0);
                config.put("exhaust_off_below_c", 27.0);
                config.put("ac_request_above_c", 29.0);
                config.put("ac_clear_below_c", 27.0);
            }
            case AutomationData.SCENARIO_ROOM_CLIMATE -> {
                config.put("off_delay_minutes", 5);
                config.put("min_toggle_minutes", 5);
            }
            case AutomationData.SCENARIO_LIGHT_SCHEDULE -> {
                config.put("start_time", "06:00");
                config.put("end_time", "22:00");
                config.put("days", List.of(1, 2, 3, 4, 5, 6, 7));
            }
            case AutomationData.SCENARIO_WATERING -> {
                config.put("soil_threshold_percent", 40.0);
                config.put("max_interval_hours", 48);
                config.put("stop_mode", STOP_MODE_FIXED_DURATION);
                config.put("run_seconds", 30);
                config.put("max_run_minutes", 10);
                config.put("pulse_enabled", false);
                config.put("pulse_run_minutes", 3);
                config.put("pulse_pause_minutes", 5);
                config.put("min_interval_hours", 6);
                config.put("daily_max_seconds", 1200);
            }
            default -> {
            }
        }
        return config;
    }

    private Map<String, Object> mergeDefaults(String scenarioType, Map<String, Object> config) {
        Map<String, Object> merged = defaultConfig(scenarioType);
        if (config != null) {
            merged.putAll(config);
        }
        return merged;
    }

    private Map<String, Object> configMap(AutomationScenarioConfigEntity config, String scenarioType) {
        return mergeDefaults(scenarioType, readJsonMap(config.getConfigJson()));
    }

    private Map<String, Object> runtimeMap(AutomationScenarioStateEntity state) {
        return readJsonMap(state != null ? state.getRuntimeJson() : null);
    }

    private boolean hasActiveWateringSession(AutomationScenarioStateEntity state) {
        Map<String, Object> runtime = runtimeMap(state);
        return asBoolean(runtime.get(RUNTIME_WATERING_ACTIVE), false);
    }

    private void clearWateringRuntime(Map<String, Object> runtime) {
        LEGACY_WATERING_RUNTIME_KEYS.forEach(runtime::remove);
    }

    private boolean isUntilDrain(Map<String, Object> cfg) {
        Object value = cfg.get("stop_mode");
        return STOP_MODE_UNTIL_DRAIN.equals(String.valueOf(value));
    }

    private int untilDrainMaxRunSeconds(Map<String, Object> cfg) {
        return minutesToSeconds(cfg.get("max_run_minutes"), 10);
    }

    private boolean pulseEnabled(Map<String, Object> cfg) {
        return asBoolean(cfg.get("pulse_enabled"), false);
    }

    private int pulseRunSeconds(Map<String, Object> cfg) {
        return minutesToSeconds(cfg.get("pulse_run_minutes"), 3);
    }

    private int pulsePauseSeconds(Map<String, Object> cfg) {
        return minutesToSeconds(cfg.get("pulse_pause_minutes"), 5);
    }

    private int minutesToSeconds(Object value, int fallbackMinutes) {
        double minutes = number(value, fallbackMinutes);
        if (minutes <= 0.0) {
            minutes = fallbackMinutes;
        }
        return Math.max(1, (int) Math.round(minutes * 60.0));
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> result = objectMapper.readValue(json, MAP_TYPE);
            return result != null ? new LinkedHashMap<>(result) : new LinkedHashMap<>();
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private boolean isLightScheduleActive(Map<String, Object> config, LocalDateTime nowUtc) {
        ZoneId zone = ZoneId.of(settings.getTimezone());
        ZonedDateTime local = nowUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone);
        int day = local.getDayOfWeek().getValue();
        Object daysValue = config.get("days");
        if (daysValue instanceof List<?> days && !days.isEmpty()) {
            boolean allowed = days.stream().anyMatch(item -> integer(item, -1) == day);
            if (!allowed) {
                return false;
            }
        }
        LocalTime start = parseTime(config.get("start_time"), LocalTime.of(6, 0));
        LocalTime end = parseTime(config.get("end_time"), LocalTime.of(22, 0));
        LocalTime current = local.toLocalTime();
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !current.isBefore(start) && current.isBefore(end);
        }
        return !current.isBefore(start) || current.isBefore(end);
    }

    private LocalTime parseTime(Object value, LocalTime fallback) {
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(value.toString());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private boolean isStale(LocalDateTime ts, LocalDateTime now) {
        return ts == null || ts.isBefore(now.minusMinutes(settings.getStaleSensorMinutes()));
    }

    private ConnectionStatus connectionStatus(String role, ResourceStatus status, LocalDateTime now) {
        if (status == null || !status.ready()) {
            return new ConnectionStatus(null, null);
        }
        if (status.connectionWarning()) {
            return new ConnectionStatus("warning", "нет связи");
        }
        LocalDateTime lastSeenAt = status.ts();
        int offlineMinutes = isDashboardSensorRole(role)
                ? settings.getSensorOfflineMinutes()
                : settings.getResourceOfflineMinutes();
        if (lastSeenAt == null || lastSeenAt.isBefore(now.minusMinutes(offlineMinutes))) {
            return new ConnectionStatus("warning", "нет связи");
        }
        return new ConnectionStatus("ok", null);
    }

    private boolean isDashboardSensorRole(String role) {
        return AutomationData.ROLE_AIR_TEMPERATURE_SENSOR.equals(role)
                || AutomationData.ROLE_SOIL_MOISTURE_SENSOR.equals(role);
    }

    private String defaultProperty(String role, String property) {
        String normalized = blankToNull(property);
        if (normalized != null) {
            return normalized;
        }
        if (AutomationData.ROLE_AIR_TEMPERATURE_SENSOR.equals(role)) {
            return "temperature";
        }
        if (AutomationData.ROLE_SOIL_MOISTURE_SENSOR.equals(role)) {
            return "soil_moisture";
        }
        if (AutomationData.ROLE_LEAK_SENSOR.equals(role)) {
            return "water_leak";
        }
        return "state";
    }

    private String defaultCommandProperty(String role, String property) {
        String normalized = blankToNull(property);
        if (!isSwitchRole(role)) {
            return normalized;
        }
        return normalized != null ? normalized : "state";
    }

    private String defaultOnValue(String value) {
        String normalized = blankToNull(value);
        return normalized != null ? normalized : "ON";
    }

    private String defaultOffValue(String value) {
        String normalized = blankToNull(value);
        return normalized != null ? normalized : "OFF";
    }

    private String requiredName(String name) {
        String normalized = blankToNull(name);
        if (normalized == null) {
            throw new DomainException("bad_request", "name obyazatelen");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new DomainException("bad_request", field + " obyazatelen");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private double number(Object value, double fallback) {
        Double parsed = asDouble(value);
        return parsed != null ? parsed : fallback;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        String normalized = value.toString().trim();
        if ("true".equalsIgnoreCase(normalized) || "ON".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized) || "OFF".equalsIgnoreCase(normalized)) {
            return false;
        }
        return fallback;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String key(String scopeType, Integer scopeId) {
        return scopeType + ":" + scopeId;
    }

    private record SensorValue(Double value, LocalDateTime ts) {
    }

    private record ResourceStatus(
            boolean ready,
            String reason,
            Object value,
            LocalDateTime ts,
            String label,
            boolean connectionWarning
    ) {
        static ResourceStatus notReady(String reason) {
            return new ResourceStatus(false, reason, null, null, null, false);
        }
    }

    private record ConnectionStatus(String status, String message) {
    }

    private record WateringTopology(
            Map<Integer, List<AutomationData.ManualWateringBox>> boxesByPump,
            Map<Integer, List<PumpSessionData.BoxTarget>> targetsByPump
    ) {
    }

    private record Catalog(
            List<AutomationData.Plant> plants,
            Map<Integer, AutomationData.Plant> plantsById,
            List<AutomationData.NativeDevice> nativeDevices,
            Map<Integer, AutomationData.NativeSensor> sensorsById,
            Map<Integer, AutomationData.NativePump> pumpsById,
            List<AutomationData.ZigbeeDevice> zigbeeDevices,
            Map<String, AutomationData.ZigbeeDevice> zigbeeByIeee
    ) {
        AutomationData.ResourceCatalog toData() {
            return new AutomationData.ResourceCatalog(plants, nativeDevices, zigbeeDevices);
        }
    }
}
