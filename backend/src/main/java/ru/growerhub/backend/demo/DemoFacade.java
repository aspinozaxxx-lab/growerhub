package ru.growerhub.backend.demo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.auth.AuthFacade;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData;
import ru.growerhub.backend.common.config.DemoSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.demo.contract.DemoData;
import ru.growerhub.backend.demo.engine.DemoTemplate;
import ru.growerhub.backend.demo.jpa.*;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.pump.PumpFacade;
import ru.growerhub.backend.pump.contract.PumpAck;
import ru.growerhub.backend.pump.contract.PumpSessionData;
import ru.growerhub.backend.sensor.contract.SensorStatus;
import ru.growerhub.backend.user.UserFacade;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeSimulationCoordinator;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttMessageType;

@Service
@Transactional
public class DemoFacade {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final DemoSpaceRepository spaces;
    private final DemoDeviceRepository devices;
    private final DemoCapacityRepository capacity;
    private final UserFacade users;
    private final DeviceFacade nativeDevices;
    private final ZigbeeFacade zigbee;
    private final AutomationFacade automation;
    private final PlantFacade plants;
    private final PumpFacade pumps;
    private final AuthFacade auth;
    private final DemoSettings settings;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final DemoTemplate template;

    public DemoFacade(DemoSpaceRepository spaces, DemoDeviceRepository devices, DemoCapacityRepository capacity,
            @Lazy UserFacade users, @Lazy DeviceFacade nativeDevices, @Lazy ZigbeeFacade zigbee,
            @Lazy AutomationFacade automation, @Lazy PlantFacade plants, @Lazy PumpFacade pumps,
            @Lazy AuthFacade auth, DemoSettings settings, ObjectMapper mapper, Clock clock, ResourceLoader resources) {
        this.spaces = spaces; this.devices = devices; this.capacity = capacity; this.users = users;
        this.nativeDevices = nativeDevices; this.zigbee = zigbee; this.automation = automation;
        this.plants = plants; this.pumps = pumps; this.auth = auth; this.settings = settings;
        this.mapper = mapper; this.clock = clock;
        try (var stream = resources.getResource(settings.templatePath()).getInputStream()) {
            this.template = mapper.readValue(stream, DemoTemplate.class);
        } catch (Exception ex) { throw new IllegalStateException("Cannot load demo farm template", ex); }
    }

    public DemoData.Space createGuest(String locale, String timezone, String admissionKey) {
        enabled(); lockCapacity();
        LocalDateTime now = now();
        if (spaces.countByAccountUserIdIsNull() >= settings.maxGuestSpaces()
                || spaces.countByAdmissionKeyAndCreatedAtAfter(admissionKey, now.minusHours(1)) >= settings.maxCreatesPerHour()) {
            throw new DomainException("too_many_requests", "Demo creation limit reached; try later");
        }
        checkActiveCapacity(now);
        return create(null, locale, timezone, admissionKey, now);
    }

    public DemoData.Space openSavedOrCreate(Integer accountId, String locale, String timezone) {
        enabled(); users.lockAccount(accountId);
        DemoSpaceEntity existing = spaces.findByAccountUserId(accountId).orElse(null);
        if (existing != null) return authorize(existing.id, existing.generation, false);
        lockCapacity(); checkActiveCapacity(now());
        return create(accountId, locale, timezone, "account:" + accountId, now());
    }

    private DemoData.Space create(Integer accountId, String locale, String timezone, String admissionKey, LocalDateTime now) {
        String resolvedLocale = "en".equals(locale) ? "en" : "ru";
        String resolvedTimezone;
        try { resolvedTimezone = ZoneId.of(timezone == null ? "UTC" : timezone).getId(); }
        catch (DateTimeException ex) { throw new DomainException("bad_request", "Invalid timezone"); }
        DemoSpaceEntity space = new DemoSpaceEntity();
        space.id = UUID.randomUUID(); space.dataUserId = users.createDemoOwner("Demo " + space.id, resolvedTimezone);
        space.accountUserId = accountId; space.generation = 1; space.templateVersion = template.version();
        space.locale = resolvedLocale; space.admissionKey = admissionKey; space.createdAt = now;
        space.lastActiveAt = now; space.expiresAt = accountId == null ? now.plusHours(settings.guestTtlHours()) : null;
        spaces.saveAndFlush(space);
        seed(space, now);
        return view(space);
    }

    public DemoData.Space resumeGuest(UUID id, int generation) {
        DemoSpaceEntity space = spaces.lockById(id).orElse(null);
        if (space == null || space.accountUserId != null || space.generation != generation
                || (space.expiresAt != null && !space.expiresAt.isAfter(now()))) return null;
        return authorize(id, generation, false);
    }

    public DemoData.Space authorize(UUID id, int generation, boolean mutation) {
        enabled();
        DemoSpaceEntity space = spaces.lockById(id).orElseThrow(() -> new DomainException("unauthorized", "Demo session expired"));
        if (space.generation != generation || (space.expiresAt != null && !space.expiresAt.isAfter(now()))) {
            throw new DomainException("unauthorized", "Demo session expired");
        }
        if (space.lastActiveAt.isBefore(now().minusMinutes(settings.inactiveMinutes()))) {
            lockCapacity(); checkActiveCapacity(now());
        }
        users.requireDemoOwner(space.dataUserId);
        if (mutation) {
            if (space.actionWindowStartedAt == null || !space.actionWindowStartedAt.isAfter(now().minusMinutes(1))) {
                space.actionWindowStartedAt = now(); space.actionsInWindow = 0;
            }
            if (space.actionsInWindow >= settings.maxActionsPerMinute()) {
                throw new DomainException("too_many_requests", "Demo action limit reached; try later");
            }
            space.actionsInWindow++;
        }
        if (space.paused) {
            for (DemoDeviceEntity device : devices.findBySpaceId(space.id)) {
                device.updatedAt = now().minusSeconds(settings.telemetryPeriodSeconds() + 1); devices.save(device);
            }
        }
        space.lastActiveAt = now(); space.paused = false;
        spaces.save(space);
        return view(space);
    }

    public DemoData.Space save(UUID id, int generation, Integer accountId, boolean replace) {
        enabled(); users.lockAccount(accountId);
        DemoSpaceEntity space = spaces.lockById(id).orElseThrow(() -> new DomainException("unauthorized", "Demo session expired"));
        if (space.generation != generation || (space.expiresAt != null && !space.expiresAt.isAfter(now()))
                || (space.accountUserId != null && !space.accountUserId.equals(accountId))) {
            throw new DomainException("unauthorized", "Demo session expired");
        }
        DemoSpaceEntity previous = spaces.findByAccountUserId(accountId).orElse(null);
        if (previous != null && !previous.id.equals(id)) {
            if (!replace) throw new DomainException("conflict", "A saved demo already exists");
            deleteSpace(previous); spaces.flush();
        }
        space.accountUserId = accountId; space.expiresAt = null; space.lastActiveAt = now();
        spaces.save(space);
        return view(space);
    }

    public DemoData.Space reset(UUID id, int generation) {
        enabled();
        DemoSpaceEntity space = spaces.lockById(id).orElseThrow(() -> new DomainException("unauthorized", "Demo session expired"));
        if (space.generation != generation) throw new DomainException("unauthorized", "Demo session expired");
        if (space.resetWindowStartedAt == null || !space.resetWindowStartedAt.isAfter(now().minusHours(1))) {
            space.resetWindowStartedAt = now(); space.resetsInWindow = 0;
        }
        if (space.resetsInWindow >= settings.maxResetsPerHour()) {
            throw new DomainException("too_many_requests", "Demo reset limit reached; try later");
        }
        space.resetsInWindow++;
        clearResources(space);
        space.generation++; space.templateVersion = template.version(); space.lastActiveAt = now(); space.paused = false;
        spaces.saveAndFlush(space); seed(space, now());
        return view(space);
    }

    @Transactional(readOnly = true)
    public DemoData.Status status(AuthenticatedUser user) {
        DemoSpaceEntity space = owned(user);
        return new DemoData.Status(space.id, space.accountUserId != null, space.locale, timezone(space), space.expiresAt,
                devices.findBySpaceId(space.id).stream().map(this::deviceView).toList());
    }

    @Transactional(readOnly = true)
    public List<DemoData.Profile> catalog(AuthenticatedUser user) {
        DemoSpaceEntity space = owned(user);
        return template.profiles().stream().map(profile -> new DemoData.Profile(profile.key(),
                label(profile.name(), space.locale), label(profile.description(), space.locale))).toList();
    }

    public DemoData.Device addDevice(AuthenticatedUser user, DemoData.AddDevice request) {
        DemoSpaceEntity space = owned(user); users.lockDemoOwner(space.dataUserId);
        if (request == null) throw new DomainException("bad_request", "Device profile required");
        DemoTemplate.Profile profile = profile(request.profile());
        String name = request.name() == null || request.name().isBlank() ? label(profile.name(), space.locale) : request.name().strip();
        if (name.length() > 80 || name.contains("/") || name.contains("+") || name.contains("#")) {
            throw new DomainException("bad_request", "Invalid device name");
        }
        DemoDeviceEntity device = add(space, profile, name, now());
        if (device.coordinatorId != null) publishInventory(space, now());
        publish(device, state(device), now());
        return deviceView(device);
    }

    public DemoData.Status environment(AuthenticatedUser user, DemoData.Environment request) {
        DemoSpaceEntity space = owned(user);
        if (request == null || request.deviceId() == null) throw new DomainException("bad_request", "Device required");
        DemoDeviceEntity device = devices.findById(request.deviceId()).filter(item -> item.spaceId.equals(space.id))
                .orElseThrow(() -> new DomainException("not_found", "Demo device not found"));
        device = devices.lockByTargetId(device.targetId).orElseThrow();
        Map<String, Object> state = state(device);
        DemoTemplate.Profile profile = profile(device.profileKey);
        if (request.temperature() != null) {
            if (!Set.of("native", "air").contains(profile.kind())) throw new DomainException("bad_request", "No air sensor");
            state.put("temperature", bounded(request.temperature(), -20, 60));
        }
        if (request.moisture() != null) {
            if (!Set.of("native", "soil").contains(profile.kind())) throw new DomainException("bad_request", "No soil sensor");
            state.put("moisture", bounded(request.moisture(), 0, 100));
        }
        if (request.leak() != null) {
            if (!"leak".equals(profile.kind())) throw new DomainException("bad_request", "No leak sensor");
            state.put("water_leak", request.leak());
        }
        persist(device, state, now()); publish(device, state, now());
        return status(user);
    }

    public PumpAck commandPump(String target, String correlationId, String action, Integer durationS) {
        DemoDeviceEntity device = devices.lockByTargetId(target).orElseThrow(() -> new DomainException("forbidden", "Unknown simulated device"));
        users.requireDemoOwner(required(device.spaceId).dataUserId);
        if (device.nativeDeviceId == null || !nativeDevices.isSimulatedDevice(target)) throw new DomainException("forbidden", "Simulated pump required");
        boolean running = "start".equals(action);
        if (running && (durationS == null || durationS < 1)) throw new DomainException("bad_request", "Duration required");
        if (!Set.of("start", "stop", "reboot").contains(action)) throw new DomainException("bad_request", "Unsupported command");
        Map<String, Object> state = state(device);
        state.put("pump_running", running); state.put("correlation_id", correlationId);
        device.stopAt = running ? now().plusSeconds(durationS) : null;
        persist(device, state, now()); publish(device, state, now());
        return new PumpAck(correlationId, "accepted", null, running ? "running" : "stopped");
    }

    public void commandZigbee(String base, String name, Map<String, Object> payload) {
        DemoDeviceEntity device = devices.lockByTargetId(base + "/" + name)
                .orElseThrow(() -> new DomainException("forbidden", "Unknown simulated device"));
        users.requireDemoOwner(required(device.spaceId).dataUserId);
        if (!zigbee.isSimulatedBaseTopic(base) || !"switch".equals(profile(device.profileKey).kind())) {
            throw new DomainException("forbidden", "Simulated switch required");
        }
        if (payload == null || payload.size() != 1 || !Set.of("ON", "OFF").contains(String.valueOf(payload.get("state")))) {
            throw new DomainException("bad_request", "Unsupported simulated command");
        }
        Map<String, Object> state = state(device);
        integrateEnergy(device, state, now()); state.put("state", payload.get("state"));
        state.put("power", "ON".equals(state.get("state")) ? profile(device.profileKey).powerWatts() : 0.0);
        persist(device, state, now()); publish(device, state, now());
    }

    public void renameZigbee(String base, String from, String to) {
        DemoDeviceEntity device = devices.lockByTargetId(base + "/" + from)
                .orElseThrow(() -> new DomainException("forbidden", "Unknown simulated device"));
        DemoSpaceEntity space = required(device.spaceId); users.requireDemoOwner(space.dataUserId);
        zigbee.renameSimulatedDevice(base, from, to);
        Map<String, Object> state = state(device); state.put("name", to); state.put("friendly_name", to);
        device.targetId = base + "/" + to; persist(device, state, now()); publishInventory(space, now());
    }

    @Transactional(readOnly = true)
    public List<UUID> activeIds() {
        if (!settings.enabled()) return List.of();
        return spaces.findActiveIds(now().minusMinutes(settings.inactiveMinutes()), now(), PageRequest.of(0, settings.workerBatchSize()));
    }

    public DemoData.Tick tick(UUID id) {
        DemoSpaceEntity space = spaces.lockById(id).orElse(null);
        LocalDateTime now = now();
        if (space == null || space.lastActiveAt.isBefore(now.minusMinutes(settings.inactiveMinutes()))
                || (space.expiresAt != null && !space.expiresAt.isAfter(now))) return null;
        List<DemoDeviceEntity> all = devices.findBySpaceId(id);
        boolean telemetry = all.stream().anyMatch(device -> device.updatedAt.isBefore(now.minusSeconds(settings.telemetryPeriodSeconds())));
        Map<UUID, double[]> effects = telemetry ? climateEffects(space, all) : Map.of();
        for (DemoDeviceEntity candidate : all) {
            DemoDeviceEntity device = devices.lockByTargetId(candidate.targetId).orElseThrow();
            Map<String, Object> state = state(device);
            boolean deadline = device.stopAt != null && !device.stopAt.isAfter(now);
            if (!telemetry && !deadline) continue;
            double seconds = Math.max(0, Duration.between(device.updatedAt, now).toMillis() / 1000.0);
            DemoTemplate.Profile profile = profile(device.profileKey);
            if (telemetry && Set.of("native", "air", "soil").contains(profile.kind())) {
                double[] effect = effects.getOrDefault(device.id, new double[2]);
                double target = profile.temperature() + template.physics().dailyTemperatureDelta() * Math.sin(now.getHour() * Math.PI / 12) + effect[0];
                double temperature = number(state, "temperature") + (target - number(state, "temperature"))
                        * Math.min(1, seconds / 60 * template.physics().temperatureResponsePerMinute());
                state.put("temperature", temperature);
                double pumpSeconds = Boolean.TRUE.equals(state.get("pump_running")) ? seconds : effect[1] * seconds;
                if (device.stopAt != null && device.stopAt.isBefore(now)) pumpSeconds = Math.max(0, seconds - Duration.between(device.stopAt, now).toSeconds());
                state.put("moisture", Math.max(0, Math.min(100, number(state, "moisture")
                        + pumpSeconds * template.physics().pumpMoisturePerSecond() - seconds / 3600 * template.physics().dryingPerHour())));
            }
            integrateEnergy(device, state, now);
            if (deadline) { state.put("pump_running", false); device.stopAt = null; }
            persist(device, state, now); publish(device, state, now);
        }
        space.lastTickAt = now; spaces.save(space);
        return new DemoData.Tick(space.dataUserId, telemetry);
    }

    public void cleanup() {
        LocalDateTime now = now();
        for (UUID id : spaces.findIdleIds(now.minusMinutes(settings.inactiveMinutes()), PageRequest.of(0, settings.workerBatchSize()))) {
            DemoSpaceEntity space = spaces.lockById(id).orElse(null);
            if (space == null || space.lastActiveAt.isAfter(now.minusMinutes(settings.inactiveMinutes()))) continue;
            pause(space, now); space.paused = true; spaces.save(space);
        }
        for (UUID id : spaces.findExpiredIds(now, PageRequest.of(0, settings.workerBatchSize()))) {
            DemoSpaceEntity space = spaces.lockById(id).orElse(null);
            if (space != null && space.accountUserId == null && space.expiresAt.isBefore(now)) deleteSpace(space);
        }
        auth.cleanupDemoSessions();
    }

    public void deleteForAccount(Integer accountId) {
        spaces.findByAccountUserId(accountId).ifPresent(this::deleteSpace);
    }

    private void pause(DemoSpaceEntity space, LocalDateTime now) {
        for (DemoDeviceEntity device : devices.findBySpaceId(space.id)) {
            Map<String, Object> state = state(device); integrateEnergy(device, state, now);
            state.put("pump_running", false); state.put("state", "OFF"); state.put("power", 0.0); device.stopAt = null;
            persist(device, state, now); publish(device, state, now);
            if (device.nativeDeviceId != null) pumps.pauseSimulatedDevice(device.targetId, now);
        }
    }

    private void clearResources(DemoSpaceEntity space) {
        users.requireDemoOwner(space.dataUserId); pause(space, now());
        AuthenticatedUser user = demoUser(space);
        for (var device : devices.findBySpaceId(space.id)) {
            if (device.nativeDeviceId != null) pumps.deleteSimulatedHistory(device.nativeDeviceId);
        }
        for (var plant : plants.listPlants(user)) automation.deletePlantWithPlacement(user, plant.id());
        for (var farm : automation.getFarmsOverview(user).farms()) automation.deleteUserFarm(user, farm.id());
        nativeDevices.deleteSimulatedDevices(space.dataUserId);
        zigbee.deleteSimulatedCoordinators(space.dataUserId);
        devices.deleteBySpaceId(space.id); devices.flush();
    }

    private void deleteSpace(DemoSpaceEntity space) {
        auth.revokeDemoSpace(space.id); clearResources(space); spaces.delete(space); spaces.flush(); users.deleteDemoOwner(space.dataUserId);
    }

    private void seed(DemoSpaceEntity space, LocalDateTime now) {
        AuthenticatedUser user = demoUser(space);
        ZigbeeSimulationCoordinator coordinator = zigbee.createSimulatedCoordinator(space.dataUserId,
                "en".equals(space.locale) ? "Demo coordinator" : "Демокоординатор");
        Integer farmId = automation.createUserFarm(user,
                new AutomationData.SaveRoomRequest(label(template.farmName(), space.locale), true)).farms().getFirst().id();
        Map<Integer, DemoDeviceEntity> controllers = new LinkedHashMap<>();
        Map<Integer, List<DemoDeviceEntity>> equipment = new LinkedHashMap<>();
        for (DemoTemplate.Greenhouse item : template.greenhouses()) {
            String name = label(item.name(), space.locale);
            var farm = automation.createGreenhouse(user, farmId, new AutomationData.SaveBoxRequest(name, true)).farms()
                    .stream().filter(value -> value.id().equals(farmId)).findFirst().orElseThrow();
            Integer greenhouseId = farm.greenhouses().stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow().id();
            DemoDeviceEntity controller = add(space, profile("controller"), name + " · Grovika", now);
            controllers.put(greenhouseId, controller);
            List<DemoDeviceEntity> switches = new ArrayList<>();
            for (String key : List.of("light", "fan", "leak")) {
                switches.add(add(space, profile(key), name + " · " + label(profile(key).name(), space.locale), now));
            }
            equipment.put(greenhouseId, switches);
            publish(controller, state(controller), now.minusDays(settings.historyDays()));
            for (Map<String, String> plantName : item.plants()) {
                var plant = automation.createPlantWithPlacement(user, new PlantFacade.PlantCreateCommand(
                        label(plantName, space.locale), now.minusDays(21), "vegetable", null, "vegetative"), greenhouseId);
                automation.updateGreenhousePlantWateringRate(user, greenhouseId, plant.id(),
                        new AutomationData.UpdateWateringRateRequest(template.physics().wateringRateMlPerHour()));
            }
        }
        DemoDeviceEntity ac = add(space, profile("ac"), label(profile("ac").name(), space.locale), now);
        publishInventory(space, now.minusDays(settings.historyDays()));
        for (DemoDeviceEntity device : devices.findBySpaceId(space.id)) {
            if (device.coordinatorId != null) publish(device, state(device), now.minusDays(settings.historyDays()));
        }
        var catalog = automation.getFarmsOverview(user).resourceCatalog();
        for (var entry : controllers.entrySet()) {
            Integer greenhouseId = entry.getKey();
            var controller = catalog.nativeDevices().stream().filter(device -> device.id().equals(entry.getValue().nativeDeviceId)).findFirst().orElseThrow();
            List<AutomationData.ResourceBindingRequest> slots = new ArrayList<>();
            for (var sensor : controller.sensors()) {
                String type = sensor.type().toUpperCase(Locale.ROOT);
                String role = switch (type) {
                    case "AIR_TEMPERATURE" -> AutomationData.ROLE_AIR_TEMPERATURE_SENSOR;
                    case "AIR_HUMIDITY" -> AutomationData.ROLE_AIR_HUMIDITY_SENSOR;
                    case "SOIL_MOISTURE" -> AutomationData.ROLE_SOIL_MOISTURE_SENSOR;
                    default -> null;
                };
                if (role != null && (sensor.channel() == null || sensor.channel() == 0)) {
                    slots.add(new AutomationData.ResourceBindingRequest(role, AutomationData.SOURCE_NATIVE_SENSOR,
                            sensor.id(), null, null, null, null, null, null, null));
                }
            }
            slots.add(new AutomationData.ResourceBindingRequest(AutomationData.ROLE_WATER_PUMP,
                    AutomationData.SOURCE_NATIVE_PUMP, null, controller.pumps().getFirst().id(), null, null, null, null, null, null));
            for (DemoDeviceEntity device : equipment.get(greenhouseId)) {
                String role = switch (device.profileKey) {
                    case "light" -> AutomationData.ROLE_LIGHT_SWITCH;
                    case "fan" -> AutomationData.ROLE_EXHAUST_SWITCH;
                    default -> AutomationData.ROLE_LEAK_SENSOR;
                };
                slots.add(zigbeeSlot(role, device, coordinator));
            }
            automation.replaceGreenhouseSlots(user, greenhouseId, new AutomationData.SaveZoneSlotsRequest(slots, false));
            automation.replaceGreenhouseScenarios(user, greenhouseId, new AutomationData.SaveScenariosRequest(
                    List.of(scenario("BOX_CLIMATE"), scenario("LIGHT_SCHEDULE"), scenario("WATERING"))));
        }
        automation.replaceUserFarmSlots(user, farmId, new AutomationData.SaveZoneSlotsRequest(
                List.of(zigbeeSlot(AutomationData.ROLE_AC_SWITCH, ac, coordinator)), false));
        automation.replaceUserFarmScenarios(user, farmId, new AutomationData.SaveScenariosRequest(List.of(scenario("ROOM_CLIMATE"))));
        seedReadings(space, now);
        var farm = automation.getFarmsOverview(user).farms().stream().filter(value -> value.id().equals(farmId)).findFirst().orElseThrow();
        for (var greenhouse : farm.greenhouses()) {
            var controller = controllers.get(greenhouse.id());
            var pump = catalog.nativeDevices().stream().filter(device -> device.id().equals(controller.nativeDeviceId)).findFirst().orElseThrow().pumps().getFirst();
            var targets = greenhouse.plants().stream().map(plant -> new PumpSessionData.PlantTarget(
                    plant.id(), plant.name(), plant.rateMlPerHour(), space.dataUserId)).toList();
            var box = new PumpSessionData.BoxTarget(greenhouse.id(), greenhouse.name(), farm.id(), farm.name(), targets, List.of());
            var start = new PumpSessionData.Start(pump.id(), PumpSessionData.SOURCE_AUTOMATION, PumpSessionData.MODE_TIMED,
                    template.physics().historyWateringSeconds(), template.physics().historyWateringSeconds(), false, null, null, List.of(box), null, null);
            for (int day = settings.historyDays(); day > 0; day--) pumps.seedSimulatedHistory(start, user, now.minusHours(day * 24L - 6));
        }
        for (DemoDeviceEntity device : devices.findBySpaceId(space.id)) publish(device, state(device), now);
        space.lastTickAt = now; spaces.save(space);
    }

    private AutomationData.ScenarioConfigRequest scenario(String key) {
        return new AutomationData.ScenarioConfigRequest(key, true, template.scenarios().get(key));
    }

    private AutomationData.ResourceBindingRequest zigbeeSlot(String role, DemoDeviceEntity device, ZigbeeSimulationCoordinator coordinator) {
        boolean leak = "leak".equals(device.profileKey);
        return new AutomationData.ResourceBindingRequest(role, AutomationData.SOURCE_ZIGBEE_DEVICE, null, null,
                coordinator.publicId(), String.valueOf(state(device).get("ieee_address")), leak ? "water_leak" : "state",
                leak ? null : "state", leak ? null : "ON", leak ? null : "OFF");
    }

    private DemoDeviceEntity add(DemoSpaceEntity space, DemoTemplate.Profile profile, String name, LocalDateTime now) {
        if (devices.countBySpaceId(space.id) >= settings.maxDevices()) throw new DomainException("conflict", "Demo device limit reached");
        DemoDeviceEntity device = new DemoDeviceEntity(); device.id = UUID.randomUUID(); device.spaceId = space.id;
        device.profileKey = profile.key(); device.updatedAt = now;
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", name); state.put("temperature", profile.temperature()); state.put("humidity", profile.humidity());
        state.put("moisture", profile.moisture()); state.put("pump_running", false); state.put("water_leak", false);
        state.put("state", "OFF"); state.put("power", 0.0); state.put("energy", 0.0);
        if ("native".equals(profile.kind())) {
            var nativeDevice = nativeDevices.createSimulatedDevice(space.dataUserId, name);
            device.nativeDeviceId = nativeDevice.id(); device.targetId = nativeDevice.deviceId();
        } else {
            var coordinator = zigbee.getSimulationCoordinator(space.dataUserId);
            String friendly = name;
            if (devices.findByTargetId(coordinator.baseTopic() + "/" + friendly).isPresent()) friendly += " " + device.id.toString().substring(0, 6);
            device.coordinatorId = coordinator.id(); device.targetId = coordinator.baseTopic() + "/" + friendly;
            state.put("friendly_name", friendly); state.put("ieee_address", "0x" + device.id.toString().replace("-", "").substring(0, 16));
        }
        device.stateJson = json(state); return devices.saveAndFlush(device);
    }

    private void publishInventory(DemoSpaceEntity space, LocalDateTime now) {
        var coordinator = zigbee.getSimulationCoordinator(space.dataUserId);
        List<Map<String, Object>> inventory = new ArrayList<>();
        for (DemoDeviceEntity device : devices.findBySpaceId(space.id)) {
            if (device.coordinatorId == null) continue;
            Map<String, Object> state = state(device);
            inventory.add(Map.of("friendly_name", state.get("friendly_name"), "ieee_address", state.get("ieee_address"),
                    "type", "Router", "supported", true, "interview_completed", true,
                    "definition", Map.of("model", "Demo " + device.profileKey, "vendor", "GrowerHub",
                            "description", label(profile(device.profileKey).description(), space.locale), "exposes", exposes(profile(device.profileKey)))));
        }
        zigbee.recordSimulatedSnapshot(coordinator.id(), ZigbeeMqttMessageType.BRIDGE_DEVICES, null, inventory, now);
        zigbee.recordSimulatedSnapshot(coordinator.id(), ZigbeeMqttMessageType.BRIDGE_STATE, null, Map.of("state", "online"), now);
    }

    private List<Map<String, Object>> exposes(DemoTemplate.Profile profile) {
        List<Map<String, Object>> result = new ArrayList<>();
        if ("switch".equals(profile.kind())) {
            result.add(Map.of("type", "switch", "features", List.of(Map.of("type", "binary", "name", "state", "property", "state",
                    "access", 7, "value_on", "ON", "value_off", "OFF"))));
            result.add(metric("power", "W")); result.add(metric("energy", "kWh"));
        }
        if ("leak".equals(profile.kind())) result.add(Map.of("type", "binary", "name", "water_leak", "property", "water_leak", "access", 1, "value_on", true, "value_off", false));
        if ("air".equals(profile.kind())) { result.add(metric("temperature", "°C")); result.add(metric("humidity", "%")); }
        if ("soil".equals(profile.kind())) result.add(metric("soil_moisture", "%"));
        return result;
    }

    private Map<String, Object> metric(String property, String unit) {
        return Map.of("type", "numeric", "name", property, "property", property, "access", 1, "unit", unit);
    }

    private void publish(DemoDeviceEntity device, Map<String, Object> state, LocalDateTime now) {
        if (device.nativeDeviceId != null) {
            DeviceShadowState previous = nativeDevices.getShadowState(device.targetId);
            var manual = previous != null ? previous.manualWatering() : null;
            boolean running = Boolean.TRUE.equals(state.get("pump_running"));
            if (manual != null) manual = new DeviceShadowState.ManualWateringState(running ? "running" : "stopped",
                    manual.durationS(), manual.startedAt(), device.stopAt == null ? 0 : (int) Math.max(0, Duration.between(now, device.stopAt).toSeconds()),
                    manual.correlationId(), manual.pumpId(), manual.waterVolumeL(), manual.ph(), manual.fertilizersPerLiter(), manual.journalWrittenForCorrelationId());
            double temperature = rounded(number(state, "temperature")); double humidity = rounded(number(state, "humidity")); double moisture = number(state, "moisture");
            nativeDevices.handleSimulatedState(device.targetId, new DeviceShadowState(manual, "demo", "GROVIKA_V1",
                    null, null, null, new DeviceShadowState.AirState(true, temperature, humidity, SensorStatus.OK),
                    new DeviceShadowState.SoilState(List.of(new DeviceShadowState.SoilPort(0, true, (int) Math.round(moisture), SensorStatus.OK))),
                    new DeviceShadowState.RelayState("off"), new DeviceShadowState.RelayState(running ? "on" : "off"), null), now);
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            switch (profile(device.profileKey).kind()) {
                case "switch" -> { payload.put("state", state.get("state")); payload.put("power", state.get("power")); payload.put("energy", state.get("energy")); }
                case "leak" -> payload.put("water_leak", state.get("water_leak"));
                case "air" -> { payload.put("temperature", state.get("temperature")); payload.put("humidity", state.get("humidity")); }
                case "soil" -> payload.put("soil_moisture", state.get("moisture"));
                default -> throw new IllegalStateException("Unsupported demo profile");
            }
            zigbee.recordSimulatedSnapshot(device.coordinatorId, ZigbeeMqttMessageType.DEVICE_STATE, String.valueOf(state.get("friendly_name")), payload, now);
            zigbee.recordSimulatedSnapshot(device.coordinatorId, ZigbeeMqttMessageType.DEVICE_AVAILABILITY, String.valueOf(state.get("friendly_name")), Map.of("state", "online"), now);
        }
    }

    private void seedReadings(DemoSpaceEntity space, LocalDateTime now) {
        List<DemoDeviceEntity> all = devices.findBySpaceId(space.id);
        LocalDateTime start = now.minusDays(settings.historyDays());
        ZoneId zone = ZoneId.of(users.getTimezone(space.dataUserId));
        for (LocalDateTime at = start; !at.isAfter(now); at = at.plusMinutes(settings.historyStepMinutes())) {
            double hours = Duration.between(start, at).toMinutes() / 60.0;
            for (DemoDeviceEntity device : all) {
                Map<String, Object> state = state(device); DemoTemplate.Profile profile = profile(device.profileKey);
                state.put("temperature", rounded(profile.temperature() + template.physics().dailyTemperatureDelta() * Math.sin(at.getHour() * Math.PI / 12)));
                state.put("humidity", rounded(profile.humidity() - template.physics().dailyTemperatureDelta() * Math.sin(at.getHour() * Math.PI / 12)));
                state.put("moisture", rounded(profile.moisture() + Math.cos(hours * Math.PI / 12) * template.physics().dryingPerHour() * 12));
                if ("switch".equals(profile.kind())) {
                    int localHour = at.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).getHour();
                    boolean on = "light".equals(profile.key()) && localHour >= 6 && localHour < 22;
                    state.put("state", on ? "ON" : "OFF"); state.put("power", on ? profile.powerWatts() : 0.0);
                    state.put("energy", number(state, "energy") + (on ? profile.powerWatts() * settings.historyStepMinutes() / 60 / 1000 : 0));
                }
                persist(device, state, at); publish(device, state, at);
            }
            entityManager.flush();
            entityManager.clear();
        }
    }

    private Map<UUID, double[]> climateEffects(DemoSpaceEntity space, List<DemoDeviceEntity> all) {
        var overview = automation.getFarmsOverview(demoUser(space));
        Map<Integer, DemoDeviceEntity> byNative = new HashMap<>();
        Map<String, DemoDeviceEntity> byIeee = new HashMap<>();
        for (DemoDeviceEntity device : all) {
            if (device.nativeDeviceId != null) byNative.put(device.nativeDeviceId, device);
            else byIeee.put(String.valueOf(state(device).get("ieee_address")), device);
        }
        Map<Integer, Integer> sensorDevices = new HashMap<>();
        for (var device : overview.resourceCatalog().nativeDevices()) for (var sensor : device.sensors()) sensorDevices.put(sensor.id(), device.id());
        Map<UUID, double[]> result = new HashMap<>();
        for (var farm : overview.farms()) {
            boolean cooling = farm.slots().stream().anyMatch(slot -> AutomationData.ROLE_AC_SWITCH.equals(slot.role()) && on(slot.currentValue()));
            for (var greenhouse : farm.greenhouses()) {
                double heat = cooling ? -template.physics().acCooling() : 0;
                double water = 0;
                for (var slot : greenhouse.slots()) {
                    if (!on(slot.currentValue())) continue;
                    if (AutomationData.ROLE_LIGHT_SWITCH.equals(slot.role())) heat += template.physics().lightHeat();
                    if (AutomationData.ROLE_EXHAUST_SWITCH.equals(slot.role())) heat -= template.physics().fanCooling();
                    if (AutomationData.ROLE_WATER_PUMP.equals(slot.role())) water = 1;
                }
                for (var slot : greenhouse.slots()) {
                    DemoDeviceEntity device = slot.nativeSensorId() != null ? byNative.get(sensorDevices.get(slot.nativeSensorId())) : byIeee.get(slot.zigbeeIeeeAddress());
                    if (device != null) result.put(device.id, new double[]{heat, water});
                }
            }
        }
        return result;
    }

    private boolean on(Object value) {
        return value != null && Set.of("on", "true", "running", "1").contains(String.valueOf(value).toLowerCase(Locale.ROOT));
    }

    private void integrateEnergy(DemoDeviceEntity device, Map<String, Object> state, LocalDateTime now) {
        if (!"switch".equals(profile(device.profileKey).kind())) return;
        double hours = Math.max(0, Duration.between(device.updatedAt, now).toMillis()) / 3_600_000.0;
        state.put("energy", number(state, "energy") + number(state, "power") * hours / 1000);
    }

    private void persist(DemoDeviceEntity device, Map<String, Object> state, LocalDateTime now) {
        device.stateJson = json(state); device.updatedAt = now; devices.save(device);
    }

    private Map<String, Object> state(DemoDeviceEntity device) {
        try { return mapper.readValue(device.stateJson, new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception ex) { throw new IllegalStateException("Invalid simulator state", ex); }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Invalid simulator value", ex); }
    }

    private double number(Map<String, Object> state, String key) {
        Object value = state.get(key); return value instanceof Number number ? number.doubleValue() : 0;
    }

    private double rounded(double value) { return Math.round(value * 100.0) / 100.0; }
    private double bounded(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) throw new DomainException("bad_request", "Value outside sensor range");
        return value;
    }
    private String label(Map<String, String> labels, String locale) { return labels.getOrDefault(locale, labels.get("en")); }
    private DemoTemplate.Profile profile(String key) {
        return template.profiles().stream().filter(profile -> profile.key().equals(key)).findFirst()
                .orElseThrow(() -> new DomainException("bad_request", "Unknown demo profile"));
    }
    private DemoData.Device deviceView(DemoDeviceEntity device) {
        Map<String, Object> state = state(device);
        return new DemoData.Device(device.id, device.profileKey, String.valueOf(state.get("name")), state);
    }
    private DemoSpaceEntity owned(AuthenticatedUser user) {
        enabled();
        if (user == null || !user.isDemo()) throw new DomainException("forbidden", "Demo session required");
        users.requireDemoOwner(user.id());
        return spaces.findByDataUserId(user.id()).orElseThrow(() -> new DomainException("unauthorized", "Demo session expired"));
    }
    private DemoSpaceEntity required(UUID id) {
        return spaces.findById(id).orElseThrow(() -> new DomainException("unauthorized", "Demo session expired"));
    }
    private DemoData.Space view(DemoSpaceEntity space) {
        return new DemoData.Space(space.id, space.dataUserId, space.generation, space.accountUserId != null, space.locale, timezone(space), space.expiresAt);
    }
    private String timezone(DemoSpaceEntity space) { return users.getTimezones(Set.of(space.dataUserId)).get(space.dataUserId); }
    private AuthenticatedUser demoUser(DemoSpaceEntity space) { return new AuthenticatedUser(space.dataUserId, "demo"); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private void enabled() { if (!settings.enabled()) throw new DomainException("not_found", "Demo is unavailable"); }
    private void lockCapacity() {
        if (capacity.lockCapacity() == null) throw new DomainException("unavailable", "Demo capacity is not initialized");
    }
    private void checkActiveCapacity(LocalDateTime now) {
        if (spaces.countByLastActiveAtAfter(now.minusMinutes(settings.inactiveMinutes())) >= settings.maxActiveSpaces()) {
            throw new DomainException("too_many_requests", "Demo is busy; try again later");
        }
    }
}
