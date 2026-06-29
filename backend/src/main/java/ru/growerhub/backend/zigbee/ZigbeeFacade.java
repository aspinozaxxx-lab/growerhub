package ru.growerhub.backend.zigbee;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.config.ZigbeeSettings;
import ru.growerhub.backend.common.config.mqtt.MqttTopicSettings;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.zigbee.contract.ZigbeeBridgeData;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandGateway;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandPublishResult;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandResponseData;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorData;
import ru.growerhub.backend.zigbee.contract.ZigbeeDeviceData;
import ru.growerhub.backend.zigbee.contract.ZigbeeFeatureData;
import ru.growerhub.backend.zigbee.contract.ZigbeeMqttSnapshotMessage;
import ru.growerhub.backend.zigbee.contract.ZigbeeOverviewData;
import ru.growerhub.backend.zigbee.jpa.ZigbeeBridgeSnapshotEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeBridgeSnapshotRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCommandResponseSnapshotEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeCommandResponseSnapshotRepository;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotEntity;
import ru.growerhub.backend.zigbee.jpa.ZigbeeDeviceSnapshotRepository;

@Service
public class ZigbeeFacade {
    private static final int ACCESS_STATE = 0b001;
    private static final int ACCESS_SET = 0b010;
    private static final String DEVICE_IMAGE_BASE_URL = "https://www.zigbee2mqtt.io/images/devices/";

    private final ZigbeeBridgeSnapshotRepository bridgeRepository;
    private final ZigbeeDeviceSnapshotRepository deviceRepository;
    private final ZigbeeCommandResponseSnapshotRepository commandResponseRepository;
    private final ZigbeeCommandGateway commandGateway;
    private final ZigbeeSettings zigbeeSettings;
    private final MqttTopicSettings topicSettings;
    private final ObjectMapper objectMapper;

    public ZigbeeFacade(
            ZigbeeBridgeSnapshotRepository bridgeRepository,
            ZigbeeDeviceSnapshotRepository deviceRepository,
            ZigbeeCommandResponseSnapshotRepository commandResponseRepository,
            ZigbeeCommandGateway commandGateway,
            ZigbeeSettings zigbeeSettings,
            MqttTopicSettings topicSettings,
            ObjectMapper objectMapper
    ) {
        this.bridgeRepository = bridgeRepository;
        this.deviceRepository = deviceRepository;
        this.commandResponseRepository = commandResponseRepository;
        this.commandGateway = commandGateway;
        this.zigbeeSettings = zigbeeSettings;
        this.topicSettings = topicSettings;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ZigbeeOverviewData getOverview() {
        ZigbeeBridgeSnapshotEntity bridge = bridgeRepository
                .findById(ZigbeeBridgeSnapshotEntity.SINGLETON_ID)
                .orElse(null);
        ZigbeeBridgeData bridgeData = bridge != null
                ? new ZigbeeBridgeData(
                        bridge.getBaseTopic(),
                        bridge.getState(),
                        readJson(bridge.getInfoJson()),
                        bridge.getPermitJoin(),
                        bridge.getPermitJoinEnd(),
                        bridge.getVersion(),
                        bridge.getUpdatedAt())
                : new ZigbeeBridgeData(topicSettings.getZigbeeBase(), null, null, null, null, null, null);

        ZigbeeCoordinatorData coordinator = buildCoordinator(bridge);
        List<ZigbeeDeviceData> devices = deviceRepository.findAllByOrderByCoordinatorDescFriendlyNameAsc().stream()
                .map(this::toDeviceData)
                .toList();
        ZigbeeCommandResponseData response = commandResponseRepository
                .findById(ZigbeeCommandResponseSnapshotEntity.SINGLETON_ID)
                .map(this::toCommandResponseData)
                .orElse(null);
        return new ZigbeeOverviewData(bridgeData, coordinator, devices, response);
    }

    @Transactional
    public void handleMqttSnapshot(ZigbeeMqttSnapshotMessage message) {
        if (message == null || message.type() == null) {
            return;
        }
        switch (message.type()) {
            case BRIDGE_STATE -> handleBridgeState(message);
            case BRIDGE_INFO -> handleBridgeInfo(message);
            case BRIDGE_DEVICES -> handleBridgeDevices(message);
            case DEVICE_STATE -> handleDeviceState(message);
            case DEVICE_AVAILABILITY -> handleDeviceAvailability(message);
            case COMMAND_RESPONSE -> handleCommandResponse(message);
            default -> {
            }
        }
    }

    @Transactional
    public ZigbeeCommandPublishResult permitJoin(Integer seconds) {
        int resolvedSeconds = seconds != null ? seconds : zigbeeSettings.getPermitJoinDefaultSeconds();
        if (resolvedSeconds < 0 || resolvedSeconds > 254) {
            throw new DomainException("bad_request", "seconds dolzhen byt' v diapazone 0..254");
        }
        commandGateway.publishPermitJoin(resolvedSeconds);
        return new ZigbeeCommandPublishResult("permit join command published", bridgeTopic("request/permit_join"));
    }

    @Transactional
    public ZigbeeCommandPublishResult setDeviceState(String ieeeAddress, String state) {
        String normalizedState = normalizeState(state);
        return setDeviceProperty(ieeeAddress, "state", normalizedState);
    }

    @Transactional
    public ZigbeeCommandPublishResult setDeviceProperty(String ieeeAddress, String property, Object value) {
        ZigbeeDeviceSnapshotEntity device = findControllableDevice(ieeeAddress);
        String normalizedProperty = normalizeProperty(property);
        if (value == null) {
            throw new DomainException("bad_request", "value obyazatelen");
        }
        ZigbeeFeatureData feature = findWritableFeature(device, normalizedProperty);
        if (feature == null) {
            throw new DomainException("bad_request", "Zigbee property nedostupen dlya zapisi");
        }
        commandGateway.publishSet(device.getFriendlyName(), Map.of(normalizedProperty, value));
        return new ZigbeeCommandPublishResult(
                "set command published",
                topicSettings.getZigbeeBase() + "/" + device.getFriendlyName() + "/set"
        );
    }

    @Transactional
    public ZigbeeCommandPublishResult renameDevice(String ieeeAddress, String friendlyName) {
        ZigbeeDeviceSnapshotEntity device = findControllableDevice(ieeeAddress);
        String nextFriendlyName = normalizeFriendlyName(friendlyName);
        commandGateway.publishRename(device.getFriendlyName(), nextFriendlyName);
        return new ZigbeeCommandPublishResult("rename command published", bridgeTopic("request/device/rename"));
    }

    private void handleBridgeState(ZigbeeMqttSnapshotMessage message) {
        ZigbeeBridgeSnapshotEntity bridge = ensureBridge(message.receivedAt());
        bridge.setState(asString(valueFromMap(message.payload(), "state")));
        bridge.setUpdatedAt(message.receivedAt());
        bridgeRepository.save(bridge);
    }

    private void handleBridgeInfo(ZigbeeMqttSnapshotMessage message) {
        ZigbeeBridgeSnapshotEntity bridge = ensureBridge(message.receivedAt());
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

    private void handleBridgeDevices(ZigbeeMqttSnapshotMessage message) {
        ZigbeeBridgeSnapshotEntity bridge = ensureBridge(message.receivedAt());
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
                ZigbeeDeviceSnapshotEntity device = upsertDevice(ieeeAddress, friendlyName, message.receivedAt());
                String type = asString(valueFromMap(item, "type"));
                device.setDeviceType(type);
                device.setSupported(asBoolean(valueFromMap(item, "supported")));
                device.setDisabled(asBoolean(valueFromMap(item, "disabled")));
                device.setCoordinator("Coordinator".equalsIgnoreCase(type));
                device.setBridgeDeviceJson(toJson(item));
                device.setUpdatedAt(message.receivedAt());
                deviceRepository.save(device);

                if (device.isCoordinator()) {
                    bridge.setCoordinatorIeeeAddress(device.getIeeeAddress());
                    bridge.setCoordinatorJson(toJson(item));
                }
            }
        }
        bridge.setUpdatedAt(message.receivedAt());
        bridgeRepository.save(bridge);
    }

    private void handleDeviceState(ZigbeeMqttSnapshotMessage message) {
        if (message.rawPayload() == null || message.rawPayload().isBlank()) {
            return;
        }
        ZigbeeDeviceSnapshotEntity device = upsertDevice(null, message.friendlyName(), message.receivedAt());
        device.setStateJson(message.rawPayload());
        device.setLastStateAt(message.receivedAt());
        device.setUpdatedAt(message.receivedAt());
        deviceRepository.save(device);
    }

    private void handleDeviceAvailability(ZigbeeMqttSnapshotMessage message) {
        ZigbeeDeviceSnapshotEntity device = upsertDevice(null, message.friendlyName(), message.receivedAt());
        device.setAvailability(asString(valueFromMap(message.payload(), "state")));
        device.setUpdatedAt(message.receivedAt());
        deviceRepository.save(device);
    }

    private void handleCommandResponse(ZigbeeMqttSnapshotMessage message) {
        ZigbeeCommandResponseSnapshotEntity response = commandResponseRepository
                .findById(ZigbeeCommandResponseSnapshotEntity.SINGLETON_ID)
                .orElseGet(() -> ZigbeeCommandResponseSnapshotEntity.create(message.receivedAt()));
        response.setTopic(message.topic());
        response.setStatus(asString(valueFromMap(message.payload(), "status")));
        response.setError(asString(valueFromMap(message.payload(), "error")));
        response.setResponseJson(message.rawPayload());
        response.setUpdatedAt(message.receivedAt());
        commandResponseRepository.save(response);
    }

    private ZigbeeBridgeSnapshotEntity ensureBridge(LocalDateTime now) {
        return bridgeRepository
                .findById(ZigbeeBridgeSnapshotEntity.SINGLETON_ID)
                .orElseGet(() -> ZigbeeBridgeSnapshotEntity.create(topicSettings.getZigbeeBase(), now));
    }

    private ZigbeeDeviceSnapshotEntity upsertDevice(String ieeeAddress, String friendlyName, LocalDateTime now) {
        if (ieeeAddress != null && !ieeeAddress.isBlank()) {
            ZigbeeDeviceSnapshotEntity byIeee = deviceRepository.findByIeeeAddress(ieeeAddress).orElse(null);
            if (byIeee != null) {
                byIeee.setFriendlyName(friendlyName);
                return byIeee;
            }
        }
        ZigbeeDeviceSnapshotEntity byFriendly = deviceRepository.findFirstByFriendlyNameOrderByIdAsc(friendlyName).orElse(null);
        if (byFriendly != null) {
            if (ieeeAddress != null && !ieeeAddress.isBlank()) {
                byFriendly.setIeeeAddress(ieeeAddress);
            }
            return byFriendly;
        }
        ZigbeeDeviceSnapshotEntity created = ZigbeeDeviceSnapshotEntity.create(friendlyName, now);
        created.setIeeeAddress(ieeeAddress);
        return created;
    }

    private ZigbeeDeviceSnapshotEntity findControllableDevice(String ieeeAddress) {
        if (ieeeAddress == null || ieeeAddress.isBlank()) {
            throw new DomainException("bad_request", "ieee_address obyazatelen");
        }
        ZigbeeDeviceSnapshotEntity device = deviceRepository.findByIeeeAddress(ieeeAddress).orElse(null);
        if (device == null) {
            throw new DomainException("not_found", "Zigbee ustrojstvo ne naideno");
        }
        if (device.isCoordinator()) {
            throw new DomainException("bad_request", "Coordinator read-only");
        }
        return device;
    }

    private ZigbeeCoordinatorData buildCoordinator(ZigbeeBridgeSnapshotEntity bridge) {
        ZigbeeDeviceSnapshotEntity coordinator = deviceRepository.findByCoordinatorTrueOrderByIdAsc().stream()
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

    private String bridgeTopic(String relative) {
        return topicSettings.getZigbeeBase() + "/bridge/" + relative;
    }
}
