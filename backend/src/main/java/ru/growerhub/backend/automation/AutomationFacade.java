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
import java.util.UUID;
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
import ru.growerhub.backend.plant.contract.PlantInfo;
import ru.growerhub.backend.pump.PumpFacade;
import ru.growerhub.backend.pump.contract.PumpSessionData;
import ru.growerhub.backend.pump.contract.PumpStartResult;
import ru.growerhub.backend.pump.contract.PumpView;
import ru.growerhub.backend.sensor.SensorFacade;
import ru.growerhub.backend.sensor.contract.SensorView;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeDeviceData;
import ru.growerhub.backend.zigbee.contract.ZigbeeFeatureData;
import ru.growerhub.backend.zigbee.contract.ZigbeeOwnedDeviceData;

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
    private static final List<String> GREENHOUSE_ROLES = List.of(
            AutomationData.ROLE_AC_SWITCH,
            AutomationData.ROLE_AIR_TEMPERATURE_SENSOR,
            AutomationData.ROLE_AIR_HUMIDITY_SENSOR,
            AutomationData.ROLE_SOIL_MOISTURE_SENSOR,
            AutomationData.ROLE_LEAK_SENSOR,
            AutomationData.ROLE_EXHAUST_SWITCH,
            AutomationData.ROLE_LIGHT_SWITCH,
            AutomationData.ROLE_WATER_PUMP
    );
    private static final List<String> FARM_ROLES = List.of(AutomationData.ROLE_AC_SWITCH);
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
    public AutomationData.ProductAnalyticsSnapshot getProductAnalytics() {
        List<AutomationRoomEntity> rooms = roomRepository.findAll();
        List<AutomationBoxEntity> boxes = boxRepository.findAll();
        Map<Integer, Integer> roomOwners = rooms.stream()
                .collect(Collectors.toMap(AutomationRoomEntity::getId, AutomationRoomEntity::getUserId));
        Map<Integer, Integer> boxOwners = boxes.stream()
                .collect(Collectors.toMap(AutomationBoxEntity::getId, box -> box.getRoom().getUserId()));
        Set<Integer> usersWithZone = boxes.stream()
                .map(box -> box.getRoom().getUserId())
                .collect(Collectors.toSet());
        Set<Integer> usersWithAutomation = new HashSet<>();
        long enabled = 0;

        for (AutomationScenarioConfigEntity scenario : configRepository.findAll()) {
            if (!scenario.isEnabled()) {
                continue;
            }
            Integer userId = AutomationData.SCOPE_ROOM.equals(scenario.getScopeType())
                    ? roomOwners.get(scenario.getScopeId())
                    : boxOwners.get(scenario.getScopeId());
            if (userId != null) {
                usersWithAutomation.add(userId);
                enabled++;
            }
        }

        return new AutomationData.ProductAnalyticsSnapshot(
                usersWithZone,
                usersWithAutomation,
                boxes.size(),
                enabled
        );
    }

    public AutomationData.Overview getOverview(AuthenticatedUser user) {
        requireAuthenticated(user);
        Catalog catalog = buildCatalog(user);
        List<AutomationRoomEntity> rooms = user.isAdmin()
                ? roomRepository.findAllByOrderByNameAscIdAsc()
                : roomRepository.findAllByUserIdOrderByNameAscIdAsc(user.id());
        List<AutomationBoxEntity> boxes = user.isAdmin()
                ? boxRepository.findAllByOrderByNameAscIdAsc()
                : boxRepository.findAllByRoom_UserIdOrderByNameAscIdAsc(user.id());
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
                overviewActionLogs(user, rooms, boxes),
                new AutomationData.Settings(
                        settings.getTimezone(),
                        settings.getStaleSensorMinutes(),
                        settings.getManualOverrideMinutes(),
                        settings.getResourceOfflineMinutes()
                )
        );
    }

    @Transactional(readOnly = true)
    public AutomationData.FarmsOverview getFarmsOverview(AuthenticatedUser user) {
        requireAuthenticated(user);
        return buildFarmsOverview(user);
    }

    public AutomationData.FarmsOverview createUserFarm(
            AuthenticatedUser user,
            AutomationData.SaveRoomRequest request
    ) {
        requireAuthenticated(user);
        LocalDateTime now = nowUtc();
        AutomationRoomEntity farm = AutomationRoomEntity.create(
                user.id(),
                requiredName(request != null ? request.name() : null),
                now
        );
        if (request != null && request.enabled() != null) {
            farm.setEnabled(request.enabled());
        }
        roomRepository.save(farm);
        return buildFarmsOverview(user);
    }

    public AutomationData.FarmsOverview updateUserFarm(
            AuthenticatedUser user,
            Integer farmId,
            AutomationData.SaveRoomRequest request
    ) {
        AutomationRoomEntity farm = requireOwnedFarm(user, farmId);
        if (request != null && request.name() != null) {
            farm.setName(requiredName(request.name()));
        }
        if (request != null && request.enabled() != null) {
            farm.setEnabled(request.enabled());
        }
        farm.setUpdatedAt(nowUtc());
        roomRepository.save(farm);
        return buildFarmsOverview(user);
    }

    public void deleteUserFarm(AuthenticatedUser user, Integer farmId) {
        deleteRoomWithResources(requireOwnedFarm(user, farmId));
    }

    public AutomationData.FarmsOverview createGreenhouse(
            AuthenticatedUser user,
            Integer farmId,
            AutomationData.SaveBoxRequest request
    ) {
        AutomationRoomEntity farm = requireOwnedFarm(user, farmId);
        LocalDateTime now = nowUtc();
        AutomationBoxEntity greenhouse = AutomationBoxEntity.create(
                farm,
                requiredName(request != null ? request.name() : null),
                now
        );
        if (request != null && request.enabled() != null) {
            greenhouse.setEnabled(request.enabled());
        }
        boxRepository.save(greenhouse);
        return buildFarmsOverview(user);
    }

    public AutomationData.FarmsOverview updateGreenhouse(
            AuthenticatedUser user,
            Integer greenhouseId,
            AutomationData.SaveGreenhouseRequest request
    ) {
        AutomationBoxEntity greenhouse = requireOwnedGreenhouse(user, greenhouseId);
        AutomationRoomEntity previousFarm = greenhouse.getRoom();
        if (request != null && request.farmId() != null
                && !Objects.equals(request.farmId(), greenhouse.getRoomId())) {
            greenhouse.setRoom(requireOwnedFarm(user, request.farmId()));
        }
        if (request != null && request.name() != null) {
            greenhouse.setName(requiredName(request.name()));
        }
        if (request != null && request.enabled() != null) {
            greenhouse.setEnabled(request.enabled());
        }
        greenhouse.setUpdatedAt(nowUtc());
        boxRepository.save(greenhouse);
        boxRepository.flush();
        if (!Objects.equals(previousFarm.getId(), greenhouse.getRoomId())) {
            synchronizeFarmClimateScenario(previousFarm);
            synchronizeFarmClimateScenario(greenhouse.getRoom());
        }
        return buildFarmsOverview(user);
    }

    public void deleteGreenhouse(AuthenticatedUser user, Integer greenhouseId) {
        AutomationBoxEntity greenhouse = requireOwnedGreenhouse(user, greenhouseId);
        AutomationRoomEntity farm = greenhouse.getRoom();
        Integer pumpId = automationPumpId(greenhouse.getId());
        deleteBoxResources(greenhouse.getId());
        boxRepository.delete(greenhouse);
        boxRepository.flush();
        synchronizeFarmClimateScenario(farm);
        if (pumpId != null) {
            syncAutomationPump(pumpId);
        }
    }

    public AutomationData.FarmsOverview replaceUserFarmSlots(
            AuthenticatedUser user,
            Integer farmId,
            AutomationData.SaveZoneSlotsRequest request
    ) {
        AutomationRoomEntity farm = requireOwnedFarm(user, farmId);
        List<AutomationData.ResourceBindingRequest> slots =
                request != null && request.slots() != null ? request.slots() : List.of();
        validateSlotRoles(slots, FARM_ROLES, "фермы");
        reassignConflictingSlots(
                user,
                farm,
                null,
                slots,
                request != null && Boolean.TRUE.equals(request.reassign())
        );
        replaceResources(
                AutomationData.SCOPE_ROOM,
                farm.getId(),
                new AutomationData.SaveResourcesRequest(slots),
                buildOwnedCatalog(user)
        );
        return buildFarmsOverview(user);
    }

    public AutomationData.FarmsOverview replaceGreenhouseSlots(
            AuthenticatedUser user,
            Integer greenhouseId,
            AutomationData.SaveZoneSlotsRequest request
    ) {
        AutomationBoxEntity greenhouse = requireOwnedGreenhouse(user, greenhouseId);
        List<AutomationData.ResourceBindingRequest> slots =
                request != null && request.slots() != null ? request.slots() : List.of();
        validateSlotRoles(slots, GREENHOUSE_ROLES, "теплицы");
        reassignConflictingSlots(
                user,
                null,
                greenhouse,
                slots,
                request != null && Boolean.TRUE.equals(request.reassign())
        );
        Integer previousPumpId = automationPumpId(greenhouse.getId());
        replaceResources(
                AutomationData.SCOPE_BOX,
                greenhouse.getId(),
                new AutomationData.SaveResourcesRequest(slots),
                buildOwnedCatalog(user)
        );
        syncAffectedPumps(previousPumpId, automationPumpId(greenhouse.getId()));
        return buildFarmsOverview(user);
    }

    public AutomationData.FarmsOverview replaceGreenhousePlants(
            AuthenticatedUser user,
            Integer greenhouseId,
            AutomationData.SavePlantsRequest request
    ) {
        replaceZonePlants(user, requireOwnedGreenhouse(user, greenhouseId), request);
        return buildFarmsOverview(user);
    }

    public AutomationData.FarmsOverview replaceUserFarmScenarios(
            AuthenticatedUser user,
            Integer farmId,
            AutomationData.SaveScenariosRequest request
    ) {
        AutomationRoomEntity farm = requireOwnedFarm(user, farmId);
        replaceScenarios(AutomationData.SCOPE_ROOM, farm.getId(), ROOM_SCENARIOS, request);
        return buildFarmsOverview(user);
    }

    public AutomationData.FarmsOverview replaceGreenhouseScenarios(
            AuthenticatedUser user,
            Integer greenhouseId,
            AutomationData.SaveScenariosRequest request
    ) {
        AutomationBoxEntity greenhouse = requireOwnedGreenhouse(user, greenhouseId);
        List<AutomationData.ScenarioConfigRequest> scenarios =
                request != null && request.scenarios() != null ? request.scenarios() : List.of();
        validatePublicScenarioReadiness(
                greenhouse.getRoom(),
                greenhouse,
                scenarios,
                buildOwnedCatalog(user)
        );
        replaceScenarios(
                AutomationData.SCOPE_BOX,
                greenhouse.getId(),
                BOX_SCENARIOS,
                new AutomationData.SaveScenariosRequest(scenarios)
        );
        synchronizeFarmClimateScenario(greenhouse.getRoom());
        return buildFarmsOverview(user);
    }

    @Transactional(readOnly = true)
    public AutomationData.FarmOverview getFarmOverview(AuthenticatedUser user) {
        requireAuthenticated(user);
        return buildFarmOverview(user);
    }

    public AutomationData.FarmOverview createFarm(
            AuthenticatedUser user,
            AutomationData.SaveFarmRequest request
    ) {
        requireAuthenticated(user);
        if (!roomRepository.findAllByUserIdOrderByNameAscIdAsc(user.id()).isEmpty()) {
            throw new DomainException("conflict", "Ферма уже создана");
        }
        LocalDateTime now = nowUtc();
        roomRepository.save(AutomationRoomEntity.create(
                user.id(),
                requiredName(request != null ? request.name() : null),
                now
        ));
        return buildFarmOverview(user);
    }

    public AutomationData.FarmOverview updateFarm(
            AuthenticatedUser user,
            AutomationData.SaveFarmRequest request
    ) {
        AutomationRoomEntity farm = requireLegacyFarm(user);
        farm.setName(requiredName(request != null ? request.name() : null));
        farm.setUpdatedAt(nowUtc());
        roomRepository.save(farm);
        return buildFarmOverview(user);
    }

    public AutomationData.FarmOverview createFarmZone(
            AuthenticatedUser user,
            AutomationData.SaveRoomRequest request
    ) {
        AutomationRoomEntity farm = requireLegacyFarm(user);
        LocalDateTime now = nowUtc();
        AutomationBoxEntity greenhouse = AutomationBoxEntity.create(
                farm,
                requiredName(request != null ? request.name() : null),
                now
        );
        if (request != null && request.enabled() != null) {
            greenhouse.setEnabled(request.enabled());
        }
        boxRepository.save(greenhouse);
        return buildFarmOverview(user);
    }

    public AutomationData.FarmOverview updateFarmZone(
            AuthenticatedUser user,
            Integer zoneId,
            AutomationData.SaveRoomRequest request
    ) {
        AutomationBoxEntity greenhouse = requireLegacyZone(user, zoneId);
        if (request != null && request.name() != null) {
            greenhouse.setName(requiredName(request.name()));
        }
        if (request != null && request.enabled() != null) {
            greenhouse.setEnabled(request.enabled());
        }
        greenhouse.setUpdatedAt(nowUtc());
        boxRepository.save(greenhouse);
        return buildFarmOverview(user);
    }

    public void deleteFarmZone(AuthenticatedUser user, Integer zoneId) {
        deleteBox(user, requireLegacyZone(user, zoneId).getId());
    }

    public AutomationData.FarmOverview replaceFarmZoneSlots(
            AuthenticatedUser user,
            Integer zoneId,
            AutomationData.SaveZoneSlotsRequest request
    ) {
        AutomationBoxEntity box = requireLegacyZone(user, zoneId);
        List<AutomationData.ResourceBindingRequest> slots =
                request != null && request.slots() != null ? request.slots() : List.of();
        validateSlotRoles(slots, GREENHOUSE_ROLES, "теплицы");
        reassignConflictingSlots(
                user,
                null,
                box,
                slots,
                request != null && Boolean.TRUE.equals(request.reassign())
        );

        Catalog catalog = buildOwnedCatalog(user);
        Integer previousPumpId = automationPumpId(box.getId());
        replaceResources(
                AutomationData.SCOPE_BOX,
                box.getId(),
                new AutomationData.SaveResourcesRequest(slots),
                catalog
        );
        Integer nextPumpId = automationPumpId(box.getId());
        syncAffectedPumps(previousPumpId, nextPumpId);
        return buildFarmOverview(user);
    }

    public AutomationData.FarmOverview replaceFarmZonePlants(
            AuthenticatedUser user,
            Integer zoneId,
            AutomationData.SavePlantsRequest request
    ) {
        replaceZonePlants(user, requireLegacyZone(user, zoneId), request);
        return buildFarmOverview(user);
    }

    public AutomationData.FarmOverview replaceFarmZoneScenarios(
            AuthenticatedUser user,
            Integer zoneId,
            AutomationData.SaveScenariosRequest request
    ) {
        AutomationBoxEntity box = requireLegacyZone(user, zoneId);
        AutomationRoomEntity room = box.getRoom();
        List<AutomationData.ScenarioConfigRequest> scenarios =
                request != null && request.scenarios() != null ? request.scenarios() : List.of();
        validatePublicScenarioReadiness(room, box, scenarios, buildOwnedCatalog(user));
        replaceScenarios(
                AutomationData.SCOPE_BOX,
                box.getId(),
                BOX_SCENARIOS,
                new AutomationData.SaveScenariosRequest(scenarios)
        );
        synchronizeFarmClimateScenario(room);
        return buildFarmOverview(user);
    }

    @Transactional(readOnly = true)
    public Map<Integer, AutomationData.ZoneReference> getPlantZones(
            AuthenticatedUser user,
            List<Integer> plantIds
    ) {
        requireAuthenticated(user);
        if (plantIds == null || plantIds.isEmpty()) {
            return Map.of();
        }
        Set<Integer> requestedIds = new HashSet<>(plantIds);
        Map<Integer, AutomationBoxEntity> boxesById = boxRepository
                .findAllByRoom_UserIdOrderByNameAscIdAsc(user.id())
                .stream()
                .collect(Collectors.toMap(AutomationBoxEntity::getId, Function.identity()));
        Map<Integer, AutomationData.ZoneReference> result = new HashMap<>();
        for (AutomationBoxPlantEntity binding : boxPlantRepository.findAllByPlantIdIn(plantIds)) {
            if (!requestedIds.contains(binding.getPlantId())) {
                continue;
            }
            AutomationBoxEntity greenhouse = boxesById.get(binding.getBoxId());
            if (greenhouse != null) {
                result.put(
                        binding.getPlantId(),
                        new AutomationData.ZoneReference(
                                greenhouse.getId(),
                                greenhouse.getName(),
                                greenhouse.getRoomId(),
                                greenhouse.getRoom().getName()
                        )
                );
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public AutomationData.ZoneReference getPlantZone(AuthenticatedUser user, Integer plantId) {
        return getPlantZones(user, plantId != null ? List.of(plantId) : List.of()).get(plantId);
    }

    public PlantInfo createPlantWithPlacement(
            AuthenticatedUser user,
            PlantFacade.PlantCreateCommand command,
            Integer zoneId
    ) {
        requireAuthenticated(user);
        PlantInfo plant = plantFacade.createPlant(command, user);
        movePlantToZone(user, plant.id(), zoneId, true);
        return plant;
    }

    public PlantInfo updatePlantWithPlacement(
            AuthenticatedUser user,
            Integer plantId,
            PlantFacade.PlantUpdateCommand command,
            boolean zoneProvided,
            Integer zoneId
    ) {
        requireAuthenticated(user);
        PlantInfo plant = plantFacade.updatePlant(plantId, command, user);
        if (zoneProvided) {
            movePlantToZone(user, plant.id(), zoneId, true);
        }
        return plant;
    }

    public void deletePlantWithPlacement(AuthenticatedUser user, Integer plantId) {
        requireAuthenticated(user);
        plantFacade.requireOwnedPlantInfo(plantId, user);
        movePlantToZone(user, plantId, null, false);
        plantFacade.deletePlant(plantId, user);
    }

    public AutomationData.Overview createRoom(AuthenticatedUser user, AutomationData.SaveRoomRequest request) {
        requireAuthenticated(user);
        LocalDateTime now = nowUtc();
        AutomationRoomEntity room = AutomationRoomEntity.create(
                user.id(),
                requiredName(request != null ? request.name() : null),
                now
        );
        if (request != null && request.enabled() != null) {
            room.setEnabled(request.enabled());
        }
        roomRepository.save(room);
        return getOverview(user);
    }

    public AutomationData.Overview updateRoom(
            AuthenticatedUser user,
            Integer roomId,
            AutomationData.SaveRoomRequest request
    ) {
        AutomationRoomEntity room = requireRoom(user, roomId);
        if (request != null && request.name() != null) {
            room.setName(requiredName(request.name()));
        }
        if (request != null && request.enabled() != null) {
            room.setEnabled(request.enabled());
        }
        room.setUpdatedAt(nowUtc());
        roomRepository.save(room);
        return getOverview(user);
    }

    public void deleteRoom(AuthenticatedUser user, Integer roomId) {
        AutomationRoomEntity room = requireRoom(user, roomId);
        deleteRoomWithResources(room);
    }

    private void deleteRoomWithResources(AutomationRoomEntity room) {
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
        boxRepository.deleteAll(boxes);
        boxRepository.flush();
        roomRepository.delete(room);
        roomRepository.flush();
        pumpIds.forEach(this::syncAutomationPump);
    }

    public AutomationData.Overview createBox(
            AuthenticatedUser user,
            Integer roomId,
            AutomationData.SaveBoxRequest request
    ) {
        AutomationRoomEntity room = requireRoom(user, roomId);
        LocalDateTime now = nowUtc();
        AutomationBoxEntity box = AutomationBoxEntity.create(room, requiredName(request != null ? request.name() : null), now);
        if (request != null && request.enabled() != null) {
            box.setEnabled(request.enabled());
        }
        boxRepository.save(box);
        return getOverview(user);
    }

    public AutomationData.Overview updateBox(
            AuthenticatedUser user,
            Integer boxId,
            AutomationData.SaveBoxRequest request
    ) {
        AutomationBoxEntity box = requireBox(user, boxId);
        if (request != null && request.name() != null) {
            box.setName(requiredName(request.name()));
        }
        if (request != null && request.enabled() != null) {
            box.setEnabled(request.enabled());
        }
        box.setUpdatedAt(nowUtc());
        boxRepository.save(box);
        return getOverview(user);
    }

    public void deleteBox(AuthenticatedUser user, Integer boxId) {
        AutomationBoxEntity box = requireBox(user, boxId);
        AutomationRoomEntity farm = box.getRoom();
        Integer pumpId = automationPumpId(box.getId());
        deleteBoxResources(box.getId());
        boxRepository.delete(box);
        boxRepository.flush();
        synchronizeFarmClimateScenario(farm);
        if (pumpId != null) {
            syncAutomationPump(pumpId);
        }
    }

    public AutomationData.Overview replaceBoxPlants(
            AuthenticatedUser user,
            Integer boxId,
            AutomationData.SavePlantsRequest request
    ) {
        AutomationBoxEntity box = requireBox(user, boxId);
        Catalog catalog = buildCatalog(user);
        LocalDateTime now = nowUtc();
        List<AutomationData.BoxPlantRequest> items = boxPlantRequests(request);
        List<Integer> plantIds = items.stream().map(AutomationData.BoxPlantRequest::plantId).toList();
        Set<Integer> uniquePlantIds = new HashSet<>(plantIds);
        if (uniquePlantIds.size() != plantIds.size()) {
            throw new DomainException("bad_request", "Значения plant_ids должны быть уникальными");
        }
        for (AutomationData.BoxPlantRequest item : items) {
            Integer plantId = item.plantId();
            if (plantId == null) {
                throw new DomainException("bad_request", "Поле plant_id обязательно");
            }
            if (item.rateMlPerHour() != null && item.rateMlPerHour() <= 0) {
                throw new DomainException("bad_request", "Поле rate_ml_per_hour должно быть больше нуля");
            }
            if (!catalog.plantsById.containsKey(plantId)) {
                throw new DomainException("not_found", "Растение не найдено");
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
        return getOverview(user);
    }

    private List<AutomationData.BoxPlantRequest> boxPlantRequests(AutomationData.SavePlantsRequest request) {
        if (request == null) {
            return List.of();
        }
        if (request.items() != null) {
            if (request.items().stream().anyMatch(Objects::isNull)) {
                throw new DomainException("bad_request", "Список items не должен содержать null");
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

    public AutomationData.Overview replaceRoomResources(
            AuthenticatedUser user,
            Integer roomId,
            AutomationData.SaveResourcesRequest request
    ) {
        requireRoom(user, roomId);
        replaceResources(AutomationData.SCOPE_ROOM, roomId, request, buildCatalog(user));
        return getOverview(user);
    }

    public AutomationData.Overview replaceBoxResources(
            AuthenticatedUser user,
            Integer boxId,
            AutomationData.SaveResourcesRequest request
    ) {
        requireBox(user, boxId);
        Integer previousPumpId = automationPumpId(boxId);
        replaceResources(AutomationData.SCOPE_BOX, boxId, request, buildCatalog(user));
        Integer nextPumpId = automationPumpId(boxId);
        Set<Integer> affectedPumpIds = new HashSet<>();
        if (previousPumpId != null) {
            affectedPumpIds.add(previousPumpId);
        }
        if (nextPumpId != null) {
            affectedPumpIds.add(nextPumpId);
        }
        affectedPumpIds.forEach(this::syncAutomationPump);
        return getOverview(user);
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
                    "Укажите water_volume_l или duration_s для запуска полива"
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

    public AutomationData.Overview replaceRoomScenarios(
            AuthenticatedUser user,
            Integer roomId,
            AutomationData.SaveScenariosRequest request
    ) {
        requireRoom(user, roomId);
        replaceScenarios(AutomationData.SCOPE_ROOM, roomId, ROOM_SCENARIOS, request);
        return getOverview(user);
    }

    public AutomationData.Overview replaceBoxScenarios(
            AuthenticatedUser user,
            Integer boxId,
            AutomationData.SaveScenariosRequest request
    ) {
        requireBox(user, boxId);
        replaceScenarios(AutomationData.SCOPE_BOX, boxId, BOX_SCENARIOS, request);
        return getOverview(user);
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

    private void replaceResources(
            String scopeType,
            Integer scopeId,
            AutomationData.SaveResourcesRequest request,
            Catalog catalog
    ) {
        LocalDateTime now = nowUtc();
        List<AutomationData.ResourceBindingRequest> resources =
                request != null && request.resources() != null ? request.resources() : List.of();
        Set<String> seenRoles = new HashSet<>();
        List<AutomationResourceBindingEntity> next = new ArrayList<>();
        for (AutomationData.ResourceBindingRequest item : resources) {
            String role = normalizeRequired(item.role(), "role");
            if (!seenRoles.add(role)) {
                throw new DomainException("bad_request", "Роль ресурса должна быть уникальной");
            }
            validateRoleForScope(scopeType, role);
            validateResource(role, item, catalog);
            AutomationResourceBindingEntity entity = AutomationResourceBindingEntity.create(scopeType, scopeId, role, now);
            String sourceType = normalizeRequired(item.sourceType(), "source_type");
            entity.setSourceType(sourceType);
            entity.setNativeSensorId(item.nativeSensorId());
            entity.setNativePumpId(item.nativePumpId());
            if (AutomationData.SOURCE_ZIGBEE_DEVICE.equals(sourceType)) {
                UUID publicCoordinatorId = resolveCoordinatorPublicId(catalog, item.zigbeeCoordinatorId());
                Integer coordinatorId = catalog.coordinatorInternalByPublic.get(publicCoordinatorId);
                if (coordinatorId == null) {
                    throw new DomainException("bad_request", "Координатор Zigbee не найден");
                }
                entity.setZigbeeCoordinatorId(coordinatorId);
            }
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
                throw new DomainException("bad_request", "Тип сценария недоступен для этой области");
            }
            if (!seen.add(scenarioType)) {
                throw new DomainException("bad_request", "Тип сценария должен быть уникальным");
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
            throw new DomainException("bad_request", "Некорректный режим остановки");
        }
        if (!STOP_MODE_UNTIL_DRAIN.equals(stopMode)) {
            return;
        }
        if (number(cfg.get("max_run_minutes"), 0.0) <= 0.0) {
            throw new DomainException("bad_request", "Параметр max_run_minutes обязателен для режима until_drain");
        }
        if (number(cfg.get("pulse_run_minutes"), 3.0) <= 0.0 || number(cfg.get("pulse_pause_minutes"), 5.0) <= 0.0) {
            throw new DomainException("bad_request", "Интервалы импульсного полива должны быть больше нуля");
        }
        Catalog catalog = buildCatalog();
        Integer pumpId = automationPumpId(scopeId);
        if (pumpId == null || !hasConfiguredLeakSensorForPump(pumpId, null, catalog)) {
            throw new DomainException("bad_request", "Для режима until_drain нужен датчик LEAK_SENSOR");
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
        AutomationData.Readiness readiness = boxClimateReadiness(box.getId(), catalog);
        if (!box.isEnabled() || room == null || !room.isEnabled()) {
            markState(state, "disabled", "Теплица или ферма выключена", false, now);
            return;
        }
        if (config == null || !config.isEnabled()) {
            markState(state, "disabled", "Сценарий выключен", false, now);
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
            markState(state, "stale", "Нет актуальной температуры теплицы", state.isAcRequestActive(), now);
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
                    AutomationData.SCOPE_BOX, box.getId(), "Температура " + value + " °C", now, null);
            runtime.put("exhaust_desired_state", "ON");
        } else if (exhaustShouldBeOff) {
            sendSwitchIfNeeded(exhaustBinding, false, catalog, AutomationData.SCENARIO_BOX_CLIMATE,
                    AutomationData.SCOPE_BOX, box.getId(), "Температура " + value + " °C", now, null);
            runtime.put("exhaust_desired_state", "OFF");
        }

        boolean acRequest = state.isAcRequestActive();
        if (value > acAbove || (value > max && risingForFiveMinutes)) {
            acRequest = true;
        }
        if (value <= acClear) {
            acRequest = false;
        }

        AutomationResourceBindingEntity localAc =
                resource(AutomationData.SCOPE_BOX, box.getId(), AutomationData.ROLE_AC_SWITCH);
        ResourceStatus localAcStatus = resolveResourceStatus(localAc, catalog);
        if (localAcStatus.ready() && !localAcStatus.connectionWarning()) {
            int offDelayMinutes = integer(cfg.get("off_delay_minutes"), 5);
            int minToggleMinutes = integer(cfg.get("min_toggle_minutes"), 5);
            LocalDateTime lastLocalActionAt = parseDateTime(runtime.get("last_local_ac_action_at"));
            boolean localToggleAllowed = lastLocalActionAt == null
                    || !lastLocalActionAt.isAfter(now.minusMinutes(minToggleMinutes));
            if (acRequest) {
                runtime.put("last_local_ac_request_at", now.toString());
                if (localToggleAllowed && sendSwitchIfNeeded(
                        localAc,
                        true,
                        catalog,
                        AutomationData.SCENARIO_BOX_CLIMATE,
                        AutomationData.SCOPE_BOX,
                        box.getId(),
                        "Теплице требуется охлаждение",
                        now,
                        null
                )) {
                    runtime.put("last_local_ac_action_at", now.toString());
                }
            } else {
                LocalDateTime lastRequestAt = parseDateTime(runtime.get("last_local_ac_request_at"));
                if (localToggleAllowed
                        && lastRequestAt != null
                        && !lastRequestAt.isAfter(now.minusMinutes(offDelayMinutes))
                        && sendSwitchIfNeeded(
                            localAc,
                            false,
                            catalog,
                            AutomationData.SCENARIO_BOX_CLIMATE,
                            AutomationData.SCOPE_BOX,
                            box.getId(),
                            "Запрос теплицы на охлаждение снят",
                            now,
                            null
                    )) {
                    runtime.put("last_local_ac_action_at", now.toString());
                }
            }
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
        List<AutomationScenarioStateEntity> pendingRequests = allBoxes.stream()
                .filter(box -> Objects.equals(box.getRoomId(), room.getId()))
                .filter(box -> !canHandleCoolingLocally(box.getId(), catalog))
                .map(box -> stateRepository.findByScopeTypeAndScopeIdAndScenarioType(
                        AutomationData.SCOPE_BOX,
                        box.getId(),
                        AutomationData.SCENARIO_BOX_CLIMATE
                ).orElse(null))
                .filter(Objects::nonNull)
                .filter(AutomationScenarioStateEntity::isAcRequestActive)
                .toList();
        boolean hasRequest = !pendingRequests.isEmpty();
        Map<String, Object> runtime = runtimeMap(state);
        runtime.put("pending_request_count", pendingRequests.size());
        state.setRuntimeJson(writeJson(runtime));

        AutomationData.Readiness readiness = roomClimateReadiness(room.getId(), allBoxes, catalog);
        if (!room.isEnabled()) {
            markState(state, "disabled", "Ферма выключена", hasRequest, now);
            return;
        }
        if (config == null || !config.isEnabled()) {
            markState(state, "disabled", "Сценарий выключен", hasRequest, now);
            return;
        }
        if (!readiness.ready()) {
            markState(state, "unready", readiness.reason(), hasRequest, now);
            return;
        }

        Map<String, Object> cfg = configMap(config, AutomationData.SCENARIO_ROOM_CLIMATE);
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
                    AutomationData.SCOPE_ROOM, room.getId(), "Есть запрос теплицы на охлаждение", now, null);
        } else if (!hasRequest
                && lastRequestAt != null
                && !lastRequestAt.isAfter(now.minusMinutes(offDelayMinutes))
                && toggleAllowed) {
            sendSwitchIfNeeded(acBinding, false, catalog, AutomationData.SCENARIO_ROOM_CLIMATE,
                    AutomationData.SCOPE_ROOM, room.getId(), "Запросов теплиц на охлаждение нет", now, null);
        }
        state.setRuntimeJson(writeJson(runtime));
        markState(state, "active", null, hasRequest, now);
    }

    private void evaluateLightSchedule(AutomationBoxEntity box, Catalog catalog, LocalDateTime now) {
        AutomationScenarioConfigEntity config = configFor(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_LIGHT_SCHEDULE);
        AutomationScenarioStateEntity state = stateFor(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_LIGHT_SCHEDULE, now);
        AutomationData.Readiness readiness = lightReadiness(box.getId(), catalog);
        if (!box.isEnabled()) {
            markState(state, "disabled", "Теплица выключена", false, now);
            return;
        }
        if (config == null || !config.isEnabled()) {
            markState(state, "disabled", "Сценарий выключен", false, now);
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
                AutomationData.SCOPE_BOX, box.getId(), shouldBeOn ? "Расписание включения" : "Расписание выключения", now, null);
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
            markState(state, "disabled", "Теплица выключена", false, now);
            return;
        }
        if (config == null || !config.isEnabled()) {
            markState(state, "disabled", "Сценарий выключен", false, now);
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
            markState(state, "unready", "Нужен доступный датчик LEAK_SENSOR", false, now);
            return;
        }
        if (pumpBinding != null && pumpFacade.currentSession(pumpBinding.getNativePumpId()) != null) {
            markState(state, "active", null, false, now);
            return;
        }
        AutomationResourceBindingEntity soilBinding = resource(AutomationData.SCOPE_BOX, box.getId(), AutomationData.ROLE_SOIL_MOISTURE_SENSOR);
        SensorValue moisture = readSensorValue(soilBinding, catalog);
        if (moisture == null || moisture.value() == null || isStale(moisture.ts(), now)) {
            markState(state, "stale", "Нет актуальной влажности почвы", false, now);
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
                    "SKIP", "Минимальный интервал между поливами", "skipped", null, now);
            markState(state, "limited", "Минимальный интервал между поливами", false, now);
            return;
        }
        long usedToday = pumpFacade.boxStatistics(box.getId(), "day", 1, null).activeDurationS();
        if (usedToday + requestedRunSeconds > dailyMaxSeconds) {
            logAction(AutomationData.SCOPE_BOX, box.getId(), AutomationData.SCENARIO_WATERING, null,
                    "SKIP", "Дневной лимит полива", "skipped", null, now);
            markState(state, "limited", "Дневной лимит полива", false, now);
            return;
        }

        String reason = "Влажность " + moisture.value() + "%";
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

    private boolean sendSwitchIfNeeded(
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
            return false;
        }
        String desired = on ? defaultOnValue(binding.getOnValue()) : defaultOffValue(binding.getOffValue());
        Object current = readSwitchValue(binding, catalog);
        if (current != null && desired.equalsIgnoreCase(String.valueOf(current))) {
            return false;
        }
        String property = defaultCommandProperty(binding.getRole(), binding.getCommandProperty());
        try {
            zigbeeFacade.setAutomationDeviceProperty(
                    binding.getZigbeeCoordinatorId(),
                    binding.getZigbeeIeeeAddress(),
                    property,
                    desired
            );
            logAction(scopeType, scopeId, scenarioType, binding,
                    switchAction(binding.getRole(), on), reason, "published", durationS, now);
            AutomationScenarioStateEntity state = stateFor(scopeType, scopeId, scenarioType, now);
            state.setLastActionAt(now);
            stateRepository.save(state);
            return true;
        } catch (RuntimeException ex) {
            logAction(scopeType, scopeId, scenarioType, binding,
                    switchAction(binding.getRole(), on), ex.getMessage(), "error", durationS, now);
            return false;
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
        readiness.put(AutomationData.SCENARIO_BOX_CLIMATE, boxClimateReadiness(box.getId(), catalog));
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
                catalog.coordinatorPublicByInternal.get(binding.getZigbeeCoordinatorId()),
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
            case AutomationData.SCENARIO_BOX_CLIMATE -> boxClimateReadiness(scopeId, catalog);
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
            return new AutomationData.Readiness(false, "Нужен кондиционер фермы", roles);
        }
        if (ac.connectionWarning()) {
            return new AutomationData.Readiness(false, "Кондиционер фермы не в сети", roles);
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
            return new AutomationData.Readiness(false, "Нет включённых климатических сценариев теплиц", roles);
        }
        return new AutomationData.Readiness(true, null, roles);
    }

    private AutomationData.Readiness boxClimateReadiness(Integer boxId, Catalog catalog) {
        List<String> roles = List.of(AutomationData.ROLE_AIR_TEMPERATURE_SENSOR);
        ResourceStatus temperature = resolveResourceStatus(
                resource(AutomationData.SCOPE_BOX, boxId, AutomationData.ROLE_AIR_TEMPERATURE_SENSOR),
                catalog
        );
        if (!temperature.ready()) {
            return new AutomationData.Readiness(false, "Нужен датчик температуры воздуха", roles);
        }
        return new AutomationData.Readiness(true, null, roles);
    }

    private boolean canHandleCoolingLocally(Integer boxId, Catalog catalog) {
        ResourceStatus localAc = resolveResourceStatus(
                resource(AutomationData.SCOPE_BOX, boxId, AutomationData.ROLE_AC_SWITCH),
                catalog
        );
        return localAc.ready() && !localAc.connectionWarning();
    }

    private AutomationData.Readiness lightReadiness(Integer boxId, Catalog catalog) {
        List<String> roles = List.of(AutomationData.ROLE_LIGHT_SWITCH);
        ResourceStatus light = resolveResourceStatus(resource(AutomationData.SCOPE_BOX, boxId, AutomationData.ROLE_LIGHT_SWITCH), catalog);
        if (!light.ready()) {
            return new AutomationData.Readiness(false, "Нужен Zigbee-выключатель света", roles);
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
            return new AutomationData.Readiness(false, "Нужен датчик влажности почвы", roles);
        }
        ResourceStatus pump = resolveResourceStatus(resource(AutomationData.SCOPE_BOX, boxId, AutomationData.ROLE_WATER_PUMP), catalog);
        if (!pump.ready()) {
            return new AutomationData.Readiness(false, "Нужен насос, подключённый к GrowerHub", roles);
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
                return ResourceStatus.notReady("Датчик GrowerHub не найден");
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
                return ResourceStatus.notReady("Насос GrowerHub не найден");
            }
            return new ResourceStatus(true, null, pump.isRunning(), pump.lastSeenAt(), pump.label(), false);
        }
        if (AutomationData.SOURCE_ZIGBEE_DEVICE.equals(binding.getSourceType())) {
            AutomationData.ZigbeeDevice device = findZigbeeDevice(binding, catalog);
            if (device == null) {
                return ResourceStatus.notReady("Устройство Zigbee не найдено");
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
            AutomationData.ZigbeeDevice device = findZigbeeDevice(binding, catalog);
            Double value = asDouble(readZigbeeFeatureValue(device, binding.getZigbeeProperty()));
            return device != null ? new SensorValue(value, device.lastStateAt()) : null;
        }
        return null;
    }

    private Object readSwitchValue(AutomationResourceBindingEntity binding, Catalog catalog) {
        if (binding == null || !AutomationData.SOURCE_ZIGBEE_DEVICE.equals(binding.getSourceType())) {
            return null;
        }
        AutomationData.ZigbeeDevice device = findZigbeeDevice(binding, catalog);
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
        if (AutomationData.SOURCE_ZIGBEE_DEVICE.equals(sourceType)) {
            requireZigbeeDeviceReference(catalog, item.zigbeeCoordinatorId(), item.zigbeeIeeeAddress());
        }
        if (AutomationData.ROLE_WATER_PUMP.equals(role)) {
            if (!AutomationData.SOURCE_NATIVE_PUMP.equals(sourceType) || !catalog.pumpsById.containsKey(item.nativePumpId())) {
                throw new DomainException("bad_request", "В первой версии WATER_PUMP должен быть насосом GrowerHub");
            }
            return;
        }
        if (isSwitchRole(role)) {
            if (!AutomationData.SOURCE_ZIGBEE_DEVICE.equals(sourceType)) {
                throw new DomainException("bad_request", role + " в первой версии должен быть Zigbee-переключателем");
            }
            String property = defaultCommandProperty(role, item.commandProperty());
            if (!zigbeeHasWritableProperty(
                    catalog,
                    item.zigbeeCoordinatorId(),
                    item.zigbeeIeeeAddress(),
                    property
            )) {
                throw new DomainException("bad_request", role + " должен иметь управляемое свойство " + property);
            }
            return;
        }
        if (AutomationData.ROLE_LEAK_SENSOR.equals(role)) {
            if (!AutomationData.SOURCE_ZIGBEE_DEVICE.equals(sourceType)) {
                throw new DomainException("bad_request", "LEAK_SENSOR должен быть Zigbee-датчиком");
            }
            String property = defaultProperty(role, item.zigbeeProperty());
            if (!zigbeeHasReadableProperty(
                    catalog,
                    item.zigbeeCoordinatorId(),
                    item.zigbeeIeeeAddress(),
                    property
            )) {
                throw new DomainException("bad_request", "LEAK_SENSOR должен иметь читаемое свойство " + property);
            }
            return;
        }
        if (AutomationData.ROLE_AIR_TEMPERATURE_SENSOR.equals(role)
                || AutomationData.ROLE_AIR_HUMIDITY_SENSOR.equals(role)
                || AutomationData.ROLE_SOIL_MOISTURE_SENSOR.equals(role)) {
            String expectedType = expectedNativeSensorType(role);
            if (AutomationData.SOURCE_NATIVE_SENSOR.equals(sourceType)) {
                AutomationData.NativeSensor sensor = catalog.sensorsById.get(item.nativeSensorId());
                if (sensor == null || !expectedType.equals(sensor.type())) {
                    throw new DomainException("bad_request", role + " должен ссылаться на датчик " + expectedType);
                }
                return;
            }
            if (AutomationData.SOURCE_ZIGBEE_DEVICE.equals(sourceType)) {
                String property = defaultProperty(role, item.zigbeeProperty());
                if (!zigbeeHasReadableProperty(
                        catalog,
                        item.zigbeeCoordinatorId(),
                        item.zigbeeIeeeAddress(),
                        property
                )) {
                    throw new DomainException("bad_request", role + " должен иметь читаемое свойство " + property);
                }
                return;
            }
        }
        throw new DomainException("bad_request", "Некорректная привязка ресурса");
    }

    private AutomationData.ZigbeeDevice requireZigbeeDeviceReference(
            Catalog catalog,
            UUID coordinatorId,
            String ieeeAddress
    ) {
        UUID resolvedCoordinatorId = resolveCoordinatorPublicId(catalog, coordinatorId);
        if (resolvedCoordinatorId == null
                || !catalog.coordinatorInternalByPublic.containsKey(resolvedCoordinatorId)) {
            throw new DomainException("not_found", "Устройство Zigbee не найдено");
        }
        AutomationData.ZigbeeDevice device = findZigbeeDevice(catalog, resolvedCoordinatorId, ieeeAddress);
        if (device == null) {
            throw new DomainException("not_found", "Устройство Zigbee не найдено");
        }
        return device;
    }

    private boolean zigbeeHasWritableProperty(
            Catalog catalog,
            UUID coordinatorId,
            String ieeeAddress,
            String property
    ) {
        AutomationData.ZigbeeDevice device = findZigbeeDevice(catalog, coordinatorId, ieeeAddress);
        if (device == null || property == null) {
            return false;
        }
        return device.controls().stream().anyMatch(feature -> property.equals(feature.property()));
    }

    private boolean zigbeeHasReadableProperty(
            Catalog catalog,
            UUID coordinatorId,
            String ieeeAddress,
            String property
    ) {
        AutomationData.ZigbeeDevice device = findZigbeeDevice(catalog, coordinatorId, ieeeAddress);
        if (device == null || property == null) {
            return false;
        }
        return device.metrics().stream().anyMatch(feature -> property.equals(feature.property()))
                || device.controls().stream().anyMatch(feature -> property.equals(feature.property()));
    }

    private AutomationData.ZigbeeDevice findZigbeeDevice(
            AutomationResourceBindingEntity binding,
            Catalog catalog
    ) {
        if (binding == null) {
            return null;
        }
        UUID coordinatorId = catalog.coordinatorPublicByInternal.get(binding.getZigbeeCoordinatorId());
        return findZigbeeDevice(catalog, coordinatorId, binding.getZigbeeIeeeAddress());
    }

    private AutomationData.ZigbeeDevice findZigbeeDevice(
            Catalog catalog,
            UUID coordinatorId,
            String ieeeAddress
    ) {
        UUID resolvedCoordinatorId = resolveCoordinatorPublicId(catalog, coordinatorId);
        String key = zigbeeKey(resolvedCoordinatorId, blankToNull(ieeeAddress));
        return key != null ? catalog.zigbeeByKey.get(key) : null;
    }

    private UUID resolveCoordinatorPublicId(Catalog catalog, UUID requestedCoordinatorId) {
        if (requestedCoordinatorId != null) {
            return requestedCoordinatorId;
        }
        if (catalog.coordinatorInternalByPublic.size() == 1) {
            return catalog.coordinatorInternalByPublic.keySet().iterator().next();
        }
        return null;
    }

    private String zigbeeKey(UUID coordinatorId, String ieeeAddress) {
        if (coordinatorId == null || ieeeAddress == null || ieeeAddress.isBlank()) {
            return null;
        }
        return coordinatorId + "|" + ieeeAddress;
    }

    private AutomationData.FarmsOverview buildFarmsOverview(AuthenticatedUser user) {
        Catalog catalog = buildOwnedCatalog(user);
        List<AutomationRoomEntity> farms = roomRepository.findAllByUserIdOrderByNameAscIdAsc(user.id());
        List<AutomationBoxEntity> greenhouses =
                boxRepository.findAllByRoom_UserIdOrderByNameAscIdAsc(user.id());
        Map<Integer, List<AutomationBoxEntity>> greenhousesByFarm = greenhouses.stream()
                .collect(Collectors.groupingBy(AutomationBoxEntity::getRoomId));
        Map<Integer, List<AutomationBoxPlantEntity>> plantsByGreenhouse = groupPlants(greenhouses);
        Map<String, List<AutomationResourceBindingEntity>> resources = groupResources(farms, greenhouses);
        Map<String, List<AutomationScenarioConfigEntity>> configs = groupConfigs(farms, greenhouses);
        Map<String, List<AutomationScenarioStateEntity>> states = groupStates(farms, greenhouses);
        AutomationData.Settings publicSettings = new AutomationData.Settings(
                settings.getTimezone(),
                settings.getStaleSensorMinutes(),
                settings.getManualOverrideMinutes(),
                settings.getResourceOfflineMinutes()
        );

        List<AutomationData.UserFarm> farmData = farms.stream()
                .map(farm -> toUserFarmData(
                        farm,
                        greenhousesByFarm.getOrDefault(farm.getId(), List.of()),
                        plantsByGreenhouse,
                        resources,
                        configs,
                        states,
                        catalog
                ))
                .toList();
        return new AutomationData.FarmsOverview(
                farmData,
                catalog.toData(),
                overviewActionLogsOwned(farms, greenhouses),
                publicSettings
        );
    }

    private AutomationData.UserFarm toUserFarmData(
            AutomationRoomEntity farm,
            List<AutomationBoxEntity> greenhouses,
            Map<Integer, List<AutomationBoxPlantEntity>> plantsByGreenhouse,
            Map<String, List<AutomationResourceBindingEntity>> resources,
            Map<String, List<AutomationScenarioConfigEntity>> configs,
            Map<String, List<AutomationScenarioStateEntity>> states,
            Catalog catalog
    ) {
        List<AutomationData.Greenhouse> greenhouseData = greenhouses.stream()
                .map(greenhouse -> {
                    AutomationData.Box box = toBoxData(
                            greenhouse,
                            plantsByGreenhouse,
                            resources,
                            configs,
                            states,
                            catalog,
                            farm.getId()
                    );
                    return new AutomationData.Greenhouse(
                            box.id(),
                            farm.getId(),
                            box.name(),
                            box.enabled(),
                            box.plants(),
                            box.resources(),
                            box.scenarios(),
                            box.states(),
                            box.readiness(),
                            box.lastActions(),
                            box.createdAt(),
                            box.updatedAt()
                    );
                })
                .toList();
        AutomationData.Room room = toRoomData(
                farm,
                List.of(),
                resources,
                configs,
                states,
                catalog
        );
        return new AutomationData.UserFarm(
                room.id(),
                room.name(),
                room.enabled(),
                room.resources(),
                room.scenarios(),
                room.states(),
                greenhouseData,
                room.lastActions(),
                room.createdAt(),
                room.updatedAt()
        );
    }

    private AutomationData.FarmOverview buildFarmOverview(AuthenticatedUser user) {
        Catalog catalog = buildOwnedCatalog(user);
        List<AutomationRoomEntity> ownedFarms =
                roomRepository.findAllByUserIdOrderByNameAscIdAsc(user.id());
        AutomationData.Settings publicSettings = new AutomationData.Settings(
                settings.getTimezone(),
                settings.getStaleSensorMinutes(),
                settings.getManualOverrideMinutes(),
                settings.getResourceOfflineMinutes()
        );
        if (ownedFarms.isEmpty()) {
            return new AutomationData.FarmOverview(null, catalog.toData(), List.of(), publicSettings);
        }

        AutomationRoomEntity farm = ownedFarms.get(0);
        List<AutomationBoxEntity> boxes = boxRepository.findAllByRoom_UserIdOrderByNameAscIdAsc(user.id()).stream()
                .filter(box -> Objects.equals(box.getRoomId(), farm.getId()))
                .toList();
        Map<Integer, List<AutomationBoxPlantEntity>> plantsByBox = groupPlants(boxes);
        Map<String, List<AutomationResourceBindingEntity>> resources = groupResources(List.of(farm), boxes);
        Map<String, List<AutomationScenarioConfigEntity>> configs = groupConfigs(List.of(farm), boxes);
        Map<String, List<AutomationScenarioStateEntity>> states = groupStates(List.of(farm), boxes);

        List<AutomationData.Zone> zones = boxes.stream()
                .map(box -> toZoneData(
                    farm,
                    box,
                    plantsByBox,
                    resources,
                    configs,
                    states,
                    catalog
                ))
                .toList();
        AutomationData.Farm farmData = new AutomationData.Farm(
                farm.getId(),
                farm.getName(),
                zones,
                farm.getCreatedAt(),
                farm.getUpdatedAt()
        );
        return new AutomationData.FarmOverview(
                farmData,
                catalog.toData(),
                overviewActionLogsOwned(List.of(farm), boxes),
                publicSettings
        );
    }

    private AutomationData.Zone toZoneData(
            AutomationRoomEntity room,
            AutomationBoxEntity box,
            Map<Integer, List<AutomationBoxPlantEntity>> plantsByBox,
            Map<String, List<AutomationResourceBindingEntity>> resources,
            Map<String, List<AutomationScenarioConfigEntity>> configs,
            Map<String, List<AutomationScenarioStateEntity>> states,
            Catalog catalog
    ) {
        AutomationData.Box boxData = toBoxData(
                box,
                plantsByBox,
                resources,
                configs,
                states,
                catalog,
                room.getId()
        );
        List<AutomationData.ResourceBinding> slots = new ArrayList<>(boxData.resources());
        slots.sort((left, right) -> Integer.compare(
                GREENHOUSE_ROLES.indexOf(left.role()),
                GREENHOUSE_ROLES.indexOf(right.role())
        ));

        List<AutomationData.ScenarioConfig> roomScenarios = scenarioData(
                AutomationData.SCOPE_ROOM,
                room.getId(),
                ROOM_SCENARIOS,
                configs.getOrDefault(key(AutomationData.SCOPE_ROOM, room.getId()), List.of()),
                catalog,
                room.getId()
        );
        Map<String, Object> roomClimateConfig = roomScenarios.isEmpty()
                ? Map.of()
                : roomScenarios.get(0).config();
        List<AutomationData.ScenarioConfig> publicScenarios = boxData.scenarios().stream()
                .map(scenario -> mergeRoomClimateConfig(scenario, roomClimateConfig))
                .toList();

        List<AutomationData.ScenarioState> publicStates = new ArrayList<>(boxData.states());
        states.getOrDefault(key(AutomationData.SCOPE_ROOM, room.getId()), List.of()).stream()
                .map(this::toStateData)
                .forEach(publicStates::add);

        List<AutomationData.ActionLog> actions = new ArrayList<>(boxData.lastActions());
        actionLogRepository.findTop10ByScopeTypeAndScopeIdOrderByCreatedAtDesc(
                        AutomationData.SCOPE_ROOM,
                        room.getId()
                ).stream()
                .map(this::toActionLogData)
                .forEach(actions::add);
        actions = actions.stream()
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .limit(10)
                .toList();

        return new AutomationData.Zone(
                box.getId(),
                box.getName(),
                box.isEnabled(),
                boxData.plants(),
                slots,
                publicScenarios,
                publicStates,
                boxData.readiness(),
                actions,
                box.getCreatedAt(),
                box.getUpdatedAt()
        );
    }

    private AutomationData.ScenarioConfig mergeRoomClimateConfig(
            AutomationData.ScenarioConfig scenario,
            Map<String, Object> roomClimateConfig
    ) {
        if (!AutomationData.SCENARIO_BOX_CLIMATE.equals(scenario.scenarioType())) {
            return scenario;
        }
        Map<String, Object> merged = new LinkedHashMap<>(scenario.config());
        copyIfPresent(roomClimateConfig, merged, "off_delay_minutes");
        copyIfPresent(roomClimateConfig, merged, "min_toggle_minutes");
        return new AutomationData.ScenarioConfig(
                scenario.id(),
                scenario.scopeType(),
                scenario.scopeId(),
                scenario.scenarioType(),
                scenario.enabled(),
                merged,
                scenario.readiness(),
                scenario.createdAt(),
                scenario.updatedAt()
        );
    }

    private List<AutomationData.ActionLog> overviewActionLogsOwned(
            List<AutomationRoomEntity> rooms,
            List<AutomationBoxEntity> boxes
    ) {
        List<AutomationActionLogEntity> logs = new ArrayList<>();
        for (AutomationRoomEntity room : rooms) {
            logs.addAll(actionLogRepository.findTop10ByScopeTypeAndScopeIdOrderByCreatedAtDesc(
                    AutomationData.SCOPE_ROOM,
                    room.getId()
            ));
        }
        for (AutomationBoxEntity box : boxes) {
            logs.addAll(actionLogRepository.findTop10ByScopeTypeAndScopeIdOrderByCreatedAtDesc(
                    AutomationData.SCOPE_BOX,
                    box.getId()
            ));
        }
        return logs.stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .limit(20)
                .map(this::toActionLogData)
                .toList();
    }

    private AutomationRoomEntity requireLegacyFarm(AuthenticatedUser user) {
        requireAuthenticated(user);
        return roomRepository.findAllByUserIdOrderByNameAscIdAsc(user.id()).stream()
                .findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Ферма не найдена"));
    }

    private AutomationRoomEntity requireOwnedFarm(AuthenticatedUser user, Integer farmId) {
        requireAuthenticated(user);
        if (farmId == null) {
            throw new DomainException("bad_request", "Поле farm_id обязательно");
        }
        return roomRepository.findByIdAndUserId(farmId, user.id())
                .orElseThrow(() -> new DomainException("not_found", "Ферма не найдена"));
    }

    private AutomationBoxEntity requireOwnedGreenhouse(AuthenticatedUser user, Integer greenhouseId) {
        requireAuthenticated(user);
        if (greenhouseId == null) {
            throw new DomainException("bad_request", "Поле greenhouse_id обязательно");
        }
        return boxRepository.findByIdAndRoom_UserId(greenhouseId, user.id())
                .orElseThrow(() -> new DomainException("not_found", "Теплица не найдена"));
    }

    private AutomationBoxEntity requireLegacyZone(AuthenticatedUser user, Integer zoneId) {
        AutomationBoxEntity greenhouse = requireOwnedGreenhouse(user, zoneId);
        AutomationRoomEntity farm = requireLegacyFarm(user);
        if (!Objects.equals(greenhouse.getRoomId(), farm.getId())) {
            throw new DomainException("not_found", "Зона не найдена");
        }
        return greenhouse;
    }

    private void validateSlotRoles(
            List<AutomationData.ResourceBindingRequest> slots,
            List<String> allowedRoles,
            String scopeLabel
    ) {
        Set<String> roles = new HashSet<>();
        for (AutomationData.ResourceBindingRequest slot : slots) {
            if (slot == null) {
                throw new DomainException("bad_request", "Список slots не должен содержать null");
            }
            String role = normalizeRequired(slot.role(), "role");
            if (!allowedRoles.contains(role)) {
                throw new DomainException("bad_request", "Роль недоступна для " + scopeLabel);
            }
            if (!roles.add(role)) {
                throw new DomainException("bad_request", "Роль слота должна быть уникальной");
            }
        }
    }

    private void reassignConflictingSlots(
            AuthenticatedUser user,
            AutomationRoomEntity targetRoom,
            AutomationBoxEntity targetBox,
            List<AutomationData.ResourceBindingRequest> slots,
            boolean reassign
    ) {
        Catalog catalog = buildOwnedCatalog(user);
        Map<String, AutomationData.ResourceBindingRequest> requestedByKey = new LinkedHashMap<>();
        for (AutomationData.ResourceBindingRequest slot : slots) {
            String resourceKey = physicalResourceKey(slot, catalog);
            if (resourceKey == null) {
                continue;
            }
            if (requestedByKey.putIfAbsent(resourceKey, slot) != null) {
                throw new DomainException("bad_request", "Один физический канал нельзя назначить в несколько слотов");
            }
        }
        if (requestedByKey.isEmpty()) {
            return;
        }

        List<AutomationRoomEntity> rooms = roomRepository.findAllByUserIdOrderByNameAscIdAsc(user.id());
        List<AutomationBoxEntity> boxes = boxRepository.findAllByRoom_UserIdOrderByNameAscIdAsc(user.id());
        Map<Integer, AutomationRoomEntity> roomsById = rooms.stream()
                .collect(Collectors.toMap(AutomationRoomEntity::getId, Function.identity()));
        Map<Integer, AutomationRoomEntity> roomsByBoxId = boxes.stream()
                .collect(Collectors.toMap(
                        AutomationBoxEntity::getId,
                        box -> roomsById.get(box.getRoomId())
                ));
        Map<Integer, AutomationBoxEntity> boxesById = boxes.stream()
                .collect(Collectors.toMap(AutomationBoxEntity::getId, Function.identity()));
        List<AutomationResourceBindingEntity> existing = new ArrayList<>();
        List<Integer> roomIds = rooms.stream().map(AutomationRoomEntity::getId).toList();
        List<Integer> boxIds = boxes.stream().map(AutomationBoxEntity::getId).toList();
        if (!roomIds.isEmpty()) {
            existing.addAll(resourceRepository.findAllByScopeTypeAndScopeIdInOrderByIdAsc(
                    AutomationData.SCOPE_ROOM,
                    roomIds
            ));
        }
        if (!boxIds.isEmpty()) {
            existing.addAll(resourceRepository.findAllByScopeTypeAndScopeIdInOrderByIdAsc(
                    AutomationData.SCOPE_BOX,
                    boxIds
            ));
        }

        List<AutomationResourceBindingEntity> conflicts = new ArrayList<>();
        for (AutomationResourceBindingEntity binding : existing) {
            boolean targetBinding = AutomationData.SCOPE_ROOM.equals(binding.getScopeType())
                    ? targetRoom != null && Objects.equals(binding.getScopeId(), targetRoom.getId())
                    : targetBox != null && Objects.equals(binding.getScopeId(), targetBox.getId());
            if (targetBinding) {
                continue;
            }
            String resourceKey = physicalResourceKey(binding, catalog);
            if (resourceKey != null && requestedByKey.containsKey(resourceKey)) {
                conflicts.add(binding);
            }
        }
        if (conflicts.isEmpty()) {
            return;
        }
        if (!reassign) {
            AutomationResourceBindingEntity conflict = conflicts.get(0);
            AutomationRoomEntity originFarm = AutomationData.SCOPE_ROOM.equals(conflict.getScopeType())
                    ? roomsById.get(conflict.getScopeId())
                    : roomsByBoxId.get(conflict.getScopeId());
            AutomationBoxEntity originGreenhouse = AutomationData.SCOPE_BOX.equals(conflict.getScopeType())
                    ? boxesById.get(conflict.getScopeId())
                    : null;
            String scopeName = originGreenhouse != null
                    ? originGreenhouse.getName()
                    : originFarm != null ? originFarm.getName() : "другое место";
            throw new DomainException(
                    "conflict",
                    "Ресурс уже назначен слоту " + conflict.getRole() + " в «" + scopeName + "»"
            );
        }
        Set<Integer> affectedPumps = conflicts.stream()
                .map(AutomationResourceBindingEntity::getNativePumpId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        resourceRepository.deleteAll(conflicts);
        resourceRepository.flush();
        affectedPumps.forEach(this::syncAutomationPump);
    }

    private String physicalResourceKey(
            AutomationData.ResourceBindingRequest request,
            Catalog catalog
    ) {
        String sourceType = normalizeRequired(request.sourceType(), "source_type");
        if (AutomationData.SOURCE_NATIVE_SENSOR.equals(sourceType)) {
            return request.nativeSensorId() != null ? "sensor:" + request.nativeSensorId() : null;
        }
        if (AutomationData.SOURCE_NATIVE_PUMP.equals(sourceType)) {
            return request.nativePumpId() != null ? "pump:" + request.nativePumpId() : null;
        }
        if (!AutomationData.SOURCE_ZIGBEE_DEVICE.equals(sourceType)) {
            return null;
        }
        UUID coordinatorId = resolveCoordinatorPublicId(catalog, request.zigbeeCoordinatorId());
        String ieee = blankToNull(request.zigbeeIeeeAddress());
        String property = isSwitchRole(request.role())
                ? defaultCommandProperty(request.role(), request.commandProperty())
                : defaultProperty(request.role(), request.zigbeeProperty());
        if (coordinatorId == null || ieee == null || property == null) {
            return null;
        }
        return "zigbee:" + coordinatorId + ":" + ieee.toLowerCase(Locale.ROOT) + ":" + property;
    }

    private String physicalResourceKey(
            AutomationResourceBindingEntity binding,
            Catalog catalog
    ) {
        if (AutomationData.SOURCE_NATIVE_SENSOR.equals(binding.getSourceType())) {
            return binding.getNativeSensorId() != null ? "sensor:" + binding.getNativeSensorId() : null;
        }
        if (AutomationData.SOURCE_NATIVE_PUMP.equals(binding.getSourceType())) {
            return binding.getNativePumpId() != null ? "pump:" + binding.getNativePumpId() : null;
        }
        if (!AutomationData.SOURCE_ZIGBEE_DEVICE.equals(binding.getSourceType())) {
            return null;
        }
        UUID coordinatorId = catalog.coordinatorPublicByInternal.get(binding.getZigbeeCoordinatorId());
        String ieee = blankToNull(binding.getZigbeeIeeeAddress());
        String property = isSwitchRole(binding.getRole())
                ? defaultCommandProperty(binding.getRole(), binding.getCommandProperty())
                : defaultProperty(binding.getRole(), binding.getZigbeeProperty());
        if (coordinatorId == null || ieee == null || property == null) {
            return null;
        }
        return "zigbee:" + coordinatorId + ":" + ieee.toLowerCase(Locale.ROOT) + ":" + property;
    }

    private void replaceZonePlants(
            AuthenticatedUser user,
            AutomationBoxEntity targetBox,
            AutomationData.SavePlantsRequest request
    ) {
        List<AutomationData.BoxPlantRequest> items = boxPlantRequests(request);
        List<Integer> plantIds = items.stream().map(AutomationData.BoxPlantRequest::plantId).toList();
        if (plantIds.stream().anyMatch(Objects::isNull)) {
            throw new DomainException("bad_request", "Поле plant_id обязательно");
        }
        if (new HashSet<>(plantIds).size() != plantIds.size()) {
            throw new DomainException("bad_request", "Значения plant_ids должны быть уникальными");
        }
        items.forEach(item -> {
            plantFacade.requireOwnedPlantInfo(item.plantId(), user);
            if (item.rateMlPerHour() != null && item.rateMlPerHour() <= 0) {
                throw new DomainException("bad_request", "Поле rate_ml_per_hour должно быть больше нуля");
            }
        });

        Map<Integer, AutomationBoxPlantEntity> previousByPlant = boxPlantRepository
                .findAllByPlantIdIn(plantIds)
                .stream()
                .collect(Collectors.toMap(AutomationBoxPlantEntity::getPlantId, Function.identity()));
        Set<Integer> affectedPumps = new HashSet<>();
        for (AutomationBoxPlantEntity binding : previousByPlant.values()) {
            if (!Objects.equals(binding.getBoxId(), targetBox.getId())) {
                Integer pumpId = automationPumpId(binding.getBoxId());
                if (pumpId != null) {
                    affectedPumps.add(pumpId);
                }
                boxPlantRepository.delete(binding);
            }
        }
        Integer targetPumpId = automationPumpId(targetBox.getId());
        if (targetPumpId != null) {
            affectedPumps.add(targetPumpId);
        }
        boxPlantRepository.deleteAllByBox_Id(targetBox.getId());
        boxPlantRepository.flush();
        LocalDateTime now = nowUtc();
        List<AutomationBoxPlantEntity> rows = items.stream()
                .map(item -> {
                    AutomationBoxPlantEntity previous = previousByPlant.get(item.plantId());
                    Integer rate = item.rateMlPerHour() != null
                            ? item.rateMlPerHour()
                            : previous != null ? previous.getRateMlPerHour() : null;
                    return AutomationBoxPlantEntity.create(targetBox, item.plantId(), rate, now);
                })
                .toList();
        boxPlantRepository.saveAll(rows);
        boxPlantRepository.flush();
        affectedPumps.forEach(this::syncAutomationPump);
    }

    private void movePlantToZone(
            AuthenticatedUser user,
            Integer plantId,
            Integer zoneId,
            boolean validatePlant
    ) {
        if (validatePlant) {
            plantFacade.requireOwnedPlantInfo(plantId, user);
        }
        AutomationBoxPlantEntity previous = boxPlantRepository.findByPlantId(plantId).orElse(null);
        Integer previousPumpId = previous != null ? automationPumpId(previous.getBoxId()) : null;
        Integer rate = previous != null ? previous.getRateMlPerHour() : null;
        if (previous != null) {
            boxPlantRepository.delete(previous);
            boxPlantRepository.flush();
        }
        Integer nextPumpId = null;
        if (zoneId != null) {
            AutomationBoxEntity box = requireOwnedGreenhouse(user, zoneId);
            boxPlantRepository.save(AutomationBoxPlantEntity.create(box, plantId, rate, nowUtc()));
            boxPlantRepository.flush();
            nextPumpId = automationPumpId(box.getId());
        }
        syncAffectedPumps(previousPumpId, nextPumpId);
    }

    private void syncAffectedPumps(Integer... pumpIds) {
        Set<Integer> unique = java.util.Arrays.stream(pumpIds)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        unique.forEach(this::syncAutomationPump);
    }

    private void validatePublicScenarioReadiness(
            AutomationRoomEntity room,
            AutomationBoxEntity box,
            List<AutomationData.ScenarioConfigRequest> scenarios,
            Catalog catalog
    ) {
        Set<String> seen = new HashSet<>();
        for (AutomationData.ScenarioConfigRequest scenario : scenarios) {
            if (scenario == null) {
                throw new DomainException("bad_request", "Список scenarios не должен содержать null");
            }
            String scenarioType = normalizeRequired(scenario.scenarioType(), "scenario_type");
            if (!BOX_SCENARIOS.contains(scenarioType)) {
                throw new DomainException("bad_request", "Тип сценария недоступен для зоны");
            }
            if (!seen.add(scenarioType)) {
                throw new DomainException("bad_request", "Тип сценария должен быть уникальным");
            }
            if (!Boolean.TRUE.equals(scenario.enabled())) {
                continue;
            }
            AutomationData.Readiness readiness = readinessForScenario(
                    AutomationData.SCOPE_BOX,
                    box.getId(),
                    scenarioType,
                    room.getId(),
                    catalog
            );
            if (!readiness.ready()) {
                throw new DomainException("conflict", readiness.reason());
            }
        }
    }

    private void synchronizeFarmClimateScenario(AutomationRoomEntity farm) {
        boolean enabled = boxRepository.findAllByRoom_IdOrderByNameAscIdAsc(farm.getId()).stream()
                .map(box -> configFor(
                        AutomationData.SCOPE_BOX,
                        box.getId(),
                        AutomationData.SCENARIO_BOX_CLIMATE
                ))
                .anyMatch(config -> config != null && config.isEnabled());
        LocalDateTime now = nowUtc();
        AutomationScenarioConfigEntity farmClimate = configFor(
                AutomationData.SCOPE_ROOM,
                farm.getId(),
                AutomationData.SCENARIO_ROOM_CLIMATE
        );
        if (farmClimate == null && !enabled) {
            return;
        }
        if (farmClimate == null) {
            farmClimate = AutomationScenarioConfigEntity.create(
                    AutomationData.SCOPE_ROOM,
                    farm.getId(),
                    AutomationData.SCENARIO_ROOM_CLIMATE,
                    now
            );
            farmClimate.setConfigJson(writeJson(defaultConfig(AutomationData.SCENARIO_ROOM_CLIMATE)));
        }
        farmClimate.setEnabled(enabled);
        farmClimate.setUpdatedAt(now);
        configRepository.save(farmClimate);
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source != null && source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private Catalog buildCatalog() {
        return buildCatalog(null, true);
    }

    private Catalog buildCatalog(AuthenticatedUser user) {
        return buildCatalog(user, user == null || user.isAdmin());
    }

    private Catalog buildOwnedCatalog(AuthenticatedUser user) {
        return buildCatalog(user, false);
    }

    private Catalog buildCatalog(AuthenticatedUser user, boolean allData) {
        List<AutomationData.Plant> plants = allData
                ? plantFacade.listAdminPlants(SYSTEM_ADMIN).stream().map(this::toPlantData).toList()
                : plantFacade.listPlants(user).stream().map(this::toPlantData).toList();
        Map<Integer, AutomationData.Plant> plantsById = plants.stream()
                .collect(Collectors.toMap(AutomationData.Plant::id, Function.identity()));

        List<AutomationData.NativeDevice> nativeDevices = new ArrayList<>();
        Map<Integer, AutomationData.NativeSensor> sensorsById = new HashMap<>();
        Map<Integer, AutomationData.NativePump> pumpsById = new HashMap<>();
        List<DeviceSummary> deviceSummaries = allData
                ? deviceFacade.listAdminDevices()
                : deviceFacade.listMyDevices(user.id());
        for (DeviceSummary summary : deviceSummaries) {
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

        List<ZigbeeOwnedDeviceData> ownedZigbeeDevices = allData
                ? zigbeeFacade.getDevicesForAutomation()
                : zigbeeFacade.getDevicesForUser(user);
        List<AutomationData.ZigbeeDevice> zigbeeDevices = ownedZigbeeDevices.stream()
                .filter(item -> !item.device().coordinator())
                .map(this::toZigbeeDeviceData)
                .toList();
        Map<String, AutomationData.ZigbeeDevice> zigbeeByKey = ownedZigbeeDevices.stream()
                .filter(item -> !item.device().coordinator())
                .filter(item -> item.device().ieeeAddress() != null)
                .collect(Collectors.toMap(
                        item -> zigbeeKey(item.coordinatorId(), item.device().ieeeAddress()),
                        this::toZigbeeDeviceData,
                        (left, right) -> left
                ));
        Map<Integer, UUID> coordinatorPublicByInternal = ownedZigbeeDevices.stream()
                .collect(Collectors.toMap(
                        ZigbeeOwnedDeviceData::coordinatorInternalId,
                        ZigbeeOwnedDeviceData::coordinatorId,
                        (left, right) -> left
                ));
        Map<UUID, Integer> coordinatorInternalByPublic = ownedZigbeeDevices.stream()
                .collect(Collectors.toMap(
                        ZigbeeOwnedDeviceData::coordinatorId,
                        ZigbeeOwnedDeviceData::coordinatorInternalId,
                        (left, right) -> left
                ));
        return new Catalog(
                plants,
                plantsById,
                nativeDevices,
                sensorsById,
                pumpsById,
                zigbeeDevices,
                zigbeeByKey,
                coordinatorPublicByInternal,
                coordinatorInternalByPublic
        );
    }

    private AutomationData.Plant toPlantData(AdminPlantInfo plant) {
        return new AutomationData.Plant(
                plant.id(),
                plant.name(),
                plant.ownerEmail(),
                plant.ownerUsername(),
                plant.ownerId()
        );
    }

    private AutomationData.Plant toPlantData(PlantInfo plant) {
        return new AutomationData.Plant(
                plant.id(),
                plant.name(),
                null,
                null,
                plant.userId()
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

    private AutomationData.ZigbeeDevice toZigbeeDeviceData(ZigbeeOwnedDeviceData ownedDevice) {
        ZigbeeDeviceData device = ownedDevice.device();
        return new AutomationData.ZigbeeDevice(
                ownedDevice.coordinatorId(),
                ownedDevice.coordinatorName(),
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
                throw new DomainException("bad_request", "Эта роль недоступна для помещения");
            }
            return;
        }
        if (AutomationData.SCOPE_BOX.equals(scopeType)) {
            if (List.of(
                    AutomationData.ROLE_AC_SWITCH,
                    AutomationData.ROLE_AIR_TEMPERATURE_SENSOR,
                    AutomationData.ROLE_AIR_HUMIDITY_SENSOR,
                    AutomationData.ROLE_EXHAUST_SWITCH,
                    AutomationData.ROLE_LIGHT_SWITCH,
                    AutomationData.ROLE_LEAK_SENSOR,
                    AutomationData.ROLE_SOIL_MOISTURE_SENSOR,
                    AutomationData.ROLE_WATER_PUMP
            ).contains(role)) {
                return;
            }
        }
        throw new DomainException("bad_request", "Эта роль недоступна для выбранной области");
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
        AutomationData.ZigbeeDevice device = findZigbeeDevice(binding, catalog);
        String property = defaultProperty(AutomationData.ROLE_LEAK_SENSOR, binding.getZigbeeProperty());
        return new PumpSessionData.LeakTarget(
                "automation-resource:" + binding.getId(),
                binding.getId(),
                binding.getSourceType(),
                zigbeeKey(
                        catalog.coordinatorPublicByInternal.get(binding.getZigbeeCoordinatorId()),
                        binding.getZigbeeIeeeAddress()
                ),
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
        AutomationData.ZigbeeDevice device = catalog.zigbeeByKey.get(target.externalId());
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

    private List<AutomationData.ActionLog> overviewActionLogs(
            AuthenticatedUser user,
            List<AutomationRoomEntity> rooms,
            List<AutomationBoxEntity> boxes
    ) {
        if (user.isAdmin()) {
            return actionLogRepository.findTop20ByOrderByCreatedAtDesc().stream()
                    .map(this::toActionLogData)
                    .toList();
        }
        List<AutomationActionLogEntity> logs = new ArrayList<>();
        for (AutomationRoomEntity room : rooms) {
            logs.addAll(actionLogRepository.findTop10ByScopeTypeAndScopeIdOrderByCreatedAtDesc(
                    AutomationData.SCOPE_ROOM,
                    room.getId()
            ));
        }
        for (AutomationBoxEntity box : boxes) {
            logs.addAll(actionLogRepository.findTop10ByScopeTypeAndScopeIdOrderByCreatedAtDesc(
                    AutomationData.SCOPE_BOX,
                    box.getId()
            ));
        }
        return logs.stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .limit(20)
                .map(this::toActionLogData)
                .toList();
    }

    private AutomationRoomEntity requireRoom(AuthenticatedUser user, Integer roomId) {
        requireAuthenticated(user);
        if (user.isAdmin()) {
            return requireRoom(roomId);
        }
        if (roomId == null) {
            throw new DomainException("bad_request", "Поле room_id обязательно");
        }
        return roomRepository.findByIdAndUserId(roomId, user.id())
                .orElseThrow(() -> new DomainException("not_found", "Помещение не найдено"));
    }

    private AutomationBoxEntity requireBox(AuthenticatedUser user, Integer boxId) {
        requireAuthenticated(user);
        if (user.isAdmin()) {
            return requireBox(boxId);
        }
        if (boxId == null) {
            throw new DomainException("bad_request", "Поле box_id обязательно");
        }
        return boxRepository.findByIdAndRoom_UserId(boxId, user.id())
                .orElseThrow(() -> new DomainException("not_found", "Бокс не найден"));
    }

    private void requireAuthenticated(AuthenticatedUser user) {
        if (user == null || user.id() == null) {
            throw new DomainException("unauthorized", "Необходимо войти в аккаунт");
        }
    }

    private AutomationRoomEntity requireRoom(Integer roomId) {
        if (roomId == null) {
            throw new DomainException("bad_request", "Поле room_id обязательно");
        }
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new DomainException("not_found", "Помещение не найдено"));
    }

    private AutomationBoxEntity requireBox(Integer boxId) {
        if (boxId == null) {
            throw new DomainException("bad_request", "Поле box_id обязательно");
        }
        return boxRepository.findById(boxId)
                .orElseThrow(() -> new DomainException("not_found", "Бокс не найден"));
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
                config.put("off_delay_minutes", 5);
                config.put("min_toggle_minutes", 5);
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
                || AutomationData.ROLE_AIR_HUMIDITY_SENSOR.equals(role)
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
        if (AutomationData.ROLE_AIR_HUMIDITY_SENSOR.equals(role)) {
            return "humidity";
        }
        if (AutomationData.ROLE_SOIL_MOISTURE_SENSOR.equals(role)) {
            return "soil_moisture";
        }
        if (AutomationData.ROLE_LEAK_SENSOR.equals(role)) {
            return "water_leak";
        }
        return "state";
    }

    private String expectedNativeSensorType(String role) {
        if (AutomationData.ROLE_AIR_TEMPERATURE_SENSOR.equals(role)) {
            return "AIR_TEMPERATURE";
        }
        if (AutomationData.ROLE_AIR_HUMIDITY_SENSOR.equals(role)) {
            return "AIR_HUMIDITY";
        }
        return "SOIL_MOISTURE";
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
            throw new DomainException("bad_request", "Поле name обязательно");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new DomainException("bad_request", "Поле " + field + " обязательно");
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
            Map<String, AutomationData.ZigbeeDevice> zigbeeByKey,
            Map<Integer, UUID> coordinatorPublicByInternal,
            Map<UUID, Integer> coordinatorInternalByPublic
    ) {
        AutomationData.ResourceCatalog toData() {
            return new AutomationData.ResourceCatalog(plants, nativeDevices, zigbeeDevices);
        }
    }
}
