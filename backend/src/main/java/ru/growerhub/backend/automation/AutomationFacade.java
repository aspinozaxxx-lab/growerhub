package ru.growerhub.backend.automation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
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
import ru.growerhub.backend.pump.contract.PumpView;
import ru.growerhub.backend.sensor.SensorFacade;
import ru.growerhub.backend.sensor.contract.SensorView;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeDeviceData;
import ru.growerhub.backend.zigbee.contract.ZigbeeFeatureData;

@Service
@Transactional
public class AutomationFacade {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final AuthenticatedUser SYSTEM_ADMIN = new AuthenticatedUser(0, "admin");
    private static final List<String> ROOM_SCENARIOS = List.of(AutomationData.SCENARIO_ROOM_CLIMATE);
    private static final List<String> BOX_SCENARIOS = List.of(
            AutomationData.SCENARIO_BOX_CLIMATE,
            AutomationData.SCENARIO_LIGHT_SCHEDULE,
            AutomationData.SCENARIO_WATERING
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
                        settings.getManualOverrideMinutes()
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
        for (AutomationBoxEntity box : boxes) {
            deleteBoxResources(box.getId());
        }
        deleteRoomResources(room.getId());
        roomRepository.delete(room);
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
        deleteBoxResources(box.getId());
        boxRepository.delete(box);
    }

    public AutomationData.Overview replaceBoxPlants(Integer boxId, AutomationData.SavePlantsRequest request) {
        AutomationBoxEntity box = requireBox(boxId);
        LocalDateTime now = nowUtc();
        List<Integer> plantIds = request != null && request.plantIds() != null ? request.plantIds() : List.of();
        Set<Integer> uniquePlantIds = new HashSet<>(plantIds);
        if (uniquePlantIds.size() != plantIds.size()) {
            throw new DomainException("bad_request", "plant_ids dolzhny byt' unikalnymi");
        }
        for (Integer plantId : uniquePlantIds) {
            if (plantFacade.getPlantInfoById(plantId) == null) {
                throw new DomainException("not_found", "rastenie ne naideno");
            }
        }
        boxPlantRepository.deleteAllByBox_Id(box.getId());
        List<AutomationBoxPlantEntity> rows = uniquePlantIds.stream()
                .map(plantId -> AutomationBoxPlantEntity.create(box, plantId, now))
                .toList();
        boxPlantRepository.saveAll(rows);
        return getOverview();
    }

    public AutomationData.Overview replaceRoomResources(Integer roomId, AutomationData.SaveResourcesRequest request) {
        requireRoom(roomId);
        replaceResources(AutomationData.SCOPE_ROOM, roomId, request);
        return getOverview();
    }

    public AutomationData.Overview replaceBoxResources(Integer boxId, AutomationData.SaveResourcesRequest request) {
        requireBox(boxId);
        replaceResources(AutomationData.SCOPE_BOX, boxId, request);
        return getOverview();
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
            entity.setEnabled(Boolean.TRUE.equals(item.enabled()));
            entity.setConfigJson(writeJson(mergeDefaults(scenarioType, item.config())));
            entity.setUpdatedAt(now);
            configRepository.save(entity);
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
        AutomationResourceBindingEntity soilBinding = resource(AutomationData.SCOPE_BOX, box.getId(), AutomationData.ROLE_SOIL_MOISTURE_SENSOR);
        SensorValue moisture = readSensorValue(soilBinding, catalog);
        if (moisture == null || moisture.value() == null || isStale(moisture.ts(), now)) {
            markState(state, "stale", "net aktual'noj vlazhnosti pochvy", false, now);
            return;
        }

        Map<String, Object> cfg = configMap(config, AutomationData.SCENARIO_WATERING);
        double threshold = number(cfg.get("soil_threshold_percent"), 40.0);
        int maxIntervalHours = integer(cfg.get("max_interval_hours"), 48);
        int runSeconds = integer(cfg.get("run_seconds"), 30);
        int minIntervalHours = integer(cfg.get("min_interval_hours"), 6);
        int dailyMaxSeconds = integer(cfg.get("daily_max_seconds"), 1200);
        AutomationActionLogEntity lastStart = actionLogRepository.findTopByScopeTypeAndScopeIdAndScenarioTypeAndActionOrderByCreatedAtDesc(
                AutomationData.SCOPE_BOX,
                box.getId(),
                AutomationData.SCENARIO_WATERING,
                "PUMP_START"
        );
        boolean tooDry = moisture.value() <= threshold;
        boolean intervalElapsed = lastStart == null || lastStart.getCreatedAt().isBefore(now.minusHours(maxIntervalHours));
        if (!tooDry && !intervalElapsed) {
            markState(state, "active", null, false, now);
            return;
        }
        if (lastStart != null && lastStart.getCreatedAt().isAfter(now.minusHours(minIntervalHours))) {
            logAction(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_WATERING, null,
                    "SKIP", "min interval mezhdu polivami", "skipped", null, now);
            markState(state, "limited", "min interval mezhdu polivami", false, now);
            return;
        }
        Long usedTodayRaw = actionLogRepository.sumDurationSince(
                AutomationData.SCOPE_BOX,
                box.getId(),
                AutomationData.SCENARIO_WATERING,
                "PUMP_START",
                "published",
                LocalDate.now(ZoneOffset.UTC).atStartOfDay()
        );
        int usedToday = usedTodayRaw != null ? usedTodayRaw.intValue() : 0;
        if (usedToday + runSeconds > dailyMaxSeconds) {
            logAction(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_WATERING, null,
                    "SKIP", "dnevnoj limit poliva", "skipped", null, now);
            markState(state, "limited", "dnevnoj limit poliva", false, now);
            return;
        }

        AutomationResourceBindingEntity pumpBinding = resource(AutomationData.SCOPE_BOX, box.getId(), AutomationData.ROLE_WATER_PUMP);
        try {
            pumpFacade.start(
                    pumpBinding.getNativePumpId(),
                    new PumpFacade.PumpWateringRequest(runSeconds, null, null, null),
                    SYSTEM_ADMIN
            );
            logAction(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_WATERING, pumpBinding,
                    "PUMP_START", "vlazhnost' " + moisture.value() + "%", "published", runSeconds, now);
            markState(state, "active", null, false, now);
        } catch (RuntimeException ex) {
            logAction(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_WATERING, pumpBinding,
                    "PUMP_START", ex.getMessage(), "error", runSeconds, now);
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
                        .map(AutomationBoxPlantEntity::getPlantId)
                        .map(catalog.plantsById::get)
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
            return new ResourceStatus(true, null, sensor.lastValue(), sensor.lastTs(), sensor.label());
        }
        if (AutomationData.SOURCE_NATIVE_PUMP.equals(binding.getSourceType())) {
            AutomationData.NativePump pump = catalog.pumpsById.get(binding.getNativePumpId());
            if (pump == null) {
                return ResourceStatus.notReady("native pump ne naiden");
            }
            return new ResourceStatus(true, null, pump.isRunning(), null, pump.label());
        }
        if (AutomationData.SOURCE_ZIGBEE_DEVICE.equals(binding.getSourceType())) {
            AutomationData.ZigbeeDevice device = catalog.zigbeeByIeee.get(binding.getZigbeeIeeeAddress());
            if (device == null) {
                return ResourceStatus.notReady("Zigbee ustrojstvo ne naideno");
            }
            Object value = readZigbeeFeatureValue(device, binding.getZigbeeProperty());
            LocalDateTime ts = device.lastStateAt();
            return new ResourceStatus(true, null, value, ts, device.friendlyName());
        }
        return ResourceStatus.notReady("neizvestnyj source_type");
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
        if (device == null || property == null) {
            return null;
        }
        for (AutomationData.ZigbeeFeature feature : device.metrics()) {
            if (property.equals(feature.property())) {
                return feature.value();
            }
        }
        for (AutomationData.ZigbeeFeature feature : device.controls()) {
            if (property.equals(feature.property())) {
                return feature.value();
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
                    .map(this::toNativeSensorData)
                    .toList();
            sensors.forEach(sensor -> sensorsById.put(sensor.id(), sensor));
            List<AutomationData.NativePump> pumps = pumpFacade.listByDeviceId(summary.id(), shadow).stream()
                    .map(this::toNativePumpData)
                    .toList();
            pumps.forEach(pump -> pumpsById.put(pump.id(), pump));
            nativeDevices.add(new AutomationData.NativeDevice(
                    summary.id(),
                    summary.deviceId(),
                    summary.name(),
                    summary.isOnline(),
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

    private AutomationData.NativeSensor toNativeSensorData(SensorView sensor) {
        return new AutomationData.NativeSensor(
                sensor.id(),
                sensor.deviceId(),
                sensor.type() != null ? sensor.type().name() : null,
                sensor.channel(),
                sensor.label(),
                sensor.status() != null ? sensor.status().name() : null,
                sensor.lastValue(),
                sensor.lastTs()
        );
    }

    private AutomationData.NativePump toNativePumpData(PumpView pump) {
        return new AutomationData.NativePump(
                pump.id(),
                pump.deviceId(),
                pump.channel(),
                pump.label(),
                pump.isRunning()
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
                config.put("run_seconds", 30);
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

    private record ResourceStatus(boolean ready, String reason, Object value, LocalDateTime ts, String label) {
        static ResourceStatus notReady(String reason) {
            return new ResourceStatus(false, reason, null, null, null);
        }
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
