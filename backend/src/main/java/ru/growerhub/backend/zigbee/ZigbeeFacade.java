package ru.growerhub.backend.zigbee;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.config.ZigbeeSettings;
import ru.growerhub.backend.common.config.mqtt.MqttTopicSettings;
import ru.growerhub.backend.common.config.zigbee.ZigbeeHistorySettings;
import ru.growerhub.backend.common.config.zigbee.ZigbeeSelfServiceSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.zigbee.contract.ZigbeeBridgeData;
import ru.growerhub.backend.zigbee.contract.ZigbeeBrokerCredentialGateway;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandGateway;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandPublishResult;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandResponseData;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorData;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorCreated;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorSetup;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorStatus;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorSummary;
import ru.growerhub.backend.zigbee.contract.ZigbeeDeviceData;
import ru.growerhub.backend.zigbee.contract.ZigbeeFeatureData;
import ru.growerhub.backend.zigbee.contract.ZigbeeHistoryPoint;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttSnapshotMessage;
import ru.growerhub.backend.zigbee.contract.ZigbeeOverviewData;
import ru.growerhub.backend.zigbee.contract.ZigbeeOwnedDeviceData;
import ru.growerhub.backend.zigbee.contract.ZigbeeProductAnalytics;
import ru.growerhub.backend.zigbee.jpa.ZigbeeBridgeSnapshotEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeBridgeSnapshotRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCommandResponseSnapshotEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCommandResponseSnapshotRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCoordinatorEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCoordinatorRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDevicePropertyReadingEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDevicePropertyReadingRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceStateEventEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceStateEventRepository;

@Service
public class ZigbeeFacade {
    private static final int ACCESS_STATE = 0b001;
    private static final int ACCESS_SET = 0b010;
    private static final String DEVICE_IMAGE_BASE_URL = "https://www.zigbee2mqtt.io/images/devices/";
    private static final int LEGACY_COORDINATOR_ID = 1;
    private static final UUID LEGACY_COORDINATOR_PUBLIC_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );

    private final ZigbeeBridgeSnapshotRepository bridgeRepository;
    private final ZigbeeDeviceSnapshotRepository deviceRepository;
    private final ZigbeeCommandResponseSnapshotRepository commandResponseRepository;
    private final ZigbeeDeviceStateEventRepository stateEventRepository;
    private final ZigbeeDevicePropertyReadingRepository propertyReadingRepository;
    private final ZigbeeCoordinatorRepository coordinatorRepository;
    private final ZigbeeCommandGateway commandGateway;
    private final ZigbeeBrokerCredentialGateway brokerCredentialGateway;
    private final ZigbeeSettings zigbeeSettings;
    private final ZigbeeHistorySettings historySettings;
    private final ZigbeeSelfServiceSettings selfServiceSettings;
    private final MqttTopicSettings topicSettings;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public ZigbeeFacade(
            ZigbeeBridgeSnapshotRepository bridgeRepository,
            ZigbeeDeviceSnapshotRepository deviceRepository,
            ZigbeeCommandResponseSnapshotRepository commandResponseRepository,
            ZigbeeDeviceStateEventRepository stateEventRepository,
            ZigbeeDevicePropertyReadingRepository propertyReadingRepository,
            ZigbeeCoordinatorRepository coordinatorRepository,
            ZigbeeCommandGateway commandGateway,
            ZigbeeBrokerCredentialGateway brokerCredentialGateway,
            ZigbeeSettings zigbeeSettings,
            ZigbeeHistorySettings historySettings,
            ZigbeeSelfServiceSettings selfServiceSettings,
            MqttTopicSettings topicSettings,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.bridgeRepository = bridgeRepository;
        this.deviceRepository = deviceRepository;
        this.commandResponseRepository = commandResponseRepository;
        this.stateEventRepository = stateEventRepository;
        this.propertyReadingRepository = propertyReadingRepository;
        this.coordinatorRepository = coordinatorRepository;
        this.commandGateway = commandGateway;
        this.brokerCredentialGateway = brokerCredentialGateway;
        this.zigbeeSettings = zigbeeSettings;
        this.historySettings = historySettings;
        this.selfServiceSettings = selfServiceSettings;
        this.topicSettings = topicSettings;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ZigbeeCoordinatorCreated createCoordinator(AuthenticatedUser user, String name) {
        requireSelfService(user);
        LocalDateTime now = LocalDateTime.now(clock);
        enforceCredentialCooldown(user.id(), now);
        String normalizedName = normalizeCoordinatorName(name);
        String mqttUsername = generateMqttUsername();
        String password = generatePassword();
        String clientId = mqttUsername;
        String baseTopic = topicSettings.getZigbeeUserPrefix() + "/" + mqttUsername;

        brokerCredentialGateway.provision(
                mqttUsername,
                password,
                clientId,
                selfServiceSettings.getBrokerRole()
        );
        try {
            ZigbeeCoordinatorEntity coordinator = ZigbeeCoordinatorEntity.create(
                    UUID.randomUUID(),
                    user.id(),
                    normalizedName,
                    mqttUsername,
                    baseTopic,
                    now
            );
            coordinatorRepository.saveAndFlush(coordinator);
            return new ZigbeeCoordinatorCreated(
                    toCoordinatorSummary(coordinator),
                    buildSetup(coordinator, password)
            );
        } catch (RuntimeException ex) {
            try {
                brokerCredentialGateway.revoke(mqttUsername);
            } catch (RuntimeException ignored) {
                // Otkat broker client vypolnjaetsja po vozmozhnosti bez raskrytija credentials.
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<ZigbeeCoordinatorSummary> listCoordinators(AuthenticatedUser user) {
        requireSelfService(user);
        return coordinatorRepository.findAllByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(user.id()).stream()
                .map(this::toCoordinatorSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ZigbeeProductAnalytics getProductAnalytics() {
        List<ZigbeeCoordinatorEntity> all = coordinatorRepository.findAll();
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Integer> usersWithCoordinator = new HashSet<>();
        Set<Integer> usersWithConnectedCoordinator = new HashSet<>();
        Set<Integer> usersWithFirstDevice = new HashSet<>();
        long connected = 0;
        long active1d = 0;
        long active7d = 0;
        long active28d = 0;

        for (ZigbeeCoordinatorEntity coordinator : all) {
            usersWithCoordinator.add(coordinator.getUserId());
            if (coordinator.getConnectedAt() != null) {
                connected++;
                usersWithConnectedCoordinator.add(coordinator.getUserId());
            }
            if (coordinator.getFirstDeviceSeenAt() != null) {
                usersWithFirstDevice.add(coordinator.getUserId());
            }
            if (coordinator.getArchivedAt() != null || coordinator.getLastSeenAt() == null) {
                continue;
            }
            if (!coordinator.getLastSeenAt().isBefore(now.minusDays(1))) {
                active1d++;
            }
            if (!coordinator.getLastSeenAt().isBefore(now.minusDays(7))) {
                active7d++;
            }
            if (!coordinator.getLastSeenAt().isBefore(now.minusDays(28))) {
                active28d++;
            }
        }

        return new ZigbeeProductAnalytics(
                usersWithCoordinator,
                usersWithConnectedCoordinator,
                usersWithFirstDevice,
                all.size(),
                connected,
                active1d,
                active7d,
                active28d
        );
    }

    @Transactional(readOnly = true)
    public ZigbeeCoordinatorSummary getCoordinator(AuthenticatedUser user, UUID coordinatorPublicId) {
        requireSelfService(user);
        return toCoordinatorSummary(findOwnedCoordinator(user, coordinatorPublicId));
    }

    @Transactional(readOnly = true)
    public List<ZigbeeOwnedDeviceData> getDevicesForUser(AuthenticatedUser user) {
        requireSelfService(user);
        List<ZigbeeCoordinatorEntity> coordinators = user.isAdmin()
                ? coordinatorRepository.findAllByArchivedAtIsNullOrderByCreatedAtAsc()
                : coordinatorRepository.findAllByUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(user.id());
        return collectCoordinatorDevices(coordinators);
    }

    @Transactional(readOnly = true)
    public List<ZigbeeOwnedDeviceData> getDevicesForAutomation() {
        List<ZigbeeCoordinatorEntity> coordinators = coordinatorRepository
                .findAllByArchivedAtIsNullOrderByCreatedAtAsc();
        List<ZigbeeOwnedDeviceData> result = new ArrayList<>(collectCoordinatorDevices(coordinators));
        boolean hasLegacy = coordinators.stream().anyMatch(item -> item.getId().equals(LEGACY_COORDINATOR_ID));
        if (!hasLegacy) {
            for (ZigbeeDeviceSnapshotEntity device : deviceRepository
                    .findAllByCoordinatorIdOrderByCoordinatorDescFriendlyNameAsc(LEGACY_COORDINATOR_ID)) {
                result.add(new ZigbeeOwnedDeviceData(
                        LEGACY_COORDINATOR_ID,
                        LEGACY_COORDINATOR_PUBLIC_ID,
                        "Legacy coordinator",
                        toDeviceData(device)
                ));
            }
        }
        return List.copyOf(result);
    }

    @Transactional
    public ZigbeeCoordinatorSetup rotateCoordinatorCredentials(
            AuthenticatedUser user,
            UUID coordinatorPublicId
    ) {
        requireSelfService(user);
        ZigbeeCoordinatorEntity coordinator = findOwnedCoordinator(user, coordinatorPublicId);
        LocalDateTime now = LocalDateTime.now(clock);
        enforceCoordinatorCredentialCooldown(coordinator, now);
        String password = generatePassword();
        brokerCredentialGateway.rotate(coordinator.getMqttUsername(), password);
        coordinator.setCredentialIssuedAt(now);
        coordinator.setStatus(ZigbeeCoordinatorStatus.OFFLINE);
        coordinator.setUpdatedAt(now);
        coordinatorRepository.save(coordinator);
        return buildSetup(coordinator, password);
    }

    @Transactional
    public void archiveCoordinator(AuthenticatedUser user, UUID coordinatorPublicId) {
        requireSelfService(user);
        ZigbeeCoordinatorEntity coordinator = findOwnedCoordinator(user, coordinatorPublicId);
        brokerCredentialGateway.revoke(coordinator.getMqttUsername());
        LocalDateTime now = LocalDateTime.now(clock);
        coordinator.setArchivedAt(now);
        coordinator.setStatus(ZigbeeCoordinatorStatus.ARCHIVED);
        coordinator.setUpdatedAt(now);
        coordinatorRepository.save(coordinator);
    }

    @Transactional(readOnly = true)
    public ZigbeeOverviewData getOverview(AuthenticatedUser user, UUID coordinatorPublicId) {
        requireSelfService(user);
        ZigbeeCoordinatorEntity coordinator = findOwnedCoordinator(user, coordinatorPublicId);
        return getOverview(coordinator.getId(), coordinator.getBaseTopic());
    }

    @Transactional(readOnly = true)
    public List<ZigbeeHistoryPoint> getHistory(
            AuthenticatedUser user,
            UUID coordinatorPublicId,
            String ieeeAddress,
            String property,
            Integer hours
    ) {
        requireSelfService(user);
        ZigbeeCoordinatorEntity coordinator = findOwnedCoordinator(user, coordinatorPublicId);
        return getHistory(coordinator.getId(), ieeeAddress, property, hours);
    }

    @Transactional
    public ZigbeeCommandPublishResult permitJoin(
            AuthenticatedUser user,
            UUID coordinatorPublicId,
            Integer seconds
    ) {
        requireSelfService(user);
        ZigbeeCoordinatorEntity coordinator = findOwnedCoordinator(user, coordinatorPublicId);
        return permitJoin(coordinator.getBaseTopic(), seconds);
    }

    @Transactional
    public ZigbeeCommandPublishResult setDeviceProperty(
            AuthenticatedUser user,
            UUID coordinatorPublicId,
            String ieeeAddress,
            String property,
            Object value
    ) {
        requireSelfService(user);
        ZigbeeCoordinatorEntity coordinator = findOwnedCoordinator(user, coordinatorPublicId);
        String normalizedProperty = normalizeProperty(property);
        if (!selfServiceSettings.getWritableProperties().contains(normalizedProperty)) {
            throw new DomainException("bad_request", "Zigbee property nedostupen dlya bezopasnogo upravlenija");
        }
        return setDeviceProperty(
                coordinator.getId(),
                coordinator.getBaseTopic(),
                ieeeAddress,
                normalizedProperty,
                value
        );
    }

    @Transactional
    public ZigbeeCommandPublishResult setDeviceState(
            AuthenticatedUser user,
            UUID coordinatorPublicId,
            String ieeeAddress,
            String state
    ) {
        return setDeviceProperty(user, coordinatorPublicId, ieeeAddress, "state", normalizeState(state));
    }

    @Transactional
    public ZigbeeCommandPublishResult renameDevice(
            AuthenticatedUser user,
            UUID coordinatorPublicId,
            String ieeeAddress,
            String friendlyName
    ) {
        requireSelfService(user);
        ZigbeeCoordinatorEntity coordinator = findOwnedCoordinator(user, coordinatorPublicId);
        return renameDevice(coordinator.getId(), coordinator.getBaseTopic(), ieeeAddress, friendlyName);
    }

    @Transactional
    public ZigbeeCommandPublishResult setAutomationDeviceProperty(
            Integer coordinatorId,
            String ieeeAddress,
            String property,
            Object value
    ) {
        ZigbeeCoordinatorEntity coordinator = coordinatorRepository
                .findByIdAndArchivedAtIsNull(coordinatorId)
                .orElseThrow(() -> new DomainException("not_found", "Koordinator ne naiden"));
        String normalizedProperty = normalizeProperty(property);
        if (!selfServiceSettings.getWritableProperties().contains(normalizedProperty)) {
            throw new DomainException("bad_request", "Zigbee property nedostupen dlya bezopasnogo upravlenija");
        }
        return setDeviceProperty(
                coordinator.getId(),
                coordinator.getBaseTopic(),
                ieeeAddress,
                normalizedProperty,
                value
        );
    }

    @Transactional(readOnly = true)
    public ZigbeeOverviewData getOverview() {
        return getOverview(LEGACY_COORDINATOR_ID, topicSettings.getZigbeeBase());
    }

    private ZigbeeOverviewData getOverview(Integer coordinatorId, String baseTopic) {
        ZigbeeBridgeSnapshotEntity bridge = bridgeRepository.findById(coordinatorId).orElse(null);
        ZigbeeBridgeData bridgeData = bridge != null
                ? new ZigbeeBridgeData(
                        bridge.getBaseTopic(),
                        bridge.getState(),
                        readJson(bridge.getInfoJson()),
                        bridge.getPermitJoin(),
                        bridge.getPermitJoinEnd(),
                        bridge.getVersion(),
                        bridge.getUpdatedAt())
                : new ZigbeeBridgeData(baseTopic, null, null, null, null, null, null);

        ZigbeeCoordinatorData coordinator = buildCoordinator(coordinatorId, bridge);
        List<ZigbeeDeviceData> devices = deviceRepository
                .findAllByCoordinatorIdOrderByCoordinatorDescFriendlyNameAsc(coordinatorId).stream()
                .map(this::toDeviceData)
                .toList();
        ZigbeeCommandResponseData response = commandResponseRepository
                .findById(coordinatorId)
                .map(this::toCommandResponseData)
                .orElse(null);
        return new ZigbeeOverviewData(bridgeData, coordinator, devices, response);
    }

    @Transactional(readOnly = true)
    public List<ZigbeeHistoryPoint> getHistory(String ieeeAddress, String property, Integer hours) {
        return getHistory(LEGACY_COORDINATOR_ID, ieeeAddress, property, hours);
    }

    private List<ZigbeeHistoryPoint> getHistory(
            Integer coordinatorId,
            String ieeeAddress,
            String property,
            Integer hours
    ) {
        if (ieeeAddress == null || ieeeAddress.isBlank()) {
            throw new DomainException("bad_request", "ieee_address obyazatelen");
        }
        String normalizedProperty = normalizeProperty(property);
        ZigbeeDeviceSnapshotEntity device = deviceRepository
                .findByCoordinatorIdAndIeeeAddress(coordinatorId, ieeeAddress)
                .orElse(null);
        if (device == null) {
            throw new DomainException("not_found", "Zigbee ustrojstvo ne naideno");
        }
        int resolvedHours = hours != null ? hours : historySettings.getDefaultHours();
        LocalDateTime since = LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(resolvedHours);
        List<ZigbeeDevicePropertyReadingEntity> rows = propertyReadingRepository
                .findAllByCoordinatorIdAndIeeeAddressAndPropertyAndTsGreaterThanEqualOrderByTs(
                        coordinatorId,
                        device.getIeeeAddress(),
                        normalizedProperty,
                        since
                );
        if (rows.isEmpty()) {
            rows = propertyReadingRepository
                    .findAllByCoordinatorIdAndFriendlyNameAndPropertyAndTsGreaterThanEqualOrderByTs(
                            coordinatorId,
                            device.getFriendlyName(),
                            normalizedProperty,
                            since
                    );
        }
        List<ZigbeeDevicePropertyReadingEntity> sampled = downsample(rows, historySettings.getMaxPoints());
        List<ZigbeeHistoryPoint> payload = new ArrayList<>();
        for (ZigbeeDevicePropertyReadingEntity row : sampled) {
            payload.add(new ZigbeeHistoryPoint(
                    row.getTs(),
                    row.getProperty(),
                    normalizedHistoryValue(row),
                    rawHistoryValue(row),
                    row.getValueText(),
                    row.getValueBoolean()
            ));
        }
        return payload;
    }

    @Transactional
    public void handleMqttSnapshot(ZigbeeMqttSnapshotMessage message) {
        if (message == null || message.type() == null) {
            return;
        }
        CoordinatorContext context = resolveInboundCoordinator(message);
        if (context == null) {
            return;
        }
        touchCoordinator(context, message);
        switch (message.type()) {
            case BRIDGE_STATE -> handleBridgeState(context, message);
            case BRIDGE_INFO -> handleBridgeInfo(context, message);
            case BRIDGE_DEVICES -> handleBridgeDevices(context, message);
            case DEVICE_STATE -> handleDeviceState(context, message);
            case DEVICE_AVAILABILITY -> handleDeviceAvailability(context, message);
            case COMMAND_RESPONSE -> handleCommandResponse(context, message);
            default -> {
            }
        }
    }

    @Transactional
    public ZigbeeCommandPublishResult permitJoin(Integer seconds) {
        return permitJoin(topicSettings.getZigbeeBase(), seconds);
    }

    private ZigbeeCommandPublishResult permitJoin(String baseTopic, Integer seconds) {
        int resolvedSeconds = seconds != null ? seconds : zigbeeSettings.getPermitJoinDefaultSeconds();
        if (resolvedSeconds < 0 || resolvedSeconds > 254) {
            throw new DomainException("bad_request", "seconds dolzhen byt' v diapazone 0..254");
        }
        commandGateway.publishPermitJoin(baseTopic, resolvedSeconds);
        return new ZigbeeCommandPublishResult(
                "permit join command published",
                bridgeTopic(baseTopic, "request/permit_join")
        );
    }

    @Transactional
    public ZigbeeCommandPublishResult setDeviceState(String ieeeAddress, String state) {
        String normalizedState = normalizeState(state);
        return setDeviceProperty(ieeeAddress, "state", normalizedState);
    }

    @Transactional
    public ZigbeeCommandPublishResult setDeviceProperty(String ieeeAddress, String property, Object value) {
        return setDeviceProperty(
                LEGACY_COORDINATOR_ID,
                topicSettings.getZigbeeBase(),
                ieeeAddress,
                property,
                value
        );
    }

    private ZigbeeCommandPublishResult setDeviceProperty(
            Integer coordinatorId,
            String baseTopic,
            String ieeeAddress,
            String property,
            Object value
    ) {
        ZigbeeDeviceSnapshotEntity device = findControllableDevice(coordinatorId, ieeeAddress);
        String normalizedProperty = normalizeProperty(property);
        if (value == null) {
            throw new DomainException("bad_request", "value obyazatelen");
        }
        ZigbeeFeatureData feature = findWritableFeature(device, normalizedProperty);
        if (feature == null) {
            throw new DomainException("bad_request", "Zigbee property nedostupen dlya zapisi");
        }
        commandGateway.publishSet(baseTopic, device.getFriendlyName(), Map.of(normalizedProperty, value));
        return new ZigbeeCommandPublishResult(
                "set command published",
                baseTopic + "/" + device.getFriendlyName() + "/set"
        );
    }

    @Transactional
    public ZigbeeCommandPublishResult renameDevice(String ieeeAddress, String friendlyName) {
        return renameDevice(
                LEGACY_COORDINATOR_ID,
                topicSettings.getZigbeeBase(),
                ieeeAddress,
                friendlyName
        );
    }

    private ZigbeeCommandPublishResult renameDevice(
            Integer coordinatorId,
            String baseTopic,
            String ieeeAddress,
            String friendlyName
    ) {
        ZigbeeDeviceSnapshotEntity device = findControllableDevice(coordinatorId, ieeeAddress);
        String nextFriendlyName = normalizeFriendlyName(friendlyName);
        ZigbeeDeviceSnapshotEntity existing = deviceRepository
                .findByCoordinatorIdAndFriendlyName(coordinatorId, nextFriendlyName)
                .orElse(null);
        if (existing != null && !existing.getId().equals(device.getId())) {
            throw new DomainException("conflict", "friendly_name uzhe ispol'zuetsja v etom koordinatore");
        }
        commandGateway.publishRename(baseTopic, device.getFriendlyName(), nextFriendlyName);
        return new ZigbeeCommandPublishResult(
                "rename command published",
                bridgeTopic(baseTopic, "request/device/rename")
        );
    }

    private void handleBridgeState(CoordinatorContext context, ZigbeeMqttSnapshotMessage message) {
        ZigbeeBridgeSnapshotEntity bridge = ensureBridge(context, message.receivedAt());
        bridge.setState(asString(valueFromMap(message.payload(), "state")));
        bridge.setUpdatedAt(message.receivedAt());
        bridgeRepository.save(bridge);
    }

    private void handleBridgeInfo(CoordinatorContext context, ZigbeeMqttSnapshotMessage message) {
        ZigbeeBridgeSnapshotEntity bridge = ensureBridge(context, message.receivedAt());
        bridge.setInfoJson(message.rawPayload());
        bridge.setPermitJoin(asBoolean(valueFromMap(message.payload(), "permit_join")));
        bridge.setPermitJoinEnd(asLong(valueFromMap(message.payload(), "permit_join_end")));
        bridge.setVersion(asString(valueFromMap(message.payload(), "version")));
        Object coordinator = valueFromMap(message.payload(), "coordinator");
        bridge.setCoordinatorJson(toJson(coordinator));
        bridge.setCoordinatorIeeeAddress(asString(valueFromMap(coordinator, "ieee_address")));
        bridge.setUpdatedAt(message.receivedAt());
        bridgeRepository.save(bridge);
    }

    private void handleBridgeDevices(CoordinatorContext context, ZigbeeMqttSnapshotMessage message) {
        ZigbeeBridgeSnapshotEntity bridge = ensureBridge(context, message.receivedAt());
        bridge.setDevicesJson(message.rawPayload());

        Object payload = message.payload();
        if (payload instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?>)) {
                    continue;
                }
                String friendlyName = asString(valueFromMap(item, "friendly_name"));
                if (friendlyName == null || friendlyName.isBlank()) {
                    continue;
                }
                String ieeeAddress = asString(valueFromMap(item, "ieee_address"));
                ZigbeeDeviceSnapshotEntity device = upsertDevice(
                        context.coordinatorId(),
                        ieeeAddress,
                        friendlyName,
                        message.receivedAt()
                );
                String type = asString(valueFromMap(item, "type"));
                device.setDeviceType(type);
                device.setSupported(asBoolean(valueFromMap(item, "supported")));
                device.setDisabled(asBoolean(valueFromMap(item, "disabled")));
                device.setCoordinator("Coordinator".equalsIgnoreCase(type));
                device.setBridgeDeviceJson(toJson(item));
                device.setUpdatedAt(message.receivedAt());
                deviceRepository.save(device);

                if (!device.isCoordinator()) {
                    markFirstDeviceSeen(context, message.receivedAt());
                }

                if (device.isCoordinator()) {
                    bridge.setCoordinatorIeeeAddress(device.getIeeeAddress());
                    bridge.setCoordinatorJson(toJson(item));
                }
            }
        }
        bridge.setUpdatedAt(message.receivedAt());
        bridgeRepository.save(bridge);
    }

    private void handleDeviceState(CoordinatorContext context, ZigbeeMqttSnapshotMessage message) {
        if (message.rawPayload() == null || message.rawPayload().isBlank()) {
            return;
        }
        ZigbeeDeviceSnapshotEntity device = upsertDevice(
                context.coordinatorId(),
                null,
                message.friendlyName(),
                message.receivedAt()
        );
        device.setStateJson(message.rawPayload());
        device.setLastStateAt(message.receivedAt());
        device.setUpdatedAt(message.receivedAt());
        deviceRepository.save(device);
        recordDeviceStateHistory(device, message);
        markFirstDeviceSeen(context, message.receivedAt());
    }

    private void handleDeviceAvailability(CoordinatorContext context, ZigbeeMqttSnapshotMessage message) {
        ZigbeeDeviceSnapshotEntity device = upsertDevice(
                context.coordinatorId(),
                null,
                message.friendlyName(),
                message.receivedAt()
        );
        device.setAvailability(asString(valueFromMap(message.payload(), "state")));
        device.setUpdatedAt(message.receivedAt());
        deviceRepository.save(device);
    }

    private void handleCommandResponse(CoordinatorContext context, ZigbeeMqttSnapshotMessage message) {
        ZigbeeCommandResponseSnapshotEntity response = commandResponseRepository
                .findById(context.coordinatorId())
                .orElseGet(() -> ZigbeeCommandResponseSnapshotEntity.create(
                        context.coordinatorId(),
                        message.receivedAt()
                ));
        response.setTopic(message.topic());
        response.setStatus(asString(valueFromMap(message.payload(), "status")));
        response.setError(asString(valueFromMap(message.payload(), "error")));
        response.setResponseJson(message.rawPayload());
        response.setUpdatedAt(message.receivedAt());
        commandResponseRepository.save(response);
    }

    private ZigbeeBridgeSnapshotEntity ensureBridge(CoordinatorContext context, LocalDateTime now) {
        return bridgeRepository
                .findById(context.coordinatorId())
                .orElseGet(() -> ZigbeeBridgeSnapshotEntity.create(
                        context.coordinatorId(),
                        context.baseTopic(),
                        now
                ));
    }

    private ZigbeeDeviceSnapshotEntity upsertDevice(
            Integer coordinatorId,
            String ieeeAddress,
            String friendlyName,
            LocalDateTime now
    ) {
        if (ieeeAddress != null && !ieeeAddress.isBlank()) {
            ZigbeeDeviceSnapshotEntity byIeee = deviceRepository
                    .findByCoordinatorIdAndIeeeAddress(coordinatorId, ieeeAddress)
                    .orElse(null);
            if (byIeee != null) {
                byIeee.setFriendlyName(friendlyName);
                return byIeee;
            }
        }
        ZigbeeDeviceSnapshotEntity byFriendly = deviceRepository
                .findByCoordinatorIdAndFriendlyName(coordinatorId, friendlyName)
                .orElse(null);
        if (byFriendly != null) {
            if (ieeeAddress != null && !ieeeAddress.isBlank()) {
                byFriendly.setIeeeAddress(ieeeAddress);
            }
            return byFriendly;
        }
        ZigbeeDeviceSnapshotEntity created = ZigbeeDeviceSnapshotEntity.create(coordinatorId, friendlyName, now);
        created.setIeeeAddress(ieeeAddress);
        return created;
    }

    private ZigbeeDeviceSnapshotEntity findControllableDevice(Integer coordinatorId, String ieeeAddress) {
        if (ieeeAddress == null || ieeeAddress.isBlank()) {
            throw new DomainException("bad_request", "ieee_address obyazatelen");
        }
        ZigbeeDeviceSnapshotEntity device = deviceRepository
                .findByCoordinatorIdAndIeeeAddress(coordinatorId, ieeeAddress)
                .orElse(null);
        if (device == null) {
            throw new DomainException("not_found", "Zigbee ustrojstvo ne naideno");
        }
        if (device.isCoordinator()) {
            throw new DomainException("bad_request", "Coordinator read-only");
        }
        return device;
    }

    private ZigbeeCoordinatorData buildCoordinator(Integer coordinatorId, ZigbeeBridgeSnapshotEntity bridge) {
        ZigbeeDeviceSnapshotEntity coordinator = deviceRepository
                .findByCoordinatorIdAndCoordinatorTrueOrderByIdAsc(coordinatorId).stream()
                .findFirst()
                .orElse(null);
        if (coordinator != null) {
            return new ZigbeeCoordinatorData(
                    coordinator.getIeeeAddress(),
                    coordinator.getFriendlyName(),
                    readJson(coordinator.getBridgeDeviceJson())
            );
        }
        if (bridge == null || bridge.getCoordinatorJson() == null) {
            return null;
        }
        return new ZigbeeCoordinatorData(
                bridge.getCoordinatorIeeeAddress(),
                "Coordinator",
                readJson(bridge.getCoordinatorJson())
        );
    }

    private ZigbeeDeviceData toDeviceData(ZigbeeDeviceSnapshotEntity device) {
        Object bridgeDevice = readJson(device.getBridgeDeviceJson());
        Object state = readJson(device.getStateJson());
        Object definition = valueFromMap(bridgeDevice, "definition");
        List<ZigbeeFeatureData> features = buildFeatures(valueFromMap(definition, "exposes"), state);
        return new ZigbeeDeviceData(
                device.getId(),
                device.getIeeeAddress(),
                device.getFriendlyName(),
                device.getDeviceType(),
                device.getSupported(),
                device.getDisabled(),
                device.isCoordinator(),
                bridgeDevice,
                definition,
                buildImageUrl(definition),
                features,
                features.stream().filter(feature -> hasFeatureAccess(feature, ACCESS_STATE)).toList(),
                features.stream().filter(feature -> hasFeatureAccess(feature, ACCESS_SET)).toList(),
                state,
                device.getAvailability(),
                device.getLastStateAt(),
                device.getUpdatedAt()
        );
    }

    private List<ZigbeeFeatureData> buildFeatures(Object exposes, Object state) {
        List<ZigbeeFeatureData> features = new ArrayList<>();
        Map<?, ?> stateMap = state instanceof Map<?, ?> map ? map : Map.of();
        collectFeatures(exposes, stateMap, features);
        return features;
    }

    private void collectFeatures(Object exposes, Map<?, ?> state, List<ZigbeeFeatureData> features) {
        if (!(exposes instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            ZigbeeFeatureData feature = toFeatureData(map, state);
            if (feature.type() != null || feature.name() != null || feature.property() != null) {
                features.add(feature);
            }
            collectFeatures(valueFromMap(map, "features"), state, features);
        }
    }

    private ZigbeeFeatureData toFeatureData(Map<?, ?> feature, Map<?, ?> state) {
        String property = asString(valueFromMap(feature, "property"));
        Object value = property != null ? state.get(property) : null;
        return new ZigbeeFeatureData(
                asString(valueFromMap(feature, "type")),
                property,
                asString(valueFromMap(feature, "name")),
                asString(valueFromMap(feature, "label")),
                asString(valueFromMap(feature, "description")),
                asInteger(valueFromMap(feature, "access")),
                asString(valueFromMap(feature, "unit")),
                valueFromMap(feature, "values"),
                valueFromMap(feature, "value_min"),
                valueFromMap(feature, "value_max"),
                valueFromMap(feature, "value_step"),
                valueFromMap(feature, "value_on"),
                valueFromMap(feature, "value_off"),
                valueFromMap(feature, "value_toggle"),
                asString(valueFromMap(feature, "endpoint")),
                value
        );
    }

    private boolean hasFeatureAccess(ZigbeeFeatureData feature, int access) {
        return feature.property() != null
                && feature.access() != null
                && (feature.access() & access) == access;
    }

    private ZigbeeFeatureData findWritableFeature(ZigbeeDeviceSnapshotEntity device, String property) {
        Object bridgeDevice = readJson(device.getBridgeDeviceJson());
        Object definition = valueFromMap(bridgeDevice, "definition");
        return buildFeatures(valueFromMap(definition, "exposes"), null).stream()
                .filter(feature -> property.equals(feature.property()))
                .filter(feature -> hasFeatureAccess(feature, ACCESS_SET))
                .findFirst()
                .orElse(null);
    }

    private String buildImageUrl(Object definition) {
        String icon = asString(valueFromMap(definition, "icon"));
        if (icon != null && icon.startsWith("https://")) {
            return icon;
        }
        String model = asString(valueFromMap(definition, "model"));
        if (model == null) {
            return null;
        }
        String sanitized = model.replaceAll("[:\\s/]+", "-");
        String encoded = URLEncoder.encode(sanitized, StandardCharsets.UTF_8).replace("+", "%20");
        return DEVICE_IMAGE_BASE_URL + encoded + ".png";
    }

    private ZigbeeCommandResponseData toCommandResponseData(ZigbeeCommandResponseSnapshotEntity response) {
        return new ZigbeeCommandResponseData(
                response.getTopic(),
                response.getStatus(),
                response.getError(),
                readJson(response.getResponseJson()),
                response.getUpdatedAt()
        );
    }

    private void recordDeviceStateHistory(ZigbeeDeviceSnapshotEntity device, ZigbeeMqttSnapshotMessage message) {
        if (!(message.payload() instanceof Map<?, ?> map)) {
            return;
        }
        ZigbeeDeviceStateEventEntity event = ZigbeeDeviceStateEventEntity.create(device.getCoordinatorId());
        event.setDeviceSnapshot(device);
        event.setIeeeAddress(device.getIeeeAddress());
        event.setFriendlyName(device.getFriendlyName());
        event.setTs(message.receivedAt());
        event.setRawStateJson(message.rawPayload());
        event.setCreatedAt(message.receivedAt());
        stateEventRepository.save(event);

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String property = entry.getKey() != null ? entry.getKey().toString() : null;
            Object value = entry.getValue();
            if (property == null || property.isBlank() || !isPrimitiveHistoryValue(value)) {
                continue;
            }
            ZigbeeDevicePropertyReadingEntity reading = ZigbeeDevicePropertyReadingEntity.create(
                    device.getCoordinatorId()
            );
            reading.setStateEvent(event);
            reading.setDeviceSnapshot(device);
            reading.setIeeeAddress(device.getIeeeAddress());
            reading.setFriendlyName(device.getFriendlyName());
            reading.setProperty(property);
            reading.setTs(message.receivedAt());
            reading.setCreatedAt(message.receivedAt());
            applyHistoryValue(reading, value);
            propertyReadingRepository.save(reading);
        }
    }

    private boolean isPrimitiveHistoryValue(Object value) {
        return value instanceof Number || value instanceof Boolean || value instanceof CharSequence;
    }

    private void applyHistoryValue(ZigbeeDevicePropertyReadingEntity reading, Object value) {
        if (value instanceof Number number) {
            reading.setValueNumeric(number.doubleValue());
            reading.setValueText(value.toString());
            return;
        }
        if (value instanceof Boolean bool) {
            reading.setValueBoolean(bool);
            reading.setValueText(value.toString());
            return;
        }
        reading.setValueText(value != null ? value.toString() : null);
    }

    private Double normalizedHistoryValue(ZigbeeDevicePropertyReadingEntity row) {
        if (row.getValueNumeric() != null) {
            return row.getValueNumeric();
        }
        if (row.getValueBoolean() != null) {
            return row.getValueBoolean() ? 1.0 : 0.0;
        }
        String text = row.getValueText();
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase();
        if ("on".equals(normalized) || "true".equals(normalized) || "running".equals(normalized)) {
            return 1.0;
        }
        if ("off".equals(normalized)
                || "false".equals(normalized)
                || "idle".equals(normalized)
                || "stopped".equals(normalized)
                || "completed".equals(normalized)) {
            return 0.0;
        }
        return null;
    }

    private String rawHistoryValue(ZigbeeDevicePropertyReadingEntity row) {
        if (row.getValueText() != null) {
            return row.getValueText();
        }
        if (row.getValueBoolean() != null) {
            return row.getValueBoolean().toString();
        }
        return row.getValueNumeric() != null ? row.getValueNumeric().toString() : null;
    }

    private List<ZigbeeDevicePropertyReadingEntity> downsample(
            List<ZigbeeDevicePropertyReadingEntity> points,
            int maxPoints
    ) {
        if (points.size() <= maxPoints) {
            return points;
        }
        int step = (int) Math.ceil(points.size() / (double) maxPoints);
        List<ZigbeeDevicePropertyReadingEntity> sampled = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            sampled.add(points.get(i));
        }
        return sampled;
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return json;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private Object valueFromMap(Object payload, String key) {
        if (payload instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private CoordinatorContext resolveInboundCoordinator(ZigbeeMqttSnapshotMessage message) {
        if (message.mqttUsername() != null && !message.mqttUsername().isBlank()) {
            ZigbeeCoordinatorEntity coordinator = coordinatorRepository
                    .findByMqttUsernameAndArchivedAtIsNull(message.mqttUsername())
                    .orElse(null);
            if (coordinator == null || !coordinator.getBaseTopic().equals(message.baseTopic())) {
                return null;
            }
            return new CoordinatorContext(coordinator.getId(), coordinator.getBaseTopic(), coordinator);
        }

        String legacyBaseTopic = message.baseTopic() != null && !message.baseTopic().isBlank()
                ? message.baseTopic()
                : topicSettings.getZigbeeBase();
        ZigbeeCoordinatorEntity legacy = coordinatorRepository
                .findByBaseTopicAndArchivedAtIsNull(legacyBaseTopic)
                .orElse(null);
        return legacy != null
                ? new CoordinatorContext(legacy.getId(), legacy.getBaseTopic(), legacy)
                : new CoordinatorContext(LEGACY_COORDINATOR_ID, legacyBaseTopic, null);
    }

    private void touchCoordinator(CoordinatorContext context, ZigbeeMqttSnapshotMessage message) {
        ZigbeeCoordinatorEntity coordinator = context.coordinator();
        if (coordinator == null) {
            return;
        }
        LocalDateTime now = message.receivedAt();
        coordinator.setLastSeenAt(now);
        coordinator.setUpdatedAt(now);

        String bridgeState = message.type() == ru.growerhub.backend.zigbee.contract.ZigbeeMqttMessageType.BRIDGE_STATE
                ? asString(valueFromMap(message.payload(), "state"))
                : null;
        if (bridgeState == null && message.type()
                == ru.growerhub.backend.zigbee.contract.ZigbeeMqttMessageType.BRIDGE_STATE) {
            bridgeState = asString(message.payload());
        }
        if (bridgeState != null && "offline".equalsIgnoreCase(bridgeState)) {
            coordinator.setStatus(ZigbeeCoordinatorStatus.OFFLINE);
        } else {
            coordinator.setStatus(ZigbeeCoordinatorStatus.ONLINE);
            if (coordinator.getConnectedAt() == null) {
                coordinator.setConnectedAt(now);
            }
        }
        coordinatorRepository.save(coordinator);
    }

    private void markFirstDeviceSeen(CoordinatorContext context, LocalDateTime seenAt) {
        ZigbeeCoordinatorEntity coordinator = context.coordinator();
        if (coordinator == null || coordinator.getFirstDeviceSeenAt() != null) {
            return;
        }
        coordinator.setFirstDeviceSeenAt(seenAt);
        coordinator.setUpdatedAt(seenAt);
        coordinatorRepository.save(coordinator);
    }

    private ZigbeeCoordinatorEntity findOwnedCoordinator(AuthenticatedUser user, UUID publicId) {
        if (publicId == null) {
            throw new DomainException("not_found", "Koordinator ne naiden");
        }
        return coordinatorRepository
                .findByPublicIdAndUserIdAndArchivedAtIsNull(publicId, user.id())
                .orElseThrow(() -> new DomainException("not_found", "Koordinator ne naiden"));
    }

    private ZigbeeCoordinatorSummary toCoordinatorSummary(ZigbeeCoordinatorEntity coordinator) {
        long deviceCount = deviceRepository.countByCoordinatorIdAndCoordinatorFalse(coordinator.getId());
        return new ZigbeeCoordinatorSummary(
                coordinator.getPublicId(),
                coordinator.getName(),
                coordinator.getMqttUsername(),
                coordinator.getBaseTopic(),
                coordinator.getStatus(),
                (int) Math.min(Integer.MAX_VALUE, deviceCount),
                coordinator.getLastSeenAt(),
                coordinator.getConnectedAt(),
                coordinator.getFirstDeviceSeenAt(),
                coordinator.getCreatedAt(),
                coordinator.getUpdatedAt()
        );
    }

    private List<ZigbeeOwnedDeviceData> collectCoordinatorDevices(List<ZigbeeCoordinatorEntity> coordinators) {
        List<ZigbeeOwnedDeviceData> result = new ArrayList<>();
        for (ZigbeeCoordinatorEntity coordinator : coordinators) {
            List<ZigbeeDeviceSnapshotEntity> devices = deviceRepository
                    .findAllByCoordinatorIdOrderByCoordinatorDescFriendlyNameAsc(coordinator.getId());
            for (ZigbeeDeviceSnapshotEntity device : devices) {
                result.add(new ZigbeeOwnedDeviceData(
                        coordinator.getId(),
                        coordinator.getPublicId(),
                        coordinator.getName(),
                        toDeviceData(device)
                ));
            }
        }
        return List.copyOf(result);
    }

    private ZigbeeCoordinatorSetup buildSetup(ZigbeeCoordinatorEntity coordinator, String password) {
        String configurationYaml = """
                version: 5
                homeassistant:
                  enabled: false
                frontend:
                  enabled: true
                  host: 127.0.0.1
                  port: 8080
                mqtt:
                  server: '!secret.yaml server'
                  user: '!secret.yaml user'
                  password: '!secret.yaml password'
                  base_topic: '%s'
                  client_id: '%s'
                  reject_unauthorized: true
                serial:
                  port: CHANGE_ME_SERIAL_PORT
                  adapter: CHANGE_ME_ADAPTER
                advanced:
                  network_key: GENERATE
                availability:
                  enabled: true
                """.formatted(coordinator.getBaseTopic(), coordinator.getMqttUsername());
        String secretYaml = """
                server: '%s'
                user: '%s'
                password: '%s'
                """.formatted(
                selfServiceSettings.getMqttServer(),
                coordinator.getMqttUsername(),
                password
        );
        return new ZigbeeCoordinatorSetup(
                selfServiceSettings.getMqttServer(),
                coordinator.getMqttUsername(),
                password,
                coordinator.getMqttUsername(),
                coordinator.getBaseTopic(),
                configurationYaml,
                secretYaml
        );
    }

    private void requireSelfService(AuthenticatedUser user) {
        if (user == null || user.id() == null) {
            throw new DomainException("unauthorized", "Nuzhna avtorizacija");
        }
        if (!selfServiceSettings.isEnabled()) {
            throw new DomainException("unavailable", "Self-service poka vyklyuchen do zavershenija proverki");
        }
    }

    private void enforceCredentialCooldown(Integer userId, LocalDateTime now) {
        ZigbeeCoordinatorEntity latest = coordinatorRepository
                .findFirstByUserIdOrderByCredentialIssuedAtDesc(userId)
                .orElse(null);
        if (latest != null) {
            enforceCoordinatorCredentialCooldown(latest, now);
        }
    }

    private void enforceCoordinatorCredentialCooldown(ZigbeeCoordinatorEntity coordinator, LocalDateTime now) {
        int cooldownSeconds = Math.max(0, selfServiceSettings.getCredentialCooldownSeconds());
        LocalDateTime issuedAt = coordinator.getCredentialIssuedAt();
        if (cooldownSeconds > 0 && issuedAt != null && issuedAt.plusSeconds(cooldownSeconds).isAfter(now)) {
            throw new DomainException("too_many_requests", "Povtorite vydachu MQTT credentials pozhe");
        }
    }

    private String generateMqttUsername() {
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        return "z2m_" + HexFormat.of().formatHex(randomBytes);
    }

    private String generatePassword() {
        int byteCount = Math.max(32, Math.min(64, selfServiceSettings.getPasswordBytes()));
        byte[] randomBytes = new byte[byteCount];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String normalizeCoordinatorName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new DomainException("bad_request", "name obyazatelen");
        }
        String normalized = name.trim();
        if (normalized.length() > 80) {
            throw new DomainException("bad_request", "name ne dolzhen byt' dlinnee 80 simvolov");
        }
        return normalized;
    }

    private String normalizeState(String state) {
        if (state == null) {
            throw new DomainException("bad_request", "state obyazatelen");
        }
        String normalized = state.trim().toUpperCase();
        if (!"ON".equals(normalized) && !"OFF".equals(normalized)) {
            throw new DomainException("bad_request", "state dolzhen byt' ON ili OFF");
        }
        return normalized;
    }

    private String normalizeFriendlyName(String friendlyName) {
        if (friendlyName == null) {
            throw new DomainException("bad_request", "friendly_name obyazatelen");
        }
        String normalized = friendlyName.trim();
        if (normalized.isBlank()) {
            throw new DomainException("bad_request", "friendly_name obyazatelen");
        }
        if (normalized.length() > 100
                || normalized.contains("/")
                || normalized.contains("+")
                || normalized.contains("#")
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new DomainException("bad_request", "friendly_name soderzhit nedopustimye simvoly");
        }
        return normalized;
    }

    private String normalizeProperty(String property) {
        if (property == null) {
            throw new DomainException("bad_request", "property obyazatelen");
        }
        String normalized = property.trim();
        if (normalized.isBlank()) {
            throw new DomainException("bad_request", "property obyazatelen");
        }
        return normalized;
    }

    private String bridgeTopic(String baseTopic, String relative) {
        return baseTopic + "/bridge/" + relative;
    }

    private record CoordinatorContext(
            Integer coordinatorId,
            String baseTopic,
            ZigbeeCoordinatorEntity coordinator
    ) {
    }
}
